package com.ominipdf.soumalya.codegarage.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageUtils {

    /** Decodes with power-of-two sampling so the larger side stays >= maxSide (or original if smaller). */
    fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        val o2 = BitmapFactory.Options().apply {
            inSampleSize = s
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, o2)
    }

    /** Decodes and scales so the larger side is at most maxSide. */
    fun decodeScaled(file: File, maxSide: Int): Bitmap? {
        val b = decodeSampled(file, maxSide) ?: return null
        return scaleToMax(b, maxSide)
    }

    fun scaleToMax(src: Bitmap, maxSide: Int): Bitmap {
        val m = max(src.width, src.height)
        if (m <= maxSide) return src
        val s = maxSide.toFloat() / m
        val out = Bitmap.createScaledBitmap(src, max(1, (src.width * s).toInt()), max(1, (src.height * s).toInt()), true)
        if (out !== src) src.recycle()
        return out
    }

    fun rotate(src: Bitmap, degrees: Int, recycleSrc: Boolean = true): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val m = Matrix()
        m.postRotate(d.toFloat())
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (recycleSrc && out !== src) src.recycle()
        return out
    }

    fun saveJpeg(bmp: Bitmap, file: File, quality: Int): Boolean {
        return try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Copies a Uri/File into [dest]: EXIF-upright, downscaled to maxSide, JPEG. */
    fun importToFile(ctx: Context, src: Any, dest: File, maxSide: Int = 2800): Boolean {
        var temp: File? = null
        try {
            val file: File = when (src) {
                is File -> src
                is Uri -> {
                    val t = File(ctx.cacheDir, "imp_" + System.nanoTime() + ".tmp")
                    ctx.contentResolver.openInputStream(src)?.use { input ->
                        FileOutputStream(t).use { out -> input.copyTo(out) }
                    } ?: return false
                    temp = t
                    t
                }
                else -> return false
            }
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                orientation = ExifInterface(file.path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } catch (e: Exception) {
            }
            var bmp = decodeSampled(file, maxSide) ?: return false
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                else -> {}
            }
            if (!m.isIdentity) {
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r !== bmp) bmp.recycle()
                bmp = r
            }
            bmp = scaleToMax(bmp, maxSide)
            val ok = saveJpeg(bmp, dest, 92)
            bmp.recycle()
            return ok
        } catch (e: Exception) {
            return false
        } catch (e: OutOfMemoryError) {
            return false
        } finally {
            temp?.delete()
        }
    }
}

object QuadUtils {
    /** Orders 4 normalized points as TL, TR, BR, BL. */
    fun order(q: FloatArray): FloatArray {
        var tl = 0; var br = 0; var tr = 0; var bl = 0
        for (i in 0..3) {
            val s = q[2 * i] + q[2 * i + 1]
            val d = q[2 * i] - q[2 * i + 1]
            if (s < q[2 * tl] + q[2 * tl + 1]) tl = i
            if (s > q[2 * br] + q[2 * br + 1]) br = i
            if (d > q[2 * tr] - q[2 * tr + 1]) tr = i
            if (d < q[2 * bl] - q[2 * bl + 1]) bl = i
        }
        if (setOf(tl, tr, br, bl).size < 4) return q.copyOf()
        val idx = intArrayOf(tl, tr, br, bl)
        val out = FloatArray(8)
        for (k in 0..3) {
            out[2 * k] = q[2 * idx[k]]
            out[2 * k + 1] = q[2 * idx[k] + 1]
        }
        return out
    }

    /** Rotates a normalized quad 90 degrees and keeps TL,TR,BR,BL order. */
    fun rotate(q: FloatArray, clockwise: Boolean): FloatArray {
        val p = Array(4) { i ->
            val x = q[2 * i]; val y = q[2 * i + 1]
            if (clockwise) floatArrayOf(1f - y, x) else floatArrayOf(y, 1f - x)
        }
        val order = if (clockwise) intArrayOf(3, 0, 1, 2) else intArrayOf(1, 2, 3, 0)
        val out = FloatArray(8)
        for (k in 0..3) {
            out[2 * k] = p[order[k]][0]
            out[2 * k + 1] = p[order[k]][1]
        }
        return out
    }
}
