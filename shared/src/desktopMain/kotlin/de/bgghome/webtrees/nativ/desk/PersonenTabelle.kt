package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.data.Ortsanzeige
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.forSex
import de.bgghome.webtrees.nativ.ui.select
import de.bgghome.webtrees.nativ.ui.setRoot
import de.bgghome.webtrees.nativ.ui.treeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/*
 * Personentabelle: alle Personen des Baums in Spalten, nach jeder Spalte sortier- und filterbar. Die Daten kommen aus
 * dem Baum-Export (BaumSpeicher) - dieselbe Quelle wie Prueffenster und Buecher, ohne eigene Anfragen.
 * Fehlt die Geburt, steht die Taufe da (mit ~), fehlt der Tod, das Begraebnis (mit □).
 */

/** Eine Zeile der Tabelle; Jahre 0 = unbekannt. */
data class TabellenZeile(
    val xref: String,
    val name: String,
    val sortName: String,
    val sex: String,
    val geburt: String,
    val geburtJd: Int,
    val geburtJahr: Int,
    val geburtsort: String,
    val tod: String,
    val todJd: Int,
    val todJahr: Int,
    val sterbeort: String,
    val beruf: String,
    val url: String,
)

private fun ereignis(haupt: EventJson?, ersatz: EventJson?, zeichen: String): Triple<String, DateJson?, String> {
    val e = haupt?.takeIf { it.date != null || it.place != null }
    val z = if (e == null && ersatz != null && (ersatz.date != null || ersatz.place != null)) ersatz else null
    val ev = e ?: z
    val datum = ev?.date?.text.orEmpty().let { if (z != null && it.isNotEmpty()) "$zeichen $it" else it }
    return Triple(datum, ev?.date, Ortsanzeige.text(ev?.place?.name))
}

/** Alle sichtbaren Personen des Baums als Tabellenzeilen; private fallen weg. */
fun tabellenZeilen(baum: TreeExport): List<TabellenZeile> = baum.individuals.values.map { it.person }.filter { !it.isPrivate }.map { p ->
    val (geb, gebDatum, gebOrt) = ereignis(p.birth, p.chr, "~")
    val (tod, todDatum, todOrt) = ereignis(p.death, p.buri, "□")
    TabellenZeile(
        xref = p.xref, name = p.name, sortName = p.sortName.ifBlank { p.name }, sex = p.sex,
        geburt = geb, geburtJd = gebDatum?.jd ?: 0, geburtJahr = gebDatum?.year ?: 0, geburtsort = gebOrt,
        tod = tod, todJd = todDatum?.jd ?: 0, todJahr = todDatum?.year ?: 0, sterbeort = todOrt,
        beruf = p.occupation.orEmpty(), url = p.url,
    )
}

enum class Spalte(val titel: StringResource, val breite: Float) {
    Id(Res.string.desk_table_col_id, 0.7f),
    Name(Res.string.desk_table_col_name, 2.4f),
    Geschlecht(Res.string.desk_table_col_sex, 0.45f),
    Geburt(Res.string.desk_table_col_birth, 1.1f),
    Geburtsort(Res.string.desk_table_col_birthplace, 1.8f),
    Tod(Res.string.desk_table_col_death, 1.1f),
    Sterbeort(Res.string.desk_table_col_deathplace, 1.6f),
    Beruf(Res.string.desk_table_col_occupation, 1.5f);

    fun text(z: TabellenZeile): String = when (this) {
        Id -> z.xref; Name -> z.name; Geschlecht -> z.sex; Geburt -> z.geburt; Geburtsort -> z.geburtsort
        Tod -> z.tod; Sterbeort -> z.sterbeort; Beruf -> z.beruf
    }

    fun jahr(z: TabellenZeile): Int? = when (this) { Geburt -> z.geburtJahr; Tod -> z.todJahr; else -> null }

