package com.talkgrow_.util

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    companion object {
        // HandLandmarkerHelper와 분리된 독립 상수 (필요 시 값만 맞추면 됨)
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1   // 0.10.7에선 기본 CPU 사용, GPU는 선택적
        const val DELEGATE_NNAPI = 2

        const val DEFAULT_NUM_HANDS = 2
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5f
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5f
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5f
    }

    private var _delegate: Int = DELEGATE_CPU

    private var _minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE
    private var _minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE
    private var _minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE

    private var _maxHands: Int = DEFAULT_NUM_HANDS

    val currentDelegate: Int get() = _delegate
    val currentMinHandDetectionConfidence: Float get() = _minHandDetectionConfidence
    val currentMinHandTrackingConfidence: Float get() = _minHandTrackingConfidence
    val currentMinHandPresenceConfidence: Float get() = _minHandPresenceConfidence
    val currentMaxHands: Int get() = _maxHands

    fun setDelegate(delegate: Int) { _delegate = delegate }
    fun setMinHandDetectionConfidence(confidence: Float) { _minHandDetectionConfidence = confidence }
    fun setMinHandTrackingConfidence(confidence: Float) { _minHandTrackingConfidence = confidence }
    fun setMinHandPresenceConfidence(confidence: Float) { _minHandPresenceConfidence = confidence }
    fun setMaxHands(maxResults: Int) { _maxHands = maxResults }
}
