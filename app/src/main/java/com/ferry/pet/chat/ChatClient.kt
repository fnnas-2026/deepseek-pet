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

    suspend fun send(history: List<ChatMessage>): Result = withContext(Dispatchers.IO) {
        if (channel.apiKey.isBlank()) return@withContext Result.Failure("请先在设置中填写 API Key")
        if (channel.model.isBlank()) return@withContext Result.Failure("请先填写或拉取模型名")
        if (isOllama) sendOllama(history) else sendOpenAi(history)
    }

    /** OpenAI 兼容：POST {baseUrl}/chat/completions */
    private fun sendOpenAi(history: List<ChatMessage>): Result {
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        history.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }

        val payload = JSONObject()
            .put("model", channel.model)
            .put("messages", messages)
            .put("temperature", temperature)

        val url = channel.baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${channel.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(MEDIA_JSON))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Result.Failure("接口返回 ${response.code}：${errorSummary(body)}")
            } else {
                val json = JSONObject(body)
                val text = json.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty()
                    .ifBlank { json.optJSONArray("choices")?.optJSONObject(0)?.optString("text").orEmpty() }
                if (text.isBlank()) Result.Failure("接口未返回有效内容") else Result.Success(text.trim())
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
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        history.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }

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
                Result.Failure("接口返回 ${response.code}：${errorSummary(body)}")
            } else {
                val json = JSONObject(body)
                val text = json.optJSONObject("message")?.optString("content").orEmpty()
                if (text.isBlank()) Result.Failure("接口未返回有效内容") else Result.Success(text.trim())
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
                return@withContext ModelsResult.Failure("接口返回 ${response.code}：${errorSummary(body)}")
            }
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

    private fun errorSummary(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message") ?: body.take(120)
        }.getOrDefault(body.take(120))
    }

    companion object {
        private val MEDIA_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
