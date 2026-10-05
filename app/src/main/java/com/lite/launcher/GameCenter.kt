package com.lite.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class GameCenter(private val a: Activity) : FrameLayout(a) {
    var onLaunch: (App) -> Unit = {}
    var onBoost: () -> Unit = {}
    private val d = a.resources.displayMetrics.density
    private val panel = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; isClickable = true }
    private var ram: TextView? = null
    val isOpen get() = visibility == View.VISIBLE
    private fun dp(v: Int) = (v * d + .5f).toInt()

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(Color.argb(92, 0, 0, 0))
        setOnClickListener { close() }
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun text(s: String, sp: Float = 13f) = TextView(a).apply {
        text = s; setTextColor(Color.WHITE); textSize = sp
    }

    private fun glass() = GradientDrawable().apply {
        setColor(Color.argb(222, 24, 25, 30))
        setStroke(dp(1), Color.argb(70, 255, 255, 255))
        cornerRadii = floatArrayOf(dp(30).toFloat(), dp(30).toFloat(), dp(30).toFloat(), dp(30).toFloat(), 0f, 0f, 0f, 0f)
    }

    private fun ramInfo(): String {
        val mi = ActivityManager.MemoryInfo()
        (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return "RAM trống: " + (mi.availMem shr 20) + " / " + (mi.totalMem shr 20) + " MB"
    }

    private fun isGame(x: App): Boolean {
        val p = x.pkg.lowercase()
        val n = x.label.lowercase()
        return p.contains("game") || p.contains("play") ||
            listOf("free fire", "call of duty", "genshin", "delta force", "honor of kings",
                "minecraft", "roblox", "pubg", "liên quân", "arena", "valorant").any { n.contains(it) }
    }

    private fun tile(title: String, sub: String, click: () -> Unit) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.argb(58, 255, 255, 255)); setStroke(dp(1), Color.argb(35, 255, 255, 255)); cornerRadius = dp(20).toFloat()
        }
        addView(text(title, 18f).apply { gravity = Gravity.CENTER })
        addView(text(sub, 10f).apply { gravity = Gravity.CENTER; setTextColor(Color.argb(190,255,255,255)) })
        setOnClickListener { click() }
    }

    fun open(apps: List<App>, st: Style, lite: Boolean) {
        panel.removeAllViews()
        panel.background = glass()
        panel.setPadding(dp(16), dp(34), dp(16), dp(18))
        panel.addView(text("🎮 Game Center", 24f))
        panel.addView(text("Tối ưu nhẹ • không root • không can thiệp CPU governor", 11f).apply {
            setTextColor(Color.argb(190,255,255,255)); setPadding(0, dp(2), 0, dp(12))
        })

        val stats = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        ram = text(ramInfo(), 12f)
        stats.addView(ram, LinearLayout.LayoutParams(0, dp(42), 1f))
        stats.addView(Button(a).apply {
            text = "⚡ BOOST"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.argb(120, 40, 120, 255)); cornerRadius = dp(18).toFloat() }
            setOnClickListener { onBoost(); ram?.text = ramInfo() }
        }, LinearLayout.LayoutParams(dp(105), dp(42)))
        panel.addView(stats)

        panel.addView(LinearLayout(a).apply {
            listOf(
                tile("🔕", "Không làm phiền") { a.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                tile("☀️", "Màn hình") { a.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
                tile("🧹", "Dọn nền") { onBoost(); ram?.text = ramInfo() }
            ).forEach { addView(it, LinearLayout.LayoutParams(0, dp(76), 1f).apply { setMargins(dp(3), dp(6), dp(3), dp(6)) }) }
        })

        panel.addView(text("Trò chơi", 16f).apply { setPadding(0, dp(8), 0, dp(6)) })
        val games = apps.filter { isGame(it) }.distinctBy { it.pkg }.take(12)
        val scroll = ScrollView(a).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val grid = GridLayout(a).apply { columnCount = 3; useDefaultMargins = false }
        games.forEach { game ->
            val iv = IconView(a, st, true, lite).apply { set(game.icon, game.label); setOnClickListener { onLaunch(game) } }
            grid.addView(iv, GridLayout.LayoutParams().apply {
                width = 0; height = dp(92); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(3), dp(4), dp(3))
            })
        }
        scroll.addView(grid, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        panel.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(Button(a).apply {
            text = "⚙ Cài đặt tối ưu game"
            setOnClickListener { a.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })

        visibility = View.VISIBLE
        alpha = 0f
        panel.translationY = dp(45).toFloat()
        animate().alpha(1f).setDuration(140).start()
        panel.animate().translationY(0f).setDuration(220).start()
    }

    fun close() {
        if (!isOpen) return
        animate().alpha(0f).setDuration(120).withEndAction {
            visibility = View.GONE
            panel.removeAllViews()
        }.start()
    }
}
