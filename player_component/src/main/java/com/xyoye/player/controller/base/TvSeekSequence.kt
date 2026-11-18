package com.xyoye.player.controller.base

import kotlin.math.abs

internal class TvSeekSequence {
    data class Step(val from: Long, val target: Long)

    private var latestTarget: Long? = null
    private var lastSeekAt = 0L

    fun step(position: Long, duration: Long, offset: Long, now: Long): Step? {
        if (duration <= 0) return null
        val reported = position.coerceIn(0L, duration)
        // A burst of remote presses can arrive before the player reports its last seek.
        val from = latestTarget?.takeIf { now - lastSeekAt < 750 && abs(it - reported) > 1000 }
            ?.coerceIn(0L, duration) ?: reported
        val target = from + offset.coerceIn(-from, duration - from)
        if (target == from) return null
        latestTarget = target
        lastSeekAt = now
        return Step(from, target)
    }

    fun reset() {
        latestTarget = null
    }
}
