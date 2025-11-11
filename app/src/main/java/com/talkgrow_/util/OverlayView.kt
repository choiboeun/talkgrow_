// android_app/src/main/java/com/talkgrow_/util/OverlayView.kt
package com.talkgrow_.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.min

class OverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var mirrorX: Boolean = true
    private var srcW: Int = 0
    private var srcH: Int = 0

    private var handRes: HandLandmarkerResult? = null
    private var poseRes: PoseLandmarkerResult? = null
    private var badgeText: String = ""

    private val drawMatrix = Matrix()
    private val tmpPt = FloatArray(2)

    private val handPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; style = Paint.Style.FILL; strokeWidth = 6f
    }
    private val handLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val posePointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; style = Paint.Style.FILL; strokeWidth = 6f
    }
    private val poseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000; style = Paint.Style.FILL
    }

    private val HAND_CONNECTIONS = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        0 to 9, 9 to 10, 10 to 11, 11 to 12,
        0 to 13, 13 to 14, 14 to 15, 15 to 16,
        0 to 17, 17 to 18, 18 to 19, 19 to 20,
        5 to 9, 9 to 13, 13 to 17
    )

    private val P = object {
        val L_SHOULDER = 11; val R_SHOULDER = 12
        val L_ELBOW = 13; val R_ELBOW = 14
        val L_WRIST = 15; val R_WRIST = 16
        val L_HIP = 23; val R_HIP = 24
        val L_KNEE = 25; val R_KNEE = 26
        val L_ANKLE = 27; val R_ANKLE = 28
    }
    private val POSE_CONNECTIONS = arrayOf(
        P.L_SHOULDER to P.R_SHOULDER,
        P.L_HIP to P.R_HIP,
        P.L_SHOULDER to P.L_ELBOW, P.L_ELBOW to P.L_WRIST,
        P.R_SHOULDER to P.R_ELBOW, P.R_ELBOW to P.R_WRIST,
        P.L_SHOULDER to P.L_HIP, P.R_SHOULDER to P.R_HIP,
        P.L_HIP to P.L_KNEE, P.L_KNEE to P.L_ANKLE,
        P.R_HIP to P.R_KNEE, P.R_KNEE to P.R_ANKLE
    )

    init { setWillNotDraw(false) }

    fun setSourceSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        srcW = w; srcH = h
        computeMatrix(width, height)
        invalidate()
    }

    fun setBadge(text: String?) {
        badgeText = text?.takeIf { it.isNotBlank() } ?: ""
        invalidate()
    }

    fun setHandResult(res: HandLandmarkerResult?) { handRes = res; invalidate() }
    fun setPoseResult(res: PoseLandmarkerResult?) { poseRes = res; invalidate() }
    fun attachTo(@Suppress("UNUSED_PARAMETER") previewView: View) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeMatrix(w, h)
    }

    private fun computeMatrix(vw: Int, vh: Int) {
        if (srcW == 0 || srcH == 0 || vw == 0 || vh == 0) return
        val s = min(vw.toFloat() / srcW, vh.toFloat() / srcH) // FIT(레터박스)
        val drawW = srcW * s
        val drawH = srcH * s
        val offX = (vw - drawW) * 0.5f
        val offY = (vh - drawH) * 0.5f

        drawMatrix.reset()
        if (mirrorX) {
            drawMatrix.postScale(-s, s)
            drawMatrix.postTranslate(vw - offX, offY)
        } else {
            drawMatrix.postScale(s, s)
            drawMatrix.postTranslate(offX, offY)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (srcW == 0 || srcH == 0) return

        poseRes?.let { res ->
            val lists = res.landmarks()
            if (lists.isNotEmpty()) {
                val lms = lists[0]
                for ((a, b) in POSE_CONNECTIONS) {
                    if (a in lms.indices && b in lms.indices) {
                        val p1 = mapNorm(lms[a].x(), lms[a].y())
                        val p2 = mapNorm(lms[b].x(), lms[b].y())
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, poseLinePaint)
                    }
                }
                for (lm in lms) {
                    val p = mapNorm(lm.x(), lm.y())
                    canvas.drawCircle(p.x, p.y, 6f, posePointPaint)
                }
            }
        }

        handRes?.let { res ->
            for (hand in res.landmarks()) {
                for ((a, b) in HAND_CONNECTIONS) {
                    if (a in hand.indices && b in hand.indices) {
                        val p1 = mapNorm(hand[a].x(), hand[a].y())
                        val p2 = mapNorm(hand[b].x(), hand[b].y())
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, handLinePaint)
                    }
                }
                for (lm in hand) {
                    val p = mapNorm(lm.x(), lm.y())
                    canvas.drawCircle(p.x, p.y, 7f, handPointPaint)
                }
            }
        }

        if (badgeText.isNotBlank()) {
            val pad = 16f
            val textW = badgePaint.measureText(badgeText)
            val fm = badgePaint.fontMetrics
            val textH = fm.bottom - fm.top
            val left = pad
            val top = pad
            val right = left + textW + pad * 2
            val bottom = top + textH + pad * 1.5f
            canvas.drawRoundRect(RectF(left, top, right, bottom), 18f, 18f, badgeBg)
            canvas.drawText(badgeText, left + pad, bottom - pad * 0.6f, badgePaint)
        }
    }

    private data class Pt(val x: Float, val y: Float)

    private fun mapNorm(xNorm: Float, yNorm: Float): Pt {
        tmpPt[0] = xNorm * srcW
        tmpPt[1] = yNorm * srcH
        drawMatrix.mapPoints(tmpPt)
        return Pt(tmpPt[0], tmpPt[1])
    }
}
