package com.xyoye.dandanplay.ui.main

import android.content.Context
import android.graphics.Rect
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal class TvSearchEditText(context: Context) : AppCompatEditText(context) {
    private val inputMethod = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private var imeWasVisible = false
    var isEditing = false
        private set
    var onEditingEnded: (() -> Unit)? = null

    private val imeVisibility = ViewTreeObserver.OnGlobalLayoutListener {
        if (isEditing) {
            val insets = ViewCompat.getRootWindowInsets(this)
            if (insets != null) {
                if (insets.isVisible(WindowInsetsCompat.Type.ime())) imeWasVisible = true
                else if (imeWasVisible) finishEditing(hideKeyboard = false)
            }
        }
    }

    init {
        showSoftInputOnFocus = false
        isCursorVisible = false
        setOnClickListener { beginEditing() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(imeVisibility)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(imeVisibility)
        finishEditing()
        super.onDetachedFromWindow()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) finishEditing()
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (isEditing && keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) finishEditing()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEditing && keyCode.isConfirm()) return true
        if (keyCode.isDirection()) {
            if (!isEditing) return false // Follow the page's focus links.
            super.onKeyDown(keyCode, event) // The IME has already had a chance to handle the key.
            return true // Keep unhandled keys from moving focus out of the editor.
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEditing && keyCode.isConfirm()) {
            if (!event.isCanceled) beginEditing()
            return true
        }
        return super.onKeyUp(keyCode, event) || (isEditing && keyCode.isDirection())
    }

    fun finishEditing(hideKeyboard: Boolean = true): Boolean {
        if (!isEditing) return false
        isEditing = false
        imeWasVisible = false
        isCursorVisible = false
        if (hideKeyboard) inputMethod.hideSoftInputFromWindow(windowToken, 0)
        onEditingEnded?.invoke()
        return true
    }

    private fun beginEditing() {
        if (isEditing || !requestFocus()) return
        isEditing = true
        isCursorVisible = true
        inputMethod.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun Int.isConfirm() = this == KeyEvent.KEYCODE_DPAD_CENTER || this == KeyEvent.KEYCODE_ENTER ||
        this == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun Int.isDirection() = this == KeyEvent.KEYCODE_DPAD_LEFT || this == KeyEvent.KEYCODE_DPAD_RIGHT ||
        this == KeyEvent.KEYCODE_DPAD_UP || this == KeyEvent.KEYCODE_DPAD_DOWN
}
