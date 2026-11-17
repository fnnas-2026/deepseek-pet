package com.ferry.pet.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 系统自带 TTS 朗读器。懒加载初始化，支持开关、语速与释放；
 * 不接第三方 TTS 服务。
 */
class Speaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    var enabled = true
    var rate = 1.0f

    private fun ensure(): TextToSpeech? {
        if (tts != null) return tts
        synchronized(this) {
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    if (ready) tts?.language = Locale.CHINESE
                }
            }
        }
        return tts
    }

    fun speak(text: String) {
        if (!enabled || text.isBlank()) return
        val engine = ensure() ?: return
        if (!ready) return
        engine.setSpeechRate(rate)
        engine.language = Locale.CHINESE
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pet-reply")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
