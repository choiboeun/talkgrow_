package com.talkgrow_.util

/**
 * 한 세션 동안 수집한 프레임을 91개 고정 윈도우로 정리한다.
 * - 91개 미만: 뒤쪽 0패딩
 * - 91개 초과: 최근 91개만 사용
 */
class FrameWindow(
    private val targetLen: Int = 91,
    private val featureDim: Int = 134
) {
    private val frames = ArrayList<FloatArray>(targetLen)

    fun clear() = frames.clear()

    fun size(): Int = frames.size

    fun add(frameFeature: FloatArray) {
        // safety: featureDim 맞추기
        val f = if (frameFeature.size == featureDim) frameFeature
        else FloatArray(featureDim).apply {
            val copy = minOf(featureDim, frameFeature.size)
            System.arraycopy(frameFeature, 0, this, 0, copy)
        }
        frames.add(f)
        // 너무 길면 앞을 버리고 최근만 유지
        if (frames.size > targetLen) {
            val overflow = frames.size - targetLen
            repeat(overflow) { frames.removeAt(0) }
        }
    }

    /**
     * 모델 입력용 (targetLen x featureDim)
     * targetLen 미만이면 뒤쪽을 0으로 채운다.
     */
    fun toFixedWindow(): Array<FloatArray> {
        val out = Array(targetLen) { FloatArray(featureDim) { 0f } }
        val start = 0
        for (i in frames.indices) {
            out[start + i] = frames[i]
        }
        return out
    }
}
