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
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.talkgrow_.inference.TFLiteSignInterpreter
import com.talkgrow_.AvatarGenerateActivity
import com.talkgrow_.util.VocabRepo

// ✅ 추가 import (전처리 기능을 위한)
import com.talkgrow_.util.TextPreprocessor
import org.json.JSONObject
import com.unity3d.player.UnityPlayer

class MainActivity : AppCompatActivity() {

    private companion object {
        private const val REQ_RECORD_AUDIO = 200
        private const val REQ_CAMERA = 201
    }

    private lateinit var micButton: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var contentEditText: EditText

    private var signInterpreter: TFLiteSignInterpreter? = null
    private val pulseAnimation: Animation by lazy {
        AlphaAnimation(0.4f, 1f).apply {
            duration = 500
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
    }

    private var isKoreanSignToKorean = true
    private var isLaunchingAvatar = false
    private var backPressedOnce = false
    private val backResetRunnable = Runnable { backPressedOnce = false }
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

        VocabRepo.ensureLoaded(applicationContext)
        Log.i("VocabRepo", "entries = " + VocabRepo.getWord2Id(applicationContext).size)

        val rootView = findViewById<View>(R.id.main)
        val avatarButton = findViewById<LinearLayout>(R.id.avatar_button)
        val cameraButton = findViewById<LinearLayout>(R.id.camera_button)
        micButton = findViewById(R.id.voice_button)
        contentEditText = findViewById(R.id.edit_text_content)

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

        tryInitTflite()

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

        avatarButton.setOnClickListener {
            if (isLaunchingAvatar) return@setOnClickListener

            val content = contentEditText.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "텍스트 또는 음성을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isLaunchingAvatar = true
            avatarButton.isEnabled = false

            // ✅ 수정: JSON payload 생성 및 Intent extra로 전달하는 구조로 변경
            val payload = buildUnityPayloadJson(content)
            val intent = Intent(this, AvatarGenerateActivity::class.java)
            // ✅ 추가: extra_json 필드에 JSON payload 넣음
            if (payload != null) {
                intent.putExtra("extra_json", payload)
            }
            startActivity(intent)

            // ✅ 제거 예정: MainActivity 내 sendToUnity 호출 (이제 AvatarGenerateActivity로 이동)
            /*
            contentEditText.postDelayed({
                sendToUnity(content)
                isLaunchingAvatar = false
                avatarButton.isEnabled = true
            }, 300L)
            */
            // ✅ 추가: 버튼 복귀 처리
            contentEditText.postDelayed({
                isLaunchingAvatar = false
                avatarButton.isEnabled = true
            }, 300L)
        }

        cameraButton.setOnClickListener {
            if (!isKoreanSignToKorean) {
                safeToast("언어 설정을 다시 해주세요.")
                return@setOnClickListener
            }
            checkCameraPermissionAndStart()
        }

        contentEditText.setOnTouchListener { v, e ->
            if (e.action == android.view.MotionEvent.ACTION_UP) v.performClick()
            if (isKoreanSignToKorean) {
                safeToast("언어 설정을 다시 해주세요.")
                true
            } else false
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        logLife("onNewIntent intent=$intent")
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        logLife("onResume (isTaskRoot=$isTaskRoot) intent=$intent")
        isLaunchingAvatar = false
        findViewById<LinearLayout>(R.id.avatar_button)?.isEnabled = true
    }

    override fun onPause() {
        super.onPause()
        runCatching { speechRecognizer.stopListening() }
        micButton.clearAnimation()
        micButton.clearColorFilter()
    }

    override fun onStop() {
        super.onStop()
        logLife("onStop")
        runCatching { speechRecognizer.cancel() }
    }

    override fun onDestroy() {
        super.onDestroy()
        logLife("onDestroy")
        contentEditText.removeCallbacks(backResetRunnable)
        runCatching { speechRecognizer.destroy() }
        micButton.clearAnimation()
        micButton.clearColorFilter()
    }

    private fun tryInitTflite() {
        try {
            signInterpreter = TFLiteSignInterpreter(this)
            Log.d("TalkGrow", "TFLite 모델 로딩 성공")
            sanityRunOnce()
        } catch (e: Exception) {
            Log.w("TalkGrow", "TFLite 초기화 생략/실패: ${e.message}")
            signInterpreter = null
        }
    }

    private fun sanityRunOnce() {
        Log.d("TalkGrow", "sanityRunOnce()")
    }

    private fun isModelReady(): Boolean = try {
        assets.open("export_infer.tflite").close()
        assets.open("feat_norm.json").close()
        assets.open("hand_landmarker.task").close()
        assets.open("pose_landmarker_full.task").close()
        runCatching { assets.open("meaning_map.json").close() }.isSuccess ||
                runCatching { assets.open("sen_label_map.json").close() }.isSuccess
    } catch (_: Throwable) { false }

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

    // ✅ 추가 메서드: JSON payload 생성
    private fun buildUnityPayloadJson(text: String): String? {
        val vocab = VocabRepo.getWord2Id(this).keys
        val sents = TextPreprocessor.processInputTextWithVocab(
            input = text,
            vocab = vocab,
            useUnk = true,
            unkToken = "<unk>"
        )
        if (sents.isEmpty()) {
            Toast.makeText(this, "전처리 결과가 비었습니다.", Toast.LENGTH_SHORT).show()
            return null
        }

        val tokens = sents.flatten()

        // ✅ Unity에서 배열 형식으로 파싱할 수 있게 변경
        return tokens.joinToString(
            prefix = "[\"",
            separator = "\",\"",
            postfix = "\"]"
        )
    }

}
