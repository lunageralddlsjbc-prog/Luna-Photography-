package com.lunagerald.lunaphotography

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var previewView: PreviewView
    private lateinit var focusRing: View
    private lateinit var shutterFlash: View
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null
    private var styleIndex = 0
    private val styleNames = arrayOf("Standard", "Warm", "Cool", "Vibrant", "Rich")
    private val flashModes = arrayOf(ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON, ImageCapture.FLASH_MODE_OFF)
    private val flashLabels = arrayOf("⚡ AUTO", "⚡ ON", "⚡ OFF")
    private var flashIndex = 0
    private lateinit var zoomPills: List<TextView>
    private lateinit var modePills: List<TextView>
    private var activeMode = "PHOTO"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.previewView)
        previewView.setMirrorMode(PreviewView.MirrorMode.MIRROR_FRONT)
        focusRing = findViewById(R.id.focusRing)
        shutterFlash = findViewById(R.id.shutterFlash)
        val shutterBtn = findViewById<ImageButton>(R.id.shutterBtn)
        val switchBtn = findViewById<ImageButton>(R.id.switchBtn)
        val flashPill = findViewById<TextView>(R.id.flashPill)
        val gearPill = findViewById<TextView>(R.id.gearPill)
        zoomPills = listOf(findViewById(R.id.zoomHalf), findViewById(R.id.zoomOne), findViewById(R.id.zoomThree))
        modePills = listOf(findViewById(R.id.mTime), findViewById(R.id.mSlo), findViewById(R.id.mVideo), findViewById(R.id.mPhoto), findViewById(R.id.mPortrait), findViewById(R.id.mPano))

        shutterBtn.setOnClickListener { triggerHaptic(); flashScreen(); takePhoto() }
        switchBtn.setOnClickListener { switchCamera() }

        // Secret: pinch-to-zoom
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return true
                val cur = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                cam.cameraControl.setZoomRatio((cur * detector.scaleFactor).coerceIn(1f, cam.cameraInfo.zoomState.value?.zoomRatioRange?.get(1) ?: 10f))
                return true
            }
        })

        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN && !scaleDetector.isInProgress) {
                triggerHaptic()
                showFocusRing(event.x, event.y)
                startTapToFocus(event.x, event.y)
            }
            true
        }

        // Zoom pills
        zoomPills[0].setOnClickListener { setZoom(0.5f, 0) }
        zoomPills[1].setOnClickListener { setZoom(1f, 1) }
        zoomPills[2].setOnClickListener { setZoom(3f, 2) }

        // Mode pills (visual selection; PHOTO is the working capture mode)
        val modeNames = arrayOf("TIME-LAPSE", "SLO-MO", "VIDEO", "PHOTO", "PORTRAIT", "PANORAMA")
        modePills.forEachIndexed { i, tv ->
            tv.setOnClickListener {
                activeMode = modeNames[i]
                modePills.forEach { it.setTextColor(0x99FFFFFF.toInt()) }
                tv.setTextColor(0xFFFFD60A.toInt())
                if (activeMode != "PHOTO") Toast.makeText(this, "$activeMode — capture uses PHOTO pipeline", Toast.LENGTH_SHORT).show()
            }
        }

        // Flash toggle
        flashPill.setOnClickListener {
            flashIndex = (flashIndex + 1) % flashModes.size
            imageCapture?.flashMode = flashModes[flashIndex]
            flashPill.text = flashLabels[flashIndex]
            triggerHaptic()
        }

        // Photographic Styles (gear cycles color presets)
        gearPill.setOnClickListener {
            styleIndex = (styleIndex + 1) % styleNames.size
            Toast.makeText(this, "Style: ${styleNames[styleIndex]}", Toast.LENGTH_SHORT).show()
            triggerHaptic()
        }

        if (allPermissionsGranted()) initializeCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE)
    }

    private fun setZoom(ratio: Float, pillIdx: Int) {
        val cam = camera ?: return
        cam.cameraControl.setZoomRatio(ratio)
        zoomPills.forEachIndexed { i, tv ->
            if (i == pillIdx) { tv.setBackgroundResource(R.drawable.pill_active); tv.setTextColor(0xFF000000.toInt()) } else { tv.setBackgroundResource(R.drawable.pill_inactive); tv.setTextColor(0xFFFFFFFF.toInt()) }
        }
        triggerHaptic()
    }

    private fun initializeCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        val extFuture = ExtensionsManager.getInstanceAsync(this, providerFuture)
        extFuture.addListener({
            try { extensionsManager = extFuture.get() } catch (_: Exception) {}
            startCamera(providerFuture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera(providerFuture: com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>) {
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val baseSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val extMgr = extensionsManager
            // Secret 1: HDR via the CORRECT selector-based extension API
            val finalSelector = if (extMgr != null && extMgr.isExtensionAvailable(baseSelector, ExtensionMode.HDR))
                extMgr.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
            else baseSelector
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            capture.flashMode = flashModes[flashIndex]
            imageCapture = capture
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, finalSelector, preview, capture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera init failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startTapToFocus(x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.visibility = View.VISIBLE
        focusRing.translationX = x - (focusRing.width / 2f)
        focusRing.translationY = y - (focusRing.height / 2f)
        focusRing.animate().scaleX(1.2f).scaleY(1.2f).alpha(1f).setDuration(200).withEndAction {
            focusRing.animate().scaleX(1f).scaleY(1f).alpha(0f).setDuration(400).withEndAction { focusRing.visibility = View.GONE }.start()
        }.start()
    }

    private fun flashScreen() {
        shutterFlash.animate().alpha(0.6f).setDuration(80).withEndAction {
            shutterFlash.animate().alpha(0f).setDuration(160).start()
        }.start()
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "LUNA_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return
        val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rot = image.imageInfo.rotationDegrees
                    val src = if (image.format == ImageFormat.JPEG) {
                        val buf = image.planes[0].buffer
                        val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else yuvToBitmap(image)
                    image.close()
                    val styled = applyStyle(src, rot, isFront)
                    src.recycle()
                    val bos = ByteArrayOutputStream()
                    styled.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                    styled.recycle()
                    resolver.openOutputStream(uri)?.use { it.write(bos.toByteArray()) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear(); contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    runOnUiThread { Toast.makeText(this@MainActivity, "Saved (${styleNames[styleIndex]})", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Encode error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun applyStyle(src: Bitmap, rotation: Int, mirror: Boolean): Bitmap {
        val m = Matrix().apply {
            if (rotation != 0) postRotate(rotation.toFloat())
            if (mirror) preScale(-1f, 1f)
        }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it != src) src.recycle() }
        val arrays = arrayOf(
            floatArrayOf(1.05f,0f,0f,0f,8f, 0f,1.03f,0f,0f,4f, 0f,0f,0.95f,0f,0f, 0f,0f,0f,1f,0f),
            floatArrayOf(1.08f,0f,0f,0f,12f, 0f,1.02f,0f,0f,6f, 0f,0f,0.90f,0f,0f, 0f,0f,0f,1f,0f),
            floatArrayOf(0.95f,0f,0f,0f,0f, 0f,1.02f,0f,0f,4f, 0f,0f,1.08f,0f,8f, 0f,0f,0f,1f,0f),
            floatArrayOf(1.12f,0f,0f,0f,6f, 0f,1.12f,0f,0f,4f, 0f,0f,1.12f,0f,2f, 0f,0f,0f,1f,0f),
            floatArrayOf(1.18f,0f,0f,0f,10f, 0f,1.15f,0f,0f,6f, 0f,0f,1.10f,0f,2f, 0f,0f,0f,1f,0f)
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { set(arrays[styleIndex]) }) }
        val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(rotated, 0f, 0f, paint)
        rotated.recycle()
        return out
    }

    private fun yuvToBitmap(image: ImageProxy): Bitmap {
        val w = image.width; val h = image.height; val ySize = w * h
        val nv21 = ByteArray(ySize + ySize / 2)
        val yBuf = image.planes[0].buffer; val uBuf = image.planes[1].buffer; val vBuf = image.planes[2].buffer
        val yRow = image.planes[0].rowStride
        val uRow = if (image.planes[1].rowStride == 0) w else image.planes[1].rowStride
        val uPix = image.planes[1].pixelStride
        val vRow = if (image.planes[2].rowStride == 0) w else image.planes[2].rowStride
        val vPix = image.planes[2].pixelStride
        var pos = 0
        for (row in 0 until h) { yBuf.position(row * yRow); yBuf.get(nv21, pos, w); pos += w }
        val uvH = h / 2; pos = ySize
        for (row in 0 until uvH) for (col in 0 until w / 2) {
            nv21[pos++] = vBuf.get(row * vRow + col * vPix)
            nv21[pos++] = uBuf.get(row * uRow + col * uPix)
        }
        val bos = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 95, bos)
        val data = bos.toByteArray()
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        camera?.let { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        initializeCamera()
    }

    private fun triggerHaptic() {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) initializeCamera()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_CODE = 10
    }
}
