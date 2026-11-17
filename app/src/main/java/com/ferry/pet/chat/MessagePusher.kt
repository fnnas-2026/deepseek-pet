package com.ferry.pet.chat

import com.ferry.pet.MessageChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 把桌宠回复推送到已启用的 QQ 机器人 / 微信·企业微信 webhook。
 * 只对接自建接入端或群机器人 webhook，不逆向官方客户端协议。
 *
 *  - qq：NapCat/Lagrange 风格 HTTP 上报。baseUrl 取到主机根，
 *    目标为 QQ 号（私聊）或群号；按是否填群号决定 send_private_msg / send_group_msg。
 *  - wechat：企业微信群机器人 webhook，POST {webhook}，markdown/text 文本。
 */
object MessagePusher {

    sealed class Result {
        data class Success(val sent: Int) : Result()
        data class Failure(val message: String) : Result()
    }

    private sealed class PushOutcome
    private object PushOk : PushOutcome()
    private class PushFail(val message: String) : PushOutcome()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val MEDIA_JSON = "application/json; charset=utf-8".toMediaType()

    /** 向所有已启用渠道推送 [text]；返回成功条数与首个错误说明。 */
    suspend fun push(channels: List<MessageChannel>, text: String): Result =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext Result.Failure("内容为空")
            val enabled = channels.filter { it.enabled && it.baseUrl.isNotBlank() }
            if (enabled.isEmpty()) return@withContext Result.Failure("未启用消息渠道")
            var sent = 0
            var firstError = ""
            enabled.forEach { channel ->
                when (val outcome = pushOne(channel, text)) {
                    PushOk -> sent++
                    is PushFail -> if (firstError.isBlank()) firstError = outcome.message
                }
            }
            when {
                sent > 0 -> Result.Success(sent)
                else -> Result.Failure(firstError.ifBlank { "推送失败" })
            }
        }

    private fun pushOne(channel: MessageChannel, text: String): PushOutcome = try {
        when (channel.kind) {
            MessageChannel.KIND_QQ -> pushQq(channel, text)
            MessageChannel.KIND_WECHAT -> pushWechat(channel, text)
            else -> PushFail("未知消息渠道类型：${channel.kind}")
        }
    } catch (e: IOException) {
        PushFail("网络错误：${e.message ?: "无法连接"}")
    } catch (e: Exception) {
        PushFail("推送失败：${e.message ?: "未知错误"}")
    }

    /** NapCat/Lagrange HTTP 上报：私聊 send_private_msg / 群聊 send_group_msg。 */
    private fun pushQq(channel: MessageChannel, text: String): PushOutcome {
        val base = channel.baseUrl.trimEnd('/')
        val isGroup = channel.target.startsWith("g:")
        val target = channel.target.removePrefix("g:")
        val endpoint = if (isGroup) "$base/send_group_msg" else "$base/send_private_msg"
        val body = JSONObject().apply {
            put("message_type", if (isGroup) "group" else "private")
            if (isGroup) put("group_id", target.toLongOrNull() ?: 0L)
            else put("user_id", target.toLongOrNull() ?: 0L)
            put("message", text)
        }
        val request = Request.Builder().url(endpoint)
            .addHeader("Content-Type", "application/json")
            .apply { if (channel.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${channel.apiKey}") }
            .post(body.toString().toRequestBody(MEDIA_JSON))
            .build()
        val response = client.newCall(request).execute()
        val respBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) return PushFail("QQ 接口返回 ${response.code}：${respBody.take(120)}")
        return PushOk
    }

    /** 企业微信群机器人 webhook：msgtype=text，content 为文本。 */
    private fun pushWechat(channel: MessageChannel, text: String): PushOutcome {
        val body = JSONObject()
            .put("msgtype", "text")
            .put("text", JSONObject().put("content", text))
        val request = Request.Builder().url(channel.baseUrl.trim())
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(MEDIA_JSON))
            .build()
        val response = client.newCall(request).execute()
        val respBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) return PushFail("微信 webhook 返回 ${response.code}：${respBody.take(120)}")
        val code = runCatching { JSONObject(respBody).optInt("errcode", 0) }.getOrDefault(0)
        if (code != 0) return PushFail("微信返回错误码 $code：${respBody.take(120)}")
        return PushOk
    }
}
