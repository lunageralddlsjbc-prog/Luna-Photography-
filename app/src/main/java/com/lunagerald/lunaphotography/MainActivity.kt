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

        shutterBtn.setOnClickListener {
            triggerHaptic()
            takePhoto()
        }
        switchBtn.setOnClickListener { switchCamera() }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera init failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "LUNA_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val contentResolver = applicationContext.contentResolver
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return

            // Manual capture: bypass CameraX's native JPEG processor (fails on this HAL)
            capture.takePicture(cameraExecutor, object : ImageCapture.OnCapturedCallback {
                override fun onCapture(image: ImageProxy) {
                    try {
                        val rotation = image.imageInfo.rotationDegrees
                        val bitmap = when (image.format) {
                            ImageFormat.JPEG -> BitmapFactory.decodeByteArray(
                                image.planes[0].buffer.array(), 0, image.planes[0].buffer.limit()
                            )
                            else -> yuvToBitmap(image)
                        }
                        image.close()
                        val rotated = if (rotation != 0) rotateBitmap(bitmap, rotation) else bitmap

                        contentResolver.openOutputStream(imageUri)?.use { out ->
                            rotated.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(imageUri, contentValues, null, null)
                        }
                        runOnUiThread { Toast.makeText(this@MainActivity, "Saved to Gallery", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        image.close()
                        runOnUiThread { Toast.makeText(this@MainActivity, "Encode error: ${e.message}", Toast.LENGTH_SHORT).show() }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show() }
                }
            })
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun yuvToBitmap(image: ImageProxy): Bitmap {
        val nv21 = yuvToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val baos = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 92, baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun yuvToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val nv21 = ByteArray(ySize + ySize / 2)
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        var pos = 0
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }
        val uvHeight = height / 2
        pos = ySize
        for (row in 0 until uvHeight) {
            for (col in 0 until width / 2) {
                val v = vBuffer.get(row * uvRowStride + col * uvPixelStride)
                val u = uBuffer.get(row * uvRowStride + col * uvPixelStride)
                nv21[pos++] = v
                nv21[pos++] = u
            }
        }
        return nv21
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result != source) source.recycle()
        return result
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun triggerHaptic() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_CODE = 10
    }
}