    /** Sortierung: Daten nach Julianischem Tag, Kennungen nach ihrer Zahl, Namen nach dem Sortiernamen; Leeres ans Ende. */
    val vergleich: Comparator<TabellenZeile> get() = when (this) {
        Id -> compareBy<TabellenZeile> { it.xref.filter(Char::isDigit).toLongOrNull() ?: Long.MAX_VALUE }.thenBy { it.xref }
        Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.sortName }
        Geburt -> compareBy { if (it.geburtJd == 0) Int.MAX_VALUE else it.geburtJd }
        Tod -> compareBy { if (it.todJd == 0) Int.MAX_VALUE else it.todJd }
        else -> compareBy<TabellenZeile> { text(it).isEmpty() }.thenBy(String.CASE_INSENSITIVE_ORDER) { text(it) }
    }
}

/**
 * Ein Spaltenfilter: "!" = Feld leer, "*" = Feld gefuellt, "1800-1850" = Jahre von-bis (nur Datumsspalten, auch "-1850"
 * und "1800-"), sonst Teiltext ohne Gross-/Kleinschreibung. Leerer Filter laesst alles durch.
 */
fun filterPasst(filter: String, text: String, jahr: Int?): Boolean {
    val f = filter.trim()
    if (f.isEmpty()) return true
    if (f == "!") return text.isBlank()
    if (f == "*") return text.isNotBlank()
    if (jahr != null) Regex("""^(\d{1,4})?\s*-\s*(\d{1,4})?$""").matchEntire(f)?.let { m ->
        val von = m.groupValues[1].toIntOrNull(); val bis = m.groupValues[2].toIntOrNull()
        if (von != null || bis != null) return jahr != 0 && (von == null || jahr >= von) && (bis == null || jahr <= bis)
    }
    return text.contains(f, ignoreCase = true)
}

/** Filtern und sortieren; [absteigend] dreht nur die gefuellten Werte um, Leeres bleibt unten. */
fun tabelleAnwenden(zeilen: List<TabellenZeile>, filter: Map<Spalte, String>, sortierung: Spalte, absteigend: Boolean): List<TabellenZeile> {
    val aktiv = filter.filterValues { it.isNotBlank() }
    val gefiltert = if (aktiv.isEmpty()) zeilen else zeilen.filter { z -> aktiv.all { (s, f) -> filterPasst(f, s.text(z), s.jahr(z)) } }
    val (voll, leer) = gefiltert.partition { sortierung == Spalte.Name || sortierung == Spalte.Id || sortierung.text(it).isNotBlank() }
    val sortiert = voll.sortedWith(sortierung.vergleich)
    return (if (absteigend) sortiert.asReversed() else sortiert) + leer.sortedWith(Spalte.Name.vergleich)
}

private fun csv(zeilen: List<TabellenZeile>, titel: List<String>): String = buildString {
    fun feld(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    append('﻿') // BOM, damit Tabellenprogramme die Umlaute erkennen
    append(titel.joinToString(";") { feld(it) }).append("\r\n")
    zeilen.forEach { z -> append(Spalte.entries.joinToString(";") { feld(it.text(z)) }).append("\r\n") }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonenTabelle(state: UiState, viewModel: AppViewModel, openSheet: (String) -> Unit, openWeb: (String) -> Unit, onClose: () -> Unit,
    onZusammenfuehren: ((String) -> Unit)? = null) {
    // Die Tasten (Pfeile, Enter) gehoeren zum Fenster, ihr Ziel (die Zeilen) zum Inhalt: der Inhalt reicht seinen Handler herauf.
    val tasten = remember { mutableStateOf<(KeyEvent) -> Boolean>({ false }) }
    androidx.compose.ui.window.Window(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_table_window) + " – " + state.tree?.title.orEmpty(),
        state = arbeitsfenster("fenster_tabelle", 1280, 820),
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                Key.Escape -> { onClose(); true }
                Key.F1 -> { Hilfe.oeffnen("tabelle"); true }
                else -> tasten.value(e)
            }
        },
    ) {
        DeskTheme { PersonenTabelleInhalt(state, viewModel, openSheet, openWeb, onZusammenfuehren) { tasten.value = it } }
    }
}

