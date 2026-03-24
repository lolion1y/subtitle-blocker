package com.subtitlescreen

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object Prefs {
    private const val NAME = "subtitle_screen"

    // Keys
    private const val INNER_COLOR = "inner_color"
    private const val INNER_ALPHA = "inner_alpha"
    private const val BAND_COLOR = "band_color"
    private const val BAND_ALPHA = "band_alpha"
    private const val TAB_COLOR = "tab_color"
    private const val TAB_ALPHA = "tab_alpha"
    private const val HANDLE_COLOR = "handle_color"
    private const val REMEMBER_POS = "remember_position"
    private const val BAND_Y = "band_y"
    private const val INNER_LEFT = "inner_left"
    private const val INNER_WIDTH = "inner_width"
    private const val INNER_HEIGHT = "inner_height"
    private const val FEATHER = "feather"
    private const val HAS_SAVED = "has_saved"

    // Defaults
    const val DEFAULT_INNER_COLOR = 0x000000
    const val DEFAULT_INNER_ALPHA = 255
    const val DEFAULT_BAND_COLOR = 0xFFFFFF
    const val DEFAULT_BAND_ALPHA = 0x20
    const val DEFAULT_TAB_COLOR = 0xFFFFFF
    const val DEFAULT_TAB_ALPHA = 0x60
    const val MIN_TAB_ALPHA = 0x1E
    const val DEFAULT_FEATHER = 0
    const val MAX_FEATHER_DP = 30f
    const val DEFAULT_INNER_HEIGHT_DP = 80
    const val HANDLE_COLOR_AUTO = Int.MIN_VALUE
    const val DEFAULT_HANDLE_COLOR = HANDLE_COLOR_AUTO

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getInnerColor(ctx: Context) = prefs(ctx).getInt(INNER_COLOR, DEFAULT_INNER_COLOR)
    fun setInnerColor(ctx: Context, v: Int) = prefs(ctx).edit().putInt(INNER_COLOR, v).apply()

    fun getInnerAlpha(ctx: Context) = prefs(ctx).getInt(INNER_ALPHA, DEFAULT_INNER_ALPHA)
    fun setInnerAlpha(ctx: Context, v: Int) = prefs(ctx).edit().putInt(INNER_ALPHA, v).apply()

    fun getBandColor(ctx: Context) = prefs(ctx).getInt(BAND_COLOR, DEFAULT_BAND_COLOR)
    fun setBandColor(ctx: Context, v: Int) = prefs(ctx).edit().putInt(BAND_COLOR, v).apply()

    fun getBandAlpha(ctx: Context) = prefs(ctx).getInt(BAND_ALPHA, DEFAULT_BAND_ALPHA)
    fun setBandAlpha(ctx: Context, v: Int) = prefs(ctx).edit().putInt(BAND_ALPHA, v).apply()

    fun getTabColor(ctx: Context) = prefs(ctx).getInt(TAB_COLOR, DEFAULT_TAB_COLOR)
    fun setTabColor(ctx: Context, v: Int) = prefs(ctx).edit().putInt(TAB_COLOR, v).apply()

    fun getTabAlpha(ctx: Context) = prefs(ctx).getInt(TAB_ALPHA, DEFAULT_TAB_ALPHA)
    fun setTabAlpha(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(TAB_ALPHA, v.coerceAtLeast(MIN_TAB_ALPHA)).apply()

    fun getHandleColor(ctx: Context) = prefs(ctx).getInt(HANDLE_COLOR, DEFAULT_HANDLE_COLOR)
    fun setHandleColor(ctx: Context, v: Int) = prefs(ctx).edit().putInt(HANDLE_COLOR, v).apply()

    fun getFeather(ctx: Context) = prefs(ctx).getInt(FEATHER, DEFAULT_FEATHER)
    fun setFeather(ctx: Context, v: Int) = prefs(ctx).edit().putInt(FEATHER, v).apply()

    fun featherToPx(feather: Int, density: Float) =
        (feather / 100f) * MAX_FEATHER_DP * density

    fun getRememberPosition(ctx: Context) = prefs(ctx).getBoolean(REMEMBER_POS, true)
    fun setRememberPosition(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(REMEMBER_POS, v).apply()

    fun hasSavedPosition(ctx: Context) = prefs(ctx).getBoolean(HAS_SAVED, false)

    fun savePosition(ctx: Context, bandY: Int, innerLeft: Int, innerWidth: Int, innerHeight: Int) {
        prefs(ctx).edit()
            .putBoolean(HAS_SAVED, true)
            .putInt(BAND_Y, bandY)
            .putInt(INNER_LEFT, innerLeft)
            .putInt(INNER_WIDTH, innerWidth)
            .putInt(INNER_HEIGHT, innerHeight)
            .apply()
    }

    fun getSavedBandY(ctx: Context) = prefs(ctx).getInt(BAND_Y, -1)
    fun getSavedInnerLeft(ctx: Context) = prefs(ctx).getInt(INNER_LEFT, -1)
    fun getSavedInnerWidth(ctx: Context) = prefs(ctx).getInt(INNER_WIDTH, -1)
    fun getSavedInnerHeight(ctx: Context) = prefs(ctx).getInt(INNER_HEIGHT, -1)

    fun resetAll(ctx: Context) = prefs(ctx).edit().clear().apply()

    // Logarithmic slider mapping: sqrt curve gives less sensitivity near 100%
    fun sliderToAlpha(pos: Int): Int =
        (255 * sqrt(pos / 255.0)).roundToInt().coerceIn(0, 255)

    fun alphaToSlider(alpha: Int): Int =
        (255 * (alpha / 255.0).pow(2.0)).roundToInt().coerceIn(0, 255)
}
