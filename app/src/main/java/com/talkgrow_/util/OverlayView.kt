package com.talkgrow_.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class OverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    /** 전면 카메라일 때 true */
    var mirrorX: Boolean = true

    private var handLms: List<List<NormalizedLandmark>>? = null
    private var poseLms: List<NormalizedLandmark>? = null
    private var srcW = 0
    private var srcH = 0

    // ✅ 손/몸 모두 동일 색(노란색)으로 통일
    private val commonLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.YELLOW
    }
    private val commonPoint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeWidth = 6f
        color = Color.YELLOW
    }

    fun setSourceSize(w: Int, h: Int) { if (w > 0 && h > 0) { srcW = w; srcH = h } }
    fun setHandResult(res: HandLandmarkerResult, w: Int, h: Int) { handLms = res.landmarks(); srcW = w; srcH = h }
    fun setPoseResult(res: PoseLandmarkerResult, w: Int, h: Int) { poseLms = res.landmarks().firstOrNull(); srcW = w; srcH = h }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d("OverlayView", "onDraw view=${width}x${height} src=${srcW}x${srcH}")
        if (srcW <= 0 || srcH <= 0) return

        val vw = width.toFloat().coerceAtLeast(1f)
        val vh = height.toFloat().coerceAtLeast(1f)

        // PreviewView(FILL_CENTER)와 동일한 letterbox 스케일
        val scale = minOf(vw / srcW, vh / srcH)
        val contentW = srcW * scale
        val contentH = srcH * scale
        val dx = (vw - contentW) / 2f
        val dy = (vh - contentH) / 2f

        fun mapX(x01: Float): Float {
            val xInBox = x01 * srcW * scale
            return dx + if (mirrorX) (contentW - xInBox) else xInBox
        }
        fun mapY(y01: Float): Float = dy + (y01 * srcH) * scale

        // Pose
        poseLms?.let { list ->
            val conn = arrayOf(
                intArrayOf(11,12), intArrayOf(11,13), intArrayOf(13,15),
                intArrayOf(12,14), intArrayOf(14,16),
                intArrayOf(11,23), intArrayOf(12,24), intArrayOf(23,24)
            )
            for (e in conn) {
                val a = e[0]; val b = e[1]
                if (a < list.size && b < list.size) {
                    val pa = list[a]; val pb = list[b]
                    canvas.drawLine(mapX(pa.x()), mapY(pa.y()), mapX(pb.x()), mapY(pb.y()), commonLine)
                }
            }
            for (i in 11..24) if (i < list.size) {
                val p = list[i]; canvas.drawCircle(mapX(p.x()), mapY(p.y()), 5f, commonPoint)
            }
        }

        // Hands (최대 2개)
        handLms?.forEach { lm ->
            val conn = arrayOf(
                intArrayOf(0,1), intArrayOf(1,2), intArrayOf(2,3), intArrayOf(3,4),
                intArrayOf(0,5), intArrayOf(5,6), intArrayOf(6,7), intArrayOf(7,8),
                intArrayOf(0,9), intArrayOf(9,10), intArrayOf(10,11), intArrayOf(11,12),
                intArrayOf(0,13), intArrayOf(13,14), intArrayOf(14,15), intArrayOf(15,16),
                intArrayOf(0,17), intArrayOf(17,18), intArrayOf(18,19), intArrayOf(19,20)
            )
            for (e in conn) {
                val a = e[0]; val b = e[1]
                if (a < lm.size && b < lm.size) {
                    val pa = lm[a]; val pb = lm[b]
                    canvas.drawLine(mapX(pa.x()), mapY(pa.y()), mapX(pb.x()), mapY(pb.y()), commonLine)
                }
            }
            lm.forEach { p -> canvas.drawCircle(mapX(p.x()), mapY(p.y()), 5f, commonPoint) }
        }
    }
}
