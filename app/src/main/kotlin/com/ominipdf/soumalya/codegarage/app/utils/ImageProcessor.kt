package com.ominipdf.soumalya.codegarage.app.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.ominipdf.soumalya.codegarage.app.data.FilterType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {

    /** Perspective-corrects the normalized [quad] (TL,TR,BR,BL) into a new upright bitmap. Always returns a new bitmap. */
    fun warp(src: Bitmap, quad: FloatArray, maxSide: Int): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val p = FloatArray(8) { if (it % 2 == 0) quad[it] * w else quad[it] * h }
        fun dist(a: Int, b: Int) = hypot(p[2 * a] - p[2 * b], p[2 * a + 1] - p[2 * b + 1])
        val dw = max(8f, max(dist(0, 1), dist(3, 2)))
        val dh = max(8f, max(dist(0, 3), dist(1, 2)))
        val s = min(1f, maxSide / max(dw, dh))
        val ow = max(1, (dw * s).toInt())
        val oh = max(1, (dh * s).toInt())
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val dst = floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(p, 0, dst, 0, 4)) m.setScale(ow / w, oh / h)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Applies filter + adjustments. May return [src] itself when nothing changes. */
    fun process(src: Bitmap, f: FilterType, brightness: Int, contrast: Int, sharpness: Int): Bitmap {
        var cur = src
        val filtered = applyFilter(cur, f)
        cur = filtered
        if (brightness != 0 || contrast != 0) {
            val adj = adjust(cur, brightness, contrast)
            if (cur !== src) cur.recycle()
            cur = adj
        }
        if (sharpness > 0) {
            val sh = sharpen(cur, sharpness / 100f * 0.9f)
            if (cur !== src) cur.recycle()
            cur = sh
        }
        return cur
    }

    private fun applyFilter(src: Bitmap, f: FilterType): Bitmap = when (f) {
        FilterType.ORIGINAL -> src
        FilterType.AUTO -> {
            val cm = autoLevels(src)
            cm.postConcat(contrastMatrix(1.08f, 0f))
            colorMatrix(src, cm)
        }
        FilterType.DOCUMENTS -> {
            val n = normalizeBackground(src, true)
            applyLut(n, lut(35, 235))
        }
        FilterType.BRIGHT -> {
            val cm = contrastMatrix(1.15f, 25f)
            val sat = ColorMatrix(); sat.setSaturation(1.1f)
            cm.postConcat(sat)
            colorMatrix(src, cm)
        }
        FilterType.NO_SHADOW -> normalizeBackground(src, false)
        FilterType.COLOR -> {
            val n = normalizeBackground(src, false)
            val cm = ColorMatrix(); cm.setSaturation(1.35f)
            cm.postConcat(contrastMatrix(1.08f, 0f))
            val out = colorMatrix(n, cm)
            n.recycle()
            out
        }
        FilterType.BW -> {
            val n = normalizeBackground(src, true)
            val l = IntArray(256) { v ->
                when {
                    v < 115 -> 0
                    v > 175 -> 255
                    else -> (v - 115) * 255 / 60
                }
            }
            applyLut(n, l)
        }
        FilterType.SUPER -> {
            val n = normalizeBackground(src, true)
            applyLut(n, lut(70, 215))
            val s = sharpen(n, 0.6f)
            n.recycle()
            s
        }
        FilterType.ENHANCE -> {
            val cm = autoLevels(src)
            val sat = ColorMatrix(); sat.setSaturation(1.15f)
            cm.postConcat(sat)
            val a = colorMatrix(src, cm)
            val s = sharpen(a, 0.35f)
            a.recycle()
            s
        }
    }

    private fun colorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun contrastMatrix(c: Float, offset: Float): ColorMatrix {
        val t = (1f - c) * 128f + offset
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun adjust(src: Bitmap, brightness: Int, contrast: Int): Bitmap {
        val c = 1f + contrast / 100f
        return colorMatrix(src, contrastMatrix(c, brightness * 0.8f))
    }

    private fun autoLevels(src: Bitmap): ColorMatrix {
        val sc = 160f / max(src.width, src.height)
        val sw = max(1, (src.width * sc).toInt())
        val sh = max(1, (src.height * sc).toInt())
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== src) small.recycle()
        val hist = IntArray(256)
        for (p in px) {
            val l = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
            hist[l]++
        }
        val total = px.size
        var acc = 0
        var lo = 0
        var hi = 255
        for (i in 0..255) { acc += hist[i]; if (acc >= total * 0.01f) { lo = i; break } }
        acc = 0
        for (i in 255 downTo 0) { acc += hist[i]; if (acc >= total * 0.01f) { hi = i; break } }
        val cm = ColorMatrix()
        if (hi - lo > 30) {
            val g = 255f / (hi - lo)
            val t = -lo * g
            cm.set(
                floatArrayOf(
                    g, 0f, 0f, 0f, t,
                    0f, g, 0f, 0f, t,
                    0f, 0f, g, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        return cm
    }

    private fun lut(black: Int, white: Int): IntArray =
        IntArray(256) { v -> ((v - black) * 255 / (white - black)).coerceIn(0, 255) }

    /** Applies the same LUT to all channels in place (bitmap must be mutable). */
    private fun applyLut(bmp: Bitmap, lut: IntArray): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            px[i] = (0xFF shl 24) or (lut[(p shr 16) and 0xFF] shl 16) or (lut[(p shr 8) and 0xFF] shl 8) or lut[p and 0xFF]
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Divides by an estimated background to remove shadows / uneven light. */
    private fun normalizeBackground(src: Bitmap, gray: Boolean): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val sw = max(4, w / 12)
        val sh = max(4, h / 12)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val sp = IntArray(sw * sh)
        small.getPixels(sp, 0, sw, 0, 0, sw, sh)
        if (small !== src) small.recycle()
        val ch = Array(3) { c -> IntArray(sw * sh) { i -> (sp[i] shr (16 - 8 * c)) and 0xFF } }
        for (c in 0..2) ch[c] = boxBlur(dilate(ch[c], sw, sh, 3), sw, sh, 3)
        val bgS = IntArray(sw * sh) { i -> (0xFF shl 24) or (ch[0][i] shl 16) or (ch[1][i] shl 8) or ch[2][i] }
        val bgBmp = Bitmap.createBitmap(bgS, sw, sh, Bitmap.Config.ARGB_8888)
        val big = Bitmap.createScaledBitmap(bgBmp, w, h, true)
        if (big !== bgBmp) bgBmp.recycle()
        val bp = IntArray(w * h)
        big.getPixels(bp, 0, w, 0, 0, w, h)
        big.recycle()
        for (i in 0 until w * h) {
            val p = px[i]
            val b = bp[i]
            val br = max(1, (b shr 16) and 0xFF)
            val bg = max(1, (b shr 8) and 0xFF)
            val bb = max(1, b and 0xFF)
            var r = min(255, ((p shr 16) and 0xFF) * 255 / br)
            var g = min(255, ((p shr 8) and 0xFF) * 255 / bg)
            var bl = min(255, (p and 0xFF) * 255 / bb)
            if (gray) {
                val l = (r * 77 + g * 150 + bl * 29) shr 8
                r = l; g = l; bl = l
            }
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun dilate(a: IntArray, w: Int, h: Int, r: Int): IntArray {
        val t = IntArray(w * h)
        val o = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (k in -r..r) {
                val xx = x + k
                if (xx in 0 until w) { val v = a[y * w + xx]; if (v > m) m = v }
            }
            t[y * w + x] = m
        }
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (k in -r..r) {
                val yy = y + k
                if (yy in 0 until h) { val v = t[yy * w + x]; if (v > m) m = v }
            }
            o[y * w + x] = m
        }
        return o
    }

    private fun boxBlur(a: IntArray, w: Int, h: Int, r: Int): IntArray {
        val t = IntArray(w * h)
        val o = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            var n = 0
            for (k in -r..r) {
                val xx = x + k
                if (xx in 0 until w) { s += a[y * w + xx]; n++ }
            }
            t[y * w + x] = s / n
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            var n = 0
            for (k in -r..r) {
                val yy = y + k
                if (yy in 0 until h) { s += t[yy * w + x]; n++ }
            }
            o[y * w + x] = s / n
        }
        return o
    }

    private fun sharpen(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        val shifts = intArrayOf(16, 8, 0)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val c = px[i]
                val l = px[i - 1]
                val r = px[i + 1]
                val u = px[i - w]
                val d = px[i + w]
                var res = 0xFF shl 24
                for (sh in shifts) {
                    val cv = (c shr sh) and 0xFF
                    val avg = (((l shr sh) and 0xFF) + ((r shr sh) and 0xFF) + ((u shr sh) and 0xFF) + ((d shr sh) and 0xFF)) / 4f
                    val v = (cv + k * 1.6f * (cv - avg)).toInt().coerceIn(0, 255)
                    res = res or (v shl sh)
                }
                out[i] = res
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }
}
