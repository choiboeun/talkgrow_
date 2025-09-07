package com.talkgrow_.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var maxNumHands: Int = DEFAULT_NUM_HANDS,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val handLandmarkerHelperListener: LandmarkerListener? = null
) {
    private var handLandmarker: HandLandmarker? = null

    init { setupHandLandmarker() }

    fun clearHandLandmarker() { handLandmarker?.close(); handLandmarker = null }
    fun isClose(): Boolean = (handLandmarker == null)

    fun setupHandLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
        }
        baseOptionBuilder.setModelAssetPath(MP_HAND_LANDMARKER_TASK)

        if (runningMode == RunningMode.LIVE_STREAM && handLandmarkerHelperListener == null) {
            throw IllegalStateException("handLandmarkerHelperListener must be set in LIVE_STREAM.")
        }

        try {
            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionBuilder.build())
                .setMinHandDetectionConfidence(minHandDetectionConfidence)
                .setMinTrackingConfidence(minHandTrackingConfidence)
                .setMinHandPresenceConfidence(minHandPresenceConfidence)
                .setNumHands(maxNumHands)
                .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker init failed. See logs.", OTHER_ERROR
            )
            Log.e(TAG, "Task load error: ${e.message}", e)
        } catch (e: RuntimeException) {
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker init failed. See logs.", GPU_ERROR
            )
            Log.e(TAG, "Model load error: ${e.message}", e)
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException("detectLiveStream only for LIVE_STREAM.")
        }
        val frameTime = SystemClock.uptimeMillis()

        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }
        val rotated = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)

        val mpImage = BitmapImageBuilder(rotated).build()
        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        handLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException("detectVideoFile only for VIDEO.")
        }
        val startTime = SystemClock.uptimeMillis()
        var didErrorOccur = false

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height
        if (videoLengthMs == null || width == null || height == null) return null

        val resultList = mutableListOf<HandLandmarkerResult>()
        val frames = videoLengthMs / inferenceIntervalMs

        for (i in 0..frames) {
            val ts = i * inferenceIntervalMs
            retriever.getFrameAtTime(ts * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { f ->
                val argb = if (f.config == Bitmap.Config.ARGB_8888) f else f.copy(Bitmap.Config.ARGB_8888, false)
                val mp = BitmapImageBuilder(argb).build()
                handLandmarker?.detectForVideo(mp, ts)?.let { r -> resultList.add(r) } ?: run {
                    didErrorOccur = true
                    handLandmarkerHelperListener?.onError(
                        "Null result in detectVideoFile.", OTHER_ERROR
                    )
                }
            } ?: run {
                didErrorOccur = true
                handLandmarkerHelperListener?.onError(
                    "Frame fetch failed in video.", OTHER_ERROR
                )
            }
        }
        retriever.release()

        val perFrame = (SystemClock.uptimeMillis() - startTime) / frames.coerceAtLeast(1)
        return if (didErrorOccur) null else ResultBundle(resultList, perFrame, height, width)
    }

    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException("detectImage only for IMAGE.")
        }
        val start = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(image).build()
        handLandmarker?.detect(mpImage)?.also { res ->
            val t = SystemClock.uptimeMillis() - start
            return ResultBundle(listOf(res), t, image.height, image.width)
        }
        handLandmarkerHelperListener?.onError("Hand Landmarker failed to detect.", OTHER_ERROR)
        return null
    }

    private fun returnLivestreamResult(result: HandLandmarkerResult, input: MPImage) {
        val t = SystemClock.uptimeMillis() - result.timestampMs()
        handLandmarkerHelperListener?.onResults(
            ResultBundle(listOf(result), t, input.height, input.width)
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        handLandmarkerHelperListener?.onError(error.message ?: "Unknown error", OTHER_ERROR)
    }

    companion object {
        const val TAG = "HandLandmarkerHelper"
        private const val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_HANDS = 2
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int)
        fun onResults(resultBundle: ResultBundle)
    }
}
