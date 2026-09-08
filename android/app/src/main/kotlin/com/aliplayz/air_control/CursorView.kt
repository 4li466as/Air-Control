package com.aliplayz.air_control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class CursorView(context: Context) : View(context) {
    private var cursorX = -100f
    private var cursorY = -100f
    private var isPinching = false
    private var isVisible = false

    private val pointerPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2196F3") // Blue
        isAntiAlias = true
    }

    private val pinchPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4CAF50") // Green
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 6f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisible) return

        val paint = if (isPinching) pinchPaint else pointerPaint
        val radius = if (isPinching) 24f else 20f

        canvas.drawCircle(cursorX, cursorY, radius, paint)
        canvas.drawCircle(cursorX, cursorY, radius, strokePaint)
    }

    fun updatePosition(x: Float, y: Float, pinching: Boolean) {
        cursorX = x
        cursorY = y
        isPinching = pinching
        isVisible = true
        postInvalidate()
    }

    fun hide() {
        isVisible = false
        postInvalidate()
    }
}
