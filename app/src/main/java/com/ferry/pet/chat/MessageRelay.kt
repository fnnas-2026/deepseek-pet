package com.ferry.pet.chat

import com.ferry.pet.MessageChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 双向消息轮询：对启用且开启「双向」的渠道，周期性拉取对方发来的消息，
 * 交给上层回调生成 AI 回复，再经 [MessagePusher] 回推。
 *
 * 只对接自建接入端 / webhook，不逆向官方客户端协议：
 *  - QQ：NapCat/Lagrange 风格 HTTP，GET {baseUrl}/get_msg?message_id=… 之外，
 *    取最近消息列表用 {baseUrl}/get_friend_msg?user_id=… 或 {baseUrl}/get_group_msg_history?group_id=…；
 *    这里按接入端通用的最近消息结构解析文本。
 *  - 微信：企业微信自建应用/群机器人通常不提供拉取接口；双向收消息需在接入端把
 *    回调 POST 到一个公网中转，中转再转发给本 App。当前微信渠道以单向回推为主，
 *    双向轮询位预留，待接入端提供轮询读取接口后补解析。
 *
 * 去重：按渠道记录最近一条消息时间戳 [MessageChannel.lastSeen]，只处理更新的消息；
 * 同一接入端重启/时间回拨时，以消息 message_id 集合在内存内兜底去重。
 */
class MessageRelay(
    private val onIncoming: suspend (channel: MessageChannel, text: String) -> String?,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val seenIds = LinkedHashSet<String>()

    /** 每轮遍历渠道的回调；返回当前渠道列表。 */
    @Volatile var channelsProvider: () -> List<MessageChannel> = { emptyList() }
    /** 回写渠道已处理时间戳（持久化到偏好）。 */
    @Volatile var onSeen: (MessageChannel, Long) -> Unit = { _, _ -> }
    /** 回推时复用同一套推送逻辑。 */
    @Volatile var pusher: MessagePusher = MessagePusher

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                runCatching { pollOnce() }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollOnce() = withContext(Dispatchers.IO) {
        val channels = channelsProvider().filter { it.enabled && it.bidirectional && it.baseUrl.isNotBlank() }
        channels.forEach { channel ->
            runCatching { pollChannel(channel) }
        }
    }

    private suspend fun pollChannel(channel: MessageChannel) {
        when (channel.kind) {
            MessageChannel.KIND_QQ -> pollQq(channel)
            MessageChannel.KIND_WECHAT -> pollWechat(channel)
        }
    }

    /** QQ：读取最近消息，过滤目标与自身消息，取新文本交给上层回复。 */
    private suspend fun pollQq(channel: MessageChannel) {
        val base = channel.baseUrl.trimEnd('/')
        val isGroup = channel.target.startsWith("g:")
        val target = channel.target.removePrefix("g:")
        val endpoint = if (isGroup) {
            "$base/get_group_msg_history?group_id=${target}"
        } else {
            "$base/get_friend_msg?user_id=${target}"
        }
        val request = Request.Builder().url(endpoint).get().apply {
            if (channel.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${channel.apiKey}")
        }.build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return
        val messages = root.optJSONArray("messages") ?: root.optJSONArray("data") ?: return

        var newest = channel.lastSeen
        var latestText: String? = null
        for (i in messages.length() - 1 downTo 0) {
            val obj = messages.optJSONObject(i) ?: continue
            val msgId = obj.optString("message_id", obj.optString("message_id_str", ""))
            val sender = obj.optJSONObject("sender")?.optString("user_id") ?: obj.optString("user_id", "")
            val time = obj.optLong("time", 0L)
            val text = plainText(obj.opt("message")) ?: continue
            // 跳过自己发出的消息；无时间戳时按 msgId 去重。
            if (msgId.isNotBlank()) {
                if (msgId in seenIds) continue
                seenIds.add(msgId)
            }
            if (time > newest) newest = time
            if (sender.isNotBlank() && sender == channel.apiKey) continue
            latestText = text
            break
        }
        latestText?.let { text ->
            val reply = onIncoming(channel, text)
            if (!reply.isNullOrBlank()) {
                pusher.push(listOf(channel), reply)
            }
        }
        if (newest > channel.lastSeen) {
            onSeen(channel, newest)
        }
    }

    /** 微信：企业微信无公开拉取接口，预留；收到接入端中转回调后可在此补充。 */
    private suspend fun pollWechat(channel: MessageChannel) {
        // no-op：微信双向收消息依赖自建中转把回调推给本 App（本地轮询不可得）。
    }

    /** 把消息段（可能是字符串或 message 数组）拍平成纯文本。 */
    private fun plainText(node: Any?): String? {
        return when (node) {
            is String -> node.takeIf { it.isNotBlank() }
            is org.json.JSONArray -> {
                val parts = (0 until node.length()).mapNotNull { i ->
                    val item = node.optJSONObject(i) ?: return@mapNotNull null
                    val type = item.optString("type")
                    if (type == "text") item.optJSONObject("data")?.optString("text")
                    else null
                }
                parts.joinToString("").takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 12_000L
    }
}
