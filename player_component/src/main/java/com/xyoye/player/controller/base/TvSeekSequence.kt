package com.xyoye.player.controller.base

internal class TvSeekSequence {
    data class Step(val from: Long, val target: Long)

    private var latestStep: Step? = null
    private var lastDirection = 0

    fun step(position: Long, duration: Long, offset: Long): Step? {
        if (duration <= 0) return null
        val reported = position.coerceIn(0L, duration)
        val direction = offset.compareTo(0L)
        // Keep accumulating until key-up. Some TV remotes repeat slower than 750 ms.
        val from = latestStep
            ?.takeIf { direction != 0 && direction == lastDirection }
            ?.target
            ?.coerceIn(0L, duration)
            ?: reported
        val target = from + offset.coerceIn(-from, duration - from)
        if (target == from) return null
        return Step(from, target).also {
            latestStep = it
            lastDirection = direction
        }
    }

    fun pending(): Step? = latestStep

    fun reset() {
        latestStep = null
        lastDirection = 0
    }
}
