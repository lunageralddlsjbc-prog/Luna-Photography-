package com.lunagerald.lunaphotography

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
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
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), QuickMenu.Callbacks {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var isIPhone = false
    private var isAI = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var previewView: PreviewView
    private lateinit var focusRing: View
    private var currentCamera: Camera? = null

    private val flashModes = arrayOf(ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON, ImageCapture.FLASH_MODE_OFF)
    private val flashLabels = arrayOf("⚡ AUTO", "⚡ ON", "⚡ OFF")
    private var flashIndex = 0

    private lateinit var quickMenu: QuickMenu
    private var activeLook: Look = Look.NORMAL
    private var isNightMode = false
    private var isDepthEnabled = false
    private var isLiveEnabled = false
    private var aspectIndex = 0
    private var timerIndex = 0
    private var exposureValue = 0f
    private var manualShutterNs = 66666667L
    private var manualIso = 250
    private var manualModeEnabled = true
                private fun applyProPipeline(src: android.graphics.Bitmap): android.graphics.Bitmap { val base = applyIPhoneFilter(src); val w = base.width; val h = base.height; val px = IntArray(w*h); base.getPixels(px,0,w,0,0,w,h); for (i in px.indices) { val c = px[i]; var r = android.graphics.Color.red(c).toFloat(); var g = android.graphics.Color.green(c).toFloat(); var b = android.graphics.Color.blue(c).toFloat(); val lum = (0.299f*r+0.587f*g+0.114f*b).toInt(); if (lum < 90) { val t = (1f-lum/90f)*0.08f; val avg=(r+g+b)/3f; r=r*(1f-t)+avg*t; g=g*(1f-t)+avg*t; b=b*(1f-t)+avg*t }; fun cur(v: Float): Float { val x=v/255f; var sc=x+0.18f*x*(1f-x)*(x-0.5f)*2f+(if (x>0.75f) 0f else (1f-x)*(1f-x)*0.032f)-x*x*x*0.015f+(1f-x)*(1f-x)*0.045f; return sc.coerceIn(0f,1f)*255f }; val xr=cur(r)/255f; val xg=cur(g)/255f; val xb=cur(b)/255f; val warm=xr*xr*7f; val teal=(1f-xr)*(1f-xr)*5f; r=(cur(r)+warm-teal*0.6f).coerceIn(0f,255f); g=(cur(g)+teal*0.8f).coerceIn(0f,255f); b=(cur(b)-warm*0.7f+teal*0.6f).coerceIn(0f,255f); if (r>g && g>b && r>=110f && r<=245f && (r-g)>=12f && (r-g)<=62f && (g-b)>=8f && (g-b)<=42f && lum<120) { r=(r+6f).coerceIn(0f,255f); g=(g+5f).coerceIn(0f,255f); b=(b+4f).coerceIn(0f,255f) }; if (lum>220) { val over=(lum-220f)/35f; r=(r-over*10f).coerceIn(0f,255f); g=(g-over*10f).coerceIn(0f,255f); b=(b-over*10f).coerceIn(0f,255f) }; if (r>g && g>b && r>=110f && r<=245f && (r-g)>=12f && (r-g)<=62f && (g-b)>=8f && (g-b)<=42f) { r=(r*1.01f+1f)*0.85f+r*0.15f; g=(g*1.01f+1f)*0.85f+g*0.15f; b=b*0.99f*0.85f+b*0.15f } else if (b>r+18f && b>130f && (r-g)<25f) { r=r*0.98f*0.90f+r*0.10f; g=g*1.01f*0.90f+g*0.10f; b=(b*1.06f+2f)*0.90f+b*0.10f } else if (g>r+12f && g>b+8f && g>90f) { r=r*1.01f*0.90f+r*0.10f; g=g*0.98f*0.90f+g*0.10f; b=b*1.02f*0.90f+b*0.10f } else if (lum<85f) { r=r*1.08f*0.80f+r*0.20f; g=g*1.08f*0.80f+g*0.20f; b=b*1.08f*0.80f+b*0.20f }; val avg2=(r+g+b)/3f; val dR=r-avg2; val dG=g-avg2; val dB=b-avg2; val lim=10f; val det=if (lum in 30..220) 0.18f else 0.0f; val sR=if (kotlin.math.abs(dR)>6f) (dR*det).coerceIn(-lim,lim) else 0f; val sG=if (kotlin.math.abs(dG)>6f) (dG*det).coerceIn(-lim,lim) else 0f; val sB=if (kotlin.math.abs(dB)>6f) (dB*det).coerceIn(-lim,lim) else 0f; r=(r+sR).coerceIn(0f,255f); g=(g+sG).coerceIn(0f,255f); b=(b+sB).coerceIn(0f,255f); px[i]=android.graphics.Color.rgb(r.toInt(),g.toInt(),b.toInt()) }; val o=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888); o.setPixels(px,0,w,0,0,w,h); return o }
    private fun autoLowLightExposure() { val cam = currentCamera ?: return; val st = cam.cameraInfo.exposureState; if (!st.isExposureCompensationSupported) return; if (exposureValue != 0f) return; val r = st.exposureCompensationRange; val idx = (0.3f * 10).toInt().coerceIn(r.lower, r.upper); if (idx > st.exposureCompensationIndex) cam.cameraControl.setExposureCompensationIndex(idx) }
    private fun applyIPhoneFilter(src: android.graphics.Bitmap): android.graphics.Bitmap { val o = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888); val c = android.graphics.Canvas(o); val pa = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG); pa.colorFilter = android.graphics.ColorMatrixColorFilter(CINEMATIC_MATRIX); c.drawBitmap(src, 0f, 0f, pa); return o }
    private val CINEMATIC_MATRIX = android.graphics.ColorMatrix(floatArrayOf(0.94f,0.05f,0.01f,0f,2f, 0.03f,1.02f,0.02f,0f,0f, 0.01f,0.06f,0.95f,0f,-2f, 0f,0f,0f,1f,0f))

    private var formatIndex = 0
    private var styleIndex = 0

    private val previewResolutions = arrayOf(
        Size(960, 720), // 4:3 preview low-res smooth
        Size(1280, 720), // 16:9 preview
        Size(720, 720) // 1:1 preview
    )
    private val aspectResolutions = arrayOf(
        Size(1280, 960),  // 4:3
        Size(1280, 720),  // 16:9
        Size(960, 960)    // 1:1
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContentView(R.layout.activity_main)
        // MANUAL UI hook - STYLE ONLY. Heavy filter stays on save only.
        try {
            val root = findViewById<android.view.ViewGroup>(android.R.id.content)
            val controls = IPhoneControls(this, root as android.widget.FrameLayout)
        controls.build()
        controls.onCapture = { try { captureImage() } catch (e: Exception) { try { takePhoto() } catch (e2: Exception) {} } }
        controls.onManualChange = { ns, iso, wb, manual -> try { manualShutterNs = ns; manualIso = iso.coerceAtLeast(100); manualModeEnabled = manual; exposureValue = 0f; applyExposure() } catch (e: Exception) {} }
        controls.onMode = { video -> try { exposureValue = if (video) 0f else 0f; applyExposure() } catch (e: Exception) {} }
        } catch (e: Exception) {}
        cameraExecutor = Executors.newSingleThreadExecutor()
        previewView = findViewById(R.id.previewView)
        previewView.scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE)
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        focusRing = findViewById(R.id.focusRing)
        val shutterBtn = findViewById<ImageButton>(R.id.shutterBtn)
        val switchBtn = findViewById<ImageButton>(R.id.switchBtn)
        val flashPill = findViewById<TextView>(R.id.flashPill)
        val gearPill = findViewById<ImageView>(R.id.gearPill)
        val zoomPills = listOf(findViewById<TextView>(R.id.zoomHalf), findViewById<TextView>(R.id.zoomOne), findViewById<TextView>(R.id.zoomThree))
        val modePills = listOf(findViewById<TextView>(R.id.mTime), findViewById<TextView>(R.id.mSlo), findViewById<TextView>(R.id.mVideo), findViewById<TextView>(R.id.mPhoto), findViewById<TextView>(R.id.mPortrait), findViewById<TextView>(R.id.mPano))

        quickMenu = QuickMenu(this, this)

        shutterBtn.setOnClickListener { triggerHaptic(); takePhoto() }
        switchBtn.setOnClickListener { switchCameraWithAnimation() }
        flashPill.setOnClickListener {
            flashIndex = (flashIndex + 1) % flashModes.size
            imageCapture?.flashMode = flashModes[flashIndex]
            flashPill.text = flashLabels[flashIndex]
            triggerHaptic()
        }
        gearPill.setOnClickListener { triggerHaptic(); quickMenu.toggle() }
        zoomPills[0].setOnClickListener { setZoom(1f, 0, zoomPills) }
        zoomPills[1].setOnClickListener { setZoom(2f, 1, zoomPills) }
        zoomPills[2].setOnClickListener { setZoom(3f, 2, zoomPills) }
        val modeNames = arrayOf("TIME-LAPSE", "SLO-MO", "VIDEO", "PHOTO", "PORTRAIT", "PANORAMA")
        modePills.forEachIndexed { i, tv ->
            tv.setOnClickListener {
                modePills.forEach { it.setTextColor(0x99FFFFFF.toInt()) }
                tv.setTextColor(0xFFFFD60A.toInt())
                if (modeNames[i] != "PHOTO") Toast.makeText(this, "${modeNames[i]} mode (Photo pipeline active)", Toast.LENGTH_SHORT).show()
                triggerHaptic()
            }
        }
        setupPinchZoom(zoomPills)
        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE)
    }

    private var currentZoom = 1f
    private var pinchStartZoom = 1f
    private var isPinching = false
    private fun setupPinchZoom(zoomPills: List<TextView>) {
        val det = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: android.view.ScaleGestureDetector): Boolean { isPinching = true; pinchStartZoom = currentZoom; return true }
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean { val zs = currentCamera?.cameraInfo?.zoomState?.value; val hi = zs?.maxZoomRatio ?: 3f; val lo = zs?.minZoomRatio ?: 1f; val z = (pinchStartZoom * d.scaleFactor).coerceIn(lo, hi); currentZoom = z; currentCamera?.cameraControl?.setZoomRatio(z); val idx = if (z < 1.5f) 0 else if (z < 2.5f) 1 else 2; zoomPills.forEachIndexed { i, tv -> if (i == idx) { tv.setBackgroundResource(R.drawable.pill_active); tv.setTextColor(0xFF000000.toInt()) } else { tv.setBackgroundResource(R.drawable.pill_inactive); tv.setTextColor(0xFFFFFFFF.toInt()) } }; return true }
            override fun onScaleEnd(d: android.view.ScaleGestureDetector) { isPinching = false }
        })
        previewView.setOnTouchListener { _, event -> det.onTouchEvent(event); if (!isPinching && event.actionMasked == android.view.MotionEvent.ACTION_UP) { triggerHaptic(); showFocusRing(event.x, event.y); startTapToFocus(event.x, event.y) }; true }
    }
    private fun setZoom(ratio: Float, activeIndex: Int, zoomPills: List<TextView>) {
        val cam = currentCamera ?: return
        val zs2 = cam.cameraInfo.zoomState.value
        val lo = zs2?.minZoomRatio ?: 1f
        val hi = zs2?.maxZoomRatio ?: 3f
        val clamped = ratio.coerceIn(lo, hi)
    val zs = cam.cameraInfo.zoomState.value
    val startZ = currentZoom; val targetZ = clamped; val zh = android.os.Handler(android.os.Looper.getMainLooper()); var zStep = 0; val zr = object : Runnable { override fun run() { zStep++; val t = (zStep / 4f).coerceIn(0f, 1f); val zz = startZ + (targetZ - startZ) * t; cam.cameraControl.setZoomRatio(zz); currentZoom = zz; if (zStep < 4) zh.postDelayed(this, 16) } }; zh.post(zr)
        zoomPills.forEachIndexed { i, tv ->
            if (i == activeIndex) { tv.setBackgroundResource(R.drawable.pill_active); tv.setTextColor(0xFF000000.toInt()) }
            else { tv.setBackgroundResource(R.drawable.pill_inactive); tv.setTextColor(0xFFFFFFFF.toInt()) }
        }
    }

    // FIXED: Correct 2-argument getInstanceAsync(Context, CameraProvider)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val extensionsFuture = ExtensionsManager.getInstanceAsync(this, cameraProvider)
            
            extensionsFuture.addListener({
                val extensionsManager = extensionsFuture.get()
                val baseSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                
                val preview = Preview.Builder().setTargetResolution(android.util.Size(1280,720)).setTargetFrameRate(android.util.Range(30,30))
                    .setTargetResolution(aspectResolutions[aspectIndex])
                    .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                
                val captureBuilder = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(100)
                    
                    .setTargetResolution(aspectResolutions[aspectIndex])
                
                var finalSelector = baseSelector
                if (false && isNightMode && extensionsManager.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT)) {
                    finalSelector = extensionsManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
    // HDR OFF - HDR blocks zoom
    // HDR selector disabled
                }
                
                Camera2Interop.Extender(captureBuilder).setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY).setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON).setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST).setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST).setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON).setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON).setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO).setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
        imageCapture = captureBuilder.build()
                imageCapture?.flashMode = flashModes[flashIndex]
                
                try {
                    cameraProvider.unbindAll()
                    currentCamera = cameraProvider.bindToLifecycle(this, finalSelector, preview, imageCapture)
                    if (exposureValue != 0f) applyExposure()
                } catch (e: Exception) {
                    Toast.makeText(this, "Camera init failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(this))
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyExposure() {
        val cam = currentCamera ?: return
        val state = cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val range = state.exposureCompensationRange
        val idx = (exposureValue * 10).roundToInt().coerceIn(range.lower, range.upper)
        cam.cameraControl.setExposureCompensationIndex(idx)
    }

    private fun switchCameraWithAnimation() {
        triggerHaptic()
        val switchBtn = findViewById<ImageButton>(R.id.switchBtn)
        ObjectAnimator.ofFloat(switchBtn, "rotation", 0f, 180f).apply { duration = 300; interpolator = AccelerateDecelerateInterpolator() }.start()
        val flipOut = ObjectAnimator.ofFloat(previewView, "rotationY", 0f, 90f).apply { duration = 150; interpolator = AccelerateInterpolator() }
        val flipIn = ObjectAnimator.ofFloat(previewView, "rotationY", -90f, 0f).apply { duration = 150; interpolator = DecelerateInterpolator() }
        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                startCamera()
                flipIn.start()
            }
        })
        flipOut.start()
    }

    private fun startTapToFocus(x: Float, y: Float) {
        val cam = currentCamera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.visibility = View.VISIBLE
        val _pv=IntArray(2); previewView.getLocationInWindow(_pv); val _pa=IntArray(2); (focusRing.parent as android.view.View).getLocationInWindow(_pa); focusRing.translationX = x - previewView.width / 2f
        focusRing.translationY = y - previewView.height / 2f
        focusRing.animate().scaleX(1.2f).scaleY(1.2f).alpha(1f).setDuration(200).withEndAction {
            focusRing.animate().scaleX(1f).scaleY(1f).alpha(0f).setDuration(400).withEndAction { focusRing.visibility = View.GONE }.start()
        }.start()
    }

    private fun takePhoto() {
        if (timerIndex > 0) {
            val delay = if (timerIndex == 1) 3000L else 10000L
            Toast.makeText(this, "Timer: ${delay/1000}s", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ triggerHaptic(); captureImage() }, delay)
        } else {
            captureImage()
        }
    }

    private var burstCount=0; private val burstList=mutableListOf<android.graphics.Bitmap>(); private var burstRot=0
