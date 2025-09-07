package com.talkgrow_.util

import kotlin.math.max

class PredictionSmoother(
    private val topKWindow: Int = 7,           // 최근 N 프레임 다수결
    private val emaAlpha: Float = 0.2f,        // EMA 가중치(0~1)
    private val minProbToConsider: Float = 0.35f, // 이 확률 미만은 “무시”
    private val minStableMs: Long = 450L       // 같은 라벨이 이 시간 이상 유지돼야 출력
) {
    private data class Item(val t: Long, val idx: Int, val prob: Float)

    private val ring = ArrayDeque<Item>()
    private var emaIdx: Int = -1
    private var emaProb: Float = 0f
    private var lastShownIdx: Int = -1
    private var candidateIdx: Int = -1
    private var candidateSince: Long = 0L

    fun reset() {
        ring.clear()
        emaIdx = -1
        emaProb = 0f
        candidateIdx = -1
        candidateSince = 0L
    }

    /** 프레임 하나 입력 → 현재 “표시해도 되는 결과” 있으면 Pair(idx,prob) 반환, 없으면 null */
    fun update(nowMs: Long, idx: Int, prob: Float): Pair<Int, Float>? {
        // 낮은 확률은 버린다(노이즈 컷)
        if (prob < minProbToConsider) {
            ring.addLast(Item(nowMs, -1, 0f))
            if (ring.size > topKWindow) ring.removeFirst()
            // 후보 취소(손 내려가거나 흔들릴 때)
            candidateIdx = -1
            candidateSince = 0L
            return null
        }

        ring.addLast(Item(nowMs, idx, prob))
        if (ring.size > topKWindow) ring.removeFirst()

        // 1) 다수결
        val counts = mutableMapOf<Int, Int>()
        for (it in ring) if (it.idx >= 0) counts[it.idx] = (counts[it.idx] ?: 0) + 1
        val votedIdx = counts.maxByOrNull { it.value }?.key ?: -1

        // 2) EMA로 확률 안정화
        if (emaIdx == -1 || votedIdx != emaIdx) {
            // 다른 라벨로 바뀌면 EMA를 빠르게 해당 라벨로 스냅
            emaIdx = votedIdx
            emaProb = prob
        } else {
            emaProb = (1f - emaAlpha) * emaProb + emaAlpha * prob
        }

        // 3) 히스테리시스(같은 라벨이 minStableMs 유지될 때만 화면 갱신)
        if (votedIdx != candidateIdx) {
            candidateIdx = votedIdx
            candidateSince = nowMs
        }

        val stable = (nowMs - candidateSince) >= minStableMs
        return if (stable && candidateIdx >= 0 && candidateIdx != lastShownIdx) {
            lastShownIdx = candidateIdx
            Pair(candidateIdx, emaProb)
        } else null
    }
}
