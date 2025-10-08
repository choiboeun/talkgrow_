package com.talkgrow_.util

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    private val context: Context,
    private val onResult: (HandLandmarkerResult, MPImage) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    companion object { private const val TAG = "HandLMHelper" }

    private var landmarker: HandLandmarker? = null

    fun setup() {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task") // assets/
                .build()

            val opts = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2) // ✅ 양손 인식
                .setResultListener { res, mpImage -> onResult(res, mpImage) }
                .setErrorListener { e -> onError(e) }
                .build()

            landmarker = HandLandmarker.createFromOptions(context, opts)
            Log.d(TAG, "HandLandmarker created (numHands=2)")
        } catch (t: Throwable) {
            onError(t)
        }
    }

    fun detectAsync(mpImage: MPImage, rotationDeg: Int, tsMs: Long) {
        val lm = landmarker ?: return
        val imgOpts = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDeg) // MP 내부에서 upright 보정
            .build()
        lm.detectAsync(mpImage, imgOpts, tsMs)
    }

    fun close() {
        runCatching { landmarker?.close() }
        landmarker = null
    }
}
