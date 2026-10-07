package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.compose.resources.stringResource

/*
 * Fenster "Buch erstellen" (26.09.2026): links die Buecher (Vorfahrenbuch; Nachfahren- und Familienbuch folgen),
 * in der Mitte die Einstellungen in Gruppen (Optionen, Daten, Darstellung, Verzeichnisse), rechts die ersten
 * Seiten des fertigen PDFs. Ausgabe als PDF, DOCX, HTML, TeX oder Text.
 */

enum class BuchArt { Vorfahren, Nachfahren, Familien }

private object BuchWahl {
    private val prefs get() = DeskLayout.prefs
    fun laden() = BuchOptionen(
        generationen = (prefs.getString("buch_gen", null)?.toIntOrNull() ?: 7).coerceIn(2, 12),
        bilder = prefs.getBoolean("buch_bilder", true), farbkodierung = prefs.getBoolean("buch_farbe", true),
        notizen = prefs.getBoolean("buch_notizen", true), quellen = prefs.getBoolean("buch_quellen", true),
        orteKuerzen = prefs.getBoolean("buch_orte_kurz", true), doppelteZeigen = prefs.getBoolean("buch_doppelt", false),
        tafel = prefs.getBoolean("buch_tafel", false),
        namen = prefs.getBoolean("buch_reg_namen", true), orte = prefs.getBoolean("buch_reg_orte", true),
        berufe = prefs.getBoolean("buch_reg_berufe", true), quellenVerzeichnis = prefs.getBoolean("buch_reg_quellen", true),
        vorwort = prefs.getString("buch_vorwort", null).orEmpty(),
        nummerierung = Nummerierung.entries.firstOrNull { it.name == prefs.getString("buch_nr", null) } ?: Nummerierung.Saragossa,
        partner = prefs.getBoolean("buch_partner", true), namenstraeger = prefs.getBoolean("buch_namen", false),
        familienChronologisch = prefs.getBoolean("buch_chrono", false), ortFilter = prefs.getString("buch_ort", null).orEmpty(),
        haeuser = prefs.getBoolean("buch_haeuser", true),
        haeuserNurTyp = prefs.getBoolean("buch_haeuser_typ", true), haeuserOhneTyp = prefs.getBoolean("buch_haeuser_ohne", true),
    )
    fun art(): BuchArt = BuchArt.entries.firstOrNull { it.name == prefs.getString("buch_art", null) } ?: BuchArt.Vorfahren
    fun sichern(o: BuchOptionen) {
        prefs.putString("buch_gen", o.generationen.toString()); prefs.putBoolean("buch_bilder", o.bilder); prefs.putBoolean("buch_farbe", o.farbkodierung)
        prefs.putBoolean("buch_notizen", o.notizen); prefs.putBoolean("buch_quellen", o.quellen); prefs.putBoolean("buch_orte_kurz", o.orteKuerzen)
        prefs.putBoolean("buch_doppelt", o.doppelteZeigen); prefs.putBoolean("buch_tafel", o.tafel); prefs.putBoolean("buch_reg_namen", o.namen); prefs.putBoolean("buch_reg_orte", o.orte)
        prefs.putBoolean("buch_reg_berufe", o.berufe); prefs.putBoolean("buch_reg_quellen", o.quellenVerzeichnis); prefs.putString("buch_vorwort", o.vorwort)
        prefs.putString("buch_nr", o.nummerierung.name); prefs.putBoolean("buch_partner", o.partner); prefs.putBoolean("buch_namen", o.namenstraeger)
        prefs.putBoolean("buch_chrono", o.familienChronologisch); prefs.putString("buch_ort", o.ortFilter); prefs.putBoolean("buch_haeuser", o.haeuser)
        prefs.putBoolean("buch_haeuser_typ", o.haeuserNurTyp); prefs.putBoolean("buch_haeuser_ohne", o.haeuserOhneTyp)
    }
}

