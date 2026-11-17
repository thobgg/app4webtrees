package de.bgghome.webtrees.nativ.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FehlerseiteTest {
    @Test fun phpFatalErrorMitDateiUndZeile() {
        val html = "<br />\n<b>Fatal error</b>:  Allowed memory size of 134217728 bytes exhausted (tried to allocate 20480 bytes) in <b>/var/www/webtrees/modules_v4/api4webtrees/src/PlaceActions.php</b> on line <b>962</b><br />\n"
        assertEquals(
            "Fatal error : Allowed memory size of 134217728 bytes exhausted (tried to allocate 20480 bytes) in /var/www/webtrees/modules_v4/api4webtrees/src/PlaceActions.php on line 962",
            Fehlerseite.kern(html),
        )
    }

    @Test fun laufzeitgrenzeInHtmlSeite() {
        val html = "<html><head><title>500 Internal Server Error</title><style>body{}</style></head><body><h1>Fehler</h1><p>Maximum execution time of 30 seconds exceeded in /x.php on line 5</p></body></html>"
        assertEquals("Maximum execution time of 30 seconds exceeded in /x.php on line 5", Fehlerseite.kern(html))
    }

    @Test fun nurTitelWennKeinFehlerbegriff() {
        val html = "<!DOCTYPE html><html><head><title>webtrees &ndash; Anmelden</title><script>var x = 'Fatal error';</script></head><body><div>Benutzername</div></body></html>"
        assertEquals("webtrees – Anmelden", Fehlerseite.kern(html))
    }

    @Test fun reinerText() = assertEquals("Service Unavailable", Fehlerseite.kern("Service Unavailable\nTry again later"))

    @Test fun leerGibtNull() {
        assertNull(Fehlerseite.kern(""))
        assertNull(Fehlerseite.kern("<html><body></body></html>"))
    }

    @Test fun kuerzt() {
        val k = Fehlerseite.kern("<p>Fatal error: " + "x".repeat(500) + "</p>")!!
        assertTrue(k.length <= 200 && k.endsWith("…"), k)
    }
}
