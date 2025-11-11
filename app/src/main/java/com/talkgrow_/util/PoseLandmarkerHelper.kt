// android_app/src/main/java/com/talkgrow_/util/PoseLandmarkerHelper.kt
package com.talkgrow_.util

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.MediaPipeException
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

class PoseLandmarkerHelper(
    private val context: Context,
    private val onResult: (res: PoseLandmarkerResult, dbg: Any?, w: Int, h: Int) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    companion object { private const val TAG = "PoseLMHelper" }

    private var landmarker: PoseLandmarker? = null
    private val ready = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var firstErrorLogged = false

    fun setup() {
        if (closed) closed = false
        if (landmarker != null) return

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_full.task")
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { res: PoseLandmarkerResult, mpImage: MPImage ->
                onResult(res, null, mpImage.width, mpImage.height)
            }
            .setErrorListener { e ->
                if (!firstErrorLogged) {
                    Log.e(TAG, "PoseLandmarker error", e)
                    firstErrorLogged = true
                }
                onError(e)
            }
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
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
                Log.e(TAG, "detectAsync failed (pose)", e)
                firstErrorLogged = true
            }
            onError(e)
        } catch (t: Throwable) {
            if (!firstErrorLogged) {
                Log.e(TAG, "detectAsync unexpected (pose)", t)
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
