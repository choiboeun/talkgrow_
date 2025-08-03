package com.talkgrow_

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
/**
 * 작성자: 조경주, 최보은
 * 작성일: 2025-07-06
 * 기능 설명: MainActivity - 앱의 메인 화면을 구성하고 버튼 클릭 이벤트를 처리함
 *
 * 수정 이력:
 *  - 2025-07-06 : 초기 생성 및 기본 버튼 기능 구현
 *  - 2025-08-02 : 음성 권한 부여 설정 및 음성 인식 버튼 기능 구현
 *  - 2025-08-03 : 음성 인식 버튼 효과 버튼 효과,
 *                  한국어 수어 -> 한국어 일때만 '수어번역 카메라' 버튼 클릭 가능,
 *                  한국어 -> 한국어 수어 일때만 '아바타 생성' 버튼 클릭 가능,
 *                  이 외에 언어 재설정 알림 기능
 *  - 2025-08-04 :
 *
 * TODO:
 *  - content 부분 클릭 시 버튼 이동 현상 수정 필요
 *  - 음성 인식 기능 추가 필요, 음성 권환 확인 필요
 *  - 아바타 생성 화면 이동 기능 구현 필요
 */
class MainActivity : AppCompatActivity() {

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private lateinit var micButton: ImageButton

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var contentEditText: EditText

    private var pulseAnimation: Animation? = null

    // true = 한국어 수어 → 한국어 모드
    // false = 한국어 → 한국어 수어 모드
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




        // 시스템 바 패딩 조절
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        // 키보드 올라왔는지 감지해서 버튼 숨기기
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)

            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight > screenHeight * 0.15) {
                // 키보드 올라온 상태
                avatarButton.visibility = View.GONE
                cameraButton.visibility = View.GONE
            } else {
                // 키보드 내려간 상태
                avatarButton.visibility = View.VISIBLE
                cameraButton.visibility = View.VISIBLE
            }
            //micButton은 항상 보이게
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

            // 토글 시마다 상태 바꾸기
            isKoreanSignToKorean = !isKoreanSignToKorean
        }

        // 아바타 버튼 클릭 시
        avatarButton.setOnClickListener {
            val content = contentEditText.text.toString().trim()
            if (content.isNotEmpty()) {
                // 텍스트가 있는 경우 → 아바타 동작 실행 (기존 동작)
                Toast.makeText(this, "아바타 생성 버튼이 클릭되었습니다.", Toast.LENGTH_SHORT).show()

                // 여기에 아바타 생성 관련 기능이 들어가면 됨!
            } else {
                // 텍스트가 비어 있는 경우 → 안내 메시지 출력
                Toast.makeText(this, "텍스트 또는 음성을 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }

        // 카메라 버튼 클릭 시
        cameraButton.setOnClickListener {
            if (isKoreanSignToKorean) {
                // 한국어 수어->한국어 모드일 때 정상 실행
                val intent = Intent(this, CameraActivity::class.java)
                startActivity(intent)
            } else {
                // 한국어->한국어 수어 모드일 때 경고 메시지
                Toast.makeText(this, "언어 설정을 다시 해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // content 입력창 터치 시
        contentEditText.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                v.performClick()  // 클릭 이벤트 처리 알림
            }

            if (isKoreanSignToKorean) {
                Toast.makeText(this, "언어 설정을 다시 해주세요.", Toast.LENGTH_SHORT).show()
                true  // 이벤트 소비(터치 막음)
            } else {
                false // 터치 이벤트 정상 처리
            }
        }


        // 마이크 버튼 클릭
        micButton.setOnClickListener {
            if (isKoreanSignToKorean) {
                // 한국어 수어->한국어 모드일 때 경고 메시지
                Toast.makeText(this, "언어 설정을 다시 해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                // 마이크 버튼 클릭 → 음성 권한 요청
                checkAudioPermission()
            }
        }

        // SpeechRecognizer 초기화
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "음성 인식 시작...", Toast.LENGTH_SHORT).show()
                micButton.startAnimation(pulseAnimation)  //  애니메이션 시작

                // 마이크 버튼 색상 변경 (진행 중일 때)
                micButton.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.voice_active))
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                micButton.clearAnimation()  // 인식 끝나면 애니메이션 중지
                micButton.clearColorFilter()  // 색상 초기화 (원래 색으로 복원)
            }
            override fun onError(error: Int) {
                micButton.clearAnimation()  // 에러 나도 중지
                micButton.clearColorFilter()  // 에러 시에도 복원
                Toast.makeText(this@MainActivity, "음성 인식 실패", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle) {
                micButton.clearAnimation()  // 결과 나오면 중지
                micButton.clearColorFilter()  // 결과 처리 후 복원

                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val currentText = contentEditText.text.toString()
                    val newText = if (currentText.isEmpty()) {
                        matches[0]
                    } else {
                        "$currentText ${matches[0]}"
                    }
                    contentEditText.setText(newText)
                    contentEditText.setSelection(newText.length) // 커서를 맨 뒤로 이동
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }


    // 음성 권한 확인 함수
    private fun checkAudioPermission() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_RECORD_AUDIO_PERMISSION)
        } else {
            startSpeechRecognition()
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "음성 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 음성 인식 시작
    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        }
        speechRecognizer.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
