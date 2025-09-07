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
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceLandmarkerHelper(
    var minFaceDetectionConfidence: Float = DEFAULT_FACE_DETECTION_CONFIDENCE,
    var minFaceTrackingConfidence: Float = DEFAULT_FACE_TRACKING_CONFIDENCE,
    var minFacePresenceConfidence: Float = DEFAULT_FACE_PRESENCE_CONFIDENCE,
    var maxNumFaces: Int = DEFAULT_NUM_FACES,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    private val context: Context,
    private val faceLandmarkerHelperListener: LandmarkerListener? = null
) {

    private var faceLandmarker: FaceLandmarker? = null

    init { setupFaceLandmarker() }

    fun clearFaceLandmarker() { faceLandmarker?.close(); faceLandmarker = null }
    fun isClose(): Boolean = faceLandmarker == null

    fun setupFaceLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
        }
        baseOptionBuilder.setModelAssetPath(MP_FACE_LANDMARKER_TASK)

        if (runningMode == RunningMode.LIVE_STREAM && faceLandmarkerHelperListener == null) {
            throw IllegalStateException("faceLandmarkerHelperListener must be set when runningMode is LIVE_STREAM.")
        }

        try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptionBuilder.build())
                .setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                .setMinTrackingConfidence(minFaceTrackingConfidence)
                .setMinFacePresenceConfidence(minFacePresenceConfidence)
                .setNumFaces(maxNumFaces)
                .setOutputFaceBlendshapes(false)
                .setRunningMode(runningMode)
                .apply {
                    if (runningMode == RunningMode.LIVE_STREAM) {
                        setResultListener(this@FaceLandmarkerHelper::returnLivestreamResult)
                        setErrorListener(this@FaceLandmarkerHelper::returnLivestreamError)
                    }
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            faceLandmarkerHelperListener?.onError("Face Landmarker init failed. See logs.", OTHER_ERROR)
            Log.e(TAG, "Task load error: ${e.message}")
        } catch (e: RuntimeException) {
            faceLandmarkerHelperListener?.onError("Face Landmarker init failed. See logs.", GPU_ERROR)
            Log.e(TAG, "Model load error: ${e.message}")
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException("detectLiveStream is only for LIVE_STREAM mode.")
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
        val rotated = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotated).build()
        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        faceLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun detectVideoFile(videoUri: Uri, intervalMs: Long): VideoResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException("detectVideoFile is only for VIDEO mode.")
        }
        val start = SystemClock.uptimeMillis()
        var errored = false

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLong()
        val first = retriever.getFrameAtTime(0)
        val w = first?.width
        val h = first?.height
        if (videoLengthMs == null || w == null || h == null) return null

        val results = mutableListOf<FaceLandmarkerResult>()
        val frames = videoLengthMs / intervalMs

        for (i in 0..frames) {
            val ts = i * intervalMs
            retriever.getFrameAtTime(ts * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { f ->
                val argb = if (f.config == Bitmap.Config.ARGB_8888) f else f.copy(Bitmap.Config.ARGB_8888, false)
                val mp = BitmapImageBuilder(argb).build()
                faceLandmarker?.detectForVideo(mp, ts)?.let { r -> results.add(r) } ?: run {
                    errored = true
                    faceLandmarkerHelperListener?.onError("Null result in detectVideoFile", OTHER_ERROR)
                }
            } ?: run {
                errored = true
                faceLandmarkerHelperListener?.onError("Frame fetch failed in video.", OTHER_ERROR)
            }
        }
        retriever.release()

        val perFrame = (SystemClock.uptimeMillis() - start) / frames.coerceAtLeast(1)
        return if (errored) null else VideoResultBundle(results, perFrame, h, w)
    }

    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException("detectImage is only for IMAGE mode.")
        }
        val start = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(image).build()
        faceLandmarker?.detect(mpImage)?.also { res ->
            val t = SystemClock.uptimeMillis() - start
            return ResultBundle(res, t, image.height, image.width)
        }
        faceLandmarkerHelperListener?.onError("Face Landmarker failed to detect.", OTHER_ERROR)
        return null
    }

    private fun returnLivestreamResult(result: FaceLandmarkerResult, input: MPImage) {
        if (result.faceLandmarks().isNotEmpty()) {
            val t = SystemClock.uptimeMillis() - result.timestampMs()
            faceLandmarkerHelperListener?.onResults(
                ResultBundle(result, t, input.height, input.width)
            )
        } else {
            faceLandmarkerHelperListener?.onEmpty()
        }
    }

    private fun returnLivestreamError(error: RuntimeException) {
        faceLandmarkerHelperListener?.onError(error.message ?: "Unknown error", OTHER_ERROR)
    }

    companion object {
        private const val MP_FACE_LANDMARKER_TASK = "face_landmarker.task"
        const val TAG = "FaceLandmarkerHelper"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_FACE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_FACE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_FACE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_FACES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val result: FaceLandmarkerResult,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    data class VideoResultBundle(
        val results: List<FaceLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int)
        fun onResults(resultBundle: ResultBundle)
        fun onEmpty() {}
    }
}
