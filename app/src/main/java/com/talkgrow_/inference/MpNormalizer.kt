package com.talkgrow_.inference

import kotlin.math.hypot
import android.util.Log

/**
 * MediaPipe 키포인트 (x,y)만으로 구성된 134차원 벡터에 대해
 * - 기준점: Pose RIGHT_SHOULDER (원점 이동)
 * - 스케일: LEFT_SHOULDER ~ RIGHT_SHOULDER 거리로 나눔
 *
 * 입력 벡터는 [Pose 25 *2] + [Left Hand 21 *2] + [Right Hand 21 *2] = 134
 * 즉 (x0,y0,x1,y1,...) 순서의 2-간격 플랫 구조.
 *
 * ※ 주의
 *  - 학습과 동일한 포즈 서브셋(25p) 인덱스 순서를 보장해야 함.
 *  - 좌표 미러링은 하지 않는다(미러링은 Overlay에서만).  // ★ 문서화
 */
object MpNormalizer {
    private const val TAG = "MpNormalizer"

    private const val POSE_N = 25
    private const val HAND_N = 21
    private const val POSE_DIM = POSE_N * 2
    private const val F = (POSE_N + HAND_N + HAND_N) * 2 // 134

    private const val IDX_LEFT_SHOULDER  = 5  // 0-based in POSE landmarks
    private const val IDX_RIGHT_SHOULDER = 6

    private const val RSH_X = IDX_RIGHT_SHOULDER * 2
    private const val RSH_Y = RSH_X + 1
    private const val LSH_X = IDX_LEFT_SHOULDER  * 2
    private const val LSH_Y = LSH_X + 1

    /** in-place 정규화: 실패 시 원본 그대로 둡니다. */
    fun normalizeXYInPlace(frameF134: FloatArray): Boolean {
        if (frameF134.size != F) return false

        val rshx = frameF134.getOrNull(RSH_X) ?: return false
        val rshy = frameF134.getOrNull(RSH_Y) ?: return false
        val lshx = frameF134.getOrNull(LSH_X) ?: return false
        val lshy = frameF134.getOrNull(LSH_Y) ?: return false

        val bothMissing = (rshx == 0f && rshy == 0f && lshx == 0f && lshy == 0f)
        if (bothMissing) return false

        val dx = lshx - rshx
        val dy = lshy - rshy
        val shoulderDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val scale = if (shoulderDist > 1e-6f) shoulderDist else 1f

        var i = 0
        while (i + 1 < F) {
            val x = frameF134[i]
            val y = frameF134[i + 1]
            frameF134[i]     = (x - rshx) / scale
            frameF134[i + 1] = (y - rshy) / scale
            i += 2
        }
        return true
    }

    /** 디버그용: 첫 프레임에 한 번만 호출해서 인덱스/값 로깅 */
    fun debugLogOnce(frameF134: FloatArray) {
        if (frameF134.size != F) return
        val rshx = frameF134[RSH_X]
        val rshy = frameF134[RSH_Y]
        val lshx = frameF134[LSH_X]
        val lshy = frameF134[LSH_Y]
        val dx = lshx - rshx
        val dy = lshy - rshy
        val dist = hypot(dx.toDouble(), dy.toDouble())
        Log.i(TAG, "MP-NORM indices: RSH=($RSH_X,$RSH_Y) LSH=($LSH_X,$LSH_Y)  rawRSH=($rshx,$rshy) rawLSH=($lshx,$lshy) dist≈$dist")
    }
}
