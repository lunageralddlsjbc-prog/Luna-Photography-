package com.lunagerald.lunaphotography

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var extensionsManager: ExtensionsManager? = null
    private lateinit var previewView: PreviewView
    private lateinit var focusRing: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.previewView)
        focusRing = findViewById(R.id.focusRing)
        val shutterBtn = findViewById<ImageButton>(R.id.shutterBtn)
        val switchBtn = findViewById<ImageButton>(R.id.switchBtn)

        shutterBtn.setOnClickListener { 
            triggerHaptic()
            takePhoto() 
        }
        switchBtn.setOnClickListener { switchCamera() }

        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                triggerHaptic()
                showFocusRing(event.x, event.y)
                startTapToFocus(event.x, event.y)
            }
            true
        }

        if (allPermissionsGranted()) {
            initializeCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE)
        }
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(this, cameraProviderFuture)

        extensionsManagerFuture.addListener({
            extensionsManager = extensionsManagerFuture.get()
            startCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(1920, 1080))
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            
            var imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(android.util.Size(4000, 3000))
            
            val extManager = extensionsManager
            if (extManager != null && extManager.isExtensionAvailable(cameraSelector, ExtensionMode.HDR)) {
                imageCaptureBuilder = extManager.getExtensionEnabledImageCapture(imageCaptureBuilder.build(), cameraSelector)
            }
            
            imageCapture = imageCaptureBuilder.build()
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera init failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startTapToFocus(x: Float, y: Float) {
        val camera = previewView.camera ?: return
        val meteringPointFactory = previewView.meteringPointFactory
        val point = meteringPointFactory.createPoint(x, y)
        
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            
        camera.cameraControl.startFocusAndMetering(action)
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.visibility = View.VISIBLE
        focusRing.translationX = x - (focusRing.width / 2f)
        focusRing.translationY = y - (focusRing.height / 2f)
        
        focusRing.animate()
            .scaleX(1.2f).scaleY(1.2f)
            .alpha(1f)
            .setDuration(200)
            .withEndAction {
                focusRing.animate()
                    .scaleX(1f).scaleY(1f)
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction { focusRing.visibility = View.GONE }
                    .start()
            }
            .start()
    }

    // FIXED: Saves directly to public Gallery using MediaStore
    private fun takePhoto() {
        val capture = imageCapture ?: return
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "LUNA_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LunaPhotography")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            val contentResolver = applicationContext.contentResolver
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            if (imageUri != null) {
                val outputOptions = ImageCapture.OutputFileOptions.Builder(contentResolver, imageUri, contentValues).build()
                capture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(imageUri, contentValues, null, null)
                        }
                        runOnUiThread { Toast.makeText(this@MainActivity, "Saved to Gallery", Toast.LENGTH_SHORT).show() }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "Capture error", Toast.LENGTH_SHORT).show() }
                    }
                })
            }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show() }
        }
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
        } catch (e: Exception) { /* Ignore */ }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializeCamera()
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_CODE = 10
    }
}
