package com.ominipdf.soumalya.codegarage.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.FilterType
import com.ominipdf.soumalya.codegarage.app.data.PageItem
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository

object SampleFactory {
    fun create(ctx: Context) {
        val doc = ScanRepository.createDoc(ctx.getString(R.string.sample_doc_name))
        val pageId = ScanRepository.newId()
        val bmp = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFE91E63.toInt()
        c.drawRect(0f, 0f, 1240f, 260f, p)
        p.color = Color.WHITE
        p.textSize = 62f
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        c.drawText(ctx.getString(R.string.sample_title), 620f, 158f, p)
        val lines = listOf(R.string.sample_line1, R.string.sample_line2, R.string.sample_line3)
        var y = 480f
        for (l in lines) {
            p.color = 0xFFE91E63.toInt()
            c.drawCircle(220f, y - 18f, 16f, p)
            p.color = 0xFF3A2A30.toInt()
            p.isFakeBoldText = false
            p.textSize = 54f
            p.textAlign = Paint.Align.LEFT
            c.drawText(ctx.getString(l), 270f, y, p)
            y += 150f
        }
        p.color = 0xFFE6D5DA.toInt()
        var by = 1000f
        while (by < 1600f) {
            c.drawRoundRect(170f, by, 1070f, by + 22f, 11f, 11f, p)
            by += 70f
        }
        val ok = ImageUtils.saveJpeg(bmp, ScanRepository.origFile(doc.id, pageId), 92)
        bmp.recycle()
        if (!ok) {
            ScanRepository.deleteDocNow(doc.id)
            return
        }
        val page = PageItem(pageId)
        page.filter = FilterType.ORIGINAL
        if (PageRenderer.render(doc.id, page)) {
            ScanRepository.addPage(doc.id, page)
        } else {
            ScanRepository.deleteDocNow(doc.id)
        }
    }
}
