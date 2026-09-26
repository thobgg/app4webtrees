package de.bgghome.webtrees.nativ.api

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/*
 * Unverschluesselt (http://) nur im Heimnetz (Auftrag nas4webtrees, 26.09.2026): ein NAS unter
 * http://192.168.178.73:8095 soll ohne Zertifikat gehen, ein oeffentlicher Server weiter nur mit https.
 * Dieselbe Regel steht in api4webtrees (Seite "App"). HTTPS mit eigenem Zertifikat folgt spaeter.
 */
object Heimnetz {
    private val ENDUNGEN = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".fritz.box", ".box")

    /** Nach Name oder IP-Literal im Heimnetz - ohne DNS-Abfrage. */
    fun host(host: String): Boolean {
        val h = host.trim().trimEnd('.').lowercase().removePrefix("[").removeSuffix("]")
        if (h.isEmpty()) return false
        literal(h)?.let { return privat(it) }
        if ('.' !in h && ':' !in h) return true  // "diskstation"
        return ENDUNGEN.any { h.endsWith(it) }
    }

    /** Liegt die Adresse im Heimnetz (privat, Loopback, Link-Local, IPv6-ULA)? */
    fun privat(a: InetAddress): Boolean = when (a) {
        is Inet4Address -> a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress   // 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16
        is Inet6Address -> a.isLoopbackAddress || a.isLinkLocalAddress || (a.address[0].toInt() and 0xFE) == 0xFC  // ::1, fe80::/10, fc00::/7
        else -> false
    }

    /** Name oder Literal im Heimnetz, oder ein Name, der nur in private Adressen aufloest (eigenes DNS im LAN). Blockiert (DNS). */
    fun hostAufgeloest(host: String): Boolean =
        host(host) || runCatching { InetAddress.getAllByName(host).let { it.isNotEmpty() && it.all(::privat) } }.getOrDefault(false)

    /** IP-Literal ohne DNS: InetAddress.getByName loest nur bei Literalen nicht auf. */
    private fun literal(h: String): InetAddress? {
        val v4 = Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(h) && h.split('.').all { it.toInt() <= 255 }
        val v6 = ':' in h && h.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' }
        return if (v4 || v6) runCatching { InetAddress.getByName(h) }.getOrNull() else null
    }
}
