package com.talkgrow_.util

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    private val context: Context,
    private val onResult: (PoseLandmarkerResult, MPImage, Int, Int) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private var landmarker: PoseLandmarker? = null

    fun setup() {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task") // assets/
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { res, mpImg ->
                    onResult(res, mpImg, mpImg.width, mpImg.height)
                }
                .setErrorListener { onError(it) }
                .build()

            landmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (t: Throwable) {
            onError(t)
        }
    }

    fun detectAsync(mpImage: MPImage, rotationDeg: Int, tsMs: Long) {
        val lm = landmarker ?: return
        val opts = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDeg) // MP 내부 upright 보정
            .build()
        lm.detectAsync(mpImage, opts, tsMs)
    }

    fun clear() {
        runCatching { landmarker?.close() }
        landmarker = null
    }
}
