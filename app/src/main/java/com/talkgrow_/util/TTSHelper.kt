// com.talkgrow_.util.TTSHelper.kt

package com.talkgrow_.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSHelper(context: Context) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTSHelper", "Korean language not supported")
            } else {
                isInitialized = true
                Log.i("TTSHelper", "Initialization success.")
            }
        } else {
            Log.e("TTSHelper", "Initialization failed.")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            // 재생 중인 음성을 멈추고 새 음성 재생
            tts.stop()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID_${System.currentTimeMillis()}")
        }
    }

    /**
     * 현재 재생 중인 TTS 음성을 즉시 중단합니다.
     */
    fun stop() {
        if (isInitialized && tts.isSpeaking) {
            tts.stop()
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}