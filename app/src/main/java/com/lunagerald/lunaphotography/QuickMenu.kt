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
    // 🌟 THE LUNA LOOK: Warm highlights, neutral shadows, punchy natural greens/blues, natural skin tones.
    NORMAL("Luna", floatArrayOf(
        1.06f, 0.00f, 0.00f, 0.00f,  6.0f,  // Red: Warm highlights, natural skin tone lift
        0.00f, 1.05f, 0.00f, 0.00f,  3.0f,  // Green: Punchy, natural greens
        0.00f, 0.00f, 1.04f, 0.00f,  2.0f,  // Blue: Natural blues, prevents muddy shadows
        0.00f, 0.00f, 0.00f, 1.00f,  0.0f   // Alpha: Unchanged
    ), 0f),
    
    VIBRANT("Vibrant", floatArrayOf(
        1.10f, 0.00f, 0.00f, 0.00f,  8.0f,
        0.00f, 1.08f, 0.00f, 0.00f,  4.0f,
        0.00f, 0.00f, 1.06f, 0.00f,  2.0f,
        0.00f, 0.00f, 0.00f, 1.00f,  0.0f
    ), 0f),
    
    WARM("Warm", floatArrayOf(
        1.12f, 0.00f, 0.00f, 0.00f, 12.0f,
        0.00f, 1.05f, 0.00f, 0.00f,  6.0f,
        0.00f, 0.00f, 0.95f, 0.00f,  0.0f,
        0.00f, 0.00f, 0.00f, 1.00f,  0.0f
    ), 0f),
    
    COOL("Cool", floatArrayOf(
        0.95f, 0.00f, 0.00f, 0.00f,  0.0f,
        0.00f, 1.02f, 0.00f, 0.00f,  3.0f,
        0.00f, 0.00f, 1.12f, 0.00f,  8.0f,
        0.00f, 0.00f, 0.00f, 1.00f,  0.0f
    ), 0f),
    
    NIGHT("Night", floatArrayOf(
        1.08f, 0.00f, 0.00f, 0.00f, 18.0f, // Strong shadow lift for low light
        0.00f, 1.06f, 0.00f, 0.00f, 12.0f,
        0.00f, 0.00f, 1.04f, 0.00f,  8.0f,
        0.00f, 0.00f, 0.00f, 1.00f,  0.0f
    ), 0.8f)
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
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { hide() }
        }

        sheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1C1C1E.toInt())
                cornerRadii = floatArrayOf(dp(20f), dp(20f), dp(20f), dp(20f), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(16), dp(12), dp(16), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setOnClickListener { }
        }

        val handle = View(activity).apply {
            background = GradientDrawable().apply {
                setColor(0xFF3A3A3C.toInt())
                cornerRadius = dp(2f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, dp(12))
            }
        }
        sheet.addView(handle)

        val title = TextView(activity).apply {
            text = "Camera Controls"
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        sheet.addView(title)

        val grid = GridLayout(activity).apply {
            columnCount = 3
            rowCount = 3
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

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

        for ((icon, label, action) in items) {
            grid.addView(buildItem(icon, label, action))
        }

        sheet.addView(grid)
        root.addView(sheet)
        content.addView(root)

        sheet.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startY = event.rawY
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (dy > 0) {
                        sheet.translationY = dy
                        if (dy > dp(100).toFloat()) hide()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (sheet.translationY < dp(100).toFloat()) {
                        sheet.animate().translationY(0f).setDuration(200).start()
                    }
                }
            }
            true
        }
    }

    private fun buildItem(icon: String, label: String, action: () -> Unit): View {
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            isClickable = true
            setOnClickListener { action() }
        }

        val iconView = TextView(activity).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0xFF2C2C2E.toInt())
                shape = GradientDrawable.OVAL
            }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        item.addView(iconView)

        val labelView = TextView(activity).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        item.addView(labelView)

        return item
    }

    private fun cycleStyle() {
        val styles = listOf(Look.NORMAL, Look.VIBRANT, Look.WARM, Look.COOL)
        val idx = styles.indexOf(currentStyle)
        currentStyle = styles[(idx + 1) % styles.size]
        if (isNightMode) toggleNight()
        onStyleChange(currentStyle)
        toast("Style: ${currentStyle.label}")
    }

    private fun toggleNight() {
        isNightMode = !isNightMode
        if (isNightMode) {
            onNightToggle(true)
            toast("Night Mode: ON")
        } else {
            onNightToggle(false)
            onStyleChange(currentStyle)
            toast("Night Mode: OFF")
        }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun toggle() = if (shown) hide() else show()

    fun show() {
        shown = true
        root.visibility = View.VISIBLE
        sheet.translationY = dp(300).toFloat()
        root.animate().alpha(1f).setDuration(200).start()
        sheet.animate().translationY(0f).setDuration(300).start()
    }

    fun hide() {
        shown = false
        root.animate().alpha(0f).setDuration(150).withEndAction { root.visibility = View.GONE }.start()
        sheet.animate().translationY(dp(300).toFloat()).setDuration(200).start()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).roundToInt()

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics)
}
