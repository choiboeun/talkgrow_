package com.talkgrow_

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AvatarGenerateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar_generate)

        val btnClose: ImageButton = findViewById(R.id.btnClose)
        val btnPlay : ImageButton = findViewById(R.id.btnPlay)
        val btnPause: ImageButton = findViewById(R.id.btnPause)
        val overlay : View        = findViewById(R.id.overlayView)

        // X 버튼: 이전 화면(홈)으로 돌아가기
        btnClose.setOnClickListener {
            finish() // 이전 액티비티로 복귀 (홈으로 돌아감)
        }

        // 재생: 오버레이 숨기고 메시지
        btnPlay.setOnClickListener {
            overlay.visibility = View.GONE
            Toast.makeText(this, "재생되었습니다", Toast.LENGTH_SHORT).show()
            // TODO: 아바타 실제 재생 로직을 여기에 연결
        }

        // 일시정지: 오버레이 보이고 메시지
        btnPause.setOnClickListener {
            overlay.visibility = View.VISIBLE
            Toast.makeText(this, "일시 정지", Toast.LENGTH_SHORT).show()
            // TODO: 아바타 실제 일시정지 로직을 여기에 연결
        }
    }
}
