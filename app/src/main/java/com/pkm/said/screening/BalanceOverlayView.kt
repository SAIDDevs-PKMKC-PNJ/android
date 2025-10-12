package com.pkm.said.screening

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.pkm.said.R
import com.pkm.said.util.PoseLandmarkerHelper
import com.pkm.said.util.PoseLandmarkerHelper.Companion.PixelLandmark
import kotlin.math.max
import kotlin.math.min

class BalanceOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var pixelLandmarks: List<PixelLandmark>? = null
    private var connections: List<Pair<Int, Int>>? = null

    private val pointPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = LANDMARK_STROKE_WIDTH
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = ContextCompat.getColor(context ?: return@apply, R.color.lightBlue)
        strokeWidth = CONNECTION_LINE_WIDTH
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    // ✅ TAMBAHKAN: Offset untuk centering
    private var xOffset: Float = 0f
    private var yOffset: Float = 0f

    fun clear() {
        pixelLandmarks = null
        connections = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val landmarks = pixelLandmarks

        if (landmarks.isNullOrEmpty()) {
            val message = "Tunggu, mendeteksi pose..."
            val originalAlign = textPaint.textAlign
            textPaint.textAlign = Paint.Align.CENTER
            val x = width / 2f
            val y = height / 2f

            val backgroundPaint = Paint().apply {
                color = Color.parseColor("#80000000")
                style = Paint.Style.FILL
            }
            val textWidth = textPaint.measureText(message)
            canvas.drawRect(
                x - textWidth/2 - 20f,
                y - 40f,
                x + textWidth/2 + 20f,
                y + 20f,
                backgroundPaint
            )

            canvas.drawText(message, x, y, textPaint)
            textPaint.textAlign = originalAlign
            return
        }

        textPaint.textAlign = Paint.Align.LEFT

        // 1. Draw connections dengan OFFSET
        connections?.let {
            for (connection in it) {
                val start = landmarks.getOrNull(connection.first)
                val end = landmarks.getOrNull(connection.second)

                if (start != null && end != null) {
                    // ✅ PERBAIKAN: Tambahkan xOffset dan yOffset
                    val scaledStartX = start.x * scaleFactor + xOffset
                    val scaledStartY = start.y * scaleFactor + yOffset
                    val scaledEndX = end.x * scaleFactor + xOffset
                    val scaledEndY = end.y * scaleFactor + yOffset

                    canvas.drawLine(
                        scaledStartX, scaledStartY,
                        scaledEndX, scaledEndY,
                        linePaint
                    )
                }
            }
        }

        // 2. Draw only IMPORTANT landmark points dengan OFFSET
        for (landmark in landmarks) {
            if (landmark.type in listOf(23, 24, 25, 26)) {
                // ✅ PERBAIKAN: Tambahkan xOffset dan yOffset
                val scaledX = landmark.x * scaleFactor + xOffset
                val scaledY = landmark.y * scaleFactor + yOffset

                pointPaint.color = when (landmark.type) {
                    23, 24 -> Color.BLUE   // Hips
                    25, 26 -> Color.GREEN  // Knees
                    else -> Color.YELLOW
                }

                canvas.drawCircle(scaledX, scaledY, 12f, pointPaint)

                // ✅ OPSIONAL: Tambahkan label untuk clarity
                val label = when (landmark.type) {
                    23 -> "LH"
                    24 -> "RH"
                    25 -> "LK"
                    26 -> "RK"
                    else -> ""
                }
                canvas.drawText(label, scaledX - 15f, scaledY - 15f, textPaint)
            }
        }

        textPaint.textAlign = Paint.Align.CENTER
    }

    fun setResults(
        landmarks: List<PixelLandmark>,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE,
        connections: List<Pair<Int, Int>> = PoseLandmarkerHelper.PoseLandmarkConnections
    ) {
        this.pixelLandmarks = landmarks
        this.connections = connections
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        // ✅ PERBAIKAN: Hitung scale factor DAN offset untuk centering
        when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                // Fit Center - maintain aspect ratio dengan centering
                scaleFactor = min(width * 1f / imageWidth, height * 1f / imageHeight)
                xOffset = (width - imageWidth * scaleFactor) / 2f
                yOffset = (height - imageHeight * scaleFactor) / 2f
            }
            RunningMode.LIVE_STREAM -> {
                // Fill Center - crop tapi tetap center
                scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
                val scaledWidth = imageWidth * scaleFactor
                val scaledHeight = imageHeight * scaleFactor
                xOffset = (width - scaledWidth) / 2f
                yOffset = (height - scaledHeight) / 2f

                // Debug log
                Log.d("BalanceOverlay", "LIVE_STREAM - " +
                        "View: ${width}x${height}, " +
                        "Image: ${imageWidth}x${imageHeight}, " +
                        "Scale: $scaleFactor, " +
                        "Offset: ($xOffset, $yOffset)")
            }
        }

        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F
        private const val CONNECTION_LINE_WIDTH = 12F
    }
}