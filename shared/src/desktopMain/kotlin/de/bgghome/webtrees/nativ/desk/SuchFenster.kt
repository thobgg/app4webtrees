package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.data.ErsetzFeld
import de.bgghome.webtrees.nativ.data.Ersetzung
import de.bgghome.webtrees.nativ.data.KennArt
import de.bgghome.webtrees.nativ.data.KennLage
import de.bgghome.webtrees.nativ.data.SuchAnfrage
import de.bgghome.webtrees.nativ.data.SuchArt
import de.bgghome.webtrees.nativ.data.SuchFeld
import de.bgghome.webtrees.nativ.data.SuchTreffer
import de.bgghome.webtrees.nativ.data.Suche
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.bookmarksSupported
import de.bgghome.webtrees.nativ.ui.isBookmarked
import de.bgghome.webtrees.nativ.ui.setRoot
import de.bgghome.webtrees.nativ.ui.toggleBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Suchen und Ersetzen (Strg+F): vier Reiter - Suchen (alle Felder, genau/alle Woerter/eines der Woerter, ganzes Feld,
 * Gross/Klein, phonetisch), Suchliste (Treffer als Datei speichern und laden), Ersetzen (mit Vorschau, nur Orte,
 * Berufe, Notizen, Ereigniswerte), Kennzeichen (Kennung, REFN, UID). Die Trefferliste darunter ist allen gemeinsam;
 * "nur in den bisherigen Treffern" verengt die naechste Suche. Grundlage ist der Baum-Export, kein Server noetig.
 */

private enum class Reiter(val titel: StringResource) {
    Suchen(Res.string.desk_search_tab_search), Liste(Res.string.desk_search_tab_list), Ersetzen(Res.string.desk_search_tab_replace), Kennzeichen(Res.string.desk_search_tab_marks)
}

internal val SuchFeld.titel: StringResource get() = when (this) {
    SuchFeld.Namen -> Res.string.desk_search_field_names; SuchFeld.Beruf -> Res.string.desk_search_field_occupation
    SuchFeld.Daten -> Res.string.desk_search_field_dates; SuchFeld.Orte -> Res.string.desk_search_field_places
    SuchFeld.Quellen -> Res.string.desk_search_field_sources; SuchFeld.Notizen -> Res.string.desk_search_field_notes
    SuchFeld.Eltern -> Res.string.desk_search_field_parents; SuchFeld.Partner -> Res.string.desk_search_field_partners
    SuchFeld.Geschwister -> Res.string.desk_search_field_siblings; SuchFeld.Kinder -> Res.string.desk_search_field_children
}

private val ErsetzFeld.titel: StringResource get() = when (this) {
    ErsetzFeld.Orte -> Res.string.desk_search_field_places; ErsetzFeld.Beruf -> Res.string.desk_search_field_occupation
    ErsetzFeld.Notizen -> Res.string.desk_search_field_notes; ErsetzFeld.Werte -> Res.string.desk_search_field_values
}

@Composable
fun SuchFenster(state: UiState, viewModel: AppViewModel, openSheet: (String) -> Unit, onTabelle: (Set<String>) -> Unit, onClose: () -> Unit) {
    androidx.compose.ui.window.Window(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_search_window) + " – " + state.tree?.title.orEmpty(),
        state = arbeitsfenster("fenster_suche", 980, 760),
        onPreviewKeyEvent = { e ->
            when {
                e.type == KeyEventType.KeyDown && e.key == Key.Escape -> { onClose(); true }
                e.type == KeyEventType.KeyDown && e.key == Key.F1 -> { Hilfe.oeffnen("suche"); true }
                else -> false
            }
        },
    ) {
        DeskTheme { SuchInhalt(state, viewModel, openSheet, onTabelle) }
    }
}

