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
    private val body = """{"total":1,"data":[{"id":42,"type":2,"name":"联网条目"}]}"""

    @Test fun bundledSnapshotNeverSkipsFirstNetworkRequest() = runBlocking {
        var calls = 0
        val repo = AniDiscoverRepository(folder.root, { body to "旧快照" }) { calls++; body }
        val result = repo.directory(2026, 10, 0, false)
        assertEquals(1, calls)
        assertEquals(DiscoverSource.NETWORK, result.source)
        assertNotNull(result.fetchedAtMillis)
        assertNull(result.networkError)
        assertEquals(1, folder.root.listFiles()!!.count { it.extension == "json" })
    }

    @Test fun freshCacheSkipsNetworkButExplicitRefreshDoesNot() = runBlocking {
        var calls = 0
        val repo = AniDiscoverRepository(folder.root, { null }) { calls++; body }
        repo.directory(null, null, 0, false)
        assertEquals(DiscoverSource.CACHE, repo.directory(null, null, 0, false).source)
        assertEquals(1, calls)
        assertEquals(DiscoverSource.NETWORK, repo.directory(null, null, 0, true).source)
        assertEquals(2, calls)
    }

    @Test fun failedRefreshRetainsCacheAndItsOriginalTimestamp() = runBlocking {
        var offline = false
        val repo = AniDiscoverRepository(folder.root, { null }) {
            if (offline) throw java.net.SocketTimeoutException("test")
            body
        }
        val online = repo.directory(null, null, 0, false)
        offline = true
        val result = repo.directory(null, null, 0, true)
        assertEquals(DiscoverSource.CACHE, result.source)
        assertEquals(online.fetchedAtMillis, result.fetchedAtMillis)
        assertNotNull(result.networkError)
        assertTrue(result.notice.contains("联网失败"))
    }

    @Test fun bundledFallbackIsExplicitAndNeverWrittenAsFreshCache() = runBlocking {
        val repo = AniDiscoverRepository(folder.root, { body to "快照 2026-11-15 UTC；非实时" }) {
            throw java.net.UnknownHostException("test")
        }
        val result = repo.directory(null, null, 0, false)
        assertEquals(DiscoverSource.BUNDLED, result.source)
        assertNull(result.fetchedAtMillis)
        assertEquals("DNS 解析失败", result.networkError)
        assertTrue(result.notice.contains("非实时"))
        assertEquals(0, folder.root.listFiles()!!.size)
    }

    @Test fun cancellationNeverReturnsSnapshot() = runBlocking {
        val repo = AniDiscoverRepository(folder.root, { body to "旧快照" }) { throw CancellationException("cancel") }
        try {
            repo.directory(null, null, 0, false)
            fail("必须传播取消")
        } catch (_: CancellationException) { }
    }

    @Test fun httpFailureWithoutCacheKeepsStatusAndCause() = runBlocking {
        val failure = DiscoverHttpException(429)
        val repo = AniDiscoverRepository(folder.root, { null }) { throw failure }
        try {
            repo.directory(null, null, 0, false)
            fail("必须报告限流")
        } catch (e: IOException) {
            val httpFailure = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<DiscoverHttpException>().firstOrNull()
            assertSame(failure, httpFailure)
            assertTrue(e.message!!.contains("HTTP 429"))
        }
    }

    @Test fun queriesPreserveDocumentedGetSortAndMonthParameters() = runBlocking {
        val paths = mutableListOf<String>()
        val repo = AniDiscoverRepository(folder.root, { null }) { paths.add(it); body }
        repo.directory(2026, 12, 20, true)
        repo.directory(null, null, 0, true)
        assertEquals("/v0/subjects?type=2&sort=date&year=2026&month=12&limit=20&offset=20", paths[0])
        assertEquals("/v0/subjects?type=2&sort=rank&limit=20&offset=0", paths[1])
        assertTrue(runCatching { repo.directory(2026, null, 0, true) }.isFailure)
        assertTrue(runCatching { repo.calendar(8, true) }.isFailure)
        assertEquals(2, paths.size)
    }

    @Test fun corruptCacheAndSnapshotCannotMaskLiveSuccess() = runBlocking {
        val repo = AniDiscoverRepository(folder.root, { "invalid" to "旧快照" }) { body }
        repo.directory(null, null, 0, true)
        folder.root.listFiles()!!.single().writeText("invalid")
        assertEquals(DiscoverSource.NETWORK, repo.directory(null, null, 0, false).source)
    }
}

// keep test bytecode in sync with repository constructor changes
