package com.xyoye.dandanplay.ui.main

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.View
import android.view.Gravity
import android.widget.*
import coil.ImageLoader
import coil.load
import coil.memory.MemoryCache
import com.xyoye.dandanplay.R
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object TvDesign {
    val paper = Color.parseColor("#141619")
    val ink = Color.parseColor("#F3F5F6")
    val muted = Color.parseColor("#B3BBC3")
    val accent = Color.parseColor("#91E2CC")
    val rating = Color.parseColor("#F0C37F")
    private val panel = Color.parseColor("#23272D")
    private val selected = Color.parseColor("#243C39")
    fun dp(c: Context, n: Int) = (c.resources.displayMetrics.density * n).toInt()
    private fun background(c: Context, fill: Int, focused: Boolean = false): Drawable {
        val shape = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(c, 8).toFloat()
            if (focused) setStroke(dp(c, 2), accent)
        }
        return InsetDrawable(shape, dp(c, 2))
    }
    fun text(c: Context, value: String, size: Float = 22f, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(ink)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(c, 3), 0, dp(c, 3))
    }
    fun focus(view: View, flat: Boolean = false, primary: Boolean = false) {
        view.isFocusable = true; view.isFocusableInTouchMode = true
        if (view.id == View.NO_ID) view.id = View.generateViewId()
        val ctx = view.context
        // Drawable assignment can replace padding with the drawable's insets on Android 6.
        val left = view.paddingLeft; val top = view.paddingTop
        val right = view.paddingRight; val bottom = view.paddingBottom
        view.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), background(ctx, if (primary) ink else selected, true))
            addState(intArrayOf(android.R.attr.state_selected), background(ctx, selected))
            addState(intArrayOf(), background(ctx, when { primary -> accent; flat -> Color.TRANSPARENT; else -> panel }))
        }
        view.setPadding(left, top, right, bottom)
        if (view is TextView) view.setTextColor(if (primary) paper else ink)
        view.setOnFocusChangeListener { v, has ->
            if (has) v.post { v.requestRectangleOnScreen(Rect(0, 0, v.width, v.height), false) }
        }
    }
    fun button(c: Context, label: String, flat: Boolean = false, primary: Boolean = false, action: () -> Unit) = TextView(c).apply {
        text = label; textSize = 18f
        gravity = Gravity.CENTER
        isSingleLine = false
        ellipsize = null
        includeFontPadding = false
        minHeight = dp(c, 44)
        setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9))
        focus(this, flat, primary)
        if (primary) setTypeface(typeface, Typeface.BOLD)
        setOnClickListener { action() }
    }

    fun destination(c: Context, label: String, icon: Int, active: Boolean, action: () -> Unit) =
        button(c, label, flat = true, action = action).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            isSelected = active
            val tint = if (active) accent else muted
            setTextColor(if (active) accent else ink)
            val glyph = c.getDrawable(icon)?.mutate()?.apply {
                setTint(tint)
                setBounds(0, 0, dp(c, 20), dp(c, 20))
            }
            setCompoundDrawablesRelative(glyph, null, null, null)
            compoundDrawablePadding = dp(c, 10)
        }

    fun brand(c: Context) = text(c, "老爷机追番", 20f, true).apply {
        setTextColor(accent)
        setPadding(dp(c, 10), dp(c, 6), 0, dp(c, 24))
    }

    fun divider(c: Context) = View(c).apply {
        setBackgroundColor(Color.parseColor("#33393F"))
        layoutParams = LinearLayout.LayoutParams(-1, dp(c, 1)).apply {
            topMargin = dp(c, 8); bottomMargin = dp(c, 8)
        }
    }
    private var loader: ImageLoader? = null
    private fun loader(c: Context): ImageLoader {
        loader?.let { return it }
        val app = c.applicationContext
        val dispatch = Dispatcher().apply { maxRequests = 3; maxRequestsPerHost = 3 }
        return ImageLoader.Builder(app).okHttpClient(
            AniTls.configure(OkHttpClient.Builder(), app).dispatcher(dispatch)
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS).followSslRedirects(false).build()
        ).memoryCache { MemoryCache.Builder(app).maxSizePercent(0.08).build() }
            .crossfade(false).build().also { loader = it }
    }
    fun poster(c: Context, url: String, title: String): View = object : FrameLayout(c) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val height = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) heightMeasureSpec
                else View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec) * 3 / 2, View.MeasureSpec.EXACTLY)
            super.onMeasure(widthMeasureSpec, height)
        }
    }.apply {
        background = GradientDrawable().apply { setColor(panel); cornerRadius = dp(c, 6).toFloat() }
        clipToOutline = true
        val placeholderImage = ImageView(c).apply {
            setImageResource(R.drawable.tv_poster_placeholder)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(c, 10), 0, dp(c, 10), 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(placeholderImage, FrameLayout.LayoutParams(-1, -1))
        val image = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = title + "封面" }
        addView(image, FrameLayout.LayoutParams(-1, -1))
        val absent = text(c, "暂无封面", 14f).apply {
            gravity = Gravity.CENTER; setBackgroundColor(Color.parseColor("#DC141619"))
        }
        addView(absent, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
        if (url.startsWith("https://")) {
            image.load(url, loader(c)) {
                size(288, 432); allowHardware(false); crossfade(false)
                listener(onSuccess = { _, _ ->
                    placeholderImage.visibility = View.GONE
                    absent.visibility = View.GONE
                })
            }
        }
    }
    fun editor(view: EditText) {
        val listener = view.keyListener
        val type = view.inputType
        val input = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        view.showSoftInputOnFocus = false
        view.keyListener = null
        view.isFocusable = true; view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    runCatching {
                        view.keyListener = listener; view.setRawInputType(type)
                        input.showSoftInput(view, 0)
                    }.onFailure { android.util.Log.w("TvDesign", "showSoftInput failed", it) }
                }
                true
            } else {
                if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    runCatching { input.hideSoftInputFromWindow(view.windowToken, 0) }
                    view.keyListener = null
                }
                false
            }
        }
    }
    fun wire(rows: List<List<View>>, navigation: List<View> = emptyList()) {
        val content = rows.filter { it.isNotEmpty() }
        val activeNavigation = navigation.firstOrNull { it.isSelected } ?: navigation.firstOrNull()
        navigation.forEachIndexed { i, v ->
            v.nextFocusUpId = navigation.getOrElse(i - 1) { v }.id
            v.nextFocusDownId = navigation.getOrElse(i + 1) { v }.id
            v.nextFocusLeftId = v.id
            v.nextFocusRightId = content.firstOrNull()?.firstOrNull()?.id ?: v.id
        }
        content.forEachIndexed { r, row -> row.forEachIndexed { col, v ->
            v.nextFocusLeftId = row.getOrNull(col - 1)?.id ?: activeNavigation?.id ?: v.id
            v.nextFocusRightId = row.getOrNull(col + 1)?.id ?: v.id
            v.nextFocusUpId = content.getOrNull(r - 1)?.let { it[col.coerceAtMost(it.lastIndex)] }?.id ?: v.id
            v.nextFocusDownId = content.getOrNull(r + 1)?.let { it[col.coerceAtMost(it.lastIndex)] }?.id ?: v.id
        } }
    }
}
