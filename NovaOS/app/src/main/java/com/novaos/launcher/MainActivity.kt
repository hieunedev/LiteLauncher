package com.novaos.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * First functional NovaOS shell: discovers and launches installed launcher activities.
 * The secure Android keyguard is intentionally not replaced or intercepted.
 */
class MainActivity : Activity() {
    private val bg = Color.rgb(8, 11, 18)
    private val surface = Color.rgb(24, 34, 53)
    private val primary = Color.rgb(244, 247, 252)
    private val secondary = Color.rgb(166, 177, 196)
    private val accent = Color.rgb(131, 185, 255)
    private lateinit var appList: LinearLayout
    private lateinit var search: EditText
    private var apps: List<ResolveInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(30), dp(20), dp(16))
            setBackgroundColor(bg)
        }
        root.addView(label("N O V A   /   ANDROID EXPERIENCE", 10, accent, true), matchWrap())
        root.addView(label("Your space.", 34, primary, true).apply {
            gravity = Gravity.START
            setPadding(0, dp(18), 0, dp(4))
        }, matchWrap())
        root.addView(label("A focused home for your apps.", 14, secondary, false).apply {
            gravity = Gravity.START
            setPadding(0, 0, 0, dp(20))
        }, matchWrap())

        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(secondary)
            setTextColor(primary)
            textSize = 15f
            singleLine = true
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(surface, dp(18), 0x24FFFFFF)
        }
        root.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(label("APPLICATIONS", 11, secondary, true).apply {
            gravity = Gravity.START
            setPadding(2, dp(22), 0, dp(10))
        }, matchWrap())

        val scroll = ScrollView(this).apply { isFillViewport = false }
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(appList)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        setContentView(root)
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (::appList.isInitialized) loadApps()
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
        renderApps(search.text?.toString().orEmpty())
    }

    private fun renderApps(query: String) {
        appList.removeAllViews()
        val filtered = apps.filter {
            it.loadLabel(packageManager).toString().contains(query, ignoreCase = true)
        }
        if (filtered.isEmpty()) {
            appList.addView(label("No matching apps", 14, secondary, false).apply {
                setPadding(0, dp(20), 0, dp(20))
            })
            return
        }
        filtered.forEach { info ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(surface, dp(16), 0x18FFFFFF)
                isClickable = true
                isFocusable = true
                setOnClickListener { launchApp(info) }
            }
            val icon = TextView(this).apply {
                text = "•"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(accent)
                background = rounded(Color.rgb(34, 49, 73), dp(13), 0x22FFFFFF)
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
            val name = label(info.loadLabel(packageManager).toString(), 15, primary, true).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(dp(14), 0, 0, 0)
            }
            row.addView(name, LinearLayout.LayoutParams(0, dp(48), 1f))
            row.addView(label("›", 24, secondary, false))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            appList.addView(row, params)
        }
    }

    private fun launchApp(info: ResolveInfo) {
        val activityInfo = info.activityInfo
        val launch = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(activityInfo.packageName, activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(launch) }
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                       else Typeface.create("sans-serif", Typeface.NORMAL)
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