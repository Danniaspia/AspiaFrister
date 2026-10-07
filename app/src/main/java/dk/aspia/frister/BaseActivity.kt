package dk.aspia.frister

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Fælles byggeklodser til skærmene, der bygges i kode (ingen AndroidX). */
abstract class BaseActivity : Activity() {

    companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }

    protected lateinit var c: Palette
    protected val store by lazy { Store(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        c = Palette.of(this)
        window.statusBarColor = c.bg
        window.navigationBarColor = c.bg
        if (!c.dark) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    protected fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(28), dp(18), dp(32))
        setBackgroundColor(c.bg)
    }

    protected fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    protected fun section(title: String) = text(title.uppercase(), 12f, c.primary, bold = true).apply {
        letterSpacing = 0.1f
        setPadding(dp(4), dp(22), 0, dp(8))
    }

    protected fun card(topMargin: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(c.card, 20).apply { if (!c.dark) setStroke(dp(1), c.line) }
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { this.topMargin = dp(topMargin) }
    }

    protected fun weighted() = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) }

    protected fun chip(label: String, onClick: () -> Unit) = text(label, 14f, c.text).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        setPadding(dp(8), dp(11), dp(8), dp(11))
        setOnClickListener { onClick() }
    }

    protected fun styleChip(v: TextView, on: Boolean) {
        v.background = rounded(if (on) c.primary else c.tile, 14)
        v.setTextColor(if (on) c.onPrimary else c.muted)
        v.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    protected fun button(label: String, primary: Boolean = true, onClick: () -> Unit) =
        text(label, 15f, if (primary) c.onPrimary else c.primary, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(if (primary) c.primary else c.tile, 14)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setOnClickListener { onClick() }
        }

    protected fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    protected fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
