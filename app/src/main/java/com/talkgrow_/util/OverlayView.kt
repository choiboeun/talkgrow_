package com.talkgrow_.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.talkgrow_.R

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    enum class ScaleMode { FIT, FILL } // 기본 FIT 사용

    data class DrawOptions(
        var showHands: Boolean = true,
        var showPose: Boolean = true,
        var showFace: Boolean = true,
        var poseUpperOnly: Boolean = true,
        var unifyColors: Boolean = true
    )

    private var opts = DrawOptions()
    private var scaleMode: ScaleMode = ScaleMode.FIT  // 기본값: FIT

    fun setDrawOptions(newOpts: DrawOptions) { opts = newOpts; invalidate() }
    fun setScaleMode(mode: ScaleMode) { scaleMode = mode; invalidate() }

    private var handResults: HandLandmarkerResult? = null
    private var poseResults: PoseLandmarkerResult? = null
    private var faceResults: FaceLandmarkerResult? = null

    private val linePaint = Paint()
    private val pointPaint = Paint()
    private val handLinePaint = Paint()
    private val handPointPaint = Paint()
    private val poseLinePaint = Paint()
    private val posePointPaint = Paint()
    private val facePointPaint = Paint()

    private var runningMode: RunningMode = RunningMode.IMAGE
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val UPPER_BODY_IDX = setOf(0,11,12,13,14,15,16,23,24)

    init { initPaints() }

    fun clear() {
        handResults = null; poseResults = null; faceResults = null
        invalidate()
    }

    private fun initPaints() {
        linePaint.apply {
            color = ContextCompat.getColor(context!!, R.color.mp_color_primary)
            strokeWidth = 8f; style = Paint.Style.STROKE; isAntiAlias = true
        }
        pointPaint.apply {
            color = ContextCompat.getColor(context!!, R.color.mp_color_primary)
            strokeWidth = 8f; style = Paint.Style.FILL; isAntiAlias = true
        }
        handLinePaint.apply { color = ContextCompat.getColor(context!!, R.color.mp_color_primary); strokeWidth = 8f; style = Paint.Style.STROKE; isAntiAlias = true }
        handPointPaint.apply { color = Color.YELLOW; strokeWidth = 8f; style = Paint.Style.FILL; isAntiAlias = true }
        poseLinePaint.apply { color = Color.CYAN; strokeWidth = 6f; style = Paint.Style.STROKE; isAntiAlias = true }
        posePointPaint.apply { color = Color.MAGENTA; strokeWidth = 6f; style = Paint.Style.FILL; isAntiAlias = true }
        facePointPaint.apply { color = Color.WHITE; strokeWidth = 5f; style = Paint.Style.FILL; isAntiAlias = true }
    }

    fun setHandResults(results: HandLandmarkerResult?, imageHeight: Int, imageWidth: Int, runningMode: RunningMode = RunningMode.IMAGE) {
        this.handResults = results
        this.imageHeight = imageHeight.coerceAtLeast(1)
        this.imageWidth = imageWidth.coerceAtLeast(1)
        this.runningMode = runningMode
        invalidate()
    }

    fun setPoseResults(results: PoseLandmarkerResult?, imageHeight: Int, imageWidth: Int, runningMode: RunningMode = RunningMode.IMAGE) {
        this.poseResults = results
        this.imageHeight = imageHeight.coerceAtLeast(1)
        this.imageWidth = imageWidth.coerceAtLeast(1)
        this.runningMode = runningMode
        invalidate()
    }

    fun setFaceResults(results: FaceLandmarkerResult?, imageHeight: Int, imageWidth: Int, runningMode: RunningMode = RunningMode.IMAGE) {
        this.faceResults = results
        this.imageHeight = imageHeight.coerceAtLeast(1)
        this.imageWidth = imageWidth.coerceAtLeast(1)
        this.runningMode = runningMode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 컨테이너가 이미 4:3 이므로 FIT 해도 레터박스가 생기지 않음
        val widthRatio = width * 1f / imageWidth
        val heightRatio = height * 1f / imageHeight
        val scale = if (scaleMode == ScaleMode.FILL) maxOf(widthRatio, heightRatio) else minOf(widthRatio, heightRatio)
        val scaledW = imageWidth * scale
        val scaledH = imageHeight * scale
        val offsetX = (width - scaledW) / 2f
        val offsetY = (height - scaledH) / 2f

        val handL = if (opts.unifyColors) linePaint else handLinePaint
        val handP = if (opts.unifyColors) pointPaint else handPointPaint
        val poseL = if (opts.unifyColors) linePaint else poseLinePaint
        val poseP = if (opts.unifyColors) pointPaint else posePointPaint
        val faceP = if (opts.unifyColors) pointPaint else facePointPaint

        // Hands
        if (opts.showHands) {
            handResults?.landmarks()?.forEach { lmList ->
                lmList.forEach { pt ->
                    canvas.drawCircle(pt.x() * imageWidth * scale + offsetX, pt.y() * imageHeight * scale + offsetY, 8f, handP)
                }
                HandLandmarker.HAND_CONNECTIONS.forEach { conn ->
                    val s = lmList[conn!!.start()]; val e = lmList[conn.end()]
                    canvas.drawLine(
                        s.x() * imageWidth * scale + offsetX, s.y() * imageHeight * scale + offsetY,
                        e.x() * imageWidth * scale + offsetX, e.y() * imageHeight * scale + offsetY,
                        handL
                    )
                }
            }
        }

        // Pose
        if (opts.showPose) {
            poseResults?.landmarks()?.forEach { lmList ->
                lmList.forEachIndexed { idx, pt ->
                    if (!opts.poseUpperOnly || idx in UPPER_BODY_IDX) {
                        canvas.drawCircle(pt.x() * imageWidth * scale + offsetX, pt.y() * imageHeight * scale + offsetY, 6f, poseP)
                    }
                }
                PoseLandmarker.POSE_LANDMARKS.forEach { seg ->
                    val si = seg!!.start(); val ei = seg.end()
                    if (!opts.poseUpperOnly || (si in UPPER_BODY_IDX && ei in UPPER_BODY_IDX)) {
                        val s = lmList[si]; val e = lmList[ei]
                        canvas.drawLine(
                            s.x() * imageWidth * scale + offsetX, s.y() * imageHeight * scale + offsetY,
                            e.x() * imageWidth * scale + offsetX, e.y() * imageHeight * scale + offsetY,
                            poseL
                        )
                    }
                }
            }
        }

        // Face
        if (opts.showFace) {
            faceResults?.faceLandmarks()?.forEach { lmList ->
                lmList.forEach { pt ->
                    canvas.drawCircle(pt.x() * imageWidth * scale + offsetX, pt.y() * imageHeight * scale + offsetY, 4.5f, faceP)
                }
            }
        }
    }
}
