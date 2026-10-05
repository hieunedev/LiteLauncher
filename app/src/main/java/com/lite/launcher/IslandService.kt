package com.lite.launcher

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "Đảo động": 1 viên thuốc đen ở giữa trên cùng, chỉ tồn tại khi có cuộc gọi / nhạc / hẹn giờ / tin nhắn...
 * Lúc rảnh KHÔNG có cửa sổ nào -> không tốn CPU/RAM. Dùng chính quyền đọc thông báo nên không cần service riêng.
 */
class IslandService : NotificationListenerService() {
    private val ui = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var box: LinearLayout? = null
    private var text: TextView? = null
    private var curKey: String? = null
    private var curSbn: StatusBarNotification? = null
    private val live = setOf("call", "transport", "alarm", "msg", "navigation", "progress", "stopwatch", "reminder", "service", "status", "event")
    private val collapse = Runnable { collapseNow() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun enabled() = getSharedPreferences("launcher", 0).getBoolean("island", false) && Settings.canDrawOverlays(this)

    override fun onListenerConnected() {
        super.onListenerConnected()
        ui.post {
            if (!enabled()) return@post
            activeNotifications?.sortedByDescending { it.postTime }?.firstOrNull()?.let { handleNotification(it) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        ui.post { handleNotification(sbn) }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        if (!enabled() || sbn.packageName == packageName) return
        val n = sbn.notification
        val category = n.category
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if ((category == null || category !in live) && !ongoing) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)
        val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)
        if (title == null && body == null) return
        curKey = sbn.key
        curSbn = sbn
        show(listOfNotNull(title, body).joinToString(": "), sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ui.post { if (sbn.key == curKey) hide() }
    }

    override fun onListenerDisconnected() { ui.post { hide() } }
    override fun onDestroy() { hide(); super.onDestroy() }

    private fun ensure(): LinearLayout {
        box?.let { return it }
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(22).toFloat() }
            setOnClickListener { try { curSbn?.notification?.contentIntent?.send() } catch (_: Exception) {}; hide() }
        }
        b.addView(ImageView(this).apply { tag = "i" }, LinearLayout.LayoutParams(dp(20), dp(20)))
        val t = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(220)
            setPadding(dp(8), 0, 0, 0)
        }
        b.addView(t); text = t
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(6) } // chỉnh y nếu lệch với camera
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try { wm?.addView(b, lp) } catch (e: Exception) { return b }
        box = b
        return b
    }

    private fun show(msg: String, sbn: StatusBarNotification) {
        val b = ensure()
        (b.findViewWithTag<ImageView>("i")).apply {
            setImageDrawable(try { sbn.notification.smallIcon?.loadDrawable(this@IslandService) } catch (_: Exception) { null })
            setColorFilter(Color.WHITE)
        }
        text?.apply { this.text = msg; visibility = View.VISIBLE }
        b.animate().cancel(); b.scaleX = .6f; b.scaleY = .6f; b.alpha = 0f
        b.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
        ui.removeCallbacks(collapse); ui.postDelayed(collapse, 3500)
    }

    /** Thông báo "đang diễn ra" (cuộc gọi, nhạc...) thu lại thành chấm nhỏ; tin nhắn thì biến mất. */
    private fun collapseNow() {
        val ongoing = curSbn?.notification?.flags?.and(Notification.FLAG_ONGOING_EVENT) != 0
        if (ongoing) text?.visibility = View.GONE else hide()
    }

    private fun hide() {
        ui.removeCallbacks(collapse)
        box?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        box = null; text = null; curKey = null; curSbn = null
    }
}
