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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.talkgrow_.inference.TFLiteSignInterpreter
import com.talkgrow_.util.TextPreprocessor
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val REQ_RECORD_AUDIO = 200
    private val REQ_CAMERA = 201

    private lateinit var micButton: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var contentEditText: EditText
    private var pulseAnimation: Animation? = null

    // (선택) 수어 모델 인터프리터 — 모델 파일이 없으면 null 유지
    private var signInterpreter: TFLiteSignInterpreter? = null

    // true = 한국어 수어 → 한국어 / false = 한국어 → 한국어 수어
    private var isKoreanSignToKorean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(R.id.main)
        val avatarButton = findViewById<LinearLayout>(R.id.avatar_button)
        val cameraButton = findViewById<LinearLayout>(R.id.camera_button)
        micButton = findViewById(R.id.voice_button)
        contentEditText = findViewById(R.id.edit_text_content)
        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.voice_pulse)

        // (선택) TFLite 모델 로드 — 파일이 없으면 조용히 스킵
        tryInitTflite()

        // 시스템 바 패딩
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 키보드에 따라 버튼 숨김/보임
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.15) {
                avatarButton.visibility = View.GONE
                cameraButton.visibility = View.GONE
            } else {
                avatarButton.visibility = View.VISIBLE
                cameraButton.visibility = View.VISIBLE
            }
            micButton.visibility = View.VISIBLE
        }

        // 헤더 버튼들
        val buttonLeft = findViewById<Button>(R.id.button_left)
        val buttonRight = findViewById<Button>(R.id.button_right)
        val buttonToggle = findViewById<ImageButton>(R.id.button_toggle)

        buttonToggle.setOnClickListener {
            val tempText = buttonLeft.text
            buttonLeft.text = buttonRight.text
            buttonRight.text = tempText
            isKoreanSignToKorean = !isKoreanSignToKorean
        }

        // 아바타 버튼
        avatarButton.setOnClickListener {
            val content = contentEditText.text.toString().trim()
            if (content.isNotEmpty()) {
                try {
                    val tokenLists = TextPreprocessor.processInputText(content)
                    Log.d("PreprocessResult", tokenLists.toString())
                    val resultString = tokenLists.joinToString("\n") { s ->
                        s.joinToString(prefix = "[", postfix = "]", separator = ", ")
                    }
                    val intent = Intent(this, PreprocessResultActivity::class.java)
                    intent.putExtra("processed_text", resultString)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "오류 발생: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "텍스트 또는 음성을 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }

        // 카메라 버튼 → CAMERA 권한 확인 후 CameraActivity 이동
        cameraButton.setOnClickListener {
            if (!isKoreanSignToKorean) {
                Toast.makeText(this, "언어 설정을 다시 해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkCameraPermissionAndStart()
        }

        // content 입력창 터치 시
        contentEditText.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) v.performClick()
            if (isKoreanSignToKorean) {
                Toast.makeText(this, "언어 설정을 다시 해주세요.", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        // 마이크 버튼
        micButton.setOnClickListener {
            if (isKoreanSignToKorean) {
                Toast.makeText(this, "언어 설정을 다시 해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                checkAudioPermission()
            }
        }

        // SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "음성 인식 시작...", Toast.LENGTH_SHORT).show()
                micButton.startAnimation(pulseAnimation)
                micButton.setColorFilter(
                    ContextCompat.getColor(this@MainActivity, R.color.voice_active)
                )
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                micButton.clearAnimation()
                micButton.clearColorFilter()
            }
            override fun onError(error: Int) {
                micButton.clearAnimation()
                micButton.clearColorFilter()
                Toast.makeText(this@MainActivity, "음성 인식 실패", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle) {
                micButton.clearAnimation()
                micButton.clearColorFilter()
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val currentText = contentEditText.text.toString()
                    val newText =
                        if (currentText.isEmpty()) matches[0] else "$currentText ${matches[0]}"
                    contentEditText.setText(newText)
                    contentEditText.setSelection(newText.length)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // ─────────────────────────────────────────────
    // TFLite: 파일 없으면 조용히 스킵 (학습 완료 후 연결)
    // ─────────────────────────────────────────────
    private fun tryInitTflite() {
        try {
            signInterpreter = TFLiteSignInterpreter(this)
            Log.d("TalkGrow", "TFLite 모델 로딩 성공")
            sanityRunOnce() // 간단 검증
        } catch (e: Exception) {
            // 모델 미배포 상태 등… 조용히 통과 (크래시 방지)
            Log.w("TalkGrow", "TFLite 초기화 생략/실패: ${e.message}")
            signInterpreter = null
        }
    }

    // 더미 입력으로 1회 추론 → Logcat에서 Top-5 확인
    private fun sanityRunOnce() {
        val itp = signInterpreter ?: return
        val dummy = Array(1) { Array(101) { FloatArray(126) } }
        for (t in 0 until 101) for (c in 0 until 126) dummy[0][t][c] = Random.nextFloat()
        val logits = itp.runInference(dummy)
        val top5 = logits.mapIndexed { idx, v -> idx to v }
            .sortedByDescending { it.second }
            .take(5)
        Log.d("TalkGrow", "TFLite Top-5: $top5")
        Toast.makeText(this, "모델 로드 완료 (Logcat 확인)", Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────
    // 권한 처리
    // ─────────────────────────────────────────────
    private fun checkAudioPermission() {
        val perm = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, perm)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_RECORD_AUDIO)
        } else startSpeechRecognition()
    }

    private fun checkCameraPermissionAndStart() {
        val perm = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, perm)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_CAMERA)
        } else {
            startCameraActivity()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "음성 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraActivity()
            } else {
                Toast.makeText(this, "카메라 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        }
        speechRecognizer.startListening(intent)
    }

    private fun startCameraActivity() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        // (필요 시) signInterpreter?.close()  // close 추가했으면 활성화
    }
}
