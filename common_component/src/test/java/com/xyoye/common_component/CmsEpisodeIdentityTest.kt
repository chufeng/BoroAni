package com.xyoye.common_component

import com.xyoye.common_component.storage.helper.CmsEpisodeIdentity
import com.xyoye.common_component.storage.helper.CmsEpisodeParser
import org.junit.Assert.*
import org.junit.Test

class CmsEpisodeIdentityTest {
    @Test fun pathRoundTripKeepsReservedCharacters() {
        val id = CmsEpisodeIdentity("12/汉字", "name:a+b%", "第 1 集?#")
        assertEquals(id, CmsEpisodeIdentity.parse(id.path()))
        assertFalse(id.path().contains("https"))
    }

    @Test fun rejectsLegacyMalformedAndUnknownVersions() {
        listOf("cms://play/https%3A%2F%2Fx/旧番名",
            "cms://play/v3/a/b/c", "cms://play/v2/a/b",
            "cms://play/v2/a/b/%ZZ", "cms://play/v2/a/b/",
            "cms://play/v2/a/b/c?url=x").forEach {
            assertNull(it, CmsEpisodeIdentity.parse(it))
        }
    }

    @Test fun signatureAndSeriesNameAreNotIdentity() {
        val old = CmsEpisodeParser.lines("01\$https://x/1.m3u8?sign=old", "line").single().episodes.single()
        val id = CmsEpisodeIdentity("42", old.lineId, old.episodeId)
        val fresh = CmsEpisodeParser.lines("01\$https://x/1.m3u8?sign=new", "line")
        assertEquals("https://x/1.m3u8?sign=new", CmsEpisodeParser.resolve(id, fresh)?.url)
        assertEquals(id, CmsEpisodeIdentity.parse(id.path()))
        assertNotEquals(id.path(), id.copy(videoId = "43").path())
    }

    @Test fun reorderedLinesAndEpisodesKeepExactSelection() {
        val lines = CmsEpisodeParser.lines("02\$https://x/2.mp4#01\$https://x/1.mp4\$\$\$01\$https://x/other.m3u8", "A\$\$\$B")
        val id = CmsEpisodeIdentity("42", "name:A", "01")
        assertEquals("https://x/1.mp4", CmsEpisodeParser.resolve(id, lines)?.url)
        assertEquals("https://x/1.mp4", CmsEpisodeParser.resolve(id, lines.reversed())?.url)
        assertNull(CmsEpisodeParser.resolve(id.copy(episodeId = "missing"), lines))
        assertNull(CmsEpisodeParser.resolve(id.copy(lineId = "missing"), lines))
    }

    @Test fun ambiguousIdentitiesNeverChooseAnotherEpisode() {
        assertTrue(CmsEpisodeParser.lines("01\$https://x/a#01\$https://x/b", "A").single().episodes.isEmpty())
        assertTrue(CmsEpisodeParser.lines("01\$https://x/a\$\$\$01\$https://x/b", "A\$\$\$A").isEmpty())
        assertTrue(CmsEpisodeParser.lines("01\$https://x/a\$\$\$01\$https://x/b", "").isEmpty())
    }

    @Test fun singleUnnamedLineAndProtocolRelativeUrlsWork() {
        val lines = CmsEpisodeParser.lines("01\$//x/1.m3u8#02\$javascript:bad", "")
        assertEquals("https://x/1.m3u8", CmsEpisodeParser.resolve(CmsEpisodeIdentity("42", "single", "01"), lines)?.url)
        assertEquals(1, CmsEpisodeParser.preferred(lines).size)
    }
}
