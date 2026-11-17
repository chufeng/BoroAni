package com.xyoye.player_component

import com.xyoye.player.utils.VlcProxyServer
import org.junit.Assert.*
import org.junit.Test

class VlcProxyServerTest {
    @Test fun segmentsKeepTheirOwnPathAndQuery() {
        val base = "https://cdn.example.org/anime/playlist.m3u8?token=main"
        val segment = VlcProxyServer.upstreamUrl(base, "/anime/0000.ts", "sign=a+b%2Fc")
        assertEquals("https://cdn.example.org/anime/0000.ts?sign=a+b%2Fc", segment.toString())
        assertEquals("https://cdn.example.org/anime/0001.ts", VlcProxyServer.upstreamUrl(base, "/anime/0001.ts", null).toString())
    }

    @Test fun localRequestsCannotChangeUpstreamHost() {
        val base = "https://cdn.example.org/playlist.m3u8"
        assertEquals("cdn.example.org", VlcProxyServer.upstreamUrl(base, "/https://other.example.org/a.ts", null).host)
        assertTrue(runCatching { VlcProxyServer.upstreamUrl(base, "//other.example.org/a.ts", null) }.isFailure)
    }
}
