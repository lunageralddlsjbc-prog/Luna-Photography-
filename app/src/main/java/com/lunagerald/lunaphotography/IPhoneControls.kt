package com.lunagerald.lunaphotography

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.view.animation.DecelerateInterpolator

// iPhone 17 Pro Max style - CLEAN, full-screen overlay, edge controls only
class IPhoneControls(val ctx: Context, val root: FrameLayout) {
  var isManual = false; private set
  var isAuto = true; private set
  var isVideo = false; private set
  var isRecording = false; private set
  var isoVal = 100; private set
  var shutterNs = 4000000L; private set
  var wbVal = 5500; private set
  var zoomVal = 1f; private set
  var onCapture: (() -> Unit)? = null
  var onVideoToggle: ((Boolean) -> Unit)? = null
  var onMode: ((Boolean) -> Unit)? = null // true=VIDEO
  var onManualChange: ((Long, Int, Int, Boolean) -> Unit)? = null
  var onZoom: ((Float) -> Unit)? = null
  var onFlip: (() -> Unit)? = null
  var onFlash: (() -> Unit)? = null

  private val YELLOW = Color.parseColor("#FFD400")
  private val BLACK = Color.parseColor("#000000")
  private lateinit var manualBtn: TextView
  private lateinit var manualRow: LinearLayout
  private lateinit var isoBtn: TextView
  private lateinit var sBtn: TextView
  private lateinit var wbBtn: TextView
  private lateinit var shutterBtn: FrameLayout
  private lateinit var shutterInner: View
  private lateinit var thumbView: ImageView
  private lateinit var photoTv: TextView
  private lateinit var videoTv: TextView
  private lateinit var timerTv: TextView
  private lateinit var sheet: LinearLayout
  private lateinit var sheetTitle: TextView
  private lateinit var sheetValue: TextView
  private lateinit var sheetSlider: SeekBar
  private lateinit var sheetOptions: LinearLayout
  private lateinit var zoomPill: LinearLayout
  private val zoomLevels = listOf(0.5f, 1f, 2f, 4f, 8f)
  private var openSheet: String? = null
  private var recStart = 0L
  private val handler = Handler(Looper.getMainLooper())
  private val recRunnable = object : Runnable { override fun run() { if (isRecording) { val s = (System.currentTimeMillis() - recStart) / 1000; timerTv.text = String.format("%02d:%02d", s / 60, s % 60); handler.postDelayed(this, 500) } } }

  private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
  private fun bg(c: Int, r: Float, stroke: Int = 0, sc: Int = 0): GradientDrawable { val d = GradientDrawable(); d.setColor(c); d.cornerRadius = r; if (stroke > 0) d.setStroke(stroke, sc); return d }
  private fun tv(t: String, size: Float = 14f, col: Int = Color.WHITE, bold: Boolean = false) = TextView(ctx).apply { text = t; textSize = size; setTextColor(col); typeface = android.graphics.Typeface.SANS_SERIF; if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER }

