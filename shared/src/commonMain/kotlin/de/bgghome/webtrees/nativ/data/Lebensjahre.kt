package de.bgghome.webtrees.nativ.data

import de.bgghome.webtrees.nativ.api.LifespanGedcom

/**
 * Lebensjahre mit Kennzeichen fuer ungenaue Daten (Issue #44). webtrees bildet "1865–1918" aus dem fruehesten Geburts- und
 * dem spaetesten Sterbejahr und verliert dabei den Zusatz des Datums; api4webtrees liefert ab Stufe 44 beide Daten in
 * GEDCOM-Form mit (lifespanGedcom). Daraus:
 *   ABT, EST, CAL -> ~1918    BEF -> <1918    AFT -> >1918    BET 1915 AND 1918, FROM 1915 TO 1918 -> 1915/1918
 * Genaue Daten, INT und Datumstexte bleiben, wie webtrees sie zeigt. Ohne lifespanGedcom (aeltere Server) oder bei
 * unerwarteter Form des Textes bleibt er unveraendert.
 */
object Lebensjahre {

    fun markiert(lifespan: String, gedcom: LifespanGedcom?): String {
        if (gedcom == null || (gedcom.birth.isNullOrBlank() && gedcom.death.isNullOrBlank())) return lifespan
        val teile = lifespan.split('–')
        if (teile.size != 2) return lifespan
        return seite(teile[0], gedcom.birth) + "–" + seite(teile[1], gedcom.death)
    }

    /** Eine Seite der Lebensjahre ("1918", "…" oder leer) nach dem GEDCOM-Datum kennzeichnen. */
    internal fun seite(jahr: String, gedcom: String?): String {
        if (gedcom.isNullOrBlank() || jahr.none(Char::isDigit)) return jahr
        val d = gedcom.replace(Regex("@#D[^@]*@"), " ").replace(Regex("\\([^)]*\\)"), " ")
            .uppercase().replace(Regex("\\s+"), " ").trim()
        Regex("^(?:BET|FROM) (.+) (?:AND|TO) (.+)$").find(d)?.let { m ->
            val a = jahrAus(m.groupValues[1])
            val b = jahrAus(m.groupValues[2])
            return if (a != null && b != null && a != b) "$a/$b" else jahr
        }
        val j = jahr.trim()
        return when (d.substringBefore(' ')) {
            "ABT", "EST", "CAL" -> "~$j"
            "BEF" -> "<$j"
            "AFT" -> ">$j"
            else -> jahr
        }
    }

    /** Das Jahr eines einfachen Datums ("1 JAN 1865", "1750/51" -> 1750). */
    private fun jahrAus(s: String): String? = Regex("\\b\\d{3,4}\\b").findAll(s).lastOrNull()?.value
}
