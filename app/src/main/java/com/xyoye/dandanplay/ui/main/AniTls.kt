package com.xyoye.dandanplay.ui.main

import android.content.Context
import android.os.Build
import com.xyoye.dandanplay.R
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal object AniTls {
    fun configure(builder: OkHttpClient.Builder, context: Context): OkHttpClient.Builder {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) return builder
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        val factory = CertificateFactory.getInstance("X.509")
        listOf(R.raw.gts_root_r4, R.raw.isrg_root_x1).forEach { id ->
            val root = context.resources.openRawResource(id).use { factory.generateCertificate(it) as X509Certificate }
            require(root.basicConstraints >= 0 && root.subjectX500Principal == root.issuerX500Principal)
            root.verify(root.publicKey)
            store.setCertificateEntry(id.toString(), root)
        }
        val manager = combined(systemManager(null), systemManager(store))
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
        return builder.sslSocketFactory(ssl.socketFactory, manager)
    }

    private fun systemManager(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().single()

    internal fun combined(system: X509TrustManager, supplement: X509TrustManager): X509TrustManager =
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + supplement.acceptedIssuers
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    system.checkServerTrusted(chain, authType)
                    return
                } catch (systemFailure: CertificateException) {
                    try {
                        supplement.checkServerTrusted(chain, authType)
                    } catch (supplementFailure: CertificateException) {
                        systemFailure.addSuppressed(supplementFailure)
                        throw systemFailure
                    }
                }
            }
        }
}
