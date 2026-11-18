package com.xyoye.player.utils

import android.util.Log
import com.xyoye.player.info.PlayerInitializer
import java.util.concurrent.atomic.AtomicInteger

internal object TvSeekLog {
    private const val TAG = "BoroAniSeek"
    private val requestIds = AtomicInteger()

    val enabled: Boolean get() = PlayerInitializer.isPrintLog

    fun nextId() = requestIds.incrementAndGet()

    fun record(id: Int, message: String) {
        if (enabled) Log.i(TAG, "id=$id $message")
    }
}
