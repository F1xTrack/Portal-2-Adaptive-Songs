package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class SimpleGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val points = FloatArray(MAX_POINTS)
    private var size = 0
    private var maxValue = 1f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00BCD4.toInt() // teal
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        strokeWidth = 2f
    }

    fun setColor(color: Int) {
        linePaint.color = color
        invalidate()
    }

    fun addValue(v: Float) {
        val clamped = if (v.isFinite()) v else 0f
        if (size < MAX_POINTS) {
            points[size++] = clamped
        } else {
            // shift left
            System.arraycopy(points, 1, points, 0, MAX_POINTS - 1)
            points[MAX_POINTS - 1] = clamped
        }
        maxValue = max(maxValue, clamped)
        invalidate()
    }

    fun clear() {
        size = 0
        maxValue = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        // axis
        canvas.drawLine(0f, h - 2f, w, h - 2f, axisPaint)
        if (size < 2) return
        val stepX = w / (MAX_POINTS - 1)
        var prevX = 0f
        var prevY = h - (points[0] / maxValue) * (h - 4f)
        for (i in 1 until size) {
            val x = i * stepX
            val y = h - (points[i] / maxValue) * (h - 4f)
            canvas.drawLine(prevX, prevY, x, y, linePaint)
            prevX = x
            prevY = y
        }
    }

    companion object {
        private const val MAX_POINTS = 200
    }
}
