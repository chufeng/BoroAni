package com.xyoye.common_component.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

object TvUi {
    fun isTelevision(context: Context): Boolean =
        (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun style(view: View) {
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        if (view.id == View.NO_ID) view.id = View.generateViewId()
        val density = view.resources.displayMetrics.density
        fun background(focused: Boolean) = GradientDrawable().apply {
            setColor(Color.parseColor(if (focused) "#245C85" else "#253346"))
            cornerRadius = 8 * density
            setStroke((if (focused) 3 * density else density).toInt(),
                Color.parseColor(if (focused) "#FFD166" else "#718096"))
        }
        view.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), background(true))
            addState(intArrayOf(), background(false))
        }
        if (view is TextView) view.setTextColor(Color.WHITE)
    }

    fun link(views: List<View>) {
        if (views.isEmpty()) return
        views.forEach { style(it) }
        views.forEachIndexed { index, view ->
            val previous = views[(index + views.size - 1) % views.size]
            val next = views[(index + 1) % views.size]
            view.nextFocusUpId = previous.id
            view.nextFocusDownId = next.id
            view.nextFocusLeftId = previous.id
            view.nextFocusRightId = next.id
            val editor = view as? EditText
            val originalKeyListener = editor?.keyListener
            val originalInputType = editor?.inputType
            fun stopEditing() {
                if (editor == null) return
                val input = editor.context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                input.hideSoftInputFromWindow(editor.windowToken, 0)
                editor.keyListener = null
                editor.isFocusable = true
                editor.isFocusableInTouchMode = true
            }
            if (editor != null) {
                editor.showSoftInputOnFocus = false
                stopEditing()
                editor.setOnFocusChangeListener { _, _ -> stopEditing() }
            }
            var editingKeyDown = false
            view.setOnKeyListener { _, key, event ->
                if (view is EditText && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) {
                    if (event.action == KeyEvent.ACTION_DOWN) editingKeyDown = true
                    if (event.action == KeyEvent.ACTION_UP && editingKeyDown && !event.isCanceled) {
                        editingKeyDown = false
                        view.keyListener = originalKeyListener
                        originalInputType?.let { view.setRawInputType(it) }
                        val input = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                        input.showSoftInput(view, 0)
                    }
                    true
                } else {
                    val target = when (key) {
                        KeyEvent.KEYCODE_DPAD_UP -> previous
                        KeyEvent.KEYCODE_DPAD_DOWN -> next
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (view is EditText) null else previous
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (view is EditText) null else next
                        else -> null
                    }
                    if (target == null) false else {
                        if (event.action == KeyEvent.ACTION_DOWN) target.requestFocus()
                        true
                    }
                }
            }
        }
    }

    fun prepareDialog(root: View) {
        val controls = mutableListOf<View>()
        fun collect(view: View) {
            if (view.visibility != View.VISIBLE || !view.isEnabled) return
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            } else if (view is EditText || view.isClickable) {
                controls.add(view)
            }
        }
        collect(root)
        link(controls)
        controls.firstOrNull()?.requestFocus()
    }
}
