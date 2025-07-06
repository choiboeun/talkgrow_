package com.talkgrow_

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.talkgrow_.util.HandLandmarkerHelper
import com.talkgrow_.util.HandLandmarkerHelper.ResultBundle
import com.talkgrow_.util.MainViewModel
import com.talkgrow_.util.OverlayView

/**
 * 작성자: 조경주, 최보은
 * 작성일: 2025-07-06
 * 기능 설명: CameraActivity - 카메라 권한 확인, 실시간 카메라 프리뷰 및 손 인식 기능 구현
 *
 * 수정 이력:
 *  - 2025-07-06 : 초기 생성 및 기본 카메라, 손 랜드마크 탐지 기능 구현
 *
 * TODO:
 *  - 전면 후면 카메라 변환 기능 구현 필요
 *  - 손 감지 불가 메시지 딜레이 수정 필요
 *  - 손 인식 결과 UI 개선 및 추가 기능 개발 ( 결과 화면 )
 */
class CameraActivity : AppCompatActivity(), HandLandmarkerHelper.LandmarkerListener {

    private val CAMERA_PERMISSION_CODE = 100
    private lateinit var previewView: PreviewView
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var overlay: OverlayView
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_screen)

        previewView = findViewById(R.id.previewView)
        checkCameraPermission()  // 카메라 권한 요청 또는 권한이 있을 경우 카메라 시작

        overlay = findViewById(R.id.overlayView)

        // HandLandmarkerHelper 초기화: Mediapipe 손 인식 라이브러리 설정
        handLandmarkerHelper = HandLandmarkerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            currentDelegate = viewModel.currentDelegate,
            minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
            minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
            minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
            maxNumHands = viewModel.currentMaxHands,
            handLandmarkerHelperListener = this
        )
    }

    /**
     * Mediapipe 손 인식 결과 처리 콜백
     */
    override fun onResults(resultBundle: ResultBundle) {
        // 첫 번째 결과에서 첫 번째 손 랜드마크 가져오기 (없으면 null)
        val landmark: NormalizedLandmark? =
            resultBundle.results.firstOrNull()
                ?.landmarks()
                ?.firstOrNull()
                ?.firstOrNull()

        runOnUiThread {
            // 손 인식 결과를 OverlayView에 전달하여 UI 업데이트
            overlay.setResults(
                resultBundle.results[0],
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM
            )

            // 손 랜드마크 좌표를 토스트로 간단히 표시하거나 손이 없으면 알림 표시
            if (landmark != null) {
                val text = "x=${"%.2f".format(landmark.x())}, y=${"%.2f".format(landmark.y())}"
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "손이 감지되지 않았습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 카메라 권한 확인 및 요청
     */
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startCamera()  // 권한이 이미 있으면 바로 카메라 시작
        }
    }

    /**
     * 권한 요청 결과 처리
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()  // 권한 허용 시 카메라 시작
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()  // 권한 거부 시 액티비티 종료
        }
    }

    /**
     * 카메라 프리뷰 및 이미지 분석 설정 후 실행
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 카메라 프리뷰 설정
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 이미지 분석 설정 및 분석기 등록
            val imageAnalyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()  // 기존 바인딩 해제
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)  // 새로 바인딩
            } catch (e: Exception) {
                Toast.makeText(this, "카메라 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * ImageProxy를 Bitmap으로 변환 후 손 인식 비동기 처리 실행
     */
    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap().rotate(imageProxy.imageInfo.rotationDegrees)
        val mpImage = BitmapImageBuilder(bitmap).build()
        val frameTime = System.currentTimeMillis()
        handLandmarkerHelper.detectAsync(mpImage, frameTime)
        imageProxy.close()  // 이미지 분석 완료 후 반드시 닫아줘야 함
    }

    /**
     * ImageProxy를 Bitmap으로 변환하는 확장 함수
     */
    private fun ImageProxy.toBitmap(): android.graphics.Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Bitmap 이미지를 주어진 각도로 회전하는 확장 함수
     */
    private fun android.graphics.Bitmap.rotate(degrees: Int): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        return android.graphics.Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    /**
     * HandLandmarkerHelper 에러 발생 시 호출되는 콜백
     */
    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, "에러 발생: $error ($errorCode)", Toast.LENGTH_LONG).show()
        }
    }
}

