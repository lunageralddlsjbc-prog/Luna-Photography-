package com.lunagerald.lunaphotography
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
object IPhoneControls {
    var currentMode = "PHOTO"
    var activeSetting = "EXPO"
    var isoVal = 400
    var shutterVal = 8333333L
    var expoVal = 0
    var focusVal = -1f
    var zoomVal = 1f
    var onMode: ((String)->Unit)? = null
    var onWheel: ((String, Int)->Unit)? = null
    fun setup(a: AppCompatActivity, root: ViewGroup) {
        val bar = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#CC000000")); setPadding(16,12,16,48) }
        val valueText = TextView(a).apply { text = "EXPO +0.0 | PHOTO"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        bar.addView(valueText)
        val modeRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val modes = listOf("PHOTO","PORTRAIT","CINEMATIC","NIGHT","PRO")
        for (m in modes) {
            val b = Button(a).apply { text = m; textSize = 11f; setOnClickListener { currentMode = m; onMode?.invoke(m); valueText.text = "MODE " + m; refresh(modeRow) } }
            modeRow.addView(b)
        }
        bar.addView(modeRow)
        val labelRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for (lab in listOf("ISO","SHUTTER","EXPO","FOCUS","ZOOM")) {
            val t = TextView(a).apply { text = lab; textSize = 12f; setPadding(18,8,18,8); setTextColor(Color.LTGRAY); setOnClickListener { activeSetting = lab; valueText.text = lab + " active" } }
            labelRow.addView(t)
        }
        bar.addView(labelRow)
        val wheel = SeekBar(a).apply { max = 100; progress = 50; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { if (u) { onWheel?.invoke(activeSetting, v); valueText.text = activeSetting + " " + v + " | " + currentMode } }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }) }
        bar.addView(wheel)
        root.addView(bar, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        refresh(modeRow)
    }
    fun refresh(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val b = row.getChildAt(i) as Button
            val on = b.text.toString() == currentMode
            b.setTextColor(if (on) Color.BLACK else Color.WHITE)
            b.setBackgroundColor(if (on) Color.WHITE else Color.parseColor("#44FFFFFF"))
        }
    }
}
