package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.kombiniertesVertrauen
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Zwei Zertifikatspeicher hintereinander: Java zuerst, dann das System; abgelehnt nur, wenn beide ablehnen. */
class VertrauenTest {
    private class Pruefer(private val vertraut: Boolean, private val name: String) : X509TrustManager {
        var gefragt = 0
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            gefragt++
            if (!vertraut) throw CertificateException("$name lehnt ab")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    @Test
    fun javaVertrautSystemWirdNichtGefragt() {
        val java = Pruefer(true, "java"); val system = Pruefer(true, "system")
        kombiniertesVertrauen(java, system).checkServerTrusted(emptyArray(), "RSA")
        assertEquals(1, java.gefragt); assertEquals(0, system.gefragt)
    }

    @Test
    fun systemRettetWasJavaAblehnt() {
        val java = Pruefer(false, "java"); val system = Pruefer(true, "system")
        kombiniertesVertrauen(java, system).checkServerTrusted(emptyArray(), "RSA")
        assertEquals(1, system.gefragt)
    }

    @Test
    fun beideLehnenAbMeldungVonJava() {
        val java = Pruefer(false, "java"); val system = Pruefer(false, "system")
        val e = assertFailsWith<CertificateException> { kombiniertesVertrauen(java, system).checkServerTrusted(emptyArray(), "RSA") }
        assertEquals("java lehnt ab", e.message)
    }
}
