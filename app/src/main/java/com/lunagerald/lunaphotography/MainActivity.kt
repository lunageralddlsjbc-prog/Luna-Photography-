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
import android.view.View
import android.widget.ImageButton
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
    private var extensionsManager: ExtensionsManager? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null // STORE THE CAMERA REFERENCE HERE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.previewView)
        focusRing = findViewById(R.id.focusRing)
        val shutterBtn = findViewById<ImageButton>(R.id.shutterBtn)
        val switchBtn = findViewById<ImageButton>(R.id.switchBtn)

        shutterBtn.setOnClickListener { triggerHaptic(); takePhoto() }
        switchBtn.setOnClickListener { switchCamera() }

        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                triggerHaptic()
                showFocusRing(event.x, event.y)
                startTapToFocus(event.x, event.y)
            }
            true
        }

        if (allPermissionsGranted()) initializeCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE)
    }

    private fun initializeCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            val extFuture = ExtensionsManager.getInstanceAsync(this, cameraProvider!!)
            extFuture.addListener({
                try { extensionsManager = extFuture.get() } catch (_: Exception) {}
                startCamera()
            }, ContextCompat.getMainExecutor(this))
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also { 
            it.setSurfaceProvider(previewView.surfaceProvider) 
        }
        val baseSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        
        val finalSelector = if (extensionsManager != null && 
            extensionsManager!!.isExtensionAvailable(baseSelector, ExtensionMode.HDR)) {
            extensionsManager!!.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
        } else baseSelector
        
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture
        
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, finalSelector, preview, capture) // STORE IT HERE
        } catch (e: Exception) {
            Toast.makeText(this, "Camera init failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTapToFocus(x: Float, y: Float) {
        val cam = camera ?: return // USE THE STORED REFERENCE (Fixes "Unresolved reference: camera")
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, 
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.visibility = View.VISIBLE
        focusRing.translationX = x - (focusRing.width / 2f)
        focusRing.translationY = y - (focusRing.height / 2f)
        focusRing.animate().scaleX(1.2f).scaleY(1.2f).alpha(1f).setDuration(200).withEndAction {
            focusRing.animate().scaleX(1f).scaleY(1f).alpha(0f).setDuration(400).withEndAction {
                focusRing.visibility = View.GONE
            }.start()
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

        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rot = image.imageInfo.rotationDegrees
                    val sourceBitmap = if (image.format == ImageFormat.JPEG) {
                        val buf = image.planes[0].buffer
                        val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        yuvToBitmap(image)
                    }
                    image.close()

                    val outputBitmap = applyLunaProcessing(sourceBitmap, rot)
                    sourceBitmap.recycle()

                    val bos = ByteArrayOutputStream()
                    outputBitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                    outputBitmap.recycle()

                    resolver.openOutputStream(uri)?.use { it.write(bos.toByteArray()) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    runOnUiThread { Toast.makeText(this@MainActivity, "Saved: Premium Quality", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Encode error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun applyLunaProcessing(src: Bitmap, rotation: Int): Bitmap {
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { 
                if (it != src) src.recycle() 
            }
        } else src

        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                1.05f, 0.0f,  0.0f,  0.0f,  8.0f,
                0.0f,  1.03f, 0.0f,  0.0f,  4.0f,
                0.0f,  0.0f,  0.95f, 0.0f,  0.0f,
                0.0f,  0.0f,  0.0f,  1.0f,  0.0f
            ))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        val result = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(rotated, 0f, 0f, paint)
        if (result != rotated) rotated.recycle()
        return result
    }

    private fun yuvToBitmap(image: ImageProxy): Bitmap {
        val w = image.width; val h = image.height
        val ySize = w * h
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
        for (row in 0 until uvH) {
            for (col in 0 until w / 2) {
                nv21[pos++] = vBuf.get(row * vRow + col * vPix)
                nv21[pos++] = uBuf.get(row * uRow + col * uPix)
            }
        }
        val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
        val bos = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, w, h), 95, bos)
        return BitmapFactory.decodeByteArray(bos.toByteArray(), 0, bos.size())
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) 
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        startCamera()
    }

    private fun triggerHaptic() {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (v != null && v.hasVibrator()) 
                v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { 
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED 
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) 
            initializeCamera()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_CODE = 10
    }
}
