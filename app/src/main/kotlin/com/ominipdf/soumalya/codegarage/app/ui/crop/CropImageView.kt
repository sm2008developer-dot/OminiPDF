package com.ominipdf.soumalya.codegarage.app.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.ominipdf.soumalya.codegarage.app.R
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class CropImageView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var bitmap: Bitmap? = null
    private val imgRect = RectF()
    private var quad = floatArrayOf(0.05f, 0.05f, 0.95f, 0.05f, 0.95f, 0.95f, 0.05f, 0.95f)

    private val d = resources.displayMetrics.density
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint().apply { color = ContextCompat.getColor(ctx, R.color.crop_dim); style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(ctx, R.color.pink_primary); style = Paint.Style.STROKE; strokeWidth = 2.5f * d; strokeJoin = Paint.Join.ROUND
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(ctx, R.color.pink_primary); style = Paint.Style.STROKE; strokeWidth = 3f * d
    }
    private val dimPath = Path()
    private val polyPath = Path()
    private val loupePath = Path()

    private var active = -1   // 0..3 corners, 4..7 sides (top,right,bottom,left)
    private var lastX = 0f
    private var lastY = 0f

    fun setBitmap(b: Bitmap) {
        bitmap = b
        layoutImage()
        invalidate()
    }

    fun getQuad(): FloatArray = quad.copyOf()

    fun setQuad(q: FloatArray) {
        quad = q.copyOf()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutImage()
    }

    private fun layoutImage() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        val pad = 24f * d
        val s = min((width - 2 * pad) / b.width, (height - 2 * pad) / b.height)
        val dw = b.width * s
        val dh = b.height * s
        imgRect.set((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
    }

    private fun vx(i: Int) = imgRect.left + quad[2 * i] * imgRect.width()
    private fun vy(i: Int) = imgRect.top + quad[2 * i + 1] * imgRect.height()
    private fun sideX(s: Int) = (vx(s) + vx((s + 1) % 4)) / 2f
    private fun sideY(s: Int) = (vy(s) + vy((s + 1) % 4)) / 2f

    override fun onDraw(c: Canvas) {
        val b = bitmap ?: return
        c.drawBitmap(b, null, imgRect, bmpPaint)
        dimPath.reset()
        dimPath.fillType = Path.FillType.EVEN_ODD
        dimPath.addRect(imgRect, Path.Direction.CW)
        polyPath.reset()
        polyPath.moveTo(vx(0), vy(0))
        for (i in 1..3) polyPath.lineTo(vx(i), vy(i))
        polyPath.close()
        dimPath.addPath(polyPath)
        c.drawPath(dimPath, dimPaint)
        c.drawPath(polyPath, linePaint)
        for (i in 0..3) {
            c.drawCircle(vx(i), vy(i), 11f * d, handleFill)
            c.drawCircle(vx(i), vy(i), 11f * d, handleStroke)
        }
        for (s in 0..3) {
            c.drawCircle(sideX(s), sideY(s), 7f * d, handleFill)
            c.drawCircle(sideX(s), sideY(s), 7f * d, handleStroke)
        }
        if (active >= 0) drawLoupe(c)
    }

    private fun drawLoupe(c: Canvas) {
        val px = if (active < 4) vx(active) else sideX(active - 4)
        val py = if (active < 4) vy(active) else sideY(active - 4)
        val r = 52f * d
        val cx = if (px < width / 2f) width - r - 14f * d else r + 14f * d
        val cy = r + 14f * d
        val z = 2.6f
        c.save()
        loupePath.reset()
        loupePath.addCircle(cx, cy, r, Path.Direction.CW)
        c.clipPath(loupePath)
        c.drawColor(0xFF000000.toInt())
        c.translate(cx, cy)
        c.scale(z, z)
        c.translate(-px, -py)
        bitmap?.let { c.drawBitmap(it, null, imgRect, bmpPaint) }
        c.restore()
        c.drawCircle(cx, cy, r, handleStroke)
        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); strokeWidth = 1.5f * d }
        c.drawLine(cx - 10f * d, cy, cx + 10f * d, cy, cross)
        c.drawLine(cx, cy - 10f * d, cx, cy + 10f * d, cross)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bitmap == null || imgRect.width() <= 0f) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = hit(e.x, e.y)
                lastX = e.x
                lastY = e.y
                if (active >= 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                }
                return active >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (active < 0) return false
                val dx = (e.x - lastX) / imgRect.width()
                val dy = (e.y - lastY) / imgRect.height()
                lastX = e.x
                lastY = e.y
                if (active < 4) {
                    quad[2 * active] = (quad[2 * active] + dx).coerceIn(0f, 1f)
                    quad[2 * active + 1] = (quad[2 * active + 1] + dy).coerceIn(0f, 1f)
                } else {
                    val s = active - 4
                    val a = s
                    val bb = (s + 1) % 4
                    for (i in intArrayOf(a, bb)) {
                        quad[2 * i] = (quad[2 * i] + dx).coerceIn(0f, 1f)
                        quad[2 * i + 1] = (quad[2 * i + 1] + dy).coerceIn(0f, 1f)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (active >= 0) {
                    active = -1
                    invalidate()
                    if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hit(x: Float, y: Float): Int {
        val r = 36f * d
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0..3) {
            val dist = hypot(x - vx(i), y - vy(i))
            if (dist < r && dist < bestD) { best = i; bestD = dist }
        }
        if (best >= 0) return best
        for (s in 0..3) {
            val dist = hypot(x - sideX(s), y - sideY(s))
            if (dist < r && dist < bestD) { best = 4 + s; bestD = dist }
        }
        return best
    }
}
