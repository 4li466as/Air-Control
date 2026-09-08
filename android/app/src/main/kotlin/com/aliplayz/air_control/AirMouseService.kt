package com.aliplayz.air_control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class AirMouseService : AccessibilityService() {

    companion object {
        var instance: AirMouseService? = null
            private set
    }

    private val lifecycleOwner = ServiceLifecycleOwner()
    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var handTracker: HandTracker? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var screenWidth = 0
    private var screenHeight = 0

    private var smoothedX = 0f
    private var smoothedY = 0f
    private val smoothingFactor = 0.5f

    private var isTracking = false
    private var isPinching = false
    private var pinchStartTime = 0L
    private var isPressActive = false
    private var pressStartX = 0f
    private var pressStartY = 0f

    private val dragMovementThresholdPx = 50f
    private val clickHoldThresholdMs = 800L
    private val pinchThreshold = 0.05f

    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var isDispatching = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        Log.d("AirMouseService", "onServiceConnected called")

        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        // ONLY add cursor view here. Do NOT initialize MediaPipe yet!
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            cursorView = CursorView(this)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            windowManager?.addView(cursorView, params)
            Log.d("AirMouseService", "Cursor overlay added successfully")
        } catch (e: Throwable) {
            Log.e("AirMouseService", "Cursor overlay failed: ${e.message}", e)
        }
    }

    fun startTracking() {
        if (isTracking) return
        isTracking = true
        Log.d("AirMouseService", "startTracking called")

        mainHandler.post {
            lifecycleOwner.start()

            // Initialize HandTracker ONLY when tracking starts
            if (handTracker == null) {
                try {
                    handTracker = HandTracker(
                        context = this,
                        onHandData = { data -> mainHandler.post { handleHandData(data) } },
                        onHandLost = { mainHandler.post { handleHandLost() } }
                    )
                    handTracker?.init()
                    Log.d("AirMouseService", "HandTracker initialized")
                } catch (e: Throwable) {
                    java.io.File(filesDir, "crash.txt").writeText("HandTracker init failed: \n${e.stackTraceToString()}\n"); Log.e("AirMouseService", "HandTracker init failed: ${e.message}", e)
                }
            }

            startCamera()
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        Log.d("AirMouseService", "stopTracking called")

        mainHandler.post {
            try {
                ProcessCameraProvider.getInstance(this).get().unbindAll()
            } catch (e: Throwable) {
                Log.e("AirMouseService", "stopTracking: ${e.message}", e)
            }
            lifecycleOwner.stop()
            cursorView?.hide()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val cameraProvider = future.get()
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { image ->
                            handTracker?.processImageProxy(image)
                        }
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalyzer
                )
                Log.d("AirMouseService", "Camera started")
            } catch (e: Throwable) {
                java.io.File(filesDir, "crash.txt").appendText("Camera failed: \n${e.stackTraceToString()}\n"); Log.e("AirMouseService", "Camera failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleHandData(data: HandTrackingData) {
        if (!isTracking) return

        val targetX = data.normX * screenWidth
        val targetY = data.normY * screenHeight
        smoothedX = smoothedX * smoothingFactor + targetX * (1f - smoothingFactor)
        smoothedY = smoothedY * smoothingFactor + targetY * (1f - smoothingFactor)

        val currentlyPinching = data.pinchDistance < pinchThreshold
        cursorView?.updatePosition(smoothedX, smoothedY, currentlyPinching)

        val now = System.currentTimeMillis()
        when {
            !isPinching && currentlyPinching -> {
                isPinching = true
                pinchStartTime = now
                pressStartX = smoothedX
                pressStartY = smoothedY
            }
            isPinching && !currentlyPinching -> {
                isPinching = false
                if (isPressActive) {
                    isPressActive = false
                    dispatchPointerUp(smoothedX, smoothedY)
                } else {
                    dispatchClick(pressStartX, pressStartY)
                }
            }
            isPinching && currentlyPinching -> {
                if (!isPressActive) {
                    val dist = Math.hypot(
                        (smoothedX - pressStartX).toDouble(),
                        (smoothedY - pressStartY).toDouble()
                    )
                    if (dist > dragMovementThresholdPx || now - pinchStartTime > clickHoldThresholdMs) {
                        isPressActive = true
                        dispatchPointerDown(pressStartX, pressStartY)
                    }
                } else {
                    dispatchPointerMove(smoothedX, smoothedY)
                }
            }
        }
    }

    private fun handleHandLost() {
        if (isPressActive) {
            isPressActive = false
            dispatchPointerUp(smoothedX, smoothedY)
        }
        isPinching = false
        cursorView?.hide()
    }

    private fun dispatchClick(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        } catch (e: Throwable) { Log.e("AirMouseService", "click: ${e.message}") }
    }

    private fun dispatchPointerDown(x: Float, y: Float) {
        if (isDispatching) return
        try {
            val path = Path().apply { moveTo(x, y) }
            activeStroke = GestureDescription.StrokeDescription(path, 0, 50, true)
            dispatchGesture(GestureDescription.Builder().addStroke(activeStroke!!).build(), null, null)
        } catch (e: Throwable) { Log.e("AirMouseService", "pointerDown: ${e.message}") }
    }

    private fun dispatchPointerMove(x: Float, y: Float) {
        if (activeStroke == null || isDispatching) return
        isDispatching = true
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = activeStroke!!.continueStroke(path, 0, 50, true)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { isDispatching = false; activeStroke = stroke }
                override fun onCancelled(g: GestureDescription?) { isDispatching = false; activeStroke = null }
            }, null)
        } catch (e: Throwable) { isDispatching = false; Log.e("AirMouseService", "pointerMove: ${e.message}") }
    }

    private fun dispatchPointerUp(x: Float, y: Float) {
        if (activeStroke == null || isDispatching) return
        isDispatching = true
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = activeStroke!!.continueStroke(path, 0, 50, false)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { isDispatching = false; activeStroke = null }
                override fun onCancelled(g: GestureDescription?) { isDispatching = false; activeStroke = null }
            }, null)
        } catch (e: Throwable) { isDispatching = false; Log.e("AirMouseService", "pointerUp: ${e.message}") }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        try { cursorView?.let { windowManager?.removeView(it) } } catch (e: Throwable) {}
        handTracker?.close()
        cameraExecutor.shutdown()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
