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

        // 1.5초 후 메인으로 전환하되, 메인을 "새 태스크의 루트"로 만든다.
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or     // 새 태스크 생성
                                Intent.FLAG_ACTIVITY_CLEAR_TASK       // 기존 태스크 비우기
                    )
                }
            )
            // 스플래시는 백스택에 남기지 않음
            finish()
        }, 1500)
    }
}

