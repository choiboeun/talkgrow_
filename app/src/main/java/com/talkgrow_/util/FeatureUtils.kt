package com.talkgrow_.util

import android.util.Size
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

object FeatureUtils {

    /** 학습 규약: 미러링 없이 BODY25 → RH(21) → LH(21) = 134D ([x0,y0,x1,y1,...]) */
    fun buildFrameFeature134_NoMirror_RFirst(
        hands: HandLandmarkerResult?,
        pose: PoseLandmarkerResult?
    ): FloatArray? {

        val body25 = extractBody25FromPose(pose) ?: FloatArray(25 * 2) { 0f }
        val (rh21, lh21) = extractHands21_R_then_L(hands)

        // body25(50) + right(42) + left(42) = 134
        val out = FloatArray(134)
        var k = 0
        for (v in body25) { out[k++] = v }
        for (v in rh21)   { out[k++] = v }
        for (v in lh21)   { out[k++] = v }
        return out
    }

    /** 기존 프로젝트에 buildFrameFeature134(...)가 있을 수도 있어 폴백용 시그니처 제공 */
    fun buildFrameFeature134(
        hands: HandLandmarkerResult?,
        pose: PoseLandmarkerResult?
    ): FloatArray? = buildFrameFeature134_NoMirror_RFirst(hands, pose)

    // ---------- 내부 헬퍼들 ----------

    /**
     * PoseLandmarker(33점) → BODY25(25점) 순서로 다운맵.
     * 좌표는 [0..1]로 정규화. (이미 정규화되어 들어오면 그대로 패스)
     * BODY25 인덱스(대략):
     *  0 Nose, 1 Neck(= (LShoulder+RShoulder)/2), 2 RShoulder, 3 RElbow, 4 RWrist,
     *  5 LShoulder, 6 LElbow, 7 LWrist,
     *  8 MidHip(= (LHip+RHip)/2), 9 RHip, 10 RKnee, 11 RAnkle,
     *  12 LHip, 13 LKnee, 14 LAnkle,
     *  15 REye, 16 LEye, 17 REar, 18 LEar,
     *  19 LBigToe, 20 LSmallToe, 21 LHeel,
     *  22 RBigToe, 23 RSmallToe, 24 RHeel
     */
    private fun extractBody25FromPose(pose: PoseLandmarkerResult?): FloatArray? {
        if (pose == null || pose.landmarks().isEmpty()) return null
        val lm = pose.landmarks()[0] // 가장 큰 1명만 사용

        fun getXY(i: Int): Pair<Float, Float>? {
            if (i !in lm.indices) return null
            val p = lm[i]
            val x = clamp01(p.x())
            val y = clamp01(p.y())
            return x to y
        }
        fun mid(a: Pair<Float,Float>?, b: Pair<Float,Float>?): Pair<Float,Float>? {
            if (a == null || b == null) return null
            return ((a.first + b.first)/2f) to ((a.second + b.second)/2f)
        }

        // MediaPipe pose index 참고
        val NOSE = 0
        val LEYE = 2; val REYE = 5
        val LEAR = 7; val REAR = 8
        val LSH  = 11; val RSH  = 12
        val LELB = 13; val RELB = 14
        val LWR  = 15; val RWR  = 16
        val LHIP = 23; val RHIP = 24
        val LKNE = 25; val RKNE = 26
        val LANK = 27; val RANK = 28
        val LHEEL = 29; val RHEEL = 30
        val LFOOT_INDEX = 31; val RFOOT_INDEX = 32
        // 발가락(큰/작은)은 MP에 직접 매핑이 없어서:
        // BigToe≈FOOT_INDEX, SmallToe≈FOOT_INDEX와 유사 좌표(약간 이동)로 근사
        fun offset(p: Pair<Float,Float>?, dx: Float, dy: Float) =
            if (p==null) null else (clamp01(p.first+dx) to clamp01(p.second+dy))

        val nose = getXY(NOSE)
        val lsh = getXY(LSH); val rsh = getXY(RSH)
        val neck = mid(lsh, rsh)

        val relb = getXY(RELB); val rwr = getXY(RWR)
        val lelb = getXY(LELB); val lwr = getXY(LWR)

        val lhip = getXY(LHIP); val rhip = getXY(RHIP)
        val midHip = mid(lhip, rhip)

        val rknee = getXY(RKNE); val rank = getXY(RANK)
        val lknee = getXY(LKNE); val lank = getXY(LANK)

        val reye = getXY(REYE); val leye = getXY(LEYE)
        val rear = getXY(REAR); val lear = getXY(LEAR)

        val lheel = getXY(LHEEL); val rheel = getXY(RHEEL)
        val lbig = getXY(LFOOT_INDEX); val rbig = getXY(RFOOT_INDEX)
        val lsmall = offset(lbig, -0.01f, 0f)
        val rsmall = offset(rbig, +0.01f, 0f)

        // BODY25 순서대로 채우기
        val out = FloatArray(25*2) { 0f }
        fun put(i: Int, p: Pair<Float,Float>?) {
            val idx = i*2
            if (p != null) { out[idx] = p.first; out[idx+1] = p.second }
        }

        put(0, nose)
        put(1, neck)
        put(2, rsh);   put(3, relb);  put(4, rwr)
        put(5, lsh);   put(6, lelb);  put(7, lwr)
        put(8, midHip)
        put(9, rhip);  put(10, rknee); put(11, rank)
        put(12, lhip); put(13, lknee); put(14, lank)
        put(15, reye); put(16, leye);  put(17, rear); put(18, lear)
        put(19, lbig); put(20, lsmall); put(21, lheel)
        put(22, rbig); put(23, rsmall); put(24, rheel)

        return out
    }

    /** 오른손→왼손 21점씩. 없는 손은 0으로 패딩. */
    private fun extractHands21_R_then_L(
        hands: HandLandmarkerResult?
    ): Pair<FloatArray, FloatArray> {
        val zero = FloatArray(21*2) { 0f }
        if (hands == null || hands.landmarks().isEmpty()) return zero to zero

        // MP Tasks: handedness()[i] -> List<Category> (Left/Right)
        var right: FloatArray? = null
        var left: FloatArray? = null

        val allLm = hands.landmarks()
        val allHd = hands.handednesses()

        for (i in allLm.indices) {
            val lm = allLm[i]
            val side = majorHand(allHd.getOrNull(i))
            val arr = FloatArray(21*2) { 0f }
            var k = 0
            val n = min(21, lm.size)
            for (j in 0 until n) {
                val p = lm[j]
                arr[k++] = clamp01(p.x())
                arr[k++] = clamp01(p.y())
            }
            when (side) {
                "Right" -> if (right == null) right = arr
                "Left"  -> if (left  == null) left  = arr
                else -> {
                    // 미분류: palm 위치로 오른쪽/왼쪽 간단 추정(중앙 0.5 기준)
                    if (arr[0] <= 0.5f) { // x작으면 Left
                        if (left == null) left = arr else if (right == null) right = arr
                    } else {
                        if (right == null) right = arr else if (left == null) left = arr
                    }
                }
            }
        }
        return (right ?: zero) to (left ?: zero)
    }

    private fun majorHand(cats: List<Category>?): String? {
        if (cats.isNullOrEmpty()) return null
        val best = cats.maxBy { it.score() }
        val name = best.categoryName()?.lowercase() ?: return null
        return when {
            "right" in name -> "Right"
            "left"  in name -> "Left"
            else -> null
        }
    }

    private fun clamp01(v: Float): Float = max(0f, min(1f, v))
}
