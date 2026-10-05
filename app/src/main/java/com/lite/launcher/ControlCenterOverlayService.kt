package com.lite.launcher

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Small always-on-top gesture strip. It only captures a short strip at the top edge
 * while the control center is closed, so normal apps/games keep all other touch input.
 */
class ControlCenterOverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var edge: View
    private lateinit var cc: ControlCenter
    private var downY = 0f
    private var tracking = false

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density + .5f).toInt()

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        edge = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = e.rawY; tracking = true; true
                    }
                    MotionEvent.ACTION_MOVE -> tracking
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dy = e.rawY - downY
                        val open = tracking && dy > dp(72)
                        tracking = false
                        if (open) showCenter()
                        true
                    }
                    else -> false
                }
            }
        }
        cc = ControlCenter(this).apply {
            onClean = {}
            onLaunch = { app ->
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(app.pkg, app.cls)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                } catch (_: Exception) {}
                hideCenter()
            }
        }
        root.addView(edge, FrameLayout.LayoutParams(-1, dp(34), Gravity.TOP))
        root.addView(cc, FrameLayout.LayoutParams(-1, -1))
        val type = if (android.os.Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            -1, -1, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try { wm.addView(root, lp) } catch (_: Exception) { stopSelf() }
    }

    private fun showCenter() {
        edge.visibility = View.GONE
        cc.open(emptyList(), null, true, 0, false)
    }

    private fun hideCenter() {
        cc.close()
        edge.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        try { wm.removeView(root) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
