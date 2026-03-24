package com.subtitlescreen

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private val innerPresets = intArrayOf(
        0x000000, 0x1A1A1A, 0x333333, 0x0D1B2A, 0x888888, 0xCCCCCC, 0xFFFFFF,
    )

    private val generalPresets = intArrayOf(
        0xFFFFFF, 0x000000, 0x333333, 0x888888, 0x3366AA, 0x33AA66, 0xAA6633,
    )

    private val handlePresets = intArrayOf(
        0xFFFFFF, 0x000000, 0x888888, 0xFF4444, 0x44AA44, 0x4488FF,
    )

    private data class ColorPicker(
        val views: MutableList<View> = mutableListOf(),
        var autoView: View? = null,
        var customView: View? = null,
        var selected: Int = 0,
        val presets: IntArray = intArrayOf(),
        val hasAuto: Boolean = false,
        val onSelect: (Int) -> Unit = {}
    )

    private lateinit var innerPicker: ColorPicker
    private lateinit var bandPicker: ColorPicker
    private lateinit var tabPicker: ColorPicker
    private lateinit var handlePicker: ColorPicker
    private lateinit var preview: PreviewView

    private lateinit var valInnerAlpha: TextView
    private lateinit var valBandAlpha: TextView
    private lateinit var valTabAlpha: TextView
    private lateinit var valFeather: TextView

    private val d by lazy { resources.displayMetrics.density }
    private fun dpPx(v: Int) = (v * d).toInt()

    private fun alphaPct(alpha: Int) = "${alpha * 100 / 255}%"

    override fun onResume() {
        super.onResume()
        sendServiceAction(OverlayService.ACTION_HIDE)
    }

    override fun onPause() {
        super.onPause()
        sendServiceAction(OverlayService.ACTION_SHOW)
    }

    private fun sendServiceAction(action: String) {
        if (OverlayService.isRunning) {
            startService(Intent(this, OverlayService::class.java).apply {
                this.action = action
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_settings)

        preview = findViewById(R.id.preview)
        valInnerAlpha = findViewById(R.id.valInnerAlpha)
        valBandAlpha = findViewById(R.id.valBandAlpha)
        valTabAlpha = findViewById(R.id.valTabAlpha)
        valFeather = findViewById(R.id.valFeather)

        innerPicker = ColorPicker(
            presets = innerPresets,
            selected = Prefs.getInnerColor(this),
            onSelect = { Prefs.setInnerColor(this, it); notifyService(); updatePreview() }
        )
        bandPicker = ColorPicker(
            presets = generalPresets,
            selected = Prefs.getBandColor(this),
            onSelect = { Prefs.setBandColor(this, it); notifyService(); updatePreview() }
        )
        tabPicker = ColorPicker(
            presets = generalPresets,
            selected = Prefs.getTabColor(this),
            onSelect = { Prefs.setTabColor(this, it); notifyService(); updatePreview() }
        )
        handlePicker = ColorPicker(
            presets = handlePresets,
            selected = Prefs.getHandleColor(this),
            hasAuto = true,
            onSelect = { Prefs.setHandleColor(this, it); notifyService(); updatePreview() }
        )

        setupColorRow(R.id.colorRowInner, innerPicker)
        setupColorRow(R.id.colorRowBand, bandPicker)
        setupColorRow(R.id.colorRowTab, tabPicker)
        setupColorRow(R.id.colorRowHandle, handlePicker)
        setupSliders()
        setupRememberToggle()
        setupResetButton()
        updateAllValues()
        updatePreview()
    }

    private fun setupColorRow(rowId: Int, picker: ColorPicker) {
        val row = findViewById<LinearLayout>(rowId)
        val sizePx = dpPx(32)
        val marginPx = dpPx(4)

        // Auto option (for handle colour)
        if (picker.hasAuto) {
            val autoView = View(this)
            autoView.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginEnd = marginPx
            }
            autoView.setOnClickListener {
                picker.selected = Prefs.HANDLE_COLOR_AUTO
                picker.onSelect(Prefs.HANDLE_COLOR_AUTO)
                updateColorRow(picker)
            }
            row.addView(autoView)
            picker.autoView = autoView
        }

        for (color in picker.presets) {
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginEnd = marginPx
            }
            v.setOnClickListener {
                picker.selected = color
                picker.onSelect(color)
                updateColorRow(picker)
            }
            row.addView(v)
            picker.views.add(v)
        }

        val customView = View(this)
        customView.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        customView.setOnClickListener {
            val startColor = if (picker.selected == Prefs.HANDLE_COLOR_AUTO || picker.selected < 0)
                0x888888 else picker.selected
            showColorPickerDialog(startColor) { color ->
                picker.selected = color
                picker.onSelect(color)
                updateColorRow(picker)
            }
        }
        row.addView(customView)
        picker.customView = customView

        updateColorRow(picker)
    }

    private fun selectionBorder(presetColor: Int): Int {
        val r = (presetColor shr 16) and 0xFF
        val g = (presetColor shr 8) and 0xFF
        val b = presetColor and 0xFF
        val lum = 0.299 * r + 0.587 * g + 0.114 * b
        return if (lum > 180) 0xFF222222.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun updateColorRow(picker: ColorPicker) {
        val strokePx = dpPx(3)
        val cornerR = 6 * d
        val isAuto = picker.selected == Prefs.HANDLE_COLOR_AUTO
        val isCustom = !isAuto && picker.selected !in picker.presets

        // Auto square
        picker.autoView?.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        ).apply {
            cornerRadius = cornerR
            setStroke(strokePx, if (isAuto) 0xFF44AAFF.toInt() else 0xFF666666.toInt())
        }

        // Preset squares
        for (i in picker.views.indices) {
            val color = picker.presets[i]
            val selected = !isAuto && !isCustom && color == picker.selected
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerR
                setColor(0xFF000000.toInt() or color)
                setStroke(
                    strokePx,
                    if (selected) selectionBorder(color) else 0xFF666666.toInt()
                )
            }
            picker.views[i].background = bg
        }

        // Custom square
        picker.customView?.background = if (isCustom) {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerR
                setColor(0xFF000000.toInt() or picker.selected)
                setStroke(strokePx, selectionBorder(picker.selected))
            }
        } else {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFFFF4444.toInt(), 0xFF44FF44.toInt(), 0xFF4444FF.toInt())
            ).apply {
                cornerRadius = cornerR
                setStroke(strokePx, 0xFF666666.toInt())
            }
        }
    }

    private fun showColorPickerDialog(currentColor: Int, onResult: (Int) -> Unit) {
        var r = (currentColor shr 16) and 0xFF
        var g = (currentColor shr 8) and 0xFF
        var b = currentColor and 0xFF

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpPx(24), dpPx(16), dpPx(24), dpPx(8))
        }

        val swatch = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(48)
            ).apply { bottomMargin = dpPx(16) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * d
                setColor(0xFF000000.toInt() or currentColor)
            }
        }
        container.addView(swatch)

        fun updateSwatch() {
            (swatch.background as GradientDrawable).setColor(
                0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            )
            swatch.invalidate()
        }

        fun addSlider(label: String, initial: Int, onChange: (Int) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val lbl = TextView(this).apply {
                text = label; textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valTv = TextView(this).apply {
                text = "$initial"; textSize = 12f; setTextColor(0xFF888888.toInt())
            }
            row.addView(lbl); row.addView(valTv)
            container.addView(row)
            container.addView(SeekBar(this).apply {
                max = 255; progress = initial
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpPx(8) }
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) { onChange(progress); updateSwatch(); valTv.text = "$progress" }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
        }

        addSlider("Red", r) { r = it }
        addSlider("Green", g) { g = it }
        addSlider("Blue", b) { b = it }

        AlertDialog.Builder(this)
            .setTitle(R.string.custom_colour_title)
            .setView(container)
            .setPositiveButton("OK") { _, _ -> onResult((r shl 16) or (g shl 8) or b) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSliders() {
        val seekInner = findViewById<SeekBar>(R.id.seekInnerAlpha)
        val seekBand = findViewById<SeekBar>(R.id.seekBandAlpha)
        val seekTab = findViewById<SeekBar>(R.id.seekTabAlpha)
        val seekFeather = findViewById<SeekBar>(R.id.seekFeather)

        // Initialise slider positions from stored alpha values (reverse log curve)
        seekInner.progress = Prefs.alphaToSlider(Prefs.getInnerAlpha(this))
        seekBand.progress = Prefs.alphaToSlider(Prefs.getBandAlpha(this))
        seekTab.progress = Prefs.alphaToSlider(Prefs.getTabAlpha(this))
        seekFeather.progress = Prefs.getFeather(this)

        seekInner.setOnSeekBarChangeListener(logAlphaListener(valInnerAlpha) { alpha ->
            Prefs.setInnerAlpha(this, alpha)
            notifyService(); updatePreview()
        })

        seekBand.setOnSeekBarChangeListener(logAlphaListener(valBandAlpha) { alpha ->
            Prefs.setBandAlpha(this, alpha)
            notifyService(); updatePreview()
        })

        seekTab.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    var alpha = Prefs.sliderToAlpha(progress)
                    if (alpha < Prefs.MIN_TAB_ALPHA) {
                        alpha = Prefs.MIN_TAB_ALPHA
                        seekTab.progress = Prefs.alphaToSlider(alpha)
                    }
                    valTabAlpha.text = alphaPct(alpha)
                    Prefs.setTabAlpha(this@SettingsActivity, alpha)
                    notifyService(); updatePreview()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekFeather.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    valFeather.text = "$progress%"
                    Prefs.setFeather(this@SettingsActivity, progress)
                    notifyService(); updatePreview()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun logAlphaListener(valueTv: TextView, onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val alpha = Prefs.sliderToAlpha(progress)
                    valueTv.text = alphaPct(alpha)
                    onChange(alpha)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }

    private fun updateAllValues() {
        valInnerAlpha.text = alphaPct(Prefs.getInnerAlpha(this))
        valBandAlpha.text = alphaPct(Prefs.getBandAlpha(this))
        valTabAlpha.text = alphaPct(Prefs.getTabAlpha(this))
        valFeather.text = "${Prefs.getFeather(this)}%"
    }

    private fun updatePreview() {
        preview.innerColor = Prefs.getInnerColor(this)
        preview.innerAlpha = Prefs.getInnerAlpha(this)
        preview.bandColor = Prefs.getBandColor(this)
        preview.bandAlpha = Prefs.getBandAlpha(this)
        preview.tabColor = Prefs.getTabColor(this)
        preview.tabAlpha = Prefs.getTabAlpha(this)
        preview.featherPx = Prefs.featherToPx(Prefs.getFeather(this), d)
        val hc = Prefs.getHandleColor(this)
        preview.handleColorOverride = hc
        preview.refresh()
    }

    private fun setupRememberToggle() {
        val sw = findViewById<SwitchCompat>(R.id.switchRemember)
        sw.isChecked = Prefs.getRememberPosition(this)
        sw.setOnCheckedChangeListener { _, checked ->
            Prefs.setRememberPosition(this, checked)
        }
    }

    private fun setupResetButton() {
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_confirm_title)
                .setMessage(R.string.reset_confirm_msg)
                .setPositiveButton("Reset") { _, _ -> performReset() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun performReset() {
        Prefs.resetAll(this)

        innerPicker.selected = Prefs.DEFAULT_INNER_COLOR
        bandPicker.selected = Prefs.DEFAULT_BAND_COLOR
        tabPicker.selected = Prefs.DEFAULT_TAB_COLOR
        handlePicker.selected = Prefs.DEFAULT_HANDLE_COLOR
        updateColorRow(innerPicker)
        updateColorRow(bandPicker)
        updateColorRow(tabPicker)
        updateColorRow(handlePicker)

        findViewById<SeekBar>(R.id.seekInnerAlpha).progress = Prefs.alphaToSlider(Prefs.DEFAULT_INNER_ALPHA)
        findViewById<SeekBar>(R.id.seekBandAlpha).progress = Prefs.alphaToSlider(Prefs.DEFAULT_BAND_ALPHA)
        findViewById<SeekBar>(R.id.seekTabAlpha).progress = Prefs.alphaToSlider(Prefs.DEFAULT_TAB_ALPHA)
        findViewById<SeekBar>(R.id.seekFeather).progress = Prefs.DEFAULT_FEATHER
        findViewById<SwitchCompat>(R.id.switchRemember).isChecked = true
        updateAllValues()
        notifyService(); updatePreview()
    }

    private fun notifyService() {
        if (OverlayService.isRunning) {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_REFRESH
            })
        }
    }
}
