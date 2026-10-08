package de.bgghome.webtrees.nativ.api

import okhttp3.OkHttpClient
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/*
 * HTTPS mit eigener Zertifikatsstelle: lokale Server wie Laragon, ServBay oder XAMPP stellen sich selbst Zertifikate aus
 * und tragen ihre Stelle in den Zertifikatspeicher des Systems ein - der Browser vertraut ihnen, Java mit seinen eigenen
 * cacerts nicht. Am Desktop kommt darum der Systemspeicher dazu (Windows: "Windows-ROOT", macOS: Schluesselbund); Android
 * bleibt beim Systemverhalten.
 */

/** Dem Client den Zertifikatspeicher des Systems zusaetzlich mitgeben, wo es einen gibt. */
expect fun systemVertrauen(builder: OkHttpClient.Builder)

/** Erst die Java-Zertifikate, bei Ablehnung die des Systems; erst wenn beide ablehnen, scheitert die Verbindung. */
fun kombiniertesVertrauen(standard: X509TrustManager, system: X509TrustManager): X509TrustManager = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = standard.checkClientTrusted(chain, authType)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            standard.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (_: CertificateException) {
                throw e
            }
        }
    }
    override fun getAcceptedIssuers(): Array<X509Certificate> = standard.acceptedIssuers + system.acceptedIssuers
}