private fun sharpnessScore(b: android.graphics.Bitmap): Double { val w=160; val hh=(b.height*w/b.width.toFloat()).toInt().coerceAtLeast(1); val sm=android.graphics.Bitmap.createScaledBitmap(b,w,hh,true); val px=IntArray(w*hh); sm.getPixels(px,0,w,0,0,w,hh); if(sm!=b) sm.recycle(); var su=0.0; var sq=0.0; var n=0; for(y in 1 until hh-1){ for(x in 1 until w-1){ val g={i:Int->0.299*android.graphics.Color.red(px[i])+0.587*android.graphics.Color.green(px[i])+0.114*android.graphics.Color.blue(px[i])}; val lap=g(y*w+x-1)+g(y*w+x+1)+g((y-1)*w+x)+g((y+1)*w+x)-4*g(y*w+x); su+=lap; sq+=lap*lap; n++ } }; val m=su/n; return sq/n-m*m }
private fun captureImage() {
        val capture = imageCapture ?: return
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "LUNA_${System.currentTimeMillis()}.${if (formatIndex == 0) "jpg" else "heic"}")
            put(MediaStore.MediaColumns.MIME_TYPE, if (formatIndex == 0) "image/jpeg" else "image/heic")
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
                    burstList.add(sourceBitmap); burstRot=rot; burstCount++; if (burstCount < 3) { capture.takePicture(cameraExecutor, this); return }; val best = burstList.maxByOrNull { sharpnessScore(it) } ?: sourceBitmap; for (b in burstList) { if (b != best) b.recycle() }; burstList.clear(); burstCount=0; val outputBitmap = applyProPipeline(best, burstRot)
                    sourceBitmap.recycle()
                    val bos = ByteArrayOutputStream()
                    if (formatIndex == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        outputBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 95, bos)
                    } else {
                        outputBitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                    }
                    outputBitmap.recycle()
                    resolver.openOutputStream(uri)?.use { it.write(bos.toByteArray()) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear(); contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    val modeLabel = if (isNightMode) "Night" else activeLook.label
                    runOnUiThread { Toast.makeText(this@MainActivity, "Saved ($modeLabel)", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Encode error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun applyProPipeline(src: Bitmap, rotation: Int): Bitmap {
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it != src) src.recycle() }
        } else src
        if (activeLook == Look.CINEMATIC) { val r = applyProPipeline(rotated); if (r != rotated) rotated.recycle(); return r }
    val matrix = if (activeLook == Look.CINEMATIC) Look.CINEMATIC.matrix else activeLook.matrix
        val cm = ColorMatrix().apply { set(matrix) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        val result = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(rotated, 0f, 0f, paint)
        if (result != rotated) rotated.recycle()
        return result
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

    private fun triggerHaptic() {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startCamera()
    }

    // QuickMenu.Callbacks implementation
    override fun onFlashCycle() {
        flashIndex = (flashIndex + 1) % flashModes.size
        imageCapture?.flashMode = flashModes[flashIndex]
    }
    override fun onLiveToggle(enabled: Boolean) { isLiveEnabled = enabled }
    override fun onAspectCycle() { aspectIndex = (aspectIndex + 1) % 3; startCamera() }
    override fun onTimerCycle() { timerIndex = (timerIndex + 1) % 3 }
    override fun onExposureChange(value: Float) { exposureValue = value; applyExposure() }
    override fun onStyleCycle() {
        val styles = arrayOf(Look.NORMAL, Look.LUNA, Look.CINEMATIC)
        styleIndex = (styleIndex + 1) % 3
        activeLook = styles[styleIndex % 3]
    }
    override fun onDepthToggle(enabled: Boolean) { isDepthEnabled = enabled }
    override fun onNightToggle(enabled: Boolean) { isNightMode = enabled; startCamera() }
    override fun onFormatCycle() { formatIndex = (formatIndex + 1) % 2 }
    private var isIPhoneOn=false
    override fun onIPhoneToggle(){isIPhoneOn=isIPhoneOn==false; applyIPhoneFilter()}
    private var isAIOn=false
    override fun onAIToggle(){isAIOn=isAIOn==false; applyIPhoneFilter()}
    private fun applyIPhoneFilter(){val v=findViewById<android.view.View>(R.id.previewView); v.foreground=if(isIPhoneOn||isAIOn) android.graphics.drawable.ColorDrawable(android.graphics.Color.argb(7,255,180,90)) else null}

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_CODE = 10
    }
}