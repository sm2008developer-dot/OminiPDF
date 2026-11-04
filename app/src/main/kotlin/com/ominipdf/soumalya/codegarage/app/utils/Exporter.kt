package com.ominipdf.soumalya.codegarage.app.utils

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.ominipdf.soumalya.codegarage.app.data.ScanDoc
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object Exporter {

    fun exportDir(ctx: Context): File = File(ctx.cacheDir, "export").apply { mkdirs() }

    fun clearExports(ctx: Context) {
        try {
            File(ctx.cacheDir, "export").listFiles()?.forEach { it.deleteRecursively() }
        } catch (e: Exception) {
        }
    }

    fun safeName(n: String): String =
        n.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().ifEmpty { "OminiPDF" }

    private fun baseSize(name: String): Pair<Int, Int>? = when (name) {
        "A3" -> Pair(842, 1191)
        "A4" -> Pair(595, 842)
        "A5" -> Pair(420, 595)
        "Letter" -> Pair(612, 792)
        "Legal" -> Pair(612, 1008)
        else -> null
    }

    val sizeNames = listOf("A4", "A3", "A5", "Letter", "Legal", "Original")

    private fun marginPt(m: Int) = when (m) { 0 -> 0; 1 -> 18; else -> 36 }

    fun buildPdf(ctx: Context, doc: ScanDoc, out: File = File(exportDir(ctx), safeName(doc.name) + ".pdf")): File? {
        val pdf = PdfDocument()
        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            var count = 0
            for (p in doc.pages.toList()) {
                val bmp = ImageUtils.decodeSampled(ScanRepository.pageFile(doc.id, p.id), 2400) ?: continue
                var pw: Int
                var ph: Int
                val base = baseSize(doc.pageSize)
                if (base == null) {
                    val s = 842f / max(bmp.width, bmp.height)
                    pw = max(50, (bmp.width * s).toInt())
                    ph = max(50, (bmp.height * s).toInt())
                } else {
                    val landscape = when (doc.orientation) {
                        1 -> false
                        2 -> true
                        else -> bmp.width > bmp.height
                    }
                    pw = if (landscape) base.second else base.first
                    ph = if (landscape) base.first else base.second
                }
                count++
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, count).create())
                val c = page.canvas
                c.drawColor(Color.WHITE)
                val m = if (base == null) 0 else marginPt(doc.margin)
                val s = min((pw - 2 * m).toFloat() / bmp.width, (ph - 2 * m).toFloat() / bmp.height)
                val dw = bmp.width * s
                val dh = bmp.height * s
                c.drawBitmap(bmp, null, RectF((pw - dw) / 2f, (ph - dh) / 2f, (pw + dw) / 2f, (ph + dh) / 2f), paint)
                pdf.finishPage(page)
                bmp.recycle()
            }
            if (count == 0) return null
            FileOutputStream(out).use { pdf.writeTo(it) }
            return out
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            return null
        } finally {
            pdf.close()
        }
    }

    fun exportJpgs(ctx: Context, doc: ScanDoc): List<File> {
        val result = ArrayList<File>()
        val base = safeName(doc.name)
        doc.pages.toList().forEachIndexed { i, p ->
            val src = ScanRepository.pageFile(doc.id, p.id)
            if (!src.exists()) return@forEachIndexed
            val dst = File(exportDir(ctx), base + "_" + (i + 1) + ".jpg")
            try {
                src.copyTo(dst, true)
                result.add(dst)
            } catch (e: Exception) {
            }
        }
        return result
    }

    fun share(ctx: Context, files: List<File>, mime: String, title: String) {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri("", uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        intent.clipData = clip
        ctx.startActivity(Intent.createChooser(intent, title))
    }

    /** Saves [src] into public storage (Downloads/OminiPDF for PDFs, Pictures/OminiPDF for images). */
    @Suppress("DEPRECATION")
    fun saveToPublic(ctx: Context, src: File, displayName: String, mime: String, isImage: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (isImage) Environment.DIRECTORY_PICTURES + "/OminiPDF" else Environment.DIRECTORY_DOWNLOADS + "/OminiPDF"
                    )
                }
                val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = ctx.contentResolver.insert(collection, values) ?: return false
                ctx.contentResolver.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } } ?: return false
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS),
                    "OminiPDF"
                )
                dir.mkdirs()
                var dst = File(dir, displayName)
                var n = 1
                while (dst.exists()) {
                    val dot = displayName.lastIndexOf('.')
                    dst = File(dir, displayName.substring(0, dot) + "_" + n + displayName.substring(dot))
                    n++
                }
                src.copyTo(dst, true)
                MediaScannerConnection.scanFile(ctx, arrayOf(dst.path), arrayOf(mime), null)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
