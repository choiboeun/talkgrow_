package com.talkgrow_.util

import kotlin.math.hypot

/**
 * 학습 당시 파이프라인과 동일하게:
 * - 기준점: 오른쪽 어깨 (pose25 내 인덱스 = FeatureBuilder134.IDX_R_SH_IN_25)
 * - 스케일: 좌/우 어깨 거리
 * - (x - originX) / scale, (y - originY) / scale
 */
object Normalizer {

    fun normalizeInPlaceBugForBug(
        pose25: FloatArray,
        leftHand: FloatArray,
        rightHand: FloatArray
    ) {
        // pose25는 (x,y) * 25
        val lIdx = FeatureBuilder134.IDX_L_SH_IN_25
        val rIdx = FeatureBuilder134.IDX_R_SH_IN_25

        val rx = pose25[rIdx * 2]
        val ry = pose25[rIdx * 2 + 1]
        val lx = pose25[lIdx * 2]
        val ly = pose25[lIdx * 2 + 1]

        val scale = maxOf(1e-6f, hypot((lx - rx).toDouble(), (ly - ry).toDouble()).toFloat())

        fun norm(xy: FloatArray) {
            var i = 0
            while (i < xy.size) {
                xy[i] = (xy[i] - rx) / scale
                xy[i + 1] = (xy[i + 1] - ry) / scale
                i += 2
            }
        }

        norm(pose25)
        norm(leftHand)
        norm(rightHand)
    }
}
