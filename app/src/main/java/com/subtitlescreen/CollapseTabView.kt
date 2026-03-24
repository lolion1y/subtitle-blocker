package com.subtitlescreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class CollapseTabView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val tapThreshold = dp(8f)

    private val bgPaint = Paint().apply { isAntiAlias = true }
    private val arrowPaint = Paint().apply {
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    var tabColor = 0xFFFFFF
        set(v) { field = v; updatePaints() }
    var tabAlpha = 0x60
        set(v) { field = v; updatePaints() }

    var onClick: (() -> Unit)? = null
    var onDragY: ((deltaY: Int) -> Unit)? = null

    private var startRawX = 0f
    private var startRawY = 0f
    private var lastRawY = 0f
    private var isDragging = false

    private fun updatePaints() {
        bgPaint.color = (tabAlpha shl 24) or (tabColor and 0x00FFFFFF)
        arrowPaint.color = DrawUtils.contrastColor(tabColor, tabAlpha, 0xDD)
        invalidate()
    }

    init {
        updatePaints()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 4f * density
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, bgPaint)

        val cx = w / 2f
        val cy = h / 2f
        val s = 5f * density
        canvas.drawLine(cx + s * 0.3f, cy - s, cx - s * 0.7f, cy, arrowPaint)
        canvas.drawLine(cx - s * 0.7f, cy, cx + s * 0.3f, cy + s, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                lastRawY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dist = hypot(
                        (event.rawX - startRawX).toDouble(),
                        (event.rawY - startRawY).toDouble()
                    ).toFloat()
                    if (dist > tapThreshold) {
                        isDragging = true
                        lastRawY = event.rawY
                    }
                } else {
                    val dy = (event.rawY - lastRawY).toInt()
                    lastRawY = event.rawY
                    onDragY?.invoke(dy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    onClick?.invoke()
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
