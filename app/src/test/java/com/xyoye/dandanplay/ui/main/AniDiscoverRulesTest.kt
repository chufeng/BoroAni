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
    @Test fun directoryPreservesServerRankAndRawPaginationCount() {
        val result = AniDiscoverRules.directory("""{"total":8,"data":[{"id":326,"type":2,"name":"Original","name_cn":"中文","date":"2004-01-01","rating":{"rank":7,"score":9.2}},{"id":2,"type":1},{"id":3,"type":2,"nsfw":true},{"id":326,"type":2}]}""")
        assertEquals(4, result.received)
        assertEquals(8, result.total)
        assertEquals(1, result.items.size)
        assertEquals(7, result.items.single().rank)
        assertEquals("2004-01-01", result.items.single().date)
        assertTrue(runCatching { AniDiscoverRules.directory("{}") }.isFailure)
    }
    @Test fun calendarUsesWeekdayIdAndLegacyDateWithoutInventingRank() {
        val result = AniDiscoverRules.calendar("""[{"weekday":{"id":7},"items":[{"id":42,"type":2,"name":"原名","name_cn":"","air_date":"2026-10-04"}]}]""", 7)
        assertEquals("原名", result.items.single().title)
        assertEquals("2026-10-04", result.items.single().date)
        assertEquals(0, result.items.single().rank)
        assertTrue(runCatching { AniDiscoverRules.calendar("[]", 1) }.isFailure)
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
