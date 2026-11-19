package com.ferry.pet.account

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * OpenRouter 公开 OAuth PKCE 辅助（官方文档：openrouter.ai/docs/guides/overview/auth/oauth）。
 *
 * 流程：本地生成 code_verifier → S256 派生 code_challenge → WebView 打开授权页 →
 * 回调 URL 带 code → 用 verifier 调 /api/v1/auth/keys 换用户可控的 API Key。
 * 该流程不读取平台 cookie、不调用内部控制台建 Key 接口。
 */
object Pkce {

    private const val AUTH_BASE = "https://openrouter.ai"
    private const val KEYS_PATH = "/api/v1/auth/keys"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 生成 43~128 位的 URL-safe code verifier。 */
    fun newVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** S256 challenge：base64url(sha256(verifier))。 */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** 构造授权页地址；callbackUrl 指向本机回跳 scheme，由 WebView 拦截。 */
    fun authorizeUrl(verifier: String, callbackUrl: String): String {
        val ch = challenge(verifier)
        return "$AUTH_BASE/auth?callback_url=${encode(callbackUrl)}" +
            "&code_challenge=${encode(ch)}&code_challenge_method=S256"
    }

    /** 从回调 URL 中取出授权 code；无则返回 null。 */
    fun codeFromCallback(url: String): String? {
        return runCatching {
            val q = url.substringAfter('?')
            q.split('&').mapNotNull { pair ->
                val (k, v) = pair.split('=', limit = 2)
                if (k == "code") v else null
            }.firstOrNull()
        }.getOrNull()?.ifBlank { null }
    }

    sealed class ExchangeResult {
        data class Success(val apiKey: String) : ExchangeResult()
        data class Failure(val message: String) : ExchangeResult()
    }

    /** 用授权 code + verifier 换取 API Key（公开接口）。 */
    suspend fun exchange(code: String, verifier: String): ExchangeResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("code", code)
                .put("code_verifier", verifier)
                .put("code_challenge_method", "S256")
                .toString()
            val req = Request.Builder()
                .url("$AUTH_BASE$KEYS_PATH")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                val key = json?.optString("key").orEmpty()
                when {
                    resp.isSuccessful && key.isNotBlank() -> ExchangeResult.Success(key)
                    else -> ExchangeResult.Failure(
                        json?.optString("message").orEmpty().ifBlank {
                            "换 Key 失败（HTTP ${resp.code}）"
                        })
                }
            }
        }.getOrElse { ExchangeResult.Failure(it.message ?: "网络错误") }
    }

    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
