package com.ferry.pet.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 下载最新 APK 并调起系统安装器。
 *
 * - 下载地址支持多条线路，依次回退；每条线路下载前复核 HTTPS 可信白名单；
 * - 清单提供 sha256 / size 时，落盘后做摘要与大小校验，不符即丢弃并换下一条；
 * - APK 落在应用私有缓存目录，安装通过 FileProvider 交给系统包安装器。
 */
object Updater {

    sealed class Download {
        data class Progress(val percent: Int) : Download()
        data class Done(val file: File) : Download()
        data class Failure(val message: String) : Download()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun download(
        context: Context,
        urls: List<String>,
        sha256: String? = null,
        expectedSize: Long? = null,
        onProgress: (Int) -> Unit,
    ): Download = withContext(Dispatchers.IO) {
        val candidates = urls.filter { it.isNotBlank() }
        if (candidates.isEmpty()) return@withContext Download.Failure("缺少下载地址")

        var lastError = "下载失败"
        for (url in candidates) {
            if (!UpdateChecker.isTrustedHttps(url)) {
                lastError = "下载地址不在可信源：$url"; continue
            }
            when (val result = downloadOne(context, url, sha256, expectedSize, onProgress)) {
                is Download.Done -> return@withContext result
                is Download.Failure -> lastError = result.message
                else -> {}
            }
        }
        Download.Failure(lastError)
    }

    /** 兼容旧单地址调用。 */
    suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit): Download =
        download(context, listOf(url), null, null, onProgress)

    private fun downloadOne(
        context: Context,
        url: String,
        sha256: String?,
        expectedSize: Long?,
        onProgress: (Int) -> Unit,
    ): Download {
        return try {
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!response.isSuccessful) return Download.Failure("下载返回 ${response.code}")
            val body = response.body ?: return Download.Failure("下载内容为空")
            val total = body.contentLength().takeIf { it > 0 } ?: -1
            val file = File(context.cacheDir, "update-${System.currentTimeMillis()}.apk")
            val digest = sha256?.let { MessageDigest.getInstance("SHA-256") }
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var bytes = 0L
                    while (input.read(buf).also { read = it } >= 0) {
                        output.write(buf, 0, read)
                        bytes += read
                        digest?.update(buf, 0, read)
                        if (total > 0) onProgress((bytes * 100 / total).toInt())
                    }
                }
            }
            if (file.length() <= 0) return Download.Failure("下载文件为空")
            expectedSize?.let {
                if (file.length() != it) {
                    file.delete(); return Download.Failure("文件大小不符（${file.length()}≠$it），换下一条线路")
                }
            }
            sha256?.let { expected ->
                val actual = digest?.digest()?.joinToString("") { b ->
                    "%02x".format(b)
                }.orEmpty()
                if (!actual.equals(expected, ignoreCase = true)) {
                    file.delete(); return Download.Failure("摘要校验未通过，换下一条线路")
                }
            }
            Download.Done(file)
        } catch (e: Exception) {
            Download.Failure("下载失败：${e.message ?: "网络错误"}")
        }
    }

    /** 调起系统安装器安装已下载的 APK。 */
    fun install(context: Context, file: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
