package com.ominipdf.soumalya.codegarage.app.ui.crop

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.FilterType
import com.ominipdf.soumalya.codegarage.app.data.PageItem
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.databinding.ActivityCropBinding
import com.ominipdf.soumalya.codegarage.app.ui.BaseActivity
import com.ominipdf.soumalya.codegarage.app.utils.EdgeDetector
import com.ominipdf.soumalya.codegarage.app.utils.Extras
import com.ominipdf.soumalya.codegarage.app.utils.ImageProcessor
import com.ominipdf.soumalya.codegarage.app.utils.ImageUtils
import com.ominipdf.soumalya.codegarage.app.utils.PageRenderer
import com.ominipdf.soumalya.codegarage.app.utils.QuadUtils
import com.ominipdf.soumalya.codegarage.app.utils.ScanFlow
import com.ominipdf.soumalya.codegarage.app.utils.haptic
import com.ominipdf.soumalya.codegarage.app.utils.showProgress
import com.ominipdf.soumalya.codegarage.app.utils.toast

class CropActivity : BaseActivity() {
    private lateinit var b: ActivityCropBinding
    private lateinit var docId: String
    private lateinit var page: PageItem        // the stored page (updated on Done)
    private lateinit var work: PageItem        // working copy
    private var fromEdit = false
    private var rotatedBmp: Bitmap? = null
    private var baseBmp: Bitmap? = null
    private var lastPreview: Bitmap? = null
    private var previewJob: Job? = null
    private var tab = 0
    private lateinit var filterAdapter: FilterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCropBinding.inflate(layoutInflater)
        setContentView(b.root)
        docId = intent.getStringExtra(Extras.DOC_ID) ?: return finish()
        val pageId = intent.getStringExtra(Extras.PAGE_ID) ?: return finish()
        fromEdit = intent.getBooleanExtra(Extras.FROM_EDIT, false)
        val doc = ScanRepository.getDoc(docId) ?: return finish()
        page = doc.pages.firstOrNull { it.id == pageId } ?: return finish()
        work = page.copyParams()

