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
import android.widget.*
import coil.ImageLoader
import coil.load
import coil.memory.MemoryCache
import com.xyoye.dandanplay.R
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object TvDesign {
    val paper = Color.parseColor("#F7F3EB")
    val ink = Color.parseColor("#253D3A")
    val muted = Color.parseColor("#596763")
    val teal = Color.parseColor("#176D65")
    val coral = Color.parseColor("#B64E3D")
    fun dp(c: Context, n: Int) = (c.resources.displayMetrics.density * n).toInt()
    /**
     * 卡片背景。用 InsetDrawable 内缩 2dp，让 3dp 焦点描边完整落在 View 内部；
     * 否则 stroke 居中画在边界上，外侧一半会被 View bounds 裁掉（表现为左边框和左圆角消失）。
     */
    fun background(c: Context, focused: Boolean = false, fill: Int? = null): Drawable {
        val shape = GradientDrawable().apply {
            setColor(fill ?: Color.parseColor(if (focused) "#E4F0E9" else "#FFFDFA"))
            cornerRadius = dp(c, 12).toFloat()
            setStroke(dp(c, if (focused) 3 else 1), if (focused) teal else Color.parseColor("#DDDCD2"))
        }
        return InsetDrawable(shape, dp(c, 2))
    }
    fun text(c: Context, value: String, size: Float = 22f, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(ink)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 10))
    }
    fun focus(view: View) {
        view.isFocusable = true; view.isFocusableInTouchMode = true
        if (view.id == View.NO_ID) view.id = View.generateViewId()
        val ctx = view.context
        // Drawable assignment can replace padding with the drawable's insets on Android 6.
        val left = view.paddingLeft; val top = view.paddingTop
        val right = view.paddingRight; val bottom = view.paddingBottom
        view.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), background(ctx, true))
            addState(intArrayOf(android.R.attr.state_selected), background(ctx, false, Color.parseColor("#EDF2E9")))
            addState(intArrayOf(), background(ctx, false))
        }
        view.setPadding(left, top, right, bottom)
        if (view is TextView) view.setTextColor(ink)
        view.setOnFocusChangeListener { v, has ->
            if (has) v.post { v.requestRectangleOnScreen(Rect(0, 0, v.width, v.height), false) }
        }
    }
    /**
     * 用 TextView 而不是 Button：系统 Button 的背景带水平 inset 与 elevation 阴影，
     * 在电视上会让卡片左侧被裁、右下出现灰边。自绘卡片可以完全控制边界。
     */
    fun button(c: Context, label: String, action: () -> Unit) = TextView(c).apply {
        text = label; textSize = 22f
        gravity = android.view.Gravity.CENTER_VERTICAL
        isSingleLine = false
        ellipsize = null
        includeFontPadding = false
        minHeight = dp(c, 60)
        setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12))
        focus(this)
        if (label.startsWith("搜索资源")) { setTextColor(coral); setTypeface(typeface, Typeface.BOLD) }
        setOnClickListener { action() }
    }
    fun hero(c: Context, title: String, subtitle: String): View = FrameLayout(c).apply {
        addView(ImageView(c).apply {
            setImageResource(R.drawable.tv_coast); scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(c, 20), dp(c, 16), dp(c, 12), dp(c, 12))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#FAF5EB"), Color.parseColor("#F2FAF5EB"), Color.TRANSPARENT))
            addView(text(c, "老爷机追番  /  TV", 16f, true).apply { setTextColor(teal); setPadding(dp(c, 8), 0, dp(c, 8), dp(c, 4)) })
            addView(text(c, title, 28f, true).apply { setPadding(dp(c, 8), 0, dp(c, 8), dp(c, 4)); ellipsize = null })
            addView(text(c, subtitle, 18f).apply { setPadding(dp(c, 8), 0, dp(c, 8), 0); ellipsize = null; maxWidth = dp(c, 440) })
        }, FrameLayout.LayoutParams(-1, -1))
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
        val image = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = title + "封面" }
        addView(image, FrameLayout.LayoutParams(-1, -1))
        val absent = text(c, "封面暂缺\n原创装饰图", 18f).apply {
            gravity = android.view.Gravity.CENTER; setBackgroundColor(Color.parseColor("#EFFFFAEE"))
        }
        addView(absent, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
        val snapshot = if (url.startsWith("https://")) {
            val key = java.security.MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            runCatching { c.assets.open("ani-discover/posters/$key.jpg").use {
                android.graphics.drawable.Drawable.createFromStream(it, null)
            } }.getOrNull()
        } else null
        fun showSnapshot() {
            absent.text = if (snapshot != null) "真实封面快照 · 非实时" else "封面暂缺\n原创装饰图"
            absent.visibility = View.VISIBLE
        }
        showSnapshot()
        image.load(url.takeIf { it.startsWith("https://") }, loader(c)) {
            size(360, 540); allowHardware(false); crossfade(false)
            if (snapshot != null) { placeholder(snapshot); error(snapshot); fallback(snapshot) }
            else { placeholder(R.drawable.tv_projector); error(R.drawable.tv_projector); fallback(R.drawable.tv_projector) }
            listener(onSuccess = { _, _ -> absent.visibility = View.GONE }, onError = { _, _ -> showSnapshot() })
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
