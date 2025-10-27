package com.talkgrow_.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 카메라 프리뷰 위에 작은 디버그 오버레이를 띄워
 * "윈도우 프레임 카운트/요구 길이" 등을 보여줍니다.
 */
class WindowCounterOverlay(
    private val activity: Activity,
    private val container: ViewGroup // Preview가 들어있는 최상단 FrameLayout/ConstraintLayout 등
) {
    private val tv: TextView = TextView(activity).apply {
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setPadding(12, 8, 12, 8)
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0x66000000) // 반투명 검정
    }

    init {
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = 12
            topMargin = 12
        }

        if (container is FrameLayout) {
            container.addView(tv, lp)
        } else {
            // 다른 레이아웃이면 FrameLayout로 감싸서 올려두는 게 가장 간단
            val wrap = FrameLayout(activity)
            container.addView(
                wrap,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            wrap.addView(tv, lp)
        }
    }

    fun update(
        windowCount: Int,
        requiredWindow: Int,
        isActive: Boolean,
        hasPose: Boolean,
        hasAnyHand: Boolean
    ) {
        val state = if (isActive) "ACTIVE" else "IDLE"
        val track = "pose=${hasPose.takeIf { it } ?: false}, hand=${hasAnyHand.takeIf { it } ?: false}"
        val txt = "win $windowCount/$requiredWindow  |  $state  |  $track"
        activity.runOnUiThread { tv.text = txt }
    }

    fun setVisible(visible: Boolean) = activity.runOnUiThread {
        tv.alpha = if (visible) 1f else 0f
    }
}
