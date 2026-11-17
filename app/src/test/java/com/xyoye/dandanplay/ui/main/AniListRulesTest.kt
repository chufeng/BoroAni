package com.xyoye.dandanplay.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListRulesTest {
    private val season = """
        {"data":{"Page":{"media":[
          {"id":195516,"title":{"romaji":"Kusuriya no Hitorigoto 3rd Season","native":"薬屋のひとりごと 第3期"},
           "coverImage":{"large":"https://s4.anilist.co/file/a.jpg"},"averageScore":85,"episodes":12,
           "status":"RELEASING","startDate":{"year":2026,"month":10,"day":2},
           "nextAiringEpisode":{"airingAt":1795183200,"episode":8}},
          {"id":0,"title":{"romaji":"","native":""},"coverImage":{"large":""},"averageScore":0}
        ]}}}
    """.trimIndent()

    private val rank = """
        {"data":{"Page":{"media":[
          {"id":1,"title":{"romaji":"A","native":"作品A"},"coverImage":{"large":"http://img/a.png"},
           "averageScore":92,"episodes":24,"status":"FINISHED","startDate":{"year":2004,"month":1}},
          {"id":2,"title":{"romaji":"B","native":""},"coverImage":{"large":"https://img/b.png"},
           "averageScore":90,"eps":13,"status":"FINISHED","startDate":{"year":2010,"month":4,"day":1}}
        ]}}}
    """.trimIndent()

    private val schedule = """
        {"data":{"Page":{"airingSchedules":[
          {"airingAt":1795183200,"episode":8,"media":{"id":195516,
           "title":{"romaji":"Kusuriya","native":"薬屋のひとりごと"},"coverImage":{"large":"https://img/c.jpg"},
           "averageScore":85,"episodes":12,"status":"RELEASING"}}
        ]}}}
    """.trimIndent()

    @Test fun seasonMapsNativeTitleScoreAndNextEpisode() {
        val batch = AniListRules.season(season)
        val item = batch.items.single()
        assertEquals(195516, item.id)
        assertEquals("薬屋のひとりごと 第3期", item.title)
        assertEquals("Kusuriya no Hitorigoto 3rd Season", item.original)
        assertEquals(8.5, item.score, 0.001)
        assertEquals("2026-10-02", item.date)
        assertEquals("https://s4.anilist.co/file/a.jpg", item.imageUrl)
        assertTrue(item.summary.contains("全 12 集"))
        assertTrue(item.summary.contains("连载中"))
        assertTrue(item.summary.contains("第 8 集"))
    }

    @Test fun rankAssignsSequentialRankAndUpgradesCoverScheme() {
        val items = AniListRules.rank(rank).items
        assertEquals(listOf(1, 2), items.map { it.rank })
        assertEquals(9.2, items[0].score, 0.001)
        assertEquals("https://img/a.png", items[0].imageUrl)
        assertEquals("作品A", items[0].title)
    }

    @Test fun rankFallsBackToRomajiWhenNativeMissing() {
        val items = AniListRules.rank(rank).items
        assertEquals("B", items[1].title)
        assertEquals("2010-04-01", items[1].date)
    }

    @Test fun scheduleUsesAiringTimeAndEpisode() {
        val item = AniListRules.schedule(schedule).items.single()
        assertEquals("薬屋のひとりごと", item.title)
        assertTrue(item.summary.contains("第 8 集"))
        assertTrue(item.date.contains(":"))
    }

    @Test fun graphqlErrorsAreSurfaced() {
        val failed = """{"errors":[{"message":"Too Many Requests"}]}"""
        val error = runCatching { AniListRules.season(failed) }.exceptionOrNull()
        assertTrue(error != null && error.message!!.contains("Too Many Requests"))
    }

    @Test fun queriesEmbedSeasonYearAndPaging() {
        val q = AniListRules.seasonQuery(2, "FALL", 2026)
        assertTrue(q.contains("season:FALL"))
        assertTrue(q.contains("seasonYear:2026"))
        assertTrue(q.contains("page:2"))
        assertTrue(AniListRules.rankQuery(1).contains("sort:SCORE_DESC"))
        assertTrue(AniListRules.scheduleQuery(1, 100, 200).contains("airingAt_greater:100"))
    }

    @Test fun seasonOfMapsMonthsToSeasons() {
        assertEquals("WINTER", AniListRules.seasonOf(1))
        assertEquals("SPRING", AniListRules.seasonOf(5))
        assertEquals("SUMMER", AniListRules.seasonOf(8))
        assertEquals("FALL", AniListRules.seasonOf(11))
    }
}
