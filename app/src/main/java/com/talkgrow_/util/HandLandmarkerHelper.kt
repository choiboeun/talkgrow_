package com.talkgrow_.util

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    private val context: Context,
    private val onResult: (res: HandLandmarkerResult, mpImage: MPImage) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private var landmarker: HandLandmarker? = null

    fun setup() {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                com.google.mediapipe.tasks.core.BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build()
            )
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { res, image -> onResult(res, image) }
            .setErrorListener { e -> onError(e) }
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detectAsync(image: MPImage, @Suppress("UNUSED_PARAMETER") rotationDeg: Int, ts: Long) {
        landmarker?.detectAsync(image, ts)
    }

    fun close() { runCatching { landmarker?.close() } }
}