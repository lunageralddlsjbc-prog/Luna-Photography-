package com.lunagerald.lunaphotography

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// STYLE ONLY - MANUAL reference layout. Preview untouched, applyProPipeline() only on save.
object IPhoneControls {

    // ---- STATE (single source of truth) ----
    var currentMode = "PHOTO"
    var isManual = true // true = M (MANUAL), false = S (SHUTTER PRIORITY, ISO AUTO)
    var shutterLabel = "1/15s"
    var shutterNs = 66_666_667L // 1/15s
    var isoVal = 250
    var zoomVal = 1f
    var isAuto = false
    var wbAuto = true
    var flashOn = false

    // ---- CALLBACKS - wired in MainActivity, preview changes instantly ----
    var onMode: ((String) -> Unit)? = null
    var onManual: ((shutterNs: Long, iso: Int, isManual: Boolean, isAuto: Boolean) -> Unit)? = null
    var onZoom: ((Float) -> Unit)? = null
    var onShutter: (() -> Unit)? = null
    var onFlip: (() -> Unit)? = null
    var onFlash: ((Boolean) -> Unit)? = null
    var onGallery: (() -> Unit)? = null
    var onWB: ((Boolean) -> Unit)? = null

    private val YELLOW = Color.parseColor("#FFD400")
    private val MODES = listOf("SLO-MO","VIDEO","PHOTO","PORTRAIT","PANO","NIGHT","CINEMATIC")
    // Ruler exactly like MANUAL: shutter speeds -> ISO values, one continuous ruler
    private val RULER = listOf(
        "1/500s" to 2_000_000L, "1/350s" to 2_857_143L, "1/250s" to 4_000_000L,
        "1/180s" to 5_555_556L, "1/125s" to 8_000_000L, "1/60s" to 16_666_667L,
        "1/30s" to 33_333_333L, "1/20s" to 50_000_000L, "1/15s" to 66_666_667L,
        "1/10s" to 100_000_000L, "1/8s" to 125_000_000L, "1/4s" to 250_000_000L,
        "1/2s" to 500_000_000L, "1s" to 1_000_000_000L, "2/3s" to 666_666_667L,
        "ISO 100" to -100L, "ISO 200" to -200L, "ISO 250" to -250L,
        "ISO 320" to -320L, "ISO 400" to -400L, "ISO 640" to -640L,
        "ISO 800" to -800L, "ISO 1000" to -1000L, "ISO 6400" to -6400L
    )

    private lateinit var liveLabel: TextView
    private lateinit var msBtn: TextView
    private lateinit var zoomLabel: TextView
    private lateinit var zoomDot: View
    private lateinit var modeRow: LinearLayout
    private val rulerViews = mutableListOf<TextView>()
    private val tickViews = mutableListOf<View>()

    private fun bg(color: Int, radius: Float = 999f, stroke: Int = 0, strokeColor: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = radius; setColor(color); if (stroke > 0) setStroke(stroke, strokeColor) }

