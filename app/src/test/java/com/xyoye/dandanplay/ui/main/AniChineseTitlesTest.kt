package com.xyoye.dandanplay.ui.main

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AniChineseTitlesTest {
    private fun item(id: Int, original: String = "日本語の題名") =
        DiscoverSubject(id, "", original, "", "", 8.0, 1)

    @Test fun displayAndSearchPreferChineseAndPreserveJapanese() {
        val titles = AniChineseTitles.parse("""{"titles":{"12":["中文片名","备用原名"]}}""")
        val source = DiscoverBatch(listOf(item(12)), 20, 20)
        val result = titles.apply(source)
        val subject = result.items.single()
        assertEquals("中文片名", subject.title)
        assertEquals("中文片名", subject.searchTitle(false))
        assertEquals("日本語の題名", subject.searchTitle(true))
        assertEquals(20, result.total)
        assertEquals(20, result.received)
    }

    @Test fun missingTranslationKeepsJapaneseWithoutInventingChinese() {
        val titles = AniChineseTitles.parse("""{"titles":{"12":["中文片名","原名"]}}""")
        val result = titles.apply(DiscoverBatch(listOf(item(13)), 1, 1)).items.single()
        assertEquals("", result.chinese)
        assertEquals("日本語の題名", result.title)
        assertEquals(result.original, result.searchTitle(false))
    }

    @Test fun parserRejectsInvalidIdsBlankChineseAndMalformedEntries() {
        val titles = AniChineseTitles.parse("""{"titles":{"-1":["错误"],"abc":["错误"],"12":[" "],"13":"错误"}}""")
        val batch = DiscoverBatch(listOf(item(12), item(13)), 2, 2)
        assertEquals(batch, titles.apply(batch))
    }

    @Test fun bundledSnapshotMatchesKnownAniListIdAndKeepsRanking() {
        val file = File("src/main/assets/ani-discover/chinese-titles.json")
        val titles = AniChineseTitles.parse(file.readText())
        val subject = titles.apply(DiscoverBatch(listOf(item(5114)), 1, 1)).items.single()
        assertEquals("钢之炼金术师 FULLMETAL ALCHEMIST", subject.title)
        assertEquals(1, subject.rank)
        assertEquals(8.0, subject.score, 0.001)
    }
}
