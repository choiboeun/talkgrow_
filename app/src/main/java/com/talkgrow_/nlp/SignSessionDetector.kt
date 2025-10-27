package com.talkgrow_.nlp

import kotlin.math.abs

/**
 * 손 존재 + 모션(프레임 간 변화량)으로 수어 세션(시작/종료) 감지.
 */
class SignSessionDetector(
    private val motionOnTh: Float = 0.08f,
    private val motionOffTh: Float = 0.03f,
    private val minOnFrames: Int = 5,
    private val minOffFrames: Int = 8
) {
    private var prevFrame: FloatArray? = null
    private var ewmaMotion = 0f
    private val alpha = 0.3f

    private var onCount = 0
    private var offCount = 0
    private var _active = false

    val isActive: Boolean get() = _active
    var lastActiveAt: Long = 0L
        private set

    fun update(frame: FloatArray, handCount: Int, nowMs: Long): Boolean {
        val motion = measureMotion(frame, prevFrame)
        prevFrame = frame
        ewmaMotion = if (ewmaMotion == 0f) motion else (alpha*motion + (1f-alpha)*ewmaMotion)

        val handsOk = handCount > 0
        val onCond  = handsOk && ewmaMotion >= motionOnTh
        val offCond = (!handsOk) || ewmaMotion <= motionOffTh

        if (_active) {
            if (offCond) {
                offCount++; onCount = 0
                if (offCount >= minOffFrames) _active = false
            } else { offCount = 0; onCount = 0 }
        } else {
            if (onCond) {
                onCount++; offCount = 0
                if (onCount >= minOnFrames) { _active = true; lastActiveAt = nowMs }
            } else { onCount = 0; offCount = 0 }
        }
        return _active
    }

    private fun measureMotion(cur: FloatArray, prev: FloatArray?): Float {
        if (prev == null || prev.size != cur.size) return 0f
        var sum = 0f
        var i = 0
        while (i < cur.size) { sum += abs(cur[i] - prev[i]); i++ }
        return sum / cur.size
    }
}
