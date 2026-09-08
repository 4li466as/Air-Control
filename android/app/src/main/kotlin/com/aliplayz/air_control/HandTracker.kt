package com.aliplayz.air_control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.sqrt

data class HandTrackingData(
    val normX: Float,
    val normY: Float,
    val pinchDistance: Float
)

class HandTracker(
    private val context: Context,
    private val onHandData: (HandTrackingData) -> Unit,
    private val onHandLost: () -> Unit
) {
    private var handLandmarker: HandLandmarker? = null
    private var lastTimestampMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val THUMB_TIP = 4
        private const val INDEX_TIP = 8
    }

    fun init() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.CPU)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM) // Use LIVE_STREAM for zero lag
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.4f)
                .setMinHandPresenceConfidence(0.4f)
                .setMinTrackingConfidence(0.4f)
                .setResultListener { result, _ ->
                    if (result != null) {
                        mainHandler.post { processResult(result) }
                    }
                }
                .setErrorListener { error ->
                    Log.e("HandTracker", "MediaPipe Error: ${error.message}")
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d("HandTracker", "HandLandmarker created in LIVE_STREAM mode")
        } catch (e: Throwable) {
            java.io.File(context.filesDir, "crash.txt").appendText("Init Error: ${e.stackTraceToString()}\n")
            Log.e("HandTracker", "Failed to initialize MediaPipe", e)
        }
    }

    fun processImageProxy(imageProxy: ImageProxy) {
        if (handLandmarker == null) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            val currentTimeMs = System.currentTimeMillis()
            val safeTimestampMs = if (currentTimeMs > lastTimestampMs) currentTimeMs else lastTimestampMs + 1
            lastTimestampMs = safeTimestampMs

            handLandmarker?.detectAsync(mpImage, safeTimestampMs)
        } catch (e: Throwable) {
            Log.e("HandTracker", "Image processing failed", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun processResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            onHandLost()
            return
        }

        val landmarks = result.landmarks()[0]
        val normX = landmarks[INDEX_TIP].x()
        val normY = landmarks[INDEX_TIP].y()

        val dx = landmarks[THUMB_TIP].x() - landmarks[INDEX_TIP].x()
        val dy = landmarks[THUMB_TIP].y() - landmarks[INDEX_TIP].y()
        val pinchDistance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        onHandData(HandTrackingData(normX, normY, pinchDistance))
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }
}
