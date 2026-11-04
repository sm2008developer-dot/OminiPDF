package com.ominipdf.soumalya.codegarage.app.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class EdgeOverlayView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var quad: FloatArray? = null
    private var stable = false

    var showGrid = false
        set(v) { field = v; invalidate() }
    var guideRatio = 0.72f
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * density; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * density; color = 0x66FFFFFF }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val path = Path()

    fun setQuad(q: FloatArray?, stable: Boolean) {
        quad = q
        this.stable = stable
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (showGrid) {
            for (i in 1..2) {
                c.drawLine(w * i / 3f, 0f, w * i / 3f, h, gridPaint)
                c.drawLine(0f, h * i / 3f, w, h * i / 3f, gridPaint)
            }
        }
        val q = quad
        if (q != null) {
            path.reset()
            path.moveTo(q[0] * w, q[1] * h)
            for (i in 1..3) path.lineTo(q[2 * i] * w, q[2 * i + 1] * h)
            path.close()
            fill.color = if (stable) 0x55E91E63 else 0x33FFFFFF
            stroke.color = if (stable) 0xFFFF4F8A.toInt() else 0xFFFFFFFF.toInt()
            c.drawPath(path, fill)
            c.drawPath(path, stroke)
            for (i in 0..3) c.drawCircle(q[2 * i] * w, q[2 * i + 1] * h, 5f * density, dot)
        } else {
            var rw = w * 0.84f
            var rh = rw / guideRatio
            if (rh > h * 0.84f) {
                rh = h * 0.84f
                rw = rh * guideRatio
            }
            val l = (w - rw) / 2f
            val t = (h - rh) / 2f
            val r = l + rw
            val b = t + rh
            val len = min(rw, rh) * 0.12f
            stroke.color = 0xFFFFFFFF.toInt()
            path.reset()
            path.moveTo(l, t + len); path.lineTo(l, t); path.lineTo(l + len, t)
            path.moveTo(r - len, t); path.lineTo(r, t); path.lineTo(r, t + len)
            path.moveTo(r, b - len); path.lineTo(r, b); path.lineTo(r - len, b)
            path.moveTo(l + len, b); path.lineTo(l, b); path.lineTo(l, b - len)
            c.drawPath(path, stroke)
        }
    }
}
