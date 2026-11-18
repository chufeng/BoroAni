package com.xyoye.dandanplay.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class AniDiscoverRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val body = """{"data":{"Page":{"pageInfo":{"hasNextPage":false},"media":[{"id":42,"title":{"native":"原名"}}]}}}"""

    @Test fun freshCacheSkipsNetworkButExplicitRefreshDoesNot() = runBlocking {
        var calls = 0
        val repo = AniDiscoverRepository(folder.root) { _, _ -> calls++; body }
        assertEquals(DiscoverSource.NETWORK, repo.anilistRank(1, false).source)
        assertEquals(DiscoverSource.CACHE, repo.anilistRank(1, false).source)
        assertEquals(1, calls)
        assertEquals(DiscoverSource.NETWORK, repo.anilistRank(1, true).source)
        assertEquals(2, calls)
    }

    @Test fun failedRefreshRetainsCacheAndItsOriginalTimestamp() = runBlocking {
        var offline = false
        val repo = AniDiscoverRepository(folder.root) { _, _ ->
            if (offline) throw java.net.SocketTimeoutException("test")
            body
        }
        val online = repo.anilistRank(1, false)
        offline = true
        val result = repo.anilistRank(1, true)
        assertEquals(DiscoverSource.CACHE, result.source)
        assertEquals(online.fetchedAtMillis, result.fetchedAtMillis)
        assertEquals(online.value.items, result.value.items)
        assertNotNull(result.networkError)
        assertTrue(result.notice.contains("联网失败"))
    }

    @Test fun cancellationDoesNotFallBackToCachedResults() = runBlocking {
        var cancel = false
        val repo = AniDiscoverRepository(folder.root) { _, _ ->
            if (cancel) throw CancellationException("cancel")
            body
        }
        repo.anilistRank(1, false)
        cancel = true
        try {
            repo.anilistRank(1, true)
            fail("必须传播取消")
        } catch (_: CancellationException) { }
    }

    @Test fun httpFailureWithoutCacheKeepsStatusAndCause() = runBlocking {
        val failure = DiscoverHttpException(429)
        val repo = AniDiscoverRepository(folder.root) { _, _ -> throw failure }
        try {
            repo.anilistRank(1, false)
            fail("必须报告限流")
        } catch (e: IOException) {
            val httpFailure = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<DiscoverHttpException>().firstOrNull()
            assertSame(failure, httpFailure)
            assertTrue(e.message!!.contains("HTTP 429"))
            assertEquals(0, folder.root.listFiles()!!.size)
        }
    }

    @Test fun adjacentSeasonsDoNotShareCachedResults() = runBlocking {
        var calls = 0
        val repo = AniDiscoverRepository(folder.root) { _, _ ->
            calls++
            body.replace("42", (41 + calls).toString())
        }
        val autumn = DiscoverSeason(2026, 10)
        val winter = autumn.shift(1)
        assertEquals(42, repo.anilistSeason(autumn, 1, false).value.items.single().id)
        assertEquals(43, repo.anilistSeason(winter, 1, false).value.items.single().id)
        assertEquals(42, repo.anilistSeason(autumn, 1, false).value.items.single().id)
        assertEquals(2, calls)
    }

    @Test fun corruptCacheCannotMaskLiveSuccess() = runBlocking {
        val repo = AniDiscoverRepository(folder.root) { _, _ -> body }
        repo.anilistRank(1, true)
        folder.root.listFiles()!!.single().writeText("invalid")
        assertEquals(DiscoverSource.NETWORK, repo.anilistRank(1, false).source)
    }
}
