package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.layout.aspectRatio
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import de.bgghome.webtrees.nativ.api.PlaceDetail
import de.bgghome.webtrees.nativ.api.PlaceEvent
import de.bgghome.webtrees.nativ.api.PlaceSummaryList
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.karte.GeoPunkt
import de.bgghome.webtrees.nativ.ui.karte.KachelEbene
import de.bgghome.webtrees.nativ.ui.karte.KachelKarte
import de.bgghome.webtrees.nativ.ui.karte.KartenPin
import de.bgghome.webtrees.nativ.ui.karte.KartenZustand
import de.bgghome.webtrees.nativ.ui.select
import de.bgghome.webtrees.nativ.ui.setRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Orte (ab API-Stufe 21): links alle Orte, wie sie an den Ereignissen stehen, mit Suche und Zahl der Ereignisse;
 * rechts der gewaehlte in Reitern - Personen und Familien mit ihren Ereignissen dort, Daten (Ebenen, uebergeordneter
 * Ort, Orte darunter, Ortsdatensatz _LOC, GOV-Kennung), Notizen, Quellen, Medien und Koordinaten mit Karte.
 * Stufe 1 liest nur.
 */

@Composable
fun OrteFenster(state: UiState, viewModel: AppViewModel, start: String?, openWeb: (String) -> Unit, onClose: () -> Unit, onBlatt: ((String) -> Unit)? = null,
    bereinigen: Boolean = false) {
    val tree = state.tree?.name
    var neu by remember { mutableStateOf(0) }
    val liste by produceState<Result<PlaceSummaryList>?>(null, tree, neu) {
        value = null
        if (tree != null) value = withContext(Dispatchers.IO) { runCatching { viewModel.client.placeList(tree) } }
    }
    var suche by remember { mutableStateOf("") }
    var gewaehlt by remember(start) { mutableStateOf(start?.takeIf { it.isNotEmpty() }) }
    val detail by produceState<Result<PlaceDetail>?>(null, tree, gewaehlt, neu) {
        value = null
        val n = gewaehlt
        if (tree != null && n != null) value = withContext(Dispatchers.IO) { runCatching { viewModel.client.place(tree, n) } }
    }

    DialogWindow(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_places_window) + " – " + state.tree?.title.orEmpty(),
        state = rememberDialogState(width = 1180.dp, height = 800.dp),
        onPreviewKeyEvent = { e ->
            when {
                e.type == KeyEventType.KeyDown && e.key == Key.Escape -> { onClose(); true }
                e.type == KeyEventType.KeyDown && e.key == Key.F1 -> { Hilfe.oeffnen("hauptfenster"); true }
                else -> false
            }
        },
    ) {
        var bearbeiten by remember { mutableStateOf<PlaceDetail?>(null) }
        var umbenennen by remember { mutableStateOf<de.bgghome.webtrees.nativ.api.PlaceRenameResult?>(null) }
        var bereinigenOffen by remember(bereinigen) { mutableStateOf(bereinigen) }
        val scope = rememberCoroutineScope()
        var meldung by remember { mutableStateOf<String?>(null) }
        val darf = state.tree?.canEdit == true && (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_PLACE_WRITE
        val darfUmbenennen = state.tree?.canEdit == true && (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_PLACE_RENAME
        // Erst die Vorschau holen, dann fragen (wie viele Ereignisse, Zusammenfuehren, Abweichungen)
        fun vorschau(von: String, nach: String) {
            meldung = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { viewModel.client.renamePlace(tree.orEmpty(), von, nach, preview = true) } }
                    .onSuccess { umbenennen = it }.onFailure { meldung = it.message ?: "?" }
            }
        }
        val verweise = meldung
        DeskTheme {
            OrteInhalt(liste, detail, gewaehlt, { gewaehlt = it }, suche, { suche = it }, viewModel, openWeb,
                onBearbeiten = if (darf) ({ bearbeiten = it }) else null, meldung = verweise,
                pflege = if (darf) OrtPflege(tree.orEmpty(), viewModel, state.archive) { neu++ } else null, onClose = onClose, onBlatt = onBlatt,
                onUmbenennen = if (darfUmbenennen) ({ von, nach -> vorschau(von, nach) }) else null,
                onBereinigen = if (darfUmbenennen) ({ bereinigenOffen = true }) else null)
            if (bereinigenOffen && darfUmbenennen) liste?.getOrNull()?.let { l ->
                OrteBereinigenDialog(tree.orEmpty(), l.places, onZeigen = { gewaehlt = it }, onZusammenfuehren = { von, nach -> vorschau(von, nach) },
                    onDismiss = { bereinigenOffen = false })
            }
            umbenennen?.let { v ->
                UmbenennenDialog(v, onDismiss = { umbenennen = null }) {
                    umbenennen = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { viewModel.client.renamePlace(tree.orEmpty(), v.from, v.to, preview = false) } }
                            .onSuccess { r ->
                                meldung = de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_place_renamed, r.events) +
                                    if (r.pending) "  " + de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_place_rename_moderated) else ""
                                gewaehlt = r.to
                                neu++
                            }.onFailure { meldung = it.message ?: "?" }
                    }
                }
            }
            bearbeiten?.let { o ->
                OrtDialog(tree.orEmpty(), o, viewModel.client, state.info?.user?.isAdmin == true, openWeb, onDismiss = { bearbeiten = null }, apiStufe = state.info?.api ?: 0) { r ->
                    meldung = listOfNotNull(
                        r.linked?.takeIf { it > 0 }?.let { de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_place_linked, it) },
                        if (r.pending) de.bgghome.webtrees.nativ.Texte.t(Res.string.msg_pending, o.name) else null,
                    ).joinToString("  ").ifEmpty { null }
                    neu++
                }
            }
        }
    }
}

