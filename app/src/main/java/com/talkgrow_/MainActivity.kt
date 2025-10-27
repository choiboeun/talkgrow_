package com.talkgrow_

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.talkgrow_.util.TextPreprocessor

class MainActivity : AppCompatActivity() {

    private val REQ_RECORD_AUDIO = 200
    private val REQ_CAMERA = 201

    private lateinit var micButton: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var contentEditText: EditText

    // 수어→한국어 모드 (기본 true)
    private var isKoreanSignToKorean = true

    // ---- Toast 디바운서 ----
    private var lastToastAt = 0L
    private fun safeToast(msg: String, minIntervalMs: Long = 1200L) {
        val now = System.currentTimeMillis()
        if (now - lastToastAt >= minIntervalMs) {
            lastToastAt = now
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(R.id.main)
        val avatarButton = findViewById<LinearLayout>(R.id.avatar_button)
        val cameraButton = findViewById<LinearLayout>(R.id.camera_button)
        micButton = findViewById(R.id.voice_button)
        contentEditText = findViewById(R.id.edit_text_content)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom)
            insets
        }

        // 키보드 열림에 따른 버튼 show/hide
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val r = Rect()
                rootView.getWindowVisibleDisplayFrame(r)
                val screenH = rootView.rootView.height
                val keyboardH = screenH - r.bottom
                val show = keyboardH > screenH * 0.15
                avatarButton.visibility = if (show) View.GONE else View.VISIBLE
                cameraButton.visibility = if (show) View.GONE else View.VISIBLE
                micButton.visibility = View.VISIBLE
            }
        })

        val buttonLeft = findViewById<Button>(R.id.button_left)
        val buttonRight = findViewById<Button>(R.id.button_right)
        val buttonToggle = findViewById<ImageButton>(R.id.button_toggle)
        buttonToggle.setOnClickListener {
            val tmp = buttonLeft.text
            buttonLeft.text = buttonRight.text
            buttonRight.text = tmp
            isKoreanSignToKorean = !isKoreanSignToKorean
        }

        // 아바타 버튼: 텍스트 전처리 화면 이동
        avatarButton.setOnClickListener {
            val text = contentEditText.text.toString().trim()
            if (text.isEmpty()) {
                safeToast("텍스트 또는 음성을 입력하세요")
                return@setOnClickListener
            }
            try {
                val tokenLists = TextPreprocessor.processInputText(text)
                val resultString =
                    tokenLists.joinToString("\n") { it.joinToString(prefix = "[", postfix = "]") }
                val intent = Intent(this, PreprocessResultActivity::class.java)
                intent.putExtra("processed_text", resultString)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("TalkGrow", "preprocess error", e)
                safeToast("오류: ${e.message}")
            }
        }

        // 카메라 버튼: 권한 → CameraActivity
        cameraButton.setOnClickListener {
            if (!isKoreanSignToKorean) {
                safeToast("언어 설정을 다시 해주세요.")
                return@setOnClickListener
            }
            checkCameraPermissionAndStart()
        }

        // 입력창 터치 시 토스트 연사 방지
        contentEditText.setOnTouchListener { v, e ->
            if (e.action == android.view.MotionEvent.ACTION_UP) v.performClick()
            if (isKoreanSignToKorean) {
                safeToast("언어 설정을 다시 해주세요.")
                true
            } else false
        }

        // 마이크 버튼
        micButton.setOnClickListener {
            if (isKoreanSignToKorean) {
                safeToast("언어 설정을 다시 해주세요.")
            } else {
                checkAudioPermission()
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                safeToast("음성 인식 시작...")
                micButton.setColorFilter(
                    ContextCompat.getColor(this@MainActivity, R.color.voice_active)
                )
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { micButton.clearColorFilter() }
            override fun onError(error: Int) { micButton.clearColorFilter() }
            override fun onResults(results: Bundle) {
                micButton.clearColorFilter()
                val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) {
                    val cur = contentEditText.text.toString()
                    val newText = if (cur.isEmpty()) list[0] else "$cur ${list[0]}"
                    contentEditText.setText(newText)
                    contentEditText.setSelection(newText.length)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun isModelReady(): Boolean {
        return try {
            assets.open("model_v8_focus16_284_fp16.tflite").close()
            assets.open("labels_ko_284.json").close()
            assets.open("hand_landmarker.task").close()
            assets.open("pose_landmarker_full.task").close()
            true
        } catch (_: Throwable) { false }
    }

    private fun startCameraActivitySafe() {
        if (!isModelReady()) {
            safeToast("모델/태스크 파일이 준비되지 않았습니다.", 2000)
            return
        }
        try {
            startActivity(Intent(this, CameraActivity::class.java))
        } catch (t: Throwable) {
            safeToast("카메라 화면 진입 실패: ${t.javaClass.simpleName}", 2000)
            Log.e("TalkGrow", "startCameraActivitySafe", t)
        }
    }

    private fun checkAudioPermission() {
        val perm = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_RECORD_AUDIO)
        } else startSpeechRecognition()
    }

    private fun checkCameraPermissionAndStart() {
        val perm = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_CAMERA)
        } else startCameraActivitySafe()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_RECORD_AUDIO ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startSpeechRecognition()
                else safeToast("음성 권한이 거부되었습니다.")
            REQ_CAMERA ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCameraActivitySafe()
                else safeToast("카메라 권한이 거부되었습니다.")
        }
    }

    private fun startSpeechRecognition() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        }
        speechRecognizer.startListening(i)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { speechRecognizer.destroy() } catch (_: Throwable) {}
    }
}
