package com.talkgrow_.nlp

/**
 * 앱 사용 패턴에 맞춘 문장 조립기.
 * - joiner/terminal 지정 가능 (예: " ", ".")
 * - offer(label) 로 토큰을 쌓고, flush() 로 한 문장 커밋
 * - size()/clear() 지원
 *
 * 필요 시 규칙 기반 정렬을 추가하려면 reorderTokens() 내부를 확장하면 된다.
 */
class SentenceAssembler(
    private val joiner: String = " ",
    private val terminal: String = ".",
    private val maxBuffer: Int = 12
) {
    private val buf = ArrayDeque<String>()

    /** 토큰 추가 (현재 시각 불필요) */
    fun offer(label: String) {
        if (buf.size >= maxBuffer) buf.removeFirst()
        buf.addLast(label)
    }

    /** 버퍼 비우기 */
    fun clear() {
        buf.clear()
    }

    /** 현재 버퍼 길이 */
    fun size(): Int = buf.size

    /**
     * 문장으로 커밋. 비어 있으면 null.
     * 커밋 후 내부 버퍼는 비워진다.
     */
    fun flush(): String? {
        if (buf.isEmpty()) return null
        val tokens = buf.toList()
        buf.clear()
        val ordered = reorderTokens(tokens)
        val body = ordered.joinToString(joiner).trim()
        if (body.isEmpty()) return null
        return if (terminal.isEmpty()) body else body + terminal
    }

    /** 간단한 순서 보정 훅(필요 시 확장) */
    private fun reorderTokens(tokens: List<String>): List<String> {
        // 현재는 그대로 사용. 필요하면 품사/룰 기반 정렬 추가.
        return tokens
    }
}
