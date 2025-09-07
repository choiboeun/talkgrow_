package com.talkgrow_

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.AspectRatio.RATIO_4_3
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.talkgrow_.inference.TFLiteSignInterpreter
import com.talkgrow_.util.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ln

class CameraActivity :
    AppCompatActivity(),
    HandLandmarkerHelper.LandmarkerListener,
    PoseLandmarkerHelper.LandmarkerListener,
    FaceLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "CameraActivity"
        private const val CAMERA_PERMISSION_CODE = 100
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var resultTextView: TextView

    private lateinit var handHelper: HandLandmarkerHelper
    private lateinit var poseHelper: PoseLandmarkerHelper
    private lateinit var faceHelper: FaceLandmarkerHelper
    private lateinit var signInterpreter: TFLiteSignInterpreter
    private val viewModel: MainViewModel by viewModels()

    private lateinit var analysisExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    // sign model windowing
    private val windowSize = 91
    private val featureSize = 134
    private val ring = ArrayDeque<FloatArray>(windowSize)

    private var labels: List<String> = emptyList()
    private var lastShownAt = 0L

    private val majorityWindow = ArrayDeque<Int>()
    private val majoritySize = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_screen)

        previewView    = findViewById(R.id.previewView)
        overlay        = findViewById(R.id.overlayView)
        resultTextView = findViewById(R.id.resultTextView)

        findViewById<ImageButton?>(R.id.switchCameraButton)?.setOnClickListener { toggleLens() }

        analysisExecutor = Executors.newSingleThreadExecutor()

        try {
            signInterpreter = TFLiteSignInterpreter(this)
        } catch (e: Exception) {
            Toast.makeText(this, "모델 초기화 실패: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Interpreter init failed", e)
            finish(); return
        }

        labels = loadLabelsFromAssets("label2id.json")

        handHelper = HandLandmarkerHelper(
            context = this, runningMode = RunningMode.LIVE_STREAM,
            currentDelegate = viewModel.currentDelegate, handLandmarkerHelperListener = this
        )
        poseHelper = PoseLandmarkerHelper(
            context = this, runningMode = RunningMode.LIVE_STREAM,
            currentDelegate = viewModel.currentDelegate, poseLandmarkerHelperListener = this
        )
        faceHelper = FaceLandmarkerHelper(
            context = this, runningMode = RunningMode.LIVE_STREAM,
            currentDelegate = viewModel.currentDelegate, faceLandmarkerHelperListener = this
        )

        // 미러링은 프리뷰/오버레이 둘 다 동일 적용
        applyMirrorForUi()

        checkCameraPermission()
    }

    // ---------- Hand ----------
    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        val first = resultBundle.results.firstOrNull()
        val noHand = first == null || first.landmarks().isNullOrEmpty()
        if (noHand) {
            ring.clear(); majorityWindow.clear()
            runOnUiThread {
                overlay.setHandResults(null, resultBundle.inputImageHeight, resultBundle.inputImageWidth, RunningMode.LIVE_STREAM)
                overlay.invalidate()
                resultTextView.text = "손 없음"
            }
            return
        }

        runOnUiThread {
            overlay.setHandResults(first, resultBundle.inputImageHeight, resultBundle.inputImageWidth, RunningMode.LIVE_STREAM)
            overlay.invalidate()
        }

        val features = extractFeatures(resultBundle) ?: return
        if (ring.size == windowSize) ring.removeFirst()
        ring.addLast(features)

        if (ring.size == windowSize) {
            val input = Array(1) { Array(windowSize) { FloatArray(featureSize) } }
            var t = 0
            for (f in ring) { System.arraycopy(f, 0, input[0][t], 0, featureSize); t++ }

            val probs = signInterpreter.runInference(input)
            if (probs.isNotEmpty()) {
                val (idx, _) = argmax(probs)

                majorityWindow.addLast(idx)
                if (majorityWindow.size > majoritySize) majorityWindow.removeFirst()
                val finalIdx = majorityWindow.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: idx
                val finalProb = probs[finalIdx]

                val now = System.currentTimeMillis()
                if (now - lastShownAt > 250) {
                    lastShownAt = now
                    val label = if (finalIdx in labels.indices) labels[finalIdx] else "ID $finalIdx"
                    runOnUiThread {
                        resultTextView.text = String.format("%.1f%% → %s", finalProb * 100f, label)
                    }
                }
            } else {
                runOnUiThread { resultTextView.text = "출력 없음" }
            }
        }
    }

    // ---------- Pose ----------
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            overlay.setPoseResults(resultBundle.results.firstOrNull(), resultBundle.inputImageHeight, resultBundle.inputImageWidth, RunningMode.LIVE_STREAM)
            overlay.invalidate()
        }
    }

    // ---------- Face ----------
    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            overlay.setFaceResults(resultBundle.result, resultBundle.inputImageHeight, resultBundle.inputImageWidth, RunningMode.LIVE_STREAM)
            overlay.invalidate()
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "MP error($errorCode): $error")
        runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_LONG).show() }
    }

    // ---------- labels ----------
    private fun loadLabelsFromAssets(filename: String): List<String> = try {
        val text = assets.open(filename).use { BufferedReader(InputStreamReader(it)).readText() }.trim()
        if (text.startsWith("{")) {
            val obj = JSONObject(text)
            val pairs = mutableListOf<Pair<Int, String>>()
            val it = obj.keys()
            while (it.hasNext()) {
                val label = it.next()
                val idx = obj.optInt(label, -1)
                if (idx >= 0) pairs += idx to label
            }
            pairs.sortedBy { it.first }.map { it.second }
        } else {
            val arr = JSONArray(text)
            List(arr.length()) { i -> arr.optString(i, "ID $i") }
        }
    } catch (e: Exception) {
        Log.e(TAG, "labels 읽기 실패: ${e.message}", e); emptyList()
    }

    // ---------- Hand features ----------
    private fun extractFeatures(resultBundle: HandLandmarkerHelper.ResultBundle): FloatArray? {
        val res = resultBundle.results.firstOrNull() ?: return null
        val lmLists: List<List<NormalizedLandmark>> = res.landmarks() ?: return null
        if (lmLists.isEmpty()) return null

        val handed = res.handednesses()
        val left = MutableList<NormalizedLandmark?>(21) { null }
        val right = MutableList<NormalizedLandmark?>(21) { null }

        if (!handed.isNullOrEmpty()) {
            for (i in lmLists.indices) {
                val name = handed.getOrNull(i)?.firstOrNull()?.categoryName() ?: ""
                val pts = lmLists[i].take(21)
                if (name.equals("Left", true)) pts.forEachIndexed { j, p -> left[j] = p }
                else if (name.equals("Right", true)) pts.forEachIndexed { j, p -> right[j] = p }
            }
        } else {
            lmLists.getOrNull(0)?.take(21)?.forEachIndexed { j, p -> left[j] = p }
            lmLists.getOrNull(1)?.take(21)?.forEachIndexed { j, p -> right[j] = p }
        }

        fun to63(list: List<NormalizedLandmark?>): FloatArray {
            val out = FloatArray(63); var k = 0
            for (lm in list) {
                if (lm != null) { out[k++] = lm.x(); out[k++] = lm.y(); out[k++] = lm.z() }
                else { out[k++] = 0f; out[k++] = 0f; out[k++] = 0f }
            }
            return out
        }

        val f = FloatArray(featureSize)
        System.arraycopy(to63(left),  0, f, 0, 63)
        System.arraycopy(to63(right), 0, f, 63, 63)
        return f
    }

    // ---------- utils ----------
    private fun argmax(arr: FloatArray): Pair<Int, Float> {
        var idx = 0; var best = Float.NEGATIVE_INFINITY
        for (i in arr.indices) if (arr[i] > best) { best = arr[i]; idx = i }
        return idx to best
    }

    private fun toggleLens() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        applyMirrorForUi()
        startCamera()
    }

    private fun applyMirrorForUi() {
        val mirror = (lensFacing == CameraSelector.LENS_FACING_FRONT)
        val scale = if (mirror) -1f else 1f
        previewView.scaleX = scale
        overlay.scaleX = scale
        // 오버레이 스케일 규칙은 FIT 유지(프리뷰 컨테이너가 4:3이라 어긋나지 않음)
        overlay.setScaleMode(OverlayView.ScaleMode.FIT)
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else startCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            val preview = Preview.Builder()
                .setTargetAspectRatio(RATIO_4_3) // 👈 종횡비만 설정
                .setTargetRotation(previewView.display.rotation)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val analysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(RATIO_4_3) // 👈 해상도(setTargetResolution)와 함께 쓰지 마세요
                .setTargetRotation(previewView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { ia ->
                    ia.setAnalyzer(analysisExecutor) { imageProxy -> processImageProxy(imageProxy) }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "bindToLifecycle failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap().rotate(rotation)
            val mpImage = BitmapImageBuilder(bitmap).build()
            val ts = System.currentTimeMillis()

            handHelper.detectAsync(mpImage, ts)
            poseHelper.detectAsync(mpImage, ts)
            faceHelper.detectAsync(mpImage, ts)
        } catch (t: Throwable) {
            Log.e(TAG, "processImageProxy error: ${t.message}", t)
        } finally {
            imageProxy.close()
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
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
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { handHelper.clearHandLandmarker() } catch (_: Throwable) {}
        try { poseHelper.clearPoseLandmarker() } catch (_: Throwable) {}
        try { faceHelper.clearFaceLandmarker() } catch (_: Throwable) {}
        try { signInterpreter.close() } catch (_: Throwable) {}
        try { analysisExecutor.shutdownNow() } catch (_: Throwable) {}
    }
}
