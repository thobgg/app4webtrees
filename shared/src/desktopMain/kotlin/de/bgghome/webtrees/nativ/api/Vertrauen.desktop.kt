package de.bgghome.webtrees.nativ.api

import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Windows: Speicher "Windows-ROOT" (Anbieter SunMSCAPI, Modul jdk.crypto.mscapi im Runtime - desktop/build.gradle.kts),
 * macOS: "KeychainStore". Linux hat keinen eigenen Speicher, dort bleibt es bei den cacerts (die Distributionen fuellen sie
 * aus /etc/ssl). Scheitert etwas, bleibt der Client, wie er ist.
 */
actual fun systemVertrauen(builder: OkHttpClient.Builder) {
    val os = System.getProperty("os.name").orEmpty()
    val typ = when {
        os.startsWith("Windows") -> "Windows-ROOT"
        os.startsWith("Mac") -> "KeychainStore"
        else -> return
    }
    runCatching {
        val system = KeyStore.getInstance(typ).apply { load(null, null) }
        val standard = trustManager(null)
        val eigene = trustManager(system)
        val kombiniert = kombiniertesVertrauen(standard, eigene)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(kombiniert), null) }
        builder.sslSocketFactory(ssl.socketFactory, kombiniert)
    }.onFailure { System.err.println("Zertifikatspeicher $typ nicht nutzbar: ${it.message}") }
}

private fun trustManager(speicher: KeyStore?): X509TrustManager =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(speicher) }
        .trustManagers.filterIsInstance<X509TrustManager>().first()
