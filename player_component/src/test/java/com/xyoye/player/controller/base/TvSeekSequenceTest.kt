package com.xyoye.player.controller.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvSeekSequenceTest {
    @Test
    fun rapidPressesUseLatestTargetWhenPlayerReportsOldPosition() {
        val sequence = TvSeekSequence()
        assertEquals(30_000L, sequence.step(20_000, 100_000, 10_000, 1_000)?.target)
        assertEquals(40_000L, sequence.step(20_100, 100_000, 10_000, 1_150)?.target)
        assertEquals(30_000L, sequence.step(30_000, 100_000, -10_000, 1_300)?.target)
        assertEquals(44_000L, sequence.step(34_000, 100_000, 10_000, 2_100)?.target)
    }

    @Test
    fun seeksStayInsideTheEpisodeAndRequireAKnownDuration() {
        val sequence = TvSeekSequence()
        assertNull(sequence.step(0, 0, 10_000, 1_000))
        assertEquals(100_000L, sequence.step(97_000, 100_000, 10_000, 1_200)?.target)
        assertNull(sequence.step(97_500, 100_000, 10_000, 1_300))
        sequence.reset()
        assertEquals(0L, sequence.step(4_000, 100_000, -10_000, 1_400)?.target)
    }
}
