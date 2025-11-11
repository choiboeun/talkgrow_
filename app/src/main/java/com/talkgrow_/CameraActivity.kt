package com.talkgrow_

import android.Manifest
import android.os.Bundle
import android.view.Surface
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.talkgrow_.demo.ReplayRunner
import com.talkgrow_.demo.ScenarioOrchestrator
import com.talkgrow_.util.OverlayView
import com.talkgrow_.util.TTSHelper

class CameraActivity : ComponentActivity() {

    companion object {
        private const val MODEL_ASSET  = "sign_lstm_fp32_1x91x134_FIXED.tflite"
        private const val LABEL_ASSET  = "vocab.json"

        // 게이트 튜닝
        private const val COOLDOWN_MS   = 400L
        private const val ARM_FRAMES    = 3
        private const val DISARM_FR     = 3
        private const val MIN_ACTIVE_FR = 3

        // 손 내린 뒤 라벨 출력 지연
        private const val LABEL_DELAY_MS = 1500L
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var tvTranslated: TextView
    private lateinit var tvStatus: TextView
    private lateinit var switchDemo: MaterialSwitch
    private lateinit var btnBack: ImageView
    private lateinit var btnRefresh: ImageView

    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analyzer: SafeImageAnalyzer? = null
    private val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var tts: TTSHelper? = null
    private var replay: ReplayRunner? = null
    private var scenario: ScenarioOrchestrator? = null

    private val motionGate = MotionGate(
        armFrames = ARM_FRAMES,
        disarmFrames = DISARM_FR,
        minActiveFrames = MIN_ACTIVE_FR,
        cooldownMs = COOLDOWN_MS,
        settleDelayMs = LABEL_DELAY_MS
    ) {
        scenario?.step()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) bindCamera() else {
                Snackbar.make(previewView, "카메라 권한이 필요해요", Snackbar.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.camera_screen)

        previewView   = findViewById(R.id.preview)
        overlayView   = findViewById(R.id.overlayView)
        tvTranslated  = findViewById(R.id.tvTranslatedText)
        tvStatus      = findViewById(R.id.tvStatus)
        switchDemo    = findViewById(R.id.switchSignLanguage)
        btnBack       = findViewById(R.id.btnBack)
        btnRefresh    = findViewById(R.id.btnRefresh)

        // 검은 띠 제거: FILL_CENTER로 화면 채움(상하 또는 좌우 약간 크롭될 수 있음)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        analyzer = SafeImageAnalyzer(
            context = this,
            overlay = overlayView,
            onHandsCount = { /* optional */ },
            onAnyResult = { ok ->
                // ok == true: 움직임/손 보임, false: 정지/손 내림, null: 오류
                if (switchDemo.isChecked) motionGate.onFrame(ok == true)
                runOnUiThread {
                    tvStatus.text = when (ok) {
                        null -> "프레임 오류"
                        false -> if (switchDemo.isChecked) "대기(손 내림)" else "대기"
                        else -> if (switchDemo.isChecked) "번역 진행" else "인식 중"
                    }
                }
            },
            onLandmarksFrame = { _, _, _, _, _, _ ->
                // 실모델 연결 지점
            }
        )
        analyzer?.start()

        tts = TTSHelper(this)
        replay = ReplayRunner(this, MODEL_ASSET, LABEL_ASSET).apply {
            initTTS(tts)
            runCatching { loadLabelMapIfNeeded() }
                .onFailure {
                    Snackbar.make(findViewById(android.R.id.content),
                        "라벨맵(vocab.json) 불러오기 실패. 표시명은 폴백 처리합니다.", Snackbar.LENGTH_LONG).show()
                }
        }

        scenario = ScenarioOrchestrator(
            context = this,
            runner = replay!!,
            onPartialWord = { who, word ->
                if (word.isNotBlank()) {
                    val speaker = if (who == "사용자") "👤" else "🧭"
                    runOnUiThread {
                        tvTranslated.text = "$speaker $word"
                        if (!switchDemo.isChecked) tvStatus.text = "인식 중"
                    }
                }
            },
            onSentence = { who, sentence ->
                val speaker = if (who == "사용자") "👤" else "🧭"
                runOnUiThread {
                    tvTranslated.text = "$speaker $sentence"
                    tvStatus.text = "완료"
                }
                tts?.speak(sentence)
            }
        )

        btnRefresh.setOnClickListener {
            if (switchDemo.isChecked) scenario?.step() else toggleCamera()
        }
        btnBack.setOnClickListener { finish() }

        switchDemo.setOnCheckedChangeListener { _, on ->
            motionGate.reset()
            if (on) {
                scenario?.reset()
                tvTranslated.text = "대기 (손을 내리면 1.5초 뒤 번역 출력)"
                tvStatus.text = "대기"
            } else {
                tvTranslated.text = ""
                tvStatus.text = "대기"
            }
        }

        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { imageAnalysis?.clearAnalyzer() }
        runCatching { analyzer?.shutdown() }
        runCatching { analysisExecutor.shutdownNow() }
        runCatching { tts?.shutdown() }
        runCatching { replay?.close() }
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

            // 16:9로 요청하여 화면 충전(FILL_CENTER)과 매칭
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(rotation)
                .build()
                .apply { setSurfaceProvider(previewView.surfaceProvider) }

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetRotation(rotation)
                .build()

            imageAnalysis?.setAnalyzer(analysisExecutor) { image ->
                val isFront = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                analyzer?.setMirror(isFront)
                analyzer?.analyze(image)
            }

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleCamera() {
        cameraSelector =
            if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                CameraSelector.DEFAULT_BACK_CAMERA
            else
                CameraSelector.DEFAULT_FRONT_CAMERA
        bindCamera()
    }
}

/** 종료(disarm) 후 일정 시간 대기(settleDelayMs) 뒤 onStep() 호출하는 게이트 */
private class MotionGate(
    private val armFrames: Int,
    private val disarmFrames: Int,
    private val minActiveFrames: Int,
    private val cooldownMs: Long,
    private val settleDelayMs: Long,
    private val onStep: () -> Unit
) {
    private var armed = false
    private var trueRun = 0
    private var falseRun = 0
    private var activeTrueTotal = 0
    private var lastStepAt = 0L

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var pending = false

    fun onFrame(movingOrVisible: Boolean) {
        if (movingOrVisible) {
            // 움직임 재개 시, 예약된 확정 취소
            if (pending) {
                handler.removeCallbacks(pendingRunnable!!)
                pendingRunnable = null
                pending = false
            }
            trueRun += 1
            activeTrueTotal += 1
            falseRun = 0
            if (!armed && trueRun >= armFrames) {
                armed = true
            }
            return
        }

        // 정지/손 내림 프레임
        falseRun += 1
        trueRun = 0
        if (armed && falseRun >= disarmFrames) {
            val now = System.currentTimeMillis()
            val ok = activeTrueTotal >= minActiveFrames && (now - lastStepAt) >= cooldownMs

            // 즉시 무장 해제(다음 사이클 준비)
            armed = false
            activeTrueTotal = 0
            falseRun = 0

            if (ok && !pending) {
                pending = true
                val r = Runnable {
                    lastStepAt = System.currentTimeMillis()
                    pending = false
                    onStep() // ← 지연 후 확정
                }
                pendingRunnable = r
                handler.postDelayed(r, settleDelayMs)
            }
        }
    }

    fun reset() {
        armed = false
        trueRun = 0
        falseRun = 0
        activeTrueTotal = 0
        lastStepAt = 0L
        if (pending) {
            handler.removeCallbacks(pendingRunnable!!)
            pendingRunnable = null
            pending = false
        }
    }
}
