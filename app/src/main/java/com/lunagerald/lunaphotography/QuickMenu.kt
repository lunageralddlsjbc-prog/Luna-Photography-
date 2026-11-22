package com.lunagerald.lunaphotography

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

enum class Look(
    val label: String,
    val swatch: Int,
    val matrix: FloatArray,
    val exposureRatio: Float
) {
    NORMAL("Normal", 0xFF8A8A8A.toInt(), floatArrayOf(
        1.05f,0f,0f,0f,4f,  0f,1.03f,0f,0f,3f,  0f,0f,1.02f,0f,1f,  0f,0f,0f,1f,0f), 0f),
    VIBRANT("Vibrant", 0xFFE23B3B.toInt(), floatArrayOf(
        1.10f,0f,0f,0f,6f,  0f,1.08f,0f,0f,3f,  0f,0f,1.06f,0f,0f,  0f,0f,0f,1f,0f), 0f),
    WARM("Warm", 0xFFE08A3C.toInt(), floatArrayOf(
        1.12f,0f,0f,0f,10f, 0f,1.05f,0f,0f,5f,  0f,0f,0.95f,0f,0f,  0f,0f,0f,1f,0f), 0f),
    COOL("Cool", 0xFF3CA0E0.toInt(), floatArrayOf(
        0.95f,0f,0f,0f,0f,  0f,1.02f,0f,0f,3f,  0f,0f,1.12f,0f,8f,  0f,0f,0f,1f,0f), 0f),
    NIGHT("Night", 0xFF1E2A5A.toInt(), floatArrayOf(
        1.10f,0f,0f,0f,22f, 0f,1.08f,0f,0f,16f, 0f,0f,1.05f,0f,10f, 0f,0f,0f,1f,0f), 0.8f)
}

class QuickMenu(
    private val activity: Activity,
    private val onLookChanged: (Look) -> Unit
) {
    var currentLook: Look = Look.NORMAL
        private set

    private val root: FrameLayout
    private val panel: LinearLayout
    private val cards = ArrayList<Pair<LinearLayout, GradientDrawable>>()
    private var shown = false

    init {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)

        root = FrameLayout(activity).apply {
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { hide() }
        }

        panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF2111111.toInt())
                cornerRadii = floatArrayOf(dp(22f), dp(22f), dp(22f), dp(22f), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(16), dp(18), dp(16), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setOnClickListener { /* absorb taps */ }
        }

        panel.addView(TextView(activity).apply {
            text = "Photographic Styles"
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(0, 0, 0, dp(2))
        })
        panel.addView(TextView(activity).apply {
            text = "Swipe to choose a look · Night boosts exposure"
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(14))
        })

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        for (look in Look.entries) row.addView(buildCard(look))

        panel.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })

        root.addView(panel)
        content.addView(root)
        refreshSelection()
    }

    private fun buildCard(look: Look): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(0x1FFFFFFF.toInt())
            cornerRadius = dp(14f)
            setStroke(0, Color.TRANSPARENT)
        }
        val swatch = View(activity).apply {
            background = GradientDrawable().apply {
                setColor(look.swatch)
                cornerRadius = dp(10f)
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }
        val label = TextView(activity).apply {
            text = look.label
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = bg
            setPadding(dp(10), dp(10), dp(10), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(10)
            layoutParams = lp
            isClickable = true
            addView(swatch)
            addView(label)
            setOnClickListener {
                currentLook = look
                refreshSelection()
                onLookChanged(look)
                postDelayed({ hide() }, 220)
            }
        }
        cards.add(card to bg)
        return card
    }

    private fun refreshSelection() {
        for ((card, bg) in cards) {
            val selected = (card.getChildAt(1) as TextView).text == currentLook.label
            if (selected) {
                bg.setStroke(dp(2), 0xFFFFD60A.toInt())
                bg.setColor(0x33FFFFFF.toInt())
            } else {
                bg.setStroke(0, Color.TRANSPARENT)
                bg.setColor(0x1FFFFFFF.toInt())
            }
        }
    }

    fun toggle() = if (shown) hide() else show()

    fun show() {
        shown = true
        root.visibility = View.VISIBLE
        root.animate().alpha(1f).setDuration(160).start()
        panel.translationY = dp(80).toFloat()
        panel.animate().translationY(0f).setDuration(220).start()
    }

    fun hide() {
        shown = false
        root.animate().alpha(0f).setDuration(140).withEndAction { root.visibility = View.GONE }.start()
        panel.animate().translationY(dp(80).toFloat()).setDuration(160).start()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).roundToInt()

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics)
}
