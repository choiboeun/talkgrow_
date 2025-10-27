package com.talkgrow_.util

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    private val context: Context,
    private val onResult: (res: PoseLandmarkerResult, dbg: Any?, w: Int, h: Int) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private var landmarker: PoseLandmarker? = null

    fun setup() {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_full.task")
                    .build()
            )
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { res: PoseLandmarkerResult, mpImage: MPImage ->
                onResult(res, null, mpImage.width, mpImage.height)
            }
            .setErrorListener { e -> onError(e) }
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detectAsync(image: MPImage, @Suppress("UNUSED_PARAMETER") rotationDeg: Int, ts: Long) {
        landmarker?.detectAsync(image, ts)
    }

    fun close() { runCatching { landmarker?.close() } }
}