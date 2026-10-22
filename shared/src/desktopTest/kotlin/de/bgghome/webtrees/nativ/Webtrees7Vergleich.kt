package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.desk.personenblattZeilen
import de.bgghome.webtrees.nativ.ui.DateInputState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test

/**
 * Was die App von demselben Stammbaum zeigt: webtrees 2.2.6 + api4webtrees 1.x gegen webtrees7 + api4webtrees fuer GEDCOM 7.0.
 * Liest die Antworten, die api4webtrees/tests/vergleich.py gesammelt hat (alt.json, neu.json), und schickt sie durch den
 * Code der App: Personenblatt (wie Anzeige, Kopieren, Druck) und Datumsfeld (Vorbelegen und unveraendert zuruecksenden).
 * Nur mit WEBTREES7_VERGLEICH=<Ordner>; das Ergebnis steht in <Ordner>/app.txt.
 */
class Webtrees7Vergleich {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun vergleichen() {
        val ordner = System.getenv("WEBTREES7_VERGLEICH") ?: return
        val alt = antworten(File(ordner, "alt.json"))
        val neu = antworten(File(ordner, "neu.json"))
        val bericht = StringBuilder()
        var personen = 0
        var blattAnders = 0
        var datumAnders = 0

        for (aufruf in alt.keys.filter { it.contains(" Individual xref=") }.sorted()) {
            val a = alt[aufruf] ?: continue
            val n = neu[aufruf] ?: run { bericht.appendLine("== $aufruf: fehlt neu"); continue }
            val da = runCatching { json.decodeFromJsonElement(IndividualDetail.serializer(), a) }
            val dn = runCatching { json.decodeFromJsonElement(IndividualDetail.serializer(), n) }
            if (da.isFailure || dn.isFailure) {
                bericht.appendLine("== $aufruf: nicht lesbar ${dn.exceptionOrNull()?.message ?: da.exceptionOrNull()?.message}")
                continue
            }
            personen++

            // Personenblatt: Zeile fuer Zeile
            val za = personenblattZeilen(da.getOrThrow()).map { "  ".repeat(it.einzug) + it.text }
            val zn = personenblattZeilen(dn.getOrThrow()).map { "  ".repeat(it.einzug) + it.text }
            if (za != zn) {
                blattAnders++
                bericht.appendLine("== $aufruf: Personenblatt")
                (za - zn.toSet()).forEach { bericht.appendLine("  - $it") }
                (zn - za.toSet()).forEach { bericht.appendLine("  + $it") }
                if (za.toSet() == zn.toSet()) {
                    val i = za.indices.firstOrNull { za[it] != zn.getOrNull(it) } ?: za.size
                    bericht.appendLine("  Reihenfolge/Anzahl (${za.size} -> ${zn.size}) ab Zeile $i: '${za.getOrNull(i)}' / '${zn.getOrNull(i)}'")
                }
            }

            // Datumsfeld: so vorbelegt, wie die App es tut; unveraendert gesendet waere es result
            val fa = da.getOrThrow().facts.associateBy { it.label + "|" + it.date?.text }
            for (f in dn.getOrThrow().facts) {
                val d = f.date ?: continue
                val vorher = fa[f.label + "|" + d.text]?.date ?: continue
                val ra = DateInputState(vorher.gedcom, vorher.text).result
                val rn = DateInputState(d.gedcom, d.text).result
                if (ra != rn) {
                    datumAnders++
                    bericht.appendLine("== $aufruf: Datumsfeld ${f.label}: alt '${vorher.gedcom}' -> sendet '$ra', neu '${d.gedcom}' -> sendet '$rn'")
                }
            }
        }

        val kopf = "$personen Personen (Admin und Gast), Personenblatt anders: $blattAnders, Datumsfeld anders: $datumAnders\n\n"
        File(ordner, "app.txt").writeText(kopf + bericht)
        println(kopf.trim())
    }

    private fun antworten(datei: File): Map<String, JsonObject> =
        json.parseToJsonElement(datei.readText()).jsonObject.mapValues { it.value.jsonObject }
}
