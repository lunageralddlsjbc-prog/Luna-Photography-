package com.lunagerald.lunaphotography
import android.graphics.ColorMatrix
object IPhoneFilter {
  fun getIPhoneMatrix(): ColorMatrix {
    val m = ColorMatrix()
    m.setSaturation(1.15f)
    val warm = ColorMatrix(floatArrayOf(1.12f,0f,0f,0f,8f, 0f,1.0f,0f,0f,2f, 0f,0f,0.95f,0f,-2f, 0f,0f,0f,1f,0f))
    m.postConcat(warm)
    val bright = ColorMatrix(floatArrayOf(1.08f,0f,0f,0f,10f, 0f,1.08f,0f,0f,10f, 0f,0f,1.08f,0f,10f, 0f,0f,0f,1f,0f))
    m.postConcat(bright)
    return m
  }
  fun getAutoMatrix(brightness: Float): ColorMatrix {
    val m = getIPhoneMatrix()
    if (brightness < 0.35f) { m.postConcat(ColorMatrix(floatArrayOf(1.25f,0f,0f,0f,25f, 0f,1.25f,0f,0f,25f, 0f,0f,1.25f,0f,25f, 0f,0f,0f,1f,0f))) }
    return m
  }
}
