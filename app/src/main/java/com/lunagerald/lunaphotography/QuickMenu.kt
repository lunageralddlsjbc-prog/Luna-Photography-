package com.lunagerald.lunaphotography

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

enum class Look(val label: String, val matrix: FloatArray, val exposureRatio: Float) {
    NORMAL("Normal", floatArrayOf(1.04f,0f,0f,0f,3f, 0f,1.03f,0f,0f,1f, 0f,0f,1.02f,0f,0f, 0f,0f,0f,1f,0f), 0f),
    LUNA("Luna", floatArrayOf(1.06f,0f,0f,0f,6f, 0f,1.05f,0f,0f,3f, 0f,0f,1.04f,0f,2f, 0f,0f,0f,1f,0f), 0f),
    CINEMATIC("Cinematic", floatArrayOf(1.15f,0f,0f,0f,12f, 0f,0.95f,0f,0f,-5f, 0.05f,0.05f,1.15f,0f,8f, 0f,0f,0f,1f,0f), 0.2f),
    NIGHT("Night", floatArrayOf(1.06f,0f,0f,0f,6f, 0f,1.04f,0f,0f,2f, 0f,0f,1.03f,0f,0f, 0f,0f,0f,1f,0f), 0.0f)
}

class QuickMenu(
    private val activity: Activity,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onFlashCycle()
        fun onLiveToggle(enabled: Boolean)
        fun onAspectCycle()
        fun onTimerCycle()
        fun onExposureChange(value: Float)
        fun onStyleCycle()
        fun onDepthToggle(enabled: Boolean)
        fun onNightToggle(enabled: Boolean)
        fun onFormatCycle()
    }

    private val root: FrameLayout
    private val sheet: LinearLayout
    private var shown = false
    private var startY = 0f

    private var flashState = 0 // 0=AUTO, 1=ON, 2=OFF
    private var liveEnabled = false
    private var aspectIndex = 0 // 0=4:3, 1=16:9, 2=1:1
    private var timerIndex = 0 // 0=OFF, 1=3s, 2=10s
    private var exposureValue = 0f
    private var styleIndex = 0
    private var depthEnabled = false
    private var nightEnabled = false
    private var formatIndex = 0 // 0=JPEG, 1=HEIC

    private val flashLabels = arrayOf("Auto", "On", "Off")
    private val aspectLabels = arrayOf("4:3", "16:9", "1:1")
    private val timerLabels = arrayOf("Off", "3s", "10s")
    private val formatLabels = arrayOf("JPEG", "WEBP")
    private val styleLabels = arrayOf("Normal", "Luna", "Cinematic")

    init {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        root = FrameLayout(activity).apply {
            setBackgroundColor(0xE6000000.toInt())
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { hide() }
        }
        sheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xE61C1C1E.toInt())
                cornerRadii = floatArrayOf(dp(24f), dp(24f), dp(24f), dp(24f), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(20), dp(12), dp(20), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setOnClickListener { }
        }
        // Drag handle
        sheet.addView(View(activity).apply {
            background = GradientDrawable().apply {
                setColor(0xFF48484A.toInt())
                cornerRadius = dp(2f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, dp(16))
            }
        })
        // 3-column grid
        val grid = GridLayout(activity).apply {
            columnCount = 3
            rowCount = 3
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val items = listOf(
            Triple(R.drawable.ic_flash, "Flash") { onFlashClick() },
            Triple(R.drawable.ic_live, "Live") { onLiveClick() },
            Triple(R.drawable.ic_aspect, "Aspect") { onAspectClick() },
            Triple(R.drawable.ic_timer, "Timer") { onTimerClick() },
            Triple(R.drawable.ic_exposure, "Exposure") { onExposureClick() },
            Triple(R.drawable.ic_styles, "Styles") { onStylesClick() },
            Triple(R.drawable.ic_depth, "Depth") { onDepthClick() },
            Triple(R.drawable.ic_night, "Night") { onNightClick() },
            Triple(R.drawable.ic_format, "Format") { onFormatClick() }
        )
        for ((iconRes, label, action) in items) grid.addView(buildItem(iconRes, label, action))
        sheet.addView(grid)
        root.addView(sheet)
        content.addView(root)
        sheet.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startY = event.rawY
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (dy > 0) { sheet.translationY = dy; if (dy > dp(100).toFloat()) hide() }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (sheet.translationY < dp(100).toFloat()) sheet.animate().translationY(0f).setDuration(200).start()
                }
            }; true
        }
    }

    private fun buildItem(iconRes: Int, label: String, action: () -> Unit): View {
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(12), dp(8), dp(8))
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            isClickable = true
            setOnClickListener { action() }
            tag = label
        }
        val iconView = View(activity).apply {
            background = activity.getDrawable(iconRes)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            tag = "icon_$label"
        }
        item.addView(iconView)
        val labelView = TextView(activity).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            tag = "label_$label"
        }
        item.addView(labelView)
        return item
    }

    private fun updateItemAppearance(label: String, active: Boolean, displayText: String? = null) {
        val grid = sheet.getChildAt(1) as? GridLayout ?: return
        for (i in 0 until grid.childCount) {
            val item = grid.getChildAt(i) as? LinearLayout ?: continue
            if (item.tag == label) {
                val iconView = item.findViewWithTag<View>("icon_$label")
                val labelView = item.findViewWithTag<TextView>("label_$label")
                if (active) {
                        setColor(0xFFFFFFFF.toInt()); shape = GradientDrawable.OVAL
                    }
                    labelView?.setTextColor(0xFFFFFFFF.toInt())
                } else {
                        setColor(0xFF2C2C2E.toInt()); shape = GradientDrawable.OVAL
                    }
                    labelView?.setTextColor(0xFFFFFFFF.toInt())
                }
                if (displayText != null) labelView?.text = "$label $displayText"
                break
            }
        }
    }

    private fun onFlashClick() {
        flashState = (flashState + 1) % 3
        callbacks.onFlashCycle()
        updateItemAppearance("Flash", flashState != 0, flashLabels[flashState])
    }
    private fun onLiveClick() {
        liveEnabled = !liveEnabled
        callbacks.onLiveToggle(liveEnabled)
        updateItemAppearance("Live", liveEnabled)
    }
    private fun onAspectClick() {
        aspectIndex = (aspectIndex + 1) % 3
        callbacks.onAspectCycle()
        updateItemAppearance("Aspect", aspectIndex != 0, aspectLabels[aspectIndex])
    }
    private fun onTimerClick() {
        timerIndex = (timerIndex + 1) % 3
        callbacks.onTimerCycle()
        updateItemAppearance("Timer", timerIndex != 0, timerLabels[timerIndex])
    }
    private fun onExposureClick() {
        showExposureSlider()
    }
    private fun onStylesClick() {
        styleIndex = (styleIndex + 1) % 3
        callbacks.onStyleCycle()
        updateItemAppearance("Styles", styleIndex != 0, styleLabels[styleIndex])
    }
    private fun onDepthClick() {
        depthEnabled = !depthEnabled
        callbacks.onDepthToggle(depthEnabled)
        updateItemAppearance("Depth", depthEnabled)
    }
    private fun onNightClick() {
        nightEnabled = !nightEnabled
        callbacks.onNightToggle(nightEnabled)
        updateItemAppearance("Night", nightEnabled)
    }
    private fun onFormatClick() {
        formatIndex = (formatIndex + 1) % 2
        callbacks.onFormatCycle()
        updateItemAppearance("Format", formatIndex != 0, formatLabels[formatIndex])
    }

    private fun showExposureSlider() {
        val dialog = android.app.AlertDialog.Builder(activity)
            .setTitle("Exposure")
            .setView(SeekBar(activity).apply {
                max = 40
                progress = (exposureValue * 10 + 20).toInt().coerceIn(0, 40)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        exposureValue = (progress - 20) / 10f
                        callbacks.onExposureChange(exposureValue)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            .setPositiveButton("Done", null)
            .create()
        dialog.show()
        updateItemAppearance("Exposure", exposureValue != 0f, String.format("%.1f", exposureValue))
    }

    fun toggle() = if (shown) hide() else show()
    fun show() {
        shown = true; root.visibility = View.VISIBLE; sheet.translationY = dp(300).toFloat()
        root.animate().alpha(1f).setDuration(200).start(); sheet.animate().translationY(0f).setDuration(300).start()
    }
    fun hide() {
        shown = false
        root.animate().alpha(0f).setDuration(150).withEndAction { root.visibility = View.GONE }.start()
        sheet.animate().translationY(dp(300).toFloat()).setDuration(200).start()
    }
    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).roundToInt()
    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics)
}
