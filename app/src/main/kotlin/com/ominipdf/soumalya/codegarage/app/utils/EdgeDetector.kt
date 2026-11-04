package com.ominipdf.soumalya.codegarage.app.utils

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object EdgeDetector {

    /** Returns a normalized quad (TL,TR,BR,BL) or null when no clear page is found. */
    fun detectBitmap(src: Bitmap): FloatArray? {
        val s = 280f / max(src.width, src.height)
        val w = max(8, (src.width * s).toInt())
        val h = max(8, (src.height * s).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        val gray = IntArray(w * h) {
            val p = px[it]
            (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
        }
        return detect(gray, w, h)
    }

    fun detect(gray: IntArray, w: Int, h: Int): FloatArray? {
        val total = w * h
        val bl = IntArray(total)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            var n = 0
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    sum += gray[yy * w + xx]
                    n++
                }
            }
            bl[y * w + x] = sum / n
        }
        // Otsu threshold
        val hist = IntArray(256)
        for (v in bl) hist[v.coerceIn(0, 255)]++
        var sumAll = 0L
        for (i in 0..255) sumAll += i.toLong() * hist[i]
        var wB = 0
        var sumB = 0L
        var best = 0.0
        var thr = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sumAll - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = t }
        }
        val fg = BooleanArray(total) { bl[it] > thr }
        var border = 0
        var borderFg = 0
        for (x in 0 until w) {
            border += 2
            if (fg[x]) borderFg++
            if (fg[(h - 1) * w + x]) borderFg++
        }
        for (y in 0 until h) {
            border += 2
            if (fg[y * w]) borderFg++
            if (fg[y * w + w - 1]) borderFg++
        }
        if (borderFg.toFloat() / border > 0.5f) for (i in 0 until total) fg[i] = !fg[i]

        val label = IntArray(total)
        val queue = IntArray(total)
        var bestSize = 0
        var cur = 0
        var bestCorners: IntArray? = null
        for (start in 0 until total) {
            if (!fg[start] || label[start] != 0) continue
            cur++
            var head = 0
            var tail = 0
            queue[tail++] = start
            label[start] = cur
            var size = 0
            var minS = Int.MAX_VALUE; var maxS = Int.MIN_VALUE
            var minD = Int.MAX_VALUE; var maxD = Int.MIN_VALUE
            var tl = start; var br = start; var tr = start; var blc = start
            while (head < tail) {
                val p = queue[head++]
                size++
                val x = p % w
                val y = p / w
                val s = x + y
                val d = x - y
                if (s < minS) { minS = s; tl = p }
                if (s > maxS) { maxS = s; br = p }
                if (d > maxD) { maxD = d; tr = p }
                if (d < minD) { minD = d; blc = p }
                if (x > 0) { val q = p - 1; if (fg[q] && label[q] == 0) { label[q] = cur; queue[tail++] = q } }
                if (x < w - 1) { val q = p + 1; if (fg[q] && label[q] == 0) { label[q] = cur; queue[tail++] = q } }
                if (y > 0) { val q = p - w; if (fg[q] && label[q] == 0) { label[q] = cur; queue[tail++] = q } }
                if (y < h - 1) { val q = p + w; if (fg[q] && label[q] == 0) { label[q] = cur; queue[tail++] = q } }
            }
            if (size > bestSize) {
                bestSize = size
                bestCorners = intArrayOf(tl, tr, br, blc)
            }
        }
        val corners = bestCorners ?: return null
        if (bestSize < total * 0.12f || bestSize > total * 0.97f) return null
        val q = FloatArray(8)
        for (k in 0..3) {
            q[2 * k] = ((corners[k] % w) + 0.5f) / w
            q[2 * k + 1] = ((corners[k] / w) + 0.5f) / h
        }
        // shoelace area
        var area = 0f
        for (i in 0..3) {
            val j = (i + 1) % 4
            area += q[2 * i] * q[2 * j + 1] - q[2 * j] * q[2 * i + 1]
        }
        if (abs(area) / 2f < 0.10f) return null
        for (i in 0..3) {
            val j = (i + 1) % 4
            if (hypot(q[2 * i] - q[2 * j], q[2 * i + 1] - q[2 * j + 1]) < 0.1f) return null
        }
        return q
    }

    fun maxDiff(a: FloatArray, b: FloatArray): Float {
        var m = 0f
        for (i in 0 until min(a.size, b.size)) m = max(m, abs(a[i] - b[i]))
        return m
    }
}
