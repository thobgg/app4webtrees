package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import de.bgghome.webtrees.nativ.Desktop
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/*
 * Fenster "Tafel erstellen" (25.09.2026): links die Tafelarten, in der Mitte die Einstellungen, rechts die
 * Vorschau - sie ist das fertige Blatt, gerendert aus demselben PDF, das gedruckt oder gespeichert wird.
 * Tafelarten (26.09.2026): Ahnentafel, seitenweise Ahnentafel, Stammlinie, Mutterstamm, aeltester Vorfahr,
 * Stammtafel, Sanduhr - alle auf demselben Zeichenkern (Stammtafel.kt), Daten in TafelDaten.kt.
 */

/** Portraets fuer Tafeln, einmal geladen (ueber die Sitzung der App) und fuer die Laufzeit behalten. */
private object TafelBilder {
    private val cache = ConcurrentHashMap<String, BufferedImage>()
    private val fehlt = ConcurrentHashMap.newKeySet<String>()
    fun bekannt(url: String): BufferedImage? = cache[url]
    fun laden(url: String): BufferedImage? {
        cache[url]?.let { return it }
        if (url in fehlt) return null
        val bild = runCatching {
            Desktop.plattform.client.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.byteStream()?.use { ImageIO.read(it) }
            }
        }.getOrNull()
        // In RGB umkopieren: JPEG verlangt Bilder ohne Transparenz
        val rgb = bild?.let { b -> BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { color = java.awt.Color.WHITE; fillRect(0, 0, b.width, b.height); drawImage(b, 0, 0, null); dispose() } } }
        if (rgb != null) cache[url] = rgb else fehlt += url
        return rgb
    }
}

private fun nachfahrenPersonen(k: DescendantNode): List<Person> = listOf(k.person) + k.families.flatMap { it.children }.flatMap(::nachfahrenPersonen)

private val stilNamen: Map<TafelStil, StringResource> = mapOf(
    TafelStil.Pergament to Res.string.desk_style_parchment, TafelStil.Klassisch to Res.string.desk_style_classic,
    TafelStil.Farbig to Res.string.desk_style_colour, TafelStil.Schwarzweiss to Res.string.desk_style_bw,
)

/** Name und Erklaerung je Tafelart. */
private val artTexte: Map<TafelArt, Pair<StringResource, StringResource>> = mapOf(
    TafelArt.Ahnen to (Res.string.desk_chart_ancestors to Res.string.desk_chart_ancestors_hint),
    TafelArt.AhnenSeiten to (Res.string.desk_chart_ancestors_pages to Res.string.desk_chart_ancestors_pages_hint),
    TafelArt.Faecher to (Res.string.desk_chart_fan to Res.string.desk_chart_fan_hint),
    TafelArt.Kreis to (Res.string.desk_chart_circle to Res.string.desk_chart_circle_hint),
    TafelArt.Stammlinie to (Res.string.desk_chart_paternal to Res.string.desk_chart_paternal_hint),
    TafelArt.Mutterstamm to (Res.string.desk_chart_maternal to Res.string.desk_chart_maternal_hint),
    TafelArt.Aeltester to (Res.string.desk_chart_oldest to Res.string.desk_chart_oldest_hint),
    TafelArt.Stamm to (Res.string.desk_chart_descendants to Res.string.desk_chart_descendants_hint),
    TafelArt.Sanduhr to (Res.string.desk_chart_hourglass to Res.string.desk_chart_hourglass_hint),
)

private val linien = setOf(TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester)

/** Faechertafel und Ahnenkreis: ohne Orte und volle Daten (dafuer ist in den Ringen kein Platz). */
private val kreise = setOf(TafelArt.Faecher, TafelArt.Kreis)

/** Tafeln, die auch waagerecht gehen (Linien bleiben senkrecht). */
private val waagerechtMoeglich = setOf(TafelArt.Ahnen, TafelArt.AhnenSeiten, TafelArt.Stamm, TafelArt.Sanduhr)

