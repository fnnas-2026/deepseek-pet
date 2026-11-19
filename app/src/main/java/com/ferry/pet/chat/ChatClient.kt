package com.ferry.pet.chat

import com.ferry.pet.Channel
import com.ferry.pet.ChatOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 / Ollama 原生双风格聊天客户端。借鉴 Ferry 摆渡的聊天体验，
 * 接口地址、Key、模型、风格都在设置页可配。
 *
 * 风格：
 *  - openai：POST {baseUrl}/chat/completions，GET {baseUrl}/models
 *  - ollama：POST {baseUrl}/api/chat，GET {baseUrl}/api/tags
 * 回调只做一次：成功返回助手文本，失败返回错误说明；不在此处做流式。
 */
class ChatClient(private val channel: Channel, private val systemPrompt: String = "",
                 private val temperature: Float = .7f) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val isOllama: Boolean get() = channel.api.trim().equals("ollama", true)

    sealed class Result {
        data class Success(val reply: String) : Result()
        data class Failure(val message: String) : Result()
    }

    sealed class ModelsResult {
        data class Success(val models: List<String>) : ModelsResult()
        data class Failure(val message: String) : ModelsResult()
    }

    sealed class BalanceResult {
        data class Success(val text: String) : BalanceResult()
        data class Failure(val message: String) : BalanceResult()
        /** 该渠道后端未提供公开余额查询接口。 */
        data class Unsupported(val message: String) : BalanceResult()
    }

    suspend fun send(history: List<ChatMessage>): Result = withContext(Dispatchers.IO) {
        if (channel.baseUrl.isBlank()) return@withContext Result.Failure("请先在渠道设置中填写接口地址")
        if (channel.apiKey.isBlank()) return@withContext Result.Failure("请先在设置中填写 API Key")
        if (channel.model.isBlank()) return@withContext Result.Failure("请先填写或拉取模型名")
        if (isOllama) sendOllama(history) else sendOpenAi(history)
    }

    /** 把网关 HTTP 状态码映射为可读提示（鉴权失效 / 余额不足 / 限流等）。 */
    private fun friendlyError(code: Int, body: String): String = when (code) {
        401 -> "鉴权失败：API Key 无效或已失效，请检查密钥"
        402 -> "余额或额度不足，请在平台充值后重试"
        429 -> "请求过于频繁或触发限流，请稍后再试"
        404 -> "接口地址或模型不存在，请检查网关地址与模型名"
        500, 502, 503, 504 -> "服务端异常（$code），请稍后再试"
        else -> "接口返回 $code：${errorSummary(body)}"
    }

    /** 归一化网关地址：去掉结尾斜杠；OpenAI 兼容缺省补 /v1；已带接口路径则不补。 */
    private fun normalizedBase(): String = normalizeBase(channel.baseUrl, isOllama)

    /** OpenAI 兼容：POST {baseUrl}/chat/completions */
    private fun sendOpenAi(history: List<ChatMessage>): Result {
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", sanitizeContent(systemPrompt)))
        }
        history.forEach {
            messages.put(JSONObject().put("role", it.role).put("content", sanitizeContent(it.content)))
        }

        val payload = JSONObject()
            .put("model", channel.model)
            .put("messages", messages)
            .put("temperature", temperature)

        val url = normalizedBase() + "/chat/completions"
        val request = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${channel.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(MEDIA_JSON))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Result.Failure(friendlyError(response.code, body))
            } else {
                businessError(body)?.let { return Result.Failure(it) }
                val text = normalizeReply(parseOpenAiText(body))
                if (text.isBlank()) Result.Failure("接口未返回有效内容") else Result.Success(text)
            }
        } catch (e: IOException) {
            Result.Failure("网络错误：${e.message ?: "无法连接"}")
        } catch (e: Exception) {
            Result.Failure("请求失败：${e.message ?: "未知错误"}")
        }
    }

    /** Ollama 原生：POST {baseUrl}/api/chat */
    private fun sendOllama(history: List<ChatMessage>): Result {
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", sanitizeContent(systemPrompt)))
        }
        history.forEach {
            messages.put(JSONObject().put("role", it.role).put("content", sanitizeContent(it.content)))
        }

        val optionsObj = JSONObject().put("temperature", temperature)
        val payload = JSONObject()
            .put("model", channel.model)
            .put("messages", messages)
            .put("stream", false)
            .put("options", optionsObj)

        val url = channel.baseUrl.trimEnd('/') + "/api/chat"
        val request = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${channel.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(MEDIA_JSON))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Result.Failure(friendlyError(response.code, body))
            } else {
                businessError(body)?.let { return Result.Failure(it) }
                val json = JSONObject(body)
                val text = normalizeReply(json.optJSONObject("message")?.optString("content").orEmpty())
                if (text.isBlank()) Result.Failure("接口未返回有效内容") else Result.Success(text)
            }
        } catch (e: IOException) {
            Result.Failure("网络错误：${e.message ?: "无法连接"}")
        } catch (e: Exception) {
            Result.Failure("请求失败：${e.message ?: "未知错误"}")
        }
    }

    /** 拉取网关可用模型列表。OpenAI：GET /models；Ollama：GET /api/tags。 */
    suspend fun fetchModels(): ModelsResult = withContext(Dispatchers.IO) {
        if (channel.apiKey.isBlank()) return@withContext ModelsResult.Failure("未填写 API Key")
        val (url, tag) = if (isOllama) channel.baseUrl.trimEnd('/') to "/api/tags"
        else channel.baseUrl.trimEnd('/') to "/models"
        val request = Request.Builder().url(url + tag)
            .addHeader("Authorization", "Bearer ${channel.apiKey}")
            .get().build()
        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext ModelsResult.Failure(friendlyError(response.code, body))
            }
            businessError(body)?.let { return@withContext ModelsResult.Failure(it) }
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
            val list = (0 until arr.length()).mapNotNull { index ->
                val obj = arr.optJSONObject(index) ?: return@mapNotNull null
                obj.optString("id").ifBlank { obj.optJSONObject("model")?.optString("name").orEmpty() }
                    .takeIf { it.isNotBlank() }
            }
            if (list.isEmpty()) ModelsResult.Failure("接口未返回模型列表")
            else ModelsResult.Success(list.distinct())
        } catch (e: IOException) {
            ModelsResult.Failure("网络错误：${e.message ?: "无法连接"}")
        } catch (e: Exception) {
            ModelsResult.Failure("请求失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 查询账户余额 / 额度。仅对接提供公开 Bearer 接口的官方平台：
     *  - DeepSeek：GET /user/balance（balance_infos[].total_balance / currency）；
     *  - OpenRouter：GET /api/v1/key（usage / usage_daily / limit / limit_remaining / 免费档）。
     * 其余平台（Groq / 智谱 / 通义 / 硅基流动 / GitHub Models / Cloudflare）无公开余额接口，
     * 返回 [BalanceResult.Unsupported]，引导去官网控制台查看。
     */
    suspend fun fetchBalance(): BalanceResult = withContext(Dispatchers.IO) {
        if (channel.apiKey.isBlank()) return@withContext BalanceResult.Failure("未填写 API Key")
        when {
            channel.baseUrl.contains("deepseek.com") -> fetchDeepSeekBalance()
            channel.baseUrl.contains("openrouter.ai") -> fetchOpenRouterBalance()
            else -> BalanceResult.Unsupported("该平台未提供公开余额查询接口，请在官网控制台查看")
        }
    }

    private fun fetchDeepSeekBalance(): BalanceResult = try {
        val url = "https://api.deepseek.com/user/balance"
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${channel.apiKey}").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return BalanceResult.Failure(friendlyError(resp.code, body))
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return BalanceResult.Failure("余额返回无法解析")
            val infos = json.optJSONArray("balance_infos")
            if (infos == null || infos.length() == 0) {
                return BalanceResult.Failure("未返回余额信息")
            }
            val parts = (0 until infos.length()).mapNotNull { i ->
                val o = infos.optJSONObject(i) ?: return@mapNotNull null
                val currency = o.optString("currency", "")
                val total = o.optString("total_balance", "")
                val granted = o.optString("granted_balance", "")
                if (total.isBlank()) null else {
                    val extra = granted.takeIf { it.isNotBlank() }?.let { "（赠金 $it）" }.orEmpty()
                    "$currency $total$extra"
                }
            }
            if (parts.isEmpty()) BalanceResult.Failure("未返回余额信息")
            else BalanceResult.Success(parts.joinToString("；"))
        }
    } catch (e: IOException) {
        BalanceResult.Failure("网络错误：${e.message ?: "无法连接"}")
    } catch (e: Exception) {
        BalanceResult.Failure("查询失败：${e.message ?: "未知错误"}")
    }

    private fun fetchOpenRouterBalance(): BalanceResult = try {
        val req = Request.Builder().url("https://openrouter.ai/api/v1/key")
            .addHeader("Authorization", "Bearer ${channel.apiKey}").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return BalanceResult.Failure(friendlyError(resp.code, body))
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return BalanceResult.Failure("额度返回无法解析")
            parseOpenRouterQuota(json.toString())?.let { BalanceResult.Success(it) }
                ?: BalanceResult.Failure("未返回额度信息")
        }
    } catch (e: IOException) {
        BalanceResult.Failure("网络错误：${e.message ?: "无法连接"}")
    } catch (e: Exception) {
        BalanceResult.Failure("查询失败：${e.message ?: "未知错误"}")
    }

    private fun errorSummary(body: String): String = businessError(body) ?: body.take(120)

    companion object {
        private val MEDIA_JSON = "application/json; charset=utf-8".toMediaType()
        const val MAX_REPLY_CHARS = 4000
        const val MAX_CONTENT_CHARS = 8000

        /**
         * 识别「HTTP 200 但业务失败」的网关返回：兼容 OpenAI `error.message`
         * 与智谱等后端 `success:false` + `msg` / `code`。无业务错误时返回 null。
         */
        fun businessError(body: String): String? {
            if (body.isBlank()) return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.let { return it }
            if (json.optBoolean("success", true) == false) {
                val msg = json.optString("msg").ifBlank { json.optString("message") }
                val code = json.optString("code")
                if (msg.isNotBlank()) return if (code.isNotBlank()) "错误 $code：$msg" else msg
            }
            return null
        }

        /**
         * 解析 OpenRouter `/api/v1/key` 返回的额度文案（纯逻辑，便于测试）。
         * 覆盖累计/今日用量、剩余额度、额度上限、免费模型每日次数与免费档标注；无可用字段返回 null。
         */
        fun parseOpenRouterQuota(body: String): String? {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val data = json.optJSONObject("data") ?: json
            val usage = data.optDouble("usage", Double.NaN)
            val usageDaily = data.optDouble("usage_daily", Double.NaN)
            val limit = data.optDouble("limit", Double.NaN)
            val limitRemaining = data.optDouble("limit_remaining", Double.NaN)
            val freeTier = data.optBoolean("is_free_tier", false)
            val freeDaily = data.optJSONObject("free_model_daily_requests")
                ?.optInt("remaining", -1).takeIf { it != -1 }
            val sb = StringBuilder()
            if (!usage.isNaN()) sb.append("累计已用 $usage")
            if (!usageDaily.isNaN()) {
                if (sb.isNotEmpty()) sb.append("，")
                sb.append("今日已用 $usageDaily")
            }
            if (!limitRemaining.isNaN()) {
                if (sb.isNotEmpty()) sb.append("，")
                sb.append("剩余额度 $limitRemaining")
            } else if (!limit.isNaN()) {
                if (sb.isNotEmpty()) sb.append("，")
                sb.append("额度上限 $limit")
            }
            freeDaily?.let {
                if (sb.isNotEmpty()) sb.append("，")
                sb.append("免费模型今日剩 $it 次")
            }
            if (freeTier && sb.isNotEmpty()) sb.append("（免费档）")
            return sb.toString().takeIf { it.isNotEmpty() }
        }

        /** 归一化网关地址（纯逻辑，便于测试）。 */
        fun normalizeBase(rawBase: String, isOllama: Boolean): String {
            var base = rawBase.trim().trimEnd('/')
            if (!isOllama && !base.endsWith("/v1") &&
                !base.contains("/chat/completions") && !base.contains("/chat") &&
                !base.endsWith("/responses") && !base.contains("/responses")) {
                base = "$base/v1"
            }
            return base
        }

        /** 解析 OpenAI 兼容返回：普通 JSON 或 `data: {...}` SSE 流式块。 */
        fun parseOpenAiText(body: String): String {
            val plain = runCatching { JSONObject(body) }.getOrNull()
            if (plain != null) {
                plain.optJSONArray("choices")?.optJSONObject(0)?.let { choice ->
                    choice.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                        ?.let { return it }
                    choice.optString("text").takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            val sb = StringBuilder()
            body.lines().forEach { line ->
                val data = line.trim().removePrefix("data:").trim()
                if (data.isBlank() || data == "[DONE]") return@forEach
                runCatching {
                    val obj = JSONObject(data)
                    obj.optJSONArray("choices")?.optJSONObject(0)?.let { choice ->
                        choice.optJSONObject("delta")?.optString("content")
                            ?.let { sb.append(it) }
                        choice.optJSONObject("message")?.optString("content")
                            ?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
            return sb.toString()
        }

        /** 消息内容清洗：去控制字符、限长。 */
        fun sanitizeContent(raw: String): String =
            raw.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
                .trim().take(MAX_CONTENT_CHARS)

        /** 回复规范化：折叠空白、超长截断。 */
        fun normalizeReply(raw: String): String {
            val collapsed = raw.replace("\r\n", "\n").replace("\r", "\n")
                .replace(Regex("[ \\t]+"), " ").trim()
            if (collapsed.length <= MAX_REPLY_CHARS) return collapsed
            return collapsed.take(MAX_REPLY_CHARS).trimEnd() + "…（已截断）"
        }
    }
}
