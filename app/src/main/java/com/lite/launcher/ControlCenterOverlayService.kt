package com.lite.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/** Global MIUI-like pull-down gesture without covering touch input in other apps. */
class ControlCenterOverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var edge: View
    private lateinit var cc: ControlCenter
    private lateinit var lp: WindowManager.LayoutParams
    private var downY = 0f
    private var tracking = false
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density + .5f).toInt()

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        edge = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = e.rawY; tracking = true; true }
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
            onCloseRequested = { hideCenter() }
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
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        lp = WindowManager.LayoutParams(
            -1, dp(34), type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try { wm.addView(root, lp) } catch (_: Exception) { stopSelf() }
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("cc", "Trung tâm điều khiển", NotificationManager.IMPORTANCE_LOW))
        }
        val n = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, "cc").setSmallIcon(android.R.drawable.ic_menu_more).setContentTitle("Lite Launcher").setContentText("Vuốt từ mép trên xuống để mở Trung tâm điều khiển").setOngoing(true).build()
        else Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_more).setContentTitle("Lite Launcher").setContentText("Trung tâm điều khiển").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(4101, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(4101, n)
    }

    private fun showCenter() {
        edge.visibility = View.GONE
        lp.height = WindowManager.LayoutParams.MATCH_PARENT
        try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
        cc.open(emptyList(), null, true, 0, false)
    }

    private fun hideCenter() {
        cc.close()
        lp.height = dp(34)
        edge.visibility = View.VISIBLE
        try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try { wm.removeView(root) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
