package com.talkgrow_

import android.widget.LinearLayout
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. activity_main 레이아웃을 화면에 표시
        setContentView(R.layout.activity_main)

        // 3. 시스템 바 영역(상태바, 내비게이션 바)의 크기만큼 패딩을 설정하는 리스너를 뷰에 붙임
        // findViewById(R.id.main)으로 최상위 레이아웃 뷰를 찾고,
        // 시스템 바가 차지하는 영역을 구해서 그만큼 패딩을 줘서
        // 컨텐츠가 시스템 바 영역과 겹치지 않도록 여백을 맞춤
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // camera_button을 눌렀을 때 카메라 화면으로 이동
        val cameraButton = findViewById<LinearLayout>(R.id.camera_button)
        cameraButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }
}
