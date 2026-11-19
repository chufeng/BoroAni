package com.xyoye.dandanplay.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TvPosterArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun posterIsFreshForNinetyDaysAndRemainsAvailableAsStaleFallback() {
        var clock = 1_700_000_000_000L
        val archive = TvPosterArchive(
            folder.newFolder("posters"),
            now = { clock },
            maxAgeMillis = RETENTION_MILLIS,
            maxBytes = 1024
        )
        archive.write("https://example.test/poster.jpg", "image".toByteArray())
        val cached = archive.fresh("https://example.test/poster.jpg")
        assertNotNull(cached)

        clock += RETENTION_MILLIS
        assertNotNull(archive.fresh("https://example.test/poster.jpg"))

        clock += 1
        assertNull(archive.fresh("https://example.test/poster.jpg"))
        assertEquals(cached, archive.stale("https://example.test/poster.jpg"))
    }

    @Test
    fun archiveRemovesOldestPostersToStayWithinSizeLimit() {
        var clock = 1_700_000_000_000L
        val archive = TvPosterArchive(
            folder.newFolder("bounded"),
            now = { clock },
            maxAgeMillis = RETENTION_MILLIS,
            maxBytes = 20
        )

        archive.write("one", ByteArray(10) { 1 })
        clock += 1000
        archive.write("two", ByteArray(10) { 2 })
        clock += 1000
        archive.write("three", ByteArray(10) { 3 })

        assertNull(archive.stale("one"))
        assertNotNull(archive.stale("two"))
        assertNotNull(archive.stale("three"))
    }

    @Test
    fun equivalentCoverUrlsReuseTheSameArchiveEntry() {
        val archive = TvPosterArchive(folder.newFolder("canonical"))
        archive.write("https://example.test/poster.jpg?width=480#one", "image".toByteArray())
        archive.write("anilist:42", "image".toByteArray())

        assertNotNull(archive.fresh("http://example.test/poster.jpg?width=960#two"))
        assertNotNull(archive.fresh("anilist:42"))
    }

    companion object {
        private const val RETENTION_MILLIS = 90L * 24L * 60L * 60L * 1000L
    }
}
