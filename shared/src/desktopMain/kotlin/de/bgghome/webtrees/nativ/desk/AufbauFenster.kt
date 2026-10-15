package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import de.bgghome.webtrees.nativ.gedcom.GedcomDatei
import de.bgghome.webtrees.nativ.gedcom.GedcomKnoten
import de.bgghome.webtrees.nativ.gedcom.GedcomLeser
import de.bgghome.webtrees.nativ.res.*
import org.jetbrains.compose.resources.stringResource

/*
 * Aufbau der Datei (gedaudit, 13.10.2026): der Tag-Baum je Datensatzart mit Haeufigkeiten, wie ihn Kenner zum
 * Vergleich zwischen Programmen brauchen. Hersteller-Tags (Unterstrich) und Tags ausserhalb von 5.5.1/7 sind markiert.
 */

/** Eine Zeile des Baums: Pfad (INDI > BIRT > DATE), Einrueckung, Anzahl, Art. */
internal class AufbauZeile(val tag: String, val tiefe: Int, val anzahl: Int, val art: Art) {
    enum class Art { Standard, GedcomL, Hersteller, Fremd, Nur551, Nur7 }
}

/** [sieben]: als GEDCOM 7 gelesen; dann sind Tags, die es nur in 5.5.1 gibt, markiert (und umgekehrt). */
internal fun aufbauZeilen(d: GedcomDatei, sieben: Boolean = d.istSieben): List<Pair<String, List<AufbauZeile>>> {
    // je Datensatzart ein Baum aus Pfaden
    class Knoten { var anzahl = 0; val kinder = LinkedHashMap<String, Knoten>() }
    val wurzeln = LinkedHashMap<String, Knoten>()
    fun zaehle(k: GedcomKnoten, ziel: Knoten) { k.kinder.forEach { n -> val z = ziel.kinder.getOrPut(n.tag) { Knoten() }; z.anzahl++; zaehle(n, z) } }
    d.saetze.forEach { s -> val w = wurzeln.getOrPut(s.art) { Knoten() }; w.anzahl++; zaehle(s.knoten, w) }
    fun art(tag: String) = when {
        tag in GedcomLeser.GEDCOML_TAGS -> AufbauZeile.Art.GedcomL
        tag.startsWith("_") -> AufbauZeile.Art.Hersteller
        sieben && tag in GedcomLeser.STANDARD_TAGS && tag !in GedcomLeser.STANDARD_70 -> AufbauZeile.Art.Nur551
        !sieben && tag in GedcomLeser.STANDARD_TAGS && tag !in GedcomLeser.STANDARD_551 -> AufbauZeile.Art.Nur7
        tag in GedcomLeser.STANDARD_TAGS -> AufbauZeile.Art.Standard
        else -> AufbauZeile.Art.Fremd
    }
    fun flach(k: Knoten, tiefe: Int, out: MutableList<AufbauZeile>) {
        k.kinder.entries.sortedByDescending { it.value.anzahl }.forEach { (tag, z) -> out += AufbauZeile(tag, tiefe, z.anzahl, art(tag)); flach(z, tiefe + 1, out) }
    }
    val reihenfolge = listOf("HEAD", "INDI", "FAM", "SOUR", "REPO", "NOTE", "SNOTE", "OBJE", "SUBM")
    return wurzeln.entries.sortedWith(compareBy({ reihenfolge.indexOf(it.key).let { i -> if (i < 0) 99 else i } }, { it.key })).map { (art, w) ->
        art to buildList { add(AufbauZeile(art, 0, w.anzahl, art(art))); flach(w, 1, this) }
    }
}

@Composable
fun AufbauFenster(d: GedcomDatei, dateiName: String, sieben: Boolean = d.istSieben, onClose: () -> Unit) {
    val zeilen = remember(d, sieben) { aufbauZeilen(d, sieben) }
    val kopf = remember(d) {
        val k = d.kopf
        // GEDCOM-L-Angabe im Kopf: 1 GEDC / 2 VERS 5.5.1 / 3 _ADDENDUM GEDCOM-L / 4 VERS 2.0
        val addendum = k?.erstes("GEDC")?.erstes("VERS")?.erstes("_ADDENDUM")?.let { a -> listOf(a.wert.ifBlank { "GEDCOM-L" }, a.wert("VERS").orEmpty()).filter(String::isNotBlank).joinToString(" ") }
        if (k == null) null else listOfNotNull(k.wert("SOUR")?.let { s -> s + (k.erstes("SOUR")?.wert("VERS")?.let { " $it" } ?: "") }, k.erstes("GEDC")?.wert("VERS")?.let { "GEDCOM $it" }, addendum, k.wert("CHAR")).joinToString(" · ")
    }
    Window(
        onCloseRequest = onClose, title = stringResource(Res.string.ga_structure) + " – " + dateiName,
        state = arbeitsfenster("ga_aufbau", 760, 760),
        onPreviewKeyEvent = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                Text(stringResource(Res.string.ga_structure), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(Res.string.ga_structure_records, d.saetze.size) + "  ·  " + stringResource(Res.string.ga_charset, d.befund.zeichensatz) +
                    (kopf?.let { "  ·  " + stringResource(Res.string.ga_structure_head, it) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.ga_structure_hint), Modifier.padding(top = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val liste = rememberLazyListState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize(), state = liste) {
                        zeilen.forEach { (art, rows) ->
                            item(key = "k$art") { Spacer(Modifier.height(10.dp)) }
                            items(rows, key = { "$art/${it.tiefe}/${it.tag}/${rows.indexOf(it)}" }) { z ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width((z.tiefe * 22).dp))
                                    Text(z.tag, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                                        fontWeight = if (z.tiefe == 0) FontWeight.Bold else FontWeight.Normal,
                                        color = when (z.art) { AufbauZeile.Art.Fremd, AufbauZeile.Art.Nur551, AufbauZeile.Art.Nur7 -> MaterialTheme.colorScheme.error; AufbauZeile.Art.Hersteller -> WARN_FARBE
                                            AufbauZeile.Art.GedcomL -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface })
                                    Text(when (z.art) { AufbauZeile.Art.Fremd -> stringResource(Res.string.ga_structure_foreign); AufbauZeile.Art.Hersteller -> stringResource(Res.string.ga_structure_vendor)
                                            AufbauZeile.Art.Nur551 -> stringResource(Res.string.ga_structure_only551); AufbauZeile.Art.Nur7 -> stringResource(Res.string.ga_structure_only7)
                                            AufbauZeile.Art.GedcomL -> "GEDCOM-L"; else -> "" },
                                        Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(z.anzahl.toString(), Modifier.width(70.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                    ListenLeiste(liste)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(top = 8.dp)) { Box(Modifier.width(160.dp)) { Knopf(stringResource(Res.string.action_close), true, onClose) } }
            }
        }
    }
}