/** Der Inhalt ohne Fenster - so laesst er sich auch ohne Bildschirm zeichnen. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SuchInhalt(state: UiState, viewModel: AppViewModel, openSheet: (String) -> Unit, onTabelle: (Set<String>) -> Unit, vorgabeBaum: TreeExport? = null, vorgabeText: String = "") {
    val farben = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var neu by remember { mutableIntStateOf(0) }
    var fortschritt by remember { mutableStateOf(0 to 0) }
    val baum by produceState<Result<TreeExport>?>(vorgabeBaum?.let { Result.success(it) }, state.tree?.name, neu) {
        if (vorgabeBaum != null) return@produceState
        value = null
        val tree = state.tree ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                BaumSpeicher.holen(viewModel.client, tree.name, Int.MAX_VALUE / 64) { a, n -> fortschritt = a to n }
                    ?: throw IllegalStateException(Texte.t(Res.string.desk_list_needs_export))
            }
        }
    }
    var reiter by remember { mutableStateOf(Reiter.Suchen) }
    // Suchen
    var text by remember { mutableStateOf(vorgabeText) }
    var art by remember { mutableStateOf(SuchArt.Genau) }
    var felder by remember { mutableStateOf(SuchFeld.entries.toSet()) }
    var ganzesFeld by remember { mutableStateOf(false) }
    var grossKlein by remember { mutableStateOf(false) }
    var phonetisch by remember { mutableStateOf(false) }
    // Kennzeichen
    var kennText by remember { mutableStateOf("") }
    var kennArt by remember { mutableStateOf(KennArt.Kennung) }
    var kennLage by remember { mutableStateOf(KennLage.Beliebig) }
    // Ersetzen
    var ersetzSuchen by remember { mutableStateOf("") }
    var ersetzDurch by remember { mutableStateOf("") }
    var ersetzFelder by remember { mutableStateOf(setOf(ErsetzFeld.Orte, ErsetzFeld.Beruf)) }
    var vorschau by remember { mutableStateOf<List<Ersetzung>?>(null) }
    var gewaehlt by remember { mutableStateOf(emptySet<Int>()) }
    var schreibt by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Gemeinsam
    var treffer by remember { mutableStateOf<List<SuchTreffer>?>(null) }
    var nurTreffer by remember { mutableStateOf(false) }
    var meldung by remember { mutableStateOf<String?>(null) }
    var aktiv by remember { mutableStateOf<String?>(state.root) }
    val canEdit = state.tree?.canEdit == true
    val fokus = remember { FocusRequester() }
    val suchenHalter = remember { mutableStateOf({}) }
    fun suchenJetzt() = suchenHalter.value()
    LaunchedEffect(reiter) { runCatching { fokus.requestFocus() } }
    // Bildwerkzeug: mit Vorgabetext gleich suchen, sobald der Baum da ist
    LaunchedEffect(baum) { if (vorgabeText.isNotBlank() && baum?.isSuccess == true && treffer == null) suchenJetzt() }

    fun gruppe(): Set<String>? = if (nurTreffer) treffer?.map { it.person.xref }?.toSet() else null
    fun suchen() {
        val b = baum?.getOrNull() ?: return
        meldung = null; vorschau = null
        treffer = Suche.suchen(b, SuchAnfrage(text, art, felder, ganzesFeld, grossKlein, phonetisch), gruppe())
    }
    suchenHalter.value = ::suchen
    fun kennzeichen() {
        val b = baum?.getOrNull() ?: return
        meldung = null; vorschau = null
        treffer = Suche.kennzeichen(b, kennText, kennArt, kennLage, gruppe())
    }
    fun vorschauErzeugen() {
        val b = baum?.getOrNull() ?: return
        meldung = null
        val v = Suche.ersetzenVorschau(b, ersetzSuchen, ersetzDurch, ersetzFelder, ganzesFeld, grossKlein, gruppe())
        vorschau = v; gewaehlt = v.indices.toSet()
        if (v.isEmpty()) meldung = Texte.t(Res.string.desk_search_replace_none)
    }
    fun ersetzen() {
        val v = vorschau ?: return
        val tree = state.tree?.name ?: return
        val liste = v.filterIndexed { i, _ -> i in gewaehlt }
        if (liste.isEmpty()) return
        scope.launch {
            var ok = 0; var ausstehend = false; var fehler: String? = null
            liste.forEachIndexed { i, e ->
                schreibt = i + 1 to liste.size
                runCatching { withContext(Dispatchers.IO) { viewModel.client.saveFact(tree, e.record, e.anfrage()) } }
                    .onSuccess { ok++; ausstehend = ausstehend || it.pending }.onFailure { fehler = it.message ?: "?" }
            }
            schreibt = null; vorschau = null; gewaehlt = emptySet()
            meldung = listOfNotNull(Texte.t(Res.string.desk_search_replace_done, ok), if (ausstehend) Texte.t(Res.string.desk_search_replace_pending) else null, fehler).joinToString("  ")
            viewModel.refresh()
            neu++
        }
    }

    Surface(Modifier.fillMaxSize(), color = farben.surface) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabRow(selectedTabIndex = reiter.ordinal, containerColor = farben.surface) {
                Reiter.entries.forEach { r -> Tab(selected = reiter == r, onClick = { reiter = r }, text = { Text(stringResource(r.titel)) }) }
            }
            // ── Reiter ──
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (reiter) {
                    Reiter.Suchen -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.desk_search_for), Modifier.width(120.dp))
                            OutlinedTextField(text, { text = it }, Modifier.weight(1f).focusRequester(fokus), singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { suchen() }))
                            Button(onClick = ::suchen, enabled = baum?.isSuccess == true && text.isNotBlank()) { Text(stringResource(Res.string.desk_search_run)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SuchWahl(stringResource(Res.string.desk_search_exact), art == SuchArt.Genau) { art = SuchArt.Genau }
                            SuchWahl(stringResource(Res.string.desk_search_all_words), art == SuchArt.AlleWoerter) { art = SuchArt.AlleWoerter }
                            SuchWahl(stringResource(Res.string.desk_search_any_word), art == SuchArt.EinWort) { art = SuchArt.EinWort }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SuchHaken(stringResource(Res.string.desk_search_whole_field), ganzesFeld) { ganzesFeld = it }
                            SuchHaken(stringResource(Res.string.desk_search_case), grossKlein) { grossKlein = it }
                            SuchHaken(stringResource(Res.string.desk_search_phonetic), phonetisch) { phonetisch = it }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.desk_search_fields), style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { felder = SuchFeld.entries.toSet() }) { Text(stringResource(Res.string.desk_search_all)) }
                            TextButton(onClick = { felder = emptySet() }) { Text(stringResource(Res.string.desk_search_none)) }
                        }
                        FlowRow(Modifier.fillMaxWidth()) {
                            SuchFeld.entries.forEach { f -> SuchHaken(stringResource(f.titel), f in felder) { felder = if (it) felder + f else felder - f } }
                        }
                    }
                    Reiter.Liste -> {
                        Text(stringResource(Res.string.desk_search_list_hint), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !treffer.isNullOrEmpty(), onClick = {
                                val d = java.awt.FileDialog(null as java.awt.Frame?, Texte.t(Res.string.desk_search_save), java.awt.FileDialog.SAVE).apply { file = "*.wtliste"; isVisible = true }
                                d.file?.let { f -> runCatching { java.io.File(d.directory, if (f.endsWith(".wtliste", true)) f else "$f.wtliste").writeText(Suche.listeText(treffer.orEmpty())) }.onFailure { meldung = it.message } }
                            }) { Text(stringResource(Res.string.desk_search_save)) }
                            OutlinedButton(enabled = baum?.isSuccess == true, onClick = {
                                val b = baum?.getOrNull() ?: return@OutlinedButton
                                val d = java.awt.FileDialog(null as java.awt.Frame?, Texte.t(Res.string.desk_search_load), java.awt.FileDialog.LOAD).apply { file = "*.wtliste"; isVisible = true }
                                d.file?.let { f -> runCatching { java.io.File(d.directory, f).readText() }.onSuccess { treffer = Suche.listeLesen(it, b); vorschau = null; meldung = null }.onFailure { meldung = it.message } }
                            }) { Text(stringResource(Res.string.desk_search_load)) }
                        }
                    }
                    Reiter.Ersetzen -> {
                        if (!canEdit) Text(stringResource(Res.string.desk_search_replace_noedit), color = farben.error)
                        Text(stringResource(Res.string.desk_search_replace_hint), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.desk_search_for), Modifier.width(120.dp))
                            OutlinedTextField(ersetzSuchen, { ersetzSuchen = it; vorschau = null }, Modifier.weight(1f).focusRequester(fokus), singleLine = true, enabled = canEdit)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.desk_search_replace_with), Modifier.width(120.dp))
                            OutlinedTextField(ersetzDurch, { ersetzDurch = it; vorschau = null }, Modifier.weight(1f), singleLine = true, enabled = canEdit)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ErsetzFeld.entries.forEach { f -> SuchHaken(stringResource(f.titel), f in ersetzFelder, canEdit) { ersetzFelder = if (it) ersetzFelder + f else ersetzFelder - f; vorschau = null } }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuchHaken(stringResource(Res.string.desk_search_whole_field), ganzesFeld, canEdit) { ganzesFeld = it; vorschau = null }
                            SuchHaken(stringResource(Res.string.desk_search_case), grossKlein, canEdit) { grossKlein = it; vorschau = null }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = ::vorschauErzeugen, enabled = canEdit && baum?.isSuccess == true && ersetzSuchen.isNotBlank() && ersetzFelder.isNotEmpty()) { Text(stringResource(Res.string.desk_search_preview)) }
                        }
                    }
                    Reiter.Kennzeichen -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.desk_search_for), Modifier.width(120.dp))
                            OutlinedTextField(kennText, { kennText = it }, Modifier.weight(1f).focusRequester(fokus), singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { kennzeichen() }))
                            Button(onClick = ::kennzeichen, enabled = baum?.isSuccess == true && kennText.isNotBlank()) { Text(stringResource(Res.string.desk_search_run)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SuchWahl(stringResource(Res.string.desk_search_pos_any), kennLage == KennLage.Beliebig) { kennLage = KennLage.Beliebig }
                            SuchWahl(stringResource(Res.string.desk_search_pos_start), kennLage == KennLage.Anfang) { kennLage = KennLage.Anfang }
                            SuchWahl(stringResource(Res.string.desk_search_pos_end), kennLage == KennLage.Ende) { kennLage = KennLage.Ende }
                            SuchWahl(stringResource(Res.string.desk_search_pos_whole), kennLage == KennLage.Komplett) { kennLage = KennLage.Komplett }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SuchWahl(stringResource(Res.string.desk_search_mark_id), kennArt == KennArt.Kennung) { kennArt = KennArt.Kennung }
                            SuchWahl(stringResource(Res.string.desk_search_mark_refn), kennArt == KennArt.Refn) { kennArt = KennArt.Refn }
                            SuchWahl(stringResource(Res.string.desk_search_mark_uid), kennArt == KennArt.Uid) { kennArt = KennArt.Uid }
                        }
                    }
                }
            }
            HorizontalDivider(color = farben.outlineVariant)
            // ── Treffer ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                SuchHaken(stringResource(Res.string.desk_search_within), nurTreffer, !treffer.isNullOrEmpty()) { nurTreffer = it }
                Spacer(Modifier.weight(1f))
                val v = vorschau; val t = treffer
                val zahl = when { v != null -> Texte.t(Res.string.desk_search_count, v.size); t != null -> Texte.t(Res.string.desk_search_count, t.size); else -> "" }
                Text(zahl, style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
            }
            meldung?.let { Text(it, color = farben.error, style = MaterialTheme.typography.bodySmall) }
            Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, farben.outlineVariant)) {
                val b = baum; val v = vorschau; val t = treffer; val s = schreibt
                when {
                    b == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        if (fortschritt.second > 0) Text("${fortschritt.first} / ${fortschritt.second}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    b.isFailure -> Text(b.exceptionOrNull()?.message ?: "?", Modifier.padding(12.dp), color = farben.error)
                    s != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.desk_search_replace_progress, s.first, s.second)) }
                    v != null -> VorschauListe(v, gewaehlt, onWahl = { i, an -> gewaehlt = if (an) gewaehlt + i else gewaehlt - i })
                    t == null -> Text(stringResource(Res.string.desk_search_hint), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                    t.isEmpty() -> Text(stringResource(Res.string.desk_search_none_found), Modifier.padding(12.dp), color = farben.onSurfaceVariant)
                    else -> TrefferListe(t, aktiv, onKlick = { aktiv = it; viewModel.setRoot(it) }, onDoppel = { aktiv = it; openSheet(it) })
                }
            }
            // ── Knoepfe ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val t = treffer.orEmpty()
                if (viewModel.bookmarksSupported) OutlinedButton(enabled = t.isNotEmpty() && vorschau == null, onClick = {
                    t.map { it.person.xref }.filter { !viewModel.isBookmarked(it) }.forEach(viewModel::toggleBookmark)
                }) { Text(stringResource(Res.string.desk_search_to_bookmarks)) }
                OutlinedButton(enabled = t.isNotEmpty() && vorschau == null, onClick = { onTabelle(t.map { it.person.xref }.toSet()) }) { Text(stringResource(Res.string.desk_search_to_table)) }
                Spacer(Modifier.weight(1f))
                if (vorschau != null) {
                    TextButton(onClick = { vorschau = null; gewaehlt = emptySet() }) { Text(stringResource(Res.string.action_cancel)) }
                    Button(onClick = ::ersetzen, enabled = canEdit && gewaehlt.isNotEmpty() && schreibt == null) { Text(stringResource(Res.string.desk_search_replace_run, gewaehlt.size)) }
                }
            }
        }
    }
}

@Composable
private fun SuchWahl(text: String, an: Boolean, onClick: () -> Unit) {
    Row(Modifier.combinedClickable(onClick = onClick).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = an, onClick = onClick)
        Text(text, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SuchHaken(text: String, an: Boolean, enabled: Boolean = true, onWechsel: (Boolean) -> Unit) {
    Row(Modifier.combinedClickable(enabled = enabled, onClick = { onWechsel(!an) }).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = an, onCheckedChange = onWechsel, enabled = enabled)
        Text(text, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrefferListe(treffer: List<SuchTreffer>, aktiv: String?, onKlick: (String) -> Unit, onDoppel: (String) -> Unit) {
    val farben = MaterialTheme.colorScheme
    val liste = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        items(treffer, key = { it.person.xref }) { t ->
            val p = t.person
            Row(
                Modifier.fillMaxWidth().background(if (p.xref == aktiv) farben.primary.copy(alpha = 0.12f) else farben.surface)
                    .combinedClickable(onClick = { onKlick(p.xref) }, onDoubleClick = { onDoppel(p.xref) }).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.width(300.dp)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(p.xref, p.lifespan).filter(String::isNotBlank).joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant, maxLines = 1)
                }
                Column(Modifier.weight(1f)) {
                    t.fundstellen.take(2).forEach { f ->
                        Text(stringResource(f.feld.titel) + ": " + f.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (t.fundstellen.size > 2) Text("+ ${t.fundstellen.size - 2}", style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = farben.outlineVariant.copy(alpha = 0.5f))
        }
    }
    Box(Modifier.fillMaxSize()) { ListenLeiste(liste) }
}

@Composable
private fun VorschauListe(v: List<Ersetzung>, gewaehlt: Set<Int>, onWahl: (Int, Boolean) -> Unit) {
    val farben = MaterialTheme.colorScheme
    val liste = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        itemsIndexed(v) { i, e ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = i in gewaehlt, onCheckedChange = { onWahl(i, it) })
                Column(Modifier.width(260.dp)) {
                    Text(e.person.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(e.fact.label.ifBlank { e.fact.tag }, stringResource(e.feld.titel)).joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant, maxLines = 1)
                }
                Column(Modifier.weight(1f)) {
                    Text(e.alt, style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(e.neu, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = farben.outlineVariant.copy(alpha = 0.5f))
        }
    }
    Box(Modifier.fillMaxSize()) { ListenLeiste(liste) }
}
