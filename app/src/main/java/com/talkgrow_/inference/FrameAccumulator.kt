package com.talkgrow_.inference

import com.talkgrow_.util.FeatureBuilder134
import com.talkgrow_.util.FrameWindow

/**
 * MediaPipe 산출값을 Feature(134)로 변환해서 FrameWindow(91)에 누적.
 * MediaPipe 쪽 코드는 건드리지 않음.
 */
class FrameAccumulator(
    private val featureBuilder: FeatureBuilder134,
    private val window: FrameWindow
) {
    fun reset() = window.clear()

    /**
     * MediaPipe 원본을 그대로 담은 FrameInput을 만들어 build(frame)으로 변환.
     * 필요 시 tsMs 등은 FrameInput 내부에서 사용.
     */
    fun onFrame(frame: FeatureBuilder134.FrameInput) {
        val feat = featureBuilder.build(frame)
        window.add(feat)
    }

    fun window(): FrameWindow = window
}
