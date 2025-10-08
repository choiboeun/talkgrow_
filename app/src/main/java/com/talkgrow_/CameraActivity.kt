package com.talkgrow_

import android.Manifest
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.talkgrow_.util.OverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var resultText: TextView
    private lateinit var switchBtn: ImageButton

    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var analyzer: SafeImageAnalyzer? = null
    private lateinit var analysisExecutor: ExecutorService

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCamera() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_screen)

        previewView  = findViewById(R.id.previewView)
        overlayView  = findViewById(R.id.overlayView)
        resultText   = findViewById(R.id.resultTextView)
        switchBtn    = findViewById(R.id.switchCameraButton)

        analysisExecutor = Executors.newSingleThreadExecutor()

        // 프리뷰/오버레이 스케일 일치
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        overlayView.bringToFront()

        switchBtn.setOnClickListener {
            cameraSelector =
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                    CameraSelector.DEFAULT_BACK_CAMERA
                else
                    CameraSelector.DEFAULT_FRONT_CAMERA
            analyzer?.close()
            startCamera()
        }

        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .apply { setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // RGBA 또는 YUV 입력 모두 Analyzer에서 처리
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            analyzer = SafeImageAnalyzer(
                context = this,
                overlay = overlayView,
                onHandsCount = { n ->
                    runOnUiThread {
                        resultText.text = when (n) {
                            0 -> "손 미인식"
                            1 -> "한 손 인식"
                            else -> "두 손 인식"
                        }
                    }
                },
                onAnyResult = { ok ->
                    runOnUiThread {
                        if (ok == null) resultText.text = "프레임 처리 에러"
                        else if (!ok)   resultText.text = "결과 대기 중…"
                    }
                }
            ).also { it.start() }

            analysis.setAnalyzer(analysisExecutor) { image ->
                analyzer?.setMirror(cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                analyzer?.analyze(image)
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { analyzer?.close() }
        analysisExecutor.shutdown()
    }
}
