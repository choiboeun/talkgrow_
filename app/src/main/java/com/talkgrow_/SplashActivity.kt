package com.talkgrow_

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * 작성자: 조경주, 최보은
 * 작성일: 2025-07-06
 * 기능 설명: SplashActivity - 앱 실행 시 스플래시 화면을 1.5초간 보여주고 MainActivity로 이동
 *
 * 수정 이력:
 *  - 2025-07-06 : 초기 생성 및 기본 딜레이 후 화면 전환 기능 구현
 *
 * TODO:
 *  - 스플래시 화면 디자인 개선 및 애니메이션 추가 고려
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 1. 1.5초 딜레이 후 메인 화면으로 전환
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()  // SplashActivity 종료하여 뒤로가기 시 다시 스플래시가 안 보이도록 함
        }, 1500)
    }
}

