package com.ferry.pet.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 轻量热更新（对齐上游 dsh-pet 的静态清单 + 发布附件模式）：
 *
 * 1. 清单（JSON）可由多个地址轮询回退：jsDelivr 多 CDN 与自建静态源依次尝试，
 *    任一成功即采用；
 * 2. APK 下载地址在清单里可为多条线路（`apkUrl` 旧字段 / `apkUrls` 数组），
 *    下载前逐跳校验为 HTTPS 且主机在可信白名单内；
 * 3. 清单可带 `sha256` 与 `size`，下载后做摘要/大小校验，避免把不可信文件
 *    交给安装器；未填摘要则跳过该环节。
 *
 * JSON 约定：
 * {
 *   "versionCode": 7,
 *   "versionName": "1.6.0",
 *   "apkUrl": "https://github.com/OWNER/REPO/releases/download/v1.6.0/app-release.apk",
 *   "apkUrls": ["https://...mirror.../app-release.apk"],
 *   "sha256": "可选，小写十六进制",
 *   "size": 25174993,
 *   "changelog": "更新说明…"
 * }
 */
object UpdateChecker {

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val apkUrls: List<String>,
        val sha256: String?,
        val size: Long?,
        val changelog: String,
    ) {
        /** 主下载线路，兼容旧单字段调用。 */
        val apkUrl: String get() = apkUrls.firstOrNull().orEmpty()
    }

    sealed class Result {
        data class UpToDate(val current: Int) : Result()
        data class Newer(val release: Release, val current: Int) : Result()
        data class Failure(val message: String) : Result()
    }

    /** 可信下载主机：GitHub Releases 链路、jsDelivr CDN 与常见加速镜像。 */
    private val trustedHosts = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "cdn.jsdelivr.net",
        "fastly.jsdelivr.net",
        "gcore.jsdelivr.net",
        "gh-proxy.com",
        "mirror.ghproxy.com",
        "ghfast.top",
        "ghproxy.net",
        "gh.llkk.cc",
        "kkgithub.com",
        "hub.gitmirror.com",
    )

    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 以逗号/分号分隔的多个清单地址；空则跳过。单个失败继续尝试下一个。 */
    suspend fun check(endpoint: String, currentCode: Int): Result = withContext(Dispatchers.IO) {
        val urls = endpoint.trim()
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (urls.isEmpty()) return@withContext Result.Failure("未配置更新检查地址")

        var lastError = "清单获取失败"
        loop@ for (manifestUrl in urls) {
            val fetched = runCatching { fetchManifest(manifestUrl) }.getOrNull()
            if (fetched == null) { lastError = "清单返回无效内容：$manifestUrl"; continue@loop }
            val json = fetched
            val release = Release(
                versionCode = json.optInt("versionCode", currentCode),
                versionName = json.optString("versionName", ""),
                apkUrls = extractApkUrls(json),
                sha256 = json.optString("sha256", "").lowercase().takeIf { it.isNotBlank() },
                size = json.optLong("size", -1L).takeIf { it > 0 },
                changelog = json.optString("changelog", ""),
            )
            when {
                release.apkUrls.isEmpty() -> {
                    lastError = "更新清单缺少下载地址"; continue@loop
                }
                !release.apkUrls.all { isTrustedHttps(it) } -> {
                    lastError = "下载地址不在可信源（需 https 且主机在白名单）"; continue@loop
                }
                release.versionCode > currentCode -> return@withContext Result.Newer(release, currentCode)
                else -> return@withContext Result.UpToDate(currentCode)
            }
        }
        Result.Failure(lastError)
    }

    private fun extractApkUrls(json: JSONObject): List<String> {
        val list = LinkedHashSet<String>()
        json.optString("apkUrl", "").takeIf { it.isNotBlank() }?.let(list::add)
        json.optJSONArray("apkUrls")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i, "").trim().takeIf { it.isNotBlank() }?.let(list::add)
            }
        }
        return list.map { it.trim() }.toList()
    }

    private fun fetchManifest(url: String): JSONObject {
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        val body = response.body?.use { it.bytes() } ?: ByteArray(0)
        if (!response.isSuccessful) throw IllegalStateException("返回 ${response.code}")
        if (body.size > MAX_MANIFEST_BYTES) throw IllegalStateException("清单过大")
        return JSONObject(String(body, Charsets.UTF_8))
    }

    /**
     * HTTPS + 主机白名单校验；重定向由 OkHttp 跟随，目标同样需过白名单。
     * 兼容 `镜像前缀/https://github.com/...` 形式：取末尾真实地址再校验主机。
     */
    fun isTrustedHttps(url: String): Boolean {
        val target = unwrapMirror(url)
        return runCatching {
            val parsed = URL(target)
            val host = parsed.host.lowercase().trimEnd('.')
            parsed.protocol.equals("https", ignoreCase = true) && host.isNotEmpty() &&
                (host in trustedHosts || trustedHosts.any { host.endsWith(".$it") })
        }.getOrDefault(false)
    }

    /** 若为 `镜像域名/https://真实地址` 形式，抽出末尾真实地址；否则原样返回。 */
    private fun unwrapMirror(url: String): String {
        val idx = url.indexOf("https://", 8)
        return if (idx > 0) url.substring(idx) else url
    }
}
