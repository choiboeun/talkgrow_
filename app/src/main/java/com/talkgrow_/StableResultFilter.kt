package com.talkgrow_

/**
 * label/prob가 프레임별로 튈 때 UI 깜빡임을 막는다.
 * - confThreshold 이상 + minStableFrames 연속이면 새 문장 커밋
 * - 커밋 후 holdMs 동안은 같은 문장 유지
 */
class StableResultFilter(
    private val confThreshold: Float = 0.55f,
    private val minStableFrames: Int = 3,
    private val holdMs: Long = 1200L
) {
    private var lastLabel: String? = null
    private var streak = 0

    private var lastCommitTime = 0L
    private var committedSentence: String? = null

    fun feed(label: String, prob: Float, candidateSentence: String?): String? {
        val now = System.currentTimeMillis()

        // hold 중이면 유지
        if (committedSentence != null && now - lastCommitTime < holdMs) {
            return committedSentence
        }

        if (prob >= confThreshold) {
            if (label == lastLabel) streak++ else { lastLabel = label; streak = 1 }
        } else {
            streak = 0
            lastLabel = null
            return committedSentence
        }

        if (streak >= minStableFrames && !candidateSentence.isNullOrBlank()) {
            committedSentence = candidateSentence
            lastCommitTime = now
            return committedSentence
        }
        return committedSentence
    }
}
