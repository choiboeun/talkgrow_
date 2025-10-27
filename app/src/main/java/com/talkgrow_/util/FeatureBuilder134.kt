package com.talkgrow_.util

/**
 * F = 134 = (Pose25 + Left21 + Right21) * (x,y)
 * - 포즈 25개 인덱스는 학습 시 순서 그대로
 * - 프리뷰 미러와 무관하게, 입력은 해부학적 좌/우를 사용(스왑 안 함)
 * - ⚠️ 정규화는 Interpreter 쪽에서 수행 (여기선 원시 xy만 구성)
 */
object FeatureBuilder134 {

    private val POSE_IDX_25 = intArrayOf(
        0,          // NOSE
        2, 5,       // L_EYE, R_EYE
        7, 8,       // L_EAR, R_EAR
        11, 12,     // L_SH, R_SH
        13, 14,     // L_EL, R_EL
        15, 16,     // L_WR, R_WR
        23, 24,     // L_HIP, R_HIP
        25, 26,     // L_KNEE, R_KNEE
        27, 28,     // L_ANK, R_ANK
        9, 10,      // MOUTH_L, MOUTH_R
        17, 18,     // L_PINKY(root), R_PINKY(root)
        19, 20,     // L_INDEX(root), R_INDEX(root)
        21, 22      // L_THUMB(root), R_THUMB(root)
    )

    private const val HAND_N = 21
    const val F = 134 // (25 + 21 + 21) * 2

    data class FrameInput(
        val pose33: List<KP>,
        val left21: List<KP>,     // 해부학적 왼손
        val right21: List<KP>,    // 해부학적 오른손
        val mirroredPreview: Boolean // (참조용: 현재 로직에선 사용 안 함)
    )

    fun build(frame: FrameInput): FloatArray {
        val pose25 = FloatArray(25 * 2) { 0f }
        for (i in POSE_IDX_25.indices) {
            val srcIdx = POSE_IDX_25[i]
            val kp = frame.pose33.getOrNull(srcIdx)
            val di = i * 2
            pose25[di]     = kp?.x ?: 0f
            pose25[di + 1] = kp?.y ?: 0f
        }

        fun copyHand(src: List<KP>): FloatArray {
            val arr = FloatArray(HAND_N * 2) { 0f }
            for (i in 0 until HAND_N) {
                val kp = src.getOrNull(i)
                arr[i * 2]     = kp?.x ?: 0f
                arr[i * 2 + 1] = kp?.y ?: 0f
            }
            return arr
        }
        val lh = copyHand(frame.left21)
        val rh = copyHand(frame.right21)

        val out = FloatArray(F)
        var k = 0
        fun push(xy: FloatArray) {
            var i = 0
            while (i < xy.size) {
                out[k++] = xy[i]
                out[k++] = xy[i + 1]
                i += 2
            }
        }
        push(pose25); push(lh); push(rh)
        return out
    }

    internal const val IDX_L_SH_IN_25 = 5
    internal const val IDX_R_SH_IN_25 = 6
}
