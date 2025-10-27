package com.talkgrow_.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

class OverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    // 외부에서 설정
    var mirrorX: Boolean = true
    private var srcW: Int = 0
    private var srcH: Int = 0

    // MediaPipe 결과
    private var handRes: HandLandmarkerResult? = null
    private var poseRes: PoseLandmarkerResult? = null

    // 배지
    private var badgeText: String = ""

    // 좌표변환 (FILL_CENTER + 미러)
    private val drawMatrix = Matrix()
    private val tmpPt = FloatArray(2)

    // 점/선 페인트
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

    // 손 연결 정의 (MediaPipe Hands)
    // 0:WRIST, 1:THUMB_CMC, 2:THUMB_MCP, 3:THUMB_IP, 4:THUMB_TIP,
    // 5:INDEX_MCP,6:INDEX_PIP,7:INDEX_DIP,8:INDEX_TIP,
    // 9:MIDDLE_MCP,10:MIDDLE_PIP,11:MIDDLE_DIP,12:MIDDLE_TIP,
    // 13:RING_MCP,14:RING_PIP,15:RING_DIP,16:RING_TIP,
    // 17:PINKY_MCP,18:PINKY_PIP,19:PINKY_DIP,20:PINKY_TIP
    private val HAND_CONNECTIONS = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,                 // 엄지
        0 to 5, 5 to 6, 6 to 7, 7 to 8,                 // 검지
        0 to 9, 9 to 10, 10 to 11, 11 to 12,            // 중지
        0 to 13, 13 to 14, 14 to 15, 15 to 16,          // 약지
        0 to 17, 17 to 18, 18 to 19, 19 to 20,          // 소지
        5 to 9, 9 to 13, 13 to 17                        // 손등 크로스
    )

    // 포즈 연결(간결 버전): 몸통/팔/다리 주요 연결
    // MediaPipe Pose의 인덱스 기준 (0..32) - 주요부위만 그립니다.
    private val P = object {
        val NOSE = 0; val L_EYE = 2; val R_EYE = 5
        val L_EAR = 7; val R_EAR = 8
        val L_SHOULDER = 11; val R_SHOULDER = 12
        val L_ELBOW = 13; val R_ELBOW = 14
        val L_WRIST = 15; val R_WRIST = 16
        val L_HIP = 23; val R_HIP = 24
        val L_KNEE = 25; val R_KNEE = 26
        val L_ANKLE = 27; val R_ANKLE = 28
    }
    private val POSE_CONNECTIONS = arrayOf(
        // 상체
        P.L_SHOULDER to P.R_SHOULDER,
        P.L_HIP to P.R_HIP,
        P.L_SHOULDER to P.L_ELBOW, P.L_ELBOW to P.L_WRIST,
        P.R_SHOULDER to P.R_ELBOW, P.R_ELBOW to P.R_WRIST,
        P.L_SHOULDER to P.L_HIP, P.R_SHOULDER to P.R_HIP,
        // 하체
        P.L_HIP to P.L_KNEE, P.L_KNEE to P.L_ANKLE,
        P.R_HIP to P.R_KNEE, P.R_KNEE to P.R_ANKLE
    )

    init { setWillNotDraw(false) }

    fun setSourceSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        srcW = w; srcH = h
        computeMatrix(width, height); invalidate()
    }

    fun setBadge(text: String) { badgeText = text; invalidate() }
    fun setHandResult(res: HandLandmarkerResult?) { handRes = res; invalidate() }
    fun setPoseResult(res: PoseLandmarkerResult?) { poseRes = res; invalidate() }
    fun attachTo(previewView: View) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeMatrix(w, h)
    }

    private fun computeMatrix(vw: Int, vh: Int) {
        if (srcW == 0 || srcH == 0 || vw == 0 || vh == 0) return
        val s = max(vw.toFloat() / srcW, vh.toFloat() / srcH)   // FILL_CENTER
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

        // ===== Pose: 선 → 점 순서로 =====
        poseRes?.let { res ->
            val lists = res.landmarks()
            if (lists.isNotEmpty()) {
                val lms = lists[0]
                // 선
                for ((a,b) in POSE_CONNECTIONS) {
                    if (a in lms.indices && b in lms.indices) {
                        val p1 = mapNorm(lms[a].x(), lms[a].y())
                        val p2 = mapNorm(lms[b].x(), lms[b].y())
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, poseLinePaint)
                    }
                }
                // 점
                for (lm in lms) {
                    val p = mapNorm(lm.x(), lm.y())
                    canvas.drawCircle(p.x, p.y, 6f, posePointPaint)
                }
            }
        }

        // ===== Hand: 선 → 점 순서로 =====
        handRes?.let { res ->
            val hands = res.landmarks()
            for (hand in hands) {
                // 선
                for ((a,b) in HAND_CONNECTIONS) {
                    if (a in hand.indices && b in hand.indices) {
                        val p1 = mapNorm(hand[a].x(), hand[a].y())
                        val p2 = mapNorm(hand[b].x(), hand[b].y())
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, handLinePaint)
                    }
                }
                // 점
                for (lm in hand) {
                    val p = mapNorm(lm.x(), lm.y())
                    canvas.drawCircle(p.x, p.y, 7f, handPointPaint)
                }
            }
        }

        // ===== Badge =====
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

    /** 정규화(0..1) → 원본픽셀 → View좌표 */
    private fun mapNorm(xNorm: Float, yNorm: Float): Pt {
        tmpPt[0] = xNorm * srcW
        tmpPt[1] = yNorm * srcH
        drawMatrix.mapPoints(tmpPt)
        return Pt(tmpPt[0], tmpPt[1])
    }
}