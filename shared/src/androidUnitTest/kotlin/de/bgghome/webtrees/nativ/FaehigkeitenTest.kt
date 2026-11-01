package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.API_EXPORT
import de.bgghome.webtrees.nativ.api.API_SOURCES
import de.bgghome.webtrees.nativ.api.Info
import de.bgghome.webtrees.nativ.api.TreeInfo
import de.bgghome.webtrees.nativ.api.hat
import de.bgghome.webtrees.nativ.api.nachFaehigkeiten
import com.sun.net.httpserver.HttpServer
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** Info.features: Nennt der Server Faehigkeiten, zaehlen nur sie; sonst die Stufe in api. */
class FaehigkeitenTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val baum = TreeInfo("t", canEdit = true, canUpload = true, canModerate = true)

    @Test
    fun ohneFeaturesGiltDieStufe() {
        val info = json.decodeFromString(Info.serializer(), """{"api":18,"module":"1.25.0"}""")
        assertNull(info.features)
        assertTrue(info.hat(1) && info.hat(API_EXPORT) && info.hat(API_SOURCES))
        assertFalse(info.hat(19))
        assertEquals(listOf(baum), info.copy(trees = listOf(baum)).nachFaehigkeiten().trees)
    }

    @Test
    fun mitFeaturesZaehltNurDieListe() {
        // hohe Stufe, aber nur "read": nichts darueber hinaus
        val info = json.decodeFromString(Info.serializer(), """{"api":40,"module":"0.1.0","features":["read"]}""")
        assertTrue(info.hat(1))
        assertFalse(info.hat(2) || info.hat(API_EXPORT) || info.hat(API_SOURCES))
        val t = info.copy(trees = listOf(baum)).nachFaehigkeiten().trees.single()
        assertFalse(t.canEdit || t.canUpload || t.canModerate)
    }

    @Test
    fun featuresSchaltenAuchUeberDerStufeFrei() {
        // kleine Stufe, aber Fotos, Export und Schreiben gemeldet
        val info = Info(api = 1, features = listOf("read", "write", "photos", "export"), trees = listOf(baum))
        assertTrue(info.hat(2) && info.hat(API_EXPORT))
        assertFalse(info.hat(4) || info.hat(11))
        val t = info.nachFaehigkeiten().trees.single()
        assertTrue(t.canEdit && t.canUpload)
        assertFalse(t.canModerate)
    }

    @Test
    fun ohneInfoNichts() {
        assertFalse((null as Info?).hat(1))
    }

    private class Speicher : Ablage {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as String? ?: default
        override fun putString(key: String, value: String?) { m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
        override fun leeren() = m.clear()
    }

    /** Der Client nimmt canEdit aus Info und Person zurueck, wenn der Server "write" nicht nennt. */
    private fun personKannBearbeiten(features: String): Pair<Boolean, Boolean> {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val q = ex.requestURI.rawQuery.orEmpty()
            val body = if ("Individual" in q) """{"person":{"xref":"I1","name":"A"},"canEdit":true}"""
            else """{"api":1,"module":"0.1.0","csrf":"x"$features,"trees":[{"name":"t","canEdit":true}]}"""
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            return runBlocking {
                val c = WtClient(Speicher(), Speicher(), "test").apply { baseUrl = "http://127.0.0.1:${server.address.port}" }
                c.info().trees.single().canEdit to c.individual("t", "I1").canEdit
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun clientNimmtBearbeitenOhneWriteZurueck() {
        assertEquals(false to false, personKannBearbeiten(""","features":["read"]"""))
        assertEquals(true to true, personKannBearbeiten(""","features":["read","write"]"""))
        assertEquals(true to true, personKannBearbeiten(""))
    }
}
