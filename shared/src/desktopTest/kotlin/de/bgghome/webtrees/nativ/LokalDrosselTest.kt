package de.bgghome.webtrees.nativ

import com.sun.net.httpserver.HttpServer
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import okhttp3.Request
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stammbaum auf diesem PC (Issue 9): an den lokalen Server hoechstens zwei Anfragen gleichzeitig, und bricht er eine
 * Leseanfrage ab, startet der Client ihn neu und wiederholt sie. Andere Server bleiben unberuehrt.
 */
class LokalDrosselTest {
    private class Speicher : Ablage {
        val werte = HashMap<String, String>()
        override fun getString(key: String, default: String?) = werte[key] ?: default
        override fun putString(key: String, value: String?) { if (value == null) werte.remove(key) else werte[key] = value }
        override fun getBoolean(key: String, default: Boolean) = werte[key]?.toBoolean() ?: default
        override fun putBoolean(key: String, value: Boolean) { werte[key] = value.toString() }
        override fun alle(): Map<String, String> = werte
        override fun leeren() = werte.clear()
    }

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 50).apply { executor = Executors.newFixedThreadPool(16) }
    private val gleichzeitig = AtomicInteger()
    private val hoechstens = AtomicInteger()
    private val abbrechen = AtomicInteger(0)
    private val neustarts = AtomicInteger()
    @Volatile private var neuerPort: Int? = null
    private val protokoll = java.util.Collections.synchronizedList(mutableListOf<String>())

    init {
        server.createContext("/") { ex ->
            val n = gleichzeitig.incrementAndGet()
            hoechstens.accumulateAndGet(n, ::maxOf)
            try {
                if (abbrechen.getAndDecrement() > 0) { ex.close(); return@createContext }  // ohne Antwort: Verbindung bricht ab
                Thread.sleep(80)
                val b = "ok".toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) }
            } finally { gleichzeitig.decrementAndGet() }
        }
        server.start()
    }

    @AfterTest
    fun aufraeumen() { WtClient.lokal = null; server.stop(0) }

    private fun client() = WtClient(Speicher(), Speicher(), "test").also { it.klartextUeberall = true }
    private fun url() = "http://127.0.0.1:${server.address.port}/x"
    private fun lokalSetzen() {
        WtClient.lokal = object : WtClient.LokalerDienst {
            override fun betrifft(host: String, port: Int) = host == "127.0.0.1" && port == server.address.port
            override fun neustarten(): Int? { neustarts.incrementAndGet(); return neuerPort ?: server.address.port }
            override fun protokoll(text: String) { protokoll += text }
        }
    }

    private fun zehnGleichzeitig(c: WtClient) {
        val pool = Executors.newFixedThreadPool(10)
        (1..10).map { pool.submit { c.http.newCall(Request.Builder().url(url()).build()).execute().use { assertEquals(200, it.code) } } }.forEach { it.get() }
        pool.shutdown()
    }

    @Test
    fun hoechstensZweiGleichzeitigAnDenLokalenServer() {
        lokalSetzen()
        zehnGleichzeitig(client())
        assertTrue(hoechstens.get() <= 2, "gleichzeitig: ${hoechstens.get()}")
    }

    @Test
    fun andereServerUngedrosselt() {
        zehnGleichzeitig(client())
        assertTrue(hoechstens.get() > 2, "gleichzeitig: ${hoechstens.get()}")
    }

    private fun holen(c: WtClient, url: String = url()) = c.http.newCall(Request.Builder().url(url).build()).execute().use { it.body!!.string() }

    @Test
    fun einAbbruchWirdSofortWiederholtOhneNeustart() {
        lokalSetzen()
        abbrechen.set(1)
        assertEquals("ok", holen(client()))
        assertEquals(0, neustarts.get())
        assertTrue(protokoll.any { "GET /x" in it } && protokoll.any { "zweiter Versuch ok" in it }, protokoll.toString())
    }

    @Test
    fun zweiAbbruecheFuehrenZuNeustartUndDrittemVersuch() {
        lokalSetzen()
        abbrechen.set(2)
        assertEquals("ok", holen(client()))
        assertEquals(1, neustarts.get())
        assertTrue(protokoll.any { "nach Neustart ok" in it }, protokoll.toString())
    }

    @Test
    fun anmeldungWirdWiederholtAndereSchreibzugriffeNicht() {
        lokalSetzen()
        // Wie writeHttp in WtClient: ohne OkHttps eigene Wiederholung, damit nur unsere Regel wirkt
        val c = client().http.newBuilder().retryOnConnectionFailure(false).build()
        abbrechen.set(1)
        val form = okhttp3.FormBody.Builder().add("a", "1").build()
        c.newCall(Request.Builder().url(url() + "?route=%2Flogin").post(form).build()).execute().use { assertEquals(200, it.code) }
        assertTrue(protokoll.any { "POST /login: zweiter Versuch ok" in it }, protokoll.toString())
        abbrechen.set(1)
        val fehler = runCatching { c.newCall(Request.Builder().url(url() + "?route=%2Fmodule%2Fx%2FFact").post(form).build()).execute().close() }
        assertTrue(fehler.isFailure, "ein abgebrochener Schreibzugriff darf nicht still wiederholt werden")
        assertEquals(0, neustarts.get())
    }

    @Test
    fun nachNeustartAufAnderemPortGehtDieWiederholungDorthin() {
        val zweiter = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 5)
        zweiter.createContext("/") { ex -> val b = "neu".toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) } }
        zweiter.start()
        try {
            lokalSetzen()
            neuerPort = zweiter.address.port
            abbrechen.set(2)
            assertEquals("neu", holen(client()))
            assertEquals(1, neustarts.get())
        } finally {
            zweiter.stop(0)
        }
    }

    @Test
    fun scheitertAuchDerNeustartKommtEineVerstaendlicheMeldung() {
        WtClient.lokal = object : WtClient.LokalerDienst {
            override fun betrifft(host: String, port: Int) = host == "127.0.0.1" && port == server.address.port
            override fun neustarten(): Int? = null
        }
        abbrechen.set(5)
        val e = runCatching { holen(client()) }.exceptionOrNull()
        assertTrue(e is de.bgghome.webtrees.nativ.api.LokalVerbindungException, "$e")
        assertTrue("wtwin.log" in de.bgghome.webtrees.nativ.ui.explain(e as Exception))
    }
}