/** Der Inhalt des Tabellenfensters, ohne das Fenster - so laesst er sich auch ohne Bildschirm zeichnen. */
@Composable
internal fun PersonenTabelleInhalt(state: UiState, viewModel: AppViewModel, openSheet: (String) -> Unit, openWeb: (String) -> Unit,
    onZusammenfuehren: ((String) -> Unit)? = null, tastenSetzen: ((KeyEvent) -> Boolean) -> Unit) {
    var fortschritt by remember { mutableStateOf(0 to 0) }
    var neu by remember { mutableStateOf(0) }
    val baum by produceState<Result<List<TabellenZeile>>?>(null, state.tree?.name, neu) {
        value = null
        val tree = state.tree ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val b = BaumSpeicher.holen(viewModel.client, tree.name, Int.MAX_VALUE / 64) { a, n -> fortschritt = a to n }
                    ?: throw IllegalStateException(Texte.t(Res.string.desk_list_needs_export))
                tabellenZeilen(b)
            }
        }
    }
    var filter by remember { mutableStateOf(emptyMap<Spalte, String>()) }
    var sortierung by remember { mutableStateOf(Spalte.Name) }
    var absteigend by remember { mutableStateOf(false) }
    var gewaehlt by remember { mutableStateOf<String?>(state.root) }
    val zeilen by produceState(emptyList(), baum, filter, sortierung, absteigend) {
        val alle = baum?.getOrNull() ?: run { value = emptyList(); return@produceState }
        value = withContext(Dispatchers.Default) { tabelleAnwenden(alle, filter, sortierung, absteigend) }
    }
    val liste = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val titel = Spalte.entries.map { stringResource(it.titel) }

    fun schritt(d: Int) {
        if (zeilen.isEmpty()) return
        val i = (zeilen.indexOfFirst { it.xref == gewaehlt }.takeIf { it >= 0 }?.plus(d) ?: 0).coerceIn(0, zeilen.lastIndex)
        gewaehlt = zeilen[i].xref
        viewModel.select(zeilen[i].xref)
        scope.launch {
            val sichtbar = liste.layoutInfo.visibleItemsInfo
            if (sichtbar.none { it.index == i && it.offset >= 0 && it.offset + it.size <= liste.layoutInfo.viewportEndOffset }) {
                liste.scrollToItem((i - if (d > 0) (sichtbar.size - 2).coerceAtLeast(0) else 0).coerceAtLeast(0))
            }
        }
    }

    SideEffect {
        tastenSetzen { e ->
            when (e.key) {
                Key.DirectionDown -> { schritt(1); true }
                Key.DirectionUp -> { schritt(-1); true }
                Key.PageDown -> { schritt(20); true }
                Key.PageUp -> { schritt(-20); true }
                Key.Enter -> { gewaehlt?.let(viewModel::setRoot); true }
                else -> false
            }
        }
    }
    run {
        run {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // ── Kopf: Anzahl, Filter leeren, CSV ──
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val alle = baum?.getOrNull()?.size ?: 0
                    Text(stringResource(Res.string.desk_table_count, zeilen.size, alle), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Tipp(stringResource(Res.string.tipp_table_filter)) {
                        OutlinedButton(onClick = { filter = emptyMap() }, enabled = filter.values.any { it.isNotBlank() }, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(Res.string.desk_table_clear))
                        }
                    }
                    OutlinedButton(onClick = {
                        val dialog = FileDialog(null as Frame?, Texte.t(Res.string.desk_table_csv), FileDialog.SAVE).apply { file = "personen.csv"; isVisible = true }
                        dialog.file?.let { name -> runCatching { File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv").writeText(csv(zeilen, titel)) } }
                    }, enabled = zeilen.isNotEmpty(), shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_table_csv)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Spaltenkoepfe (Klick sortiert, zweiter Klick dreht um) und Filterzeile ──
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 8.dp, end = 20.dp)) {
                    Spalte.entries.forEachIndexed { i, s ->
                        val aktiv = s == sortierung
                        Text(titel[i] + if (aktiv) (if (absteigend) " ▼" else " ▲") else "",
                            Modifier.weight(s.breite).clickable { if (aktiv) absteigend = !absteigend else { sortierung = s; absteigend = false } }
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge, fontWeight = if (aktiv) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                    }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 8.dp, end = 20.dp, bottom = 6.dp)) {
                    Spalte.entries.forEach { s ->
                        FilterFeld(filter[s].orEmpty(), Modifier.weight(s.breite)) { filter = filter + (s to it) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val fehler = baum?.exceptionOrNull()
                    when {
                        fehler != null -> Text(fehler.message ?: "?", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        baum == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            val (x, n) = fortschritt
                            if (n > 0) LinearProgressIndicator(progress = { x.toFloat() / n }, modifier = Modifier.width(280.dp)) else CircularProgressIndicator()
                            Text(if (n > 0) stringResource(Res.string.desk_check_loading, x, n) else stringResource(Res.string.desk_chart_loading_any), Modifier.padding(top = 8.dp))
                        }
                        zeilen.isEmpty() -> Text(stringResource(Res.string.desk_table_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> {
                            val mittel = MaterialTheme.colorScheme
                            LazyColumn(Modifier.fillMaxSize(), state = liste) {
                                itemsIndexed(zeilen, key = { _, z -> z.xref }) { i, z ->
                                    val aktiv = z.xref == gewaehlt
                                    ContextMenuArea(items = {
                                        listOf(
                                            ContextMenuItem(Texte.t(Res.string.action_make_root)) { viewModel.setRoot(z.xref) },
                                            ContextMenuItem(Texte.t(Res.string.desk_sheet)) { openSheet(z.xref) },
                                            ContextMenuItem(Texte.t(Res.string.chip_open_web)) { openWeb(z.url) },
                                            ContextMenuItem(Texte.t(Res.string.desk_copy_text)) { personentextKopieren(viewModel, z.xref) },
                                            ContextMenuItem(Texte.t(Res.string.desk_meta_menu)) { MetaSuche.fuer(viewModel, z.xref) },
                                        ) + listOfNotNull(onZusammenfuehren?.let { f -> ContextMenuItem(Texte.t(Res.string.desk_merge_with)) { f(z.xref) } },
                                            if (state.tree?.canEdit == true) ContextMenuItem(Texte.t(Res.string.action_delete_person)) { Loeschwahl.person = z.xref to z.name } else null)
                                    }) {
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .background(when { aktiv -> mittel.secondaryContainer; i % 2 == 1 -> mittel.surfaceVariant.copy(alpha = 0.35f); else -> Color.Transparent })
                                                .combinedClickable(onDoubleClick = { viewModel.setRoot(z.xref) }) { gewaehlt = z.xref; viewModel.select(z.xref) }
                                                .padding(start = 8.dp, end = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Spalte.entries.forEach { s -> Zelle(s, z, z.xref == state.root) }
                                        }
                                    }
                                }
                            }
                            ListenLeiste(liste)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(Res.string.desk_table_hint), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    // Aenderungen aus dem Hauptfenster (Speichern) machen den Zwischenspeicher alt: nach dem Schreiben neu holen.
    var warBeschaeftigt by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy) { if (warBeschaeftigt && !state.busy) neu++; warBeschaeftigt = state.busy }
}

@Composable
private fun RowScope.Zelle(s: Spalte, z: TabellenZeile, mittelperson: Boolean) {
    val m = Modifier.weight(s.breite).padding(horizontal = 6.dp, vertical = 5.dp)
    if (s == Spalte.Geschlecht) {
        Box(m) { Box(Modifier.size(10.dp).background(treeColors.forSex(z.sex), CircleShape)) }
        return
    }
    val text = s.text(z)
    Text(text, m, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (s == Spalte.Name && mittelperson) FontWeight.Bold else FontWeight.Normal,
        color = if (s == Spalte.Id) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun FilterFeld(wert: String, modifier: Modifier, onWert: (String) -> Unit) {
    Box(modifier.padding(horizontal = 3.dp)) {
        BasicTextField(
            wert, onWert, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        if (wert.isEmpty()) Text("…", Modifier.padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline)
    }
}
