package com.talkgrow_

import android.Manifest
import android.os.Bundle
import android.util.Rational
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
import com.talkgrow_.util.OverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var tvTranslated: TextView
    private lateinit var tvStatus: TextView
    private lateinit var switchSignLanguage: MaterialSwitch
    private lateinit var btnBack: ImageView
    private lateinit var btnRefresh: ImageView

    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var analyzer: SafeImageAnalyzer? = null
    // **수정: Nullable로 선언하여 안전한 해제 및 초기화**
    private var analysisExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var hasPermission = false

    // TFLiteSignInterpreter, MeaningMapper, TTSHelper 클래스가 존재한다고 가정합니다.
    private var signInterpreter: TFLiteSignInterpreter? = null
    private lateinit var meaningMapper: MeaningMapper
    private var tts: TTSHelper? = null
    private var segmentPipeline: SegmentPipeline? = null
    private var imageAnalysis: ImageAnalysis? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            hasPermission = ok
            if (ok) bindCameraUseCases() else {
                Snackbar.make(previewView, "카메라 권한이 필요해요", Snackbar.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.camera_screen)

        // view refs
        previewView          = findViewById(R.id.preview)
        overlayView          = findViewById(R.id.overlayView)
        tvTranslated         = findViewById(R.id.tvTranslatedText)
        tvStatus             = findViewById(R.id.tvStatus)
        switchSignLanguage   = findViewById(R.id.switchSignLanguage)
        btnBack              = findViewById(R.id.btnBack)
        btnRefresh           = findViewById(R.id.btnRefresh)

        // PreviewView 기본 세팅
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        overlayView.attachTo(previewView)
        overlayView.bringToFront()
        tvStatus.bringToFront()

        // 헤더 버튼
        btnBack.setOnClickListener {
            segmentPipeline?.resetAll()
            safeShutdown()
            onBackPressedDispatcher.onBackPressed()
        }

        btnRefresh.setOnClickListener {
            cameraSelector =
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                    CameraSelector.DEFAULT_BACK_CAMERA
                else
                    CameraSelector.DEFAULT_FRONT_CAMERA
            segmentPipeline?.resetAll()
            rebind()
        }

        analysisExecutor = Executors.newSingleThreadExecutor()

        if (!initSpeechAndModel()) return

        // 스위치: 데모/라이브 토글
        switchSignLanguage.setOnCheckedChangeListener { _, isChecked ->
            segmentPipeline?.setDemo(isChecked)
            tvStatus.text = "대기"
        }

        tvStatus.text = "대기"

        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onStop() {
        super.onStop()
        unbindAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        safeShutdown()
    }

    private fun safeShutdown() {
        unbindAll()
        runCatching { analysisExecutor?.shutdownNow() }
        analysisExecutor = null
        runCatching { signInterpreter?.close() }
        runCatching { tts?.shutdown() }
        signInterpreter = null
        tts = null
    }

    private fun initSpeechAndModel(): Boolean {
        fun assetExists(name: String): Boolean =
            try { assets.open(name).close(); true } catch (_: Throwable) { false }

        val model = "model_v8_focus16_284_fp16.tflite"
        val labels = "labels_ko_284.json"
        if (!assetExists(model) || !assetExists(labels)) {
            Snackbar.make(findViewById(android.R.id.content),
                "모델/라벨 에셋을 찾을 수 없어요.", Snackbar.LENGTH_LONG).show()
            finish()
            return false
        }

        return try {
            tts = TTSHelper(this)
            signInterpreter = TFLiteSignInterpreter(this, model, labels)
            meaningMapper = MeaningMapper()

            segmentPipeline = SegmentPipeline(
                interpreter = signInterpreter!!,
                mapper = meaningMapper,
                onSentence = { sentence ->
                    runOnUiThread {
                        // **번역 결과는 여기에만 표시됩니다.**
                        tvTranslated.text = sentence
                        tts?.speak(sentence)
                    }
                },
                onLabelBadge = { label ->
                    runOnUiThread {
                        // **OverlayView의 배지는 숨기도록 설정됩니다.**
                        overlayView.setBadge(label ?: "")
                        overlayView.postInvalidateOnAnimation()
                    }
                },
                onClearResult = {
                    runOnUiThread {
                        tvTranslated.text = "번역 시작"
                        tts?.stop()
                    }
                },
                mirroredPreview = true
            )
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            Snackbar.make(findViewById(android.R.id.content),
                "모델 초기화 실패: ${e.javaClass.simpleName}", Snackbar.LENGTH_LONG).show()
            finish()
            false
        }
    }

    private fun rebind() { unbindAll(); bindCameraUseCases() }

    private fun unbindAll() {
        runCatching { imageAnalysis?.clearAnalyzer() }
        runCatching { analyzer?.shutdown() }
        analyzer = null
        runCatching { cameraProvider?.unbindAll() }
        imageAnalysis = null
    }

    private fun bindCameraUseCases() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build()
                .apply { setSurfaceProvider(previewView.surfaceProvider) }

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer = SafeImageAnalyzer(
                context = this,
                overlay = overlayView,
                onHandsCount = { /* 생략 */ },
                onAnyResult = { ok ->
                    // tvStatus는 인식 상태만 업데이트합니다.
                    runOnUiThread {
                        tvStatus.text = when (ok) {
                            null  -> "프레임 오류"
                            false -> "대기"
                            else  -> "인식 중"
                        }
                    }
                },
                onLandmarksFrame = { pose33, left21, right21, handsVisible, ts ->
                    segmentPipeline?.onFrame(pose33, left21, right21, handsVisible, ts)
                }
            ).also { it.start() }

            imageAnalysis?.setAnalyzer(analysisExecutor ?: return@addListener) { image ->
                val isFront = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                analyzer?.setMirror(isFront)
                analyzer?.analyze(image)
            }

            val w = previewView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val h = previewView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val vp = ViewPort.Builder(Rational(w, h), rotation)
                .setScaleType(ViewPort.FILL_CENTER)
                .build()

            val group = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageAnalysis!!)
                .setViewPort(vp)
                .build()

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(this, cameraSelector, group)
        }, ContextCompat.getMainExecutor(this))
    }
}