package com.lunagerald.lunaphotography

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var previewView: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.previewView)
        val shutterBtn = findViewById<ImageButton>(R.id.shutterBtn)
        val switchBtn = findViewById<ImageButton>(R.id.switchBtn)

        shutterBtn.setOnClickListener { triggerHaptic(); takePhoto() }
        switchBtn.setOnClickListener { switchCamera() }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera init failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
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
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            Toast.makeText(this, "Storage insert failed", Toast.LENGTH_SHORT).show()
            return
        }
        
        // CORRECTED: ImageCapture.OnImageCapturedCallback() and onCaptureSuccess
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rot = image.imageInfo.rotationDegrees
                    val bmp: Bitmap = if (image.format == ImageFormat.JPEG) {
                        val buf = image.planes[0].buffer
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        yuvToBitmap(image)
                    }
                    image.close()
                    
                    val finalBmp = if (rot != 0) rotate(bmp, rot) else bmp
                    val bos = ByteArrayOutputStream()
                    finalBmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                    val finalBytes = bos.toByteArray()
                    
                    resolver.openOutputStream(uri)?.use { it.write(finalBytes) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    runOnUiThread { Toast.makeText(this@MainActivity, "Saved to Gallery", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    try { image.close() } catch (_: Exception) {}
                    runOnUiThread { Toast.makeText(this@MainActivity, "Encode error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun yuvToBitmap(image: ImageProxy): Bitmap {
        val w = image.width
        val h = image.height
        val ySize = w * h
        val nv21 = ByteArray(ySize + ySize / 2)
        val yBuf = image.planes[0].buffer
        val uBuf = image.planes[1].buffer
        val vBuf = image.planes[2].buffer
        val yRow = image.planes[0].rowStride
        val uRow = if (image.planes[1].rowStride == 0) w else image.planes[1].rowStride
        val uPix = image.planes[1].pixelStride
        val vRow = if (image.planes[2].rowStride == 0) w else image.planes[2].rowStride
        val vPix = image.planes[2].pixelStride
        
        var pos = 0
        for (row in 0 until h) {
            yBuf.position(row * yRow)
            yBuf.get(nv21, pos, w)
            pos += w
        }
        val uvH = h / 2
        pos = ySize
        for (row in 0 until uvH) {
            for (col in 0 until w / 2) {
                nv21[pos++] = vBuf.get(row * vRow + col * vPix)
                nv21[pos++] = uBuf.get(row * uRow + col * uPix)
            }
        }
        val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
        val bos = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, w, h), 92, bos)
        val data = bos.toByteArray()
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    private fun rotate(src: Bitmap, deg: Int): Bitmap {
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out != src) src.recycle()
        return out
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
        if (requestCode == REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startCamera()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_CODE = 10
    }
}
