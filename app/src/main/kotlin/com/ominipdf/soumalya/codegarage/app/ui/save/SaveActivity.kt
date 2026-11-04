package com.ominipdf.soumalya.codegarage.app.ui.save

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.ScanDoc
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.databinding.ActivitySaveBinding
import com.ominipdf.soumalya.codegarage.app.ui.BaseActivity
import com.ominipdf.soumalya.codegarage.app.utils.Exporter
import com.ominipdf.soumalya.codegarage.app.utils.Extras
import com.ominipdf.soumalya.codegarage.app.utils.Prefs
import com.ominipdf.soumalya.codegarage.app.utils.ThumbLoader
import com.ominipdf.soumalya.codegarage.app.utils.haptic
import com.ominipdf.soumalya.codegarage.app.utils.showInputDialog
import com.ominipdf.soumalya.codegarage.app.utils.showProgress
import com.ominipdf.soumalya.codegarage.app.utils.toast

class SaveActivity : BaseActivity() {
    private lateinit var b: ActivitySaveBinding
    private var docId = ""
    private var pendingAction: (() -> Unit)? = null

    private val storagePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val a = pendingAction
        pendingAction = null
        if (granted) a?.invoke() else toast(R.string.storage_permission_msg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySaveBinding.inflate(layoutInflater)
        setContentView(b.root)
        docId = intent.getStringExtra(Extras.DOC_ID) ?: return finish()
        b.btnBack.setOnClickListener { finish() }
        b.saveName.setOnClickListener {
            val doc = ScanRepository.getDoc(docId) ?: return@setOnClickListener
            showInputDialog(R.string.rename, R.string.doc_name_hint, doc.name) { n ->
                doc.name = n
                ScanRepository.touch(doc)
                bindDoc()
            }
        }
        b.btnSharePdf.setOnClickListener { it.haptic(); sharePdf() }
        b.btnShareJpg.setOnClickListener { it.haptic(); shareJpg() }
        b.btnSavePdf.setOnClickListener { it.haptic(); withStorage { savePdf() } }
        b.btnSaveGallery.setOnClickListener { it.haptic(); withStorage { saveGallery() } }
        val soon = { _: android.view.View -> toast(R.string.coming_soon) }
        b.featText.setOnClickListener(soon)
        b.featErase.setOnClickListener(soon)
        b.featSign.setOnClickListener(soon)
        b.featWatermark.setOnClickListener(soon)
    }

    override fun onResume() {
        super.onResume()
        bindDoc()
    }

    private fun bindDoc() {
        val doc = ScanRepository.getDoc(docId)
        if (doc == null || doc.pages.isEmpty()) {
            finish()
            return
        }
        b.saveName.text = doc.name
        b.saveMeta.text = getString(R.string.pages_count, doc.pages.size)
        ThumbLoader.load(b.saveThumb, ScanRepository.pageFile(doc.id, doc.pages[0].id), 400)
    }

    private fun withStorage(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 29 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.storage_permission_msg)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                pendingAction = action
                storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sharePdf() {
        val doc = ScanRepository.getDoc(docId) ?: return
        val dlg = showProgress(R.string.preparing_pdf)
        lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) { Exporter.buildPdf(applicationContext, doc) }
            dlg.dismiss()
            if (f == null) toast(R.string.error_generic)
            else Exporter.share(this@SaveActivity, listOf(f), "application/pdf", getString(R.string.share_via))
        }
    }

    private fun shareJpg() {
        val doc = ScanRepository.getDoc(docId) ?: return
        val dlg = showProgress(R.string.processing)
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { Exporter.exportJpgs(applicationContext, doc) }
            dlg.dismiss()
            if (files.isEmpty()) toast(R.string.error_generic)
            else Exporter.share(this@SaveActivity, files, "image/jpeg", getString(R.string.share))
        }
    }

    private fun savePdf() {
        val doc = ScanRepository.getDoc(docId) ?: return
        val dlg = showProgress(R.string.preparing_pdf)
        lifecycleScope.launch {
            val name = Exporter.safeName(doc.name) + ".pdf"
            val ok = withContext(Dispatchers.IO) {
                val f = Exporter.buildPdf(applicationContext, doc)
                f != null && Exporter.saveToPublic(applicationContext, f, name, "application/pdf", false)
            }
            dlg.dismiss()
            if (ok) {
                toast(getString(R.string.saved_pdf_msg, name))
                maybeAskRating()
            } else {
                toast(R.string.save_failed)
            }
        }
    }

    private fun saveGallery() {
        val doc = ScanRepository.getDoc(docId) ?: return
        val dlg = showProgress(R.string.processing)
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                var n = 0
                for (f in Exporter.exportJpgs(applicationContext, doc)) {
                    if (Exporter.saveToPublic(applicationContext, f, f.name, "image/jpeg", true)) n++
                }
                n
            }
            dlg.dismiss()
            if (saved > 0) {
                toast(getString(R.string.saved_gallery_msg, saved))
                maybeAskRating()
            } else {
                toast(R.string.save_failed)
            }
        }
    }

    private fun maybeAskRating() {
        if (Prefs.ratingShown) return
        Prefs.ratingShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rate_title)
            .setMessage(R.string.rate_msg)
            .setPositiveButton("😍") { _, _ ->
                toast(R.string.rate_thanks)
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                    } catch (e2: Exception) {
                    }
                }
            }
            .setNegativeButton("😞") { _, _ -> toast(R.string.rate_thanks) }
            .show()
    }
}
