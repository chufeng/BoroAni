package com.xyoye.player.controller.base

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import com.xyoye.data_component.enums.PlayState
import com.xyoye.player.controller.video.InterGestureView
import com.xyoye.player_component.R

/**
 * Created by xyoye on 2021/5/30.
 */

abstract class TvVideoController(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseVideoController(context, attrs, defStyleAttr) {

    private val remoteSeek = TvSeekSequence()
    private val handledKeys = mutableSetOf<Int>()
    private var navigatingControls = false

    // Handle playback shortcuts before a focused control or Android's focus search sees them.
    fun dispatchPlaybackShortcut(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return handledKeys.remove(key)
        if (event.action != KeyEvent.ACTION_DOWN || !isWrapperInitialized()) return false
        if (event.repeatCount == 0) handledKeys.remove(key)
        if (isPopupMode() || isLocked() || mControlWrapper.isSettingViewShowing()) {
            return key in handledKeys
        }
        if (navigatingControls && key in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER) {
            startFadeOut()
            return key in handledKeys
        }

        when (key) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                changePosition(if (key == KeyEvent.KEYCODE_DPAD_RIGHT) SEEK_STEP else -SEEK_STEP, event.repeatCount == 0)
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU -> {
                if (event.repeatCount == 0) showPlaybackDetails()
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (key in handledKeys) return true
                navigatingControls = true
                showController(true)
                focusPlaybackControls()
            }
            else -> return false
        }
        handledKeys.add(key)
        return true
    }

    protected abstract fun showPlaybackDetails()

    protected abstract fun focusPlaybackControls()

    override fun hideController() {
        navigatingControls = false
        super.hideController()
    }

    override fun onPlayStateChanged(playState: PlayState) {
        super.onPlayStateChanged(playState)
        if (playState == PlayState.STATE_IDLE || playState == PlayState.STATE_PREPARING) {
            remoteSeek.reset()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isPopupMode()) {
            return false
        }
        val intercept = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> onActionCenter()
            KeyEvent.KEYCODE_DPAD_UP -> onActionUp()
            KeyEvent.KEYCODE_DPAD_DOWN -> onActionDown()
            KeyEvent.KEYCODE_DPAD_LEFT -> onActionLeft()
            KeyEvent.KEYCODE_DPAD_RIGHT -> onActionRight()
            else -> null
        }
        return if (intercept == null) {
            false
        } else if (intercept) {
            true
        } else {
            mControlWrapper.onKeyDown(keyCode, event)
        }
    }

    private fun onActionCenter(): Boolean {
        if (isLocked()) {
            showController(true)
            return true
        }
        if (mControlWrapper.isSettingViewShowing()) {
            return false
        }
        if (isControllerShowing()) {
            showController(true)
            return false
        }
        togglePlay()
        return true
    }

    private fun onActionUp(): Boolean {
        if (mControlWrapper.isSettingViewShowing()) {
            return false
        }
        showController(true)
        return false
    }

    private fun onActionDown(): Boolean {
        if (mControlWrapper.isSettingViewShowing()) {
            return false
        }
        showController(true)
        return false
    }

    private fun onActionLeft(): Boolean {
        if (mControlWrapper.isSettingViewShowing()) {
            return false
        }
        if (isLocked() || isControllerShowing()) {
            showController(true)
            return false
        }
        changePosition(-SEEK_STEP)
        return true
    }

    private fun onActionRight(): Boolean {
        if (mControlWrapper.isSettingViewShowing()) {
            return false
        }
        if (isLocked() || isControllerShowing()) {
            showController(true)
            return false
        }
        changePosition(SEEK_STEP)
        return true
    }

    private fun changePosition(offset: Long, notifyIfUnavailable: Boolean = true) {
        val duration = mControlWrapper.getDuration()
        if (!mControlWrapper.canSeek() || duration <= 0) {
            remoteSeek.reset()
            if (notifyIfUnavailable) showMessage(context.getString(R.string.tv_seek_unavailable))
            return
        }
        val seek = remoteSeek.step(mControlWrapper.getCurrentPosition(), duration, offset, SystemClock.uptimeMillis())
            ?: return
        mControlWrapper.seekTo(seek.target)

        for (entry in mControlComponents.entries) {
            val view = entry.key
            if (view is InterGestureView) {
                view.onStartSlide()
                view.onPositionChange(seek.target, seek.from, duration)
                view.onStopSlide()
            }
        }
    }

    private companion object {
        const val SEEK_STEP = 10_000L
    }
}
