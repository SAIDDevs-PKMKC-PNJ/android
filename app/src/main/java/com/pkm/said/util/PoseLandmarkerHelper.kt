package com.pkm.said.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.YuvImage
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,

    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    private val TAG = "PoseLandmarkerHelper"
    private val pendingFrames = AtomicInteger(0)
    private val ALIGNMENT_TOLERANCE_PX = 5f

    private fun logWaistKneeLinearity(pixelLandmarks: List<PixelLandmark>) {
        if (pixelLandmarks.size < RIGHT_KNEE) {
            Log.d(TAG, "Not enough landmarks detected for linearity check.")
            return
        }

        val tolerance = ALIGNMENT_TOLERANCE_PX

        // --- LEFT LEG Y-AXIS CHECK ---
        val leftHipY = pixelLandmarks[LEFT_HIP].y
        val leftKneeY = pixelLandmarks[LEFT_KNEE].y

        // Calculate the difference for vertical linearity check
        val leftDiffY = Math.abs(leftHipY - leftKneeY)

        // The condition now logs if it's vertically linear OR if the knee is 'taller' (smaller Y-coordinate) than the hip.
        if (leftDiffY < tolerance) {
            // Log.i for a significant check result: vertically linear
            Log.i(TAG, "LEFT knee is **vertically linear** (HipY=${leftHipY.toInt()}, KneeY=${leftKneeY.toInt()}, Diff=${leftDiffY.toInt()}px)")
        } else if (leftKneeY < leftHipY) {
            // This logs if the knee is 'taller' than the hip.
            Log.i(TAG, "LEFT knee is **taller/higher** than hip! (HipY=${leftHipY.toInt()}, KneeY=${leftKneeY.toInt()}, Diff=${leftDiffY.toInt()}px)")
        } else {
            // Default non-linear, non-taller result (knee is correctly lower than hip, but not perfectly linear)
            Log.d(TAG, "Left knee is lower than hip (Correct) but not perfectly linear. (Diff=${leftDiffY.toInt()}px)")
        }


        // --- RIGHT LEG Y-AXIS CHECK ---
        val rightHipY = pixelLandmarks[RIGHT_HIP].y
        val rightKneeY = pixelLandmarks[RIGHT_KNEE].y

        // Calculate the difference for vertical linearity check
        val rightDiffY = Math.abs(rightHipY - rightKneeY)

        // The condition now logs if it's vertically linear OR if the knee is 'taller' (smaller Y-coordinate) than the hip.
        if (rightDiffY < tolerance) {
            // Use Log.i for a significant check result: vertically linear
            Log.i(TAG, "RIGHT knee is **vertically linear** (HipY=${rightHipY.toInt()}, KneeY=${rightKneeY.toInt()}, Diff=${rightDiffY.toInt()}px)")
        } else if (rightKneeY < rightHipY) {
            // This logs if the knee is 'taller' than the hip.
            Log.i(TAG, "RIGHT knee is **taller/higher** than hip! (HipY=${rightHipY.toInt()}, KneeY=${rightKneeY.toInt()}, Diff=${rightDiffY.toInt()}px)")
        }
        else {
            // Default non-linear, non-taller result (knee is correctly lower than hip, but not perfectly linear)
            Log.d(TAG, "Right knee is lower than hip (Correct) but not perfectly linear. (Diff=${rightDiffY.toInt()}px)")
        }
    }
    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    fun setupPoseLandmarker() {
        // Set general pose landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName =
            when (currentModel) {
                MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
                MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
                MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
                else -> "pose_landmarker_full.task"
            }

        baseOptionBuilder.setModelAssetPath(modelName)

        // Check if runningMode is consistent with poseLandmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (poseLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "poseLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> {
                // no-op
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            // Create an option builder with base options and specific
            // options only use for Pose Landmarker.
            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker =
                PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    fun ImageProxy.toBitmap(): Bitmap? {
        try {
            Log.d(TAG, "🔄 Converting YUV_420_888 to Bitmap - ${width}x${height}")
            return convertYuvToRgbManual()
        } catch (e: Exception) {
            Log.e(TAG, "❌ YUV conversion failed: ${e.message}")
            return createFallbackBitmap(width, height)
        }
    }

    // ✅ Copy the exact same YUV conversion methods from your working Eye Test
    private fun ImageProxy.convertYuvToRgbManual(): Bitmap? {
        try {
            val planes = this.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            Log.d(TAG, "📊 YUV sizes - Y: $ySize, U: $uSize, V: $vSize")

            // Gabungkan ke NV21 format (YUV420SP)
            val nv21 = ByteArray(ySize + uSize + vSize)

            // Copy Y plane
            yBuffer.get(nv21, 0, ySize)

            // Copy V and U planes (NV21 format: Y + V + U)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            // Convert NV21 to JPEG, lalu ke Bitmap
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)

            val jpegBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

            Log.d(TAG, "✅ YUV to RGB successful: ${bitmap.width}x${bitmap.height}")
            return bitmap

        } catch (e: Exception) {
            Log.e(TAG, "❌ Manual YUV conversion failed: ${e.message}")
            return convertYuvToGrayscale()
        }
    }

    private fun ImageProxy.convertYuvToGrayscale(): Bitmap {
        Log.d(TAG, "⚫ Creating grayscale from Y plane")
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        yBuffer.rewind()

        for (i in 0 until width * height) {
            if (yBuffer.hasRemaining()) {
                val luminance = yBuffer.get().toInt() and 0xFF
                pixels[i] = Color.argb(255, luminance, luminance, luminance)
            } else {
                pixels[i] = Color.BLACK
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun createFallbackBitmap(width: Int, height: Int): Bitmap {
        Log.w(TAG, "🔄 Using fallback bitmap")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Gradient background
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.BLUE, Color.GREEN, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        return bitmap
    }

    // Convert the ImageProxy to MP Image and feed it to PoselandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        pendingFrames.incrementAndGet()

        Log.d(TAG, "📸 detectLiveStream: Started - Format ${imageProxy.format}")

        val frameTime = SystemClock.uptimeMillis()
        var originalBitmap: Bitmap? = null
        var transformedBitmap: Bitmap? = null
        var mpImage: MPImage? = null

        if (runningMode != RunningMode.LIVE_STREAM) {
            val error = "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM"
            Log.e(TAG, "❌ detectLiveStream: $error")
            imageProxy.close()
            return
        }

        try {
            // Convert ImageProxy to Bitmap
            originalBitmap = imageProxy.toBitmap()
            if (originalBitmap == null) {
                Log.e(TAG, "❌ Bitmap conversion failed")
                poseLandmarkerHelperListener?.onError("Bitmap conversion failed")
                return
            }

            Log.d(TAG, "✅ Original bitmap: ${originalBitmap.width}x${originalBitmap.height}")

            // Apply transformations
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, originalBitmap.width / 2f, originalBitmap.height / 2f)
                }
            }

            transformedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
            )

            // Convert to MPImage
            mpImage = BitmapImageBuilder(transformedBitmap).build()
            Log.d(TAG, "✅ MPImage created")

            // Call pose detection
            detectAsync(mpImage!!, frameTime) // ✅ mpImage akan di-manage oleh detectAsync
            mpImage = null // ✅ Set to null to prevent double-close in finally block

            Log.d(TAG, "🚀 detectAsync called successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ detectLiveStream error: ${e.message}", e)
            poseLandmarkerHelperListener?.onError("Pose detection failed: ${e.message}")
        } finally {
            Log.d(TAG, "🧹 Starting cleanup...")

            // Cleanup order matters!
            try {
                mpImage?.close() // ✅ Close MPImage if it wasn't passed to detectAsync
            } catch (e: Exception) {
                Log.e(TAG, "Error closing MPImage in finally", e)
            }

            originalBitmap?.recycle()
            if (transformedBitmap != null && transformedBitmap != originalBitmap) {
                transformedBitmap.recycle()
            }

            imageProxy.close()

            val remainingFrames = pendingFrames.decrementAndGet()
            Log.d(TAG, "📊 Remaining pending frames: $remainingFrames")
        }
    }

    // Run pose landmark using MediaPipe Pose Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        Log.d(TAG, "🎯 detectAsync: Starting - Frame time: $frameTime")

        if (poseLandmarker == null) {
            Log.e(TAG, "❌ detectAsync: poseLandmarker is NULL!")
            mpImage.close() // ✅ CRITICAL FIX: Close MPImage when not used
            return
        }

        try {
            poseLandmarker?.detectAsync(mpImage, frameTime)
            Log.d(TAG, "✅ detectAsync: Detection queued successfully")
            // ✅ MPImage akan di-close di returnLivestreamResult

        } catch (e: Exception) {
            Log.e(TAG, "❌ detectAsync: Kotlin Exception - ${e.message}", e)

            // ✅ CRITICAL FIX: Close MPImage on error
            try {
                mpImage.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "Error closing MPImage in detectAsync", closeException)
            }

            poseLandmarkerHelperListener?.onError("Async detection failed: ${e.message}")
        }
    }

    private fun analyzeBalanceMetrics(pixelLandmarks: List<PixelLandmark>): BalanceMetrics {
        Log.d(TAG, "🔍 analyzeBalanceMetrics: Input landmarks count = ${pixelLandmarks.size}")

        if (pixelLandmarks.size < RIGHT_KNEE + 1) {
            Log.w(TAG, "❌ analyzeBalanceMetrics: Not enough landmarks detected!")
            return BalanceMetrics(0f, 0f, 0f, false, false, false)
        }

        val leftHip = pixelLandmarks[LEFT_HIP]
        val rightHip = pixelLandmarks[RIGHT_HIP]
        val leftKnee = pixelLandmarks[LEFT_KNEE]
        val rightKnee = pixelLandmarks[RIGHT_KNEE]

        // ✅ DEBUG: Log koordinat aktual
        Log.d(TAG, "🔍 Hip-Knee Coordinates:")
        Log.d(TAG, "🔍 Left - Hip: ${leftHip.y.toInt()}, Knee: ${leftKnee.y.toInt()}")
        Log.d(TAG, "🔍 Right - Hip: ${rightHip.y.toInt()}, Knee: ${rightKnee.y.toInt()}")

        // Hitung perbedaan Y-axis (dalam pixels)
        val leftDiffY = Math.abs(leftHip.y - leftKnee.y)
        val rightDiffY = Math.abs(rightHip.y - rightKnee.y)

        Log.d(TAG, "🔍 Y Differences - Left: ${leftDiffY.toInt()}px, Right: ${rightDiffY.toInt()}px")

        // ✅ PERBAIKAN: Tolerance yang lebih realistis untuk pose berdiri
        // Untuk gambar 1088x1088, perbedaan normal adalah 200-400 pixels
        val maxExpectedDiff = 500f // Maximum expected difference for standing pose
        val minExpectedDiff = 100f // Minimum expected difference for standing pose

        // Normalisasi stabilitas: 1.0 = ideal (200-300px), 0.0 = sangat tidak stabil
        val leftStability = calculateNormalizedStability(leftDiffY, minExpectedDiff, maxExpectedDiff)
        val rightStability = calculateNormalizedStability(rightDiffY, minExpectedDiff, maxExpectedDiff)
        val overallStability = (leftStability + rightStability) / 2f

        // ✅ PERBAIKAN: Deteksi ketidakseimbangan berdasarkan perbedaan antara kiri dan kanan
        val legBalanceDiff = Math.abs(leftDiffY - rightDiffY)
        val isLegBalanced = legBalanceDiff < 100f // Perbedaan antara kaki < 100px

        // Deteksi jika lutut lebih tinggi dari pinggul (ini abnormal)
        val leftKneeHigher = leftKnee.y < leftHip.y - 50f // Beri buffer 50px
        val rightKneeHigher = rightKnee.y < rightHip.y - 50f

        // ✅ PERBAIKAN: Kriteria keseimbangan yang lebih realistis
        val isBalanced = overallStability > 0.5f && isLegBalanced && !leftKneeHigher && !rightKneeHigher

        Log.d(TAG, "✅ analyzeBalanceMetrics - " +
                "Stability: ${String.format("%.2f", overallStability)}, " +
                "Balanced: $isBalanced, " +
                "Leg Balance Diff: ${legBalanceDiff.toInt()}px")

        return BalanceMetrics(
            leftLegStability = leftStability,
            rightLegStability = rightStability,
            overallStability = overallStability,
            isBalanced = isBalanced,
            leftKneeHigher = leftKneeHigher,
            rightKneeHigher = rightKneeHigher
        )
    }

    // ✅ FUNGSI BARU: Normalisasi stabilitas berdasarkan range yang expected
    private fun calculateNormalizedStability(diffY: Float, minExpected: Float, maxExpected: Float): Float {
        // Ideal range: 150-350 pixels (bisa disesuaikan)
        val idealMin = 150f
        val idealMax = 350f

        return when {
            diffY < idealMin -> {
                // Terlalu dekat (mungkin pose tidak normal)
                1f - (idealMin - diffY) / idealMin
            }
            diffY > idealMax -> {
                // Terlalu jauh (mungkin pose tidak normal)
                1f - (diffY - idealMax) / (maxExpected - idealMax)
            }
            else -> {
                // Dalam range ideal
                1f
            }
        }.coerceIn(0f, 1f)
    }

    // Accepts the URI for a video file loaded from the user's gallery and attempts to run
    // pose landmarker inference on the video. This process will evaluate every
    // frame in the video and attach the results to a bundle that will be
    // returned.
    fun detectVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile" +
                        " while not using RunningMode.VIDEO"
            )
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        // Load frames from the video and run the pose landmarker.
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()

        // Note: We need to read width/height from frame instead of getting the width/height
        // of the video directly because MediaRetriever returns frames that are smaller than the
        // actual dimension of the video file.
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        // If the video is invalid, returns a null detection result
        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        // Next, we'll get one frame every frameInterval ms, then run detection on these frames.
        val resultList = mutableListOf<PoseLandmarkerResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            var mpImage: MPImage? = null
            var argb8888Frame: Bitmap? = null

            try {
                val timestampMs = i * inferenceIntervalMs

                retriever
                    .getFrameAtTime(
                        timestampMs * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    ?.let { frame ->
                        // Convert the video frame to ARGB_8888
                        argb8888Frame = if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)

                        // Convert to MPImage
                        mpImage = BitmapImageBuilder(argb8888Frame!!).build()

                        // Run detection
                        poseLandmarker?.detectForVideo(mpImage!!, timestampMs)
                            ?.let { detectionResult ->
                                resultList.add(detectionResult)
                            } ?: run {
                            didErrorOccurred = true
                            poseLandmarkerHelperListener?.onError(
                                "ResultBundle could not be returned in detectVideoFile"
                            )
                        }
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing video frame at $i", e)
                didErrorOccurred = true
            } finally {
                // ✅ Cleanup resources for each frame
                try {
                    mpImage?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing MPImage in video processing", e)
                }

                // Only recycle if we created a copy
                if (argb8888Frame != null && argb8888Frame?.config == Bitmap.Config.ARGB_8888) {
                    argb8888Frame?.recycle()
                }
            }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            if (numberOfFrameToRead > 0) (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead) else 0L

        // NEW: For VIDEO mode, we don't calculate pixelLandmarks here as it's done per frame in GalleryFragment.
        // We ensure we pass an empty list and the default connections.

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(
                resultList,
                inferenceTimePerFrameMs,
                height,
                width,
                pixelLandmarks = emptyList(), // Keep empty as it's calculated per frame in fragment
                connections = PoseLandmarkConnections // Use default connections
            )
        }
    }

    // Accepted a Bitmap and runs pose landmarker inference on it to return
    // results back to the caller
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" +
                        " while not using RunningMode.IMAGE"
            )
        }


        // Inference time is the difference between the system time at the
        // start and finish of the process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run pose landmarker using MediaPipe Pose Landmarker API
        poseLandmarker?.detect(mpImage)?.also { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime

            // NEW: Extract PixelLandmarks and Connections for the single image result
            val pixelLandmarks = extractPixelLandmarks(landmarkResult, image.width, image.height)
            val connections = extractConnections(landmarkResult)

            return ResultBundle(
                listOf(landmarkResult),
                inferenceTimeMs,
                image.height,
                image.width,
                pixelLandmarks,
                connections
            )
        }

        // If poseLandmarker?.detect() returns null, this is likely an error. Returning null
        // to indicate this.
        poseLandmarkerHelperListener?.onError(
            "Pose Landmarker failed to detect."
        )
        return null
    }

    private fun extractPixelLandmarks(result: PoseLandmarkerResult, width: Int, height: Int): List<PixelLandmark> {
        val pixelLandmarks = mutableListOf<PixelLandmark>()

        try {
            Log.d(TAG, "🔍 extractPixelLandmarks: Processing ${result.landmarks().size} poses")

            if (result.landmarks().isEmpty()) {
                Log.w(TAG, "❌ extractPixelLandmarks: No poses detected in result")
                return emptyList()
            }

            val poseLandmarks = result.landmarks()[0]
            Log.d(TAG, "🔍 extractPixelLandmarks: First pose has ${poseLandmarks.size} landmarks")

            var visibleLandmarks = 0

            for ((index, landmark) in poseLandmarks.withIndex()) {
                try {
                    // ✅ PERBAIKAN: Handle Optional<Float> dengan benar
                    val visibility = landmark.visibility()
                    val visibilityValue = visibility.orElse(0.0f)

                    // ✅ PERBAIKAN: Log tanpa String.format untuk Optional
                    if (index in listOf(23, 24, 25, 26)) {
                        Log.d(TAG, "🔍 Key Landmark $index - " +
                                "Vis: $visibilityValue, " +  // ✅ LANGSUNG pakai value, tanpa format
                                "Pos: (${landmark.x()}, ${landmark.y()})")
                    }

                    // ✅ INCLUDE SEMUA LANDMARK untuk testing
                    val pixelX = landmark.x() * width
                    val pixelY = landmark.y() * height

                    pixelLandmarks.add(
                        PixelLandmark(
                            type = index,
                            x = pixelX,
                            y = pixelY
                        )
                    )
                    visibleLandmarks++

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing landmark $index: ${e.message}")
                }
            }

            Log.d(TAG, "✅ extractPixelLandmarks: $visibleLandmarks/${poseLandmarks.size} landmarks")

        } catch (e: Exception) {
            Log.e(TAG, "❌ extractPixelLandmarks error: ${e.message}", e)
        }

        return pixelLandmarks
    }

    private fun extractConnections(result: PoseLandmarkerResult): List<Pair<Int, Int>> {
        return PoseLandmarkConnections
    }

    // In PoseLandmarkerHelper class, in returnLivestreamResult:
    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        try {
            val finishTimeMs = SystemClock.uptimeMillis()
            val inferenceTime = finishTimeMs - result.timestampMs()
            val width = input.width
            val height = input.height

            // ✅ DEBUG DETAIL: Log everything about the detection
            Log.d(TAG, "🎯 returnLivestreamResult: " +
                    "Poses: ${result.landmarks().size}, " +
                    "Image: ${width}x${height}, " +
                    "Inference: ${inferenceTime}ms")

            if (result.landmarks().isNotEmpty()) {
                val pose = result.landmarks()[0]
                Log.d(TAG, "🎯 First Pose Details:")
                Log.d(TAG, "🎯 - Total landmarks: ${pose.size}")

                // Log specific landmarks we need
                if (pose.size > 26) {
                    val landmarksToCheck = listOf(23, 24, 25, 26)
                    for (index in landmarksToCheck) {
                        val landmark = pose[index]
                        val visibility = landmark.visibility()
                        val visibilityValue = visibility.orElse(0.0f) // ✅ AMBIL VALUE NYA

                        // ✅ PERBAIKAN: Log tanpa String.format untuk Optional
                        Log.d(TAG, "🎯 Landmark $index - " +
                                "Visibility: $visibilityValue, " +  // ✅ LANGSUNG pakai value
                                "Position: (${landmark.x()}, ${landmark.y()})")
                    }
                }
            }

            val pixelLandmarks = extractPixelLandmarks(result, width, height)
            val connections = extractConnections(result)

            // ✅ PERBAIKAN: Jangan kirim metrics jika tidak ada landmarks
            val balanceMetrics = if (pixelLandmarks.size > RIGHT_KNEE) {
                analyzeBalanceMetrics(pixelLandmarks)
            } else {
                Log.w(TAG, "⚠️ Not enough landmarks for balance analysis")
                null
            }

            logWaistKneeLinearity(pixelLandmarks)

            poseLandmarkerHelperListener?.onResults(
                ResultBundle(
                    results = listOf(result),
                    inferenceTime = inferenceTime,
                    inputImageHeight = height,
                    inputImageWidth = width,
                    pixelLandmarks = pixelLandmarks,
                    connections = connections,
                    balanceMetrics = balanceMetrics // Bisa null sekarang
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ returnLivestreamResult error", e)
        } finally {
            try {
                input.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing MPImage", e)
            }
        }
    }
    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"
        data class PixelLandmark(
            val type: Int,
            val x: Float,
            val y: Float
        )

        val PoseLandmarkConnections = listOf(
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7), Pair(0, 4), Pair(4, 5),
            Pair(5, 6), Pair(6, 8), Pair(9, 10), Pair(11, 12), Pair(11, 13),
            Pair(13, 15), Pair(15, 17), Pair(17, 19), Pair(19, 15), Pair(15, 21),
            Pair(12, 14), Pair(14, 16), Pair(16, 18), Pair(18, 20), Pair(20, 16),
            Pair(16, 22), Pair(11, 23), Pair(12, 24), Pair(23, 24), Pair(23, 25),
            Pair(25, 27), Pair(27, 29), Pair(29, 31), Pair(31, 27), Pair(24, 26),
            Pair(26, 28), Pair(28, 30), Pair(30, 32), Pair(32, 28)
        )

        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_POSES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
    }

    data class BalanceMetrics(
        val leftLegStability: Float,
        val rightLegStability: Float,
        val overallStability: Float,
        val isBalanced: Boolean,
        val leftKneeHigher: Boolean,
        val rightKneeHigher: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val pixelLandmarks: List<PixelLandmark> = emptyList(),
        val connections: List<Pair<Int, Int>> = PoseLandmarkConnections,
        val balanceMetrics: BalanceMetrics? = null
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}