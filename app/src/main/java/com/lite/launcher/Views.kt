package com.lite.launcher

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

const val T_APP = 0
const val T_WIDGET = 1
const val T_FOLDER = 2
const val T_CLOCK = 3

/** 1 phần tử trên màn hình chính. page = -1 nghĩa là nằm ở dock. */
class Item(var type: Int, var key: String, var page: Int, var col: Int, var row: Int,
           var w: Int = 1, var h: Int = 1, var extra: String = "") {
    fun has(p: Int, c: Int, r: Int) = page == p && c >= col && c < col + w && r >= row && r < row + h
    fun ser() = listOf(type, key, page, col, row, w, h, extra).joinToString("\t")
    companion object {
        fun de(s: String): Item? = try {
            val f = s.split("\t")
            Item(f[0].toInt(), f[1], f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt(), f.getOrElse(7) { "" })
        } catch (e: Exception) { null }
    }
}

class App(val label: String, val pkg: String, val cls: String, val icon: Bitmap) {
    val id = "$pkg/$cls"
    val nk = norm(label)
}

fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "").lowercase().replace('đ', 'd')

/** Một "kiểu giao diện" lấy cảm hứng từ các hãng (chỉ bố cục/hình dạng, không dùng tài nguyên của hãng). */
class Style(val name: String, val cols: Int, val rows: Int, val icon: Int, val radius: Float,
            val label: Boolean, val labelSp: Float, val dockAlpha: Int, val dockRadius: Int,
            val dockMargin: Int, val clockCenter: Boolean, val clockSp: Float, val font: String)

val STYLES = listOf(
    Style("ColorOS / Realme UI", 4, 5, 52, .27f, true, 11f, 50, 30, 12, true, 54f, "sans-serif-thin"),
    Style("One UI (kiểu Samsung)", 4, 6, 54, .32f, true, 11f, 45, 26, 10, false, 40f, "sans-serif"),
    Style("HyperOS / MIUI (kiểu Xiaomi)", 4, 6, 52, .30f, true, 11f, 60, 34, 14, false, 58f, "sans-serif-medium"),
    Style("Kiểu iOS", 4, 6, 56, .225f, true, 11f, 55, 36, 8, true, 40f, "sans-serif-light"),
    Style("Pixel (icon tròn)", 5, 5, 50, .5f, true, 11f, 0, 0, 0, false, 48f, "sans-serif-light"),
    Style("Tối giản (không nhãn)", 4, 6, 46, .5f, false, 11f, 0, 0, 0, true, 64f, "sans-serif-thin")
)

/** Icon vẽ trực tiếp bằng 1 View duy nhất (không ImageView + TextView) -> rất nhẹ cho máy yếu. */
class IconView(c: Context, st: Style, private val labelOn: Boolean, lite: Boolean) : View(c) {
    private val dn = resources.displayMetrics.density
    private var bmp: Bitmap? = null
    private var label = ""
    private var ell: String? = null
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = st.labelSp * dn
        if (!lite) setShadowLayer(3f, 0f, 1f, Color.argb(120, 0, 0, 0))
    }
    fun set(b: Bitmap, l: String) { bmp = b; label = l; ell = null; invalidate() }
    override fun onDraw(cv: Canvas) {
        val b = bmp ?: return
        val gap = if (labelOn) 5 * dn else 0f
        val th = if (labelOn) tp.fontSpacing else 0f
        val top = (height - b.height - gap - th) / 2f
        cv.drawBitmap(b, (width - b.width) / 2f, top, null)
        if (labelOn) {
            val t = ell ?: TextUtils.ellipsize(label, tp, width - 6 * dn, TextUtils.TruncateAt.END).toString().also { ell = it }
            cv.drawText(t, width / 2f, top + b.height + gap - tp.ascent(), tp)
        }
    }
}

class VH(val v: IconView) : RecyclerView.ViewHolder(v)

/** Lưới ô: mỗi con có (col,row,w,h). Đo/bố trí 1 lần, không dùng layout lồng nhau. */
class CellLayout(c: Context, var cols: Int, var rows: Int) : ViewGroup(c) {
    class LP(val col: Int, val row: Int, val w: Int, val h: Int) : ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    var cw = 0
    var ch = 0
    override fun onMeasure(wm: Int, hm: Int) {
        val w = MeasureSpec.getSize(wm); val h = MeasureSpec.getSize(hm)
        setMeasuredDimension(w, h)
        cw = (w - paddingLeft - paddingRight) / cols; ch = (h - paddingTop - paddingBottom) / rows
        for (i in 0 until childCount) {
            val v = getChildAt(i); val p = v.layoutParams as LP
            v.measure(MeasureSpec.makeMeasureSpec(cw * p.w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch * p.h, MeasureSpec.EXACTLY))
        }
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val v = getChildAt(i); val p = v.layoutParams as LP
            val x = paddingLeft + p.col * cw; val y = paddingTop + p.row * ch
            v.layout(x, y, x + cw * p.w, y + ch * p.h)
        }
    }
}

class Dots(c: Context) : View(c) {
    var count = 1
    var pos = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        if (count < 2) return
        val dn = resources.displayMetrics.density
        val gap = 14 * dn
        val x0 = (width - (count - 1) * gap) / 2f
        val cy = height / 2f
        p.color = Color.parseColor("#80FFFFFF")
        for (i in 0 until count) cv.drawCircle(x0 + i * gap, cy, 3 * dn, p)
        p.color = Color.WHITE
        cv.drawCircle(x0 + pos * gap, cy, 4 * dn, p)
    }
}