/** Die Einstellungen bleiben je Tafelart zwischen den Aufrufen erhalten (Desktop-Einstellungen). */
private object TafelWahl {
    private val prefs get() = DeskLayout.prefs
    private fun k(art: TafelArt, name: String) = when (art) {
        TafelArt.Stamm -> "tafel_$name"
        TafelArt.Ahnen -> "tafel_ahnen_$name"
        else -> "tafel_${art.name.lowercase()}_$name"
    }
    private fun vorgabe(art: TafelArt) = when (art) {
        TafelArt.Stamm -> 6; TafelArt.Ahnen, TafelArt.Faecher -> 5; TafelArt.Kreis -> 6; TafelArt.Sanduhr -> 3; TafelArt.AhnenSeiten -> 7; else -> 13
    }
    fun letzte(): TafelArt = TafelArt.entries.firstOrNull { it.name == prefs.getString("tafel_art", null) } ?: TafelArt.Stamm
    fun laden(art: TafelArt) = TafelOptionen(
        generationen = (prefs.getString(k(art, "gen"), null)?.toIntOrNull() ?: vorgabe(art)).coerceIn(2, maxGen(art)),
        stil = TafelStil.entries.firstOrNull { it.name == prefs.getString(k(art, "stil"), null) } ?: TafelStil.Pergament,
        rahmenMm = prefs.getString(k(art, "rahmen"), null)?.toIntOrNull() ?: 30,
        bilder = prefs.getBoolean(k(art, "bilder"), true),
        nummern = prefs.getBoolean(k(art, "nummern"), true),
        ausgangOben = prefs.getBoolean(k(art, "oben"), false),
        nachfahren = (prefs.getString(k(art, "nach"), null)?.toIntOrNull() ?: 3).coerceIn(1, 9),
        namenstraeger = prefs.getBoolean(k(art, "namen"), false),
        partner = prefs.getBoolean(k(art, "partner"), art in linien),
        orte = prefs.getBoolean(k(art, "orte"), false),
        volleDaten = prefs.getBoolean(k(art, "voll"), false),
        waagerecht = art in waagerechtMoeglich && prefs.getBoolean(k(art, "waagerecht"), art == TafelArt.Sanduhr),
    )
    fun sichern(art: TafelArt, o: TafelOptionen) {
        prefs.putString("tafel_art", art.name)
        prefs.putString(k(art, "gen"), o.generationen.toString()); prefs.putString(k(art, "stil"), o.stil.name)
        prefs.putString(k(art, "rahmen"), o.rahmenMm.toString()); prefs.putBoolean(k(art, "bilder"), o.bilder)
        prefs.putBoolean(k(art, "nummern"), o.nummern); prefs.putBoolean(k(art, "oben"), o.ausgangOben)
        prefs.putString(k(art, "nach"), o.nachfahren.toString()); prefs.putBoolean(k(art, "namen"), o.namenstraeger)
        prefs.putBoolean(k(art, "partner"), o.partner); prefs.putBoolean(k(art, "orte"), o.orte); prefs.putBoolean(k(art, "voll"), o.volleDaten)
        prefs.putBoolean(k(art, "waagerecht"), o.waagerecht)
    }
}

