package com.lunagerald.lunaphotography

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.view.animation.DecelerateInterpolator

// CLEAN iPhone 17 Pro Max style - 1 screen, 1 sheet, 1 slider at a time
class IPhoneControls(val ctx: Context, val root: FrameLayout) {
  var isManual = false; private set
  var isAuto = true; private set
  var isoVal = 100; private set // NEVER 0, min 100
  var shutterNs = 4000000L; private set // 1/250s
  var wbVal = 5500; private set
  var onCapture: (() -> Unit)? = null
  var onMode: ((Boolean) -> Unit)? = null // true=VIDEO
  var onManualChange: ((Long, Int, Int, Boolean) -> Unit)? = null

  private val YELLOW = Color.parseColor("#FFCC00")
  private lateinit var topBar: LinearLayout
  private lateinit var manualBtn: TextView
  private lateinit var isoBtn: TextView
  private lateinit var sBtn: TextView
  private lateinit var wbBtn: TextView
  private lateinit var manualRow: LinearLayout
  private lateinit var shutterBtn: FrameLayout
  private lateinit var thumbView: ImageView
  private lateinit var sheet: LinearLayout
  private lateinit var sheetTitle: TextView
  private lateinit var sheetValue: TextView
  private lateinit var slider: SeekBar
  private var openSheet: String? = null // "ISO" | "S" | "WB" | null

