package com.xyoye.player.controller.base

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import com.xyoye.data_component.enums.PlayState
import com.xyoye.player.controller.video.InterGestureView
import com.xyoye.player.utils.TvSeekLog
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
    private var playState = PlayState.STATE_IDLE

    // Handle playback shortcuts before a focused control or Android's focus search sees them.
    fun dispatchPlaybackShortcut(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return handledKeys.remove(key)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val isSeekKey = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
        val logId = if (isSeekKey && TvSeekLog.enabled) TvSeekLog.nextId() else null
        logId?.let {
            val direction = if (key == KeyEvent.KEYCODE_DPAD_RIGHT) "right" else "left"
            TvSeekLog.record(it, "keydown direction=$direction repeat=${event.repeatCount}")
        }
        if (!isWrapperInitialized()) {
            logId?.let { TvSeekLog.record(it, "skipped reason=controller_not_ready") }
            return false
        }
        if (event.repeatCount == 0) handledKeys.remove(key)
        val blockedBy = when {
            isPopupMode() -> "popup"
            isLocked() -> "locked"
            mControlWrapper.isSettingViewShowing() -> "settings"
            else -> null
        }
        if (blockedBy != null) {
            logId?.let { TvSeekLog.record(it, "skipped reason=$blockedBy") }
            return key in handledKeys
        }
        if (navigatingControls && key in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER) {
            logId?.let { TvSeekLog.record(it, "skipped reason=control_navigation") }
            startFadeOut()
            return key in handledKeys
        }

        when (key) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val offset = if (key == KeyEvent.KEYCODE_DPAD_RIGHT) SEEK_STEP else -SEEK_STEP
                changePosition(offset, event.repeatCount == 0, logId)
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
        this.playState = playState
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

    private fun changePosition(offset: Long, notifyIfUnavailable: Boolean = true, logId: Int? = null) {
        val duration = mControlWrapper.getDuration()
        val canSeek = mControlWrapper.canSeek()
        if (!canSeek || duration <= 0) {
            logId?.let {
                TvSeekLog.record(it, "skipped reason=unavailable state=$playState canSeek=$canSeek durationMs=$duration")
            }
            remoteSeek.reset()
            if (notifyIfUnavailable) showMessage(context.getString(R.string.tv_seek_unavailable))
            return
        }
        val reportedPosition = mControlWrapper.getCurrentPosition()
        val seek = remoteSeek.step(reportedPosition, duration, offset, SystemClock.uptimeMillis())
        if (seek == null) {
            logId?.let { TvSeekLog.record(it, "skipped reason=at_boundary reportedMs=$reportedPosition durationMs=$duration") }
            return
        }
        logId?.let {
            TvSeekLog.record(it, "request reportedMs=$reportedPosition fromMs=${seek.from} targetMs=${seek.target} durationMs=$duration")
        }
        mControlWrapper.seekTo(seek.target, logId)

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
