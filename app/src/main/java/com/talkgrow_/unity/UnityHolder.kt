package com.talkgrow_.unity

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.unity3d.player.UnityPlayer

object UnityHolder {
    @Volatile
    var player: UnityPlayer? = null

    fun ensurePlayer(context: Context, currentActivity: Activity): UnityPlayer {
        val p = player ?: UnityPlayer(currentActivity).also { player = it } // ← Activity 컨텍스트
        UnityPlayer.currentActivity = currentActivity
        return p
    }


    /** 컨테이너에 Unity 뷰를 붙임(재사용) */
    fun attachTo(container: FrameLayout, activity: Activity) {
        val p = ensurePlayer(container.context, activity)
        if (p.view.parent !== container) {
            // 이전 부모에서 떼어내고 붙이기
            (p.view.parent as? FrameLayout)?.removeView(p.view)
            container.addView(
                p.view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        UnityPlayer.currentActivity = activity
        p.requestFocus()
        p.resume()
        p.windowFocusChanged(true)
    }

    /** 화면에서만 떼어냄(엔진은 유지) */
    fun detachFrom(container: FrameLayout) {
        val p = player ?: return
        if (p.view.parent === container) container.removeView(p.view)
        p.windowFocusChanged(false)
        p.pause()
        // ❌ p.destroy() / p.quit() 금지 (프로세스 종료 유발 가능)
    }

    /** 정말 앱을 끝낼 때만 호출(보통은 쓰지 않음) */
    fun destroyAndClear() {
        val p = player ?: return
        // 제조사/Unity 버전에 따라 프로세스 종료를 유발할 수 있음
        runCatching { p.windowFocusChanged(false); p.pause(); /* p.destroy() 금지 권장 */ }
        player = null
    }
}
