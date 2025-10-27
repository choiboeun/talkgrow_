package com.talkgrow_

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.talkgrow_.util.HandLandmarkerHelper
import com.talkgrow_.util.KP
import com.talkgrow_.util.OverlayView
import com.talkgrow_.util.PoseLandmarkerHelper
import com.talkgrow_.util.YuvToRgb

/**
 * CameraX 프레임 -> (YUV->ARGB) -> MediaPipe Hands/Pose -> Overlay/SegmentPipeline
 * - 비동기 결과를 프레임 단위로 동기화(maybeEmit)하여 빈 입력으로 모델을 호출하지 않음
 */
class SafeImageAnalyzer(
    private val context: Context,
    private val overlay: OverlayView,
    private val onHandsCount: (Int) -> Unit,
    private val onAnyResult: (Boolean?) -> Unit,
    private val onLandmarksFrame: (
        pose33: List<KP>,
        left21: List<KP>,
        right21: List<KP>,
        handsVisible: Boolean,
        ts: Long
    ) -> Unit
) : ImageAnalysis.Analyzer {

    companion object { private const val TAG = "SafeImageAnalyzer" }

    // 외부에서 전면카메라 미러 설정
    fun setMirror(isFront: Boolean) { overlay.mirrorX = isFront }

    // --- lifecycle ---
    fun start() { poseHelper.setup(); handHelper.setup() }
    fun shutdown() {
        runCatching { handHelper.close() }
        runCatching { poseHelper.close() }
        runCatching { yuv.close() }
        baseBitmap?.recycle(); baseBitmap = null
        rotatedBitmap?.recycle(); rotatedBitmap = null
    }

    // 변환기 & 비트맵 버퍼
    private val yuv = YuvToRgb()
    private var baseBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null

    // 소스 크기 (오버레이 좌표계용)
    private var srcW = 0
    private var srcH = 0

    // --- 프레임 동기화 상태 ---
    @Volatile private var gotPose = false
    @Volatile private var gotHands = false

    // 이 프레임에서 파이프라인에 보낼 스냅샷
    @Volatile private var stagePose: List<KP> = emptyList()
    @Volatile private var stageLeft: List<KP> = emptyList()
    @Volatile private var stageRight: List<KP> = emptyList()
    @Volatile private var stageHandsVisible = false
    @Volatile private var stageTs: Long = 0L

    // “인식 중/대기중” 깜빡임 방지 디바운스
    private var lastHandOkMs = 0L
    private val HAND_OK_HOLD_MS = 300L

    // 둘 다 왔을 때만 1회 호출
    @Synchronized
    private fun maybeEmit() {
        if (!(gotPose && gotHands)) return
        val pose = stagePose
        val l = stageLeft
        val r = stageRight
        val hv = stageHandsVisible
        val ts = stageTs

        gotPose = false
        gotHands = false

        onLandmarksFrame(pose, l, r, hv, ts)
    }

    // --- MediaPipe helpers ---
    private val handHelper = HandLandmarkerHelper(
        context = context,
        onResult = { res: HandLandmarkerResult, _: MPImage ->
            overlay.setHandResult(res)
            overlay.postInvalidateOnAnimation()

            // MediaPipe Hands: 결과는 정규화 좌표(0..1)
            val hands = res.landmarks() // List<List<NormalizedLandmark>>
            val count = hands.size
            onHandsCount(count)

            // 디바운스된 UI 상태
            if (count > 0) lastHandOkMs = SystemClock.uptimeMillis()
            val uiOk = (SystemClock.uptimeMillis() - lastHandOkMs) < HAND_OK_HOLD_MS
            onAnyResult(if (uiOk) true else false)

            stageLeft  = hands.getOrNull(0)?.map { lm -> KP(lm.x(), lm.y(), lm.z()) } ?: emptyList()
            stageRight = hands.getOrNull(1)?.map { lm -> KP(lm.x(), lm.y(), lm.z()) } ?: emptyList()
            stageHandsVisible = count > 0
            gotHands = true
            maybeEmit()
        },
        onError = { e: Throwable ->
            Log.e(TAG, "HandLandmarker error", e)
            onAnyResult(null)
        }
    )

    private val poseHelper = PoseLandmarkerHelper(
        context = context,
        onResult = { res: PoseLandmarkerResult, _: Any?, w: Int, h: Int ->
            overlay.setPoseResult(res)
            overlay.postInvalidateOnAnimation()

            val lms = res.landmarks().firstOrNull() // List<NormalizedLandmark>?
            stagePose = lms?.map { lm -> KP(lm.x(), lm.y(), lm.z()) } ?: emptyList()

            if (w != srcW || h != srcH) {
                srcW = w; srcH = h
                overlay.setSourceSize(srcW, srcH)
            }

            gotPose = true
            maybeEmit()
        },
        onError = { e: Throwable ->
            Log.e(TAG, "PoseLandmarker error", e)
        }
    )

    // 비트맵 버퍼 보장
    private fun ensureBaseBitmap(w: Int, h: Int) {
        val needNew = baseBitmap?.let { it.width != w || it.height != h } ?: true
        if (needNew) {
            baseBitmap?.recycle()
            rotatedBitmap?.recycle()
            baseBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            rotatedBitmap = null
            srcW = w; srcH = h
            overlay.setSourceSize(w, h)
        }
    }

    // 회전 적용 비트맵 생성
    private fun rotateBitmap(src: Bitmap, deg: Int): Bitmap {
        if (deg == 0) return src
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    override fun analyze(image: ImageProxy) {
        val ts: Long = SystemClock.uptimeMillis()
        stageTs = ts

        try {
            val w = image.width
            val h = image.height
            ensureBaseBitmap(w, h)

            val base = baseBitmap!!
            // 1) YUV -> ARGB
            yuv.yuvToRgb(image, base)

            // 2) 회전 보정
            val rotDeg: Int = image.imageInfo.rotationDegrees
            val upright: Bitmap = if (rotDeg == 0) base else rotateBitmap(base, rotDeg)

            // 3) 소스 크기 갱신
            if (upright.width != srcW || upright.height != srcH) {
                srcW = upright.width
                srcH = upright.height
                overlay.setSourceSize(srcW, srcH)
            }

            // 4) MPImage (회전 반영했으므로 rotation=0)
            val mpImage: MPImage = BitmapImageBuilder(upright).build()

            // 5) 두 태스크에 비동기 요청
            handHelper.detectAsync(mpImage, /*rotationDeg=*/0, ts)
            poseHelper.detectAsync(mpImage, /*rotationDeg=*/0, ts)

        } catch (t: Throwable) {
            Log.e(TAG, "analyze() failed", t)
            onAnyResult(null)
        } finally {
            image.close()
        }
    }
}