package com.xyoye.dandanplay.ui.main

import org.junit.Assert.*
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class AniTlsTest {
    private class Manager(private val trusted: Boolean) : X509TrustManager {
        var calls = 0
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            calls++; if (!trusted) throw CertificateException("拒绝")
        }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            calls++; if (!trusted) throw CertificateException("拒绝")
        }
    }
    @Test fun preservesSystemTrust() {
        val system = Manager(true); val extra = Manager(false)
        AniTls.combined(system, extra).checkServerTrusted(emptyArray(), "RSA")
        assertEquals(1, system.calls); assertEquals(0, extra.calls)
    }
    @Test fun supplementsOnlyAfterSystemRejection() {
        val system = Manager(false); val extra = Manager(true)
        AniTls.combined(system, extra).checkServerTrusted(emptyArray(), "RSA")
        assertEquals(1, system.calls); assertEquals(1, extra.calls)
    }
    @Test fun rejectsWhenBothReject() {
        val manager = AniTls.combined(Manager(false), Manager(false))
        try { manager.checkServerTrusted(emptyArray(), "RSA"); fail("不得信任未知证书") }
        catch (e: CertificateException) { assertEquals(1, e.suppressed.size) }
    }
    @Test fun neverSupplementsClientTrust() {
        val extra = Manager(true)
        try { AniTls.combined(Manager(false), extra).checkClientTrusted(emptyArray(), "RSA"); fail() }
        catch (_: CertificateException) { assertEquals(0, extra.calls) }
    }
}
