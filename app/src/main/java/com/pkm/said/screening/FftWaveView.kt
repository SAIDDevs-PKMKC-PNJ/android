package com.pkm.said.screening

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class FftWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var amplitudes: List<Float> = List(128) { 0f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()
    private var waveColorGradient: LinearGradient? = null
    private var fillGradient: LinearGradient? = null

    // FFT Parameters
    private var num = 128
    private var ampR = 1f
    private var points = Array(0) { GravityPoint() }

    fun setVoiceAmplitudes(list: List<Float>) {
        // Convert to FFT-like data dan smooth dengan gravity
        amplitudes = list.map { amp ->
            val norm = amp / 10000f
            norm.coerceIn(0f, 1f)
        }

        // Update gravity points untuk smooth animation
        if (points.size != amplitudes.size) {
            points = Array(amplitudes.size) { GravityPoint() }
        }
        points.forEachIndexed { index, point ->
            point.update(amplitudes[index] * ampR)
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty()) return

        setupGradients(canvas)
        drawWave(canvas)
    }

    private fun setupGradients(canvas: Canvas) {
        if (waveColorGradient == null && width > 0) {
            waveColorGradient = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(
                    Color.parseColor("#42A5F5"),
                    Color.parseColor("#AB47BC"),
                    Color.parseColor("#26C6DA")
                ),
                null, Shader.TileMode.CLAMP
            )

            fillGradient = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.parseColor("#1042A5F5"),
                    Color.parseColor("#3042A5F5"),
                    Color.parseColor("#0042A5F5")
                ),
                null, Shader.TileMode.CLAMP
            )
        }
        paint.shader = waveColorGradient
        fillPaint.shader = fillGradient
    }

    private fun drawWave(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f
        val sliceWidth = w / (num - 1)

        path.reset()

        // Stroke path (outline wave)
        val strokePath = Path()
        strokePath.moveTo(0f, centerY - getWaveHeight(0, h))

        for (i in 1 until num) {
            val x = sliceWidth * i
            val y = centerY - getWaveHeight(i, h)
            strokePath.lineTo(x, y)
        }

        // Fill path (area under wave)
        path.moveTo(0f, h)
        path.lineTo(0f, centerY - getWaveHeight(0, h))

        for (i in 1 until num) {
            val x = sliceWidth * i
            val y = centerY - getWaveHeight(i, h)
            path.lineTo(x, y)
        }

        path.lineTo(w, h)
        path.close()

        // Draw fill first, then stroke
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(strokePath, paint)
    }

    private fun getWaveHeight(i: Int, canvasHeight: Float): Float {
        if (i >= points.size) return 0f

        // Smooth interpolated wave dengan sinus untuk natural curve
        val baseHeight = points[i].value * (canvasHeight / 2 - 32f)
        val phase = i * PI / (num / 4)
        val smoothFactor = sin(phase).toFloat() * 0.3f + 1f

        return baseHeight * smoothFactor
    }

    // Gravity model untuk smooth animation seperti di NextGenVisualizer
    inner class GravityPoint(
        private var gravity: Float = 0.85f,
        private var friction: Float = 0.92f
    ) {
        var value: Float = 0f
            private set
        private var velocity: Float = 0f

        fun update(target: Float) {
            val force = target - value
            velocity += force * gravity
            velocity *= friction
            value += velocity

            // Prevent negative values
            if (value < 0f) value = 0f
        }
    }
}