  fun build(): View {
    val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; layoutParams = FrameLayout.LayoutParams(-1,-1) }
    // TOP: flash | MANUAL | flip - clean
    topBar = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(24, 24, 24, 0) }
    val flash = tv("⚡"); val flip = tv("⇄")
    manualBtn = tv("Ⓜ MANUAL").apply { setOnClickListener { setManual(!isManual) } }
    topBar.addView(flash); topBar.addView(manualBtn.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { gravity = Gravity.CENTER } }); topBar.addView(flip)
    col.addView(topBar)
    // NO SPACER - overlay ra, dili magduso sa preview
    // MANUAL ROW: 3 small buttons ONLY when MANUAL - hidden in clean mode
    manualRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; visibility = View.GONE; setPadding(0,0,0,12) }
    isoBtn = chip("ISO 100") { open("ISO") }; sBtn = chip("S  1/250") { open("S") }; wbBtn = chip("WB  5500K") { open("WB") }
    manualRow.addView(isoBtn); manualRow.addView(sBtn); manualRow.addView(wbBtn)
    col.addView(manualRow)
    // SHUTTER ROW
    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(24, 0, 24, 8) }
    thumbView = ImageView(ctx).apply { layoutParams = LinearLayout.LayoutParams(96,96); setBackgroundColor(Color.DKGRAY) }
    shutterBtn = FrameLayout(ctx).apply { layoutParams = LinearLayout.LayoutParams(168,168).apply { leftMargin=40; rightMargin=40 } }
    val outer = View(ctx).apply { layoutParams = FrameLayout.LayoutParams(168,168); background = bg(Color.WHITE, 999f, 6, Color.WHITE) }
    val inner = View(ctx).apply { layoutParams = FrameLayout.LayoutParams(140,140).apply { gravity = Gravity.CENTER }; background = bg(Color.WHITE, 999f) }
    shutterBtn.addView(outer); shutterBtn.addView(inner)
    shutterBtn.setOnClickListener { doCapture() }
    val spacer = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(96,96) }
    row.addView(thumbView); row.addView(shutterBtn); row.addView(spacer)
    col.addView(row)
    // VIDEO / PHOTO - ONLY this in clean bottom
    val modes = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0,4,0,24) }
    val vBtn = tv("VIDEO").apply { setOnClickListener { setVideo(true) } }
    val pBtn = tv("PHOTO").apply { setTextColor(YELLOW); setOnClickListener { setVideo(false) } }
    modes.addView(vBtn); modes.addView(pBtn.apply { layoutParams = LinearLayout.LayoutParams(-2,-2).apply { leftMargin = 48 } })
    col.addView(modes)
    // BOTTOM SHEET - 1 slider only
    sheet = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setBackgroundColor(Color.parseColor("#1C1C1E")); setPadding(24, 16, 24, 32) }
    val handle = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(80, 8).apply { gravity = Gravity.CENTER }; background = bg(Color.GRAY, 999f) }
    val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    sheetTitle = tv("ISO"); sheetValue = tv("ISO 100").apply { setTextColor(YELLOW) }
    val close = tv("✕").apply { setOnClickListener { closeSheet() } }
    head.addView(sheetTitle); head.addView(sheetValue.apply { layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { gravity = Gravity.CENTER } }); head.addView(close)
    slider = SeekBar(ctx).apply { setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) { if (fromUser) applySlider(pr) }
      override fun onStartTrackingTouch(s: SeekBar?) {}
      override fun onStopTrackingTouch(s: SeekBar?) {}
    }) }
    sheet.addView(handle); sheet.addView(head); sheet.addView(slider)
    root.addView(col); root.addView(sheet.apply { layoutParams = FrameLayout.LayoutParams(-1,-2).apply { gravity = Gravity.BOTTOM } })
    refresh(); return col
  }
  private fun chip(t: String, onClick: () -> Unit) = TextView(ctx).apply { text = t; textSize = 13f; setTextColor(Color.WHITE); setPadding(28,16,28,16); background = bg(Color.parseColor("#333333"), 999f); layoutParams = LinearLayout.LayoutParams(-2,-2).apply { leftMargin=12; rightMargin=12 }; setOnClickListener { onClick() } }
  private fun tv(t: String) = TextView(ctx).apply { text = t; textSize = 16f; setTextColor(Color.WHITE); setPadding(16,16,16,16) }
  private fun bg(c: Int, r: Float, stroke: Int = 0, sc: Int = 0): android.graphics.drawable.GradientDrawable { val d = android.graphics.drawable.GradientDrawable(); d.setColor(c); d.cornerRadius = r; if (stroke>0) d.setStroke(stroke, sc); return d }
  private fun setVideo(video: Boolean) { onMode?.invoke(video) }
  fun setManual(m: Boolean) {
    isManual = m
    if (m) { isAuto = false } // M ON = AUTO OFF - bug fix
    manualBtn.setBackgroundColor(if (m) YELLOW else Color.TRANSPARENT); manualBtn.setTextColor(if (m) Color.BLACK else Color.WHITE)
    manualRow.visibility = if (m) View.VISIBLE else View.GONE
    if (!m) closeSheet()
    emit()
  }
  fun open(which: String) {
    if (!isManual) return
    openSheet = which
    when (which) {
      "ISO" -> { sheetTitle.text = "ISO"; slider.max = 55; slider.progress = (isoVal - 100) / 100; sheetValue.text = "ISO $isoVal" }
      "S" -> { sheetTitle.text = "Shutter Speed"; slider.max = 100; slider.progress = 50; sheetValue.text = "1/250s" }
      "WB" -> { sheetTitle.text = "White Balance"; slider.max = 100; slider.progress = 50; sheetValue.text = "${wbVal}K" }
    }
    sheet.visibility = View.VISIBLE; sheet.translationY = 400f; sheet.animate().translationY(0f).setDuration(280).start()
    highlight(which)
  }
  fun closeSheet() { openSheet = null; sheet.animate().translationY(400f).setDuration(220).withEndAction { sheet.visibility = View.GONE }.start(); highlight(null) }
  private fun highlight(which: String?) {
    isoBtn.setBackgroundColor(if (which=="ISO") YELLOW else Color.parseColor("#333333")); isoBtn.setTextColor(if (which=="ISO") Color.BLACK else Color.WHITE)
    sBtn.setBackgroundColor(if (which=="S") YELLOW else Color.parseColor("#333333")); sBtn.setTextColor(if (which=="S") Color.BLACK else Color.WHITE)
    wbBtn.setBackgroundColor(if (which=="WB") YELLOW else Color.parseColor("#333333")); wbBtn.setTextColor(if (which=="WB") Color.BLACK else Color.WHITE)
  }
  private fun applySlider(pr: Int) {
    when (openSheet) {
      "ISO" -> { isoVal = (100 + pr * 100).coerceIn(100, 6400); sheetValue.text = "ISO $isoVal"; isoBtn.text = "ISO $isoVal" } // never 0
      "S" -> { val speeds = listOf("1/8000","1/4000","1/2000","1/1000","1/500","1/250","1/125","1/60","1/30","1s"); val label = speeds[(pr * speeds.size / 101).coerceIn(0, speeds.size-1)]; sheetValue.text = label; sBtn.text = "S  $label" }
      "WB" -> { wbVal = (2500 + pr * 50).coerceIn(2500, 7500); sheetValue.text = "${wbVal}K"; wbBtn.text = "WB  ${wbVal}K" }
    }
    emit()
  }
  private fun doCapture() {
    // shutter feedback: scale + thumb pop
    val s1 = ObjectAnimator.ofFloat(shutterBtn, "scaleX", 1f, 0.85f, 1f); val s2 = ObjectAnimator.ofFloat(shutterBtn, "scaleY", 1f, 0.85f, 1f)
    val t1 = ObjectAnimator.ofFloat(thumbView, "scaleX", 1f, 1.25f, 1f); val t2 = ObjectAnimator.ofFloat(thumbView, "scaleY", 1f, 1.25f, 1f)
    AnimatorSet().apply { playTogether(s1,s2,t1,t2); duration = 280; interpolator = DecelerateInterpolator(); start() }
    onCapture?.invoke()
  }
  fun setThumbnail(bmp: android.graphics.Bitmap) { thumbView.setImageBitmap(bmp) }
  private fun emit() { onManualChange?.invoke(shutterNs, isoVal, wbVal, isManual) }
  private fun refresh() { isoBtn.text = "ISO $isoVal" }
}
