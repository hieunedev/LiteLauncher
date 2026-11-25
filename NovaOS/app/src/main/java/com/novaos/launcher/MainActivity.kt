package com.novaos.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal native Views shell. This is an initial scaffold, not a complete launcher.
 * Platform launcher discovery and home-grid behavior are implemented in later phases.
 */
class MainActivity : Activity() {
    private val bg = Color.rgb(8, 11, 18)
    private val textPrimary = Color.rgb(244, 247, 252)
    private val textSecondary = Color.rgb(166, 177, 196)
    private val accent = Color.rgb(131, 185, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(42), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        val eyebrow = label("N O V A   /   ANDROID EXPERIENCE", 11, accent, true)
        root.addView(eyebrow, matchWrap())

        val clock = label("09:41", 58, textPrimary, true).apply {
            setPadding(0, dp(34), 0, 0)
        }
        root.addView(clock, matchWrap())

        root.addView(label("TUESDAY  ·  YOUR SPACE", 12, textSecondary, false).apply {
            setPadding(0, dp(4), 0, dp(30))
        }, matchWrap())

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = rounded(Color.rgb(24, 34, 53), dp(24), 0x24FFFFFF)
        }
        card.addView(label("WELCOME TO NOVAOS", 12, accent, true))
        card.addView(label("A calmer Android experience.", 21, textPrimary, true).apply {
            setPadding(0, dp(10), 0, dp(6))
        })
        card.addView(label("Your launcher foundation is ready to grow.", 14, textSecondary, false))
        root.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val footer = label("FOUNDATION BUILD  ·  v0.1.0", 10, textSecondary, true).apply {
            setPadding(0, dp(28), 0, 0)
        }
        root.addView(footer, matchWrap())

        setContentView(root)
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                       else Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }

    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        setStroke(dp(1), stroke)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
