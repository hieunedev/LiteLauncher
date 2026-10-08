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
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ControlCenterSkin(val name: String, val panel: Int, val tile: Int, val accent: Int, val primary: Int, val secondary: Int, val scrim: Int)
val CONTROL_CENTER_SKINS = listOf(
    ControlCenterSkin("Đêm than", Color.rgb(25, 27, 32), Color.rgb(53, 56, 63), Color.rgb(22, 133, 248), Color.WHITE, 0xFFD4D7DE.toInt(), 142),
    ControlCenterSkin("Đại dương", Color.rgb(13, 35, 52), Color.rgb(27, 66, 84), Color.rgb(0, 190, 215), Color.WHITE, 0xFFD1E8EF.toInt(), 150),
    ControlCenterSkin("Tím cực quang", Color.rgb(35, 24, 51), Color.rgb(66, 46, 83), Color.rgb(177, 105, 255), Color.WHITE, 0xFFE2D5EF.toInt(), 150),
    ControlCenterSkin("Rừng ngọc", Color.rgb(19, 39, 34), Color.rgb(39, 69, 57), Color.rgb(39, 183, 125), Color.WHITE, 0xFFD2E7DA.toInt(), 150),
    ControlCenterSkin("Sương sáng", Color.rgb(229, 234, 243), Color.rgb(205, 214, 229), Color.rgb(54, 111, 211), Color.rgb(27, 37, 54), Color.rgb(76, 89, 110), 90)
)

