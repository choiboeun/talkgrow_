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


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 시스템 바(상태바, 내비게이션 바) 영역에 맞춰 padding 조절
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

        // 2. 헤더에 있는 토글 버튼, 좌우 버튼 찾기
        val buttonLeft = findViewById<Button>(R.id.button_left)
        val buttonRight = findViewById<Button>(R.id.button_right)
        val buttonToggle = findViewById<ImageButton>(R.id.button_toggle)

        // 3. 토글 버튼 클릭 시 좌우 버튼 텍스트 교환
        buttonToggle.setOnClickListener {
            val tempText = buttonLeft.text
            buttonLeft.text = buttonRight.text
            buttonRight.text = tempText
        }

        // 4. 아바타 생성 화면으로 넘어가는 버튼 (예상)
        val avatarButton = findViewById<LinearLayout>(R.id.avatar_button)
        avatarButton.setOnClickListener {
            Toast.makeText(this, "아바타 생성 버튼이 클릭되었습니다.", Toast.LENGTH_SHORT).show()
        }


        // 5. 카메라 화면으로 넘어가는 버튼
        val cameraButton = findViewById<LinearLayout>(R.id.camera_button)
        cameraButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)  // 카메라 화면으로 이동
            startActivity(intent)
        }
    }
}

