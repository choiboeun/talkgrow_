// android_app/src/main/java/com/talkgrow_/util/HandLandmarkerHelper.kt
package com.talkgrow_.util

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.MediaPipeException
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

class HandLandmarkerHelper(
    private val context: Context,
    private val onResult: (res: HandLandmarkerResult, mpImage: MPImage) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    companion object { private const val TAG = "HandLMHelper" }

    private var landmarker: HandLandmarker? = null
    private val ready = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var firstErrorLogged = false

    fun setup() {
        if (closed) closed = false
        if (landmarker != null) return

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { res, image -> onResult(res, image) }
            .setErrorListener { e ->
                if (!firstErrorLogged) {
                    Log.e(TAG, "HandLandmarker error", e)
                    firstErrorLogged = true
                }
                onError(e)
            }
            .build()

        landmarker = HandLandmarker.createFromOptions(context, options)
        ready.set(true)
        firstErrorLogged = false
    }

    fun isReady(): Boolean = ready.get() && !closed && landmarker != null

    fun detectAsync(image: MPImage, @Suppress("UNUSED_PARAMETER") rotationDeg: Int, ts: Long) {
        val lm = landmarker ?: return
        if (!isReady()) return
        try {
            // MediaPipe LIVE_STREAM 타임스탬프는 "ms" 단위
            lm.detectAsync(image, ts)
        } catch (e: MediaPipeException) {
            if (!firstErrorLogged) {
                Log.e(TAG, "detectAsync failed (hand)", e)
                firstErrorLogged = true
            }
            onError(e)
        } catch (t: Throwable) {
            if (!firstErrorLogged) {
                Log.e(TAG, "detectAsync unexpected (hand)", t)
                firstErrorLogged = true
            }
            onError(t)
        }
    }

    fun close() {
        closed = true
        ready.set(false)
        runCatching { landmarker?.close() }
        landmarker = null
    }
}
