package com.xyoye.player.controller.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvSeekSequenceTest {
    @Test
    fun heldKeyKeepsAccumulatingEvenWhenRepeatsAreSlowOrKernelPositionIsStale() {
        val sequence = TvSeekSequence()
        assertEquals(30_000L, sequence.step(20_000, 100_000, 10_000)?.target)
        assertEquals(40_000L, sequence.step(20_100, 100_000, 10_000)?.target)
        assertEquals(50_000L, sequence.step(20_200, 100_000, 10_000)?.target)
        assertEquals(50_000L, sequence.pending()?.target)
    }

    @Test
    fun seeksStayInsideTheEpisodeAndRequireAKnownDuration() {
        val sequence = TvSeekSequence()
        assertNull(sequence.step(0, 0, 10_000))
        assertEquals(100_000L, sequence.step(97_000, 100_000, 10_000)?.target)
        assertNull(sequence.step(97_500, 100_000, 10_000))
        sequence.reset()
        assertEquals(0L, sequence.step(4_000, 100_000, -10_000)?.target)
    }

    @Test
    fun reversingDirectionUsesKernelPositionInsteadOfThePreviousPreview() {
        val sequence = TvSeekSequence()
        assertEquals(30_000L, sequence.step(20_000, 100_000, 10_000)?.target)
        assertEquals(24_000L, sequence.step(34_000, 100_000, -10_000)?.target)
    }
}
