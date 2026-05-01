/*
 * Subtitle Blocker — overlay to mask hard-coded video subtitles.
 * Copyright (C) 2026 Jonathan Heard
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
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
    private val handleTouchRadius = dp(40f)
    private val handleVisualLen = dp(14f)
    private val handleStroke = dp(2.5f)
    private val collapseTouchRadius = dp(18f)
    private val tapThreshold = dp(8f)
    private val minInnerWidth = dpInt(60f)
    private val minInnerHeight = dpInt(20f)

    // Inner rect geometry (relative to this view)
    var innerLeft = 0
    var innerWidth = 0
    var innerHeight = dpInt(80f)

    // Appearance — call invalidate() after changing any of these
    var innerColor = 0x000000
        set(v) { field = v; updatePaints() }
    var innerAlpha = 255
        set(v) { field = v; updatePaints() }
    var bandColor = 0xFFFFFF
        set(v) { field = v; updatePaints() }
    var bandAlpha = 0x20
        set(v) { field = v; updatePaints() }
    var featherPx = 0f
    var handleColorOverride = Prefs.HANDLE_COLOR_AUTO
        set(v) { field = v; updatePaints() }
    var tabSide = Prefs.TAB_SIDE_RIGHT
        set(v) { field = v; invalidate() }
    var symmetricResize = false

    // Paints
    private val bandPaint = Paint()
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
    private val tripleTapWindow = 600L

    // Callbacks
    var onLayoutUpdate: ((deltaY: Int, newTotalHeight: Int) -> Unit)? = null
    var onCollapse: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null

    fun getTotalHeight() = borderV + innerHeight + borderV

    private fun updatePaints() {
        bandPaint.color = (bandAlpha shl 24) or (bandColor and 0x00FFFFFF)
        if (handleColorOverride == Prefs.HANDLE_COLOR_AUTO) {
            handlePaint.color = DrawUtils.contrastColor(innerColor, innerAlpha, 0xFF)
            collapsePaint.color = DrawUtils.contrastColor(innerColor, innerAlpha, 0x80)
        } else {
            handlePaint.color = (0xFF shl 24) or (handleColorOverride and 0x00FFFFFF)
            collapsePaint.color = (0x80 shl 24) or (handleColorOverride and 0x00FFFFFF)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (innerWidth == 0) {
            innerWidth = w - dpInt(8f)
            innerLeft = dpInt(4f)
        }
    }

    override fun onDraw(canvas: Canvas) {
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
        canvas.drawLine(iL, iT, iL + h, iT, handlePaint)
        canvas.drawLine(iL, iT, iL, iT + h, handlePaint)
        canvas.drawLine(iR, iT, iR - h, iT, handlePaint)
        canvas.drawLine(iR, iT, iR, iT + h, handlePaint)
        canvas.drawLine(iL, iB, iL + h, iB, handlePaint)
        canvas.drawLine(iL, iB, iL, iB - h, handlePaint)
        canvas.drawLine(iR, iB, iR - h, iB, handlePaint)
        canvas.drawLine(iR, iB, iR, iB - h, handlePaint)

        // Collapse button (» pointing right, or « pointing left)
        val cx = (iL + iR) / 2f
        val cy = (iT + iB) / 2f
        val chevH = dp(5f)
        val chevW = dp(3.5f)
        if (tabSide == Prefs.TAB_SIDE_LEFT) {
            val p1 = cx + dp(1.5f)
            canvas.drawLine(p1 + chevW, cy - chevH, p1, cy, collapsePaint)
            canvas.drawLine(p1, cy, p1 + chevW, cy + chevH, collapsePaint)
            val p2 = cx - dp(4.5f)
            canvas.drawLine(p2 + chevW, cy - chevH, p2, cy, collapsePaint)
            canvas.drawLine(p2, cy, p2 + chevW, cy + chevH, collapsePaint)
        } else {
            val p1 = cx - dp(1.5f)
            canvas.drawLine(p1 - chevW, cy - chevH, p1, cy, collapsePaint)
            canvas.drawLine(p1, cy, p1 - chevW, cy + chevH, collapsePaint)
            val p2 = cx + dp(4.5f)
            canvas.drawLine(p2 - chevW, cy - chevH, p2, cy, collapsePaint)
            canvas.drawLine(p2, cy, p2 - chevW, cy + chevH, collapsePaint)
        }
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

                        if (symmetricResize) {
                            // Positive growDx/growDy = grow outward from centre,
                            // negative = shrink toward centre. Both edges move by
                            // growDx/growDy, keeping the inner rect centre fixed.
                            val growDx = if (isLeft) -dx else dx
                            val growDy = if (isTop) -dy else dy

                            val slackL = innerLeft
                            val slackR = (width - innerLeft - innerWidth).coerceAtLeast(0)
                            val maxGrowDx = minOf(slackL, slackR)
                            val minGrowDx = -((innerWidth - minInnerWidth) / 2)
                            val clampedGrowDx = growDx.coerceIn(minGrowDx, maxGrowDx)
                            innerLeft -= clampedGrowDx
                            innerWidth += 2 * clampedGrowDx

                            val minGrowDy = -((innerHeight - minInnerHeight) / 2)
                            val clampedGrowDy = growDy.coerceAtLeast(minGrowDy)
                            innerHeight += 2 * clampedGrowDy
                            bandDy = -clampedGrowDy
                        } else {
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

                            if (isTop) {
                                val maxDy = innerHeight - minInnerHeight
                                val clampedDy = dy.coerceAtMost(maxDy)
                                innerHeight -= clampedDy
                                bandDy = clampedDy
                            } else {
                                innerHeight = (innerHeight + dy).coerceAtLeast(minInnerHeight)
                            }
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
