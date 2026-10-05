package com.lite.launcher

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

/** MIUI-inspired quick settings panel. It can also be hosted by the global overlay service. */
class ControlCenter(private val a: Context) : FrameLayout(a) {
    var onClean: () -> Unit = {}
    var onLaunch: (App) -> Unit = {}
    private val d = a.resources.displayMetrics.density
    private val panel = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; isClickable = true }
    private var ramTv: TextView? = null
    private var torchOn = false
    private var cam: String? = null
    val isOpen get() = visibility == View.VISIBLE

    private fun dp(v: Int) = (v * d + .5f).toInt()
    private fun launch(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); a.startActivity(i) }
    private fun radius(v: Int) = v * d

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(Color.argb(118, 0, 0, 0))
        setOnClickListener { close() }
        panel.background = GradientDrawable().apply {
            setColor(Color.argb(222, 24, 25, 30))
            setStroke(dp(1), Color.argb(48, 255, 255, 255))
            cornerRadius = radius(30)
        }
        panel.elevation = dp(16).toFloat()
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            leftMargin = dp(10); rightMargin = dp(10); topMargin = dp(8)
        })
    }

    private fun label(t: String, sp: Float = 12f) = TextView(a).apply {
        text = t; setTextColor(Color.WHITE); textSize = sp
    }

    private fun card(bg: Int = Color.argb(48, 255, 255, 255), r: Int = 22) = GradientDrawable().apply {
        setColor(bg); setStroke(dp(1), Color.argb(24, 255, 255, 255)); cornerRadius = radius(r)
    }

    private fun tile(icon: String, title: String, active: Boolean = false, click: (TextView) -> Unit) =
        LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true
            background = card(if (active) Color.argb(205, 48, 116, 235) else Color.argb(52, 255, 255, 255), 22)
            addView(TextView(a).apply { text = icon; textSize = 22f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(MATCH_PARENT, dp(28)))
            addView(TextView(a).apply { text = title; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(MATCH_PARENT, dp(25)))
            setOnClickListener { try { click(this.findViewById<TextView>(android.R.id.text1) ?: this.getChildAt(1) as TextView) } catch (_: Exception) {} }
        }

    private fun quick(icon: String, title: String, click: () -> Unit) = TextView(a).apply {
        text = "$icon\n$title"; gravity = Gravity.CENTER; textSize = 11f; setTextColor(Color.WHITE)
        background = card(Color.argb(48, 255, 255, 255), 20)
        setOnClickListener { try { click() } catch (_: Exception) {} }
    }

    private fun row(vararg v: View, h: Int = 76) = LinearLayout(a).apply {
        v.forEach { addView(it, LinearLayout.LayoutParams(0, dp(h), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
    }

    private fun go(action: String) = launch(Intent(action))

    private fun torch() {
        val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cam ?: cm.cameraIdList.firstOrNull { x -> cm.getCameraCharacteristics(x).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return
        cam = id; torchOn = !torchOn; cm.setTorchMode(id, torchOn)
    }

    private fun slider(title: String, max: Int, cur: Int, onStart: () -> Unit, onChange: (Int) -> Unit) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), 0)
        addView(label(title, 11f))
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
        panel.removeAllViews()
        val wide = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        panel.setPadding(dp(14), topInset + dp(12), dp(14), dp(14))

        panel.addView(LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("Trung tâm điều khiển", 20f), LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(label("⌄", 25f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(42), dp(42)))
        })

        // MIUI-style: connectivity 2x2 on the left, media card on the right.
        val connectivity = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        connectivity.addView(row(
            quick("📶", "Wi-Fi") { if (Build.VERSION.SDK_INT >= 29) go(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else go(Settings.ACTION_WIFI_SETTINGS) },
            quick("📱", "Dữ liệu") { if (Build.VERSION.SDK_INT >= 29) go(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else go(Settings.ACTION_WIRELESS_SETTINGS) }))
        connectivity.addView(row(
            quick("🔵", "Bluetooth") { go(Settings.ACTION_BLUETOOTH_SETTINGS) },
            quick("✈️", "Máy bay") { go(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }))

        val media = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(14), dp(10), dp(14), dp(10)); background = card(Color.argb(48,255,255,255), 24)
            addView(label("♪  Không phát", 13f)); addView(label("‹    ▶    ›", 26f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4)) })
            addView(label("Âm thanh đa phương tiện", 10f).apply { setTextColor(Color.argb(175,255,255,255)); gravity = Gravity.CENTER })
        }
        val top = LinearLayout(a).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.HORIZONTAL }
        top.addView(connectivity, LinearLayout.LayoutParams(0, if (wide) dp(160) else dp(160), 1.45f))
        top.addView(media, LinearLayout.LayoutParams(0, dp(160), 1f).apply { setMargins(dp(6), dp(4), dp(4), dp(4)) })
        panel.addView(top)

        panel.addView(row(
            quick("🔦", "Đèn pin") { torch() },
            quick("🔕", "Im lặng") { go(Settings.ACTION_SOUND_SETTINGS) },
            quick("⚙️", "Cài đặt") { go(Settings.ACTION_SETTINGS) },
            h = 72))
        panel.addView(row(
            quick("📸", "Camera") { launch(Intent("android.media.action.STILL_IMAGE_CAMERA")) },
            quick("📍", "Vị trí") { go(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
            quick("🔒", "Khóa màn") { try { go(Settings.ACTION_SECURITY_SETTINGS) } catch (_: Exception) {} },
            h = 72))

        val cr = a.contentResolver
        panel.addView(slider("☀  Độ sáng", 255, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128),
            { if (!Settings.System.canWrite(a)) launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${a.packageName}"))) },
            { p -> if (Settings.System.canWrite(a)) { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL); Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, p.coerceAtLeast(1)) } }))

        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        panel.addView(slider("🔊  Âm lượng", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), am.getStreamVolume(AudioManager.STREAM_MUSIC), {}, { p -> am.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0) }))

        if (recents.isNotEmpty()) {
            panel.addView(label("Ứng dụng gần đây", 11f).apply { setPadding(dp(6), dp(8), 0, dp(3)) })
            panel.addView(HorizontalScrollView(a).apply {
                isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
                addView(LinearLayout(a).apply { recents.take(6).forEach { app -> addView(IconView(a, st, true, lite).apply { set(app.icon, app.label); setOnClickListener { onLaunch(app) } }, LinearLayout.LayoutParams(dp(70), dp(78))) } })
            })
        }

        visibility = View.VISIBLE; alpha = 0f; panel.translationY = -dp(35).toFloat()
        animate().alpha(1f).setDuration(150).start(); panel.animate().translationY(0f).setDuration(170).start()
    }

    fun close() {
        if (!isOpen) return
        animate().alpha(0f).setDuration(120).withEndAction { visibility = View.GONE; panel.removeAllViews(); ramTv = null }.start()
    }
}
