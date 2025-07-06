package com.talkgrow_

import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Toast
import android.content.Intent

/**
 * 작성자: 조경주, 최보은
 * 작성일: 2025-07-06
 * 기능 설명: MainActivity - 앱의 메인 화면을 구성하고 버튼 클릭 이벤트를 처리함
 *
 * 수정 이력:
 *  - 2025-07-06 : 초기 생성 및 기본 버튼 기능 구현
 *
 * TODO:
 *  - content 부분 클릭 시 버튼 이동 현상 수정 필요
 *  - 음성 인식 기능 추가 필요, 음성 권환 확인 필요
 *  - 아바타 생성 화면 이동 기능 구현 필요
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 시스템 바(상태바, 내비게이션 바)에 맞춰 메인 레이아웃의 패딩 조절
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        // 2. 헤더에 있는 좌, 우 버튼과 토글 버튼을 변수에 할당
        val buttonLeft = findViewById<Button>(R.id.button_left)
        val buttonRight = findViewById<Button>(R.id.button_right)
        val buttonToggle = findViewById<ImageButton>(R.id.button_toggle)

        // 3. 토글 버튼 클릭 시 좌우 버튼의 텍스트를 서로 교환
        buttonToggle.setOnClickListener {
            val tempText = buttonLeft.text
            buttonLeft.text = buttonRight.text
            buttonRight.text = tempText
        }

        // 4. 아바타 생성 화면으로 이동하는 버튼 (현재는 클릭 시 토스트 메시지 출력)
        val avatarButton = findViewById<LinearLayout>(R.id.avatar_button)
        avatarButton.setOnClickListener {
            Toast.makeText(this, "아바타 생성 버튼이 클릭되었습니다.", Toast.LENGTH_SHORT).show()
        }

        // 5. 카메라 화면으로 넘어가는 버튼 클릭 이벤트 처리
        val cameraButton = findViewById<LinearLayout>(R.id.camera_button)
        cameraButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)  // 카메라 화면으로 이동
            startActivity(intent)
        }
    }
}


