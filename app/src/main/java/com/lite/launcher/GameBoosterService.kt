package com.lite.launcher

import android.app.*
import android.app.ActivityManager
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlin.math.abs

class GameBoosterService : Service() {
    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var handle: TextView? = null
    private var panel: LinearLayout? = null
    private var gamePkg = ""
    private var gameName = "Game"
    private var expanded = false
    private var downX = 0f
    private var downY = 0f
    private var downWinX = 0
    private var downWinY = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (Build.VERSION.SDK_INT >= 26) {
            startForeground(1901, notification())
        } else {
            startForeground(1901, notification())
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        gamePkg = intent?.getStringExtra("package") ?: gamePkg
        gameName = intent?.getStringExtra("name") ?: gameName
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel("game_booster", "Game Booster", NotificationManager.IMPORTANCE_LOW)
            c.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(c)
        }
    }

    private fun notification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "game_booster") else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Game Booster đang hoạt động")
            .setContentText(gameName)
            .setOngoing(true)
            .build()
    }

    private fun bg(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(226, 22, 23, 29))
        setStroke(dp(1), Color.argb(75, 255, 255, 255))
        cornerRadius = dp(22).toFloat()
    }

    private fun showOverlay() {
        if (root != null) return
        root = FrameLayout(this)
        handle = TextView(this).apply {
            text = "⚡"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = bg()
            setOnTouchListener { _, e -> handleTouch(e) }
        }
        root!!.addView(handle, FrameLayout.LayoutParams(dp(44), dp(76), Gravity.START or Gravity.TOP))

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = bg()
            visibility = View.GONE
            addView(TextView(this@GameBoosterService).apply {
                text = "⚡ Game Booster"
                textSize = 20f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@GameBoosterService).apply {
                text = gameName
                textSize = 12f
                setTextColor(Color.argb(185,255,255,255))
                setPadding(0, dp(2), 0, dp(10))
            })
            addView(status())
            addView(button("🚀 BOOST", ::boost))
            addView(button("🔕 Không làm phiền", ::toggleDnd))
            addView(button("📊 Cập nhật RAM", ::refresh))
            addView(button("× Thu gọn", ::collapse))
        }
        root!!.addView(panel, FrameLayout.LayoutParams(dp(320), dp(330), Gravity.START or Gravity.TOP))

        lp = WindowManager.LayoutParams(
            dp(44), dp(76),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(180)
        }
        try { wm.addView(root, lp) } catch (_: Exception) { stopSelf() }
    }

    private fun status() = TextView(this).apply {
        tag = "ram"
        text = ramText()
        textSize = 12f
        setTextColor(Color.argb(205,255,255,255))
        setPadding(0, 0, 0, dp(8))
    }

    private fun button(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.argb(58,255,255,255))
            cornerRadius = dp(15).toFloat()
        }
        setOnClickListener { click() }
        val p = LinearLayout.LayoutParams(-1, dp(46))
        p.setMargins(0, dp(3), 0, dp(3))
        layoutParams = p
    }

    private fun ramText(): String {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return "RAM trống: " + (mi.availMem shr 20) + " / " + (mi.totalMem shr 20) + " MB"
    }

    private fun refresh() {
        root?.findViewWithTag<TextView>("ram")?.text = ramText()
    }

    private fun boost() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (Build.VERSION.SDK_INT >= 23) {
            am.runningAppProcesses?.forEach { p ->
                if (p.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    p.pkgList.none { it == packageName || it == gamePkg || it == "android" || it.startsWith("com.android.systemui") }) {
                    p.pkgList.forEach { pkg ->
                        try { am.killBackgroundProcesses(pkg) } catch (_: Exception) {}
                    }
                }
            }
        }
        if (Settings.canDrawOverlays(this)) refresh()
    }

    private fun toggleDnd() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 23) {
            if (!nm.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
    }

    private fun handleTouch(e: MotionEvent): Boolean {
        val p = lp ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                downWinX = p.x
                downWinY = p.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - downX).toInt()
                val dy = (e.rawY - downY).toInt()
                if (!expanded) {
                    p.x = (downWinX + dx).coerceIn(0, dp(90))
                    p.y = (downWinY + dy).coerceIn(dp(70), resources.displayMetrics.heightPixels - dp(100))
                } else {
                    p.y = (downWinY + dy).coerceIn(dp(70), resources.displayMetrics.heightPixels - dp(350))
                }
                try { wm.updateViewLayout(root, p) } catch (_: Exception) {}
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = e.rawX - downX
                if (!expanded && dx > dp(35)) expand()
                else if (expanded && dx < -dp(70)) collapse()
                return true
            }
        }
        return true
    }

    private fun expand() {
        expanded = true
        panel?.visibility = View.VISIBLE
        handle?.visibility = View.GONE
        lp?.width = dp(320)
        lp?.height = dp(330)
        lp?.x = 0
        try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
        refresh()
    }

    private fun collapse() {
        expanded = false
        panel?.visibility = View.GONE
        handle?.visibility = View.VISIBLE
        lp?.width = dp(44)
        lp?.height = dp(76)
        lp?.x = 0
        try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try { root?.let { wm.removeView(it) } } catch (_: Exception) {}
        root = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        fun start(c: Context, pkg: String, name: String) {
            if (!Settings.canDrawOverlays(c)) return
            val i = Intent(c, GameBoosterService::class.java)
                .putExtra("package", pkg)
                .putExtra("name", name)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
    }
}
