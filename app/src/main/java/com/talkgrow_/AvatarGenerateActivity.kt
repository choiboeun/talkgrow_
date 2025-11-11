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

    // ✅ 여기에 Unity 준비 완료 콜백도 함께 정의
    companion object {
        private const val TAG = "AvatarGenerate"

        // ✅ Unity에서 호출됨 (AndroidBridge.Start() -> onUnityReady)
        @JvmStatic
        fun onUnityReady(activity: android.app.Activity) {
            Log.d(TAG, "✅ Unity BridgeObject is ready and active")
            Toast.makeText(activity, "Unity Ready!", Toast.LENGTH_SHORT).show()
        }
    }

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

    private var sending = false
    private val ui = Handler(Looper.getMainLooper())
    private val overlayBlocksTouch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar_generate)

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

        onBackPressedDispatcher.addCallback(this) { goBackToMain() }
        btnClose.setOnClickListener { goBackToMain() }

        unityContainer.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { fixZAndTouch(); raiseUiHard() }
            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })

        hideOverlay()
        btnPause.isEnabled = true
        btnPlay.isEnabled  = true

        btnPlay.setOnClickListener {
            Log.d(TAG, "Play clicked")
            Toast.makeText(this, "재생", Toast.LENGTH_SHORT).show()
            if (sending) return@setOnClickListener
            sending = true

            val payload = intent.getStringExtra("extra_json")
                ?: JSONObject().put("text", "Demo").toString()

            ensureUnityAttached {
                sendToUnityAndPlay(payload)
            }
        }

        btnPause.setOnClickListener {
            Log.d(TAG, "Pause clicked")
            Toast.makeText(this, "일시정지", Toast.LENGTH_SHORT).show()
            sending = false
            UnityPlayer.UnitySendMessage("BridgeObject", "Pause", "")
            showOverlay()
        }

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
        runCatching { UnityPlayer.UnitySendMessage("BridgeObject", "Pause", "") }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun ensureUnityAttached(onReady: () -> Unit) {
        unityContainer.post {
            try {
                UnityHolder.attachTo(unityContainer, this)
                UnityPlayer.currentActivity = this
                UnityPlayer.UnitySendMessage("BridgeObject", "ResetPose", "")
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

    // ✅ payload를 그대로 Unity로 전달
    private fun sendToUnityAndPlay(payload: String) {
        runCatching {
            ui.postDelayed({
                UnityPlayer.UnitySendMessage("BridgeObject", "ResetPose", "")
                Log.d("UnityBridge", "UnitySendMessage 호출 전: $payload")
                UnityPlayer.UnitySendMessage("BridgeObject", "OnReceiveText", payload)
                UnityPlayer.UnitySendMessage("BridgeObject", "Play", "")
                sending = false
                hideOverlay()
                raiseUiHard()
            }, 1000)
        }.onFailure {
            Log.e(TAG, "UnitySendMessage error", it)
            toast("재생 명령 실패")
            restoreButtons()
        }
    }

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
            isClickable = overlayBlocksTouch.not()
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

    override fun onResume() {
        super.onResume()
        UnityHolder.attachTo(unityContainer, this)
        UnityPlayer.currentActivity = this
        UnityPlayer.UnitySendMessage("BridgeObject", "ResetPose", "")
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
        UnityPlayer.UnitySendMessage("BridgeObject", "Pause", "")
        UnityHolder.player?.windowFocusChanged(false)
        UnityHolder.player?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        UnityHolder.detachFrom(unityContainer)
        super.onDestroy()
    }
}
  