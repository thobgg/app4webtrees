package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.desk.fokusRahmen
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.karte.GeoPunkt
import de.bgghome.webtrees.nativ.ui.karte.KachelEbene
import de.bgghome.webtrees.nativ.ui.karte.KachelKarte
import de.bgghome.webtrees.nativ.ui.karte.KartenLinie
import de.bgghome.webtrees.nativ.ui.karte.KartenPin
import de.bgghome.webtrees.nativ.ui.karte.KartenZustand
import org.jetbrains.compose.resources.stringResource

/*
 * Lebensstationen auf der Kachelkarte (24.09.2026, ersetzt die Liste mit Browser-Links):
 * ein nummerierter Pin je Ort in zeitlicher Reihenfolge, die Stationen durch eine Linie
 * verbunden, darunter die Liste. Zeile anklicken rueckt den Ort in die Mitte, Pin anklicken
 * hebt die Zeilen des Ortes hervor. Mausrad und Doppelklick zoomen, Ziehen verschiebt,
 * die Knoepfe rechts oben sind fuer die Maus ohne Rad.
 */

private class Station(val nummer: Int, val fact: FactJson, val punkt: GeoPunkt)

@Composable
actual fun LifeMap(facts: List<FactJson>) {
    val stationen = remember(facts) {
        facts.filter { it.place?.lat != null && it.place.lng != null }
            .mapIndexed { i, f -> Station(i + 1, f, GeoPunkt(f.place!!.lat!!, f.place.lng!!)) }
    }
    if (stationen.isEmpty()) {
        Text(stringResource(Res.string.map_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val zustand = remember { KartenZustand() }
    var gewaehlt by remember(facts) { mutableStateOf<GeoPunkt?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val akzent = MaterialTheme.colorScheme.tertiary
    // Mehrere Ereignisse am selben Ort: ein Pin mit der ersten Nummer.
    val orte = remember(stationen) { stationen.groupBy { it.punkt } }
    val pins = orte.map { (punkt, hier) ->
        KartenPin(punkt.lat, punkt.lon, text = hier.first().nummer.toString(), farbe = if (punkt == gewaehlt) akzent else primary, radiusDp = 11f, tag = punkt)
    }
    val linien = if (orte.size > 1) listOf(KartenLinie(stationen.map { it.punkt }, primary.copy(alpha = 0.7f), 3f)) else emptyList()

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val w = constraints.maxWidth; val h = constraints.maxHeight
            LaunchedEffect(stationen, w, h) { zustand.passeEin(orte.keys.toList(), w, h, randPx = 48, einzelZoom = 11, maxZoom = 14) }
            KachelKarte(
                zustand, KachelEbene.STANDARD, Modifier.fillMaxSize(),
                pins = pins, linien = linien,
                onPinTap = { gewaehlt = it.tag as? GeoPunkt },
                onTap = { gewaehlt = null },
            )
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                ZoomTaste("+", stringResource(Res.string.tree_zoom_in)) { zustand.zoom = (zustand.zoom + 1).coerceAtMost(KachelEbene.STANDARD.maxZoom) }
                Spacer(Modifier.size(4.dp))
                ZoomTaste("−", stringResource(Res.string.tree_zoom_out)) { zustand.zoom = (zustand.zoom - 1).coerceAtLeast(1) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Die Liste nimmt hoechstens ein Drittel; bei vielen Stationen rollt sie.
        Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            stationen.forEach { s ->
                val hervor = s.punkt == gewaehlt
                val year = s.fact.date?.year?.takeIf { it != 0 }?.toString()
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (hervor) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .fokusRahmen()
                        .clickable { gewaehlt = s.punkt; zustand.setze(s.punkt.lat, s.punkt.lon, maxOf(zustand.zoom, 10)) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.nummer.toString(), Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium, color = primary, fontWeight = FontWeight.Bold)
                    Text(year.orEmpty(), Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.fact.label, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.fact.place!!.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ZoomTaste(zeichen: String, hinweis: String, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)
            .border(1.dp, Color(0x66000000), RoundedCornerShape(4.dp)).clickable(onClickLabel = hinweis, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(zeichen, color = Color.Black, style = MaterialTheme.typography.titleMedium) }
}
