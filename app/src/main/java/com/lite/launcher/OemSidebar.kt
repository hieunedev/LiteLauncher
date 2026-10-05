package com.lite.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * Thanh bên thông minh lấy cảm hứng từ Edge Panel / Magic Sidebar.
 * Chỉ dùng View chuẩn, không phụ thuộc Compose hay thư viện nặng.
 */
class OemSidebar(private val a: MainActivity) : FrameLayout(a) {
    var onSearch: () -> Unit = {}
    var onControlCenter: () -> Unit = {}
    var onGameBooster: () -> Unit = {}
    var onWallpaper: () -> Unit = {}
    var onClean: () -> Unit = {}
    var onClose: () -> Unit = {}
    var appsProvider: () -> List<App> = { emptyList() }

    private val d = resources.displayMetrics.density
    private val handle = TextView(a)
    private val panel = LinearLayout(a)
    private var open = false
    private var downX = 0f
    private var tracking = false

    private fun dp(v: Int) = (v * d + .5f).toInt()

    init {
        visibility = View.VISIBLE
        isClickable = true
        clipChildren = false
        clipToPadding = false

        handle.text = "‹"
        handle.gravity = Gravity.CENTER
        handle.textSize = 22f
        handle.setTextColor(Color.WHITE)
        handle.background = glass(220, 30f)
        handle.elevation = dp(8).toFloat()

        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(14), dp(16), dp(14), dp(14))
        panel.background = glass(238, 28f)
        panel.elevation = dp(14).toFloat()

        addView(panel, LayoutParams(dp(318), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END).apply {
            topMargin = dp(10); bottomMargin = dp(10)
            setMargins(0, dp(10), -dp(318), dp(10))
        })
        addView(handle, LayoutParams(dp(30), dp(70), Gravity.END or Gravity.CENTER_VERTICAL))

        handle.setOnTouchListener { _, e -> edgeTouch(e) }
        setOnTouchListener { _, e ->
            if (open) {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    downX = e.rawX; tracking = true
                    true
                } else if (e.actionMasked == MotionEvent.ACTION_UP && tracking) {
                    tracking = false
                    if (e.rawX - downX > dp(70)) close()
                    true
                } else tracking
            } else false
        }
    }

    private fun glass(alpha: Int, radius: Float) = GradientDrawable().apply {
        setColor(Color.argb(alpha, 25, 27, 33))
        setStroke(dp(1), Color.argb(55, 255, 255, 255))
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun row(icon: String, title: String, sub: String, click: () -> Unit) =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(12), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(34, 255, 255, 255))
                cornerRadius = dp(16).toFloat()
            }
            addView(TextView(a).apply {
                text = icon; textSize = 21f; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(42), dp(50)))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(a).apply {
                    text = title; textSize = 14f; setTextColor(Color.WHITE)
                })
                addView(TextView(a).apply {
                    text = sub; textSize = 10f
                    setTextColor(Color.argb(155, 255, 255, 255))
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { click() }
        }

    private fun expandHost() {
        val lp = layoutParams
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams = lp
        }
        bringToFront()
    }

    private fun shrinkHost() {
        val lp = layoutParams
        if (lp != null) {
            lp.width = dp(30)
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.END
            layoutParams = lp
        }
    }

    fun open() {
        if (open) return
        open = true
        build()
        // MainActivity keeps a 30dp host while closed so it doesn't block the launcher.
        // Expand this host before animating; otherwise the 318dp panel is clipped by the parent.
        expandHost()
        val p = panel.layoutParams as ViewGroup.MarginLayoutParams
        p.width = dp(318); p.height = ViewGroup.LayoutParams.MATCH_PARENT
        p.setMargins(0, dp(10), 0, dp(10))
        panel.layoutParams = p
        panel.translationX = dp(318).toFloat()
        handle.text = "›"
        panel.animate().translationX(0f).setDuration(180)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    fun close() {
        if (!open) return
        open = false
        handle.text = "‹"
        panel.animate().translationX(dp(318).toFloat()).setDuration(130)
            .withEndAction {
                val p = panel.layoutParams as ViewGroup.MarginLayoutParams
                p.setMargins(0, dp(10), -dp(318), dp(10))
                panel.layoutParams = p
                shrinkHost()
            }.start()
    }

    fun isOpen() = open

    private fun edgeTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                tracking = true
                return true
            }
            MotionEvent.ACTION_MOVE -> return true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = e.rawX - downX
                tracking = false
                if (!open && dx < -dp(35)) open()
                else if (open && dx > dp(45)) close()
                return true
            }
        }
        return true
    }

    private fun build() {
        panel.removeAllViews()
        panel.addView(TextView(a).apply {
            text = "Thanh bên thông minh"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, dp(4), dp(3))
        })
        panel.addView(TextView(a).apply {
            text = "Edge Panel • Magic Sidebar"
            textSize = 11f
            setTextColor(Color.argb(150, 255, 255, 255))
            setPadding(dp(4), 0, dp(4), dp(12))
        })

        val recent = appsProvider().distinctBy { it.pkg }.take(6)
        if (recent.isNotEmpty()) {
            panel.addView(TextView(a).apply {
                text = "Ứng dụng nhanh"
                textSize = 13f; setTextColor(Color.WHITE)
                setPadding(dp(4), dp(4), dp(4), dp(6))
            })
            panel.addView(HorizontalScrollView(a).apply {
                isHorizontalScrollBarEnabled = false
                addView(LinearLayout(a).apply {
                    recent.forEach { app ->
                        addView(LinearLayout(a).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            addView(IconView(a, a.currentStyle(), true, a.isLiteMode()).apply {
                                set(app.icon, app.label)
                                setOnClickListener { onLaunch(app) }
                            }, LinearLayout.LayoutParams(dp(72), dp(72)))
                        }, LinearLayout.LayoutParams(dp(76), dp(92)))
                    }
                })
            })
        }

        panel.addView(Space(a), LinearLayout.LayoutParams(1, dp(8)))
        panel.addView(row("🔎", "Tìm kiếm", "Mở tìm kiếm ứng dụng") { onSearch(); close() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)).apply { bottomMargin = dp(7) })
        panel.addView(row("🎛️", "Trung tâm điều khiển", "Wi‑Fi, Bluetooth, sáng, âm lượng") { onControlCenter(); close() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)).apply { bottomMargin = dp(7) })
        panel.addView(row("🎮", "Game Booster", "Công cụ nổi khi chơi game") { onGameBooster(); close() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)).apply { bottomMargin = dp(7) })
        panel.addView(row("⚡", "Dọn RAM", "Dọn các tiến trình nền không cần thiết") { onClean() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)).apply { bottomMargin = dp(7) })
        panel.addView(row("🌄", "Hình nền", "Đổi hình nền hệ thống") { onWallpaper(); close() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)).apply { bottomMargin = dp(7) })

        panel.addView(TextView(a).apply {
            text = "Vuốt từ mép phải sang trái để mở • Vuốt panel sang phải để đóng"
            textSize = 10f
            setTextColor(Color.argb(130, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(14), dp(4), 0)
        })
    }

    private fun onLaunch(app: App) {
        onClose()
        a.launchFromSidebar(app)
    }
}
