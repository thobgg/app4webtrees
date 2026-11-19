package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.gedcom.Aenderung
import de.bgghome.webtrees.nativ.gedcom.GedcomLeser
import de.bgghome.webtrees.nativ.gedcom.GedcomVergleich
import de.bgghome.webtrees.nativ.gedcom.Paar
import de.bgghome.webtrees.nativ.gedcom.PaarArt
import de.bgghome.webtrees.nativ.gedcom.Vergleich
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.LocalAppName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File

/*
 * Dateien vergleichen (gedaudit 0.3): die geoeffnete Datei A gegen eine zweite Datei B. Links die Gruppen (geaendert,
 * wahrscheinlich dieselbe, nur in A, nur in B, unverhaendert), in der Mitte die Personen der Gruppe, unten die geaenderten
 * Felder und die beiden Personenkarten nebeneinander. Druck und PDF wie bei der Pruefung.
 */

private enum class Gruppe { Geaendert, Wahrscheinlich, NurA, NurB, Gleich }

/** Ein Eintrag der Liste: Paar oder Einzelperson. */
private class Eintrag(val a: String?, val b: String?, val paar: Paar?)

internal fun feldName(feld: String): String = when (feld) {
    "NAME" -> Texte.t(Res.string.desk_ev_name)
    "SEX" -> Texte.t(Res.string.desk_chart_field_sex)
    "ELTERN" -> Texte.t(Res.string.ga_person_parents)
    "PARTNER" -> Texte.t(Res.string.ga_compare_partner)
    "KINDER" -> Texte.t(Res.string.desk_children)
    else -> GedcomLeser.label(feld)
}

/** Der Vergleich als Liste fuer Druck und PDF. */
internal fun vergleichZeilen(v: Vergleich, a: TreeExport, b: TreeExport, nameA: String, nameB: String): List<Zeile> = buildList {
    add(Zeile(Texte.t(Res.string.ga_compare) + ": $nameA – $nameB", gross = true))
    add(Zeile(Texte.t(Res.string.ga_compare_summary, a.individuals.size, nameA, b.individuals.size, nameB, v.paare.size)))
    fun abschnitt(titel: String, eintraege: List<Eintrag>) {
        if (eintraege.isEmpty()) return
        add(Zeile("")); add(Zeile("$titel (${eintraege.size})", fett = true))
        eintraege.forEach { e ->
            val pa = e.a?.let { a.person(it) }; val pb = e.b?.let { b.person(it) }
            val name = (pa ?: pb)?.let(::registerName) ?: "?"
            add(Zeile(name + "  " + listOfNotNull(e.a, e.b?.takeIf { it != e.a }).joinToString(" / "), fett = e.paar != null && e.paar.aenderungen.isNotEmpty()))
            e.paar?.aenderungen?.forEach { ae -> add(Zeile("", spalten = listOf(feldName(ae.feld), ae.a.ifBlank { "–" }, ae.b.ifBlank { "–" }), anteile = listOf(0.2f, 0.4f, 0.4f))) }
        }
    }
    abschnitt(Texte.t(Res.string.ga_compare_changed), v.geaendert.map { Eintrag(it.a, it.b, it) })
    abschnitt(Texte.t(Res.string.ga_compare_likely), v.wahrscheinlich.map { Eintrag(it.a, it.b, it) })
    abschnitt(Texte.t(Res.string.ga_compare_only, nameA), v.nurA.map { Eintrag(it, null, null) })
    abschnitt(Texte.t(Res.string.ga_compare_only, nameB), v.nurB.map { Eintrag(null, it, null) })
}

@Composable
fun VergleichFenster(g: GeladeneDatei, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose, title = stringResource(Res.string.ga_compare) + " – " + g.datei.name,
        state = arbeitsfenster("ga_vergleich", 1240, 860),
        onPreviewKeyEvent = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme { VergleichInhalt(g, onClose) }
    }
}

