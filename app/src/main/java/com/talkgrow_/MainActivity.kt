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
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.talkgrow_.inference.TFLiteSignInterpreter
import kotlin.random.Random



class MainActivity : AppCompatActivity() {

    private val REQ_RECORD_AUDIO = 200
    private val REQ_CAMERA = 201

    private lateinit var micButton: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var contentEditText: EditText

    // 수어→한국어 모드 (기본 true)
    private var isKoreanSignToKorean = true


    // 아바타 버튼 더블클릭 방지
    private var isLaunchingAvatar = false

    // 두 번 뒤로가기 처리
    private var backPressedOnce = false
    private val backResetRunnable = Runnable { backPressedOnce = false }

    // ---- Toast 디바운서 ----
    private var lastToastAt = 0L

    private fun logLife(msg: String) = Log.d("MainLife", msg)

    private fun safeToast(msg: String, minIntervalMs: Long = 1200L) {
        val now = System.currentTimeMillis()
        if (now - lastToastAt >= minIntervalMs) {
            lastToastAt = now
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLife("onCreate intent=$intent isTaskRoot=$isTaskRoot")
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(R.id.main)
        val avatarButton = findViewById<LinearLayout>(R.id.avatar_button)
        val cameraButton = findViewById<LinearLayout>(R.id.camera_button)
        micButton = findViewById(R.id.voice_button)
        contentEditText = findViewById(R.id.edit_text_content)


        // ✅ 메인에서만 태스크를 백그라운드로 (singleTask 전제)
        onBackPressedDispatcher.addCallback(this) {
            if (backPressedOnce) {
                contentEditText.removeCallbacks(backResetRunnable)
                backPressedOnce = false
                moveTaskToBack(true)
            } else {
                backPressedOnce = true
                Toast.makeText(this@MainActivity, "뒤로가기를 한 번 더 누르면 종료", Toast.LENGTH_SHORT).show()
                contentEditText.removeCallbacks(backResetRunnable)
                contentEditText.postDelayed(backResetRunnable, 1500)
            }
        }

        // (선택) TFLite 모델 로드 — 파일이 없으면 조용히 스킵
        tryInitTflite()

        // 시스템 바 패딩
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

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


        // ✅ 아바타 버튼 (전환 안전화 + 디바운스 + 예외 안전망)
        avatarButton.setOnClickListener {
            if (isLaunchingAvatar) return@setOnClickListener

            val content = contentEditText.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "텍스트 또는 음성을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isLaunchingAvatar = true
            avatarButton.isEnabled = false

            runCatching {
                // 메인(루트 singleTask) 유지, 아바타만 위로
                startActivity(
                    Intent(this, AvatarGenerateActivity::class.java)
                        .putExtra("extra_text", content)
                )
                // 절대 finish() 호출하지 말 것
            }.onFailure { t ->
                isLaunchingAvatar = false
                avatarButton.isEnabled = true
                Toast.makeText(this, "아바타 화면 전환 실패: ${t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "start AvatarGenerateActivity failed", t)

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

                Toast.makeText(this@MainActivity, "음성 인식 시작...", Toast.LENGTH_SHORT).show()
                micButton.startAnimation(pulseAnimation)
                micButton.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.voice_active))

            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { micButton.clearColorFilter() }
            override fun onError(error: Int) { micButton.clearColorFilter() }
            override fun onResults(results: Bundle) {
                micButton.clearColorFilter()

                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val currentText = contentEditText.text.toString()
                    val newText = if (currentText.isEmpty()) matches[0] else "$currentText ${matches[0]}"
                    contentEditText.setText(newText)
                    contentEditText.setSelection(newText.length)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }


    // ✅ singleTask 전제: 다른 화면에서 돌아올 때 호출될 수 있음
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        logLife("onNewIntent intent=$intent")
        setIntent(intent) // 현재 액티비티의 인텐트 최신화 (UI 업데이트 필요 시 여기서)
        // 절대 여기서 moveTaskToBack(true) 호출 X
    }

    override fun onResume() {
        super.onResume()
        logLife("onResume (isTaskRoot=$isTaskRoot) intent=$intent")
        isLaunchingAvatar = false
        findViewById<LinearLayout>(R.id.avatar_button)?.isEnabled = true
        // 절대 여기서 moveTaskToBack(true) 호출 X
    }

    override fun onPause() {
        super.onPause()
        // 🔒 음성 인식/애니메이션 중단(안정성)
        runCatching { speechRecognizer.stopListening() }
        micButton.clearAnimation()
        micButton.clearColorFilter()
    }

    override fun onStop() {
        super.onStop()
        logLife("onStop")
        // 🔒 더 강하게 정리(희귀 크래시 케이스 방지)
        runCatching { speechRecognizer.cancel() }
    }

    override fun onDestroy() {
        super.onDestroy()
        logLife("onDestroy")
        contentEditText.removeCallbacks(backResetRunnable)
        // 🔒 완전 정리
        runCatching { speechRecognizer.destroy() }
        micButton.clearAnimation()
        micButton.clearColorFilter()
        // signInterpreter?.close() // 필요 시
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
            Log.w("TalkGrow", "TFLite 초기화 생략/실패: ${e.message}")
            signInterpreter = null

    private fun isModelReady(): Boolean {
        return try {
            assets.open("export_infer.tflite").close()
            assets.open("feat_norm.json").close()
            assets.open("hand_landmarker.task").close()
            assets.open("pose_landmarker_full.task").close()
            runCatching { assets.open("meaning_map.json").close() }.isSuccess ||
                    runCatching { assets.open("sen_label_map.json").close() }.isSuccess
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
        speechRecognizer.destroy()
    }

}
