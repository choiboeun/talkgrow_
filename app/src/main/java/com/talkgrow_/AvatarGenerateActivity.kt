package com.talkgrow_

import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.talkgrow_.unity.UnityHolder
import com.unity3d.player.UnityPlayer
import org.json.JSONObject

class AvatarGenerateActivity : AppCompatActivity() {

    companion object { private const val TAG = "AvatarGenerate" }

    private lateinit var unityContainer: FrameLayout
    private lateinit var overlay: View
    private lateinit var btnClose: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var loadingBox: LinearLayout
    private lateinit var loadingText: TextView
    private lateinit var previewHint: TextView
    private lateinit var playBar: View
    private lateinit var uiLayer: View

    /** 중복 전송 방지용 */
    private var sending = false
    private val ui = Handler(Looper.getMainLooper())

    /** 오버레이(반투명)로 화면만 덮고 터치는 통과시키는 정책 */
    private val overlayBlocksTouch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar_generate)

        // ── view refs
        unityContainer = findViewById(R.id.unityContainer)
        overlay        = findViewById(R.id.overlayView)
        btnClose       = findViewById(R.id.btnClose)
        btnPlay        = findViewById(R.id.btnPlay)
        btnPause       = findViewById(R.id.btnPause)
        loadingBox     = findViewById(R.id.loadingBox)
        loadingText    = findViewById(R.id.loadingText)
        previewHint    = findViewById(R.id.previewHint)
        playBar        = findViewById(R.id.playBar)
        uiLayer        = findViewById(R.id.uiLayer)

        // 뒤로가기 = 현재 화면만 종료
        onBackPressedDispatcher.addCallback(this) { goBackToMain() }
        btnClose.setOnClickListener { goBackToMain() }

        // Unity가 내부 SurfaceView를 추가/제거할 때마다 Z, 터치 정리
        unityContainer.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { fixZAndTouch(); raiseUiHard() }
            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })

        // 초기 UI
        hideOverlay()
        btnPause.isEnabled = true
        btnPlay.isEnabled  = true

        // ▶ 재생
        btnPlay.setOnClickListener {
            Log.d(TAG, "Play clicked")
            Toast.makeText(this, "재생", Toast.LENGTH_SHORT).show()
            if (sending) return@setOnClickListener
            sending = true

            // 재생에 사용할 payload 준비(없으면 Demo)
            val text = intent.getStringExtra("extra_text") ?: "Demo"
            val payload = JSONObject().put("text", text).toString()

            // Unity 뷰가 컨테이너에 붙어 있지 않다면 먼저 attach
            ensureUnityAttached {
                sendToUnityAndPlay(payload)
            }
        }

        // ⏸ 일시정지
        btnPause.setOnClickListener {
            Log.d(TAG, "Pause clicked")
            Toast.makeText(this, "일시정지", Toast.LENGTH_SHORT).show()
            sending = false
            // 유니티에 Pause 전송
            UnityPlayer.UnitySendMessage("SignPipeline", "Pause", "")
            showOverlay() // 화면 반투명 처리(버튼은 클릭 가능)
        }

        // 디버깅: 실제로 버튼 터치가 도달하는지 로그
        btnPause.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) Log.d(TAG, "Pause touch DOWN")
            false
        }
        btnPlay.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) Log.d(TAG, "Play touch DOWN")
            false
        }
    }

    private fun goBackToMain() {
        finish()
        overridePendingTransition(0, 0)
    }

    /** Unity 엔진은 재사용, 뷰만 현재 activity의 컨테이너에 붙임 */
    private fun ensureUnityAttached(onReady: () -> Unit) {
        unityContainer.post {
            try {
                UnityHolder.attachTo(unityContainer, this)
                UnityPlayer.currentActivity = this

                // 화면 진입 시 자동 재생 방지: Idle 상태로 리셋만 수행(Play는 절대 호출하지 않음)
                UnityPlayer.UnitySendMessage("SignPipeline", "ResetPose", "")

                fixZAndTouch()
                raiseUiHard()
                startDemoteLoopForAWhile()
                onReady()
            } catch (e: Resources.NotFoundException) {
                Log.e(TAG, "Unity attach fail (Resources)", e)
                toast("Unity 초기화 실패: 리소스")
                restoreButtons()
            } catch (t: Throwable) {
                Log.e(TAG, "Unity attach fail", t)
                toast("Unity 초기화 실패: ${t.javaClass.simpleName}")
                restoreButtons()
            }
        }
    }

    /**
     * Android → Unity:
     * 1) ResetPose (Idle 보장)
     * 2) RunFromAndroid(payload)  : CSV/파라미터 로드 등
     * 3) Play                      : 실제 재생 시작
     */
    private fun sendToUnityAndPlay(payload: String) {
        runCatching {
            UnityPlayer.UnitySendMessage("SignPipeline", "ResetPose", "")
            UnityPlayer.UnitySendMessage("SignPipeline", "RunFromAndroid", payload)

            // 짧게 한 템포 두고 Play (러닝타임 초기화 여유)
            ui.postDelayed({
                UnityPlayer.UnitySendMessage("SignPipeline", "Play", "")
                sending = false
                hideOverlay()
                raiseUiHard()
            }, 200)
        }.onFailure {
            Log.e(TAG, "UnitySendMessage error", it)
            toast("재생 명령 실패")
            restoreButtons()
        }
    }

    /** Unity Surface를 아래로, 앱 UI를 위로 올리고 Unity가 터치를 가로채지 않도록 설정 */
    private fun fixZAndTouch() {
        (UnityHolder.player?.view as? ViewGroup)?.let { root ->
            forEachChildRecursive(root) { v ->
                if (v is SurfaceView) {
                    v.setZOrderOnTop(false)
                    v.setZOrderMediaOverlay(false)
                    v.holder.setFormat(PixelFormat.OPAQUE)
                }
                v.isClickable = false
                v.isFocusable = false
                v.isFocusableInTouchMode = false
                v.setOnTouchListener { _, _ -> false }
            }
            root.isClickable = false
            root.isFocusable = false
            root.isFocusableInTouchMode = false
            root.setOnTouchListener { _, _ -> false }
            root.translationZ = 0f
            root.elevation = 0f
        }
    }

    /** 실제 조작 UI를 항상 최상단으로 */
    private fun raiseUiHard() {
        overlay.apply {
            bringToFront()
            translationZ = 1200f
            elevation = 1200f
            isClickable = false
            isFocusable = false
        }
        val tops = listOf(uiLayer, playBar, btnPlay, btnPause, btnClose, previewHint, loadingBox)
        tops.forEach { v ->
            v?.bringToFront()
            v?.translationZ = 2000f
            v?.elevation = 2000f
            if (v === btnPlay || v === btnPause || v === btnClose || v === playBar) {
                v.isClickable = true
                v.isFocusable = true
            }
        }
    }

    /** Unity가 자신의 Z를 바꾸는 걸 잠시 동안 계속 되돌림 */
    private fun startDemoteLoopForAWhile() {
        var left = 50
        val r = object : Runnable {
            override fun run() {
                fixZAndTouch()
                raiseUiHard()
                if (--left > 0) ui.postDelayed(this, 160)
            }
        }
        ui.post(r)
    }

    private fun forEachChildRecursive(vg: ViewGroup, f: (View) -> Unit) {
        for (i in 0 until vg.childCount) {
            val c = vg.getChildAt(i)
            f(c)
            if (c is ViewGroup) forEachChildRecursive(c, f)
        }
    }

    private fun showOverlay() {
        overlay.apply {
            alpha = 0.6f
            visibility = View.VISIBLE
            isClickable = overlayBlocksTouch.not() // false면 터치 통과
            isFocusable = false
        }
        previewHint.visibility = View.VISIBLE
        raiseUiHard()
    }

    private fun hideOverlay() {
        overlay.apply {
            clearAnimation(); animate()?.cancel()
            alpha = 0f
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        previewHint.visibility = View.GONE
        loadingBox.visibility = View.GONE
        btnPlay.isEnabled = true
        btnPause.isEnabled = true
        raiseUiHard()
    }

    private fun restoreButtons() {
        sending = false
        btnPlay.isEnabled = true
        btnPause.isEnabled = true
        raiseUiHard()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ── 생명주기: 자동 재생 방지(Play는 절대 호출하지 않음) ──
    override fun onResume() {
        super.onResume()
        UnityHolder.attachTo(unityContainer, this)
        UnityPlayer.currentActivity = this
        // 화면 복귀 시엔 Idle로만 리셋(자동 재생 X)
        UnityPlayer.UnitySendMessage("SignPipeline", "ResetPose", "")
        hideOverlay()
        startDemoteLoopForAWhile()
    }

    override fun onPostResume() {
        super.onPostResume()
        ui.post { raiseUiHard() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        UnityHolder.player?.windowFocusChanged(hasFocus)
        if (hasFocus) startDemoteLoopForAWhile()
    }

    override fun onPause() {
        // 앱이 내려갈 때는 Unity도 멈추게
        UnityPlayer.UnitySendMessage("SignPipeline", "Pause", "")
        UnityHolder.player?.windowFocusChanged(false)
        UnityHolder.player?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        UnityHolder.detachFrom(unityContainer) // 엔진은 유지
        super.onDestroy()
    }
}

