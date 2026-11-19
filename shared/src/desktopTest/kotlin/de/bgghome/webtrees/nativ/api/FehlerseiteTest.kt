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

class FehlerseiteWebtreesTest {
    /** webtrees 2.2 (HandleExceptions): ganze Seite mit Menue, der Fehler steht als <pre class="alert alert-danger"> mit Meldung, Datei:Zeile und Stacktrace. */
    @Test fun webtreesFehlerblockNenntDieMeldungNichtDenTrace() {
        val html = """<html><head><title>Error – webtrees</title></head><body><nav><a>Meine Seite</a><a>Verwaltung</a></nav>
<pre class="alert alert-danger">Failed to set memory limit to 2097152 bytes (Current memory usage is 25165824 bytes) …/modules_v4/api4webtrees/src/PlaceActions.php:99
#0 [internal function]: Fisharebest\\Webtrees\\Http\\Middleware\\ErrorHandler-&gt;errorHandler(2, 'Failed to set m...', '/tmp/x', 99)
#55 …/app/Http/Middleware/HandleExceptions.php(81): Psr\\Http\\Server\\RequestHandlerInterface@anonymous-&gt;handle(Object(Nyholm\\Psr7\\ServerRequest))</pre></body></html>"""
        assertEquals("Failed to set memory limit to 2097152 bytes (Current memory usage is 25165824 bytes) …/modules_v4/api4webtrees/src/PlaceActions.php:99", Fehlerseite.kern(html))
    }

    /** Ohne Fehlerblock: Trace-Zeilen mit "Exception" im Namen sind kein Treffer. */
    @Test fun traceZeilenZaehlenNicht() {
        val html = "<p>#3 …/app/Http/Middleware/HandleExceptions.php(81): x</p><p>Uncaught TypeError: y in z:4</p>"
        assertEquals("Uncaught TypeError: y in z:4", Fehlerseite.kern(html))
    }
}

class FehlerDetailsTest {
    @Test fun klartextDerGanzenSeite() {
        val html = "<html><head><title>Fehler</title></head><body><h1>Oops</h1><p>Zeile eins</p><p>Zeile zwei</p></body></html>"
        assertEquals("Fehler\nOops\nZeile eins\nZeile zwei", Fehlerseite.klartext(html))
        assertNull(Fehlerseite.klartext(""))
    }

    @Test fun detailsNennenAdresseUndSeite() {
        val e = NotJsonException(500, serverText = "Fatal error", url = "https://h/webtrees226/index.php?route=/webtrees226/module/_api4webtrees_/Places/x&list=1", serverSeite = "Fatal error: x in y on line 3")
        val d = de.bgghome.webtrees.nativ.ui.details(e)
        assertTrue(d.startsWith("HTTP 500\nhttps://h/webtrees226/"), d)
        assertTrue(d.endsWith("on line 3"), d)
    }

    /** Die Fenster zeigen ueber meldung() den Servertext, nicht den rohen Ausnahmetext "Keine JSON-Antwort (HTTP 500)". */
    @Test fun meldungNenntServertextStattAusnahmetext() {
        val e = NotJsonException(500, serverText = "Fatal error: Allowed memory size of 134217728 bytes exhausted", url = "https://h/index.php")
        val m = de.bgghome.webtrees.nativ.ui.meldung(e)
        assertTrue(m.contains("Allowed memory size"), m)
        assertTrue(m.contains("500"), m)
        assertTrue(!m.contains("Keine JSON-Antwort"), m)
        assertEquals("?", de.bgghome.webtrees.nativ.ui.meldung(null))
    }
}
