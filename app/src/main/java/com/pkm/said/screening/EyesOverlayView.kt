package com.pkm.said.screening

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Optional
import kotlin.math.max
import kotlin.math.min

class EyesOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var pupilLandmarks: List<List<NormalizedLandmark>> = emptyList()
    // The target type is List<List<Category>>
    private var blendshapesList: List<List<Category>> = emptyList()
    private var imageHeight: Int = 1
    private var imageWidth: Int = 1
    private var scaleFactor: Float = 1f

    private val pupilPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    fun clear() {
        pupilLandmarks = emptyList()
        blendshapesList = emptyList()
        invalidate()
    }

    fun setRawResults(
        // Passes the full list of landmarks as received from the results object
        results: List<List<NormalizedLandmark>>,
        // Passes the raw Optional object as received from the results object
        blendshapes: Optional<List<List<Category?>?>?>?,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        // Filter landmarks for pupil points: 468 (right eye) and 473 (left eye)
        val filtered = results.map { face ->
            face.withIndex()
                .filter { it.index == 468 || it.index == 473 }
                .map { it.value }
        }

        this.pupilLandmarks = filtered

        // FIX: Safely unwrap the Optional and handle nested nullability
        this.blendshapesList = blendshapes
            ?.orElse(null) // Unwrap the Optional, returning null if empty
            ?.filterNotNull() // Filter out any null elements at the top list level
            ?.map { innerList ->
                // Map each inner list, filtering out null Categories or defaulting to emptyList
                innerList?.filterNotNull() ?: emptyList()
            }
                // If the initial 'blendshapes' or the inner value was null, use emptyList()
            ?: emptyList()

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE, RunningMode.VIDEO -> min(width * 1f / imageWidth, height * 1f / imageHeight)
            RunningMode.LIVE_STREAM -> max(width * 1f / imageWidth, height * 1f / imageHeight)
        }

        invalidate()
    }

    private fun getBlendshapeScore(
        blendshapes: List<Category>,
        name: String
    ): Float {
        return blendshapes.find { it.categoryName() == name }?.score() ?: 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaledImageWidth = imageWidth * scaleFactor
        val scaledImageHeight = imageHeight * scaleFactor
        val offsetX = (width - scaledImageWidth) / 2f
        val offsetY = (height - scaledImageHeight) / 2f

        for ((faceIndex, face) in pupilLandmarks.withIndex()) {
            face.forEachIndexed { i, landmark ->
                val x = landmark.x() * imageWidth * scaleFactor + offsetX
                val y = landmark.y() * imageHeight * scaleFactor + offsetY

                canvas.drawCircle(x, y, 8f, pupilPaint)

                val label = if (i == 0) "Right" else "Left"
                val px = (landmark.x() * imageWidth).toInt()
                val py = (landmark.y() * imageHeight).toInt()
                canvas.drawText("$label($px, $py)", x + 12f, y - 12f, textPaint)
            }

            if (blendshapesList.size > faceIndex) {
                val blendshapes = blendshapesList[faceIndex]
                val blinkThreshold = 0.5f
                val lookThreshold = 0.3f
                val leftBlink = getBlendshapeScore(blendshapes, "eyeBlinkLeft") > blinkThreshold
                val rightBlink = getBlendshapeScore(blendshapes, "eyeBlinkRight") > blinkThreshold
                val lookLeft = getBlendshapeScore(blendshapes, "eyeLookLeft") > lookThreshold
                val lookRight = getBlendshapeScore(blendshapes, "eyeLookRight") > lookThreshold
                val lookUp = getBlendshapeScore(blendshapes, "eyeLookUp") > lookThreshold
                val lookDown = getBlendshapeScore(blendshapes, "eyeLookDown") > lookThreshold
                val blinkStatus = when {
                    leftBlink && rightBlink -> "Blinking"
                    leftBlink -> "Left Eye Blink"
                    rightBlink -> "Right Eye Blink"
                    else -> "No Blink"
                }
                val gazeStatus = when {
                    lookLeft -> "Looking Left"
                    lookRight -> "Looking Right"
                    lookUp -> "Looking Up"
                    lookDown -> "Looking Down"
                    else -> "Looking Center"
                }
                val baseX = offsetX + 20f
                val baseY = offsetY + scaledImageHeight - 80f + faceIndex * 80f
                canvas.drawText(blinkStatus, baseX, baseY, textPaint)
                canvas.drawText(gazeStatus, baseX, baseY + 40f, textPaint)
            }
        }
    }
}