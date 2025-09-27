package com.pkm.said.screening

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

class FftWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---- KONFIGURASI ----
    private var capacity = 128                     // jumlah “kolom” wave
    private val strokeWidthPx = 6f
    private val topPaddingPx = 32f                 // headroom
    private var ampRange = 32767f                  // max amplitude MediaRecorder

    // ---- DATA ----
    private val amps = FloatArray(capacity)        // ring buffer amplitudo [0..1]
    private val points = Array(capacity) { GravityPoint() }

    // ---- GRAFIK ----
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPath = Path()
    private val strokePath = Path()
    private var waveGradient: LinearGradient? = null
    private var fillGradient: LinearGradient? = null

    // API lama: set batch amplitudes (opsional)
    fun setVoiceAmplitudes(list: List<Float>) {
        val n = minOf(list.size, capacity)
        // copy ke ujung kanan, sisanya nol di depan
        val start = capacity - n
        java.util.Arrays.fill(amps, 0f)
        for (i in 0 until n) {
            amps[start + i] = list[i].coerceIn(0f, 1f)
        }
        smoothPoints()
        invalidate()
    }

    // NEW: dipanggil setiap ~50–100ms dengan nilai maxAmplitude dari MediaRecorder
    fun pushAmplitude(rawMaxAmplitude: Int) {
        val v = (rawMaxAmplitude / ampRange).coerceIn(0f, 1f)
        // geser kiri 1 langkah (ring buffer sederhana)
        System.arraycopy(amps, 1, amps, 0, capacity - 1)
        amps[capacity - 1] = v
        smoothPoints()
        invalidate()
    }

    private fun smoothPoints() {
        // update gravity smoothing terhadap amps
        for (i in 0 until capacity) points[i].update(amps[i])
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        waveGradient = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(Color.parseColor("#42A5F5"), Color.parseColor("#AB47BC"), Color.parseColor("#26C6DA")),
            null, Shader.TileMode.CLAMP
        )
        // ARGB: AA RR GG BB
        fillGradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.parseColor("#1042A5F5"), Color.parseColor("#3042A5F5"), Color.parseColor("#0042A5F5")),
            null, Shader.TileMode.CLAMP
        )
        strokePaint.shader = waveGradient
        fillPaint.shader = fillGradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2f
        val dx = w / (capacity - 1)

        fillPath.reset()
        strokePath.reset()

        // Outline atas
        strokePath.moveTo(0f, mid - waveY(0, h))
        for (i in 1 until capacity) {
            val x = dx * i
            val y = mid - waveY(i, h)
            strokePath.lineTo(x, y)
        }

        // Area fill: dari bawah → garis atas → turun lagi
        fillPath.moveTo(0f, mid + waveY(0, h))             // mulai dari sisi bawah (mirror)
        for (i in 1 until capacity) {
            val x = dx * i
            val y = mid + waveY(i, h)
            fillPath.lineTo(x, y)
        }
        for (i in capacity - 1 downTo 0) {
            val x = dx * i
            val y = mid - waveY(i, h)
            fillPath.lineTo(x, y)
        }
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(strokePath, strokePaint)
    }

    private fun waveY(i: Int, canvasH: Float): Float {
        // tinggi maksimal setiap sisi
        val maxH = (canvasH / 2f) - topPaddingPx
        // “curve” sinus biar halus di kiri/kanan
        val phase = i * PI / (capacity / 4.0)
        val curve = (sin(phase).toFloat() * 0.3f + 0.7f)   // 0.4..1.0 (sedikit lebih rata)
        return points[i].value * maxH * curve
    }

    // --- smoothing model (spring-like) ---
    private class GravityPoint(
        private var gravity: Float = 0.85f,
        private var friction: Float = 0.92f
    ) {
        var value = 0f; private set
        private var vel = 0f
        fun update(target: Float) {
            val force = target - value
            vel += force * gravity
            vel *= friction
            value += vel
            if (value < 0f) value = 0f
        }
    }
}
