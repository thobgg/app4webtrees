package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.WtClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlTest {
    @Test
    fun addsHttpsAndTrims() {
        assertEquals("https://example.org/webtrees", WtClient.normalizeBaseUrl(" example.org/webtrees/ "))
        assertEquals("https://example.org/webtrees", WtClient.normalizeBaseUrl("https://example.org/webtrees/index.php?route=/tree/x"))
        assertEquals("https://example.org", WtClient.normalizeBaseUrl("HTTPS://example.org"))
        assertEquals("", WtClient.normalizeBaseUrl("  "))
    }

    @Test
    fun detectsCleartext() {
        assertTrue(WtClient.isCleartext("http://example.org/webtrees"))
        assertTrue(WtClient.isCleartext("  HTTP://example.org"))
        assertFalse(WtClient.isCleartext("https://example.org"))
        assertFalse(WtClient.isCleartext("example.org/webtrees"))
        assertFalse(WtClient.isCleartext("httpx.example.org"))
    }
}