  fun build(): View {
    val overlay = FrameLayout(ctx).apply { layoutParams = FrameLayout.LayoutParams(-1, -1); setBackgroundColor(Color.TRANSPARENT) }
    // TOP-LEFT: flash + MANUAL
    val topLeft = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.TOP or Gravity.START; setMargins(dp(16), dp(16), 0, 0) } }
    val flash = tv("⚡", 18f).apply { setPadding(dp(10), dp(8), dp(10), dp(8)); background = bg(Color.parseColor("#222222"), 999f); setOnClickListener { onFlash?.invoke() } }
    manualBtn = tv("MANUAL", 12f, Color.WHITE, true).apply { setPadding(dp(14), dp(8), dp(14), dp(8)); background = bg(Color.parseColor("#222222"), 999f); setOnClickListener { setManual(!isManual) } }
    topLeft.addView(flash); topLeft.addView(manualBtn.apply { layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) } })
    overlay.addView(topLeft)
    // TOP-RIGHT: flip only
    val flip = tv("⇄", 18f).apply { setPadding(dp(12), dp(8), dp(12), dp(8)); background = bg(Color.parseColor("#222222"), 999f); layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.TOP or Gravity.END; setMargins(0, dp(16), dp(16), 0) }; setOnClickListener { onFlip?.invoke() } }
    overlay.addView(flip)
    // RIGHT EDGE: ZOOM vertical pill - 0.5 / 1 / 2 / 4 / 8 only
    zoomPill = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = bg(Color.parseColor("#1A1A1A"), dp(20).toFloat()); setPadding(dp(6), dp(10), dp(6), dp(10)); layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; setMargins(0, 0, dp(12), dp(120)) } }
    zoomLevels.forEach { z ->
      val t = tv(if (z == 0.5f) ".5" else if (z == 1f) "1x" else "${z.toInt()}x", 12f, if (z == 1f) YELLOW else Color.WHITE, z == 1f).apply { setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { setZoom(z) } }
      zoomPill.addView(t)
    }
    overlay.addView(zoomPill)
    // MANUAL ROW: ISO | S | WB - above shutter, hidden in normal
    manualRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; visibility = View.GONE; layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.BOTTOM or Gravity.END; setMargins(0, 0, dp(16), dp(170)) } }
    isoBtn = chip("ISO 100") { openSheet("ISO") }; sBtn = chip("S  1/250") { openSheet("S") }; wbBtn = chip("WB  Auto") { openSheet("WB") }
    manualRow.addView(isoBtn); manualRow.addView(sBtn); manualRow.addView(wbBtn)
    overlay.addView(manualRow)
    // BOTTOM-RIGHT: thumb + shutter + PHOTO/VIDEO vertical pill
    val bottom = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.BOTTOM or Gravity.END; setMargins(0, 0, dp(16), dp(24)) } }
    thumbView = ImageView(ctx).apply { layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) }; background = bg(Color.DKGRAY, 8f) }
    // Shutter 70dp, white circle - LEFT of pill
    shutterBtn = FrameLayout(ctx).apply { layoutParams = LinearLayout.LayoutParams(dp(70), dp(70)).apply { rightMargin = dp(12) } }
    val outer = View(ctx).apply { layoutParams = FrameLayout.LayoutParams(dp(70), dp(70)); background = bg(Color.WHITE, 999f) }
    shutterInner = View(ctx).apply { layoutParams = FrameLayout.LayoutParams(dp(58), dp(58)).apply { gravity = Gravity.CENTER }; background = bg(Color.WHITE, 999f) }
    shutterBtn.addView(outer); shutterBtn.addView(shutterInner)
    shutterBtn.setOnClickListener { onShutterTap() }
    // PHOTO/VIDEO vertical pill - ONE pill, PHOTO top, VIDEO bottom
    val modePill = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = bg(Color.parseColor("#1A1A1A"), dp(20).toFloat()); setPadding(dp(10), dp(12), dp(10), dp(12)) }
    photoTv = tv("PHOTO", 12f, YELLOW, true).apply { setPadding(0, dp(6), 0, dp(6)); setOnClickListener { setVideoMode(false) } }
    videoTv = tv("VIDEO", 12f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, dp(6)); setOnClickListener { setVideoMode(true) } }
    modePill.addView(photoTv); modePill.addView(videoTv)
    timerTv = tv("00:00", 12f, Color.RED, true).apply { visibility = View.GONE }
    val rightCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    rightCol.addView(timerTv); rightCol.addView(modePill)
    bottom.addView(thumbView); bottom.addView(shutterBtn); bottom.addView(rightCol)
    overlay.addView(bottom)
    // BOTTOM SHEET - ONE sheet, ONE control only
    sheet = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; background = bg(Color.parseColor("#1C1C1E"), dp(20).toFloat()); setPadding(dp(20), dp(12), dp(20), dp(24)); layoutParams = FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.BOTTOM } }
    val handle = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply { gravity = Gravity.CENTER }; background = bg(Color.GRAY, 999f) }
    val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    sheetTitle = tv("ISO", 14f, Color.WHITE, true); sheetValue = tv("ISO 100", 14f, YELLOW, true)
    val close = tv("✕", 16f).apply { setOnClickListener { closeSheet() } }
    head.addView(sheetTitle); head.addView(sheetValue.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }); head.addView(close)
    sheetSlider = SeekBar(ctx).apply { setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) { if (fromUser) applySlider(pr) }
      override fun onStartTrackingTouch(s: SeekBar?) {}
      override fun onStopTrackingTouch(s: SeekBar?) {}
    }) }
    sheetOptions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; visibility = View.GONE }
    sheet.addView(handle); sheet.addView(head); sheet.addView(sheetSlider); sheet.addView(sheetOptions)
    overlay.addView(sheet)
    // Tap outside sheet = close
    overlay.setOnClickListener { if (sheet.visibility == View.VISIBLE) closeSheet() }
    root.addView(overlay)
    return overlay
  }

  private fun chip(t: String, onClick: () -> Unit) = TextView(ctx).apply { text = t; textSize = 12f; setTextColor(Color.WHITE); setPadding(dp(14), dp(8), dp(14), dp(8)); background = bg(Color.parseColor("#333333"), 999f); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6); rightMargin = dp(6) }; setOnClickListener { onClick() } }

  fun setVideoMode(video: Boolean) {
    isVideo = video
    photoTv.setTextColor(if (video) Color.WHITE else YELLOW)
    videoTv.setTextColor(if (video) YELLOW else Color.WHITE)
    onMode?.invoke(video)
  }
  fun setManual(m: Boolean) {
    isManual = m
    if (m) isAuto = false // M ON = AUTO OFF, never both
    manualBtn.setBackgroundColor(if (m) YELLOW else Color.parseColor("#222222"))
    manualBtn.setTextColor(if (m) BLACK else Color.WHITE)
    manualRow.visibility = if (m) View.VISIBLE else View.GONE
    if (!m) closeSheet()
    emit()
  }
  fun setAuto(a: Boolean) { isAuto = a; if (a) isManual = false; if (a) { manualRow.visibility = View.GONE; closeSheet() }; emit() }
  fun setZoom(z: Float) {
    zoomVal = z.coerceIn(0.5f, 8f)
    // update pill highlight
    for (i in 0 until zoomPill.childCount) { val v = zoomPill.getChildAt(i) as TextView; val lvl = zoomLevels[i]; v.setTextColor(if (lvl == zoomVal) YELLOW else Color.WHITE) }
    onZoom?.invoke(zoomVal)
  }
  private fun onShutterTap() {
    // animation + thumb pop - 1 tap = 1 action only
    val s1 = ObjectAnimator.ofFloat(shutterBtn, "scaleX", 1f, 0.88f, 1f)
    val s2 = ObjectAnimator.ofFloat(shutterBtn, "scaleY", 1f, 0.88f, 1f)
    val t1 = ObjectAnimator.ofFloat(thumbView, "scaleX", 1f, 1.2f, 1f)
    val t2 = ObjectAnimator.ofFloat(thumbView, "scaleY", 1f, 1.2f, 1f)
    AnimatorSet().apply { playTogether(s1, s2, t1, t2); duration = 260; interpolator = DecelerateInterpolator(); start() }
    if (isVideo) {
      isRecording = !isRecording
      if (isRecording) { recStart = System.currentTimeMillis(); timerTv.visibility = View.VISIBLE; shutterInner.setBackgroundColor(Color.RED); handler.post(recRunnable) }
      else { timerTv.visibility = View.GONE; shutterInner.setBackgroundColor(Color.WHITE); handler.removeCallbacks(recRunnable) }
      onVideoToggle?.invoke(isRecording)
    } else {
      onCapture?.invoke()
    }
  }
  fun openSheet(which: String) {
    if (!isManual) return
    openSheet = which
    sheetOptions.removeAllViews(); sheetOptions.visibility = View.GONE; sheetSlider.visibility = View.VISIBLE
    when (which) {
      "ISO" -> { sheetTitle.text = "ISO"; sheetSlider.max = 6; sheetSlider.progress = listOf(100,200,400,800,1600,3200,6400).indexOf(isoVal).coerceIn(0,6); sheetValue.text = "ISO $isoVal" }
      "S" -> { sheetTitle.text = "Shutter Speed"; sheetSlider.max = 8; sheetSlider.progress = 5; sheetValue.text = "1/250s" }
      "WB" -> { sheetTitle.text = "White Balance"; sheetSlider.visibility = View.GONE; sheetOptions.visibility = View.VISIBLE; listOf("Auto","Daylight","Cloudy","Tungsten","Fluorescent").forEach { name -> val b = chip(name) { wbVal = when(name){"Daylight"->5500;"Cloudy"->6500;"Tungsten"->3200;"Fluorescent"->4000;else->5500}; wbBtn.text = "WB  $name"; sheetValue.text = name; emit() }; sheetOptions.addView(b) }; sheetValue.text = "Auto" }
    }
    sheet.visibility = View.VISIBLE; sheet.translationY = 400f; sheet.animate().translationY(0f).setDuration(280).start()
    highlight(which)
  }
  fun closeSheet() { openSheet = null; sheet.animate().translationY(400f).setDuration(220).withEndAction { sheet.visibility = View.GONE }.start(); highlight(null) }
  private fun highlight(which: String?) {
    isoBtn.setBackgroundColor(if (which=="ISO") YELLOW else Color.parseColor("#333333")); isoBtn.setTextColor(if (which=="ISO") BLACK else Color.WHITE)
    sBtn.setBackgroundColor(if (which=="S") YELLOW else Color.parseColor("#333333")); sBtn.setTextColor(if (which=="S") BLACK else Color.WHITE)
    wbBtn.setBackgroundColor(if (which=="WB") YELLOW else Color.parseColor("#333333")); wbBtn.setTextColor(if (which=="WB") BLACK else Color.WHITE)
  }
  private fun applySlider(pr: Int) {
    when (openSheet) {
      "ISO" -> { val vals = listOf(100,200,400,800,1600,3200,6400); isoVal = vals[pr.coerceIn(0,6)]; sheetValue.text = "ISO $isoVal"; isoBtn.text = "ISO $isoVal" } // never 0, min 100
      "S" -> { val labels = listOf("1/8000","1/4000","1/2000","1/1000","1/500","1/250","1/125","1/60","1s"); val label = labels[pr.coerceIn(0,8)]; sheetValue.text = label; sBtn.text = "S  $label" }
    }
    emit()
  }
  fun setThumbnail(bmp: android.graphics.Bitmap) { thumbView.setImageBitmap(bmp) }
  private fun emit() { onManualChange?.invoke(shutterNs, isoVal.coerceAtLeast(100), wbVal, isManual) }
}
