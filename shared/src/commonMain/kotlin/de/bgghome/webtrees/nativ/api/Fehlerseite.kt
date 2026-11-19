package de.bgghome.webtrees.nativ.api

/**
 * Der aussagekraeftige Teil einer Fehlerseite, die der Server statt JSON geliefert hat - damit die Meldung in der App
 * die Ursache nennt (PHP-Fehler mit Datei und Zeile, Speicher- oder Laufzeitgrenze) und nicht nur die HTTP-Nummer.
 * Bevorzugt den Fehlerblock von webtrees (<pre class="alert alert-danger">: erste Zeile = Meldung mit Datei und Zeile,
 * danach der Stacktrace), sonst eine Zeile mit einem PHP-Fehlerbegriff (Stacktrace-Zeilen "#n …" zaehlen nicht),
 * sonst der Seitentitel, sonst die erste Textzeile.
 */
object Fehlerseite {
    private val BEGRIFFE = listOf("Fatal error", "Allowed memory size", "Maximum execution time", "Uncaught", "Parse error", "Exception")
    private val TITEL = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val WEBTREES_BLOCK = Regex("<pre[^>]*alert-danger[^>]*>(.*?)</pre>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TRACE = Regex("^#\\d+ ")

    fun kern(html: String, maxLaenge: Int = 200): String? {
        if (html.isBlank()) return null
        val titel = TITEL.find(html)?.groupValues?.get(1)?.let(::text)?.takeIf { it.isNotBlank() }
        val zeilen = zeilen(html)
        val block = WEBTREES_BLOCK.find(html)?.groupValues?.get(1)?.let { zeilen(it).firstOrNull() }
        val treffer = zeilen.firstOrNull { z -> !TRACE.containsMatchIn(z) && BEGRIFFE.any { z.contains(it, ignoreCase = true) } }
        val wahl = block ?: treffer ?: titel ?: zeilen.firstOrNull() ?: return null
        return if (wahl.length > maxLaenge) wahl.take(maxLaenge - 1).trimEnd() + "…" else wahl
    }

    /** Die ganze Fehlerseite als Klartext (Tags weg, je Zeile ein Absatz), fuer die Details einer Meldung; null ohne Text. */
    fun klartext(html: String, maxLaenge: Int = 4000): String? {
        val t = zeilen(html).joinToString("\n").ifBlank { return null }
        return if (t.length > maxLaenge) t.take(maxLaenge - 1).trimEnd() + "…" else t
    }

    private fun zeilen(html: String): List<String> = html
        .replace(Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<br\\s*/?>|</(p|div|h[1-6]|li|title|pre|td|tr)>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .lines().map(::text).filter { it.isNotEmpty() }

    private fun text(s: String): String = s
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&ndash;", "–").replace("&mdash;", "—").replace("&raquo;", "»").replace("&laquo;", "«")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ").trim()
}
