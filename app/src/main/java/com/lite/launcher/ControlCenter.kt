package com.lite.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

/** Trung tâm điều khiển trong launcher (không thay được bảng cài đặt nhanh của hệ thống). Dựng lại mỗi lần mở -> không giữ RAM. */
class ControlCenter(private val a: Activity) : FrameLayout(a) {
    var onClean: () -> Unit = {}
    var onLaunch: (App) -> Unit = {}
    private val d = a.resources.displayMetrics.density
    private val panel = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; isClickable = true }
    private var ramTv: TextView? = null
    private var torchOn = false
    private var cam: String? = null
    val isOpen get() = visibility == View.VISIBLE

    private fun dp(v: Int) = (v * d + .5f).toInt()

    init {
        visibility = View.GONE; isClickable = true
        setBackgroundColor(Color.argb(82, 0, 0, 0)); setOnClickListener { close() }
        val r = dp(28).toFloat()
        panel.background = GradientDrawable().apply {
            setColor(Color.argb(218, 28, 29, 35))
            setStroke(dp(1), Color.argb(55, 255, 255, 255))
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        }
        panel.elevation = dp(10).toFloat()
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
    }

    private fun label(t: String, sp: Float = 12f) = TextView(a).apply { text = t; setTextColor(Color.WHITE); textSize = sp }

    private fun tile(t: String, click: (TextView) -> Unit) = TextView(a).apply {
        text = t; setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(Color.argb(40, 255, 255, 255)); cornerRadius = dp(20).toFloat() }
        setOnClickListener { try { click(this) } catch (_: Exception) {} }
    }

    private fun row(vararg v: View) = LinearLayout(a).apply {
        v.forEach { addView(it, LinearLayout.LayoutParams(0, dp(66), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
    }

    private fun go(action: String) = a.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun torch(t: TextView) {
        val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cam ?: cm.cameraIdList.firstOrNull { cameraId ->
            cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        cam = id
        torchOn = !torchOn
        cm.setTorchMode(id, torchOn)
        (t.background as GradientDrawable).setColor(if (torchOn) Color.argb(210, 255, 200, 60) else Color.argb(40, 255, 255, 255))
        t.setTextColor(if (torchOn) Color.BLACK else Color.WHITE)
    }

    private fun slider(title: String, max: Int, cur: Int, onStart: () -> Unit, onChange: (Int) -> Unit) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), 0)
        addView(label(title))
        addView(SeekBar(a).apply {
            this.max = max; progress = cur
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { if (u) onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar) = onStart()
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
    }

    private fun ramInfo(): String {
        val mi = ActivityManager.MemoryInfo()
        (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return "RAM trống: ${mi.availMem shr 20} / ${mi.totalMem shr 20} MB"
    }
    fun refreshRam() { ramTv?.text = ramInfo() }

    fun open(recents: List<App>, st: Style, lite: Boolean, topInset: Int, usageOk: Boolean) {
        panel.removeAllViews(); panel.setPadding(dp(14), topInset + dp(12), dp(14), dp(16))
        panel.addView(label("◈  Trung tâm điều khiển", 20f).apply { setPadding(dp(4), 0, 0, dp(8)) })
        panel.addView(row(
            tile("📶\nInternet") { if (Build.VERSION.SDK_INT >= 29) go(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else go(Settings.ACTION_WIFI_SETTINGS) },
            tile("🔵\nBluetooth") { go(Settings.ACTION_BLUETOOTH_SETTINGS) },
            tile("🔦\nĐèn pin") { torch(it) }))
        panel.addView(row(
            tile("✈️\nMáy bay") { go(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
            tile("🔊\nÂm thanh") { if (Build.VERSION.SDK_INT >= 29) go(Settings.Panel.ACTION_VOLUME) else go(Settings.ACTION_SOUND_SETTINGS) },
            tile("⚙️\nCài đặt") { go(Settings.ACTION_SETTINGS) }))

        val cr = a.contentResolver
        panel.addView(slider("☀️ Độ sáng", 255, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128),
            { if (!Settings.System.canWrite(a)) a.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${a.packageName}"))) },
            { p ->
                if (Settings.System.canWrite(a)) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, p.coerceAtLeast(1))
                }
            }))
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        panel.addView(slider("🎵 Âm lượng", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), am.getStreamVolume(AudioManager.STREAM_MUSIC),
            {}, { p -> am.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0) }))

        // --- Đa nhiệm nhẹ: dọn RAM + app dùng gần đây ---
        val info = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(14), dp(6), dp(4)) }
        ramTv = label(ramInfo()); info.addView(ramTv, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        info.addView(TextView(a).apply {
            text = "Dọn RAM"; setTextColor(Color.WHITE); textSize = 12f; setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { setColor(Color.parseColor("#2E7D32")); cornerRadius = dp(18).toFloat() }
            setOnClickListener { onClean() }
        })
        panel.addView(info)
        panel.addView(label("Đa nhiệm gần đây").apply { setPadding(dp(6), dp(8), 0, dp(4)) })
        if (!usageOk) panel.addView(label("Cấp quyền xem app gần đây ›").apply {
            setPadding(dp(6), dp(8), 0, dp(8)); setTextColor(Color.parseColor("#82B1FF"))
            setOnClickListener { go(Settings.ACTION_USAGE_ACCESS_SETTINGS) }
        })
        else if (recents.isEmpty()) panel.addView(label("Chưa có app nào").apply { setPadding(dp(6), dp(8), 0, dp(8)) })
        else panel.addView(HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            addView(LinearLayout(a).apply {
                recents.forEach { app ->
                    addView(IconView(a, st, true, lite).apply { set(app.icon, app.label); setOnClickListener { onLaunch(app) } },
                        LinearLayout.LayoutParams(dp(72), dp(86)))
                }
            })
        })

        visibility = View.VISIBLE; alpha = 0f; panel.translationY = -dp(30).toFloat()
        animate().alpha(1f).setDuration(150).start()
        panel.animate().translationY(0f).setDuration(150).start()
    }

    fun close() {
        if (!isOpen) return
        animate().alpha(0f).setDuration(120).withEndAction { visibility = View.GONE; panel.removeAllViews(); ramTv = null }.start()
    }
}
