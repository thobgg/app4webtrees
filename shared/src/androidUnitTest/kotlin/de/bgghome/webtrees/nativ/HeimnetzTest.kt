package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.Heimnetz
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** http:// nur im Heimnetz (nas4webtrees) - die Grenzen der Adressbereiche und Namen. */
class HeimnetzTest {
    @Test
    fun privateIpv4() {
        listOf("10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.254", "192.168.178.73", "127.0.0.1", "169.254.1.2")
            .forEach { assertTrue(it, Heimnetz.host(it)) }
    }

    @Test
    fun publicIpv4AtTheEdges() {
        listOf("172.15.255.255", "172.32.0.1", "192.169.0.1", "192.167.255.255", "11.0.0.1", "9.255.255.255", "169.255.0.1", "8.8.8.8", "256.1.1.1")
            .forEach { assertFalse(it, Heimnetz.host(it)) }
    }

    @Test
    fun ipv6() {
        listOf("::1", "[::1]", "fd12:3456::1", "fc00::1", "fe80::1", "FE80::abcd").forEach { assertTrue(it, Heimnetz.host(it)) }
        listOf("2001:db8::1", "2a00:1450::1", "fec0::1", "ff02::1").forEach { assertFalse(it, Heimnetz.host(it)) }
    }

    @Test
    fun names() {
        listOf("diskstation", "DiskStation", "nas.local", "nas.lan", "nas.home", "nas.home.arpa", "nas.internal", "nas.fritz.box", "nas.box", "nas.local.")
            .forEach { assertTrue(it, Heimnetz.host(it)) }
        listOf("evil.local.example.com", "example.org", "nas.localhost.example", "x.homes", "")
            .forEach { assertFalse(it, Heimnetz.host(it)) }
    }

    @Test
    fun resolvedOnlyWhenAllPrivate() {
        assertTrue(Heimnetz.hostAufgeloest("localhost"))
        assertFalse(Heimnetz.hostAufgeloest("example.invalid"))
    }
}
