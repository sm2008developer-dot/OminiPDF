package com.ominipdf.soumalya.codegarage.app.utils

import android.content.Context
import android.content.Intent
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.FilterType
import com.ominipdf.soumalya.codegarage.app.data.PageItem
import com.ominipdf.soumalya.codegarage.app.data.ScanDoc
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.ui.crop.CropActivity
import com.ominipdf.soumalya.codegarage.app.ui.doc.DocumentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Extras {
    const val DOC_ID = "doc_id"
    const val PAGE_ID = "page_id"
    const val FROM_EDIT = "from_edit"
    const val MODE = "scan_mode"
}

enum class ScanMode { DOCUMENT, OCR, ID_CARD, PASSPORT }

object ScanFlow {

    fun defaultName(ctx: Context): String =
        ctx.getString(R.string.app_name) + " " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date())

    /**
     * Imports [sources] (Uri or File) as new pages. Creates a document when [docId] is null/unknown.
     * Blocking: call from a background thread. Returns null if nothing could be imported.
     */
    fun addImages(ctx: Context, docId: String?, sources: List<Any>, autoCrop: Boolean = true): Pair<ScanDoc, List<PageItem>>? {
        val existing = ScanRepository.getDoc(docId)
        val doc = existing ?: ScanRepository.createDoc(defaultName(ctx))
        val added = ArrayList<PageItem>()
        for (src in sources) {
            val pageId = ScanRepository.newId()
            val orig = ScanRepository.origFile(doc.id, pageId)
            if (!ImageUtils.importToFile(ctx, src, orig)) continue
            val page = PageItem(pageId)
            page.filter = FilterType.fromId(Prefs.defaultFilter)
            if (autoCrop) {
                val bmp = ImageUtils.decodeSampled(orig, 400)
                if (bmp != null) {
                    EdgeDetector.detectBitmap(bmp)?.let { page.quad = it }
                    bmp.recycle()
                }
            }
            if (PageRenderer.render(doc.id, page)) {
                ScanRepository.addPage(doc.id, page)
                added.add(page)
            } else {
                orig.delete()
            }
        }
        if (added.isEmpty()) {
            if (existing == null) ScanRepository.deleteDocNow(doc.id)
            return null
        }
        return Pair(doc, added)
    }

    fun openCrop(ctx: Context, docId: String, pageId: String, fromEdit: Boolean) {
        ctx.startActivity(
            Intent(ctx, CropActivity::class.java)
                .putExtra(Extras.DOC_ID, docId)
                .putExtra(Extras.PAGE_ID, pageId)
                .putExtra(Extras.FROM_EDIT, fromEdit)
        )
    }

    fun openDoc(ctx: Context, docId: String, clearTop: Boolean = false) {
        val i = Intent(ctx, DocumentActivity::class.java).putExtra(Extras.DOC_ID, docId)
        if (clearTop) i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        ctx.startActivity(i)
    }
}