/** Trung tâm điều khiển dùng chung cho màn hình chính và lớp phủ toàn hệ thống. */
class ControlCenter(private val a: Context) : FrameLayout(a) {
    var onClean: () -> Unit = {}
    var onLaunch: (App) -> Unit = {}
    var onCloseRequested: (() -> Unit)? = null
    private val d = a.resources.displayMetrics.density
    private val panel = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; isClickable = true }
    private var ramTv: TextView? = null
    private var torchOn = false
    private var cam: String? = null
    private val audio by lazy { a.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val skin: ControlCenterSkin
        get() = CONTROL_CENTER_SKINS[a.getSharedPreferences("launcher", 0).getInt("controlCenterSkin", 0).coerceIn(0, CONTROL_CENTER_SKINS.lastIndex)]
    val isOpen get() = visibility == View.VISIBLE
    private var downX = 0f
    private var downY = 0f

    private fun dp(v: Int) = (v * d + .5f).toInt()
    private fun radius(v: Int) = v * d
    private fun launch(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); a.startActivity(i) }
    private fun go(action: String) = launch(Intent(action))
    private fun surface(color: Int, r: Int = 24) = GradientDrawable().apply {
        setColor(color); setStroke(dp(1), Color.argb(30, Color.red(skin.primary), Color.green(skin.primary), Color.blue(skin.primary))); cornerRadius = radius(r)
    }

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(Color.argb(skin.scrim, 0, 0, 0))
        setOnClickListener { requestClose() }
        panel.background = surface(skin.panel, 30)
        panel.elevation = dp(18).toFloat()
        val scroll = ScrollView(a).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(panel, FrameLayout.LayoutParams(-1, -2))
        }
        addView(scroll, LayoutParams(-1, -1).apply {
            leftMargin = dp(10); rightMargin = dp(10); topMargin = dp(8); bottomMargin = dp(8)
        })
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val closeSwipeDistance = height / 10f
                if (dy < -closeSwipeDistance && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) {
                    val cancel = android.view.MotionEvent.obtain(event).apply {
                        action = android.view.MotionEvent.ACTION_CANCEL
                    }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    requestClose()
                    return true
                }
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                // Let the child view finish cancelling its current touch sequence.
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun requestClose() {
        val callback = onCloseRequested
        if (callback != null) callback() else close()
    }

    private fun label(t: String, sp: Float = 12f, color: Int = skin.primary) = TextView(a).apply {
        text = t; setTextColor(color); textSize = sp
    }

    private fun header(): View = LinearLayout(a).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 34f).apply {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(label(SimpleDateFormat("EEEE, d MMMM", Locale("vi", "VN")).format(Date()), 13f, skin.secondary))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(a).apply {
            text = "⚙"; textSize = 21f; gravity = Gravity.CENTER
            setTextColor(skin.primary); background = surface(if (skin.primary == Color.WHITE) Color.argb(36, 255, 255, 255) else Color.argb(120, 255, 255, 255), 20)
            contentDescription = "Cài đặt"
            setOnClickListener { go(Settings.ACTION_SETTINGS) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(TextView(a).apply {
            text = "×"; textSize = 28f; gravity = Gravity.CENTER
            setTextColor(skin.primary); background = surface(if (skin.primary == Color.WHITE) Color.argb(36, 255, 255, 255) else Color.argb(120, 255, 255, 255), 20)
            contentDescription = "Đóng Trung tâm điều khiển"
            setOnClickListener { requestClose() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
    }

    private fun tile(icon: String, title: String, detail: String = "", active: Boolean = false, click: () -> Unit) =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = surface(if (active) skin.accent else skin.tile, 24)
            addView(TextView(a).apply {
                text = icon; textSize = 24f; gravity = Gravity.CENTER
                setTextColor(if (active) Color.WHITE else skin.primary)
            }, LinearLayout.LayoutParams(dp(38), dp(42)))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                addView(label(title, 13f, if (active) Color.WHITE else skin.primary))
                if (detail.isNotBlank()) addView(label(detail, 10f, if (active) Color.WHITE else skin.secondary))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
            isClickable = true
            setOnClickListener { try { click() } catch (_: Exception) {} }
        }

    private fun quick(icon: String, title: String, active: Boolean = false, click: () -> Unit) =
        LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = surface(if (active) skin.accent else skin.tile, 22)
            addView(label(icon, 21f, if (active) Color.WHITE else skin.primary).apply { gravity = Gravity.CENTER })
            addView(label(title, 10f, if (active) Color.WHITE else skin.secondary).apply {
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(3), dp(3), dp(3), 0)
            })
            isClickable = true
            setOnClickListener { try { click() } catch (_: Exception) {} }
        }

    private fun tileRow(vararg views: View, height: Int = 76) = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(height), 1f).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }) }
    }

    private fun toggleTorch() {
        val manager = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cam ?: manager.cameraIdList.firstOrNull { camera ->
            manager.getCameraCharacteristics(camera).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        cam = id; torchOn = !torchOn; manager.setTorchMode(id, torchOn)
    }

    private fun verticalSlider(title: String, max: Int, progress: Int, onStart: () -> Unit, onChange: (Int) -> Unit) =
        LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
            background = surface(skin.tile, 26)
            addView(label(title, 17f).apply { gravity = Gravity.CENTER })
            val holder = FrameLayout(a)
            val seek = SeekBar(a).apply {
                this.max = max.coerceAtLeast(1); this.progress = progress.coerceIn(0, this.max)
                rotation = -90f
                progressTintList = android.content.res.ColorStateList.valueOf(skin.accent)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(skin.secondary)
                thumbTintList = android.content.res.ColorStateList.valueOf(skin.accent)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) onChange(value) }
                    override fun onStartTrackingTouch(seekBar: SeekBar) { onStart() }
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }
            holder.addView(seek, FrameLayout.LayoutParams(dp(205), dp(52), Gravity.CENTER))
            addView(holder, LinearLayout.LayoutParams(dp(64), dp(208)).apply { topMargin = dp(7) })
        }

    private fun horizontalSlider(title: String, max: Int, progress: Int, onChange: (Int) -> Unit) =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(2))
            background = surface(skin.tile, 22)
            addView(label(title, 18f), LinearLayout.LayoutParams(dp(34), -2))
            addView(SeekBar(a).apply {
                this.max = max.coerceAtLeast(1); this.progress = progress.coerceIn(0, this.max)
                progressTintList = android.content.res.ColorStateList.valueOf(skin.accent)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(skin.secondary)
                thumbTintList = android.content.res.ColorStateList.valueOf(skin.accent)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) onChange(value) }
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
        }

    private fun wifiEnabled(): Boolean = try {
        @Suppress("DEPRECATION")
        (a.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (_: Exception) { false }

    private fun openInternetSettings() {
        if (Build.VERSION.SDK_INT >= 29) go(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        else go(Settings.ACTION_WIFI_SETTINGS)
    }

    private fun openBrightnessAccess() {
        if (!Settings.System.canWrite(a)) launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${a.packageName}")))
    }

    fun refreshRam() { ramTv?.text = ramInfo() }

    fun refreshSkin() {
        setBackgroundColor(Color.argb(skin.scrim, 0, 0, 0))
        panel.background = surface(skin.panel, 30)
    }

    fun open(recents: List<App>, st: Style?, lite: Boolean, topInset: Int, usageOk: Boolean) {
        setBackgroundColor(Color.argb(skin.scrim, 0, 0, 0))
        panel.background = surface(skin.panel, 30)
        panel.removeAllViews()
        panel.setPadding(dp(16), topInset + dp(12), dp(16), dp(14))
        panel.addView(header(), LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) })

        val left = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        left.addView(tile("◉", "Wi-Fi", if (wifiEnabled()) "Đang bật · chạm để xem mạng" else "Đang tắt · chạm để xem mạng", wifiEnabled()) {
            openInternetSettings()
        }, LinearLayout.LayoutParams(-1, dp(88)).apply { bottomMargin = dp(7) })
        left.addView(tile("▰", "Dữ liệu di động", "Cài đặt mạng") {
            openInternetSettings()
        }, LinearLayout.LayoutParams(-1, dp(88)))

        val brightness = Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val mainControls = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
            addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            addView(verticalSlider("☼", 255, brightness, ::openBrightnessAccess) { value ->
                if (Settings.System.canWrite(a)) {
                    Settings.System.putInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceAtLeast(1))
                }
            }, LinearLayout.LayoutParams(dp(76), dp(280)).apply { leftMargin = dp(8) })
        }
        panel.addView(mainControls, LinearLayout.LayoutParams(-1, -2))

        val ringer = audio.ringerMode
        val quickGrid = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(7), 0, dp(2))
            addView(tileRow(
                quick("ϟ", "Đèn pin", torchOn) { toggleTorch() },
                quick("ᛒ", "Bluetooth") { go(Settings.ACTION_BLUETOOTH_SETTINGS) },
                quick("✈", "Máy bay") { go(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
            ))
            addView(tileRow(
                quick("◖", "Im lặng", ringer != AudioManager.RINGER_MODE_NORMAL) {
                    audio.ringerMode = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                },
                quick("⌖", "Vị trí") { go(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
                quick("◐", "Màn hình") { go(Settings.ACTION_DISPLAY_SETTINGS) }
            ))
        }
        panel.addView(quickGrid)

        panel.addView(horizontalSlider("◖", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.getStreamVolume(AudioManager.STREAM_MUSIC)) { value ->
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })

        if (st != null && recents.isNotEmpty()) {
            panel.addView(label("Ứng dụng gần đây", 11f, skin.secondary).apply {
                setPadding(dp(6), dp(13), 0, dp(5))
            })
            panel.addView(HorizontalScrollView(a).apply {
                isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
                addView(LinearLayout(a).apply {
                    recents.take(6).forEach { app ->
                        addView(IconView(a, st, true, lite).apply {
                            set(app.icon, app.label); setOnClickListener { onLaunch(app) }
                        }, LinearLayout.LayoutParams(dp(70), dp(78)))
                    }
                })
            })
        }
        visibility = View.VISIBLE; alpha = 0f; panel.translationY = -dp(30).toFloat()
        animate().alpha(1f).setDuration(150).start(); panel.animate().translationY(0f).setDuration(170).start()
    }

    private fun ramInfo(): String {
        val info = ActivityManager.MemoryInfo()
        (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return "RAM trống: ${info.availMem shr 20} / ${info.totalMem shr 20} MB"
    }

    fun close() {
        if (!isOpen) return
        animate().alpha(0f).setDuration(120).withEndAction { visibility = View.GONE; panel.removeAllViews(); ramTv = null }.start()
    }
}
