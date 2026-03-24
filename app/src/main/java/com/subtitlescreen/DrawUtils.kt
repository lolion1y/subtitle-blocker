package com.subtitlescreen

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

object DrawUtils {

    fun drawFeatheredRect(
        canvas: Canvas,
        l: Float, t: Float, r: Float, b: Float,
        color: Int, feather: Float
    ) {
        val solidPaint = Paint().apply { this.color = color }

        if (feather <= 0f) {
            canvas.drawRect(l, t, r, b, solidPaint)
            return
        }

        // Clamp feather to half the smallest dimension instead of
        // falling back to solid — allows full feathering on small rects
        val f = feather.coerceAtMost(minOf((r - l) / 2f, (b - t) / 2f))

        val transparent = color and 0x00FFFFFF
        val gp = Paint().apply { isAntiAlias = true }

        // Solid core (may be zero-size at max feather, which is fine)
        if (l + f < r - f && t + f < b - f) {
            canvas.drawRect(l + f, t + f, r - f, b - f, solidPaint)
        }

        // Top edge
        gp.shader = LinearGradient(0f, t, 0f, t + f, transparent, color, Shader.TileMode.CLAMP)
        canvas.drawRect(l + f, t, r - f, t + f, gp)

        // Bottom edge
        gp.shader = LinearGradient(0f, b - f, 0f, b, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(l + f, b - f, r - f, b, gp)

        // Left edge
        if (t + f < b - f) {
            gp.shader = LinearGradient(l, 0f, l + f, 0f, transparent, color, Shader.TileMode.CLAMP)
            canvas.drawRect(l, t + f, l + f, b - f, gp)
        }

        // Right edge
        if (t + f < b - f) {
            gp.shader = LinearGradient(r - f, 0f, r, 0f, color, transparent, Shader.TileMode.CLAMP)
            canvas.drawRect(r - f, t + f, r, b - f, gp)
        }

        // Corners
        gp.shader = RadialGradient(l + f, t + f, f, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, l + f, t + f, gp)

        gp.shader = RadialGradient(r - f, t + f, f, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(r - f, t, r, t + f, gp)

        gp.shader = RadialGradient(l + f, b - f, f, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(l, b - f, l + f, b, gp)

        gp.shader = RadialGradient(r - f, b - f, f, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(r - f, b - f, r, b, gp)
    }

    /**
     * Returns white or black (with given alpha) depending on the perceived
     * luminance of the background color+alpha combination.
     */
    fun contrastColor(color: Int, alpha: Int, iconAlpha: Int = 0x80): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        // Factor in opacity — if mostly transparent, assume mid-tone background
        val effective = lum * (alpha / 255.0) + 0.5 * (1.0 - alpha / 255.0)
        val rgb = if (effective > 0.45) 0x000000 else 0xFFFFFF
        return (iconAlpha shl 24) or rgb
    }
}
