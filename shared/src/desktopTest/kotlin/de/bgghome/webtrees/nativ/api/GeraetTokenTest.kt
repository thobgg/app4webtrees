package de.bgghome.webtrees.nativ.api

import com.sun.net.httpserver.HttpServer
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Geraete-Token (api4webtrees ab Stufe 47): nach abgelaufener Sitzung still neu anmelden, das naechste Token sofort
 * speichern, bei Ablehnung verwerfen (ausser device-busy), beim Abmelden am Server trennen, aeltere Module unberuehrt.
 * Der Testserver spielt die Routen nach docs/API.md des Moduls nach.
 */
class GeraetTokenTest {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private var api = 47
    private var angemeldet = false
    /** Gueltiges Token am Server; Einloesen liefert das naechste. */
    private var gueltig = ""
    private var zaehler = 0
    /** Antwort auf das naechste Einloesen statt des Normalfalls, z. B. "device-revoked". */
    private var fehler: String? = null
    private val aufrufe = mutableListOf<String>()
    /** Ein OTP-Modul faengt das Einloesen ab (Umleitung auf seine Bestaetigungsseite). */
    private var zweiteStufe = false

    init {
        server.createContext("/") { ex ->
            val route = java.net.URLDecoder.decode(ex.requestURI.rawQuery.orEmpty(), "UTF-8").split('&')
                .firstOrNull { it.startsWith("route=") }.orEmpty().substringAfterLast('/')
            val rumpf = ex.requestBody.readBytes().decodeToString()
            fun json(text: String) {
                val b = text.toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            fun neu(): String { gueltig = "%016x.%064x".format(1, ++zaehler); return gueltig }
            aufrufe += route
            when (route) {
                "Info" -> json("""{"api":$api,"module":"1.30.0","csrf":"C","user":{"loggedIn":$angemeldet,"userName":"anna"}}""")
                "login" -> { angemeldet = true; json("{}") }
                "logout" -> { angemeldet = false; json("{}") }
                "Device" -> when {
                    zweiteStufe && !rumpf.contains("deviceName") -> {
                        // OTP-Modul: Antwort ersetzt durch eine Umleitung auf seine Bestaetigungsseite
                        neu(); ex.responseHeaders.add("Location", "/index.php?route=/module/_otp-login_/Verify")
                        ex.sendResponseHeaders(302, -1); ex.close()
                    }
                    rumpf.contains("deviceName") -> json("""{"ok":true,"deviceToken":"${neu()}","user":"anna"}""")
                    fehler != null -> json("""{"ok":false,"error":"$fehler","status":403}""").also { fehler = null }
                    rumpf.contains("\"$gueltig\"") -> { angemeldet = true; json("""{"ok":true,"deviceToken":"${neu()}","user":"anna","csrf":"D"}""") }
                    else -> json("""{"ok":false,"error":"device-revoked","status":403}""")
                }
                "DeviceDelete" -> { gueltig = ""; json("""{"ok":true}""") }
                "Verify" -> { val bb = "<html>Code</html>".toByteArray(); ex.responseHeaders.add("Content-Type", "text/html"); ex.sendResponseHeaders(200, bb.size.toLong()); ex.responseBody.use { it.write(bb) } }
                else -> json("""{"ok":true}""")
            }
        }
        server.start()
    }

    @AfterTest fun stop() = server.stop(0)

    private val ablage = TokenAblage()
    private val adresse = "http://127.0.0.1:${server.address.port}"
    private fun client() = WtClient(ablage, TokenAblage(), userAgent = "wtTest/1").also { it.baseUrl = adresse }

    @Test fun passwortAnmeldungHoltTokenUndAbgelaufeneSitzungMeldetStillAn() = runBlocking {
        val c = client()
        assertTrue(c.login("anna", "geheim").user.loggedIn)
        val erstes = gueltig
        assertTrue(erstes.isNotEmpty(), "nach der Anmeldung gibt es ein Token")
        angemeldet = false                      // Server hat die Sitzung weggeraeumt
        assertTrue(client().infoMitGeraet().user.loggedIn, "still neu angemeldet, ohne Passwort")
        assertTrue(gueltig != erstes, "das Token hat gewechselt")
        angemeldet = false
        assertTrue(client().infoMitGeraet().user.loggedIn, "das gespeicherte ist das neue Token")
    }

    @Test fun abgelehntesTokenWirdVerworfenBesetztNicht() = runBlocking {
        client().login("anna", "geheim")
        angemeldet = false
        fehler = "device-busy"
        assertFalse(client().infoMitGeraet().user.loggedIn)
        assertTrue(client().infoMitGeraet().user.loggedIn, "device-busy: Token bleibt, naechster Versuch klappt")
        angemeldet = false
        fehler = "device-expired"
        assertFalse(client().infoMitGeraet().user.loggedIn)
        aufrufe.clear()
        assertFalse(client().infoMitGeraet().user.loggedIn)
        assertFalse("Device" in aufrufe, "verworfenes Token wird nicht mehr gesendet")
    }

    @Test fun abmeldenTrenntDasGeraetAmServer() = runBlocking {
        val c = client()
        c.login("anna", "geheim")
        c.logout()
        assertTrue("DeviceDelete" in aufrufe)
        assertEquals("", gueltig)
        aufrufe.clear()
        client().infoMitGeraet()
        assertFalse("Device" in aufrufe)
    }

    @Test fun aelteresModulOhneToken() = runBlocking {
        api = 46
        client().login("anna", "geheim")
        assertFalse("Device" in aufrufe, "Stufe 46 kennt die Route nicht")
        angemeldet = false
        assertFalse(client().infoMitGeraet().user.loggedIn)
    }

    @Test fun tokenGiltNurFuerSeinenServer() = runBlocking {
        client().login("anna", "geheim")
        angemeldet = false
        aufrufe.clear()
        val anderer = WtClient(ablage, TokenAblage(), userAgent = "wtTest/1").also { it.baseUrl = "http://localhost:${server.address.port}" }
        assertFalse(anderer.infoMitGeraet().user.loggedIn)
        assertFalse("Device" in aufrufe, "an einen anderen Server geht das Token nicht")
    }

    @Test fun zweiteAnmeldestufeVerwirftDasToken() = runBlocking {
        client().login("anna", "geheim")
        angemeldet = false
        zweiteStufe = true
        assertFalse(client().infoMitGeraet().user.loggedIn)
        aufrufe.clear()
        client().infoMitGeraet()
        assertFalse("Device" in aufrufe, "nach der Umleitung wird das Token nicht noch einmal eingeloest")
    }
}

private class TokenAblage : Ablage {
    private val m = mutableMapOf<String, Any>()
    override fun getString(key: String, default: String?) = m[key] as? String ?: default
    override fun putString(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    override fun getBoolean(key: String, default: Boolean) = m[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { m[key] = value }
    override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
    override fun leeren() = m.clear()
}
