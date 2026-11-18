package com.xyoye.dandanplay.ui.main

import org.junit.Assert.*
import org.junit.Test

class AniDiscoverRulesTest {
    @Test fun titleFallbackAndSearchNames() {
        val item = DiscoverSubject(1, " 中文 ", "Original", "", "", 0.0, 0)
        assertEquals("中文", item.searchTitle(false))
        assertEquals("Original", item.searchTitle(true))
        assertEquals("Original", item.copy(chinese = "").title)
        assertEquals("中文", item.copy(original = "").searchTitle(true))
        assertEquals("未命名条目 #1", item.copy(chinese = "", original = "").title)
        assertEquals("", item.copy(chinese = "", original = "").searchTitle(false))
    }
    @Test fun seasonBoundaries() {
        assertEquals(DiscoverSeason(2026, 10), AniDiscoverRules.season(2026, 11))
        assertEquals(DiscoverSeason(2026, 10), AniDiscoverRules.season(2026, 12))
        assertEquals(DiscoverSeason(2027, 1), AniDiscoverRules.season(2027, 1))
        assertEquals(DiscoverSeason(2026, 4), AniDiscoverRules.season(2026, 6))
        assertTrue(runCatching { AniDiscoverRules.season(2026, 13) }.isFailure)
    }
    @Test fun quarterSwitchesAcrossYearAtBeijingMidnight() {
        val firstMinuteOf2027 = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, java.util.Calendar.DECEMBER, 31, 16, 0)
        }.timeInMillis
        val autumn = AniDiscoverRules.currentSeason(firstMinuteOf2027 - 1)
        val winter = AniDiscoverRules.currentSeason(firstMinuteOf2027)
        assertEquals("2026年秋季", autumn.label)
        assertEquals("10–12月", autumn.months)
        assertEquals("2027年冬季", winter.label)
        assertEquals(autumn, winter.shift(-1))
        assertEquals(winter, autumn.shift(1))
        assertEquals("WINTER", AniListRules.seasonOf(autumn.shift(1).month))
    }
    @Test fun returnRoutesPreserveDetailAndSearchContext() {
        assertEquals("search", AniDiscoverRules.parent("episodes", "catalog", "metadata", "search"))
        assertEquals("metadata", AniDiscoverRules.parent("search", "catalog", "metadata", "search"))
        assertEquals("catalog", AniDiscoverRules.parent("metadata", "catalog", "metadata", "search"))
        assertEquals("search", AniDiscoverRules.parent("picker", "catalog", "metadata", "search"))
        assertEquals("discover", AniDiscoverRules.parent("help", "catalog", "metadata", "search"))
        assertNull(AniDiscoverRules.parent("discover", "catalog", "metadata", "search"))
    }
}