        setupTabs()
        setupCropTools()
        setupFilters()
        setupAdjust()
        b.btnCancel.setOnClickListener { cancel() }
        b.btnDone.setOnClickListener { done() }
        loadImage()
    }

    private fun cancel() {
        if (!fromEdit) {
            // page was already auto-saved when it was captured; just continue to the document
            ScanFlow.openDoc(this, docId, true)
        }
        finish()
    }

    private fun loadImage() {
        b.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                val raw = ImageUtils.decodeScaled(ScanRepository.origFile(docId, page.id), 1800)
                if (raw == null) null else ImageUtils.rotate(raw, work.rotation)
            }
            b.loading.visibility = View.GONE
            if (bmp == null) {
                toast(R.string.error_generic)
                finish()
                return@launch
            }
            rotatedBmp = bmp
            b.cropView.setBitmap(bmp)
            b.cropView.setQuad(work.quad)
        }
    }

    // ---------- tabs ----------
    private fun setupTabs() {
        b.tabs.addTab(b.tabs.newTab().setText(R.string.tab_crop))
        b.tabs.addTab(b.tabs.newTab().setText(R.string.tab_filter))
        b.tabs.addTab(b.tabs.newTab().setText(R.string.tab_adjust))
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) = switchTab(t.position)
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    private fun switchTab(i: Int) {
        val old = tab
        tab = i
        b.panelCrop.visibility = if (i == 0) View.VISIBLE else View.GONE
        b.panelFilter.visibility = if (i == 1) View.VISIBLE else View.GONE
        b.panelAdjust.visibility = if (i == 2) View.VISIBLE else View.GONE
        if (i == 0) {
            b.cropView.visibility = View.VISIBLE
            b.previewImage.visibility = View.GONE
            b.btnCompare.visibility = View.GONE
        } else {
            b.cropView.visibility = View.GONE
            b.previewImage.visibility = View.VISIBLE
            b.btnCompare.visibility = View.VISIBLE
            if (old == 0) buildBase()
        }
    }

    // ---------- crop tools ----------
    private fun setupCropTools() {
        b.btnAuto.setOnClickListener {
            it.haptic()
            val bmp = rotatedBmp ?: return@setOnClickListener
            b.cropView.setQuad(EdgeDetector.detectBitmap(bmp) ?: PageItem.fullQuad())
        }
        b.btnFull.setOnClickListener { it.haptic(); b.cropView.setQuad(PageItem.fullQuad()) }
        b.btnRotL.setOnClickListener { it.haptic(); rotate(false) }
        b.btnRotR.setOnClickListener { it.haptic(); rotate(true) }
    }

    private fun rotate(cw: Boolean) {
        val bmp = rotatedBmp ?: return
        val q = QuadUtils.rotate(b.cropView.getQuad(), cw)
        val out = ImageUtils.rotate(bmp, if (cw) 90 else 270, true)
        rotatedBmp = out
        work.rotation = ((work.rotation + (if (cw) 90 else 270)) % 360)
        b.cropView.setBitmap(out)
        b.cropView.setQuad(q)
    }

    // ---------- filters ----------
    private fun setupFilters() {
        val names = listOf(
            R.string.filter_original, R.string.crop_auto, R.string.mode_document, R.string.filter_bright,
            R.string.filter_no_shadow, R.string.filter_color, R.string.filter_bw, R.string.filter_super, R.string.filter_enhance
        ).map { getString(it) }
        filterAdapter = FilterAdapter(names) { f ->
            work.filter = f
            renderPreview()
        }
        filterAdapter.selected = work.filter
        b.panelFilter.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.panelFilter.adapter = filterAdapter
        setupCompare()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCompare() {
        b.btnCompare.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> baseBmp?.let { b.previewImage.setImageBitmap(it) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lastPreview?.let { b.previewImage.setImageBitmap(it) }
                    if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }

    private fun buildBase() {
        val src = rotatedBmp ?: return
        work.quad = QuadUtils.order(b.cropView.getQuad())
        val quad = work.quad
        b.loading.visibility = View.VISIBLE
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val base = ImageProcessor.warp(src, quad, 1100)
                val small = ImageUtils.scaleToMax(ImageProcessor.warp(base, PageItem.fullQuad(), 160), 160)
                val thumbs = FilterType.entries.map { f ->
                    val t = ImageProcessor.process(small, f, 0, 0, 0)
                    if (t === small) small.copy(Bitmap.Config.ARGB_8888, false) else t
                }
                Pair(base, thumbs)
            }
            baseBmp = result.first
            filterAdapter.thumbs = result.second
            b.loading.visibility = View.GONE
            renderPreview()
        }
    }

    private fun renderPreview() {
        val base = baseBmp ?: return
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            delay(60)
            val f = work.filter
            val br = work.brightness
            val ct = work.contrast
            val sh = work.sharpness
            val out = withContext(Dispatchers.Default) { ImageProcessor.process(base, f, br, ct, sh) }
            lastPreview = out
            b.previewImage.setImageBitmap(out)
        }
    }

    // ---------- adjust ----------
    private fun setupAdjust() {
        b.seekBrightness.progress = work.brightness + 100
        b.seekContrast.progress = work.contrast + 100
        b.seekSharpness.progress = work.sharpness
        val l = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                work.brightness = b.seekBrightness.progress - 100
                work.contrast = b.seekContrast.progress - 100
                work.sharpness = b.seekSharpness.progress
                renderPreview()
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        }
        b.seekBrightness.setOnSeekBarChangeListener(l)
        b.seekContrast.setOnSeekBarChangeListener(l)
        b.seekSharpness.setOnSeekBarChangeListener(l)
        b.btnResetAdjust.setOnClickListener {
            work.brightness = 0; work.contrast = 0; work.sharpness = 0
            b.seekBrightness.progress = 100
            b.seekContrast.progress = 100
            b.seekSharpness.progress = 0
            renderPreview()
        }
    }

    // ---------- done ----------
    private fun done() {
        if (tab == 0) work.quad = QuadUtils.order(b.cropView.getQuad())
        page.applyFrom(work)
        val dlg = showProgress(R.string.processing)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { PageRenderer.render(docId, page) }
            dlg.dismiss()
            if (!ok) {
                toast(R.string.error_generic)
                return@launch
            }
            ScanRepository.getDoc(docId)?.let { ScanRepository.touch(it) }
            if (!fromEdit) ScanFlow.openDoc(this@CropActivity, docId, true)
            finish()
        }
    }
}
