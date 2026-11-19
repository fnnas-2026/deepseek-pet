package com.ferry.pet.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * 系统语音识别输入封装：复用设备自带 SpeechRecognizer，不接第三方语音服务。
 *
 * 用法：构造后调用 [start]，识别结果通过 [onResult] 回填；[onError] 给出可读错误。
 * 离开页面时调用 [destroy] 释放识别器。
 */
class VoiceInput(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    companion object {
        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        /** 设备是否支持语音识别。 */
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun start() {
        if (!hasPermission(context)) {
            onError("缺少录音权限，请在系统设置中允许麦克风")
            return
        }
        if (!isAvailable(context)) {
            onError("当前设备不支持语音识别")
            return
        }
        destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onResult(text) else onError("未识别到语音")
            }

            override fun onError(error: Int) = onError(messageFor(error))
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        sr.startListening(intent)
    }

    fun destroy() {
        recognizer?.runCatching { cancel(); destroy() }
        recognizer = null
    }

    private fun messageFor(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "录音出错，请重试"
        SpeechRecognizer.ERROR_NETWORK -> "语音识别网络异常"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请再说一次"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "超时未检测到语音"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
        else -> "语音识别失败（$code）"
    }
}
