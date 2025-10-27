package com.talkgrow_.unity

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.unity3d.player.UnityPlayer

/**
 * 하나의 UnityPlayer 인스턴스를 재사용(attach/detach)하는 홀더.
 * - 절대 quit() 하지 않는다 (검은 화면/크래시 원인).
 * - 액티비티 전환 시 detach → 다른 액티비티에서 attach.
 */
object UnityPlayerHolder {

    private var _player: UnityPlayer? = null
    val player: UnityPlayer
        get() = _player ?: error("UnityPlayer not created. call ensurePlayer(context) first.")

    /** 없으면 생성, 있으면 그대로 재사용 */
    @Synchronized
    fun ensurePlayer(context: Context): UnityPlayer {
        if (_player == null) {
            _player = UnityPlayer(context.applicationContext)
        }
        return _player!!
    }

    /** 현재 어디에도 붙어있지 않게 분리 */
    fun detachFromParent() {
        val view = _player ?: return
        (view.parent as? ViewGroup)?.removeView(view)
    }

    /** 액티비티/컨테이너에 붙이기 */
    fun attachTo(activity: Activity, container: ViewGroup) {
        ensurePlayer(activity)
        detachFromParent()
        // Unity 뷰를 FrameLayout에 꽉 채우기
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(player.view, lp)

        UnityPlayer.currentActivity = activity

        player.windowFocusChanged(true)
        player.resume()
    }

    /** 포커스/생명주기 포워딩 */
    fun onResume() { _player?.resume() }
    fun onPause()  { _player?.pause()  }
    fun onLowMemory() { _player?.lowMemory() }

    /** Unity 쪽으로 메시지를 보낼 때 사용 (필요 시) */
    fun sendMessage(gameObject: String, method: String, arg: String) {
        UnityPlayer.UnitySendMessage(gameObject, method, arg)
    }
}

