package com.lite.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Dialog
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.recyclerview.widget.*
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val REQ_BIND = 11
    private val REQ_CFG = 12

    private val bg = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var d = 1f
    private lateinit var prefs: SharedPreferences
    private lateinit var st: Style
    private var drawerMode = false
    private var lite = true

    private var items = mutableListOf<Item>()
    private var apps = emptyList<App>()
    private var byId = emptyMap<String, App>()
    private var shown = listOf<App>()

    private lateinit var awm: AppWidgetManager
    private lateinit var host: AppWidgetHost
    private var pendingId = 0

    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var pager: RecyclerView
    private val snap = PagerSnapHelper()
    private lateinit var dock: CellLayout
    private lateinit var dots: Dots
    private lateinit var zone: TextView
    private lateinit var ph: View
    private lateinit var drawer: FrameLayout
    private lateinit var input: EditText
    private lateinit var results: RecyclerView
    private lateinit var cc: ControlCenter
    private lateinit var gameCenter: GameCenter

    private var curPage = 0
    private var dragItem: Item? = null
    private var dragView: View? = null
    private var tgtPage = 0; private var tgtCol = 0; private var tgtRow = 0
    private var tgtRemove = false
    private var menuOn = false
    private var edgeDir = 0
    private var gestureOk = true
    private var pullStartY = 0f
    private var pullingDrawer = false
    private var drawerTouchStartY = 0f
    private var pullingDrawerDown = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = load()
    }

    private fun dp(v: Int) = (v * d + .5f).toInt()
    private fun iconSizePx() = prefs.getInt("iconSize", st.icon).coerceIn(40, 72)
    private fun labelsOn() = prefs.getBoolean("labels", st.label)
    private fun clockCenterOn() = prefs.getBoolean("clockCenter", st.clockCenter)
    private fun clockSp() = prefs.getInt("clockSp", st.clockSp.toInt()).coerceIn(28, 72).toFloat()
    private fun dockAlpha() = prefs.getInt("dockAlpha", st.dockAlpha).coerceIn(0, 90)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun colsOf(p: Int) = if (p < 0) 4 else st.cols
    private fun rowsOf(p: Int) = if (p < 0) 1 else st.rows

    @Suppress("DEPRECATION")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        d = resources.displayMetrics.density
        prefs = getSharedPreferences("launcher", 0)
        st = STYLES[prefs.getInt("style", 0).coerceIn(0, STYLES.size - 1)]
        drawerMode = prefs.getBoolean("drawer", false)
        lite = prefs.getBoolean("lite", true)
        items = (prefs.getString("items", "") ?: "").split("\n").mapNotNull { Item.de(it) }.toMutableList()
        awm = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, 1024)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        root = FrameLayout(this)
        root.setOnApplyWindowInsetsListener { v, i ->
            v.setPadding(0, i.systemWindowInsetTop, 0, i.systemWindowInsetBottom); i
        }

        home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pager = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            snap.attachToRecyclerView(this)
            overScrollMode = View.OVER_SCROLL_NEVER
            setHasFixedSize(true); setItemViewCacheSize(8); itemAnimator = null
            adapter = pageAdapter({ pageCount() }) { buildPage(it) }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (rv.width == 0) return
                    val p = rv.computeHorizontalScrollOffset() / rv.width.toFloat()
                    dots.pos = p.coerceIn(0f, (dots.count - 1).coerceAtLeast(0).toFloat()); dots.invalidate()
                    curPage = Math.round(p)
                }
            })
        }
        home.addView(pager, LinearLayout.LayoutParams(MATCH, 0, 1f))
        dots = Dots(this)
        home.addView(dots, LinearLayout.LayoutParams(MATCH, dp(20)))
        dock = CellLayout(this, 4, 1).apply { setPadding(dp(8), 0, dp(8), 0) }
        home.addView(dock, LinearLayout.LayoutParams(MATCH, dp(80)).apply { bottomMargin = dp(10) })
        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))

        zone = TextView(this).apply {
            text = "Xóa"; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 200, 40, 40)); visibility = View.GONE
        }
        root.addView(zone, FrameLayout.LayoutParams(MATCH, dp(56)))
        ph = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(50, 255, 255, 255)); setStroke(dp(1), Color.argb(150, 255, 255, 255))
                cornerRadius = dp(16).toFloat()
            }
            visibility = View.GONE
        }
        root.addView(ph, FrameLayout.LayoutParams(dp(10), dp(10)))
        buildDrawer()
        cc = ControlCenter(this).apply {
            onLaunch = { a -> launch(a) }
            onClean = { cleanRam() }
        }
        root.addView(cc, FrameLayout.LayoutParams(MATCH, MATCH))
        gameCenter = GameCenter(this).apply { onLaunch = { a -> launch(a); closeGameCenter() }; onBoost = { cleanRam() } }
        root.addView(gameCenter, FrameLayout.LayoutParams(MATCH, MATCH))
        root.setOnDragListener { _, e -> onDrag(e) }
        setContentView(root)
        applyStyle()

        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        })
        load()
    }

    override fun onStart() { super.onStart(); try { host.startListening() } catch (_: Exception) {} }
    override fun onStop() { try { host.stopListening() } catch (_: Exception) {}; super.onStop() }
    override fun onDestroy() { unregisterReceiver(receiver); bg.shutdown(); super.onDestroy() }

    // ================= Dữ liệu & icon =================
    private fun load() {
        val s = st; val px = dp(iconSizePx())
        bg.execute {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name, mask(it.loadIcon(pm), px, s.radius)) }
                .sortedBy { it.nk }
            ui.post {
                apps = list; byId = list.associateBy { it.id }
                if (!prefs.getBoolean("init", false)) initLayout()
                reflow(); refresh()
                if (drawer.visibility == View.VISIBLE) filter(input.text.toString())
            }
        }
    }

    private fun mask(dr: Drawable, px: Int, rad: Float): Bitmap {
        val src = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(src)
        if (Build.VERSION.SDK_INT >= 26 && dr is AdaptiveIconDrawable) {
            val o = px / 4
            for (l in listOf(dr.background, dr.foreground)) { l?.setBounds(-o, -o, px + o, px + o); l?.draw(c) }
        } else { dr.setBounds(0, 0, px, px); dr.draw(c) }
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * rad, px * rad, paint)
        src.recycle()
        return out
    }

    private fun dockPackages(): List<String> {
        val pm = packageManager
        return listOf(
            Intent(Intent.ACTION_DIAL), Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        ).mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.distinct()
    }

    // ================= Mô hình bố cục =================
    private fun save() { prefs.edit().putString("items", items.joinToString("\n") { it.ser() }).apply() }

    private fun pages0() = (items.filter { it.page >= 0 }.maxOfOrNull { it.page } ?: 0) + 1
    private fun pageCount() = pages0() + if (dragItem != null) 1 else 0

    private fun fits(p: Int, c: Int, r: Int, w: Int, h: Int, ign: Item?): Boolean {
        if (c < 0 || r < 0 || c + w > colsOf(p) || r + h > rowsOf(p)) return false
        return items.none { o -> o !== ign && o.page == p && o.col < c + w && c < o.col + o.w && o.row < r + h && r < o.row + o.h }
    }

    private fun place(m: Item, from: Int = 0) {
        val n = pages0()
        for (k in 0 until n) {
            val p = (from + k) % n
            for (r in 0..st.rows - m.h) for (c in 0..st.cols - m.w)
                if (fits(p, c, r, m.w, m.h, m)) { m.page = p; m.col = c; m.row = r; return }
        }
        m.page = n; m.col = 0; m.row = 0
    }

    private fun initLayout() {
        items.clear()
        dockPackages().mapNotNull { p -> apps.firstOrNull { it.pkg == p } }.take(4)
            .forEachIndexed { i, a -> items.add(Item(T_APP, a.id, -1, i, 0)) }
        items.add(Item(T_CLOCK, "clock", 0, 0, 0, st.cols, 2))
        prefs.edit().putBoolean("init", true).apply()
    }

    /** Sau khi đổi kiểu (lưới khác): giữ ô cũ nếu còn vừa, còn lại xếp lại vào chỗ trống. */
    private fun reflow() {
        val all = items.toList(); items.clear()
        val pend = mutableListOf<Item>()
        for (m in all) {
            m.w = m.w.coerceAtMost(colsOf(m.page)); m.h = m.h.coerceAtMost(rowsOf(m.page))
            if (fits(m.page, m.col, m.row, m.w, m.h, null)) items.add(m) else pend.add(m)
        }
        for (m in pend) { if (m.page < 0) m.page = 0; place(m); items.add(m) }
    }

    /** Dọn app đã gỡ, giải tán thư mục thiếu app, và (chế độ Chuẩn) tự xếp app mới vào chỗ trống. */
    private fun sync() {
        if (apps.isEmpty()) return
        val ids = byId.keys
        items.removeAll { it.type == T_APP && it.key !in ids }
        for (f in items.filter { it.type == T_FOLDER }) {
            val l = f.extra.split(";").filter { it.isNotEmpty() && it in ids }
            if (l.size >= 2) f.extra = l.joinToString(";")
            else { items.remove(f); l.firstOrNull()?.let { items.add(Item(T_APP, it, f.page, f.col, f.row)) } }
        }
        if (!drawerMode) {
            val placed = items.flatMap { if (it.type == T_APP) listOf(it.key) else if (it.type == T_FOLDER) it.extra.split(";") else emptyList() }.toSet()
            for (a in apps) if (a.id !in placed) { val m = Item(T_APP, a.id, 0, 0, 0); place(m, curPage.coerceAtLeast(0)); items.add(m) }
        }
    }

    private fun compact() {
        val map = items.filter { it.page >= 0 }.map { it.page }.distinct().sorted().withIndex().associate { it.value to it.index }
        items.forEach { if (it.page >= 0) it.page = map[it.page] ?: 0 }
    }

    private fun refresh() {
        sync(); compact(); save()
        pager.adapter?.notifyDataSetChanged()
        dots.count = pageCount(); dots.invalidate()
        dock.removeAllViews()
        items.filter { it.page < 0 }.forEach { m -> viewFor(m)?.let { dock.addView(it, CellLayout.LP(m.col, 0, 1, 1)) } }
    }

    // ================= Dựng view =================
    private fun pageAdapter(count: () -> Int, bind: (Int) -> View) = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = count()
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            object : RecyclerView.ViewHolder(FrameLayout(p.context).apply { layoutParams = RecyclerView.LayoutParams(MATCH, MATCH) }) {}
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
            (h.itemView as FrameLayout).apply { removeAllViews(); addView(bind(i)) }
        }
    }

    private fun buildPage(i: Int): View = CellLayout(this, st.cols, st.rows).apply {
        setPadding(dp(6), dp(4), dp(6), 0)
        items.filter { it.page == i }.forEach { m -> viewFor(m)?.let { addView(it, CellLayout.LP(m.col, m.row, m.w, m.h)) } }
        // Nhấn giữ vùng trống của màn hình chính -> menu tính năng.
        setOnLongClickListener { showMenu(); true }
        isLongClickable = true
    }

    private fun pageView(i: Int) = (pager.layoutManager?.findViewByPosition(i) as? ViewGroup)?.getChildAt(0) as? CellLayout

    private fun press(v: View) {
        if (lite) return
        v.setOnTouchListener { x, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(.92f).scaleY(.92f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }

    private fun iv(b: Bitmap, label: String, on: Boolean) = IconView(this, st, on, lite).apply { set(b, label); press(this) }

    private fun viewFor(m: Item): View? {
        val v: View = when (m.type) {
            T_APP -> { val a = byId[m.key] ?: return null; iv(a.icon, a.label, labelsOn() && m.page >= 0).also { it.setOnClickListener { launch(a) } } }
            T_FOLDER -> iv(folderBmp(m), m.key, labelsOn() && m.page >= 0).also { it.setOnClickListener { openFolder(m) } }
            T_WIDGET -> {
                val id = m.key.toIntOrNull() ?: return null
                val info = awm.getAppWidgetInfo(id) ?: return null
                host.createView(this, id, info).also { hv ->
                    hv.post { hv.updateAppWidgetSize(null, (m.w * pager.width / st.cols / d).toInt(), (m.h * pager.height / st.rows / d).toInt(),
                        (m.w * pager.width / st.cols / d).toInt(), (m.h * pager.height / st.rows / d).toInt()) }
                }
            }
            else -> clockView()
        }
        v.setOnLongClickListener { startDrag(v, m); true }
        return v
    }

    private fun clockView() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = if (clockCenterOn()) Gravity.CENTER else (Gravity.CENTER_VERTICAL or Gravity.START)
        setPadding(dp(16), 0, dp(16), 0)
        val g = if (clockCenterOn()) Gravity.CENTER_HORIZONTAL else Gravity.START
        addView(TextClock(context).apply {
            format12Hour = "H:mm"; format24Hour = "H:mm"; gravity = g
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSp())
            typeface = Typeface.create(st.font, Typeface.NORMAL)
            if (!lite) setShadowLayer(8f, 0f, 2f, Color.argb(100, 0, 0, 0))
        })
        addView(TextClock(context).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; gravity = g
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
    }

    private fun folderBmp(f: Item): Bitmap {
        val px = dp(st.icon)
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        c.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * st.radius, px * st.radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) })
        val s = (px * .38f).toInt(); val gap = (px - 2 * s) / 3
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        f.extra.split(";").take(4).forEachIndexed { i, id ->
            byId[id]?.let { a ->
                val x = gap + (i % 2) * (s + gap); val y = gap + (i / 2) * (s + gap)
                c.drawBitmap(a.icon, null, Rect(x, y, x + s, y + s), p)
            }
        }
        return b
    }

    // ================= Kéo thả (dùng drag của hệ thống: mượt, không tự vẽ lại) =================
    private fun startDrag(v: View, m: Item) {
        dragItem = m; dragView = v; tgtRemove = false
        if (v.startDragAndDrop(null, View.DragShadowBuilder(v), m, 0)) v.visibility = View.INVISIBLE else dragItem = null
    }

    private fun origin(v: View): IntArray {
        val a = IntArray(2); val b = IntArray(2)
        v.getLocationOnScreen(a); root.getLocationOnScreen(b)
        return intArrayOf(a[0] - b[0], a[1] - b[1])
    }

    private fun onDrag(e: DragEvent): Boolean {
        val m = e.localState as? Item ?: return false
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                zone.visibility = View.VISIBLE; zone.alpha = .7f
                pager.adapter?.notifyItemInserted(pageCount() - 1); dots.count = pageCount(); dots.invalidate()
            }
            DragEvent.ACTION_DRAG_LOCATION -> hover(e.x, e.y, m)
            DragEvent.ACTION_DROP -> drop(m)
            DragEvent.ACTION_DRAG_ENDED -> {
                dragItem = null; dragView?.visibility = View.VISIBLE
                zone.visibility = View.GONE; if (!menuOn) ph.visibility = View.GONE
                edge(0); refresh()
            }
        }
        return true
    }

    private fun hover(x: Float, y: Float, m: Item) {
        tgtRemove = y < zone.bottom
        zone.alpha = if (tgtRemove) 1f else .7f
        if (tgtRemove) { ph.visibility = View.GONE; edge(0); return }
        edge(if (x < dp(32)) -1 else if (x > root.width - dp(32)) 1 else 0)
        val useDock = m.type == T_APP && y >= origin(dock)[1]
        val p = if (useDock) -1 else curPage
        val lay = if (useDock) dock else pageView(curPage) ?: return
        if (lay.cw == 0) return
        val o = origin(lay)
        val fc = (x - o[0] - lay.paddingLeft).toInt() / lay.cw
        val fr = (y - o[1] - lay.paddingTop).toInt() / lay.ch
        tgtPage = p
        tgtCol = (fc - (m.w - 1) / 2).coerceIn(0, (colsOf(p) - m.w).coerceAtLeast(0))
        tgtRow = (fr - (m.h - 1) / 2).coerceIn(0, (rowsOf(p) - m.h).coerceAtLeast(0))
        val lp = ph.layoutParams; val nw = lay.cw * m.w; val nh = lay.ch * m.h
        if (lp.width != nw || lp.height != nh) { lp.width = nw; lp.height = nh; ph.layoutParams = lp }
        ph.translationX = (o[0] + lay.paddingLeft + tgtCol * lay.cw).toFloat()
        ph.translationY = (o[1] + lay.paddingTop + tgtRow * lay.ch - root.paddingTop).toFloat()
        ph.visibility = View.VISIBLE
    }

    private val edgeRun = object : Runnable {
        override fun run() {
            if (edgeDir == 0) return
            pager.smoothScrollToPosition((curPage + edgeDir).coerceIn(0, pageCount() - 1))
            ui.postDelayed(this, 750)
        }
    }
    private fun edge(dir: Int) {
        if (dir == edgeDir) return
        edgeDir = dir; ui.removeCallbacks(edgeRun)
        if (dir != 0) ui.postDelayed(edgeRun, 450)
    }

    private fun drop(m: Item) {
        if (tgtRemove) { removeItem(m); return }
        if (tgtPage == m.page && tgtCol == m.col && tgtRow == m.row) { menuOn = true; ui.post { showItemMenu(m) }; return }
        val p = tgtPage
        val occ = items.firstOrNull { o -> o !== m && o.has(p, tgtCol, tgtRow) }
        if (m.type == T_APP && p >= 0 && occ != null && occ.type == T_APP) {
            occ.type = T_FOLDER; occ.extra = occ.key + ";" + m.key; occ.key = "Thư mục"; items.remove(m); return
        }
        if (m.type == T_APP && p >= 0 && occ != null && occ.type == T_FOLDER) {
            occ.extra += ";" + m.key; items.remove(m); return
        }
        if (fits(p, tgtCol, tgtRow, m.w, m.h, m)) { m.page = p; m.col = tgtCol; m.row = tgtRow; return }
        var best: IntArray? = null; var bd = Int.MAX_VALUE
        for (r in 0..rowsOf(p) - m.h) for (c in 0..colsOf(p) - m.w) if (fits(p, c, r, m.w, m.h, m)) {
            val dd = abs(c - tgtCol) + abs(r - tgtRow); if (dd < bd) { bd = dd; best = intArrayOf(c, r) }
        }
        if (best != null) { m.page = p; m.col = best[0]; m.row = best[1] } else toast("Không đủ chỗ trống")
    }

    private fun removeItem(m: Item) {
        if (m.type == T_APP && !drawerMode) { toast("Chế độ Chuẩn: app luôn hiện trên màn hình. Chuyển sang Ngăn kéo để ẩn app."); return }
        items.remove(m)
        if (m.type == T_WIDGET) m.key.toIntOrNull()?.let { host.deleteAppWidgetId(it) }
    }

    private fun showItemMenu(m: Item) {
        val pm = PopupMenu(this, ph)
        if (m.type == T_APP) { pm.menu.add(0, 1, 0, "Thông tin ứng dụng"); pm.menu.add(0, 2, 0, "Gỡ cài đặt") }
        pm.menu.add(0, 3, 0, "Xóa khỏi màn hình")
        pm.setOnMenuItemClickListener { mi ->
            val a = byId[m.key]
            when (mi.itemId) {
                1 -> a?.let { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.pkg}"))) }
                2 -> a?.let { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${it.pkg}"))) }
                else -> { removeItem(m); refresh() }
            }
            true
        }
        pm.setOnDismissListener { menuOn = false; ph.visibility = View.GONE }
        pm.show()
    }

    // ================= Thư mục =================
    private inner class Cells(private val src: () -> List<App>, private val tap: (App) -> Unit,
                              private val hold: (View, App) -> Unit) : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = src().size
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val v = IconView(this@MainActivity, st, true, lite)
            v.layoutParams = RecyclerView.LayoutParams(MATCH, dp(92))
            v.setOnClickListener { tap(it.tag as App) }
            v.setOnLongClickListener { hold(it, it.tag as App); true }
            press(v)
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, i: Int) { val a = src()[i]; h.v.tag = a; h.v.set(a.icon, a.label) }
    }

    private fun openFolder(f: Item) {
        val dlg = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val list = f.extra.split(";").mapNotNull { byId[it] }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(10)); isClickable = true
            background = GradientDrawable().apply { setColor(Color.parseColor("#E61C1C1E")); cornerRadius = dp(28).toFloat() }
        }
        panel.addView(TextView(this).apply { text = f.key; setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8)) })
        panel.addView(RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 4); overScrollMode = View.OVER_SCROLL_NEVER
            adapter = Cells({ list }, { a -> dlg.dismiss(); launch(a) }, { v, a ->
                PopupMenu(this@MainActivity, v).apply {
                    menu.add(0, 1, 0, "Đưa ra màn hình"); menu.add(0, 2, 0, "Thông tin ứng dụng")
                    setOnMenuItemClickListener { mi ->
                        if (mi.itemId == 1) {
                            f.extra = f.extra.split(";").filter { it != a.id }.joinToString(";")
                            val m = Item(T_APP, a.id, 0, 0, 0); place(m, curPage); items.add(m); dlg.dismiss(); refresh()
                        } else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                        true
                    }
                    show()
                }
            })
        })
        val scrim = FrameLayout(this).apply { setBackgroundColor(Color.argb(120, 0, 0, 0)); setOnClickListener { dlg.dismiss() } }
        scrim.addView(panel, FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply { setMargins(dp(20), 0, dp(20), 0) })
        dlg.setContentView(scrim)
        dlg.window?.setLayout(MATCH, MATCH)
        dlg.show()
    }

    // ================= Widget =================
    private fun pickWidget() {
        val ps = awm.installedProviders.sortedBy { it.loadLabel(packageManager).lowercase() }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Chọn widget")
            .setItems(ps.map { "${it.loadLabel(packageManager)}  (${(it.minWidth / d).toInt()}×${(it.minHeight / d).toInt()}dp)" }.toTypedArray()) { _, i -> addWidget(ps[i]) }
            .show()
    }

    private fun addWidget(p: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId(); pendingId = id
        if (awm.bindAppWidgetIdIfAllowed(id, p.provider)) configureOrAdd(id)
        else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider), REQ_BIND)
    }

    private fun configureOrAdd(id: Int) {
        if (awm.getAppWidgetInfo(id)?.configure != null) {
            try { host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CFG, null) } catch (e: Exception) { finishAdd(id) }
        } else finishAdd(id)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_BIND || req == REQ_CFG) {
            if (res != RESULT_OK) { host.deleteAppWidgetId(pendingId); return }
            if (req == REQ_BIND) configureOrAdd(pendingId) else finishAdd(pendingId)
        }
    }

    private fun finishAdd(id: Int) {
        val info = awm.getAppWidgetInfo(id) ?: return
        val cw = (pager.width / st.cols).coerceAtLeast(1); val chh = (pager.height / st.rows).coerceAtLeast(1)
        val m = Item(T_WIDGET, id.toString(), 0, 0, 0,
            Math.ceil(info.minWidth.toDouble() / cw).toInt().coerceIn(1, st.cols),
            Math.ceil(info.minHeight.toDouble() / chh).toInt().coerceIn(1, st.rows))
        place(m, curPage); items.add(m); refresh(); pager.scrollToPosition(m.page)
    }

    // ================= Menu, kiểu giao diện, chế độ =================
    private fun dlg() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun showMenu() {
        val o = arrayOf("📱 Ngăn ứng dụng", "🎮 Game Center", "🧩 Thêm widget", "🎨 Cá nhân hóa", "🖼️ Kiểu giao diện: " + st.name,
            "📐 Chế độ: " + if (drawerMode) "Ngăn kéo (bấm để đổi sang Chuẩn)" else "Chuẩn (bấm để đổi sang Ngăn kéo)",
            "🎛️ Trung tâm điều khiển & đa nhiệm",
            "🏝️ Đảo động (Dynamic Island): " + if (prefs.getBoolean("island", false)) "Bật" else "Tắt",
            "⚡ Siêu nhẹ (tắt bóng & hiệu ứng): " + if (lite) "Bật" else "Tắt", "🌄 Đổi hình nền", "♻️ Đặt lại bố cục")
        dlg().setItems(o) { _, i ->
            when (i) {
                0 -> openDrawer(false)
                1 -> openGameCenter()
                2 -> pickWidget()
                3 -> showPersonalization()
                4 -> dlg().setTitle("Kiểu giao diện").setSingleChoiceItems(STYLES.map { it.name }.toTypedArray(), STYLES.indexOf(st)) { dd, k ->
                    dd.dismiss(); st = STYLES[k]; prefs.edit().putInt("style", k).apply(); applyStyle(); load()
                }.show()
                5 -> {
                    drawerMode = !drawerMode; prefs.edit().putBoolean("drawer", drawerMode).apply()
                    if (drawerMode) items.removeAll { it.type == T_APP && it.page >= 0 }
                    refresh(); toast(if (drawerMode) "Đã bật Ngăn kéo: vuốt lên để mở" else "Đã về chế độ Chuẩn")
                }
                6 -> openCC()
                7 -> toggleIsland()
                8 -> { lite = !lite; prefs.edit().putBoolean("lite", lite).apply(); applyStyle(); refresh() }
                9 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hình nền"))
                else -> { initLayout(); reflow(); refresh() }
            }
        }.show()
    }

    private fun showPersonalization() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }

        box.addView(Button(this).apply {
            text = "🎨 Kiểu giao diện: ${st.name}"
            setOnClickListener {
                dlg().setTitle("Chọn kiểu giao diện")
                    .setSingleChoiceItems(STYLES.map { it.name }.toTypedArray(), STYLES.indexOf(st)) { dd, k ->
                        dd.dismiss()
                        st = STYLES[k]
                        prefs.edit().putInt("style", k).apply()
                        applyStyle()
                        load()
                        toast("Đã đổi kiểu giao diện")
                    }.show()
            }
        })

        val iconValue = TextView(this).apply { text = "Kích thước biểu tượng: ${iconSizePx()} dp"; setTextColor(Color.WHITE) }
        box.addView(iconValue)
        box.addView(SeekBar(this).apply {
            max = 32; progress = iconSizePx() - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { iconValue.text = "Kích thước biểu tượng: ${p + 40} dp" }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })

        val labels = Switch(this).apply {
            text = "Hiện tên ứng dụng"; setTextColor(Color.WHITE); isChecked = labelsOn()
        }
        box.addView(labels)

        val center = Switch(this).apply {
            text = "Đồng hồ ở giữa"; setTextColor(Color.WHITE); isChecked = clockCenterOn()
        }
        box.addView(center)

        val clockValue = TextView(this).apply { text = "Cỡ đồng hồ: ${clockSp().toInt()} sp"; setTextColor(Color.WHITE) }
        box.addView(clockValue)
        box.addView(SeekBar(this).apply {
            max = 44; progress = clockSp().toInt() - 28
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { clockValue.text = "Cỡ đồng hồ: ${p + 28} sp" }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })

        val dockValue = TextView(this).apply { text = "Độ trong suốt thanh dock: ${dockAlpha()}%"; setTextColor(Color.WHITE) }
        box.addView(dockValue)
        box.addView(SeekBar(this).apply {
            max = 90; progress = dockAlpha()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { dockValue.text = "Độ trong suốt thanh dock: $p%" }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })

        box.addView(Button(this).apply {
            text = "🧩 Thêm widget"
            setOnClickListener { pickWidget() }
        })
        box.addView(Button(this).apply {
            text = "🎛️ Mở Trung tâm điều khiển"
            setOnClickListener { openCC() }
        })
        box.addView(Button(this).apply {
            text = "🏝️ Cài đặt Đảo động"
            setOnClickListener { toggleIsland() }
        })

        dlg().setTitle("Cá nhân hóa")
            .setView(box)
            .setNegativeButton("Đóng", null)
            .setPositiveButton("Lưu") { _, _ ->
                val iconSb = box.getChildAt(2) as SeekBar
                val clockSb = box.getChildAt(6) as SeekBar
                val dockSb = box.getChildAt(8) as SeekBar
                prefs.edit()
                    .putInt("iconSize", iconSb.progress + 40)
                    .putBoolean("labels", labels.isChecked)
                    .putBoolean("clockCenter", center.isChecked)
                    .putInt("clockSp", clockSb.progress + 28)
                    .putInt("dockAlpha", dockSb.progress)
                    .apply()
                applyStyle()
                load()
                refresh()
            }.show()
    }

    private fun applyStyle() {
        dock.background = if (st.dockAlpha > 0) GradientDrawable().apply {
            setColor(Color.argb(dockAlpha(), 255, 255, 255)); cornerRadius = dp(st.dockRadius).toFloat()
        } else null
        (dock.layoutParams as LinearLayout.LayoutParams).apply { leftMargin = dp(st.dockMargin); rightMargin = dp(st.dockMargin) }
        dock.requestLayout()
        (results.layoutManager as GridLayoutManager).spanCount = st.cols
        results.adapter = Cells({ shown }, { launch(it) }, { v, a -> appMenu(v, a) })
    }

    // ================= Ngăn kéo + tìm kiếm =================
    private fun buildDrawer() {
        drawer = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT); visibility = View.GONE; isClickable = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(18), dp(12), dp(8))
            background = GradientDrawable().apply {
                // Glass trong suốt: vẫn nhìn thấy màn hình chính phía dưới.
                setColor(Color.argb(112, 20, 22, 28))
                setStroke(dp(1), Color.argb(70, 255, 255, 255))
                cornerRadii = floatArrayOf(dp(30).toFloat(), dp(30).toFloat(), dp(30).toFloat(), dp(30).toFloat(), 0f, 0f, 0f, 0f)
            }
            elevation = dp(10).toFloat()
        }
        input = EditText(this).apply {
            hint = "Tìm kiếm ứng dụng"; setHintTextColor(Color.parseColor("#99FFFFFF")); setTextColor(Color.WHITE)
            setSingleLine(); setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply { setColor(Color.parseColor("#26FFFFFF")); cornerRadius = dp(24).toFloat() }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = filter(s.toString())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        results = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 4); overScrollMode = View.OVER_SCROLL_NEVER
            setHasFixedSize(true); itemAnimator = null
        }
        col.addView(input, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        col.addView(results, LinearLayout.LayoutParams(MATCH, 0, 1f))
        drawer.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun appMenu(v: View, a: App) {
        PopupMenu(this, v).apply {
            if (drawerMode) menu.add(0, 0, 0, "Thêm vào màn hình chính")
            menu.add(0, 1, 0, "Thông tin ứng dụng"); menu.add(0, 2, 0, "Gỡ cài đặt")
            setOnMenuItemClickListener { mi ->
                val u = Uri.parse("package:${a.pkg}")
                when (mi.itemId) {
                    0 -> { val m = Item(T_APP, a.id, 0, 0, 0); place(m, curPage); items.add(m); closeDrawer(); refresh(); pager.scrollToPosition(m.page) }
                    1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, u))
                    else -> startActivity(Intent(Intent.ACTION_DELETE, u))
                }
                true
            }
            show()
        }
    }

    private fun filter(q: String) {
        val k = norm(q.trim())
        shown = if (k.isEmpty()) apps else apps.filter { it.nk.contains(k) }
        results.adapter?.notifyDataSetChanged()
    }

    private fun openDrawer(focus: Boolean) {
        if (drawer.visibility == View.VISIBLE) return
        filter("")
        drawer.visibility = View.VISIBLE
        drawer.alpha = 1f
        drawer.translationY = root.height.toFloat()
        drawer.animate().translationY(0f).setDuration(190).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        if (focus) {
            input.requestFocus()
            ui.postDelayed({ (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0) }, 190)
        }
    }

    private fun startDrawerPull() {
        if (drawer.visibility == View.VISIBLE || cc.isOpen || gameCenter.isOpen) return
        filter("")
        drawer.visibility = View.VISIBLE
        drawer.alpha = 1f
        drawer.translationY = root.height.toFloat()
        pullingDrawer = true
    }

    private fun updateDrawerPull(dy: Float) {
        if (pullingDrawer) drawer.translationY = (root.height + dy).coerceIn(0f, root.height.toFloat())
    }

    private fun finishDrawerPull(open: Boolean) {
        if (!pullingDrawer) return
        pullingDrawer = false
        if (open) {
            drawer.animate().translationY(0f).setDuration(115).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        } else {
            drawer.animate().translationY(root.height.toFloat()).setDuration(100).withEndAction { drawer.visibility = View.GONE }.start()
        }
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        pullingDrawer = false
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        drawer.animate().translationY(root.height.toFloat()).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).withEndAction {
            drawer.visibility = View.GONE; input.setText("")
        }.start()
    }

    private fun launch(a: App) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(a.pkg, a.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            closeDrawer(); cc.close()
        } catch (_: Exception) {}
    }

    // ================= Trung tâm điều khiển, đa nhiệm nhẹ, đảo động =================
    @Suppress("DEPRECATION")
    private fun hasUsage() = (getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED

    private fun recentApps(): List<App> {
        if (!hasUsage()) return emptyList()
        val um = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val l = um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now) ?: return emptyList()
        val byPkg = apps.associateBy { it.pkg }
        return l.filter { it.lastTimeUsed > 0 }.sortedByDescending { it.lastTimeUsed }
            .mapNotNull { byPkg[it.packageName] }.distinctBy { it.pkg }.take(8)
    }

    private fun openCC() { closeDrawer(); closeGameCenter(); cc.open(recentApps(), st, lite, root.paddingTop, hasUsage()) }

    private fun openGameCenter() { closeDrawer(); cc.close(); gameCenter.open(apps, st, lite) }

    private fun closeGameCenter() { if (::gameCenter.isInitialized) gameCenter.close() }

    private fun cleanRam() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        fun free(): Long { val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); return mi.availMem }
        val before = free(); val keep = dockPackages().toSet() + packageName
        apps.map { it.pkg }.distinct().filter { it !in keep }.forEach { am.killBackgroundProcesses(it) }
        ui.postDelayed({ cc.refreshRam(); toast("Đã dọn thêm khoảng ${(free() - before).coerceAtLeast(0) shr 20} MB RAM") }, 900)
    }

    private fun toggleIsland() {
        val on = !prefs.getBoolean("island", false)
        prefs.edit().putBoolean("island", on).apply()
        if (!on) { toast("Đã tắt Đảo động"); return }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            toast("Bật 'Hiển thị trên ứng dụng khác' rồi quay lại chọn mục này lần nữa"); return
        }
        val l = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        if (!l.contains(packageName)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            toast("Bật quyền đọc thông báo cho Lite Launcher")
        } else toast("Đã bật Đảo động")
    }

    // ================= Cử chỉ & phím =================
    private val gestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || abs(vy) < abs(vx)) return false
                // Vuốt xuống từ góc trên bên phải: Trung tâm điều khiển.
                // Vuốt lên ở bất kỳ vị trí nào: Ngăn ứng dụng.
                if (e2.y - e1.y > dp(80)) {
                    if (e1.y < root.height * .3f && e1.x > root.width * .5f) openCC()
                } else if (e1.y - e2.y > dp(80)) {
                    openDrawer(false)
                }
                return false
            }
        })
    }

    private fun onWidget(x: Float, y: Float): Boolean {
        val pv = pageView(curPage) ?: return false
        for (i in 0 until pv.childCount) {
            val c = pv.getChildAt(i)
            if (c is AppWidgetHostView) {
                val l = IntArray(2); c.getLocationOnScreen(l)
                if (x >= l[0] && x <= l[0] + c.width && y >= l[1] && y <= l[1] + c.height) return true
            }
        }
        return false
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        // Ngăn kéo đang mở: vuốt xuống để kéo cả ngăn kéo theo ngón tay.
        // Thả đủ xa -> đóng; kéo chưa đủ -> tự trượt về vị trí mở.
        if (drawer.visibility == View.VISIBLE && !cc.isOpen && !gameCenter.isOpen) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    drawerTouchStartY = e.rawY
                    pullingDrawerDown = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - drawerTouchStartY
                    if (!pullingDrawerDown && dy > dp(12)) pullingDrawerDown = true
                    if (pullingDrawerDown) {
                        drawer.translationY = dy.coerceIn(0f, root.height.toFloat())
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pullingDrawerDown) {
                        val dy = e.rawY - drawerTouchStartY
                        pullingDrawerDown = false
                        if (dy > dp(58)) {
                            closeDrawer()
                        } else {
                            drawer.animate()
                                .translationY(0f)
                                .setDuration(140)
                                .setInterpolator(android.view.animation.DecelerateInterpolator())
                                .start()
                        }
                        return true
                    }
                }
            }
            return super.dispatchTouchEvent(e)
        }

        if (!cc.isOpen && !gameCenter.isOpen) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pullStartY = e.rawY
                    pullingDrawer = false
                    gestureOk = !onWidget(e.rawX, e.rawY)
                    if (gestureOk) gestures.onTouchEvent(e)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - pullStartY
                    if (gestureOk && !pullingDrawer && dy < -dp(12) && pullStartY > root.height * .35f) {
                        startDrawerPull()
                    }
                    if (pullingDrawer) {
                        updateDrawerPull(dy)
                        return true
                    }
                    if (gestureOk) gestures.onTouchEvent(e)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = e.rawY - pullStartY
                    if (pullingDrawer) {
                        finishDrawerPull(dy < -dp(58))
                        return true
                    }
                    if (gestureOk) gestures.onTouchEvent(e)
                }
            }
        }
        return super.dispatchTouchEvent(e)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) { closeDrawer(); cc.close(); pager.smoothScrollToPosition(0) }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (gameCenter.isOpen) gameCenter.close()
        else if (cc.isOpen) cc.close()
        else if (drawer.visibility == View.VISIBLE) closeDrawer()
        else pager.smoothScrollToPosition(0)
    }
}