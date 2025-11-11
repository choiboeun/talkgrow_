// com/talkgrow_/util/FeatureBuilder134.kt
package com.talkgrow_.util

import android.util.Log
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Python 정규화 로직과 **완전 동치**가 되도록 정리한 버전
 * - 전체 정규화(어깨 중점 이동 + 프레임 평균 어깨거리 스케일) -> start부터 91프레임 복사 -> 부족분 0패드
 * - 입력 클램프(clamp [0,1])는 **기본 비활성화**(학습 파이프라인과 일치), 필요 시 build(..., clamp=true)로 사용
 */
object FeatureBuilder134 {
    const val T_FIXED = 91
    const val F_FIXED = 134

    private const val POSE_N = 25
    private const val HAND_N = 21
    private const val FEAT_DIM = (POSE_N + HAND_N + HAND_N) * 2 // 134

    // MediaPipe Pose(33) 원본 인덱스
    private const val NOSE = 0
    private const val LEFT_EYE = 2
    private const val RIGHT_EYE = 5
    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_ELBOW = 13
    private const val RIGHT_ELBOW = 14
    private const val LEFT_WRIST = 15
    private const val RIGHT_WRIST = 16
    private const val LEFT_PINKY = 17
    private const val RIGHT_PINKY = 18
    private const val LEFT_INDEX = 19
    private const val RIGHT_INDEX = 20
    private const val LEFT_THUMB = 21
    private const val RIGHT_THUMB = 22
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24
    private const val LEFT_KNEE = 25
    private const val RIGHT_KNEE = 26
    private const val LEFT_ANKLE = 27
    private const val RIGHT_ANKLE = 28
    private const val MOUTH_LEFT = 9
    private const val MOUTH_RIGHT = 10

    /**
     * ⚠ 학습 파이프라인의 POSE_SELECT(25점 순서)와 완전히 동일
     *
     * 내부 pose25 기준 인덱스:
     * 0:NOSE, 1:L_EYE, 2:R_EYE, 3:L_EAR, 4:R_EAR,
     * 5:L_SHOULDER, 6:R_SHOULDER, 7:L_ELBOW, 8:R_ELBOW,
     * 9:L_WRIST,10:R_WRIST, 11:L_HIP,12:R_HIP, 13:L_KNEE,14:R_KNEE,
     * 15:L_ANKLE,16:R_ANKLE, 17:MOUTH_L,18:MOUTH_R,
     * 19:L_PINKY,20:R_PINKY, 21:L_INDEX,22:R_INDEX, 23:L_THUMB,24:R_THUMB
     */
    private val POSE_SELECT_25 = intArrayOf(
        NOSE,
        LEFT_EYE, RIGHT_EYE,
        LEFT_EAR, RIGHT_EAR,
        LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_ELBOW, RIGHT_ELBOW,
        LEFT_WRIST, RIGHT_WRIST,
        LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE,
        LEFT_ANKLE, RIGHT_ANKLE,
        MOUTH_LEFT, MOUTH_RIGHT,
        LEFT_PINKY, RIGHT_PINKY,
        LEFT_INDEX, RIGHT_INDEX,
        LEFT_THUMB, RIGHT_THUMB
    )

    // 선택순서에서 어깨의 "pose25 내부 인덱스" 자동 탐색 (하드코딩 금지)
    private val IDX_L_SHOULDER: Int = POSE_SELECT_25.indexOf(LEFT_SHOULDER).also {
        require(it >= 0) { "LEFT_SHOULDER not found in POSE_SELECT_25" }
    }
    private val IDX_R_SHOULDER: Int = POSE_SELECT_25.indexOf(RIGHT_SHOULDER).also {
        require(it >= 0) { "RIGHT_SHOULDER not found in POSE_SELECT_25" }
    }

    @JvmStatic
    fun logShoulderIndices(tag: String = "FeatureBuilder134") {
        val l = IDX_L_SHOULDER
        val r = IDX_R_SHOULDER
        val srcL = LEFT_SHOULDER
        val srcR = RIGHT_SHOULDER
        val idxInSelectL = POSE_SELECT_25.indexOf(srcL)
        val idxInSelectR = POSE_SELECT_25.indexOf(srcR)

        Log.i(tag, "POSE_SELECT_25 size=${POSE_SELECT_25.size}")
        Log.i(tag, "Shoulder indices (pose25 internal): L=$l, R=$r (expected L=5, R=6)")
        Log.i(tag, "From MP33 -> pose25 mapping: LEFT_SHOULDER($srcL) -> $idxInSelectL, RIGHT_SHOULDER($srcR) -> $idxInSelectR")

        if (l != idxInSelectL || r != idxInSelectR) {
            Log.w(tag, "Mismatch: computed=($l,$r), searched=($idxInSelectL,$idxInSelectR)")
        }
    }

    fun reset() = Unit

    private fun clamp01(v: Float) = when {
        v < 0f -> 0f
        v > 1f -> 1f
        else -> v
    }

    private fun xyOf(kp: KP?, clamp: Boolean): Pair<Float, Float> {
        var x = kp?.x ?: 0f
        var y = kp?.y ?: 0f
        if (clamp) {
            x = clamp01(x)
            y = clamp01(y)
        }
        return x to y
    }

