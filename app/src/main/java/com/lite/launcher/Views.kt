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

/** Small launcher menu glyphs drawn as vectors so they stay crisp and emoji-free. */
class MenuGlyphView(c: Context, private val glyph: String) : View(c) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tint = when (glyph) {
        "game", "bolt" -> Color.rgb(255, 190, 92)
        "widget", "magic", "island" -> Color.rgb(189, 151, 255)
        "palette", "wallpaper" -> Color.rgb(255, 133, 160)
        "control", "globe" -> Color.rgb(100, 207, 255)
        "reset", "pin" -> Color.rgb(113, 222, 177)
        else -> Color.rgb(139, 178, 255)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = 42f * d
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 48f, size / 48f)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(32, Color.red(tint), Color.green(tint), Color.blue(tint))
        canvas.drawRoundRect(0f, 0f, 48f, 48f, 15f, 15f, paint)
        paint.color = tint
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        fun line(vararg xy: Float) {
            val path = Path().apply {
                moveTo(xy[0], xy[1])
                for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
            }
            canvas.drawPath(path, paint)
        }
        fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 3f) =
            canvas.drawRoundRect(l, t, r, b, radius, radius, paint)
        fun circle(x: Float, y: Float, r: Float) = canvas.drawCircle(x, y, r, paint)

        when (glyph) {
            "apps" -> { rect(11f, 11f, 21f, 21f); rect(27f, 11f, 37f, 21f); rect(11f, 27f, 21f, 37f); rect(27f, 27f, 37f, 37f) }
            "game" -> { rect(9f, 16f, 39f, 33f, 7f); line(16f, 24f, 23f, 24f); line(19.5f, 20.5f, 19.5f, 27.5f); circle(31f, 22f, 1f); circle(35f, 27f, 1f) }
            "widget" -> { rect(10f, 10f, 38f, 38f, 7f); line(24f, 16f, 24f, 32f); line(16f, 24f, 32f, 24f) }
            "palette" -> { circle(24f, 24f, 14f); circle(19f, 20f, 1.3f); circle(26f, 17f, 1.3f); circle(31f, 23f, 1.3f); circle(20f, 29f, 1.3f) }
            "style" -> { rect(11f, 11f, 37f, 37f, 6f); line(17f, 18f, 31f, 18f); line(17f, 24f, 31f, 24f); line(17f, 30f, 26f, 30f) }
            "layout" -> { rect(10f, 12f, 38f, 36f, 5f); line(19f, 12f, 19f, 36f); line(29f, 12f, 29f, 36f); line(10f, 24f, 38f, 24f) }
            "magic" -> { line(24f, 10f, 27f, 20f, 37f, 24f, 27f, 27f, 24f, 38f, 21f, 27f, 11f, 24f, 21f, 20f, 24f, 10f); line(35f, 10f, 35f, 16f); line(32f, 13f, 38f, 13f) }
            "bolt" -> line(27f, 9f, 16f, 26f, 24f, 26f, 21f, 39f, 33f, 21f, 25f, 21f, 27f, 9f)
            "wallpaper" -> { rect(10f, 12f, 38f, 36f, 5f); circle(29f, 19f, 2.5f); line(13f, 32f, 21f, 24f, 27f, 29f, 31f, 25f, 36f, 31f) }
            "control" -> { circle(24f, 24f, 14f); line(24f, 13f, 24f, 35f); line(13f, 24f, 35f, 24f); circle(24f, 24f, 4f) }
            "globe" -> { circle(24f, 24f, 14f); line(10f, 24f, 38f, 24f); line(24f, 10f, 24f, 38f); canvas.drawOval(17f, 10f, 31f, 38f, paint) }
            "pin" -> { line(24f, 38f, 15f, 25f); circle(24f, 20f, 9f); circle(24f, 20f, 3f) }
            "island" -> { rect(13f, 13f, 35f, 35f, 9f); line(18f, 24f, 30f, 24f); circle(17f, 18f, 1f); circle(31f, 30f, 1f) }
            "reset" -> { line(14f, 19f, 14f, 12f, 21f, 12f); canvas.drawArc(12f, 12f, 37f, 37f, 205f, 285f, false, paint); line(34f, 29f, 34f, 36f, 27f, 36f) }
        }
        canvas.restore()
    }
}
