// com/talkgrow_/util/YuvToRgbConverter.kt (새 파일)
package com.talkgrow_.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class YuvToRgbConverter(context: Context) {
    private var cachedBitmap: Bitmap? = null
    fun yuvToRgb(image: ImageProxy, output: Bitmap): Bitmap {
        // AndroidX CameraX 내부 구현을 참고한 경량 변환기 (빠른 버전)
        // 핵심: Image.Plane 버퍼를 한 번에 RGB 로 변환
        // 간단화를 위해 android.graphics.YuvImage를 쓰지 않고, 행 단위로 복사
        // 아래는 최소 구현(성능 좋은 외부 유틸 쓰셔도 OK)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        // 빠르게 가려면, RenderScript/ScriptIntrinsicYuvToRGB 대신
        // libyuv 등을 쓰는게 최적. 여기선 간단화(정확도 OK, 속도 충분)
        // 참고: 프로덕션에선 https://developer.android.com/media/camera/camerax を 참고

        val width = image.width
        val height = image.height
        val argb = IntArray(width * height)

        var yp = 0
        for (j in 0 until height) {
            val pY = yRowStride * j
            val pUV = uvRowStride * (j shr 1)
            for (i in 0 until width) {
                val y = (yBuffer.get(pY + i).toInt() and 0xFF)
                val uvOffset = pUV + (i shr 1) * uvPixelStride
                val u = (uBuffer.get(uvOffset).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvOffset).toInt() and 0xFF) - 128

                // YUV -> RGB (BT.601)
                var r = (y + 1.402f * v).toInt()
                var g = (y - 0.344136f * u - 0.714136f * v).toInt()
                var b = (y + 1.772f * u).toInt()

                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255

                argb[yp++] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }
        output.setPixels(argb, 0, width, 0, 0, width, height)
        return output
    }
}
