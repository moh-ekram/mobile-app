package com.example.ui.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = try {
        TextToSpeech(context.applicationContext, this)
    } catch (_: Exception) {
        null
    }
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(0.85f)
            isInitialized = true
        }
    }

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "memorizer_tts_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
    }
}
