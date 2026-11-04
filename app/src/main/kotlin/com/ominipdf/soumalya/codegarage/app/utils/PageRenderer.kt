package com.ominipdf.soumalya.codegarage.app.utils

import android.graphics.Bitmap
import com.ominipdf.soumalya.codegarage.app.data.PageItem
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository

object PageRenderer {
    /** Renders original -> rotate -> perspective crop -> filter into the page file. Blocking; call off the main thread. */
    fun render(docId: String, page: PageItem, maxSide: Int = 2200): Boolean {
        try {
            val orig = ImageUtils.decodeSampled(ScanRepository.origFile(docId, page.id), maxSide) ?: return false
            val rotated = ImageUtils.rotate(orig, page.rotation)
            val warped = ImageProcessor.warp(rotated, QuadUtils.order(page.quad), maxSide)
            if (rotated !== warped) rotated.recycle()
            val out = ImageProcessor.process(warped, page.filter, page.brightness, page.contrast, page.sharpness)
            val ok = ImageUtils.saveJpeg(out, ScanRepository.pageFile(docId, page.id), Prefs.jpegQuality)
            if (out !== warped) out.recycle()
            warped.recycle()
            return ok
        } catch (e: Exception) {
            return false
        } catch (e: OutOfMemoryError) {
            return false
        }
    }
}
