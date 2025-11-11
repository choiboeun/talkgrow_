package com.talkgrow_

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.talkgrow_.util.KP
import com.talkgrow_.util.OverlayView
import com.talkgrow_.util.PoseLandmarkerHelper
import com.talkgrow_.util.HandLandmarkerHelper
import com.talkgrow_.util.YuvToRgb
import kotlin.math.abs

class SafeImageAnalyzer(
    private val context: Context,
    private val overlay: OverlayView,
    private val onHandsCount: (Int) -> Unit,
    private val onAnyResult: (Boolean?) -> Unit, // true=움직임/손 보임, false=정지/손 내림, null=오류
    private val onLandmarksFrame: (
        pose33: List<KP>,
        left21: List<KP>,
        right21: List<KP>,
        handsVisible: Boolean,
        mirroredPreview: Boolean,
        ts: Long
    ) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "SafeImageAnalyzer"
        private const val LOG_VERBOSE = false
        private const val L_SHOULDER_IDX = 11
        private const val R_SHOULDER_IDX = 12

        // 움직임 감지(히스테리시스 약간 완화)
        private const val EMA_ALPHA = 0.20f
        private const val MOVE_ON  = 0.007f
        private const val MOVE_OFF = 0.0035f
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayMirrored: Boolean = true
    fun isMirrored(): Boolean = overlayMirrored
    fun setMirror(isFront: Boolean) {
        overlayMirrored = isFront
        mainHandler.post { overlay.mirrorX = isFront }
    }

    @Volatile private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        poseHelper.setup()
        handHelper.setup()
        lastPose = null
        lastHands = null
        lastPoseTimeMs = 0L
        lastHandsTimeMs = 0L
        lastTsMs = 0L

        // 배지 비활성화
        mainHandler.post { overlay.setBadge(null) }

        prevFeat = null
        emaEnergy = 0f
        moving = false
        lastHandsVisibleTs = 0L
    }

    fun shutdown() {
        isRunning = false
        runCatching { handHelper.close() }
        runCatching { poseHelper.close() }
        runCatching { yuv.close() }
        baseBitmap?.recycle()
        baseBitmap = null
        srcW = 0; srcH = 0
        lastPose = null
        lastHands = null
    }

    private val yuv = YuvToRgb()
    private var baseBitmap: Bitmap? = null
    private var srcW: Int = 0
    private var srcH: Int = 0

    @Volatile private var lastPose: List<KP>? = null
    @Volatile private var lastHands: Pair<List<KP>, List<KP>>? = null
    @Volatile private var lastPoseTimeMs: Long = 0L
    @Volatile private var lastHandsTimeMs: Long = 0L
    private val JOIN_TOL_MS = 120L

    // 오버레이 스무딩(표시용). 트리거는 raw 가시성 사용.
    private val HAND_SMOOTH_MS = 1200L
    private var lastHandsVisibleTs = 0L

    private var prevFeat: FloatArray? = null
    private var emaEnergy = 0f
    private var moving = false

    private var lastTsMs = 0L
    private fun nextMonotonicTsMs(): Long {
        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTsMs) ts = lastTsMs + 1
        lastTsMs = ts
        return ts
    }

    private fun maybeSwapByShoulder(pose: List<KP>?, left: List<KP>, right: List<KP>): Pair<List<KP>, List<KP>> {
        if (pose == null || pose.size <= R_SHOULDER_IDX) return left to right
        if (left.isEmpty() || right.isEmpty()) return left to right
        val lShoulderX = pose[L_SHOULDER_IDX].x
        val rShoulderX = pose[R_SHOULDER_IDX].x
        return if (lShoulderX > rShoulderX) right to left else left to right
    }

    private fun buildFeature(pose: List<KP>, left: List<KP>, right: List<KP>): FloatArray {
        fun toXY(src: List<KP>) = src.flatMap { listOf(it.x, it.y) }
        val f = ArrayList<Float>(66 + 42 + 42)
        f += toXY(pose); f += toXY(left); f += toXY(right)
        return f.toFloatArray()
    }

    private fun updateMotionEnergy(cur: FloatArray): Boolean {
        val prev = prevFeat
        val inst = if (prev == null) 0f else {
            val n = kotlin.math.min(prev.size, cur.size)
            var sum = 0f
            for (i in 0 until n) sum += kotlin.math.abs(cur[i] - prev[i])
            sum / n
        }
        emaEnergy = (1 - EMA_ALPHA) * emaEnergy + EMA_ALPHA * inst
        prevFeat = cur
        moving = if (!moving) emaEnergy > MOVE_ON else emaEnergy > MOVE_OFF
        return moving
    }

    @Synchronized
    private fun tryEmitLatest() {
        val pose = lastPose ?: return
        val hands = lastHands ?: return
        if (abs(lastPoseTimeMs - lastHandsTimeMs) > JOIN_TOL_MS) return

        var (left21, right21) = hands
        val swapped = maybeSwapByShoulder(pose, left21, right21)
        left21 = swapped.first
        right21 = swapped.second

        val hvRaw = left21.isNotEmpty() || right21.isNotEmpty() // 트리거는 이 값으로
        val now = SystemClock.uptimeMillis()
        if (hvRaw) lastHandsVisibleTs = now
        val hvSmoothForUI = hvRaw || (now - lastHandsVisibleTs) <= HAND_SMOOTH_MS

        // ---- 핵심: 손 미가시 즉시 '정지' 발신 + 모션에너지 리셋 → 즉시 disarm ----
        if (!hvRaw) {
            prevFeat = null      // 이전 프레임과의 차이 0으로 만들어 즉시 정지 상태 유지
            emaEnergy = 0f
            moving = false
            onAnyResult(false)   // 바로 정지 이벤트
        } else {
            // 손이 보일 때는 모션 기반
            val feat = buildFeature(pose, left21, right21)
            val isMovingNow = updateMotionEnergy(feat)
            onAnyResult(isMovingNow || hvRaw) // 손 보이면 true
        }

        val tsOut = nextMonotonicTsMs()
        if (LOG_VERBOSE) Log.d(TAG, "emit ts=$tsOut hvRaw=$hvRaw")
        onLandmarksFrame(pose, left21, right21, hvSmoothForUI, /*mirroredPreview=*/false, tsOut)
    }

    private val handHelper = HandLandmarkerHelper(
        context = context,
        onResult = { res: HandLandmarkerResult, _: MPImage ->
            if (!isRunning) return@HandLandmarkerHelper

            mainHandler.post {
                overlay.setHandResult(res)
                overlay.postInvalidateOnAnimation()
            }

            val hands = res.landmarks()
            val handed = res.handednesses()
            val count = hands.size
            onHandsCount(count)

            var leftList: List<KP> = emptyList()
            var rightList: List<KP> = emptyList()
            for (i in hands.indices) {
                val label = handed.getOrNull(i)?.firstOrNull()?.categoryName()?.lowercase() ?: ""
                val xy = hands[i].map { KP(it.x(), it.y()) }
                when (label) {
                    "left" -> leftList = xy
                    "right" -> rightList = xy
                    else -> {
                        if (leftList.isEmpty()) leftList = xy
                        else if (rightList.isEmpty()) rightList = xy
                    }
                }
            }

            val tsNow = SystemClock.uptimeMillis()
            synchronized(this) {
                lastHands = leftList to rightList
                lastHandsTimeMs = tsNow
            }
            tryEmitLatest()
        },
        onError = { e: Throwable ->
            Log.e(TAG, "HandLandmarker error", e)
            onAnyResult(null)
        }
    )

    private val poseHelper = PoseLandmarkerHelper(
        context = context,
        onResult = { res: PoseLandmarkerResult, _: Any?, w: Int, h: Int ->
            if (!isRunning) return@PoseLandmarkerHelper

            val lms = res.landmarks().firstOrNull()
            val pose = lms?.map { KP(it.x(), it.y()) } ?: emptyList()

            mainHandler.post {
                overlay.setPoseResult(res)
                if (w != srcW || h != srcH) {
                    srcW = w; srcH = h
                    overlay.setSourceSize(srcW, srcH)
                }
                overlay.postInvalidateOnAnimation()
            }

            val tsNow = SystemClock.uptimeMillis()
            synchronized(this) {
                lastPose = pose
                lastPoseTimeMs = tsNow
            }
            tryEmitLatest()
        },
        onError = { e: Throwable -> Log.e(TAG, "PoseLandmarker error", e) }
    )

    private fun ensureBaseBitmap(w: Int, h: Int) {
        val needNew = baseBitmap?.let { it.width != w || it.height != h } ?: true
        if (needNew) {
            baseBitmap?.recycle()
            baseBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            srcW = w; srcH = h
            mainHandler.post { overlay.setSourceSize(w, h) }
        }
    }

    private fun rotateBitmapIfNeeded(src: Bitmap, deg: Int): Bitmap {
        if (deg == 0) return src
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        if (!isRunning) { image.close(); return }
        try {
            val originalBitmap = image.toBitmap()
            val rotDeg = image.imageInfo.rotationDegrees
            val uprightBitmap = rotateBitmapIfNeeded(originalBitmap, rotDeg)

            if (uprightBitmap.width != srcW || uprightBitmap.height != srcH) {
                srcW = uprightBitmap.width
                srcH = uprightBitmap.height
                mainHandler.post { overlay.setSourceSize(srcW, srcH) }
            }

            val mpImage: MPImage = BitmapImageBuilder(uprightBitmap).build()
            val tsNow = SystemClock.uptimeMillis()
            handHelper.detectAsync(mpImage, 0, tsNow)
            poseHelper.detectAsync(mpImage, 0, tsNow)

            if (uprightBitmap !== originalBitmap) uprightBitmap.recycle()
        } catch (t: Throwable) {
            Log.e(TAG, "analyze() failed", t)
            onAnyResult(null)
        } finally {
            image.close()
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        ensureBaseBitmap(width, height)
        val bitmap = baseBitmap ?: throw IllegalStateException("Bitmap buffer unavailable")
        yuv.yuvToRgb(this, bitmap)
        return bitmap
    }
}