@Composable
fun BuchFenster(state: UiState, viewModel: AppViewModel, onClose: () -> Unit) {
    val appName = LocalAppName.current
    val baum = state.tree?.title.orEmpty()
    var o by remember { mutableStateOf(BuchWahl.laden()) }
    var art by remember { mutableStateOf(BuchWahl.art()) }
    val nachfahren = art == BuchArt.Nachfahren
    val familien = art == BuchArt.Familien
    LaunchedEffect(o, art) { BuchWahl.sichern(o); DeskLayout.prefs.putString("buch_art", art.name) }
    var fortschritt by remember { mutableStateOf("") }
    // Bilder abbrechen: der Lauf endet, das Buch kommt ohne die restlichen Bilder
    var bilderAbbruch by remember { mutableStateOf(false) }
    // Daten: alle Angaben und Bilder - neu nur, wenn sich Buchart, Tiefe oder Bilder aendern
    val daten by produceState<Result<Any>?>(null, state.tree?.name, state.root, if (familien) 0 else o.generationen, o.bilder, art) {
        value = null
        fortschritt = ""; bilderAbbruch = false
        val tree = state.tree; val root = state.root
        value = if (tree == null || root == null) null else withContext(Dispatchers.IO) {
            runCatching {
                // Kurznamen der Orte fuer "Orte kuerzen" (Server ab API-Stufe 21; sonst bleibt es beim ersten Namensteil)
                if ((state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_PLACE_LIST) OrtsKurznamen.laden(viewModel.client, tree.name)
                if (familien) familienbuchLaden(viewModel.client, tree.name, o.bilder, haeuser = (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_LOC_HIERARCHY, fortschritt = { fortschritt = it }, abbruch = { bilderAbbruch })
                else if (nachfahren) nachfahrenbuchLaden(viewModel.client, tree.name, root, o.generationen, o.bilder, fortschritt = { fortschritt = it }, abbruch = { bilderAbbruch })
                else vorfahrenbuchLaden(viewModel.client, tree.name, root, o.generationen, o.bilder, fortschritt = { fortschritt = it }, abbruch = { bilderAbbruch })
            }
        }
    }
    val buch = remember(daten, o) {
        when (val d = daten?.getOrNull()) {
            is NachfahrenDaten -> nachfahrenbuch(d, o, baum, appName)
            is FamilienDaten -> familienbuch(d, o, baum, appName)
            is BuchDaten -> vorfahrenbuch(d, o, baum, appName)
            else -> null
        }
    }
    var breitePx by remember { mutableStateOf(800) }
    val seiten by produceState<Pair<List<ImageBitmap>, Int>?>(null, buch, breitePx) {
        val b = buch ?: run { value = null; return@produceState }
        delay(250)
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = java.io.ByteArrayOutputStream().also { out -> buchPdf(b).use { it.save(out) } }.toByteArray()
                org.apache.pdfbox.Loader.loadPDF(bytes).use { d ->
                    val r = PDFRenderer(d)
                    val skala = breitePx / d.getPage(0).mediaBox.width
                    (0 until minOf(d.numberOfPages, 12)).map { r.renderImage(it, skala).toComposeImageBitmap() } to d.numberOfPages
                }
            }.getOrNull()
        }
    }

    DialogWindow(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_book_window),
        state = rememberDialogState(width = 1220.dp, height = 880.dp),
        onPreviewKeyEvent = { e -> when { e.key == Key.Escape -> { onClose(); true }; e.key == Key.F1 && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> { Hilfe.oeffnen("buecher"); true }; else -> false } },
    ) {
        DeskTheme {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val artBreite = rememberBreite("buch_arten", 200f)
                Column(Modifier.width(artBreite.value.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    ArtGruppe(stringResource(Res.string.desk_book_group_standard))
                    ArtEintrag(stringResource(Res.string.desk_book_ancestor_book), art == BuchArt.Vorfahren) { art = BuchArt.Vorfahren; o = o.copy(generationen = o.generationen.coerceAtMost(12)) }
                    ArtEintrag(stringResource(Res.string.desk_book_descendant_book), nachfahren) { art = BuchArt.Nachfahren; o = o.copy(generationen = o.generationen.coerceAtMost(10)) }
                    Spacer(Modifier.height(8.dp))
                    ArtGruppe(stringResource(Res.string.desk_book_group_complete))
                    ArtEintrag(stringResource(Res.string.desk_book_family_book), familien) { art = BuchArt.Familien }
                }
                Trenner(artBreite, "buch_arten", 160f, 400f)
                val einstBreite = rememberBreite("buch_einstellungen", 340f)
                Column(Modifier.width(einstBreite.value.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(when (art) { BuchArt.Nachfahren -> Res.string.desk_book_descendant_book; BuchArt.Familien -> Res.string.desk_book_family_book; else -> Res.string.desk_book_ancestor_book }),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(when (art) { BuchArt.Nachfahren -> Res.string.desk_book_descendant_hint; BuchArt.Familien -> Res.string.desk_book_family_hint; else -> Res.string.desk_book_ancestor_hint }),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val wer = state.detail?.takeIf { it.person.xref == state.root }?.person?.name ?: state.people.firstOrNull { it.xref == state.root }?.name.orEmpty()
                    if (familien) {
                        Einstellung(stringResource(Res.string.desk_book_sort)) {
                            val werte = listOf(stringResource(Res.string.desk_book_sort_alpha), stringResource(Res.string.desk_book_sort_chrono))
                            Auswahl(werte[if (o.familienChronologisch) 1 else 0], werte) { w -> o = o.copy(familienChronologisch = werte.indexOf(w) == 1) }
                        }
                        OutlinedTextField(o.ortFilter, { o = o.copy(ortFilter = it) }, label = { Text(stringResource(Res.string.desk_book_place_filter)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if ((state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_LOC_HIERARCHY)
                            Haken(stringResource(Res.string.desk_book_houses_option), o.haeuser, stringResource(Res.string.tipp_book_houses)) { o = o.copy(haeuser = it) }
                        if ((state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_LOC_HIERARCHY && o.haeuser) {
                            Haken(stringResource(Res.string.desk_book_houses_only_type), o.haeuserNurTyp, stringResource(Res.string.tipp_book_houses_only_type)) { o = o.copy(haeuserNurTyp = it) }
                            if (o.haeuserNurTyp) Haken(stringResource(Res.string.desk_book_houses_untyped), o.haeuserOhneTyp, stringResource(Res.string.tipp_book_houses_untyped)) { o = o.copy(haeuserOhneTyp = it) }
                        }
                    }
                    if (!familien) Einstellung(stringResource(Res.string.desk_chart_person)) { Text(wer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    if (!familien) Einstellung(stringResource(Res.string.desk_chart_generations)) {
                        Auswahl(o.generationen.toString(), (2..(if (nachfahren) 10 else 12)).map { it.toString() }) { o = o.copy(generationen = it.toInt()) }
                    }
                    if (nachfahren) {
                        Einstellung(stringResource(Res.string.desk_list_numbering), stringResource(Res.string.tipp_list_numbering)) {
                            val namen = mapOf(Nummerierung.Saragossa to "1.2.3", Nummerierung.Aboville to "d'Aboville (C1.2.3)", Nummerierung.Henry to "Henry (123)",
                                Nummerierung.Fortlaufend to stringResource(Res.string.desk_list_numbering_serial))
                            Auswahl(namen.getValue(o.nummerierung), namen.values.toList()) { w -> o = o.copy(nummerierung = namen.entries.first { it.value == w }.key) }
                        }
                        Haken(stringResource(Res.string.desk_chart_spouses), o.partner) { o = o.copy(partner = it) }
                        Haken(stringResource(Res.string.desk_chart_name_bearers), o.namenstraeger, stringResource(Res.string.tipp_name_bearers)) { o = o.copy(namenstraeger = it) }
                    }
                    OutlinedTextField(o.titel, { o = o.copy(titel = it) }, label = { Text(stringResource(Res.string.desk_chart_heading)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(Res.string.desk_book_section_data), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Haken(stringResource(Res.string.desk_book_notes), o.notizen) { o = o.copy(notizen = it) }
                    Haken(stringResource(Res.string.desk_book_sources), o.quellen) { o = o.copy(quellen = it) }
                    Haken(stringResource(Res.string.desk_book_short_places), o.orteKuerzen, stringResource(Res.string.tipp_short_places)) { o = o.copy(orteKuerzen = it) }
                    if (art == BuchArt.Vorfahren) Haken(stringResource(Res.string.desk_book_duplicates), o.doppelteZeigen, stringResource(Res.string.tipp_duplicates)) { o = o.copy(doppelteZeigen = it) }
                    Text(stringResource(Res.string.desk_book_section_look), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Haken(stringResource(Res.string.desk_chart_photos), o.bilder) { o = o.copy(bilder = it) }
                    // Beim Stammbaum auf diesem PC liegt der Medienordner bei uns - kein Feld noetig
                    if (o.bilder && state.tree != null && !de.bgghome.webtrees.nativ.lokal.LokalBetrieb.istLokal(viewModel.client.baseUrl)) MedienOrdnerZeile(viewModel.client.baseUrl, state.tree.name)
                    if (!familien) Haken(stringResource(Res.string.desk_book_foldout), o.tafel, stringResource(Res.string.tipp_foldout)) { o = o.copy(tafel = it) }
                    if (!familien) Haken(stringResource(if (nachfahren) Res.string.desk_book_branch_colors else Res.string.desk_book_color), o.farbkodierung, stringResource(Res.string.tipp_book_color)) { o = o.copy(farbkodierung = it) }
                    Text(stringResource(Res.string.desk_book_section_indexes), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Haken(stringResource(Res.string.desk_book_index_names), o.namen) { o = o.copy(namen = it) }
                    Haken(stringResource(Res.string.desk_book_index_places), o.orte) { o = o.copy(orte = it) }
                    Haken(stringResource(Res.string.desk_book_index_occupations), o.berufe) { o = o.copy(berufe = it) }
                    Haken(stringResource(Res.string.desk_book_index_sources), o.quellenVerzeichnis) { o = o.copy(quellenVerzeichnis = it) }
                    OutlinedTextField(o.vorwort, { o = o.copy(vorwort = it) }, label = { Text(stringResource(Res.string.desk_book_preface_field)) }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                    seiten?.second?.let { Text(stringResource(Res.string.desk_list_pages, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val b = buch
                    Knopf(stringResource(Res.string.desk_print), b != null) { b?.let { drucken(buchPdf(it), it.titel) } }
                    Text(stringResource(Res.string.desk_book_save_as), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BuchFormat.entries.forEach { f ->
                            // Fuenf Knoepfe nebeneinander: ohne den ueblichen Innenabstand bricht "DOCX" sonst je Buchstabe um
                            OutlinedButton(onClick = { b?.let { buchSpeichern(it, f, it.titel) } }, enabled = b != null, shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp), modifier = Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
                Trenner(einstBreite, "buch_einstellungen", 240f, 600f)
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF8C8F8E)), contentAlignment = Alignment.TopCenter) {
                    val px = with(LocalDensity.current) { (maxWidth - 48.dp).roundToPx() }
                    LaunchedEffect(px) { breitePx = px.coerceAtLeast(300) }
                    val fehler = daten?.exceptionOrNull()
                    val s = seiten
                    when {
                        fehler != null -> Text(fehler.message ?: "?", Modifier.padding(24.dp), color = androidx.compose.ui.graphics.Color.White)
                        s == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                            Text(fortschritt.ifBlank { stringResource(Res.string.desk_book_loading) }, Modifier.padding(top = 8.dp), color = androidx.compose.ui.graphics.Color.White)
                            // Waehrend die Bilder laufen: ohne den Rest weitermachen (die Personen sind dann schon da)
                            if (fortschritt.startsWith(stringResource(Res.string.desk_book_progress_images_prefix)) && !bilderAbbruch)
                                OutlinedButton(onClick = { bilderAbbruch = true }, Modifier.padding(top = 12.dp), shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) { Text(stringResource(Res.string.desk_book_cancel_images)) }
                        }
                        else -> Box(Modifier.fillMaxSize()) {
                            val scroll = rememberScrollState()
                            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                s.first.forEach { Image(it, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                                if (s.second > s.first.size) Text(stringResource(Res.string.desk_list_more_pages, s.second - s.first.size), color = androidx.compose.ui.graphics.Color.White)
                            }
                            SenkrechteLeiste(scroll)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Medienordner auf diesem PC (optional) und der Zwischenspeicher fuer Bilder - je Server und Baum. Wer den webtrees-
 * Medienordner ohnehin auf dem PC hat, spart sich den Server; alle anderen lassen das Feld leer.
 */
@Composable
private fun MedienOrdnerZeile(server: String, tree: String) {
    var pfad by remember(server, tree) { mutableStateOf(Bilder.medienOrdnerPfad(server, tree)) }
    var geleert by remember { mutableStateOf<Int?>(null) }
    val cacheMb = remember(server, tree, geleert) { Bilder.cacheGroesse(server, tree) / 1_048_576 }
    Column(Modifier.padding(start = 4.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Tipp(stringResource(Res.string.tipp_book_media_folder)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(pfad, { pfad = it; Bilder.medienOrdnerSetzen(server, tree, it) }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(Res.string.desk_book_media_folder)) }, textStyle = MaterialTheme.typography.bodySmall,
                    isError = pfad.isNotBlank() && !java.io.File(pfad).isDirectory)
                TextButton(onClick = {
                    val chooser = javax.swing.JFileChooser(pfad.takeIf { it.isNotBlank() }).apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
                    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) { pfad = chooser.selectedFile.absolutePath; Bilder.medienOrdnerSetzen(server, tree, pfad) }
                }) { Text(stringResource(Res.string.desk_book_media_folder_choose)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.desk_book_cache_size, cacheMb), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.desk_book_cache_clear), Modifier.clickable { geleert = Bilder.cacheLeeren(server, tree) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            geleert?.let { Text(stringResource(Res.string.desk_book_cache_cleared, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
