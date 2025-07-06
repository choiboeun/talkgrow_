package com.talkgrow_

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        val rootView = findViewById<View>(R.id.main)
        val avatarButton = findViewById<LinearLayout>(R.id.avatar_button)
        val cameraButton = findViewById<LinearLayout>(R.id.camera_button)

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
        }

        // 헤더 버튼들
        val buttonLeft = findViewById<Button>(R.id.button_left)
        val buttonRight = findViewById<Button>(R.id.button_right)
        val buttonToggle = findViewById<ImageButton>(R.id.button_toggle)

        buttonToggle.setOnClickListener {
            val tempText = buttonLeft.text
            buttonLeft.text = buttonRight.text
            buttonRight.text = tempText
        }

        // 아바타 버튼 클릭 시
        avatarButton.setOnClickListener {
            Toast.makeText(this, "아바타 생성 버튼이 클릭되었습니다.", Toast.LENGTH_SHORT).show()
        }

        // 카메라 버튼 클릭 시
        cameraButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }
}
