package com.talkgrow_.util

import kotlin.math.max
import kotlin.math.sqrt

/**
 * FeatureBuilder134가 생성한 134차원 특징 벡터에 좌표 정규화를 적용하는 클래스입니다.
 *
 * 이 정규화는 프로젝트 문서에 명시된 'bug-for-bug normalization'의 인덱스 오류를 수정한 버전입니다.
 * 134차원 (Pose 25 + Hands 21x2) 벡터를 기준으로 합니다.
 */
object FeatureNormalizer134 {

    // 134차원 벡터 내 오른쪽 어깨 (R_SH)와 왼쪽 어깨 (L_SH)의 CORRECTED 인덱스
    private const val R_SH_X = 12 // Pose 25의 6번째 키포인트(R_SH) X 좌표
    private const val R_SH_Y = 13 // Pose 25의 6번째 키포인트(R_SH) Y 좌표
    private const val L_SH_X = 10 // Pose 25의 5번째 키포인트(L_SH) X 좌표
    private const val L_SH_Y = 11 // Pose 25의 5번째 키포인트(L_SH) Y 좌표

    /**
     * 단일 134차원 특징 벡터에 중심 이동 및 스케일 정규화를 적용합니다.
     *
     * @param feature FloatArray (F=134) 원시 좌표 배열. (배열 자체가 수정됨)
     */
    fun normalize(feature: FloatArray) {
        if (feature.size != FeatureBuilder134.F) {
            throw IllegalArgumentException("Feature size must be ${FeatureBuilder134.F}")
        }

        // 1. 기준 좌표 추출: 오른쪽 어깨를 기준으로 사용 [cite: 359]
        val rsX = feature[R_SH_X]
        val rsY = feature[R_SH_Y]

        // 2. 중심 이동 (Center Shift): 모든 좌표를 오른쪽 어깨 기준으로 이동
        // 134차원 배열은 (x0, y0, x1, y1, ...) 구조입니다.
        for (i in 0 until feature.size) {
            if (i % 2 == 0) { // X 좌표일 경우 (0, 2, 4, ...)
                feature[i] -= rsX
            } else { // Y 좌표일 경우 (1, 3, 5, ...)
                feature[i] -= rsY
            }
        }

        // 3. 스케일 정규화 (Scale Normalization): 어깨 간 거리로 나누기
        // L_SH와 R_SH 간의 거리 계산
        val distSq = (feature[R_SH_X] - feature[L_SH_X]) * (feature[R_SH_X] - feature[L_SH_X]) +
                (feature[R_SH_Y] - feature[L_SH_Y]) * (feature[R_SH_Y] - feature[L_SH_Y])

        // 어깨 거리는 0이 되지 않도록 엡실론(1e-6)을 더해줍니다. [cite: 365]
        val shoulderDist = sqrt(max(distSq, 1e-6f))

        // 모든 좌표를 어깨 거리로 나눕니다.
        for (i in feature.indices) {
            feature[i] /= shoulderDist
        }
    }
}