    private fun fillPose25(dst: FloatArray, pose33: List<KP>?, clamp: Boolean) {
        var di = 0
        if (pose33.isNullOrEmpty()) {
            repeat(POSE_N) { dst[di++] = 0f; dst[di++] = 0f }
            return
        }
        for (idx in POSE_SELECT_25) {
            val (x, y) = xyOf(pose33.getOrNull(idx), clamp)
            dst[di++] = x; dst[di++] = y
        }
    }

    private fun fillHand(dst: FloatArray, start: Int, hand21: List<KP>?, clamp: Boolean) {
        var di = start
        if (hand21.isNullOrEmpty()) {
            repeat(HAND_N) { dst[di++] = 0f; dst[di++] = 0f }
            return
        }
        for (i in 0 until HAND_N) {
            val (x, y) = xyOf(hand21.getOrNull(i), clamp)
            dst[di++] = x; dst[di++] = y
        }
    }

    /**
     * 프레임당 134차원 벡터 생성
     * @param clamp  학습 파이프라인과 일치시키려면 false 권장(파이썬은 기본적으로 clamp 안 함)
     */
    fun build(input: FrameInput, clamp: Boolean = false): FloatArray {
        val out = FloatArray(FEAT_DIM)
        fillPose25(out, input.pose33, clamp)
        fillHand(out, POSE_N * 2, input.left21, clamp)
        fillHand(out, (POSE_N + HAND_N) * 2, input.right21, clamp)
        return out
    }

    /**
     * 파이썬 normalize_keypoints_torso와 동치:
     * - 각 프레임의 (L/R 어깨) 중점으로 평행이동
     * - (프레임별 어깨거리)의 평균값으로 스케일
     */
    private fun normalizeShoulderCenter(frames: Array<FloatArray>, valid: Int) {
        if (valid <= 0) return

        val l = IDX_L_SHOULDER * 2
        val r = IDX_R_SHOULDER * 2

        // 평균 스케일(= 어깨거리 평균)
        var scaleSum = 0f
        for (i in 0 until valid) {
            val fr = frames[i]
            val dx = fr[r] - fr[l]
            val dy = fr[r + 1] - fr[l + 1]
            val dist = sqrt(dx * dx + dy * dy)
            scaleSum += if (dist > 1e-6f) dist else 1f
        }
        val scale = (scaleSum / valid).coerceAtLeast(1e-6f)

        // 각 프레임별 중심 이동 후 단일 scale 적용
        for (i in 0 until valid) {
            val fr = frames[i]
            val cx = (fr[l] + fr[r]) * 0.5f
            val cy = (fr[l + 1] + fr[r + 1]) * 0.5f
            var j = 0
            while (j + 1 < fr.size) {
                fr[j] = (fr[j] - cx) / scale
                fr[j + 1] = (fr[j + 1] - cy) / scale
                j += 2
            }
        }
    }

    /**
     * 규칙: 전체 정규화 → start부터 91프레임 복사 → 부족분 제로패드
     * (Python normalize_keypoints_torso + pad_or_truncate(start)와 동치)
     */
    fun buildNormalizedWindow(frames: List<FloatArray>, start: Int): Array<FloatArray> {
        if (frames.isEmpty()) return Array(T_FIXED) { FloatArray(F_FIXED) }

        // 길이/차원 보정하여 복사
        val full = Array(frames.size) { i ->
            val dst = FloatArray(F_FIXED)
            val src = frames[i]
            val n = min(src.size, F_FIXED)
            if (n > 0) System.arraycopy(src, 0, dst, 0, n)
            dst
        }

        // 전체 정규화
        normalizeShoulderCenter(full, full.size)

        // 윈도우 추출 + 패딩
        val window = Array(T_FIXED) { FloatArray(F_FIXED) }
        var ti = 0
        while (ti < T_FIXED && (start + ti) < full.size) {
            System.arraycopy(full[start + ti], 0, window[ti], 0, F_FIXED)
            ti++
        }
        // 나머지는 0패드 이미 되어 있음
        return window
    }

    @Deprecated("학습 파이프라인 동치 유지를 위해 buildNormalizedWindow 사용 권장")
    fun buildWindowedSequence(frames: List<FloatArray>): Array<FloatArray> {
        if (frames.isEmpty()) return Array(T_FIXED) { FloatArray(F_FIXED) }
        val count = min(frames.size, T_FIXED)

        val trimmed = Array(count) { FloatArray(F_FIXED) }
        for (i in 0 until count) {
            val src = frames[i]
            val n = min(src.size, F_FIXED)
            if (n > 0) System.arraycopy(src, 0, trimmed[i], 0, n)
        }

        normalizeShoulderCenter(trimmed, count)

        val window = Array(T_FIXED) { FloatArray(F_FIXED) }
        for (i in 0 until count) {
            System.arraycopy(trimmed[i], 0, window[i], 0, F_FIXED)
        }
        return window
    }
}

data class FrameInput(
    val pose33: List<KP>?,
    val left21: List<KP>?,
    val right21: List<KP>?,
    val mirroredPreview: Boolean = true // UI-only; 모델 입력에는 미사용
)
