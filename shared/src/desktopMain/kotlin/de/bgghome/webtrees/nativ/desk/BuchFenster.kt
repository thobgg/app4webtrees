package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
 * in der Mitte die Einstellungen wie beim Vorbild (Optionen, Daten, Darstellung, Verzeichnisse), rechts die ersten
 * Seiten des fertigen PDFs. Ausgabe als PDF, DOCX, HTML, TeX oder Text.
 */

private object BuchWahl {
    private val prefs get() = DeskLayout.prefs
    fun laden() = BuchOptionen(
        generationen = (prefs.getString("buch_gen", null)?.toIntOrNull() ?: 7).coerceIn(2, 12),
        bilder = prefs.getBoolean("buch_bilder", true), farbkodierung = prefs.getBoolean("buch_farbe", true),
        notizen = prefs.getBoolean("buch_notizen", true), quellen = prefs.getBoolean("buch_quellen", true),
        orteKuerzen = prefs.getBoolean("buch_orte_kurz", true), doppelteZeigen = prefs.getBoolean("buch_doppelt", false),
        namen = prefs.getBoolean("buch_reg_namen", true), orte = prefs.getBoolean("buch_reg_orte", true),
        berufe = prefs.getBoolean("buch_reg_berufe", true), quellenVerzeichnis = prefs.getBoolean("buch_reg_quellen", true),
        vorwort = prefs.getString("buch_vorwort", null).orEmpty(),
    )
    fun sichern(o: BuchOptionen) {
        prefs.putString("buch_gen", o.generationen.toString()); prefs.putBoolean("buch_bilder", o.bilder); prefs.putBoolean("buch_farbe", o.farbkodierung)
        prefs.putBoolean("buch_notizen", o.notizen); prefs.putBoolean("buch_quellen", o.quellen); prefs.putBoolean("buch_orte_kurz", o.orteKuerzen)
        prefs.putBoolean("buch_doppelt", o.doppelteZeigen); prefs.putBoolean("buch_reg_namen", o.namen); prefs.putBoolean("buch_reg_orte", o.orte)
        prefs.putBoolean("buch_reg_berufe", o.berufe); prefs.putBoolean("buch_reg_quellen", o.quellenVerzeichnis); prefs.putString("buch_vorwort", o.vorwort)
    }
}

@Composable
fun BuchFenster(state: UiState, viewModel: AppViewModel, onClose: () -> Unit) {
    val appName = LocalAppName.current
    val baum = state.tree?.title.orEmpty()
    var o by remember { mutableStateOf(BuchWahl.laden()) }
    LaunchedEffect(o) { BuchWahl.sichern(o) }
    // Daten: Vorfahren mit allen Angaben und Bildern - neu nur, wenn sich Tiefe oder Bilder aendern
    val daten by produceState<Result<BuchDaten>?>(null, state.tree?.name, state.root, o.generationen, o.bilder) {
        value = null
        val tree = state.tree; val root = state.root
        value = if (tree == null || root == null) null else withContext(Dispatchers.IO) {
            runCatching { vorfahrenbuchLaden(viewModel.client, tree.name, root, o.generationen, o.bilder) }
        }
    }
    val buch = remember(daten, o) { daten?.getOrNull()?.let { vorfahrenbuch(it, o, baum, appName) } }
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
        onPreviewKeyEvent = { e -> if (e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(Modifier.width(200.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    ArtGruppe(stringResource(Res.string.desk_book_group_standard))
                    ArtEintrag(stringResource(Res.string.desk_book_ancestor_book), true) {}
                    ArtEintrag(stringResource(Res.string.desk_book_descendant_book) + " …", false) {}
                    Spacer(Modifier.height(8.dp))
                    ArtGruppe(stringResource(Res.string.desk_book_group_complete))
                    ArtEintrag(stringResource(Res.string.desk_book_family_book) + " …", false) {}
                    Text(stringResource(Res.string.desk_book_coming), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.desk_book_ancestor_book), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.desk_book_ancestor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val wer = state.detail?.takeIf { it.person.xref == state.root }?.person?.name ?: state.people.firstOrNull { it.xref == state.root }?.name.orEmpty()
                    Einstellung(stringResource(Res.string.desk_chart_person)) { Text(wer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    Einstellung(stringResource(Res.string.desk_chart_generations)) {
                        Auswahl(o.generationen.toString(), (2..12).map { it.toString() }) { o = o.copy(generationen = it.toInt()) }
                    }
                    OutlinedTextField(o.titel, { o = o.copy(titel = it) }, label = { Text(stringResource(Res.string.desk_chart_heading)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(Res.string.desk_book_section_data), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Haken(stringResource(Res.string.desk_book_notes), o.notizen) { o = o.copy(notizen = it) }
                    Haken(stringResource(Res.string.desk_book_sources), o.quellen) { o = o.copy(quellen = it) }
                    Haken(stringResource(Res.string.desk_book_short_places), o.orteKuerzen) { o = o.copy(orteKuerzen = it) }
                    Haken(stringResource(Res.string.desk_book_duplicates), o.doppelteZeigen) { o = o.copy(doppelteZeigen = it) }
                    Text(stringResource(Res.string.desk_book_section_look), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Haken(stringResource(Res.string.desk_chart_photos), o.bilder) { o = o.copy(bilder = it) }
                    Haken(stringResource(Res.string.desk_book_color), o.farbkodierung) { o = o.copy(farbkodierung = it) }
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
                        BuchFormat.entries.forEach { f -> Box(Modifier.weight(1f)) { Knopf(f.name, b != null) { b?.let { buchSpeichern(it, f, it.titel) } } } }
                    }
                    Spacer(Modifier.height(4.dp))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF8C8F8E)), contentAlignment = Alignment.TopCenter) {
                    val px = with(LocalDensity.current) { (maxWidth - 48.dp).roundToPx() }
                    LaunchedEffect(px) { breitePx = px.coerceAtLeast(300) }
                    val fehler = daten?.exceptionOrNull()
                    val s = seiten
                    when {
                        fehler != null -> Text(fehler.message ?: "?", Modifier.padding(24.dp), color = androidx.compose.ui.graphics.Color.White)
                        s == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                            Text(stringResource(Res.string.desk_book_loading), Modifier.padding(top = 8.dp), color = androidx.compose.ui.graphics.Color.White)
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
