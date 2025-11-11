package com.talkgrow_.camera

/**
 * MediaPipe Pose(25점) + Left(21) + Right(21) → 134 float
 * 이미 0..1 정규화된 landmarks를 넘겨받는다고 가정 (x,y only).
 */
object KeypointExtractor134 {
    private const val POSE_N = 25
    private const val HAND_N = 21
    private const val F = (POSE_N + HAND_N + HAND_N) * 2

    /**
     * @param poseXY  FloatArray(POSE_N*2) or null (없으면 0)
     * @param leftXY  FloatArray(HAND_N*2) or null
     * @param rightXY FloatArray(HAND_N*2) or null
     */
    fun to134(poseXY: FloatArray?, leftXY: FloatArray?, rightXY: FloatArray?): FloatArray {
        val out = FloatArray(F)
        var dst = 0
        // pose
        if (poseXY != null && poseXY.size >= POSE_N * 2) {
            for (i in 0 until POSE_N * 2) out[dst++] = poseXY[i].coerceIn(0f, 1f)
        } else {
            dst += POSE_N * 2
        }
        // left
        if (leftXY != null && leftXY.size >= HAND_N * 2) {
            for (i in 0 until HAND_N * 2) out[dst++] = leftXY[i].coerceIn(0f, 1f)
        } else {
            dst += HAND_N * 2
        }
        // right
        if (rightXY != null && rightXY.size >= HAND_N * 2) {
            for (i in 0 until HAND_N * 2) out[dst++] = rightXY[i].coerceIn(0f, 1f)
        } else {
            dst += HAND_N * 2
        }
        return out
    }
}