    private fun tv(a: AppCompatActivity, text: String, size: Float = 12f, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(a).apply { this.text = text; textSize = size; setTextColor(color); typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; gravity = Gravity.CENTER }

    fun setup(a: AppCompatActivity, root: ViewGroup) {
        // Remove old overlay if re-called
        root.findViewWithTag<View>("MANUAL_OVERLAY")?.let { root.removeView(it) }
        val overlay = FrameLayout(a).apply { tag = "MANUAL_OVERLAY"; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        root.addView(overlay)

        // ========== 1. TOP BAR ==========
        val top = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.BLACK); setPadding(24, 36, 24, 20) }
        val menu = tv(a, "☰", 24f).apply { setOnClickListener { /* menu */ } }
        val flash = tv(a, "⚡", 22f).apply { setOnClickListener { flashOn = !flashOn; if (flashOn) { alpha = 1f } else { alpha = 0.45f }; onFlash?.invoke(flashOn) } }
        // Yellow pill MANUAL with M circle
        val pill = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = bg(YELLOW, 999f); setPadding(18, 8, 28, 8) }
        val mCircle = tv(a, "M", 13f, Color.BLACK, true).apply { background = bg(Color.TRANSPARENT, 999f, 3, Color.BLACK); setPadding(10, 2, 10, 2) }
        pill.addView(mCircle); pill.addView(tv(a, "  MANUAL", 13f, Color.BLACK, true))
        val aspect = tv(a, "▢", 26f)
        val flip = tv(a, "⇄", 24f).apply { setOnClickListener { onFlip?.invoke() } }
        fun sp(w: Float = 1f) = View(a).apply { layoutParams = LinearLayout.LayoutParams(0, 1, w) }
        top.addView(menu); top.addView(tv(a, "  ", 12f)); top.addView(flash); top.addView(sp()); top.addView(pill); top.addView(sp()); top.addView(aspect); top.addView(tv(a, "  ", 12f)); top.addView(flip)
        overlay.addView(top, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // ========== 3. RIGHT VERTICAL ZOOM ==========
        val zoomWrap = FrameLayout(a).apply { setPadding(0, 0, 28, 0) }
        zoomLabel = tv(a, "1×", 13f, Color.WHITE, true)
        val line = View(a).apply { setBackgroundColor(Color.WHITE); layoutParams = FrameLayout.LayoutParams(3, 520) }
        zoomDot = View(a).apply { background = bg(YELLOW, 999f); layoutParams = FrameLayout.LayoutParams(36, 36) }
        zoomWrap.addView(line); zoomWrap.addView(zoomDot); zoomWrap.addView(zoomLabel)
        // Position dot: bottom = 1x, top = 5x
        fun placeDot(z: Float) { zoomDot.y = (520f - (z - 0.5f) / 4.5f * 520f).coerceIn(0f, 520f); zoomLabel.y = zoomDot.y - 48f; zoomLabel.x = -14f; zoomDot.x = -16f; line.x = 0f }
        placeDot(1f)
        var downY = 0f; var downZoom = 1f
        zoomWrap.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downY = e.y; downZoom = zoomVal; true }
                MotionEvent.ACTION_MOVE -> { val z = (downZoom + (downY - e.y) / 110f).coerceIn(0.5f, 5f); setZoom(z, false); placeDot(z); true }
                else -> false
            }
        }
        overlay.addView(zoomWrap, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 560, Gravity.CENTER_VERTICAL or Gravity.END))

        // ========== BOTTOM ==========
        val bottom = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(24, 8, 24, 12) }

        // Live value bottom-left yellow: "1/15s  ISO 250"
        liveLabel = tv(a, "1/15s\nISO 250", 13f, YELLOW, true).apply { gravity = Gravity.START; setLineSpacing(0f, 1.05f) }
        // ========== 2. BOTTOM HORIZONTAL RULER ==========
        val rulerRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val autoBtn = tv(a, "AUTO", 10f, Color.WHITE, true).apply { background = bg(Color.parseColor("#3A3A3A"), 999f); setPadding(18, 20, 18, 20); setOnClickListener { setAuto(true) } }
        val scroll = HorizontalScrollView(a).apply { layoutParams = LinearLayout.LayoutParams(0, 96, 1f); isHorizontalScrollBarEnabled = false }
        val ruler = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM or Gravity.CENTER_VERTICAL }
        RULER.forEachIndexed { idx, (label, ns) ->
            val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(18, 0, 18, 0) }
            val tick = View(a).apply { setBackgroundColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(2, if (idx % 2 == 0) 28 else 16) }
            val t = tv(a, label, 11f, Color.WHITE).apply { setOnClickListener { selectRuler(idx) } }
            tickViews.add(tick); rulerViews.add(t); col.addView(tick); col.addView(tv(a, "", 2f)); col.addView(t); ruler.addView(col)
        }
        scroll.addView(ruler)
        val plusBtn = tv(a, "+", 20f, Color.WHITE, true).apply { background = bg(Color.parseColor("#3A3A3A"), 999f); setPadding(20, 8, 20, 8); setOnClickListener { val cur = RULER.indexOfFirst { it.first == shutterLabel || it.first == "ISO $isoVal" }; selectRuler((cur + 1).coerceAtMost(RULER.size - 1)) } }
        rulerRow.addView(autoBtn); rulerRow.addView(scroll); rulerRow.addView(plusBtn)
        bottom.addView(liveLabel); bottom.addView(rulerRow)

        // ========== 4. CONTROL ROW ABOVE SHUTTER ==========
        val ctrl = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 18, 0, 6) }
        val autoBox = tv(a, "AUTO", 10f, Color.WHITE, true).apply { background = bg(Color.TRANSPARENT, 12f, 2, Color.WHITE); setPadding(20, 12, 20, 12); setOnClickListener { setAuto(true) } }
        msBtn = tv(a, "M", 14f, Color.BLACK, true).apply { background = bg(YELLOW, 12f); setPadding(28, 16, 28, 16); setOnClickListener { setMode(!isManual) } }
        val wb = tv(a, "WB\nAUTO", 11f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setOnClickListener { wbAuto = !wbAuto; onWB?.invoke(wbAuto) } }
        val oneX = tv(a, "1×", 12f, Color.WHITE, true).apply { background = bg(Color.TRANSPARENT, 999f, 2, Color.WHITE); setPadding(22, 12, 22, 12); setOnClickListener { setZoom(1f, true) } }
        ctrl.addView(autoBox); ctrl.addView(tv(a, "   ", 12f)); ctrl.addView(msBtn); ctrl.addView(sp()); ctrl.addView(wb); ctrl.addView(sp()); ctrl.addView(oneX)
        bottom.addView(ctrl)

        // ========== SHUTTER ROW ==========
        val shutterRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8, 10, 8, 4) }
        val gallery = FrameLayout(a).apply { layoutParams = LinearLayout.LayoutParams(96, 96); background = bg(Color.parseColor("#222222"), 16f, 2, Color.WHITE) }
        val galleryIcon = tv(a, "⊞", 26f).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER) }
        gallery.addView(galleryIcon); gallery.setOnClickListener { onGallery?.invoke() }
        val shutterWrap = FrameLayout(a).apply { layoutParams = LinearLayout.LayoutParams(168, 168) }
        val outer = View(a).apply { background = bg(Color.parseColor("#555555"), 999f); layoutParams = FrameLayout.LayoutParams(168, 168, Gravity.CENTER) }
        val inner = View(a).apply { background = bg(Color.WHITE, 999f); layoutParams = FrameLayout.LayoutParams(136, 136, Gravity.CENTER); setOnClickListener { onShutter?.invoke() } }
        val plusBadge = tv(a, "+", 18f, Color.WHITE, true).apply { background = bg(Color.parseColor("#555555"), 999f); layoutParams = FrameLayout.LayoutParams(48, 48, Gravity.TOP or Gravity.END) }
        shutterWrap.addView(outer); shutterWrap.addView(inner); shutterWrap.addView(plusBadge)
        val hamburger = tv(a, "☰", 26f)
        shutterRow.addView(gallery); shutterRow.addView(sp()); shutterRow.addView(shutterWrap); shutterRow.addView(sp()); shutterRow.addView(hamburger)
        bottom.addView(shutterRow)

        // ========== MODE ROW - ONE ROW ONLY ==========
        val modeScroll = HorizontalScrollView(a).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 64); isHorizontalScrollBarEnabled = false }
        modeRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        MODES.forEach { m -> modeRow.addView(tv(a, m, 13f, if (m == currentMode) YELLOW else Color.WHITE, m == currentMode).apply { setPadding(26, 10, 26, 10); setOnClickListener { currentMode = m; onMode?.invoke(m); refreshModes() } }) }
        modeScroll.addView(modeRow); bottom.addView(modeScroll)

        overlay.addView(bottom, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        refreshAll()
    }

    // ---- LOGIC ----
    private fun refreshAll() { refreshModes(); refreshRuler(); refreshLive(); refreshMS() }
    private fun refreshModes() { for (i in 0 until modeRow.childCount) { val v = modeRow.getChildAt(i) as TextView; val on = v.text.toString() == currentMode; v.setTextColor(if (on) YELLOW else Color.WHITE); v.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT } }
    private fun refreshRuler() { rulerViews.forEachIndexed { i, v -> val active = RULER[i].first == shutterLabel || RULER[i].first == "ISO $isoVal"; v.setTextColor(if (active) YELLOW else Color.WHITE); v.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; tickViews[i].setBackgroundColor(if (active) YELLOW else Color.WHITE) } }
    private fun refreshLive() { liveLabel.text = "$shutterLabel\nISO $isoVal" }
    private fun refreshMS() { msBtn.text = (if (isManual) "M" else "S"; msBtn.setBackgroundColor(YELLOW) }

    fun selectRuler(idx: Int) {
        val (label, ns) = RULER[idx]
        isAuto = false
        if (ns < 0) { isoVal = (-ns).toInt() } else { shutterLabel = label; shutterNs = ns }
        refreshAll(); emit()
    }
    fun setAuto(auto: Boolean) { isAuto = auto; if (auto) { shutterLabel = "AUTO"; isoVal = 0 } refreshAll(); emit() }
    fun setMode(manual: Boolean) { isManual = manual; refreshMS(); refreshAll(); emit() }
    fun setZoom(z: Float, fromButton: Boolean) {
        zoomVal = z.coerceIn(0.5f, 5f)
        zoomLabel.text = if (zoomVal == 1f) "1×" else String.format("%.1f×", zoomVal)
        zoomDot.y = (520f - (zoomVal - 0.5f) / 4.5f * 520f).coerceIn(0f, 520f)
        onZoom?.invoke(zoomVal)
    }
    // Called from MainActivity when pinch / 1x / 2x changes zoom - keeps all in sync
    fun syncZoom(z: Float) { zoomVal = z; zoomLabel.text = if (z == 1f) "1×" else String.format("%.1f×", z) }
    private fun emit() { onManual?.invoke(shutterNs, isoVal, isManual, isAuto) }
}
