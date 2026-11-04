package com.ominipdf.soumalya.codegarage.app.ui.doc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.ScanDoc
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.databinding.ActivityDocumentBinding
import com.ominipdf.soumalya.codegarage.app.databinding.DialogPageSettingsBinding
import com.ominipdf.soumalya.codegarage.app.databinding.ItemFolderChipBinding
import com.ominipdf.soumalya.codegarage.app.ui.BaseActivity
import com.ominipdf.soumalya.codegarage.app.ui.camera.CameraActivity
import com.ominipdf.soumalya.codegarage.app.ui.save.SaveActivity
import com.ominipdf.soumalya.codegarage.app.utils.Exporter
import com.ominipdf.soumalya.codegarage.app.utils.Extras
import com.ominipdf.soumalya.codegarage.app.utils.ScanFlow
import com.ominipdf.soumalya.codegarage.app.utils.showInputDialog
import com.ominipdf.soumalya.codegarage.app.utils.showProgress
import com.ominipdf.soumalya.codegarage.app.utils.toast

class DocumentActivity : BaseActivity() {
    private lateinit var b: ActivityDocumentBinding
    private var docId: String = ""
    private lateinit var adapter: PageAdapter

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importUris(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDocumentBinding.inflate(layoutInflater)
        setContentView(b.root)
        docId = intent.getStringExtra(Extras.DOC_ID) ?: return finish()
        adapter = PageAdapter(docId)
        b.pager.adapter = adapter
        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateIndicator()
        })
        b.btnBack.setOnClickListener { finish() }
        b.docName.setOnClickListener { renameDoc() }
        b.btnSave.setOnClickListener { startActivity(Intent(this, SaveActivity::class.java).putExtra(Extras.DOC_ID, docId)) }
        b.btnAddPage.setOnClickListener { showAddPage() }
        b.btnEditPage.setOnClickListener {
            val p = adapter.pageAt(b.pager.currentItem) ?: return@setOnClickListener
            ScanFlow.openCrop(this, docId, p.id, true)
        }
        b.btnManage.setOnClickListener { startActivity(Intent(this, ManagePagesActivity::class.java).putExtra(Extras.DOC_ID, docId)) }
        b.btnPageSettings.setOnClickListener { showPageSettings() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra(Extras.DOC_ID)
        if (id != null && id != docId) {
            docId = id
            adapter = PageAdapter(docId)
            b.pager.adapter = adapter
        }
    }

    override fun onResume() {
        super.onResume()
        reload(false)
    }

    private fun reload(goLast: Boolean) {
        val doc = ScanRepository.getDoc(docId)
        if (doc == null || doc.pages.isEmpty()) {
            finish()
            return
        }
        val cur = b.pager.currentItem
        adapter.submit(doc.pages.toList())
        b.docName.text = doc.name
        val target = if (goLast) doc.pages.size - 1 else cur.coerceIn(0, doc.pages.size - 1)
        b.pager.setCurrentItem(target, false)
        updateIndicator()
    }

    private fun updateIndicator() {
        val doc = ScanRepository.getDoc(docId) ?: return
        b.pageIndicator.text = getString(R.string.page_indicator, b.pager.currentItem + 1, doc.pages.size)
    }

    private fun renameDoc() {
        val doc = ScanRepository.getDoc(docId) ?: return
        showInputDialog(R.string.rename, R.string.doc_name_hint, doc.name) { n ->
            doc.name = n
            ScanRepository.touch(doc)
            b.docName.text = n
        }
    }

    private fun showAddPage() {
        val items = arrayOf(getString(R.string.add_from_camera), getString(R.string.gallery))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_page)
            .setItems(items) { _, which ->
                if (which == 0) {
                    startActivity(Intent(this, CameraActivity::class.java).putExtra(Extras.DOC_ID, docId))
                } else {
                    pickImages.launch("image/*")
                }
            }
            .show()
    }

    private fun importUris(uris: List<Uri>) {
        val dlg = showProgress(R.string.processing)
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { ScanFlow.addImages(applicationContext, docId, uris, true) }
            dlg.dismiss()
            if (res == null) toast(R.string.error_generic) else reload(true)
        }
    }

    private fun showPageSettings() {
        val doc = ScanRepository.getDoc(docId) ?: return
        val sb = DialogPageSettingsBinding.inflate(LayoutInflater.from(this))
        fun fill(group: ChipGroup, labels: List<String>, checked: Int): List<Chip> {
            val chips = ArrayList<Chip>()
            labels.forEachIndexed { i, text ->
                val chip = ItemFolderChipBinding.inflate(LayoutInflater.from(this), group, false).root as Chip
                chip.id = android.view.View.generateViewId()
                chip.text = text
                group.addView(chip)
                chips.add(chip)
                if (i == checked) chip.isChecked = true
            }
            return chips
        }
        val sizeLabels = Exporter.sizeNames.map { if (it == "Original") getString(R.string.size_original) else it }
        val sizeChips = fill(sb.cgSize, sizeLabels, Exporter.sizeNames.indexOf(doc.pageSize).coerceAtLeast(0))
        val orientChips = fill(
            sb.cgOrient,
            listOf(getString(R.string.crop_auto), getString(R.string.orient_portrait), getString(R.string.orient_landscape)),
            doc.orientation
        )
        val marginChips = fill(
            sb.cgMargin,
            listOf(getString(R.string.margin_none), getString(R.string.margin_small), getString(R.string.margin_normal)),
            doc.margin
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.page_settings)
            .setView(sb.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val si = sizeChips.indexOfFirst { it.isChecked }
                if (si >= 0) doc.pageSize = Exporter.sizeNames[si]
                val oi = orientChips.indexOfFirst { it.isChecked }
                if (oi >= 0) doc.orientation = oi
                val mi = marginChips.indexOfFirst { it.isChecked }
                if (mi >= 0) doc.margin = mi
                ScanRepository.touch(doc)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
