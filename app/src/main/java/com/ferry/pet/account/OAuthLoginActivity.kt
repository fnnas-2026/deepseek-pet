package com.ferry.pet.account

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ferry.pet.R

/**
 * 第三方模型平台网页授权登录宿主。
 *
 * 仅承载官方公开 OAuth 流程：WebView 打开授权页，拦截本机回跳 scheme 里的 code，
 * 回调给上一个 Activity；不读取页面 cookie / localStorage，不调用内部控制台接口。
 *
 * 入参：
 *  - EXTRA_VERIFIER: PKCE verifier（由发起方生成并保留）
 *  - EXTRA_CALLBACK: 本机回跳 URL scheme
 * 回跳后通过 [EXTRA_RESULT_KEY] 把换取到的 API Key 回填给发起方。
 */
class OAuthLoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VERIFIER = "extra_verifier"
        const val EXTRA_CALLBACK = "extra_callback"
        const val EXTRA_RESULT_KEY = "extra_result_key"
        const val RESULT_OK = 1
        const val RESULT_CANCEL = 0
    }

    private var verifier: String = ""
    private var callback: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        verifier = intent.getStringExtra(EXTRA_VERIFIER).orEmpty()
        callback = intent.getStringExtra(EXTRA_CALLBACK).orEmpty()
        val url = Pkce.authorizeUrl(verifier, callback)

        setContent {
            var busy by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf("") }
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(getString(R.string.oauth_title), style = MaterialTheme.typography.titleMedium)
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    } else if (busy) {
                        CircularProgressIndicator(Modifier.padding(16.dp))
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                // 仅承载官方授权页：禁用文件域与跨域文件访问，避免本地资源被带入。
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.allowUniversalAccessFromFileURLs = false
                                settings.allowFileAccessFromFileURLs = false
                                // 不持久化密码 / 表单数据，不保留站点数据库。
                                settings.savePassword = false
                                settings.saveFormData = false
                                settings.domStorageEnabled = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView, request: android.webkit.WebResourceRequest
                                    ): Boolean {
                                        val target = request.url.toString()
                                        val code = Pkce.codeFromCallback(target)
                                        if (target.startsWith(callback) && code != null) {
                                            finishWithCode(code)
                                            return true
                                        }
                                        return false
                                    }
                                }
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun finishWithCode(code: String) {
        setResult(RESULT_OK, android.content.Intent().putExtra("code", code))
        finish()
    }
}
