package com.talkgrow_.util

/**
 * 미디어파이프 랜드마크 1개. x,y는 [0,1] 정규화 좌표라고 가정.
 * z, visibility 는 사용하지 않더라도 호환을 위해 제공.
 */
data class KP(
    val x: Float,
    val y: Float,
    val z: Float? = null,
    val visibility: Float? = null
)