package com.xyoye.dandanplay.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AniSearchTest {
    private fun source(id: String) = AniSource(id, id, emptyMap())
    private val first = source("first")
    private val second = source("second")
    private val hit = listOf(AniItem("测试番剧", "https://example.org/subject/1"))

    @Test fun metadataShortensSeasonSubtitleAndLongTitles() {
        assertEquals(listOf("葬送的芙莉莲", "葬送的芙莉莲 第二季"), AniSearch.keywords("葬送的芙莉莲 第二季", true))
        assertEquals(listOf("测试番剧", "测试番剧：漫长的冒险故事"), AniSearch.keywords("测试番剧：漫长的冒险故事", true))
        assertEquals("这是一个非常漫长", AniSearch.keywords("这是一个非常漫长而且没有标点的番剧名称", true).first())
        assertEquals("Example Anime", AniSearch.keywords("Example Anime Season 2", true).first())
    }

    @Test fun manualSearchPreservesExactInputAndDeduplicates() {
        assertEquals(listOf("测试番剧 第二季", "测试番剧"), AniSearch.keywords("  测试番剧   第二季  "))
        assertEquals(listOf("86"), AniSearch.keywords("86", true))
        assertEquals(listOf("A: Subtitle"), AniSearch.keywords("A: Subtitle", true))
        assertEquals(emptyList<String>(), AniSearch.keywords("   "))
    }

    @Test fun exactHitStopsWithoutRequestingOtherSources() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val result = AniSearch({ s, q -> calls.add(s.id to q); hit }).find(listOf(second, first), first, "测试番剧 第二季")
        assertEquals(listOf(first.id to "测试番剧 第二季"), calls)
        assertEquals(first, result.source)
        assertEquals(hit, result.items)
    }

    @Test fun metadataTriesShortTitleFirstAndKeepsResultSource() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val result = AniSearch({ s, q ->
            calls.add(s.id to q)
            if (s == second && q == "测试番剧") hit else emptyList()
        }).find(listOf(second, first), first, "测试番剧 第二季", true)
        assertEquals(listOf(first.id to "测试番剧", first.id to "测试番剧 第二季", second.id to "测试番剧"), calls)
        assertEquals(second, result.source)
        assertEquals("测试番剧", result.keyword)
        assertEquals(listOf(0, 0, 1), result.attempts.map { it.count })
    }

    @Test fun fallbackIsBoundedAndSkipsUnsupportedAndDuplicateSources() = runBlocking {
        val sources = (1..6).map { source("s$it") }
        val unsupported = source("script").copy(unsupported = "不支持脚本")
        val result = AniSearch({ _, _ -> emptyList() }).find(listOf(unsupported, sources[0]) + sources, sources[0], "测试番剧 第二季")
        assertEquals(6, result.attempts.size)
        assertEquals(sources.take(3), result.attempts.map { it.source }.distinct())
        assertNull(result.source)
        assertTrue(result.items.isEmpty())
    }

    @Test fun unselectedOrUnsupportedSourceCannotBeSearched() = runBlocking {
        var calls = 0
        val engine = AniSearch({ _, _ -> calls++; hit })
        assertTrue(runCatching { engine.find(listOf(second), first, "测试") }.isFailure)
        assertTrue(runCatching { engine.find(listOf(first.copy(unsupported = "脚本")), first, "测试") }.isFailure)
        assertEquals(0, calls)
    }

    @Test fun transportErrorsSkipSameSourceAndRemainVisible() = runBlocking {
        val result = AniSearch({ s, _ -> if (s == first) throw IOException("HTTP 404") else hit })
            .find(listOf(first, second), first, "测试番剧 第二季")
        assertEquals(2, result.attempts.size)
        assertEquals("HTTP 404", result.attempts.first().error)
        assertEquals(second, result.source)
    }

    @Test fun attemptTimeoutFallsBackAndReportsError() = runBlocking {
        val result = AniSearch({ s, _ -> if (s == first) delay(10000); hit }, 100)
            .find(listOf(first, second), first, "测试")
        assertEquals("搜索超时", result.attempts.first().error)
        assertEquals(second, result.source)
    }

    @Test fun cancellationNeverSearchesAnotherSource() = runBlocking {
        var calls = 0
        try {
            AniSearch({ _, _ -> calls++; throw CancellationException("返回") })
                .find(listOf(first, second), first, "测试")
            fail("取消必须继续传播")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun outerDeadlineIsNotMistakenForAttemptTimeout() = runBlocking {
        var calls = 0
        try {
            withTimeout(100) {
                AniSearch({ _, _ -> calls++; delay(10000); hit }, 5000)
                    .find(listOf(first, second), first, "测试")
            }
            fail("外层超时必须继续传播")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }
}
