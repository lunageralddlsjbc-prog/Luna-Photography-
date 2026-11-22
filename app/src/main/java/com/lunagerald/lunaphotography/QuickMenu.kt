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
import android.widget.TextView
import kotlin.math.roundToInt

enum class Look(val label: String, val matrix: FloatArray, val exposureRatio: Float) {
    NORMAL("Luna", floatArrayOf(1.04f,0f,0f,0f,3f, 0f,1.03f,0f,0f,1f, 0f,0f,1.02f,0f,0f, 0f,0f,0f,1f,0f), 0f),
    VIBRANT("Vibrant", floatArrayOf(1.08f,0f,0f,0f,5f, 0f,1.06f,0f,0f,3f, 0f,0f,1.05f,0f,1f, 0f,0f,0f,1f,0f), 0f),
    WARM("Warm", floatArrayOf(1.10f,0f,0f,0f,8f, 0f,1.04f,0f,0f,4f, 0f,0f,0.96f,0f,0f, 0f,0f,0f,1f,0f), 0f),
    COOL("Cool", floatArrayOf(0.96f,0f,0f,0f,0f, 0f,1.02f,0f,0f,2f, 0f,0f,1.10f,0f,5f, 0f,0f,0f,1f,0f), 0f),
    
    // 🎬 CINEMATIC LOOK: High contrast, rich reds, teal shadows, deep blacks.
    CINEMATIC("Cinematic", floatArrayOf(
        1.15f, 0.00f, 0.00f, 0.00f,  12.0f, // Boost Red (Rich skin/reds) + Warm highlight lift
        0.00f, 0.95f, 0.00f, 0.00f,  -5.0f, // Slightly reduce Green (pushes towards Teal/Magenta balance) + Deepen shadows
        0.05f, 0.05f, 1.15f, 0.00f,   8.0f, // Boost Blue (Teal shadows) + Cool shadow lift
        0.00f, 0.00f, 0.00f, 1.00f,   0.0f  // Alpha
    ), 0.2f), // Slight exposure boost for cinematic mood

    NIGHT("Night", floatArrayOf(1.06f,0f,0f,0f,6f, 0f,1.04f,0f,0f,2f, 0f,0f,1.03f,0f,0f, 0f,0f,0f,1f,0f), 0.0f)
}

class QuickMenu(
    private val activity: Activity,
    private val onFlashCycle: () -> Unit,
    private val onStyleChange: (Look) -> Unit,
    private val onNightToggle: (Boolean) -> Unit
) {
    private val root: FrameLayout
    private val sheet: LinearLayout
    private var shown = false
    private var startY = 0f
    private var currentStyle = Look.NORMAL
    private var isNightMode = false

    init {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        root = FrameLayout(activity).apply {
            setBackgroundColor(0x99000000.toInt()); visibility = View.GONE; alpha = 0f; setOnClickListener { hide() }
        }
        sheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xFF1C1C1E.toInt()); cornerRadii = floatArrayOf(dp(20f), dp(20f), dp(20f), dp(20f), 0f, 0f, 0f, 0f) }
            setPadding(dp(16), dp(12), dp(16), dp(24))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            setOnClickListener { }
        }
        sheet.addView(View(activity).apply {
            background = GradientDrawable().apply { setColor(0xFF3A3A3C.toInt()); cornerRadius = dp(2f).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, 0, 0, dp(12)) }
        })
        sheet.addView(TextView(activity).apply { text = "Camera Controls"; setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        
        val grid = GridLayout(activity).apply {
            columnCount = 3; rowCount = 3
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        
        // Updated items list to include Cinematic logic if needed, but Styles cycles through all looks now
        val items = listOf(
            Triple("⚡", "Flash") { onFlashCycle() },
            Triple("●", "Live") { toast("Live Photo coming soon") },
            Triple("⬜", "Aspect") { toast("Aspect ratio coming soon") },
            Triple("⏱", "Timer") { toast("Timer coming soon") },
            Triple("☀", "Exposure") { toast("Auto exposure active") },
            Triple("🎨", "Styles") { cycleStyle() },
            Triple("◐", "Depth") { toast("Depth control coming soon") },
            Triple("🌙", "Night") { toggleNight() },
            Triple("📄", "Format") { toast("Format settings coming soon") }
        )
        for ((icon, label, action) in items) grid.addView(buildItem(icon, label, action))
        sheet.addView(grid); root.addView(sheet); content.addView(root)
        
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

    private fun buildItem(icon: String, label: String, action: () -> Unit): View {
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = GridLayout.LayoutParams().apply { width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
            isClickable = true; setOnClickListener { action() }
        }
        item.addView(TextView(activity).apply {
            text = icon; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(0xFF2C2C2E.toInt()); shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        item.addView(TextView(activity).apply { text = label; setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        return item
    }

    private fun cycleStyle() {
        // Cycle through all styles including Cinematic
        val styles = listOf(Look.NORMAL, Look.VIBRANT, Look.WARM, Look.COOL, Look.CINEMATIC)
        currentStyle = styles[(styles.indexOf(currentStyle) + 1) % styles.size]
        if (isNightMode) toggleNight()
        onStyleChange(currentStyle); toast("Style: ${currentStyle.label}")
    }

    private fun toggleNight() {
        isNightMode = !isNightMode
        if (isNightMode) { onNightToggle(true); toast("Night Mode: ON") }
        else { onNightToggle(false); onStyleChange(currentStyle); toast("Night Mode: OFF") }
    }

    private fun toast(msg: String) = android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show()

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
