package com.talkgrow_

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.talkgrow_.util.HandLandmarkerHelper
import com.talkgrow_.util.OverlayView
import com.talkgrow_.util.PoseLandmarkerHelper
import com.talkgrow_.util.YuvToRgbConverter
import java.nio.ByteBuffer

class SafeImageAnalyzer(
    private val context: Context,
    private val overlay: OverlayView,
    private val onHandsCount: (Int) -> Unit,
    private val onAnyResult: (Boolean?) -> Unit
) {
    companion object {
        private const val TAG = "SafeImageAnalyzer"
        private const val TAG_CONV = "FrameConv"
    }

    private var lastSrcW = 0
    private var lastSrcH = 0

    private val yuvConverter by lazy { YuvToRgbConverter(context) }

    private val hand = HandLandmarkerHelper(
        context,
        onResult = { res, _ ->
            overlay.setHandResult(res, lastSrcW, lastSrcH)
            overlay.postInvalidateOnAnimation()
            onHandsCount(res.landmarks().size)
            onAnyResult(res.landmarks().isNotEmpty())
        },
        onError = { t ->
            Log.e("HandLMHelper", "error", t)
            onAnyResult(null)
        }
    )

    private val pose = PoseLandmarkerHelper(
        context,
        onResult = { res, _, _, _ ->
            overlay.setPoseResult(res, lastSrcW, lastSrcH)
            overlay.postInvalidateOnAnimation()
        },
        onError = { t -> Log.e("PoseLMHelper", "error", t) }
    )

    fun setMirror(v: Boolean) { overlay.mirrorX = v }
    fun start() { hand.setup(); pose.setup() }
    fun close() { hand.close(); pose.clear(); yuvConverter.release() }

    /** RGBA → Bitmap */
    private fun rgbaToBitmap(image: ImageProxy): Bitmap {
        val w = image.width
        val h = image.height
        val buf: ByteBuffer = image.planes[0].buffer
        buf.rewind()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buf)
        return bmp
    }

    /** YUV → Bitmap */
    private fun yuvToBitmap(image: ImageProxy): Bitmap {
        val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        yuvConverter.yuvToRgb(image, bmp)
        return bmp
    }

    /** CameraX의 rotationDegrees를 실제 비트맵에 직접 반영 (한 번만 회전!) */
    private fun rotateBitmap(src: Bitmap, rotationDeg: Int): Bitmap {
        if (rotationDeg == 0) return src
        val m = Matrix().apply { postRotate(rotationDeg.toFloat()) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        src.recycle()
        return rotated
    }

    fun analyze(image: ImageProxy) {
        val ts = SystemClock.uptimeMillis()
        val camRot = image.imageInfo.rotationDegrees // 0, 90, 180, 270
        try {
            // 1️⃣ 입력 프레임을 비트맵으로 변환
            val bmp: Bitmap = when (image.format) {
                PixelFormat.RGBA_8888 -> rgbaToBitmap(image)
                ImageFormat.YUV_420_888 -> yuvToBitmap(image)
                else -> yuvToBitmap(image)
            }

            // 2️⃣ 회전 적용 (우리가 직접 회전)
            val uprightBmp = rotateBitmap(bmp, camRot)

            // 3️⃣ 오버레이 크기 갱신
            lastSrcW = uprightBmp.width
            lastSrcH = uprightBmp.height
            overlay.setSourceSize(lastSrcW, lastSrcH)

            // 4️⃣ MediaPipe에 rotation=0 (이미 우리가 회전했으니까!)
            val mpImage: MPImage = BitmapImageBuilder(uprightBmp).build()
            hand.detectAsync(mpImage, 0, ts)
            pose.detectAsync(mpImage, 0, ts)

        } catch (t: Throwable) {
            Log.e(TAG, "analyze failed", t)
            onAnyResult(null)
        } finally {
            image.close()
        }
    }
}
