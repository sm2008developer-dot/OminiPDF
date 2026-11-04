package com.ominipdf.soumalya.codegarage.app.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.CheckedTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.databinding.ActivityCameraBinding
import com.ominipdf.soumalya.codegarage.app.ui.BaseActivity
import com.ominipdf.soumalya.codegarage.app.utils.EdgeDetector
import com.ominipdf.soumalya.codegarage.app.utils.Extras
import com.ominipdf.soumalya.codegarage.app.utils.Prefs
import com.ominipdf.soumalya.codegarage.app.utils.ScanFlow
import com.ominipdf.soumalya.codegarage.app.utils.ScanMode
import com.ominipdf.soumalya.codegarage.app.utils.haptic
import com.ominipdf.soumalya.codegarage.app.utils.showProgress
import com.ominipdf.soumalya.codegarage.app.utils.toast
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class CameraActivity : BaseActivity() {
    override val darkSystemBars = true

    private lateinit var b: ActivityCameraBinding
    @Volatile private var docId: String? = null
    private var mode = ScanMode.DOCUMENT
    private var batch = false
    private var autoCapture = false
    private var gridOn = false
    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private var capturedCount = 0
    private var busy = false
    private var lastAnalysisTs = 0L
    private var smoothQuad: FloatArray? = null
    private var stableFrames = 0
    private var autoArmed = true
    private var lastShotQuad: FloatArray? = null
    private var lastAutoShot = 0L

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showPermissionDenied()
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importFromGallery(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(b.root)
        docId = intent.getStringExtra(Extras.DOC_ID)
        mode = try { ScanMode.valueOf(intent.getStringExtra(Extras.MODE) ?: "DOCUMENT") } catch (e: Exception) { ScanMode.DOCUMENT }
        autoCapture = Prefs.autoCapture

        b.previewArea.doOnLayout { fitPreview() }
        setupButtons()
        selectMode(mode)
        updateAutoIcon()
        ensureCamera()
    }

    private fun fitPreview() {
        val aw = b.previewArea.width
        val ah = b.previewArea.height
        if (aw == 0 || ah == 0) return
        var w = aw
        var h = (w * 4f / 3f).toInt()
        if (h > ah) {
            h = ah
            w = (h * 3f / 4f).toInt()
        }
        val lp = b.previewHolder.layoutParams
        lp.width = w
        lp.height = h
        b.previewHolder.layoutParams = lp
    }

    private fun setupButtons() {
        b.btnClose.setOnClickListener { finish() }
        b.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        b.btnPermGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        b.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        b.shutter.setOnClickListener { it.haptic(); capture() }
        b.btnDone.setOnClickListener { finishBatch() }
        b.btnGrid.setOnClickListener {
            gridOn = !gridOn
            b.overlay.showGrid = gridOn
            b.btnGrid.alpha = if (gridOn) 1f else 0.6f
        }
        b.btnGrid.alpha = 0.6f
        b.btnFlash.setOnClickListener {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                else -> ImageCapture.FLASH_MODE_AUTO
            }
            imageCapture?.flashMode = flashMode
            b.btnFlash.setImageResource(
                when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                    ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_flash_off
                    else -> R.drawable.ic_flash_auto
                }
            )
        }
        b.btnAutoCapture.setOnClickListener {
            autoCapture = !autoCapture
            Prefs.autoCapture = autoCapture
            updateAutoIcon()
            toast(if (autoCapture) R.string.auto_capture_on else R.string.auto_capture_off)
        }
        b.btnBatch.setOnClickListener {
            batch = !batch
            b.btnBatch.setImageResource(if (batch) R.drawable.ic_batch else R.drawable.ic_single)
            toast(if (batch) R.string.mode_batch else R.string.mode_single)
            updateDoneUi()
        }
        val chips = mapOf<CheckedTextView, ScanMode>(
            b.modeDocument to ScanMode.DOCUMENT, b.modeOcr to ScanMode.OCR,
            b.modeId to ScanMode.ID_CARD, b.modePassport to ScanMode.PASSPORT
        )
        chips.forEach { (v, m) -> v.setOnClickListener { selectMode(m) } }
        setupZoom()
    }

    private fun updateAutoIcon() {
        b.btnAutoCapture.alpha = if (autoCapture) 1f else 0.6f
    }

    private fun selectMode(m: ScanMode) {
        mode = m
        b.modeDocument.isChecked = m == ScanMode.DOCUMENT
        b.modeOcr.isChecked = m == ScanMode.OCR
        b.modeId.isChecked = m == ScanMode.ID_CARD
        b.modePassport.isChecked = m == ScanMode.PASSPORT
        b.overlay.guideRatio = when (m) {
            ScanMode.ID_CARD -> 1.586f
            ScanMode.PASSPORT -> 1.42f
            else -> 0.72f
        }
    }

    private fun setupZoom() {
        val sgd = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val cam = camera ?: return true
                val zs = cam.cameraInfo.zoomState.value ?: return true
                val ratio = (zs.zoomRatio * d.scaleFactor).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                cam.cameraControl.setZoomRatio(ratio)
                b.zoomText.text = String.format(Locale.US, "%.1fx", ratio)
                b.zoomText.visibility = View.VISIBLE
                b.zoomText.removeCallbacks(hideZoom)
                b.zoomText.postDelayed(hideZoom, 1200)
                return true
            }
        })
        b.previewView.setOnTouchListener { v, e ->
            sgd.onTouchEvent(e)
            if (e.action == MotionEvent.ACTION_UP) v.performClick()
            true
        }
    }

    private val hideZoom = Runnable { b.zoomText.visibility = View.GONE }

    // ---------- permission ----------
    private fun ensureCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.grant_permission) { _, _ -> permLauncher.launch(Manifest.permission.CAMERA) }
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .show()
    }

    private fun showPermissionDenied() {
        b.permissionView.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (b.permissionView.visibility == View.VISIBLE &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            b.permissionView.visibility = View.GONE
            startCamera()
        }
    }

    // ---------- camera ----------
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val rs = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = Preview.Builder().setResolutionSelector(rs).build()
                preview.surfaceProvider = b.previewView.surfaceProvider
                val ic = ImageCapture.Builder()
                    .setResolutionSelector(rs)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(flashMode)
                    .build()
                imageCapture = ic
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(rs)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image -> analyze(image) }
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, ic, analysis)
            } catch (e: Exception) {
                toast(R.string.camera_error)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalysisTs < 120 || busy) return
            lastAnalysisTs = now
            val r = image.imageInfo.rotationDegrees
            val plane = image.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pxStride = plane.pixelStride
            val w = image.width
            val h = image.height
            val rw = if (r == 90 || r == 270) h else w
            val rh = if (r == 90 || r == 270) w else h
            val scale = 160f / max(rw, rh)
            val sw = max(8, (rw * scale).toInt())
            val sh = max(8, (rh * scale).toInt())
            val gray = IntArray(sw * sh)
            for (oy in 0 until sh) {
                val uy = min(rh - 1, ((oy + 0.5f) / sh * rh).toInt())
                for (ox in 0 until sw) {
                    val ux = min(rw - 1, ((ox + 0.5f) / sw * rw).toInt())
                    val sx: Int
                    val sy: Int
                    when (r) {
                        90 -> { sx = uy; sy = h - 1 - ux }
                        180 -> { sx = w - 1 - ux; sy = h - 1 - uy }
                        270 -> { sx = w - 1 - uy; sy = ux }
                        else -> { sx = ux; sy = uy }
                    }
                    gray[oy * sw + ox] = buf.get(sy * rowStride + sx * pxStride).toInt() and 0xFF
                }
            }
            val quad = EdgeDetector.detect(gray, sw, sh)
            runOnUiThread { onDetected(quad) }
        } catch (e: Exception) {
            // ignore a bad frame
        } finally {
            image.close()
        }
    }

    private fun onDetected(quad: FloatArray?) {
        if (quad == null) {
            smoothQuad = null
            stableFrames = 0
            autoArmed = true
            b.overlay.setQuad(null, false)
            b.hintText.setText(R.string.camera_hint)
            return
        }
        val prev = smoothQuad
        val moved = if (prev == null) 1f else EdgeDetector.maxDiff(prev, quad)
        val sm = if (prev == null) quad else FloatArray(8) { prev[it] * 0.5f + quad[it] * 0.5f }
        stableFrames = if (moved < 0.02f) stableFrames + 1 else 0
        smoothQuad = sm
        val stable = stableFrames >= 3
        b.overlay.setQuad(sm, stable)
        b.hintText.setText(if (stable) R.string.camera_hold_steady else R.string.camera_hint)
        val shot = lastShotQuad
        if (!autoArmed && shot != null && EdgeDetector.maxDiff(shot, sm) > 0.1f) autoArmed = true
        val now = SystemClock.elapsedRealtime()
        if (autoCapture && autoArmed && stableFrames >= 7 && now - lastAutoShot > 2500) {
            lastAutoShot = now
            lastShotQuad = sm
            autoArmed = false
            stableFrames = 0
            capture()
        }
    }

    // ---------- capture ----------
    private fun capture() {
        val ic = imageCapture ?: return
        if (busy) return
        busy = true
        b.shutter.isEnabled = false
        val file = File(cacheDir, "cap_" + System.currentTimeMillis() + ".jpg")
        ic.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onCaptured(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    b.shutter.isEnabled = true
                    toast(R.string.camera_error)
                }
            }
        )
    }

    private fun onCaptured(file: File) {
        if (batch) {
            busy = false
            b.shutter.isEnabled = true
            lifecycleScope.launch(worker) {
                val res = ScanFlow.addImages(applicationContext, docId, listOf(file), true)
                file.delete()
                if (res != null) docId = res.first.id
                withContext(Dispatchers.Main) {
                    if (res != null) {
                        capturedCount += res.second.size
                        updateDoneUi()
                    } else {
                        toast(R.string.error_generic)
                    }
                }
            }
        } else {
            val dlg = showProgress(R.string.processing)
            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    val r = ScanFlow.addImages(applicationContext, docId, listOf(file), true)
                    file.delete()
                    r
                }
                dlg.dismiss()
                if (res == null) {
                    busy = false
                    b.shutter.isEnabled = true
                    toast(R.string.error_generic)
                } else {
                    ScanFlow.openCrop(this@CameraActivity, res.first.id, res.second[0].id, false)
                    finish()
                }
            }
        }
    }

    private fun updateDoneUi() {
        val show = batch && capturedCount > 0
        b.doneHolder.visibility = if (show) View.VISIBLE else View.INVISIBLE
        b.countBadge.text = capturedCount.toString()
    }

    private fun finishBatch() {
        if (capturedCount == 0) return
        val dlg = showProgress(R.string.processing)
        lifecycleScope.launch {
            withContext(worker) { /* waits for queued captures */ }
            dlg.dismiss()
            val id = docId
            if (id != null) {
                ScanFlow.openDoc(this@CameraActivity, id, true)
                finish()
            }
        }
    }

    private fun importFromGallery(uris: List<Uri>) {
        val dlg = showProgress(R.string.processing)
        lifecycleScope.launch {
            val res = withContext(worker) { ScanFlow.addImages(applicationContext, docId, uris, true) }
            dlg.dismiss()
            if (res == null) {
                toast(R.string.error_generic)
                return@launch
            }
            docId = res.first.id
            if (res.second.size == 1 && !batch) {
                ScanFlow.openCrop(this@CameraActivity, res.first.id, res.second[0].id, false)
            } else {
                ScanFlow.openDoc(this@CameraActivity, res.first.id, true)
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }
}
