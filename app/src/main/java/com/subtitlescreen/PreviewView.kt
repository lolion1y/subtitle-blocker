package com.subtitlescreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class PreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = context.resources.displayMetrics.density

    var innerColor = 0x000000
    var innerAlpha = 255
    var bandColor = 0xFFFFFF
    var bandAlpha = 0x20
    var tabColor = 0xFFFFFF
    var tabAlpha = 0x60
    var featherPx = 0f
    var handleColorOverride = Prefs.HANDLE_COLOR_AUTO

    private val checkerLight = Paint().apply { color = 0xFFE0E0E0.toInt() }
    private val checkerDark = Paint().apply { color = 0xFFC0C0C0.toInt() }
    private val bandPaint = Paint()
    private val tabPaint = Paint().apply { isAntiAlias = true }
    private val tabArrowPaint = Paint().apply {
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val subtitleBgPaint = Paint().apply { color = 0xCC000000.toInt() }
    private val subtitleTextPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    fun refresh() = invalidate()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cs = 8f * density

        // 1. Checkerboard
        var chy = 0f
        var row = 0
        while (chy < h) {
            var chx = 0f
            var col = row
            while (chx < w) {
                canvas.drawRect(chx, chy, (chx + cs).coerceAtMost(w), (chy + cs).coerceAtMost(h),
                    if (col % 2 == 0) checkerLight else checkerDark)
                chx += cs
                col++
            }
            chy += cs
            row++
        }

        // 2. Subtitle text — vertically centered in the view
        val subText = "Example Subtitle Text"
        val fm = subtitleTextPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val pad = 4f * density
        val textBaseline = (h - textH) / 2f - fm.ascent
        val textTop = (h - textH) / 2f
        val textWidth = subtitleTextPaint.measureText(subText)
        canvas.drawRect(
            w / 2 - textWidth / 2 - pad,
            textTop - pad,
            w / 2 + textWidth / 2 + pad,
            textTop + textH + pad,
            subtitleBgPaint
        )
        canvas.drawText(subText, w / 2, textBaseline, subtitleTextPaint)

        // 3. Band overlay
        bandPaint.color = (bandAlpha shl 24) or (bandColor and 0x00FFFFFF)
        canvas.drawRect(0f, 0f, w, h, bandPaint)

        // 4. Inner rect with feathering
        val iW = w * 0.7f
        val iH = h * 0.55f
        val iL = (w - iW) / 2f
        val iT = (h - iH) / 2f
        val iR = iL + iW
        val iB = iT + iH
        val innerArgb = (innerAlpha shl 24) or (innerColor and 0x00FFFFFF)
        DrawUtils.drawFeatheredRect(canvas, iL, iT, iR, iB, innerArgb, featherPx)

        // 5. Corner handle hints
        handlePaint.color = if (handleColorOverride == Prefs.HANDLE_COLOR_AUTO)
            DrawUtils.contrastColor(innerColor, innerAlpha, 0xFF)
        else (0xFF shl 24) or (handleColorOverride and 0x00FFFFFF)
        val hl = 6f * density
        canvas.drawLine(iL, iT, iL + hl, iT, handlePaint)
        canvas.drawLine(iL, iT, iL, iT + hl, handlePaint)
        canvas.drawLine(iR, iT, iR - hl, iT, handlePaint)
        canvas.drawLine(iR, iT, iR, iT + hl, handlePaint)
        canvas.drawLine(iL, iB, iL + hl, iB, handlePaint)
        canvas.drawLine(iL, iB, iL, iB - hl, handlePaint)
        canvas.drawLine(iR, iB, iR - hl, iB, handlePaint)
        canvas.drawLine(iR, iB, iR, iB - hl, handlePaint)

        // 6. Tab — proportional to real 36x56dp tab
        val tW = 18f * density
        val tH = 28f * density
        val tR = 3f * density
        val tT = (h - tH) / 2f
        val tL = w - tW
        tabPaint.color = (tabAlpha shl 24) or (tabColor and 0x00FFFFFF)
        canvas.drawRoundRect(RectF(tL, tT, w, tT + tH), tR, tR, tabPaint)

        // Chevron on the tab (contrasting colour)
        tabArrowPaint.color = DrawUtils.contrastColor(tabColor, tabAlpha, 0xDD)
        val tcx = tL + tW / 2f
        val tcy = tT + tH / 2f
        val s = 3f * density
        canvas.drawLine(tcx + s * 0.3f, tcy - s, tcx - s * 0.7f, tcy, tabArrowPaint)
        canvas.drawLine(tcx - s * 0.7f, tcy, tcx + s * 0.3f, tcy + s, tabArrowPaint)
    }
}
