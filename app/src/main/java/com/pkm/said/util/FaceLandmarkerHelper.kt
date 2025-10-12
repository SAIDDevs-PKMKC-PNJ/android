package com.pkm.said.util

/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.ImageFormat
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import androidx.core.graphics.createBitmap
import java.util.concurrent.atomic.AtomicInteger

class FaceLandmarkerHelper(
    var minFaceDetectionConfidence: Float = DEFAULT_FACE_DETECTION_CONFIDENCE,
    var minFaceTrackingConfidence: Float = DEFAULT_FACE_TRACKING_CONFIDENCE,
    var minFacePresenceConfidence: Float = DEFAULT_FACE_PRESENCE_CONFIDENCE,
    var maxNumFaces: Int = DEFAULT_NUM_FACES,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    private var pendingFrames: AtomicInteger = AtomicInteger(0),
    // this listener is only used when running in RunningMode.LIVE_STREAM
    val faceLandmarkerHelperListener: LandmarkerListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Face Landmarker will not change, a lazy val would be preferable.
    private var faceLandmarker: FaceLandmarker? = null

    init {
        Log.d(TAG, "🔄 FaceLandmarkerHelper initialization started")
        Log.d(TAG, "📊 Config - DetectionConf: $minFaceDetectionConfidence, TrackingConf: $minFaceTrackingConfidence, PresenceConf: $minFacePresenceConfidence")
        Log.d(TAG, "📊 Config - MaxFaces: $maxNumFaces, Delegate: $currentDelegate, RunningMode: $runningMode")
        setupFaceLandmarker()
    }

    fun clearFaceLandmarker() {
        Log.d(TAG, "🧹 clearFaceLandmarker: Cleaning up FaceLandmarker")
        faceLandmarker?.close()
        faceLandmarker = null
        Log.d(TAG, "✅ clearFaceLandmarker: Cleanup completed")
    }

    // Return running status of FaceLandmarkerHelper
    fun isClose(): Boolean {
        return faceLandmarker == null
    }

    // Initialize the Face landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupFaceLandmarker() {
        Log.d(TAG, "🚀 setupFaceLandmarker: Starting FaceLandmarker setup...")

        // Set general face landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
                Log.d(TAG, "🔧 setupFaceLandmarker: Using CPU delegate")
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
                Log.d(TAG, "🔧 setupFaceLandmarker: Using GPU delegate")
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_FACE_LANDMARKER_TASK)
        Log.d(TAG, "📁 setupFaceLandmarker: Model path set to: $MP_FACE_LANDMARKER_TASK")

        // Check if runningMode is consistent with faceLandmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (faceLandmarkerHelperListener == null) {
                    val error = "faceLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    Log.e(TAG, "❌ setupFaceLandmarker: $error")
                    throw IllegalStateException(error)
                } else {
                    Log.d(TAG, "✅ setupFaceLandmarker: LIVE_STREAM mode with valid listener")
                }
            }
            else -> {
                Log.d(TAG, "ℹ️ setupFaceLandmarker: Running mode: $runningMode")
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            Log.d(TAG, "✅ setupFaceLandmarker: Base options built successfully")

            // Create an option builder with base options and specific
            // options only use for Face Landmarker.
            val optionsBuilder =
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                    .setMinTrackingConfidence(minFaceTrackingConfidence)
                    .setMinFacePresenceConfidence(minFacePresenceConfidence)
                    .setNumFaces(maxNumFaces)
                    .setOutputFaceBlendshapes(true)
                    .setRunningMode(runningMode)

            Log.d(TAG, "🔧 setupFaceLandmarker: Options builder configured")

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
                Log.d(TAG, "🎯 setupFaceLandmarker: Live stream listeners set")
            }

            val options = optionsBuilder.build()
            Log.d(TAG, "🔧 setupFaceLandmarker: Creating FaceLandmarker instance...")

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.i(TAG, "🎉 setupFaceLandmarker: SUCCESS - FaceLandmarker created successfully!")

        } catch (e: IllegalStateException) {
            val errorMsg = "❌ setupFaceLandmarker: Face Landmarker failed to initialize - IllegalState"
            Log.e(TAG, errorMsg, e)
            faceLandmarkerHelperListener?.onError(
                "Face Landmarker failed to initialize. See error logs for details"
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            val errorMsg = "❌ setupFaceLandmarker: Face Landmarker failed to initialize - Runtime"
            Log.e(TAG, errorMsg, e)
            faceLandmarkerHelperListener?.onError(
                "Face Landmarker failed to initialize. See error logs for details",
                GPU_ERROR
            )
        } catch (e: Exception) {
            val errorMsg = "❌ setupFaceLandmarker: Unexpected error during setup"
            Log.e(TAG, errorMsg, e)
            faceLandmarkerHelperListener?.onError(
                "Unexpected error during FaceLandmarker setup: ${e.message}"
            )
        }
    }

    fun ImageProxy.toBitmap(): Bitmap? {
        try {
            Log.d("ImageProxy", "🔄 Converting YUV_420_888 to Bitmap - ${width}x${height}")

            return convertYuvToRgbManual()

        } catch (e: Exception) {
            Log.e("ImageProxy", "❌ YUV conversion failed: ${e.message}")
            return createFallbackBitmap(width, height)
        }
    }

    // ✅ Manual YUV to RGB conversion (REKOMENDASI)
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

            Log.d("ImageProxy", "📊 YUV sizes - Y: $ySize, U: $uSize, V: $vSize")

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

            Log.d("ImageProxy", "✅ YUV to RGB successful: ${bitmap.width}x${bitmap.height}")
            return bitmap

        } catch (e: Exception) {
            Log.e("ImageProxy", "❌ Manual YUV conversion failed: ${e.message}")
            return convertYuvToGrayscale() // Fallback ke grayscale
        }
    }

    // ✅ Fallback: Simple grayscale dari Y plane saja
    private fun ImageProxy.convertYuvToGrayscale(): Bitmap {
        Log.d("ImageProxy", "⚫ Creating grayscale from Y plane")

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

    // ✅ Fallback bitmap untuk testing
    private fun createFallbackBitmap(width: Int, height: Int): Bitmap {
        Log.w("ImageProxy", "🔄 Using fallback bitmap")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Buat gradient atau pattern untuk testing
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Gradient background
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.BLUE, Color.GREEN, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Debug text
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("FALLBACK BITMAP", width / 2f, height / 2f - 30f, textPaint)
        canvas.drawText("$width x $height", width / 2f, height / 2f, textPaint)
        canvas.drawText("YUV_420_888", width / 2f, height / 2f + 30f, textPaint)

        return bitmap
    }


    // Convert the ImageProxy to MP Image and feed it to FacelandmakerHelper.
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
            throw IllegalArgumentException(error)
        }

        try {
            // ✅ Convert ImageProxy to Bitmap
            originalBitmap = imageProxy.toBitmap()

            if (originalBitmap == null) {
                Log.e(TAG, "❌ Bitmap conversion failed")
                imageProxy.close()
                return
            }

            Log.d(TAG, "✅ Original bitmap: ${originalBitmap.width}x${originalBitmap.height}")

            // ✅ Apply transformations
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                Log.d(TAG, "🔄 Rotation: ${imageProxy.imageInfo.rotationDegrees}°")

                if (isFrontCamera) {
                    postScale(-1f, 1f, originalBitmap.width / 2f, originalBitmap.height / 2f)
                    Log.d(TAG, "🔄 Front camera flip applied")
                }
            }

            transformedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
            )

            Log.d(TAG, "✅ Transformed bitmap: ${transformedBitmap.width}x${transformedBitmap.height}")

            // ✅ Convert to MPImage
            mpImage = BitmapImageBuilder(transformedBitmap).build()
            Log.d(TAG, "✅ MPImage created")

            // ✅ Call detection
            detectAsync(mpImage, frameTime)
            Log.d(TAG, "🚀 detectAsync called successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ detectLiveStream error: ${e.message}", e)
        } finally {
            // ✅ CLEANUP YANG BENAR - SELALU DILAKUKAN
            Log.d(TAG, "🧹 Starting cleanup...")

            // Cleanup bitmaps
            originalBitmap?.recycle()
            if (transformedBitmap != null && transformedBitmap != originalBitmap) {
                transformedBitmap.recycle()
                Log.d(TAG, "🧹 Transformed bitmap recycled")
            }

            // ✅ ImageProxy HARUS selalu di-close
            imageProxy.close()
            Log.d(TAG, "🧹 ImageProxy closed")

            Log.d(TAG, "✅ Cleanup completed")
        }
    }

    // Run face face landmark using MediaPipe Face Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        Log.d(TAG, "🎯 detectAsync: Starting - Frame time: $frameTime")

        if (faceLandmarker == null) {
            Log.e(TAG, "❌ detectAsync: faceLandmarker is NULL!")
            return
        }

        try {

            // ✅ LOG FaceLandmarker STATUS
            Log.d(TAG, "🔍 detectAsync: FaceLandmarker instance: $faceLandmarker")
            Log.d(TAG, "🔍 detectAsync: About to call detectAsync...")

            // ⚠️ INI YANG BERHENTI - CRASH NATIVE
            faceLandmarker?.detectAsync(mpImage, frameTime)

            // ✅ JIKA BERHASIL, LOG INI AKAN MUNCUL
            Log.d(TAG, "✅ detectAsync: Detection queued successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ detectAsync: Kotlin Exception - ${e.message}", e)
            faceLandmarkerHelperListener?.onError("Async detection failed: ${e.message}")
        }
    }

    // Return the landmark result to this FaceLandmarkerHelper's caller
    private fun returnLivestreamResult(
        result: FaceLandmarkerResult,
        input: MPImage
    ) {
        Log.d(TAG, "📨 returnLivestreamResult: CALLED - Timestamp: ${result.timestampMs()}")
        Log.d(TAG, "📊 returnLivestreamResult: Face landmarks count: ${result.faceLandmarks().size}")

        if (result.faceLandmarks().isNotEmpty()) {
            val finishTimeMs = SystemClock.uptimeMillis()
            val inferenceTime = finishTimeMs - result.timestampMs()

            Log.d(TAG, "✅ returnLivestreamResult: Face DETECTED - ${result.faceLandmarks().size} faces")
            Log.d(TAG, "⏱️ returnLivestreamResult: Inference time: ${inferenceTime}ms")

            // Log details about the first face
            val firstFaceLandmarks = result.faceLandmarks().first()
            Log.d(TAG, "📍 returnLivestreamResult: First face has ${firstFaceLandmarks.size} landmarks")

            // Log blendshapes info if available
            result.faceBlendshapes()?.let { blendshapes ->
                if (blendshapes.isPresent && blendshapes.get()?.isNotEmpty() == true) {
                    val firstFaceBlendshapes = blendshapes.get()!!.first()
                    Log.d(TAG, "😀 returnLivestreamResult: First face has ${firstFaceBlendshapes.size} blendshapes")

                    // Log top 5 blendshapes for debugging
                    val topBlendshapes = firstFaceBlendshapes
                        .filterNotNull()
                        .sortedByDescending { it.score() }
                        .take(5)
                    Log.d(TAG, "🏆 returnLivestreamResult: Top 5 blendshapes:")
                    topBlendshapes.forEach { category ->
                        Log.d(TAG, "   ${category.categoryName()}: ${category.score()}")
                    }
                } else {
                    Log.d(TAG, "ℹ️ returnLivestreamResult: No blendshapes data available")
                }
            }

            faceLandmarkerHelperListener?.onResults(
                ResultBundle(
                    result,
                    inferenceTime,
                    input.height,
                    input.width
                )
            )
            Log.d(TAG, "✅ returnLivestreamResult: Listener notified with results")
        } else {
            Log.d(TAG, "❌ returnLivestreamResult: NO face landmarks detected")
            faceLandmarkerHelperListener?.onEmpty()
        }
    }

    // Return errors thrown during detection to this FaceLandmarkerHelper's caller
    private fun returnLivestreamError(error: RuntimeException) {
        Log.e(TAG, "💥 returnLivestreamError: CALLED - ${error.message}", error)
        faceLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    // Accepts the URI for a video file loaded from the user's gallery and attempts to run
    // face landmarker inference on the video. This process will evaluate every
    // frame in the video and attach the results to a bundle that will be
    // returned.
    fun detectVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): VideoResultBundle? {
        Log.d(TAG, "🎥 detectVideoFile: Starting video detection - URI: $videoUri, Interval: ${inferenceIntervalMs}ms")

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

        // Load frames from the video and run the face landmarker.
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
        if ((videoLengthMs == null) || (width == null) || (height == null)) {
            Log.e(TAG, "❌ detectVideoFile: Invalid video - Length: $videoLengthMs, Width: $width, Height: $height")
            return null
        }

        Log.d(TAG, "📹 detectVideoFile: Video info - Length: ${videoLengthMs}ms, Dimensions: ${width}x${height}")

        // Next, we'll get one frame every frameInterval ms, then run detection on these frames.
        val resultList = mutableListOf<FaceLandmarkerResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        Log.d(TAG, "🔄 detectVideoFile: Processing $numberOfFrameToRead frames...")

        for (i in 0..numberOfFrameToRead) {
            val timestampMs = i * inferenceIntervalMs // ms

            retriever
                .getFrameAtTime(
                    timestampMs * 1000, // convert from ms to micro-s
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                ?.let { frame ->
                    // Convert the video frame to ARGB_8888 which is required by the MediaPipe
                    val argb8888Frame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)

                    // Convert the input Bitmap object to an MPImage object to run inference
                    val mpImage = BitmapImageBuilder(argb8888Frame).build()

                    // Run face landmarker using MediaPipe Face Landmarker API
                    faceLandmarker?.detectForVideo(mpImage, timestampMs)
                        ?.let { detectionResult ->
                            resultList.add(detectionResult)
                            Log.v(TAG, "✅ detectVideoFile: Frame $i at ${timestampMs}ms - Faces: ${detectionResult.faceLandmarks().size}")
                        } ?: {
                        didErrorOccurred = true
                        Log.e(TAG, "❌ detectVideoFile: No result for frame $i at ${timestampMs}ms")
                        faceLandmarkerHelperListener?.onError(
                            "ResultBundle could not be returned" +
                                    " in detectVideoFile"
                        )
                    }
                }
                ?: run {
                    didErrorOccurred = true
                    Log.e(TAG, "❌ detectVideoFile: Could not retrieve frame $i at ${timestampMs}ms")
                    faceLandmarkerHelperListener?.onError(
                        "Frame at specified time could not be" +
                                " retrieved when detecting in video."
                    )
                }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        Log.d(TAG, "📊 detectVideoFile: Completed - Total frames processed: ${resultList.size}, Avg time per frame: ${inferenceTimePerFrameMs}ms")

        return if (didErrorOccurred) {
            Log.e(TAG, "❌ detectVideoFile: Errors occurred during processing")
            null
        } else {
            Log.i(TAG, "🎉 detectVideoFile: SUCCESS - Video processing completed")
            VideoResultBundle(resultList, inferenceTimePerFrameMs, height, width)
        }
    }

    // Accepted a Bitmap and runs face landmarker inference on it to return
    // results back to the caller
    fun detectImage(image: Bitmap): ResultBundle? {
        Log.d(TAG, "🖼️ detectImage: Starting image detection - Size: ${image.width}x${image.height}")

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

        // Run face landmarker using MediaPipe Face Landmarker API
        faceLandmarker?.detect(mpImage)?.also { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            Log.d(TAG, "✅ detectImage: Detection successful - Faces: ${landmarkResult.faceLandmarks().size}, Time: ${inferenceTimeMs}ms")
            return ResultBundle(
                landmarkResult,
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        // If faceLandmarker?.detect() returns null, this is likely an error. Returning null
        // to indicate this.
        Log.e(TAG, "❌ detectImage: Face Landmarker failed to detect any faces")
        faceLandmarkerHelperListener?.onError(
            "Face Landmarker failed to detect."
        )
        return null
    }

    companion object {
        const val TAG = "FaceLandmarkerHelper"
        private const val MP_FACE_LANDMARKER_TASK = "face_landmarker.task"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_FACE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_FACE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_FACE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_FACES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val result: FaceLandmarkerResult,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    data class VideoResultBundle(
        val results: List<FaceLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)

        fun onEmpty() {}
    }

    data class CapturedExpressionData(
        val blendshapeName: String,
        val score: Float,
        val featureCoordinates: List<NormalizedLandmark>,
        val timestamp: Long
    )
}