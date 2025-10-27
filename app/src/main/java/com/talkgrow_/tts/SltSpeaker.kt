package com.talkgrow_.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SltSpeaker(ctx: Context) : TextToSpeech.OnInitListener {

    private val TAG = "SltSpeaker"
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private val ready = AtomicBoolean(false)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.KOREA)
            ready.set(r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED)
            Log.i(TAG, "TTS ready=$ready, engine=${tts.defaultEngine}")
        } else {
            Log.e(TAG, "TTS init failed: $status")
        }
    }

    fun speak(text: String) {
        if (!ready.get()) return
        tts.stop()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
}
