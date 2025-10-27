package com.talkgrow_

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SentenceTTS(ctx: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech = TextToSpeech(ctx, this)
    private var ready = false
    override fun onInit(status: Int) {
        ready = (status == TextToSpeech.SUCCESS)
        if (ready) runCatching { tts.language = Locale.KOREAN }
    }
    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sign-tts")
    }
    fun shutdown() = runCatching { tts.stop(); tts.shutdown() }
}

class SentenceEngine(private val tts: SentenceTTS) {
    /** 데모 ON: 간단 키워드 → 자연 문장 */
    fun demoSentence(hints: Map<String, Float>): String {
        val len = hints["len"] ?: 0f
        // 길면 목적지류 우선, 짧으면 인사류도 허용
        val candidates = if (len > 45f) listOf(
            "버스" to "가까운 버스로 안내할게요.",
            "곳"   to "원하는 곳으로 가요.",
            "가깝다" to "가까운 길로 갈게요."
        ) else listOf(
            "안녕하세요" to "안녕하세요, 무엇을 도와드릴까요?",
            "감사합니다" to "감사합니다. 도움이 되었다니 기뻐요.",
            "반갑다" to "만나서 반갑습니다."
        )
        return candidates.random().second
    }
}
