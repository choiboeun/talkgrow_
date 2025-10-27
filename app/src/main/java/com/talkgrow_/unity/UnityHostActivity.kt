package com.talkgrow_.unity

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback

class UnityHostActivity : ComponentActivity() {

    private lateinit var container: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container = FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(container)

        // Unity 붙이기
        UnityPlayerHolder.attachTo(this, container)

        // ◀◀ 뒤로가기: deprecated onBackPressed() 대신 Dispatcher 사용
        onBackPressedDispatcher.addCallback(this) {
            // UnityPlayer 는 그대로 두고, 이 화면만 닫음
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        UnityPlayerHolder.onResume()
    }

    override fun onPause() {
        UnityPlayerHolder.onPause()
        super.onPause()
    }

    override fun onLowMemory() {
        UnityPlayerHolder.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        // Unity 엔진은 유지, 뷰만 분리
        UnityPlayerHolder.detachFromParent()
        super.onDestroy()
    }

    // ✅ 더 이상 onBackPressed() 오버라이드하지 않음 (경고/에러 제거)
}
