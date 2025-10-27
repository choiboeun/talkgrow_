package com.talkgrow_.util

/**
 * Analyzer에서 pose/hand 결과가 따로/비동기로 들어올 수 있으니
 * 가장 최근 값을 모아서 한 번에 콜백으로 넘겨주는 단순 집계기.
 *
 * CameraActivity에서 쓰는 시그니처와 동일:
 *   val aggr = ResultAggregator { pose, left, right, ts, mirrored -> ... }
 *   aggr.onPose(...); aggr.onHands(...); aggr.mirroredPreview = true/false
 */
class ResultAggregator(
    private val onPacket: (
        pose: List<KP>?, left: List<KP>?, right: List<KP>?, tsMs: Long, mirroredPreview: Boolean
    ) -> Unit
) {

    @Volatile var mirroredPreview: Boolean = true

    private var lastPose: Pair<List<KP>?, Long>? = null
    private var lastHands: Triple<List<KP>?, List<KP>?, Long>? = null

    fun reset() {
        lastPose = null
        lastHands = null
    }

    fun onPose(pose: List<KP>?, tsMs: Long) {
        lastPose = pose to tsMs
        emit()
    }

    fun onHands(left: List<KP>?, right: List<KP>?, tsMs: Long) {
        lastHands = Triple(left, right, tsMs)
        emit()
    }

    private fun emit() {
        val (pose, pts) = lastPose ?: (null to 0L)
        val (left, right, hts) = lastHands ?: Triple(null, null, 0L)
        val tsRaw = maxOf(pts, hts)
        if (tsRaw == 0L) return

        // TS 수선: 완전히 이상한 TS면 현재 시각으로 보정
        val ts = sanitizeTs(tsRaw)
        onPacket(pose, left, right, ts, mirroredPreview)
    }

    private fun sanitizeTs(ts: Long): Long {
        val now = System.currentTimeMillis()
        if (ts <= 946_684_800_000L || ts >= 4_102_444_800_000L || ts > now + 10_000L) {
            return now
        }
        return ts
    }
}
