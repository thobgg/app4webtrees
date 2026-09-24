package de.bgghome.webtrees.nativ

import com.sun.net.httpserver.HttpServer
import de.bgghome.webtrees.nativ.api.LoginWallException
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Eine SSO-Anmeldung vor webtrees (Authelia, Authentik, oauth2-proxy ...) darf nicht als "Modul zu alt" enden
 * (Rueckmeldung 24.09.2026). Der Testserver spielt den Proxy; localhost und 127.0.0.1 sind zwei Hosts.
 */
class LoginWallTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val port get() = server.address.port

    private class Speicher : Ablage {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as String? ?: default
        override fun putString(key: String, value: String?) { m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
        override fun leeren() = m.clear()
    }

    private fun antwort(status: Int, type: String, body: String, location: String? = null) {
        server.createContext("/") { ex ->
            location?.let { ex.responseHeaders.add("Location", it) }
            ex.responseHeaders.add("Content-Type", type)
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    private fun info(host: String = "127.0.0.1") = runBlocking {
        WtClient(Speicher(), Speicher(), "test").apply { baseUrl = "http://$host:$port" }.info()
    }

    private fun erwarteWall(host: String = "127.0.0.1") {
        try {
            info(host)
            fail("LoginWallException erwartet")
        } catch (e: LoginWallException) {
            // gewuenscht
        }
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun unauthorizedJsonIsLoginWall() {
        antwort(401, "application/json", """{"error":"unauthorized"}""")
        erwarteWall()
    }

    @Test
    fun foreignJsonIsLoginWallNotTooOld() {
        antwort(200, "application/json", """{"status":"ok","message":"please sign in"}""")
        erwarteWall()
    }

    @Test
    fun redirectToOtherHostIsLoginWall() {
        server.createContext("/sso") { ex ->
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(200, 5)
            ex.responseBody.use { it.write("login".toByteArray()) }
        }
        server.createContext("/") { ex ->
            ex.responseHeaders.add("Location", "http://localhost:$port/sso")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.start()
        erwarteWall()
    }

    @Test
    fun realInfoPasses() {
        antwort(200, "application/json", """{"api":3,"module":"1.7.0","csrf":"x"}""")
        val info = info()
        assertEquals(3, info.api)
        assertTrue(info.module.isNotEmpty())
    }
}
