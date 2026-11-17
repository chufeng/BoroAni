package com.xyoye.dandanplay.ui.main

import org.junit.Assert.*
import org.junit.Test

class AniRulesTest {
    private fun source(extra: Map<String, Any?> = emptyMap()) = AniSource("test", "测试源", mapOf("searchUrl" to "https://example.org/search?wd={keyword}") + extra)
    private fun rejected(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun realSubscriptionEnvelopesAndUnsupportedFactory() {
        val row = """{"factoryId":"web-selector","version":2,"id":"stable","arguments":{"name":"测试","searchConfig":{"searchUrl":"https://example.org/?wd={keyword}"}}}"""
        val modern = AniRules.subscription("""{"schemaVersion":2,"mediaSources":[$row]}""").single()
        val legacy = AniRules.subscription("""{"exportedMediaSourceDataList":{"mediaSources":[$row]}}""").single()
        assertEquals("stable", modern.id); assertEquals(modern, legacy); assertNull(modern.unsupported)
        assertNotNull(AniRules.subscription("""{"mediaSources":[{"factoryId":"json-api","version":1}]}""").single().unsupported)
        rejected { AniRules.subscription("""{"schemaVersion":3,"mediaSources":[]}""") }
        rejected { AniRules.subscription("""{"list":[]}""") }
    }
    @Test fun invalidRulesAreReportedBeforeSelection() {
        fun parsed(config: String) = AniRules.subscription("""{"mediaSources":[{"factoryId":"web-selector","version":2,"arguments":{"searchConfig":$config}}]}""").single()
        val valid = """{"searchUrl":"https://example.org/?q={keyword}","subjectFormatId":"json-path-indexed","selectorSubjectFormatJsonPathIndexed":{"selectNames":"$.info.rows[*].name","selectLinks":"$.info.rows[*].id"}}"""
        assertNull(parsed(valid).unsupported)
        assertNotNull(parsed(valid.replace("$.info.rows[*].name", "$..name")).unsupported)
        assertNotNull(parsed("""{"searchUrl":"https://example.org/?q={keyword}","subjectFormatId":"script"}""").unsupported)
        rejected { AniRules.subscription("""{"schemaVersion":"2","mediaSources":[]}""") }
        rejected { AniRules.subscription("""{"schemaVersion":2.5,"mediaSources":[]}""") }
    }
    @Test fun cookiesAndHeaderInjection() {
        val s = source(mapOf("matchVideo" to mapOf("cookies" to "quality=720")))
        assertEquals("quality=720", AniRules.headers(s, "https://example.org/")["Cookie"])
        rejected { AniRules.headers(source(mapOf("userAgent" to "bad\r\nX-Injected: true")), "https://example.org/") }
        assertNull(AniRules.video(source(), "javascript:alert(1)"))
    }
    @Test fun namedCaptureFallsBackToWholeUrlWhenUnmatched() {
        val s = source(mapOf("matchVideo" to mapOf("enableNestedUrl" to false, "matchVideoUrl" to "(?<v>https://example.org/a)|cdn")))
        assertEquals("https://cdn.example.org/a.m3u8", AniRules.video(s, "https://cdn.example.org/a.m3u8"))
    }
    @Test fun httpOnlyAndEncodedKeyword() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "ftp://example.org/a", "https://user:pass@example.org/a").forEach { value -> rejected { AniRules.http(value) } }
        assertEquals("https://example.org/a", AniRules.resolve("https://example.org/b/", "/a"))
        assertEquals("https://example.org/search?wd=%E5%8A%A8%E6%BC%AB%20A%26B", AniRules.searchUrl(source(), "动漫 A&B"))
        rejected { AniRules.resolve("https://example.org", "") }
    }
    @Test fun cssAnchorAndIndexedFormats() {
        val a = source(mapOf("subjectFormatId" to "a", "selectorSubjectFormatA" to mapOf("selectLists" to ".hit a", "linkTemplate" to "/detail/{value}")))
        val result = AniRules.subjects(a, "<div class='hit'><a href='42' title='番名'>别名</a></div>").single()
        assertEquals("番名", result.name); assertEquals("https://example.org/detail/42", result.url)
        val indexed = source(mapOf("subjectFormatId" to "indexed", "selectorSubjectFormatIndexed" to mapOf("selectNames" to "h3", "selectLinks" to "a")))
        assertEquals("条目", AniRules.subjects(indexed, "<h3>条目</h3><a href='/detail/1'>打开</a>").single().name)
    }
    @Test fun realGirigiriJsonPathAndLinkTemplateShape() {
        val rule = source(mapOf("rawBaseUrl" to "https://bg.girigirilove.com", "subjectFormatId" to "json-path-indexed", "selectorSubjectFormatJsonPathIndexed" to mapOf("selectNames" to "$.info.rows[*].vod_name", "selectLinks" to "$.info.rows[*].vod_id", "linkTemplate" to "/GV{value}/")))
        val item = AniRules.subjects(rule, """{"info":{"rows":[{"vod_id":5395,"vod_name":"测试"}]}}""").single()
        assertEquals("https://bg.girigirilove.com/GV5395/", item.url)
        assertEquals(listOf("备用"), AniRules.path(AniRules.decode("""[{"name":"备用"}]"""), "$[*]['title','name']"))
        rejected { AniRules.path(emptyList<Any>(), "$[?(@.id)]") }
        rejected { AniRules.path(emptyList<Any>(), "$..url") }
    }
    @Test fun groupedAndNoChannelEpisodes() {
        val s = source(mapOf("channelFormatId" to "index-grouped", "selectorChannelFormatFlattened" to mapOf("selectChannelNames" to "h3", "matchChannelName" to "^(?<ch>.+?)([0-9]+)?$", "selectEpisodeLists" to "ul", "selectEpisodesFromList" to "a")))
        val result = AniRules.episodes(s, "<h3>主线1</h3><ul><li><a href='/play/1'>第1集</a></li></ul><h3>备用2</h3><ul><a href='/play/2'>第2集</a></ul>")
        assertEquals(listOf("主线", "备用"), result.map { it.channel })
        assertEquals("https://example.org/play/2", result[1].url)
        val noChannel = source(mapOf("selectorChannelFormatNoChannel" to mapOf("selectEpisodes" to "b", "selectEpisodeLinks" to "a")))
        assertEquals("第1集", AniRules.episodes(noChannel, "<b>第1集</b><a href='/play/1'></a>").single().name)
    }
    @Test fun publicPlayerConfigDecodingPreservesSignedValues() {
        val url = "https://cdn.example.org/play.m3u8?sign=a+b%2Fc&x=1"
        val encoded = java.net.URLEncoder.encode(url, "UTF-8").replace("+", "%20")
        val base64 = java.util.Base64.getEncoder().encodeToString(encoded.toByteArray())
        listOf(0 to url, 1 to encoded, 2 to base64).forEach { (mode, value) ->
            val html = "<script>var player_aaaa={\"encrypt\":$mode,\"url\":\"$value\",\"vod_data\":{\"name\":\"brace }\"}};</script>"
            assertEquals(url, AniRules.pageCandidates("https://example.org/play/1", html).first.first())
        }
    }
    @Test fun fullyPercentEncodedPlayerAndNestedCapture() {
        val url = "https://cdn.example.org/playlist.m3u8?sign=a+b%2Fc"
        val encoded = url.toByteArray(Charsets.UTF_8).joinToString("") { "%%%02X".format(it.toInt() and 255) }
        val value = java.util.Base64.getEncoder().encodeToString(encoded.toByteArray())
        val html = "<script>var player_aaaa={\"encrypt\":2,\"trysee\":0,\"points\":0,\"url\":\"$value\"};</script>"
        assertEquals(url, AniRules.pageCandidates("https://example.org/play/1", html).first.first())
        val s = source(mapOf("matchVideo" to mapOf("matchNestedUrl" to "[?&]next=(?<v>https://example.org/embed[^ ]+)")))
        assertEquals("https://example.org/embed?id=1", AniRules.nestedUrl(s, "https://example.org/wrapper?next=https://example.org/embed?id=1"))
        assertNull(AniRules.nestedUrl(s, "https://example.org/unrelated"))
        assertNull(AniRules.nestedUrl(source(mapOf("matchVideo" to mapOf("enableNestedUrl" to false))), url))
    }
    @Test fun jsonEscapesFramesAndQuotedBody() {
        val html = """<script>const x={"url":"https:\/\/cdn.example.org\/a.m3u8?x=1\u0026sign=a+b%2Fc"}</script><iframe src="/embed?id=1&amp;x=2"></iframe><source src="//cdn.example.org/b.mp4">"""
        val candidates = AniRules.pageCandidates("https://example.org/play/1", html)
        assertTrue(candidates.first.contains("https://cdn.example.org/a.m3u8?x=1&sign=a+b%2Fc"))
        assertEquals(listOf("https://example.org/embed?id=1&x=2"), candidates.second)
        val quoted = com.squareup.moshi.Moshi.Builder().build().adapter(String::class.java).toJson(html)
        assertEquals(candidates, AniRules.pageCandidates("https://example.org/play/1", quoted))
    }
    @Test fun invalidPlayerConfigNeverExecutesOrReturnsLocalFiles() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "https://user:pass@example.org/a.m3u8").forEach { value ->
            val html = "<script>var player_x={\"url\":\"$value\"}</script>"
            assertTrue(AniRules.pageCandidates("https://example.org/", html).first.isEmpty())
        }
        assertTrue(AniRules.pageCandidates("https://example.org/", "<script>var player_x={\"encrypt\":2,\"url\":\"!!!\"}</script>").first.isEmpty())
        rejected { AniRules.pageCandidates("https://example.org/", "<script>var player_x={\"points\":5,\"url\":\"https://cdn.example.org/paid.mp4\"}</script>") }
    }
    @Test fun namedVideoCaptureHeadersAndNestedPriority() {
        val s = source(mapOf("userAgent" to "TestUA", "matchVideo" to mapOf("enableNestedUrl" to false, "matchVideoUrl" to "(?:^|[?&]url=)(?<v>https?://(?!.*https?://)[^ ]+\\.m3u8)$", "addHeadersToVideo" to mapOf("referer" to "https://example.org/", "userAgent" to "LegacyUA"))))
        assertEquals("https://cdn.example.org/a.m3u8", AniRules.video(s, "https://example.org/player?url=https://cdn.example.org/a.m3u8"))
        assertEquals("TestUA", AniRules.headers(s, "https://other.example.org/")["User-Agent"])
        assertEquals("https://example.org/", AniRules.headers(s, "https://other.example.org/")["Referer"])
        assertNull(AniRules.video(source(mapOf("matchVideo" to mapOf("enableNestedUrl" to true, "matchNestedUrl" to "player", "matchVideoUrl" to "m3u8"))), "https://example.org/player?url=https://cdn.example.org/a.m3u8"))
    }
}
