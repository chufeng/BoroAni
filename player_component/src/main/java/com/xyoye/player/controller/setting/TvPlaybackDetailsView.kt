package com.xyoye.player.controller.setting

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.xyoye.common_component.config.PlayerConfig
import com.xyoye.common_component.utils.TvUi
import com.xyoye.data_component.enums.PlayState
import com.xyoye.data_component.enums.SettingViewType
import com.xyoye.data_component.enums.VideoScreenScale
import com.xyoye.player.info.PlayerInitializer
import com.xyoye.player.utils.formatDuration
import com.xyoye.player.wrapper.ControlWrapper
import com.xyoye.player_component.R

internal class TvPlaybackDetailsView(context: Context) : FrameLayout(context), InterSettingView {
    private lateinit var controls: ControlWrapper
    private var previousFocus: View? = null
    private var playing = false
    private val speeds = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    private val scales = listOf(
        VideoScreenScale.SCREEN_SCALE_DEFAULT to R.string.action_aspect_default,
        VideoScreenScale.SCREEN_SCALE_16_9 to R.string.action_aspect_16_9,
        VideoScreenScale.SCREEN_SCALE_4_3 to R.string.action_aspect_4_3,
        VideoScreenScale.SCREEN_SCALE_MATCH_PARENT to R.string.action_aspect_match,
        VideoScreenScale.SCREEN_SCALE_CENTER_CROP to R.string.action_aspect_crop,
        VideoScreenScale.SCREEN_SCALE_ORIGINAL to R.string.action_aspect_origin
    )

    private val title = label(19f, Color.WHITE).apply { setTypeface(typeface, Typeface.BOLD) }
    private val time = label(16f, MUTED)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progressTintList = ColorStateList.valueOf(ACCENT)
    }
    private val play = action {
        if (playing) controls.pause() else controls.start()
    }
    private val speed = action {
        val current = PlayerInitializer.Player.videoSpeed
        val next = speeds.firstOrNull { it > current } ?: speeds.first()
        PlayerInitializer.Player.videoSpeed = next
        PlayerConfig.putNewVideoSpeed(next)
        controls.setSpeed(next)
        updateOptions()
    }
    private val aspect = action {
        val index = scales.indexOfFirst { it.first == PlayerInitializer.screenScale }
        val next = scales[(index + 1) % scales.size].first
        PlayerInitializer.screenScale = next
        controls.setScreenScale(next)
        updateOptions()
    }
    private val close = action { controls.hideSettingView() }.apply { setText(R.string.tv_playback_close) }
    private val actions = listOf(play, speed, aspect, close)
    private var playState = PlayState.STATE_IDLE

    init {
        visibility = View.GONE
        setBackgroundColor(0x50000000)
        setOnClickListener { controls.hideSettingView() }
        isFocusable = false

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            addView(label(17f, ACCENT).apply { setText(R.string.tv_playback_details) }, row(8))
            addView(title, row(12))
            addView(progress, row(8, dp(6)))
            addView(time, row(16))
            actions.forEach { addView(it, row(8)) }
        }
        actions.forEachIndexed { index, view ->
            view.nextFocusUpId = actions[(index + actions.lastIndex) % actions.size].id
            view.nextFocusDownId = actions[(index + 1) % actions.size].id
            view.nextFocusLeftId = view.id
            view.nextFocusRightId = view.id
        }
        addView(ScrollView(context).apply {
            setBackgroundColor(0xFF181D23.toInt())
            isFillViewport = true
            isClickable = true
            isFocusable = false
            addView(content)
        }, LayoutParams(dp(resources.configuration.screenWidthDp.coerceIn(1, 336)), LayoutParams.MATCH_PARENT, Gravity.END))
    }

    fun setVideoTitle(value: String?) {
        title.text = value.orEmpty()
        title.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    override fun attach(controlWrapper: ControlWrapper) {
        controls = controlWrapper
    }

    override fun getView() = this

    override fun getSettingViewType() = SettingViewType.TV_PLAYBACK_DETAILS

    override fun isSettingShowing() = visibility == View.VISIBLE

    override fun onSettingVisibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            previousFocus = rootView.findFocus()
            visibility = View.VISIBLE
            bringToFront()
            onProgressChanged(controls.getDuration(), controls.getCurrentPosition())
            updateOptions()
            play.requestFocus()
        } else {
            visibility = View.GONE
            previousFocus?.requestFocus()
            previousFocus = null
        }
    }

    override fun onPlayStateChanged(playState: PlayState) {
        this.playState = playState
        if (isSettingShowing()) updateOptions()
    }

    override fun onProgressChanged(duration: Long, position: Long) {
        if (!isSettingShowing()) return
        val knownDuration = duration > 0L
        progress.progress = if (knownDuration) {
            (position.coerceIn(0L, duration).toDouble() / duration * 100).toInt()
        } else 0
        time.text = context.getString(R.string.tv_playback_time, formatDuration(position.coerceAtLeast(0L)),
            if (knownDuration) formatDuration(duration) else "–:–")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
        else -> false
    }

    private fun updateOptions() {
        playing = playState == PlayState.STATE_PLAYING || playState == PlayState.STATE_BUFFERING_PLAYING ||
            (playState == PlayState.STATE_IDLE && controls.isPlaying())
        play.setText(if (playing) R.string.tv_playback_pause else R.string.tv_playback_resume)
        speed.text = context.getString(R.string.tv_playback_speed, PlayerInitializer.Player.videoSpeed.toString())
        val scale = scales.first { it.first == PlayerInitializer.screenScale }
        aspect.text = context.getString(R.string.tv_playback_aspect, context.getString(scale.second))
    }

    private fun label(size: Float, color: Int) = TextView(context).apply {
        textSize = size
        setTextColor(color)
    }

    private fun action(onClick: () -> Unit) = label(16f, Color.WHITE).apply {
        TvUi.style(this)
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { onClick() }
    }

    private fun row(bottom: Int, height: Int = LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val ACCENT = 0xFF91E2CC.toInt()
        val MUTED = 0xFFB3BBC3.toInt()
    }
}
