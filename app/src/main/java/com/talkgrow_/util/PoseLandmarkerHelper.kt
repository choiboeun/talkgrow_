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
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    private val context: Context,
    private val poseLandmarkerHelperListener: LandmarkerListener? = null
) {
    private var poseLandmarker: PoseLandmarker? = null

    init { setupPoseLandmarker() }

    fun clearPoseLandmarker() { poseLandmarker?.close(); poseLandmarker = null }
    fun isClose(): Boolean = (poseLandmarker == null)

    fun setupPoseLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
        }

        val modelName = when (currentModel) {
            MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
            MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
            MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
            else -> "pose_landmarker_full.task"
        }
        baseOptionBuilder.setModelAssetPath(modelName)

        if (runningMode == RunningMode.LIVE_STREAM && poseLandmarkerHelperListener == null) {
            throw IllegalStateException("LIVE_STREAM 모드에선 poseLandmarkerHelperListener 가 필요합니다.")
        }

        try {
            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionBuilder.build())
                .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                .setMinTrackingConfidence(minPoseTrackingConfidence)
                .setMinPosePresenceConfidence(minPosePresenceConfidence)
                .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            poseLandmarker = PoseLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            poseLandmarkerHelperListener?.onError("Pose Landmarker 초기화 실패(IllegalState).", OTHER_ERROR)
            Log.e(TAG, "PoseLandmarker init error: ${e.message}", e)
        } catch (e: RuntimeException) {
            poseLandmarkerHelperListener?.onError("Pose Landmarker 초기화 실패(Runtime).", GPU_ERROR)
            Log.e(TAG, "PoseLandmarker runtime error: ${e.message}", e)
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException("LIVE_STREAM 이 아닙니다.")
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

        detectAsync(BitmapImageBuilder(rotated).build(), frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException("IMAGE 모드가 아닙니다.")
        }
        val start = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(image).build()
        val result = poseLandmarker?.detect(mpImage) ?: run {
            poseLandmarkerHelperListener?.onError("Pose detect 실패.", OTHER_ERROR)
            return null
        }
        return ResultBundle(listOf(result), SystemClock.uptimeMillis() - start, image.height, image.width)
    }

    fun detectVideoFile(videoUri: Uri, intervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException("VIDEO 모드가 아닙니다.")
        }
        val start = SystemClock.uptimeMillis()
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLen = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
        val first = retriever.getFrameAtTime(0)
        val width = first?.width; val height = first?.height
        if (videoLen == null || width == null || height == null) return null

        val results = mutableListOf<PoseLandmarkerResult>()
        val frames = videoLen / intervalMs
        for (i in 0..frames) {
            val tsMs = i * intervalMs
            val frame = retriever.getFrameAtTime(tsMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
            val bmp = if (frame.config == Bitmap.Config.ARGB_8888) frame else frame.copy(Bitmap.Config.ARGB_8888, false)
            poseLandmarker?.detectForVideo(BitmapImageBuilder(bmp).build(), tsMs)?.let { results.add(it) }
        }
        retriever.release()
        val dur = (SystemClock.uptimeMillis() - start) / (frames.coerceAtLeast(1))
        return ResultBundle(results, dur, height, width)
    }

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val inf = SystemClock.uptimeMillis() - result.timestampMs()
        poseLandmarkerHelperListener?.onResults(
            ResultBundle(listOf(result), inf, input.height, input.width)
        )
    }
    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(error.message ?: "Unknown error", OTHER_ERROR)
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int)
        fun onResults(resultBundle: ResultBundle)
    }
}
