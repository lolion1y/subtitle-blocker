package com.subtitlescreen

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

object DrawUtils {

    private val solidPaint = Paint()
    private val gradPaint = Paint().apply { isAntiAlias = true }

    fun drawFeatheredRect(
        canvas: Canvas,
        l: Float, t: Float, r: Float, b: Float,
        color: Int, feather: Float
    ) {
        solidPaint.color = color

        if (feather <= 0f) {
            canvas.drawRect(l, t, r, b, solidPaint)
            return
        }

        val f = feather.coerceAtMost(minOf((r - l) / 2f, (b - t) / 2f))
        val transparent = color and 0x00FFFFFF

        if (l + f < r - f && t + f < b - f) {
            canvas.drawRect(l + f, t + f, r - f, b - f, solidPaint)
        }

        gradPaint.shader = LinearGradient(0f, t, 0f, t + f, transparent, color, Shader.TileMode.CLAMP)
        canvas.drawRect(l + f, t, r - f, t + f, gradPaint)

        gradPaint.shader = LinearGradient(0f, b - f, 0f, b, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(l + f, b - f, r - f, b, gradPaint)

        if (t + f < b - f) {
            gradPaint.shader = LinearGradient(l, 0f, l + f, 0f, transparent, color, Shader.TileMode.CLAMP)
            canvas.drawRect(l, t + f, l + f, b - f, gradPaint)

            gradPaint.shader = LinearGradient(r - f, 0f, r, 0f, color, transparent, Shader.TileMode.CLAMP)
            canvas.drawRect(r - f, t + f, r, b - f, gradPaint)
        }

        gradPaint.shader = RadialGradient(l + f, t + f, f, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, l + f, t + f, gradPaint)

        gradPaint.shader = RadialGradient(r - f, t + f, f, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(r - f, t, r, t + f, gradPaint)

        gradPaint.shader = RadialGradient(l + f, b - f, f, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(l, b - f, l + f, b, gradPaint)

        gradPaint.shader = RadialGradient(r - f, b - f, f, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(r - f, b - f, r, b, gradPaint)

        gradPaint.shader = null
    }

    fun contrastColor(color: Int, alpha: Int, iconAlpha: Int = 0x80): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        val effective = lum * (alpha / 255.0) + 0.5 * (1.0 - alpha / 255.0)
        val rgb = if (effective > 0.45) 0x000000 else 0xFFFFFF
        return (iconAlpha shl 24) or rgb
    }
}
