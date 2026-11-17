package com.xyoye.dandanplay.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniBiliRulesTest {
    private val timeline = """
        {"code":0,"message":"success","result":[
          {"date":"11-14","day_of_week":6,"episodes":[
            {"title":"小羊肖恩 第七季","pub_index":"第9话~第10话","pub_time":"10:00",
             "cover":"http://i0.hdslb.com/bfs/bangumi/a.jpg","season_id":355139,"episode_id":7702231,"follows":12000},
            {"title":"","season_id":1,"cover":"","pub_index":"","pub_time":""}
          ]},
          {"date":"11-15","day_of_week":7,"episodes":[
            {"title":"周日番","pub_index":"第1话","pub_time":"23:30",
             "cover":"//i0.hdslb.com/b.jpg","season_id":42,"episode_id":7,"follows":0}
          ]}
        ]}
    """.trimIndent()

    private val rank = """
        {"code":0,"result":{"list":[
          {"rank":1,"title":"名侦探柯南","season_id":33415,"rating":"8.8分",
           "cover":"http://i0.hdslb.com/c.png","new_ep":{"index_show":"更新至第1280话"},
           "stat":{"follow":2996551}},
          {"rank":2,"title":"","season_id":2,"rating":"","cover":""}
        ]}}
    """.trimIndent()

    private val season = """
        {"code":0,"data":{"total":1126,"list":[
          {"title":"鬼灭之刃 无限列车篇","subTitle":"鬼滅の刃","season_id":39444,
           "cover":"http://i0.hdslb.com/d.png","index_show":"全7话","order":"1458.2万追番",
           "score":"9.8","badge":"独家"}
        ]}}
    """.trimIndent()

    @Test fun timelineFiltersByWeekdayAndSkipsBlankRows() {
        val batch = AniDiscoverRules.biliTimeline(timeline, 6)
        assertEquals(1, batch.items.size)
        val item = batch.items.single()
        assertEquals(355139, item.id)
        assertEquals("小羊肖恩 第七季", item.title)
        assertEquals("11-14 10:00", item.date)
        assertTrue(item.summary.contains("第9话~第10话"))
        assertTrue(item.summary.contains("1.2万追番"))
        assertEquals("https://i0.hdslb.com/bfs/bangumi/a.jpg", item.imageUrl)
    }

    @Test fun timelineReturnsEmptyForDayWithoutEpisodes() {
        val batch = AniDiscoverRules.biliTimeline(timeline, 1)
        assertTrue(batch.items.isEmpty())
    }

    @Test fun rankParsesChineseScoreSuffixAndUpgradesCover() {
        val item = AniDiscoverRules.biliRank(rank).items.single()
        assertEquals(1, item.rank)
        assertEquals(8.8, item.score, 0.001)
        assertEquals("https://i0.hdslb.com/c.png", item.imageUrl)
        assertTrue(item.summary.contains("更新至第1280话"))
        assertTrue(item.summary.contains("299.7万追番"))
    }

    @Test fun seasonKeepsTotalAndProtocolRelativeCover() {
        val batch = AniDiscoverRules.biliSeason(season)
        assertEquals(1126, batch.total)
        val item = batch.items.single()
        assertEquals("鬼滅の刃", item.original)
        assertEquals(9.8, item.score, 0.001)
        assertEquals("https://i0.hdslb.com/d.png", item.imageUrl)
    }

    @Test fun protocolRelativeCoverBecomesHttps() {
        val item = AniDiscoverRules.biliTimeline(timeline, 7).items.single()
        assertEquals("https://i0.hdslb.com/b.jpg", item.imageUrl)
    }

    @Test fun apiErrorCodeIsReportedNotEmptyList() {
        val failed = """{"code":-404,"message":"啥都木有"}"""
        val error = runCatching { AniDiscoverRules.biliRank(failed) }.exceptionOrNull()
        assertTrue(error != null && error.message!!.contains("-404"))
    }
}
