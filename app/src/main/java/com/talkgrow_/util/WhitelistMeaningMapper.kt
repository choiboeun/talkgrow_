package com.talkgrow_.util

class WhitelistMeaningMapper(
    labels: List<String>,
    private val topKMinProb: Float = 0.45f,
    private val holdMs: Long = 500L,
    private val allowSet: Set<String> = setOf("버스", "곳", "가깝다"),
) {
    data class TopPair(val label: String, val prob: Float)
    data class MapResult(val finalLabel: String?)

    private var lastEmitAt: Long = 0L
    private var pending: String? = null
    private var lastSeenLabel: String? = null
    private var lastSeenAt: Long = 0L

    private val known: Set<String> = labels.toSet()

    fun reset() {
        lastEmitAt = 0L
        pending = null
        lastSeenLabel = null
        lastSeenAt = 0L
    }

    /**
     * stable: SltEngineRunner에서 확정된 라벨 (있을 수도, 없을 수도)
     * topK:   (label, prob) 리스트
     * nowMs:  현재 ms
     */
    fun map(stable: String?, topK: List<TopPair>, nowMs: Long): MapResult {
        // 1) stable이 화이트리스트에 있으면 바로 후보로
        val candidateFromStable = stable?.takeIf { it in allowSet && it in known }

        // 2) 없으면 topK에서 화이트리스트 + 확률 조건 만족하는 첫 항목
        val candidateFromTop = candidateFromStable ?: topK.firstOrNull {
            it.prob >= topKMinProb && it.label in allowSet && it.label in known
        }?.label

        val cand = candidateFromTop
        if (cand == null) {
            // 후보가 없으면 펜딩만 유지. 오래되면 취소.
            if (pending != null && nowMs - lastSeenAt > holdMs) {
                pending = null
            }
            return MapResult(null)
        }

        // 동일 후보가 holdMs 동안 유지되면 확정
        if (cand == lastSeenLabel) {
            if (nowMs - lastSeenAt >= holdMs) {
                pending = null
                lastEmitAt = nowMs
                return MapResult(cand)
            }
        } else {
            // 후보가 바뀌었으면 시계 리셋
            lastSeenLabel = cand
            lastSeenAt = nowMs
            pending = cand
        }

        return MapResult(null)
    }
}