/** Der Inhalt des Fensters, ohne Fenster (testbar ohne Bildschirm). */
@Composable
internal fun OrteInhalt(
    liste: Result<PlaceSummaryList>?, detail: Result<PlaceDetail>?, gewaehlt: String?, onWahl: (String) -> Unit,
    suche: String, onSuche: (String) -> Unit, viewModel: AppViewModel?, openWeb: (String) -> Unit, reiterStart: Int = 0,
    onBearbeiten: ((PlaceDetail) -> Unit)? = null, meldung: String? = null, onUmbenennen: ((String, String) -> Unit)? = null,
    karteStart: Boolean = false, pflege: OrtPflege? = null, onClose: (() -> Unit)? = null, onBlatt: ((String) -> Unit)? = null,
    onBereinigen: (() -> Unit)? = null,
) {
    // Rechts entweder der gewaehlte Ort oder die Karte aller Orte
    var karte by remember { mutableStateOf(karteStart) }
    // Breite der Liste: am Trennstrich ziehen, gemerkt wie die Seitenleisten im Hauptfenster
    var listeBreite by remember { mutableStateOf(DeskLayout.prefs.getString("orte_liste", null)?.toFloatOrNull() ?: 380f) }
    val dichte = androidx.compose.ui.platform.LocalDensity.current.density
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Liste ──
        Column(Modifier.width(listeBreite.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxWidth().padding(10.dp)) {
                BasicTextField(suche, onSuche, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small).padding(8.dp))
                if (suche.isEmpty()) Text(stringResource(Res.string.desk_places_search), Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
            // "Hof Nr. 2" vor "Hof Nr. 12": Zahlen im Namen als Zahlen (die Liste vom Server ist alphabetisch)
            val alle = remember(liste) { liste?.getOrNull()?.places.orEmpty().sortedWith(compareBy(de.bgghome.webtrees.nativ.data.Ortsnamen.NATUERLICH) { it.name }) }
            val treffer = if (suche.isBlank()) alle else alle.filter { it.name.contains(suche.trim(), ignoreCase = true) || it.gov?.contains(suche.trim(), ignoreCase = true) == true }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.desk_places_count, treffer.size, alle.size), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                onBereinigen?.let { b ->
                    Text(stringResource(Res.string.desk_places_cleanup), Modifier.clickable(onClick = b).padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                // Umschalter Ort | Karte
                listOf(false to Res.string.desk_places_details, true to Res.string.desk_places_map).forEach { (k, text) ->
                    val an = karte == k
                    Text(stringResource(text), Modifier.padding(start = 4.dp)
                        .background(if (an) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .clickable { karte = k }.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = if (an) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f)) {
                val fehler = liste?.exceptionOrNull()
                when {
                    fehler != null -> Text(fehler.message ?: "?", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                    liste == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> {
                        val ls = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxSize(), state = ls) {
                            items(treffer, key = { it.name }) { o ->
                                val aktiv = o.name.equals(gewaehlt, ignoreCase = true)
                                Row(Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                    .fokusRahmen().clickable { onWahl(o.name) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(o.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    // Ein Punkt fuer "hat Koordinaten", wie das Symbol im Reiter
                                    if (o.lat != null) Text("◉ ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text("${o.events}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        ListenLeiste(ls)
                    }
                }
            }
        }
        Ziehgriff(onZiehen = { listeBreite = (listeBreite + it / dichte).coerceIn(220f, 720f) }) { DeskLayout.prefs.putString("orte_liste", listeBreite.toString()) }

        // ── Der gewaehlte Ort oder die Karte ──
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val alleOrte = liste?.getOrNull()?.places
            when {
                karte && alleOrte != null -> {
                    val treffer = if (suche.isBlank()) alleOrte else alleOrte.filter { it.name.contains(suche.trim(), ignoreCase = true) || it.gov?.contains(suche.trim(), ignoreCase = true) == true }
                    OrtsKarte(treffer, gewaehlt, onWahl, onAnzeigen = { onWahl(it); karte = false })
                }
                gewaehlt == null -> Text(stringResource(Res.string.desk_places_choose), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                detail.exceptionOrNull() != null -> Text(detail.exceptionOrNull()?.message ?: "?", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                else -> OrtDetail(detail.getOrThrow(), viewModel, onWahl, openWeb, reiterStart, onBearbeiten, onUmbenennen, pflege, onBlatt, onClose)
            }
            // Schliessen oben rechts - beim Ort steht der Knopf in seiner Kopfzeile neben "In webtrees oeffnen"
            val mitKopf = !karte && gewaehlt != null && detail?.isSuccess == true
            if (onClose != null && !mitKopf) OutlinedButton(onClick = onClose, shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp)) { Text(stringResource(Res.string.action_close)) }
            meldung?.let { Text(it, Modifier.align(Alignment.BottomStart).padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

private enum class OrtReiter(val titel: StringResource) {
    Daten(Res.string.desk_place_tab_data), Personen(Res.string.desk_place_tab_people), Geschichte(Res.string.desk_place_tab_history),
    Notizen(Res.string.desk_tab_notes), Quellen(Res.string.desk_sources_window), Medien(Res.string.tab_media), Koordinaten(Res.string.desk_place_tab_coords),
}

@Composable
private fun OrtDetail(o: PlaceDetail, viewModel: AppViewModel?, onWahl: (String) -> Unit, openWeb: (String) -> Unit, reiterStart: Int,
                      onBearbeiten: ((PlaceDetail) -> Unit)?, onUmbenennen: ((String, String) -> Unit)? = null, pflege: OrtPflege? = null, onBlatt: ((String) -> Unit)? = null,
                      onClose: (() -> Unit)? = null) {
    var reiter by remember(o.name) { mutableStateOf(OrtReiter.entries[reiterStart]) }
    val loc = o.location
    // Wie viel in einem Reiter steht - 0 = leer (der Reiter bleibt, steht aber blasser)
    fun anzahl(r: OrtReiter): Int = when (r) {
        OrtReiter.Personen -> o.individuals.size + o.families.size + o.moreIndividuals + o.moreFamilies
        OrtReiter.Geschichte -> geschichte(o).size
        OrtReiter.Daten -> 1
        OrtReiter.Notizen -> loc?.notes?.size ?: 0
        OrtReiter.Quellen -> loc?.sources?.size ?: 0
        OrtReiter.Medien -> loc?.media?.size ?: 0
        OrtReiter.Koordinaten -> if (o.lat != null && o.lng != null) 1 else 0
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onUmbenennen != null && o.canEdit) {
                // Wie ein Eingabefeld: aendern und mit dem Haken (oder Enter) bestaetigen - gibt es den Namen schon,
                // wird daraus ein Zusammenfuehren
                var neuerName by remember(o.name) { mutableStateOf(o.name) }
                val geaendert = neuerName.trim().isNotEmpty() && neuerName.trim() != o.name
                OutlinedTextField(neuerName, { neuerName = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && geaendert) { onUmbenennen(o.name, neuerName.trim()); true } else false
                    },
                    trailingIcon = {
                        Tipp(stringResource(Res.string.desk_place_rename_tip)) {
                            IconButton(onClick = { onUmbenennen(o.name, neuerName.trim()) }, enabled = geaendert) {
                                Icon(Icons.Default.CheckCircle, stringResource(Res.string.desk_place_rename_tip),
                                    tint = if (geaendert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    })
            } else {
                SelectionContainer(Modifier.weight(1f)) { Text(o.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            }
            if (onBearbeiten != null && o.canEdit && loc?.canEdit != false) OutlinedButton(onClick = { onBearbeiten(o) }, shape = MaterialTheme.shapes.small) {
                Text(stringResource(Res.string.action_edit))
            }
            if (loc != null && loc.url.isNotBlank()) OutlinedButton(onClick = { openWeb(loc.url) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.chip_open_web)) }
            onClose?.let { OutlinedButton(onClick = it, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.action_close)) } }
        }
        PrimaryScrollableTabRow(selectedTabIndex = reiter.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
            OrtReiter.entries.forEach { r ->
                val n = anzahl(r)
                val text = stringResource(r.titel) + if (n > 0 && r != OrtReiter.Daten && r != OrtReiter.Koordinaten) " ($n)" else ""
                Tab(selected = r == reiter, onClick = { reiter = r }, text = {
                    Text(text, maxLines = 1, color = if (n == 0 && pflege == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (reiter) {
                OrtReiter.Personen -> Rollbar { Personen(o, viewModel, onBlatt) }
                OrtReiter.Geschichte -> Rollbar { Geschichte(o, viewModel, onBlatt) }
                OrtReiter.Daten -> Rollbar { Daten(o, onWahl, openWeb, viewModel?.client?.userAgent) }
                OrtReiter.Notizen -> Rollbar { OrtNotizen(o, pflege) }
                OrtReiter.Quellen -> Rollbar { OrtQuellen(o, pflege, openWeb) }
                OrtReiter.Medien -> Rollbar { OrtMedien(o, pflege, openWeb) }
                OrtReiter.Koordinaten -> Koordinaten(o, openWeb)
            }
        }
    }
}

@Composable
private fun Rollbar(inhalt: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { inhalt() }
        SenkrechteLeiste(scroll)
    }
}

@Composable
private fun Leer() = Text(stringResource(Res.string.desk_place_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)

/** "Geburt 1800, Taufe 1800" */
private fun ereignisse(f: List<PlaceEvent>) = f.joinToString(", ") { e -> listOfNotNull(e.label, e.date?.year?.takeIf { it > 0 }?.toString()).joinToString(" ") }

/**
 * Ein Eintrag der Ortsgeschichte: ein Ereignis am Ort selbst (EVEN am _LOC: Brand, Umbau ...) oder ein Ereignis einer
 * Person oder Familie hier (Geburt, Wohnort, Heirat ...). Sortiert nach dem Datum; ohne Datum ans Ende.
 */
internal data class GeschichtsEintrag(val jd: Int, val datum: String, val was: String, val wer: String?, val xref: String?, val amOrt: Boolean, val notizen: List<String> = emptyList())

internal fun geschichte(o: PlaceDetail): List<GeschichtsEintrag> {
    val eintraege = mutableListOf<GeschichtsEintrag>()
    o.location?.events?.forEach { e ->
        eintraege += GeschichtsEintrag(e.date?.jd?.takeIf { it > 0 } ?: Int.MAX_VALUE, e.date?.text.orEmpty(),
            listOfNotNull(e.type ?: e.label.takeIf(String::isNotBlank), e.value).joinToString(": "), null, null, true, e.notes)
    }
    o.individuals.forEach { p -> p.facts.forEach { f ->
        eintraege += GeschichtsEintrag(f.date?.jd?.takeIf { it > 0 } ?: Int.MAX_VALUE, f.date?.text.orEmpty(), f.label, p.name, p.xref, false)
    } }
    o.families.forEach { fam -> fam.facts.forEach { f ->
        eintraege += GeschichtsEintrag(f.date?.jd?.takeIf { it > 0 } ?: Int.MAX_VALUE, f.date?.text.orEmpty(), f.label, fam.name, fam.husband ?: fam.wife, false)
    } }
    return eintraege.sortedWith(compareBy({ it.jd }, { !it.amOrt }, { it.wer.orEmpty() }))
}

/** Reiter Geschichte: Ereignisse am Ort und aller Personen und Familien hier, chronologisch - die Chronik eines Hauses. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Geschichte(o: PlaceDetail, viewModel: AppViewModel?, onBlatt: ((String) -> Unit)? = null) {
    val farben = MaterialTheme.colorScheme
    val eintraege = geschichte(o)
    if (eintraege.isEmpty()) { Leer(); return }
    Text(stringResource(Res.string.desk_place_history_hint), Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
    eintraege.forEach { e ->
        Row(Modifier.fillMaxWidth().fokusRahmen()
            .then(if (e.xref != null && viewModel != null) Modifier.combinedClickable(onDoubleClick = { if (onBlatt != null) onBlatt(e.xref) else viewModel.setRoot(e.xref) }) { viewModel.select(e.xref) } else Modifier)
            .padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
            Text(e.datum, Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.weight(1f)) {
                if (e.amOrt) {
                    // Ereignis des Orts selbst: hervorgehoben, mit Hinweis - so hebt es sich von den Personenereignissen ab
                    Text(e.was, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = farben.primary)
                    Text(stringResource(Res.string.desk_place_event_here), style = MaterialTheme.typography.labelSmall, color = farben.outline)
                    e.notizen.forEach { n -> Text(n, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant) }
                } else {
                    Text(e.was, style = MaterialTheme.typography.bodyMedium)
                    e.wer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Personen(o: PlaceDetail, viewModel: AppViewModel?, onBlatt: ((String) -> Unit)? = null) {
    val farben = MaterialTheme.colorScheme
    if (o.individuals.isEmpty() && o.families.isEmpty()) Leer()
    val bedienung = stringResource(if (onBlatt != null) Res.string.desk_place_person_hint_sheet else Res.string.desk_place_person_hint)
    o.individuals.forEach { p ->
        val name = registerName(p.person(), stringResource(Res.string.person_private), stringResource(Res.string.person_no_name))
        // Beim Darueberfahren: Name, Lebensdaten, die Ereignisse hier und wie man die Person oeffnet
        val tipp = listOf(p.name.ifBlank { name }, p.lifespan, ereignisse(p.facts), bedienung).filter(String::isNotBlank).joinToString("\n")
        Tipp(tipp) {
            Row(Modifier.fillMaxWidth().fokusRahmen()
                .combinedClickable(enabled = !p.isPrivate && viewModel != null,
                    onDoubleClick = { if (onBlatt != null) onBlatt(p.xref) else viewModel?.setRoot(p.xref) }) { viewModel?.select(p.xref) }
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name + jahre(p.person()).let { if (it.isNotEmpty()) "  $it" else "" },
                    Modifier.width(340.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ereignisse(p.facts), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            }
        }
    }
    if (o.moreIndividuals > 0) Text(stringResource(Res.string.desk_source_more, o.moreIndividuals), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
    if (o.families.isNotEmpty()) {
        Text(stringResource(Res.string.desk_place_families), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = farben.outlineVariant)
    }
    o.families.forEach { f ->
        // Klick waehlt den Ehemann (sonst die Ehefrau) - die Familie selbst hat keine eigene Ansicht
        val wer = f.husband ?: f.wife
        Row(Modifier.fillMaxWidth().fokusRahmen().clickable(enabled = wer != null && viewModel != null) { wer?.let { viewModel?.select(it) } }.padding(vertical = 4.dp)) {
            Text(f.name, Modifier.width(340.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ereignisse(f.facts), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        }
    }
    if (o.moreFamilies > 0) Text(stringResource(Res.string.desk_source_more, o.moreFamilies), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
}

@Composable
private fun Daten(o: PlaceDetail, onWahl: (String) -> Unit, openWeb: (String) -> Unit, userAgent: String? = null) {
    val farben = MaterialTheme.colorScheme
    val loc = o.location
    // Von aussen (GOV, Wikimedia, GenWiki) - im Hintergrund, 7 Tage zwischengespeichert
    val aussen by produceState<OrtAussen?>(null, o.name, loc?.gov, o.lat, o.lng) {
        value = withContext(Dispatchers.IO) { runCatching { ortAussen(o, userAgent) }.getOrNull() ?: OrtAussen() }
    }
    @Composable
    fun Zeile(label: StringResource, inhalt: @Composable () -> Unit) {
        Row(Modifier.padding(vertical = 2.dp)) {
            Text(stringResource(label), Modifier.width(160.dp), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
            Column { inhalt() }
        }
    }
    @Composable
    fun Verweis(text: String, onClick: () -> Unit) =
        Text(text, Modifier.clickable(onClick = onClick), style = MaterialTheme.typography.bodyMedium, color = farben.primary)

    OrtKopf(o, aussen, openWeb) {
        Zeile(Res.string.desk_place_levels) { Text(o.levels.joinToString(" › "), style = MaterialTheme.typography.bodyMedium) }
        // Art aus dem Ortsdatensatz (Hof, Haus, Gemeinde ...) - macht aus einem Unterort ein Gebaeude
        // Art als Text und, wenn vorhanden, die GOV-Typnummer (GEDCOM-L 2 _GOVTYPE) mit GOV-Namen - sie entscheidet im Haeuserteil
        val govTyp = loc?.govType?.takeIf(String::isNotBlank)?.let { nr ->
            de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_place_gov_type, nr) +
                (de.bgghome.webtrees.nativ.data.GovTypen.name(nr, de.bgghome.webtrees.nativ.Sprache.aktiv)?.let { " ($it)" } ?: "")
        }
        listOfNotNull(loc?.type?.takeIf(String::isNotBlank), govTyp).takeIf { it.isNotEmpty() }
            ?.let { Zeile(Res.string.desk_place_type) { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium) } }
        o.parent?.let { p -> Zeile(Res.string.desk_place_parent) { Verweis(p) { onWahl(p) } } }
        // Uebergeordnete Orte aus der _LOC-Hierarchie (GEDCOM-L), wenn sie vom Ortsnamen abweichen oder datiert sind
        loc?.parents?.takeIf { ps -> ps.any { it.fullName != o.parent || it.date != null } }?.let { ps ->
            Zeile(Res.string.desk_place_loc_parents) {
                ps.forEach { p ->
                    val zusatz = listOfNotNull(p.type, p.date?.text?.takeIf(String::isNotBlank)).joinToString(", ").let { if (it.isEmpty()) "" else "  ($it)" }
                    Verweis(p.fullName.ifBlank { p.name } + zusatz) { onWahl(p.fullName.ifBlank { p.name }) }
                }
            }
        }
        if (o.children.isNotEmpty()) Zeile(Res.string.desk_place_children) {
            o.children.sortedWith(compareBy(de.bgghome.webtrees.nativ.data.Ortsnamen.NATUERLICH) { it.name }).forEach { c ->
                val zusatz = listOfNotNull(c.type, c.events.takeIf { it > 0 }?.toString()).joinToString(", ").let { if (it.isEmpty()) "" else "  ($it)" }
                Verweis(de.bgghome.webtrees.nativ.data.Ortsnamen.blatt(c.name) + zusatz) { onWahl(c.name) }
            }
        }
        Zeile(Res.string.desk_place_events) { Text("${o.events}", style = MaterialTheme.typography.bodyMedium) }
        Zeile(Res.string.desk_place_record) {
            if (loc == null) Text(stringResource(Res.string.desk_place_no_record), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
            else Text(listOf(loc.name, loc.xref).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
        // Kurzname, Postleitzahl, Region, Land - so wie andere Programme sie am Ortsdatensatz ablegen
        listOf(Res.string.desk_place_short to loc?.shortName, Res.string.desk_place_postal to loc?.postalCode, Res.string.desk_place_region to loc?.region, Res.string.desk_place_country to loc?.country)
            .forEach { (label, wert) -> if (!wert.isNullOrBlank()) Zeile(label) { SelectionContainer { Text(wert, style = MaterialTheme.typography.bodyMedium) } } }
        loc?.gov?.let { gov ->
            Zeile(Res.string.desk_place_gov) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectionContainer { Text(gov, style = MaterialTheme.typography.bodyMedium) }
                    Verweis(stringResource(Res.string.desk_place_gov_show)) { openWeb("https://gov.genealogy.net/item/show/$gov") }
                }
            }
        }
    }
}

@Composable
private fun Koordinaten(o: PlaceDetail, openWeb: (String) -> Unit) {
    val lat = o.lat; val lng = o.lng
    val farben = MaterialTheme.colorScheme
    if (lat == null || lng == null) {
        Text(stringResource(Res.string.desk_place_no_coords), Modifier.padding(20.dp), color = farben.onSurfaceVariant)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row { Text(stringResource(Res.string.desk_place_decimal), Modifier.width(200.dp), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                SelectionContainer { Text(dezimal(lat, lng), style = MaterialTheme.typography.bodyMedium) } }
            Row { Text(stringResource(Res.string.desk_place_dms), Modifier.width(200.dp), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                SelectionContainer { Text(gms(lat, true) + "   " + gms(lng, false), style = MaterialTheme.typography.bodyMedium) } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (o.coordSource) {
                    "location" -> Res.string.desk_place_coords_location
                    "mapData" -> Res.string.desk_place_coords_mapdata
                    "event" -> Res.string.desk_place_coords_event
                    else -> null
                }?.let { Text(stringResource(it), Modifier.width(200.dp), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant) }
                OutlinedButton(onClick = { openWeb("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=13/$lat/$lng") }, shape = MaterialTheme.shapes.small) {
                    Text("OpenStreetMap")
                }
                OutlinedButton(onClick = { openWeb("https://www.bing.com/maps?cp=$lat~$lng&lvl=13&sp=point.${lat}_${lng}") }, shape = MaterialTheme.shapes.small) {
                    Text("Bing Maps")
                }
                OutlinedButton(onClick = { openWeb("https://www.google.com/maps/search/?api=1&query=$lat,$lng") }, shape = MaterialTheme.shapes.small) {
                    Text("Google Maps")
                }
            }
        }
        HorizontalDivider(color = farben.outlineVariant)
        val zustand = remember(o.name) { KartenZustand() }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val w = constraints.maxWidth; val h = constraints.maxHeight
            LaunchedEffect(o.name, w, h) { zustand.passeEin(listOf(GeoPunkt(lat, lng)), w, h, einzelZoom = 12, maxZoom = 15) }
            KachelKarte(zustand, KachelEbene.STANDARD, Modifier.fillMaxSize(), pins = listOf(KartenPin(lat, lng, farbe = farben.primary, radiusDp = 9f)))
            // Nach Verschieben und Zoomen: den Ort wieder in die Mitte
            androidx.compose.material3.TextButton(onClick = { zustand.setze(lat, lng, 12) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(farben.surface.copy(alpha = 0.9f), MaterialTheme.shapes.small)) {
                Text(stringResource(Res.string.desk_place_center), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

internal fun dezimal(lat: Double, lng: Double) = "%.6f, %.6f".format(java.util.Locale.ROOT, lat, lng)

/** 53,778417 -> 53° 46′ 42,3″ N */
internal fun gms(wert: Double, breite: Boolean): String {
    val a = abs(wert)
    var grad = a.toInt()
    var min = ((a - grad) * 60).toInt()
    var sek = ((a - grad - min / 60.0) * 3600 * 10).roundToInt() / 10.0
    if (sek >= 60) { sek -= 60; min++ }
    if (min >= 60) { min -= 60; grad++ }
    val seite = if (breite) (if (wert < 0) "S" else "N") else (if (wert < 0) "W" else "E")
    return "$grad° $min′ ${"%.1f".format(java.util.Locale.ROOT, sek)}″ $seite"
}

/** Rueckfrage vor dem Umbenennen/Zusammenfuehren mit den Zahlen aus der Vorschau. */
@Composable
private fun UmbenennenDialog(v: de.bgghome.webtrees.nativ.api.PlaceRenameResult, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val text = buildList {
        add(stringResource(Res.string.desk_place_rename_text, v.from, v.to, v.events, v.records))
        if (v.subPlaces > 0) add(stringResource(Res.string.desk_place_rename_sub, v.subPlaces))
        if (v.merge) add(stringResource(Res.string.desk_place_merge_text, v.to))
        if ("gov" in v.location.conflicts) add(stringResource(Res.string.desk_place_conflict_gov, v.to))
        if ("coordinates" in v.location.conflicts) add(stringResource(Res.string.desk_place_conflict_coords, v.to))
        if (v.skipped > 0) add(stringResource(Res.string.desk_place_rename_skipped, v.skipped))
    }.joinToString("\n\n")
    de.bgghome.webtrees.nativ.ui.ConfirmDialog(
        title = stringResource(if (v.merge) Res.string.desk_place_merge_title else Res.string.desk_place_rename_title),
        text = text,
        confirm = stringResource(if (v.merge) Res.string.desk_place_merge_do else Res.string.desk_place_rename_do),
        onDismiss = onDismiss, onConfirm = onConfirm,
    )
}

private val farbeWenig = androidx.compose.ui.graphics.Color(0xFF6FA8AE)
private val farbeMittel = androidx.compose.ui.graphics.Color(0xFF1F6F78)
private val farbeViel = androidx.compose.ui.graphics.Color(0xFF0B3D44)
private val farbeGruppe = androidx.compose.ui.graphics.Color(0xFF6B7276)
private val farbeGewaehlt = androidx.compose.ui.graphics.Color(0xFFC0582A)

/** Farbe nach Zahl der Ereignisse wie im Ortsregister: wenige hell, viele dunkel. */
private fun ortFarbe(ereignisse: Int) = when { ereignisse >= 100 -> farbeViel; ereignisse >= 20 -> farbeMittel; else -> farbeWenig }

/** Durchmesser 18-32 px nach Wurzel der Ereignisse (wie im Ortsregister) - hier als Radius in dp. */
private fun ortRadius(ereignisse: Int) = (kotlin.math.sqrt(ereignisse.toDouble()) * 6).coerceIn(18.0, 32.0).toFloat() / 2

/**
 * Orte, die auf der Karte naeher als etwa 50 Pixel beieinander liegen, als eine Gruppe - je Zoomstufe neu, wie
 * MarkerCluster im Ortsregister.
 */
internal fun ortsGruppen(orte: List<de.bgghome.webtrees.nativ.api.PlaceSummary>, zoom: Int, zelle: Double = 50.0): List<List<de.bgghome.webtrees.nativ.api.PlaceSummary>> =
    orte.filter { it.lat != null && it.lng != null }.groupBy { o ->
        val x = de.bgghome.webtrees.nativ.ui.karte.WebMercator.xTile(o.lng!!, zoom) * 256 / zelle
        val y = de.bgghome.webtrees.nativ.ui.karte.WebMercator.yTile(o.lat!!, zoom) * 256 / zelle
        kotlin.math.floor(x).toLong() to kotlin.math.floor(y).toLong()
    }.values.toList()

/** Karte aller Orte mit Koordinaten: Groesse und Farbe nach Ereignissen, Gruppen je Zoomstufe, Klick waehlt. */
@Composable
private fun OrtsKarte(orte: List<de.bgghome.webtrees.nativ.api.PlaceSummary>, gewaehlt: String?, onWahl: (String) -> Unit, onAnzeigen: (String) -> Unit) {
    val mit = remember(orte) { orte.filter { it.lat != null && it.lng != null } }
    val farben = MaterialTheme.colorScheme
    if (mit.isEmpty()) {
        Text(stringResource(Res.string.desk_places_none_on_map), Modifier.padding(24.dp), color = farben.onSurfaceVariant)
        return
    }
    val zustand = remember { KartenZustand() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        // Alle (gefundenen) Orte ins Bild - neu, wenn die Suche die Auswahl aendert
        LaunchedEffect(mit, w, h) { zustand.passeEin(mit.map { GeoPunkt(it.lat!!, it.lng!!) }, w, h, randPx = 40, einzelZoom = 10, maxZoom = 12) }
        // Erst spaeter in der Liste gewaehlt: in die Mitte holen (beim Oeffnen bleiben alle Orte im Bild)
        var vorher by remember { mutableStateOf(gewaehlt) }
        LaunchedEffect(gewaehlt) {
            if (gewaehlt != vorher) mit.firstOrNull { it.name.equals(gewaehlt, ignoreCase = true) }?.let { zustand.setze(it.lat!!, it.lng!!, maxOf(zustand.zoom, 9)) }
            vorher = gewaehlt
        }
        // Der gewaehlte Ort steht immer einzeln, die uebrigen werden gruppiert
        val gruppen = remember(mit, zustand.zoom, gewaehlt) {
            val (ich, rest) = mit.partition { it.name.equals(gewaehlt, ignoreCase = true) }
            ortsGruppen(rest, zustand.zoom) + ich.map { listOf(it) }
        }
        val pins = gruppen.map { g ->
            val ereignisse = g.sumOf { it.events }
            val drin = g.any { it.name.equals(gewaehlt, ignoreCase = true) }
            if (g.size == 1) KartenPin(g[0].lat!!, g[0].lng!!, text = "$ereignisse", farbe = if (drin) farbeGewaehlt else ortFarbe(ereignisse), radiusDp = ortRadius(ereignisse), tag = g[0])
            else KartenPin(g.map { it.lat!! }.average(), g.map { it.lng!! }.average(), text = "${g.size}",
                farbe = if (drin) farbeGewaehlt else farbeGruppe, radiusDp = ortRadius(ereignisse), tag = g)
        }
        KachelKarte(zustand, KachelEbene.STANDARD, Modifier.fillMaxSize(), pins = pins, onPinTap = { p ->
            when (val t = p.tag) {
                is de.bgghome.webtrees.nativ.api.PlaceSummary -> onWahl(t.name)
                // Gruppe: hineinzoomen, bis sie zerfaellt
                is List<*> -> zustand.setze(p.lat, p.lon, (zustand.zoom + 2).coerceAtMost(KachelEbene.STANDARD.maxZoom))
            }
        })
        // Oben: wie viele Orte fehlen
        val ohne = orte.size - mit.size
        if (ohne > 0) Text(stringResource(Res.string.desk_places_without_coords, ohne, orte.size),
            Modifier.align(Alignment.TopStart).padding(8.dp).background(farben.surface.copy(alpha = 0.92f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
        // Unten rechts: Legende
        Column(Modifier.align(Alignment.BottomEnd).padding(8.dp).background(farben.surface.copy(alpha = 0.92f), MaterialTheme.shapes.small).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(Res.string.desk_places_legend), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            listOf(farbeWenig to "1–19", farbeMittel to "20–99", farbeViel to "100+").forEach { (f, t) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(f, androidx.compose.foundation.shape.CircleShape))
                    Text(t, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(farbeGruppe, androidx.compose.foundation.shape.CircleShape))
                Text(stringResource(Res.string.desk_places_legend_cluster), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        // Unten links: der gewaehlte Ort mit Knopf zur Ansicht
        mit.firstOrNull { it.name.equals(gewaehlt, ignoreCase = true) }?.let { o ->
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp).background(farben.surface, MaterialTheme.shapes.small)
                .border(1.dp, farben.outlineVariant, MaterialTheme.shapes.small).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.widthIn(max = 360.dp)) {
                    Text(o.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${o.events} " + stringResource(Res.string.desk_place_events) + " · ${o.individuals} " + stringResource(Res.string.desk_col_persons),
                        style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
                }
                androidx.compose.material3.TextButton(onClick = { onAnzeigen(o.name) }) { Text(stringResource(Res.string.desk_places_show)) }
            }
        }
    }
}

/** Was die Ortsseite von aussen holt; leer, solange es nichts gibt. */
internal data class OrtAussen(
    val heute: List<GovStufe> = emptyList(), val frueher: List<GovStufe> = emptyList(),
    val wiki: OrtWikimedia = OrtWikimedia(null, null, null), val genWiki: String? = null, val extern: List<Pair<String, String>> = emptyList(),
)

internal fun ortAussen(o: PlaceDetail, userAgent: String?): OrtAussen {
    userAgent?.let { OrtExtern.userAgent = it }
    val gov = o.location?.gov
    val obj = gov?.let { OrtExtern.govObjekt(it) }
    val (heute, frueher) = gov?.let { OrtExtern.govKetten(it) } ?: (emptyList<GovStufe>() to emptyList())
    val qid = obj?.extern?.firstOrNull { it.startsWith("wikidata:", ignoreCase = true) }?.substringAfter(':')
    val wiki = OrtExtern.wikimedia(o.levels.firstOrNull() ?: o.name, o.lat, o.lng, qid, suche = o.levels.take(2).joinToString(" ").ifBlank { o.name })
    val extern = OrtExtern.externeLinks(obj?.extern.orEmpty()) +
        (if (qid == null && wiki.qid != null) listOf("Wikidata" to "https://www.wikidata.org/wiki/${wiki.qid}") else emptyList())
    return OrtAussen(heute, frueher, wiki, gov?.let { OrtExtern.genWiki(it) }, extern)
}

/** Kopf der Ortsseite wie im Ortsregister: Titelbild, Kacheln, GOV-Hierarchie, Nachschlagen. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OrtKopf(o: PlaceDetail, aussen: OrtAussen?, openWeb: (String) -> Unit, angaben: @Composable () -> Unit) {
    val farben = MaterialTheme.colorScheme
    val leaf = o.levels.firstOrNull() ?: o.name
    // Titelbild: ein eigenes Foto am Ortsdatensatz geht vor, sonst der Vorschlag aus Wikimedia Commons
    val eigenes = o.location?.media?.firstOrNull { it.isImage && it.thumb != null }
    val vorschlag = aussen?.wiki?.bild
    // Bilder aus Wikimedia Commons zum Grossansehen: der Vorschlag (wenn er oben steht) und die Galerie darunter
    val commons = listOfNotNull(vorschlag.takeIf { eigenes == null }) + aussen?.wiki?.galerie.orEmpty()
    var gross by remember(o.name) { mutableStateOf<Int?>(null) }
    gross?.let { i -> CommonsBetrachter(commons, i, openWeb) { gross = null } }
    val bild: @Composable () -> Unit = { if (eigenes != null || vorschlag != null) {
        Box(Modifier.fillMaxWidth().height(200.dp).border(1.dp, farben.outlineVariant)
            .then(if (eigenes == null && vorschlag != null) Modifier.clickable { gross = 0 } else Modifier)) {
            coil3.compose.AsyncImage(model = eigenes?.thumb ?: vorschlag?.bild, contentDescription = o.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (eigenes == null && vorschlag != null) {
                val nachweis = listOfNotNull(vorschlag.urheber?.let { "© $it" }, vorschlag.lizenz, stringResource(Res.string.desk_place_image_hint)).joinToString(" · ")
                Text(nachweis, Modifier.align(Alignment.BottomEnd).background(farben.surface.copy(alpha = 0.85f)).clickable { openWeb(vorschlag.seite) }
                    .padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    // Galerie: weitere Bilder aus Wikimedia Commons, nur angezeigt (nichts davon steht im Stammbaum)
    val galerie = aussen?.wiki?.galerie.orEmpty()
    if (galerie.isNotEmpty()) {
        Text(stringResource(Res.string.desk_place_gallery), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Sechs gleich breite Quadrate, die sich nach der Spalte richten
            (0 until 6).forEach { i ->
                val b = galerie.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(1f)) {
                    if (b != null) Tipp(listOfNotNull(b.urheber?.let { "© $it" }, b.lizenz).joinToString(" · ").ifBlank { null }) {
                        coil3.compose.AsyncImage(model = b.bild, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().border(1.dp, farben.outlineVariant).clickable { gross = commons.indexOf(b).coerceAtLeast(i) })
                    }
                }
            }
        }
    }
    }
    // Kacheln: Ereignisse nach Art
    val kacheln: @Composable (Int) -> Unit = { jeZeile -> o.eventCounts?.let { z ->
        listOf(z.birth to Res.string.desk_place_births, z.marriage to Res.string.desk_place_marriages,
            z.death to Res.string.desk_place_deaths, z.other to Res.string.desk_place_other_events).chunked(jeZeile).forEach { reihe ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reihe.forEach { (n, t) ->
                Column(Modifier.weight(1f).border(1.dp, farben.outlineVariant, MaterialTheme.shapes.small).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", style = MaterialTheme.typography.headlineSmall, color = if (n > 0) farben.primary else farben.outline)
                    Text(stringResource(t), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
                }
            }
        }
        }
    } }
    // GOV-Hierarchie (von oben nach unten), sonst die Ebenen des Ortsnamens
    val hierarchie: @Composable () -> Unit = {
    if (aussen == null && o.location?.gov != null) Text(stringResource(Res.string.desk_place_loading), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
    aussen?.heute?.takeIf { it.size > 1 }?.let { heute ->
        Text(stringResource(Res.string.desk_place_hierarchy), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        @Composable
        fun Kette(label: String, k: List<GovStufe>) = Row {
            Text(label, Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            SelectionContainer {
                // Stadt und gleichnamiger Kreis (GOV: zwei Objekte) nur einmal
                val namen = k.reversed().map { it.name }.fold(listOf<String>()) { acc, n -> if (acc.lastOrNull() == n) acc else acc + n }
                Text(namen.joinToString(" › "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Kette(stringResource(Res.string.desk_place_today), heute)
        aussen.frueher.takeIf { it.size > 1 }?.let { f ->
            val zeit = f.getOrNull(1)?.let { st -> listOfNotNull(st.von, st.bis).joinToString("–") }.orEmpty()
            Kette(stringResource(Res.string.desk_place_earlier) + if (zeit.isNotEmpty()) " ($zeit)" else "", f)
        }
    }
    }
    // Nachschlagen: GOV, GenWiki, Wikipedia, Archivportale, externe Kennungen aus GOV
    val gov = o.location?.gov
    val links = buildList {
        add("GOV" to (gov?.let { "https://gov.genealogy.net/item/show/$it" } ?: ("https://gov.genealogy.net/search/name?name=" + java.net.URLEncoder.encode(leaf, "UTF-8"))))
        aussen?.genWiki?.let { add("GenWiki" to it) }
        add("Wikipedia" to (aussen?.wiki?.wikipedia ?: ("https://${java.util.Locale.getDefault().language}.wikipedia.org/w/index.php?search=" + java.net.URLEncoder.encode(leaf, "UTF-8"))))
        addAll(OrtExtern.suchLinks(leaf))
        addAll(aussen?.extern.orEmpty())
    }
    val linkleiste: @Composable () -> Unit = {
    Text(stringResource(Res.string.desk_place_links), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        links.forEach { (name, url) ->
            Text("$name ↗", Modifier.border(1.dp, farben.outlineVariant, MaterialTheme.shapes.small).clickable { openWeb(url) }.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium, color = farben.primary)
        }
    }
    Text(stringResource(Res.string.desk_place_external_note), style = MaterialTheme.typography.labelSmall, color = farben.outline)
    }
    // Breit: links Bild und Angaben, rechts Kacheln, Hierarchie und Nachschlagen - so passt meist alles ohne Rollen
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 640.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { bild(); angaben() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { kacheln(2); hierarchie(); linkleiste() }
        } else Column {
            bild(); kacheln(4); hierarchie(); linkleiste()
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = farben.outlineVariant)
            angaben()
        }
    }
}

/** Was die Reiter zum Schreiben brauchen - nur mit Bearbeitungsrecht und Server ab API-Stufe 22. */
class OrtPflege(val tree: String, val viewModel: AppViewModel, val archive: de.bgghome.webtrees.nativ.api.ArchiveOverview?, val onGeaendert: () -> Unit) {
    /** Der _LOC des Orts - fehlt er, wird er angelegt (leer, nur mit dem Namen). */
    suspend fun locXref(o: PlaceDetail): String = o.location?.xref
        ?: withContext(Dispatchers.IO) { viewModel.client.savePlace(tree, de.bgghome.webtrees.nativ.api.PlaceRequest(o.name)).xref }
}

/** Notiz am Ortsdatensatz direkt schreiben; weitere Notizen (z. B. Notiz-Datensaetze) darunter nur lesend. */
@Composable
private fun OrtNotizen(o: PlaceDetail, pflege: OrtPflege?) {
    val notizen = o.location?.notes.orEmpty()
    if (pflege == null) {
        if (notizen.isEmpty()) Leer() else notizen.forEach { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        return
    }
    val alt = notizen.firstOrNull().orEmpty()
    var text by remember(o.name, alt) { mutableStateOf(alt) }
    var speichert by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 8,
        label = { Text(stringResource(Res.string.fact_note)) })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            speichert = true; fehler = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { pflege.viewModel.client.savePlace(pflege.tree, de.bgghome.webtrees.nativ.api.PlaceRequest(o.name, note = text.trim())) } }
                    .onSuccess { pflege.onGeaendert() }.onFailure { fehler = it.message ?: "?" }
                speichert = false
            }
        }, enabled = text.trim() != alt.trim() && !speichert, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.action_save)) }
        fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    notizen.drop(1).forEach { SelectionContainer { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium) } }
}

/** Quellen am Ortsdatensatz; mit Bearbeitungsrecht "Quelle zitieren" (allgemeiner Verweis am _LOC). */
@Composable
private fun OrtQuellen(o: PlaceDetail, pflege: OrtPflege?, openWeb: ((String) -> Unit)? = null) {
    val q = o.location?.sources.orEmpty()
    if (q.isEmpty() && pflege == null) Leer()
    q.forEach { s ->
        val titel = s.title ?: s.xref.orEmpty()
        // Eine Quelle als reiner Text kann eine Internetadresse sein (so schreiben es andere Programme) - dann anklickbar
        val adresse = titel.trim().takeIf { s.xref == null && (it.startsWith("http://") || it.startsWith("https://")) }
        Row {
            Text(titel, Modifier.width(360.dp).then(if (adresse != null && openWeb != null) Modifier.clickable { openWeb(adresse) } else Modifier),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (adresse != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(s.page.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (pflege == null) return
    var ziel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    OutlinedButton(onClick = { scope.launch { runCatching { pflege.locXref(o) }.onSuccess { ziel = it } } }, shape = MaterialTheme.shapes.small) {
        Text("+ " + stringResource(Res.string.desk_cite_add))
    }
    ziel?.let { x -> ZitatDialog(ZitatZiel(x, null, null, null), pflege.tree, pflege.viewModel, onDismiss = { ziel = null; pflege.onGeaendert() }) }
}

/** Fotos und Dokumente am Ortsdatensatz: Datei hochladen, vorhandenes Medium (auch aus dem Archiv) verknuepfen, loesen. */
@Composable
private fun OrtMedien(o: PlaceDetail, pflege: OrtPflege?, openWeb: (String) -> Unit) {
    val m = o.location?.media.orEmpty()
    if (m.isEmpty() && pflege == null) { Leer(); return }
    val scope = rememberCoroutineScope()
    var fehler by remember { mutableStateOf<String?>(null) }
    var waehlen by remember { mutableStateOf<String?>(null) }
    var bearbeiten by remember { mutableStateOf<de.bgghome.webtrees.nativ.api.MediaJson?>(null) }
    var ziehtDarueber by remember { mutableStateOf(false) }
    fun medienSetzen(neu: List<String>) {
        if (pflege == null) return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { pflege.viewModel.client.savePlace(pflege.tree, de.bgghome.webtrees.nativ.api.PlaceRequest(o.name, media = neu.distinct())) } }
                .onSuccess { pflege.onGeaendert() }.onFailure { fehler = it.message ?: "?" }
        }
    }
    fun hochladen(dateien: List<java.io.File>) {
        if (pflege == null || dateien.isEmpty()) return
        scope.launch {
            runCatching {
                val x = pflege.locXref(o)
                dateien.forEach { datei ->
                    val bytes = withContext(Dispatchers.IO) { datei.readBytes() }
                    withContext(Dispatchers.IO) { pflege.viewModel.client.uploadMedia(pflege.tree, x, bytes, datei.name, mimeVon(datei), titelAusDatei(datei)) }
                }
            }.onSuccess { pflege.onGeaendert() }.onFailure { fehler = it.message ?: "?" }
        }
    }
    // Dateien aus dem Dateimanager hineinziehen; waehrend des Ziehens ist der Bereich umrandet
    val ablage = remember(pflege, o.name) {
        object : androidx.compose.ui.draganddrop.DragAndDropTarget {
            override fun onEntered(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { ziehtDarueber = true }
            override fun onExited(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { ziehtDarueber = false }
            override fun onEnded(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { ziehtDarueber = false }
            override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
                ziehtDarueber = false
                val liste = event.dragData() as? androidx.compose.ui.draganddrop.DragData.FilesList ?: return false
                val dateien = liste.readFiles().mapNotNull { runCatching { java.io.File(java.net.URI(it)) }.getOrNull() }.filter { it.isFile }
                hochladen(dateien)
                return dateien.isNotEmpty()
            }
        }
    }
    Column(
        (if (pflege != null) Modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = ablage) else Modifier)
            .fillMaxWidth().border(if (ziehtDarueber) 2.dp else 0.dp, if (ziehtDarueber) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.small)
            .padding(if (ziehtDarueber) 6.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (m.isNotEmpty()) MedienReihe(m, openWeb,
            onLoesen = if (pflege != null) ({ x -> medienSetzen(m.map { it.xref }.filter { it != x.xref }) }) else null,
            onBearbeiten = if (pflege != null) ({ x -> bearbeiten = x }) else null)
        if (pflege != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dateiOeffnen(scanDialogTitel())?.let { hochladen(listOf(it)) } }, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(Res.string.desk_source_add_file))
                }
                OutlinedButton(onClick = { scope.launch { runCatching { pflege.locXref(o) }.onSuccess { waehlen = it }.onFailure { fehler = it.message } } },
                    shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_media_existing)) }
            }
            Text(stringResource(Res.string.desk_media_drop_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (pflege != null) waehlen?.let { x ->
        MedienWahlDialog(pflege.tree, pflege.viewModel.client, m.map { it.xref }.toSet(), onDismiss = { waehlen = null },
            archive = pflege.archive, rechteXref = x) { neu -> waehlen = null; medienSetzen(m.map { it.xref } + neu.xref) }
    }
    if (pflege != null) bearbeiten?.let { medium ->
        MediumDialog(medium, onDismiss = { bearbeiten = null }) { titel, art ->
            bearbeiten = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { pflege.viewModel.client.mediaObject(pflege.tree, medium.xref, titel, art) } }
                    .onSuccess { pflege.onGeaendert() }.onFailure { fehler = it.message ?: "?" }
            }
        }
    }
}

/** Titel und Art eines Mediums; gesendet wird nur, was sich geaendert hat. */
@Composable
internal fun MediumDialog(m: de.bgghome.webtrees.nativ.api.MediaJson, onDismiss: () -> Unit, onSave: (String?, String?) -> Unit) {
    var titel by remember { mutableStateOf(m.title) }
    var art by remember { mutableStateOf(m.type.orEmpty()) }
    val arten = listOf("" to stringResource(Res.string.desk_media_type_none), "photo" to stringResource(Res.string.desk_media_type_photo),
        "document" to stringResource(Res.string.desk_media_type_document), "card" to stringResource(Res.string.desk_media_type_card),
        "map" to stringResource(Res.string.desk_media_type_map), "certificate" to stringResource(Res.string.desk_media_type_certificate),
        "other" to stringResource(Res.string.desk_media_type_other))
    de.bgghome.webtrees.nativ.ui.WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_media_edit_title)) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(titel, { titel = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(Res.string.desk_media_title)) })
                Einstellung(stringResource(Res.string.desk_media_type)) {
                    Auswahl(arten.firstOrNull { it.first == art }?.second ?: art, arten.map { it.second }) { w -> art = arten.first { it.second == w }.first }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = titel.trim() != m.title || art != m.type.orEmpty(), onClick = {
                onSave(titel.trim().takeIf { it != m.title }, art.takeIf { it != m.type.orEmpty() })
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Grossansicht der Bilder aus Wikimedia Commons: blaettern mit Knoepfen oder Pfeiltasten, Bildnachweis, Link zur Quelle. */
@Composable
private fun CommonsBetrachter(bilder: List<OrtBild>, start: Int, openWeb: (String) -> Unit, onClose: () -> Unit) {
    if (bilder.isEmpty()) return
    var i by remember { mutableStateOf(start.coerceIn(0, bilder.lastIndex)) }
    val b = bilder[i]
    val fokus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fokus.requestFocus() } }
    fun weiter(d: Int) { i = (i + d + bilder.size) % bilder.size }
    WtAlertDialog(
        onDismissRequest = onClose,
        title = { Text("${i + 1} / ${bilder.size}") },
        text = {
            Column(Modifier.width(960.dp).focusRequester(fokus).focusable().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                    Key.DirectionLeft -> { weiter(-1); true }
                    Key.DirectionRight -> { weiter(1); true }
                    else -> false
                }
            }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                coil3.compose.AsyncImage(model = b.bild, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(600.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOfNotNull(b.urheber?.let { "© $it" }, b.lizenz).joinToString(" · "), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.desk_place_gallery_source) + " ↗", Modifier.clickable { openWeb(b.seite) }.padding(4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        breite = 1000.dp,
        confirmButton = {
            Row {
                androidx.compose.material3.TextButton(onClick = { weiter(-1) }, enabled = bilder.size > 1) { Text("‹") }
                androidx.compose.material3.TextButton(onClick = { weiter(1) }, enabled = bilder.size > 1) { Text("›") }
                androidx.compose.material3.TextButton(onClick = onClose) { Text(stringResource(Res.string.action_close)) }
            }
        },
    )
}