/** Der Inhalt des Vergleichsfensters, ohne Fensterrahmen (fuer Bilder ohne Bildschirm). [vorgabeB] oeffnet gleich eine zweite Datei und waehlt den ersten geaenderten Eintrag. */
@Composable
fun VergleichInhalt(g: GeladeneDatei, onClose: () -> Unit, vorgabeB: File? = null) {
    val appName = LocalAppName.current
    var dateiB by remember { mutableStateOf(vorgabeB) }
    var gruppe by remember { mutableStateOf(Gruppe.Geaendert) }
    var gewaehlt by remember { mutableStateOf<Eintrag?>(null) }
    val a = g.baum; val nameA = g.datei.name
    val ergebnis by produceState<Result<Pair<TreeExport, Vergleich>>?>(null, dateiB) {
        value = null
        val f = dateiB ?: return@produceState
        value = withContext(Dispatchers.Default) { runCatching { val b = GedcomLeser.alsExport(GedcomLeser.lesen(f), stand = f.lastModified()); b to GedcomVergleich.vergleichen(a, b) } }
    }
    val nameB = dateiB?.name.orEmpty()
    run {
        run {
            val b = ergebnis?.getOrNull()?.first; val v = ergebnis?.getOrNull()?.second
            if (vorgabeB != null && v != null && gewaehlt == null) v.geaendert.firstOrNull()?.let { gewaehlt = Eintrag(it.a, it.b, it) }
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.ga_compare), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(nameA + "  ↔  " + (nameB.ifBlank { "?" }), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.width(220.dp)) { Knopf(stringResource(Res.string.ga_compare_other) + " …", true) { gedcomDateiWaehlen(Texte.t(Res.string.ga_compare_other))?.let { dateiB = it; gewaehlt = null } } }
                }
                if (v != null && b != null) Text(stringResource(Res.string.ga_compare_summary, a.individuals.size, nameA, b.individuals.size, nameB, v.paare.size),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.ga_compare_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                when {
                    dateiB == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.ga_drop_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ergebnis == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text(stringResource(Res.string.ga_compare_loading), Modifier.padding(top = 8.dp)) } }
                    ergebnis?.isFailure == true -> Text(stringResource(Res.string.ga_read_error, meldung(ergebnis?.exceptionOrNull())), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    v != null && b != null -> {
                        val gruppen = listOf(
                            Gruppe.Geaendert to (stringResource(Res.string.ga_compare_changed) to v.geaendert.map { Eintrag(it.a, it.b, it) }),
                            Gruppe.Wahrscheinlich to (stringResource(Res.string.ga_compare_likely) to v.wahrscheinlich.map { Eintrag(it.a, it.b, it) }),
                            Gruppe.NurA to (stringResource(Res.string.ga_compare_only, nameA) to v.nurA.map { Eintrag(it, null, null) }),
                            Gruppe.NurB to (stringResource(Res.string.ga_compare_only, nameB) to v.nurB.map { Eintrag(null, it, null) }),
                            Gruppe.Gleich to (stringResource(Res.string.ga_compare_same) to v.gleich.map { Eintrag(it.a, it.b, it) }),
                        )
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            // Gruppen
                            Column(Modifier.width(260.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                                gruppen.forEach { (gr, inhalt) ->
                                    val (titel, liste) = inhalt
                                    Row(Modifier.fillMaxWidth().background(if (gruppe == gr) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).fokusRahmen()
                                        .clickable { gruppe = gr; gewaehlt = null }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(titel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (gruppe == gr) FontWeight.SemiBold else FontWeight.Normal)
                                        Text(liste.size.toString(), Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                                            color = if (liste.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else if (gr == Gruppe.Gleich) MaterialTheme.colorScheme.onSurface else WARN_FARBE,
                                            fontWeight = if (liste.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal)
                                    }
                                }
                            }
                            VerticalDivider(Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            // Eintraege und Details
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                val liste = gruppen.first { it.first == gruppe }.second.second
                                val lst = rememberLazyListState()
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    if (liste.isEmpty()) Text(stringResource(Res.string.desk_check_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else LazyColumn(Modifier.fillMaxSize(), state = lst) {
                                        items(liste) { e ->
                                            val pa = e.a?.let { a.person(it) }; val pb = e.b?.let { b.person(it) }
                                            val aktiv = gewaehlt === e
                                            val kopierText = stringResource(Res.string.desk_copy_text)
                                            ContextMenuArea(items = { listOfNotNull(
                                                e.a?.let { x -> ContextMenuItem("$kopierText ($nameA)") { a.detail(x)?.let(::personentextKopieren) } },
                                                e.b?.let { x -> ContextMenuItem("$kopierText ($nameB)") { b.detail(x)?.let(::personentextKopieren) } },
                                            ) }) {
                                            Row(Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).fokusRahmen()
                                                .clickable { gewaehlt = e }.padding(start = 8.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text((pa ?: pb)?.let(::registerName) ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        (pa ?: pb)?.lifespan?.takeIf(String::isNotBlank)?.let { Text("  ($it)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                                        Text("  " + listOfNotNull(e.a, e.b?.takeIf { it != e.a }).joinToString(" / "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    e.paar?.aenderungen?.takeIf { it.isNotEmpty() }?.let { ae ->
                                                        Text(ae.joinToString(", ") { feldName(it.feld) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                            }
                                        }
                                    }
                                    ListenLeiste(lst)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val e = gewaehlt
                                Box(Modifier.height(330.dp).fillMaxWidth()) {
                                    if (e == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.ga_compare_select), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    else Column(Modifier.fillMaxSize()) {
                                        e.paar?.aenderungen?.takeIf { it.isNotEmpty() }?.let { ae -> Aenderungen(ae, nameA, nameB) }
                                        Row(Modifier.weight(1f).fillMaxWidth()) {
                                            Box(Modifier.weight(1f).fillMaxHeight()) { PersonKarte(a, e.a) }
                                            VerticalDivider(Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                            Box(Modifier.weight(1f).fillMaxHeight()) { PersonKarte(b, e.b) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bereit = v != null && b != null
                    fun doc() = listenPdf(vergleichZeilen(v!!, a, b!!, nameA, nameB), appName, "$nameA – $nameB")
                    val titel = Texte.t(Res.string.ga_compare) + ": $nameA – $nameB"
                    Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.desk_print), bereit) { drucken(doc(), titel) } }
                    Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.desk_save_pdf), bereit) { alsPdf(doc(), titel) } }
                    Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.action_close), true, onClose) }
                }
            }
        }
    }
}

@Composable
private fun Aenderungen(ae: List<Aenderung>, nameA: String, nameB: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.ga_compare_field), Modifier.width(140.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(nameA, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(nameB, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ae.take(8).forEach { x ->
            Row(Modifier.fillMaxWidth()) {
                Text(feldName(x.feld), Modifier.width(140.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold)
                Text(x.a.ifBlank { "–" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(x.b.ifBlank { "–" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (ae.size > 8) Text("… +${ae.size - 8}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