@Composable
fun TafelFenster(state: UiState, viewModel: AppViewModel, start: TafelArt?, onClose: () -> Unit) {
    val appName = LocalAppName.current
    val tree = state.tree
    val root = state.root
    val wurzelName = state.detail?.takeIf { it.person.xref == root }?.person?.name ?: state.people.firstOrNull { it.xref == root }?.name.orEmpty()
    var art by remember { mutableStateOf(start ?: TafelWahl.letzte()) }
    val titelVorgabe = remember(art, wurzelName) { tafelTitel(art, wurzelName) }
    var o by remember(art) { mutableStateOf(TafelWahl.laden(art).copy(titel = titelVorgabe)) }
    LaunchedEffect(art, o) { TafelWahl.sichern(art, o) }
    val privat = stringResource(Res.string.person_private)
    val fuss = remember(tree) { fusszeile(appName, tree?.title.orEmpty()) }

    // Daten: Nachfahren einmal in voller Tiefe (Umstellen der Generationen kuerzt nur); Vorfahren so tief wie
    // eingestellt, weil jede Generation ueber sieben weitere Anfragen kostet.
    val ladeTiefe = if (art == TafelArt.Stamm) maxGen(art) else o.generationen
    val daten by produceState<Result<TafelDaten>?>(null, tree?.name, root, art, ladeTiefe) {
        value = null
        value = if (tree == null || root == null) null else withContext(Dispatchers.IO) {
            runCatching { tafelDatenLaden(viewModel.client, tree.name, root, art, ladeTiefe) }
        }
    }
    // Bilder im Hintergrund laden; jedes fertige Buendel zaehlt hoch und zeichnet die Vorschau neu.
    var bilderStand by remember { mutableStateOf(0) }
    LaunchedEffect(daten, o.bilder) {
        val d = daten?.getOrNull() ?: return@LaunchedEffect
        if (!o.bilder) return@LaunchedEffect
        val personen = d.ahnen.values.map { it.person } + (d.nachfahren?.let(::nachfahrenPersonen) ?: emptyList())
        val urls = personen.mapNotNull { it.thumb }.distinct().filter { TafelBilder.bekannt(it) == null }
        urls.chunked(8).forEach { gruppe ->
            withContext(Dispatchers.IO) { gruppe.forEach { TafelBilder.laden(it) } }
            bilderStand++
        }
    }
    fun erzeugen() = daten?.getOrNull()?.let { d -> tafelErzeugen(art, d, o, { p -> p.thumb?.let(TafelBilder::bekannt) }, privat, fuss) }

    // Vorschau: das Blatt als Bild, kurz verzoegert, damit schnelles Umstellen nicht jedes Mal rendert. Das PDF wird
    // dafuer einmal gespeichert und neu geladen - erst beim Speichern bettet PDFBox die Schriften ein, vorher zeichnet
    // der Renderer den Titel in einer Ersatzschrift. [gross]: Blatt in Lesegroesse, rollbar.
    var vorschauPx by remember { mutableStateOf(1000 to 800) }
    var gross by remember { mutableStateOf(false) }
    val vorschau by produceState<Result<Pair<ImageBitmap, TafelInfo>?>?>(null, daten, o, art, bilderStand, vorschauPx, gross) {
        delay(150)
        if (daten?.getOrNull() == null) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val (doc, info) = erzeugen() ?: return@runCatching null
                val bytes = java.io.ByteArrayOutputStream().also { out -> doc.use { it.save(out) } }.toByteArray()
                org.apache.pdfbox.Loader.loadPDF(bytes).use {
                    val box = it.getPage(0).mediaBox
                    val passend = minOf(vorschauPx.first / box.width, vorschauPx.second / box.height)
                    // Gross: Personenrahmen etwa 180 Pixel breit, das Bild aber hoechstens 9000 Pixel an der langen Seite
                    val skala = if (gross && info.seiten == 0) minOf(180f / (o.rahmenMm * 72f / 25.4f), 9000f / maxOf(box.width, box.height)) else if (gross) passend * 3f else passend * 1.5f
                    PDFRenderer(it).renderImage(0, skala.coerceIn(0.05f, 4f)).toComposeImageBitmap() to info
                }
            }
        }
    }

    DialogWindow(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_chart_window),
        state = rememberDialogState(width = 1280.dp, height = 860.dp),
        onPreviewKeyEvent = { e -> if (e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Tafelarten
                Column(Modifier.width(210.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    listOf(
                        Res.string.desk_chart_group_ancestors to listOf(TafelArt.Ahnen, TafelArt.AhnenSeiten, TafelArt.Faecher, TafelArt.Kreis, TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester),
                        Res.string.desk_chart_group_descendants to listOf(TafelArt.Stamm),
                        Res.string.desk_chart_group_both to listOf(TafelArt.Sanduhr),
                    ).forEachIndexed { i, (gruppe, arten) ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        ArtGruppe(stringResource(gruppe))
                        arten.forEach { a -> ArtEintrag(stringResource(artTexte.getValue(a).first), art == a) { art = a; gross = false } }
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Einstellungen
                Column(Modifier.width(330.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(artTexte.getValue(art).first), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(artTexte.getValue(art).second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Einstellung(stringResource(Res.string.desk_chart_person)) { Text(wurzelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    Einstellung(stringResource(if (art == TafelArt.Sanduhr) Res.string.desk_chart_generations_anc else Res.string.desk_chart_generations)) {
                        Auswahl(o.generationen.toString(), (2..maxGen(art)).map { it.toString() }) { o = o.copy(generationen = it.toInt()) }
                    }
                    if (art == TafelArt.Sanduhr) Einstellung(stringResource(Res.string.desk_chart_generations_desc)) {
                        Auswahl(o.nachfahren.toString(), (1..9).map { it.toString() }) { o = o.copy(nachfahren = it.toInt()) }
                    }
                    Einstellung(stringResource(Res.string.desk_chart_style)) {
                        val namen = TafelStil.entries.associateWith { stringResource(stilNamen.getValue(it)) }
                        Auswahl(namen.getValue(o.stil), namen.values.toList()) { w -> o = o.copy(stil = namen.entries.first { it.value == w }.key) }
                    }
                    Einstellung(stringResource(Res.string.desk_chart_box_width)) {
                        Auswahl("${o.rahmenMm} mm", (20..60 step 5).map { "$it mm" }) { o = o.copy(rahmenMm = it.substringBefore(' ').toInt()) }
                    }
                    if (art in waagerechtMoeglich) Haken(stringResource(Res.string.desk_chart_horizontal), o.waagerecht) { o = o.copy(waagerecht = it) }
                    Haken(stringResource(Res.string.desk_chart_photos), o.bilder) { o = o.copy(bilder = it) }
                    if (art != TafelArt.Stamm) Haken(stringResource(Res.string.desk_chart_numbers), o.nummern) { o = o.copy(nummern = it) }
                    if (art == TafelArt.Ahnen) Haken(stringResource(if (o.waagerecht) Res.string.desk_chart_root_right else Res.string.desk_chart_root_top), o.ausgangOben) { o = o.copy(ausgangOben = it) }
                    if (art == TafelArt.Stamm || art == TafelArt.Sanduhr) {
                        Haken(stringResource(Res.string.desk_chart_name_bearers), o.namenstraeger) { o = o.copy(namenstraeger = it) }
                        Haken(stringResource(Res.string.desk_chart_spouses), o.partner) { o = o.copy(partner = it) }
                    }
                    if (art in linien) Haken(stringResource(Res.string.desk_chart_both_parents), o.partner) { o = o.copy(partner = it) }
                    if (art !in kreise) {
                        Haken(stringResource(Res.string.desk_chart_places), o.orte) { o = o.copy(orte = it) }
                        Haken(stringResource(Res.string.desk_chart_full_dates), o.volleDaten) { o = o.copy(volleDaten = it) }
                    }
                    OutlinedTextField(o.titel, { o = o.copy(titel = it) }, label = { Text(stringResource(Res.string.desk_chart_heading)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val info = vorschau?.getOrNull()?.second
                    info?.let {
                        Text(if (it.seiten > 0) stringResource(Res.string.desk_chart_size_pages, it.personen, it.seiten) else stringResource(Res.string.desk_chart_size, it.personen, it.breiteCm, it.hoeheCm),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(Res.string.desk_chart_zoom_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val bereit = info != null
                    val titel = o.titel.ifBlank { titelVorgabe }
                    // Beim Drucken bleibt das Blatt offen: der Druck laeuft im Hintergrund und greift auf seine Inhalte zu.
                    if (art == TafelArt.AhnenSeiten) {
                        Knopf(stringResource(Res.string.desk_chart_print_pages), bereit) { erzeugen()?.first?.let { drucken(it, titel) } }
                        Knopf(stringResource(Res.string.desk_chart_pdf_pages), bereit) { erzeugen()?.first?.let { alsPdf(it, titel) } }
                    } else {
                        Knopf(stringResource(Res.string.desk_chart_print_one), bereit) { erzeugen()?.first?.let { drucken(aufEinBlatt(it), titel) } }
                        Knopf(stringResource(Res.string.desk_chart_print_tiles), bereit) { erzeugen()?.first?.let { drucken(aufA4Blaetter(it), titel) } }
                        Knopf(stringResource(Res.string.desk_chart_pdf_poster), bereit) { erzeugen()?.first?.let { alsPdf(it, titel) } }
                        Knopf(stringResource(Res.string.desk_chart_pdf_tiles), bereit) { erzeugen()?.first?.let { p -> p.use { alsPdf(aufA4Blaetter(it), "$titel A4") } } }
                    }
                    Spacer(Modifier.height(4.dp))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Vorschau
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF8C8F8E)).padding(16.dp), contentAlignment = Alignment.Center) {
                    val dichte = LocalDensity.current
                    val px = with(dichte) { maxWidth.roundToPx() to maxHeight.roundToPx() }
                    LaunchedEffect(px) { vorschauPx = px }
                    val fehler = daten?.exceptionOrNull() ?: vorschau?.exceptionOrNull()
                    val bild = vorschau?.getOrNull()?.first
                    when {
                        fehler != null -> Text(fehler.message ?: "?", color = androidx.compose.ui.graphics.Color.White)
                        vorschau != null && bild == null -> Text(stringResource(Res.string.desk_chart_empty), color = androidx.compose.ui.graphics.Color.White)
                        bild == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                            Text(stringResource(Res.string.desk_chart_loading_any), Modifier.padding(top = 8.dp), color = androidx.compose.ui.graphics.Color.White)
                        }
                        gross -> Box(Modifier.fillMaxSize()) {
                            val quer = rememberScrollState(); val hoch = rememberScrollState()
                            val dp = with(dichte) { bild.width.toDp() to bild.height.toDp() }
                            // Beginnt mittig oben - dort steht die Ausgangsperson
                            LaunchedEffect(bild.width, quer.maxValue) { if (quer.value == 0) quer.scrollTo(quer.maxValue / 2) }
                            Box(Modifier.fillMaxSize().horizontalScroll(quer).verticalScroll(hoch)) {
                                Image(bild, contentDescription = null, modifier = Modifier.size(dp.first, dp.second).clickable { gross = false })
                            }
                            SenkrechteLeiste(hoch)
                            WaagerechteLeiste(quer)
                        }
                        else -> Image(bild, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clickable { gross = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun Haken(text: String, wert: Boolean, onWechsel: (Boolean) -> Unit) {
    Row(Modifier.clickable { onWechsel(!wert) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = wert, onCheckedChange = onWechsel, modifier = Modifier.size(32.dp))
        Text(text, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ArtGruppe(text: String) {
    Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ArtEintrag(text: String, aktiv: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .fokusRahmen().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium, fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal)
}

@Composable
private fun Einstellung(label: String, inhalt: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f)) { inhalt() }
    }
}

@Composable
private fun Auswahl(wert: String, werte: List<String>, onWahl: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { offen = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(wert, Modifier.weight(1f), maxLines = 1)
            Text("▾")
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            werte.forEach { w -> DropdownMenuItem(text = { Text(w, fontWeight = if (w == wert) FontWeight.SemiBold else FontWeight.Normal) }, onClick = { offen = false; onWahl(w) }) }
        }
    }
}

@Composable
private fun Knopf(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) { Text(text) }
}
