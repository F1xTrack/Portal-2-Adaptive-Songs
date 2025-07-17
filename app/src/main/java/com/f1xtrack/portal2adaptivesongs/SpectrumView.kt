package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

class SpectrumView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {
    private var amplitudes: FloatArray = FloatArray(64) { 0f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#FF9800") // portal_orange по умолчанию
    }
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val path = Path()
    private var gradient: LinearGradient? = null

    fun updateSpectrum(newAmplitudes: FloatArray) {
        amplitudes = newAmplitudes.copyOf()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val n = amplitudes.size
        val step = w / (n - 1)
        path.reset()
        // Мягкая кривая через точки (кубические Безье)
        var prevX = 0f
        var prevY = h - amplitudes[0] * h
        path.moveTo(prevX, prevY)
        for (i in 1 until n) {
            val x = i * step
            val y = h - amplitudes[i] * h
            val cpx = (prevX + x) / 2
            path.cubicTo(cpx, prevY, cpx, y, x, y)
            prevX = x
            prevY = y
        }
        // Градиент в стиле Portal (синий-оранжевый)
        gradient = LinearGradient(0f, 0f, w, h,
            intArrayOf(
                Color.parseColor("#00B4FF"), // portal_blue
                Color.parseColor("#FF9800")  // portal_orange
            ),
            null, Shader.TileMode.CLAMP)
        gradientPaint.shader = gradient
        canvas.drawPath(path, gradientPaint)
    }
} 