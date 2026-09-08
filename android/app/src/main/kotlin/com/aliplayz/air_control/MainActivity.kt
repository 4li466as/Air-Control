package com.aliplayz.air_control

import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.aliplayz.air_control/service"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        val crashFile = java.io.File(context.filesDir, "crash.txt")
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crashFile.writeText("Crash in ${thread.name}:\n${throwable.stackTraceToString()}")
            } catch(e: Exception) {}
            oldHandler?.uncaughtException(thread, throwable)
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getCrashLog" -> {
                    if (crashFile.exists()) {
                        val log = crashFile.readText()
                        crashFile.delete()
                        result.success(log)
                    } else {
                        result.success(null)
                    }
                }

                "isAccessibilityEnabled" -> {
                    result.success(AirMouseService.instance != null)
                }
                "openAccessibilitySettings" -> {
                    // FLAG_ACTIVITY_NEW_TASK ensures Accessibility Settings opens in its own task,
                    // so pressing Back always returns to our app instead of closing it.
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    result.success(null)
                }
                "startTracking" -> {
                    AirMouseService.instance?.startTracking()
                    result.success(null)
                }
                "stopTracking" -> {
                    AirMouseService.instance?.stopTracking()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
