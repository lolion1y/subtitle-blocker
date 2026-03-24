package com.subtitlescreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class OverlayView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dpInt(v: Float) = (v * density).toInt()

    // Layout
    private val borderV = dpInt(3f)
    private val handleTouchRadius = dp(28f)
    private val handleVisualLen = dp(14f)
    private val handleStroke = dp(2.5f)
    private val collapseTouchRadius = dp(18f)
    private val collapseLineHalf = dp(8f)
    private val tapThreshold = dp(8f)
    private val minInnerWidth = dpInt(60f)
    private val minInnerHeight = dpInt(20f)

    // Inner rect geometry (relative to this view)
    var innerLeft = 0
    var innerWidth = 0
    var innerHeight = dpInt(80f)

    // Appearance
    var innerColor = 0x000000
    var innerAlpha = 255
    var bandColor = 0xFFFFFF
    var bandAlpha = 0x20
    var featherPx = 0f
    var handleColorOverride = Prefs.HANDLE_COLOR_AUTO

    // Paints
    private val bandPaint = Paint()
    private val innerPaint = Paint()
    private val handlePaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = handleStroke
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val collapsePaint = Paint().apply {
        color = 0x80FFFFFF.toInt()
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // Touch
    private enum class Mode { NONE, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR, COLLAPSE_TAP }

    private var mode = Mode.NONE
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var startRawX = 0f
    private var startRawY = 0f

    // Triple-tap tracking for opening settings
    private val tapTimes = LongArray(3)
    private var tapCount = 0
    private val tripleTapWindow = 600L // ms

    // Callbacks
    var onLayoutUpdate: ((deltaY: Int, newTotalHeight: Int) -> Unit)? = null
    var onCollapse: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null

    fun getTotalHeight() = borderV + innerHeight + borderV

    private fun updatePaints() {
        bandPaint.color = (bandAlpha shl 24) or (bandColor and 0x00FFFFFF)
        innerPaint.color = (innerAlpha shl 24) or (innerColor and 0x00FFFFFF)
        if (handleColorOverride == Prefs.HANDLE_COLOR_AUTO) {
            handlePaint.color = DrawUtils.contrastColor(innerColor, innerAlpha, 0xFF)
            collapsePaint.color = DrawUtils.contrastColor(innerColor, innerAlpha, 0x80)
        } else {
            handlePaint.color = (0xFF shl 24) or (handleColorOverride and 0x00FFFFFF)
            collapsePaint.color = (0x80 shl 24) or (handleColorOverride and 0x00FFFFFF)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (innerWidth == 0) {
            innerWidth = w - dpInt(8f)
            innerLeft = dpInt(4f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        updatePaints()

        val iL = innerLeft.toFloat()
        val iT = borderV.toFloat()
        val iR = (innerLeft + innerWidth).toFloat()
        val iB = (borderV + innerHeight).toFloat()

        // Band background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bandPaint)

        // Inner rect (with optional feathered edges)
        val innerArgb = (innerAlpha shl 24) or (innerColor and 0x00FFFFFF)
        DrawUtils.drawFeatheredRect(canvas, iL, iT, iR, iB, innerArgb, featherPx)

        // Corner handles
        val h = handleVisualLen
        // Top-left
        canvas.drawLine(iL, iT, iL + h, iT, handlePaint)
        canvas.drawLine(iL, iT, iL, iT + h, handlePaint)
        // Top-right
        canvas.drawLine(iR, iT, iR - h, iT, handlePaint)
        canvas.drawLine(iR, iT, iR, iT + h, handlePaint)
        // Bottom-left
        canvas.drawLine(iL, iB, iL + h, iB, handlePaint)
        canvas.drawLine(iL, iB, iL, iB - h, handlePaint)
        // Bottom-right
        canvas.drawLine(iR, iB, iR - h, iB, handlePaint)
        canvas.drawLine(iR, iB, iR, iB - h, handlePaint)

        // Collapse button (» double chevron pointing right, indicating "push to side")
        val cx = (iL + iR) / 2f
        val cy = (iT + iB) / 2f
        val chevH = dp(5f)  // vertical half-size
        val chevW = dp(3.5f)  // horizontal depth
        // First >
        val p1 = cx - dp(1.5f)
        canvas.drawLine(p1 - chevW, cy - chevH, p1, cy, collapsePaint)
        canvas.drawLine(p1, cy, p1 - chevW, cy + chevH, collapsePaint)
        // Second >
        val p2 = cx + dp(4.5f)
        canvas.drawLine(p2 - chevW, cy - chevH, p2, cy, collapsePaint)
        canvas.drawLine(p2, cy, p2 - chevW, cy + chevH, collapsePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY

                val x = event.x
                val y = event.y

                val iL = innerLeft.toFloat()
                val iT = borderV.toFloat()
                val iR = (innerLeft + innerWidth).toFloat()
                val iB = (borderV + innerHeight).toFloat()
                val cx = (iL + iR) / 2f
                val cy = (iT + iB) / 2f

                val collapseDist = hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()

                val tlDist = hypot((x - iL).toDouble(), (y - iT).toDouble()).toFloat()
                val trDist = hypot((x - iR).toDouble(), (y - iT).toDouble()).toFloat()
                val blDist = hypot((x - iL).toDouble(), (y - iB).toDouble()).toFloat()
                val brDist = hypot((x - iR).toDouble(), (y - iB).toDouble()).toFloat()
                val minCornerDist = minOf(tlDist, trDist, blDist, brDist)
                val closestCorner = when (minCornerDist) {
                    tlDist -> Mode.RESIZE_TL
                    trDist -> Mode.RESIZE_TR
                    blDist -> Mode.RESIZE_BL
                    else -> Mode.RESIZE_BR
                }

                mode = when {
                    collapseDist < collapseTouchRadius -> Mode.COLLAPSE_TAP
                    minCornerDist < handleTouchRadius -> closestCorner
                    else -> Mode.MOVE
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.COLLAPSE_TAP) {
                    val dist = hypot(
                        (event.rawX - startRawX).toDouble(),
                        (event.rawY - startRawY).toDouble()
                    ).toFloat()
                    if (dist > tapThreshold) {
                        mode = Mode.MOVE
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                    }
                    return true
                }

                val dx = (event.rawX - lastRawX).toInt()
                val dy = (event.rawY - lastRawY).toInt()
                lastRawX = event.rawX
                lastRawY = event.rawY

                when (mode) {
                    Mode.MOVE -> {
                        innerLeft = (innerLeft + dx)
                            .coerceIn(0, (width - innerWidth).coerceAtLeast(0))
                        onLayoutUpdate?.invoke(dy, getTotalHeight())
                        invalidate()
                    }

                    Mode.RESIZE_TL, Mode.RESIZE_TR, Mode.RESIZE_BL, Mode.RESIZE_BR -> {
                        var bandDy = 0
                        val isLeft = mode == Mode.RESIZE_TL || mode == Mode.RESIZE_BL
                        val isTop = mode == Mode.RESIZE_TL || mode == Mode.RESIZE_TR

                        // Horizontal
                        if (isLeft) {
                            val maxDx = innerWidth - minInnerWidth
                            val clampedDx = dx.coerceIn(-innerLeft, maxDx)
                            innerLeft += clampedDx
                            innerWidth -= clampedDx
                        } else {
                            innerWidth = (innerWidth + dx).coerceIn(
                                minInnerWidth,
                                (width - innerLeft).coerceAtLeast(minInnerWidth)
                            )
                        }

                        // Vertical
                        if (isTop) {
                            val maxDy = innerHeight - minInnerHeight
                            val clampedDy = dy.coerceAtMost(maxDy)
                            innerHeight -= clampedDy
                            bandDy = clampedDy
                        } else {
                            innerHeight = (innerHeight + dy).coerceAtLeast(minInnerHeight)
                        }

                        onLayoutUpdate?.invoke(bandDy, getTotalHeight())
                        invalidate()
                    }

                    else -> {}
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.action == MotionEvent.ACTION_UP) {
                    when (mode) {
                        Mode.COLLAPSE_TAP -> onCollapse?.invoke()
                        Mode.MOVE -> {
                            // Check if this was a tap (no significant movement)
                            val dist = hypot(
                                (event.rawX - startRawX).toDouble(),
                                (event.rawY - startRawY).toDouble()
                            ).toFloat()
                            if (dist < tapThreshold) {
                                val now = System.currentTimeMillis()
                                tapTimes[tapCount % 3] = now
                                tapCount++
                                if (tapCount >= 3) {
                                    val oldest = tapTimes[(tapCount - 3) % 3]
                                    if (now - oldest < tripleTapWindow) {
                                        onOpenSettings?.invoke()
                                        tapCount = 0
                                    }
                                }
                            } else {
                                tapCount = 0
                            }
                        }
                        else -> {}
                    }
                }
                mode = Mode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
