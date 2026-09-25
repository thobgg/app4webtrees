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
 * Ahnentafel und Stammtafel; Sanduhr und Faecher sollen auf denselben Zeichenkern folgen.
 */

/**
 * Nachfahren bis [generationen] tief. api4webtrees liefert ab 1.8.0 bis 10 Generationen am Stueck, aeltere nur 4 -
 * dann holt die Tafel die fehlenden Ebenen je Blatt nach (die Antwort nennt die gelieferte Tiefe).
 */
suspend fun nachfahrenLaden(client: WtClient, tree: String, xref: String, generationen: Int): DescendantNode {
    val r = client.descendants(tree, xref, generationen)
    if (r.generations >= generationen || r.generations < 1) return r.tree
    suspend fun erweitern(k: DescendantNode, tiefe: Int): DescendantNode =
        if (tiefe == r.generations) {
            if (k.person.isPrivate || k.person.xref.isEmpty()) k else nachfahrenLaden(client, tree, k.person.xref, generationen - tiefe + 1)
        } else k.copy(families = k.families.map { f -> f.copy(children = f.children.map { erweitern(it, tiefe + 1) }) })
    return erweitern(r.tree, 1)
}

/** Aus dem Nachkommenbaum der API die Personen der Tafel, hoechstens [generationen] Ebenen. */
fun tafelBaum(k: DescendantNode, generationen: Int, tiefe: Int = 1): TafelPerson =
    TafelPerson(k.person, if (tiefe >= generationen) emptyList() else k.families.flatMap { it.children }.map { tafelBaum(it, generationen, tiefe + 1) })

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

private fun alle(k: TafelPerson): List<Person> = listOf(k.person) + k.kinder.flatMap(::alle)

private val stilNamen: Map<TafelStil, StringResource> = mapOf(
    TafelStil.Pergament to Res.string.desk_style_parchment, TafelStil.Klassisch to Res.string.desk_style_classic,
    TafelStil.Farbig to Res.string.desk_style_colour, TafelStil.Schwarzweiss to Res.string.desk_style_bw,
)

/** Ahnentafel aus der Ahnenliste der API (Kekule-Nummern): Vater 2n, Mutter 2n+1, hoechstens [generationen] Reihen. */
fun ahnenBaum(ahnen: Map<Int, de.bgghome.webtrees.nativ.api.Ancestor>, generationen: Int): TafelPerson? {
    fun reihe(n: Int) = 31 - Integer.numberOfLeadingZeros(n)
    fun knoten(n: Int): TafelPerson? = ahnen[n]?.let { a ->
        TafelPerson(a.person, if (reihe(n) + 1 >= generationen) emptyList() else listOfNotNull(knoten(2 * n), knoten(2 * n + 1)), n)
    }
    return knoten(1)
}

/** Groesste Tiefe je Tafelart: Nachfahren liefert api4webtrees ab 1.8.0 bis 10, Vorfahren bis 7 Generationen. */
private fun maxGen(art: TafelArt) = if (art == TafelArt.Stamm) 10 else 7

/** Die Einstellungen bleiben je Tafelart zwischen den Aufrufen erhalten (Desktop-Einstellungen). */
private object TafelWahl {
    private val prefs get() = DeskLayout.prefs
    private fun k(art: TafelArt, name: String) = if (art == TafelArt.Stamm) "tafel_$name" else "tafel_ahnen_$name"
    fun letzte(): TafelArt = TafelArt.entries.firstOrNull { it.name == prefs.getString("tafel_art", null) } ?: TafelArt.Stamm
    fun laden(art: TafelArt) = TafelOptionen(
        generationen = (prefs.getString(k(art, "gen"), null)?.toIntOrNull() ?: if (art == TafelArt.Stamm) 6 else 5).coerceIn(2, maxGen(art)),
        stil = TafelStil.entries.firstOrNull { it.name == prefs.getString(k(art, "stil"), null) } ?: TafelStil.Pergament,
        rahmenMm = prefs.getString(k(art, "rahmen"), null)?.toIntOrNull() ?: 30,
        bilder = prefs.getBoolean(k(art, "bilder"), true),
        nummern = prefs.getBoolean(k(art, "nummern"), true),
    )
    fun sichern(art: TafelArt, o: TafelOptionen) {
        prefs.putString("tafel_art", art.name)
        prefs.putString(k(art, "gen"), o.generationen.toString()); prefs.putString(k(art, "stil"), o.stil.name)
        prefs.putString(k(art, "rahmen"), o.rahmenMm.toString()); prefs.putBoolean(k(art, "bilder"), o.bilder)
        prefs.putBoolean(k(art, "nummern"), o.nummern)
    }
}

@Composable
fun TafelFenster(state: UiState, viewModel: AppViewModel, start: TafelArt?, onClose: () -> Unit) {
    val appName = LocalAppName.current
    val tree = state.tree
    val root = state.root
    val wurzelName = state.detail?.takeIf { it.person.xref == root }?.person?.name ?: state.people.firstOrNull { it.xref == root }?.name.orEmpty()
    var art by remember { mutableStateOf(start ?: TafelWahl.letzte()) }
    val titelStamm = stringResource(Res.string.desk_chart_title_default, wurzelName)
    val titelAhnen = stringResource(Res.string.desk_chart_title_ancestors, wurzelName)
    val titelVorgabe = if (art == TafelArt.Stamm) titelStamm else titelAhnen
    var o by remember(art) { mutableStateOf(TafelWahl.laden(art).copy(titel = titelVorgabe)) }
    LaunchedEffect(art, o) { TafelWahl.sichern(art, o) }
    val privat = stringResource(Res.string.person_private)
    val fuss = remember(tree) { fusszeile(appName, tree?.title.orEmpty()) }

    // Daten: einmal in voller Tiefe laden, dann nur noch kuerzen - das Umstellen der Generationen geht sofort.
    val daten by produceState<Result<(Int) -> TafelPerson?>?>(null, tree?.name, root, art) {
        value = if (tree == null || root == null) null else withContext(Dispatchers.IO) {
            runCatching {
                when (art) {
                    TafelArt.Stamm -> nachfahrenLaden(viewModel.client, tree.name, root, maxGen(art)).let { d -> { g: Int -> tafelBaum(d, g) } }
                    TafelArt.Ahnen -> viewModel.client.pedigree(tree.name, root, maxGen(art)).ancestors.associateBy { it.n }.let { m -> { g: Int -> ahnenBaum(m, g) } }
                }
            }
        }
    }
    val baum = daten?.getOrNull()?.invoke(o.generationen)
    // Bilder im Hintergrund laden; jedes fertige Bild zaehlt hoch und zeichnet die Vorschau neu.
    var bilderStand by remember { mutableStateOf(0) }
    LaunchedEffect(daten, o.bilder) {
        val voll = daten?.getOrNull()?.invoke(maxGen(art)) ?: return@LaunchedEffect
        if (!o.bilder) return@LaunchedEffect
        val urls = alle(voll).mapNotNull { it.thumb }.distinct().filter { TafelBilder.bekannt(it) == null }
        urls.chunked(8).forEach { gruppe ->
            withContext(Dispatchers.IO) { gruppe.forEach { TafelBilder.laden(it) } }
            bilderStand++
        }
    }
    fun erzeugen() = baum?.let { tafelPdf(it, o, { p -> p.thumb?.let(TafelBilder::bekannt) }, privat, fuss, art) }

    // Vorschau: das Blatt als Bild, kurz verzoegert, damit schnelles Umstellen nicht jedes Mal rendert. Das PDF wird
    // dafuer einmal gespeichert und neu geladen - erst beim Speichern bettet PDFBox die Schriften ein, vorher zeichnet
    // der Renderer den Titel in einer Ersatzschrift. [gross]: Blatt in Lesegroesse, rollbar.
    var vorschauPx by remember { mutableStateOf(1000 to 800) }
    var gross by remember { mutableStateOf(false) }
    val vorschau by produceState<Pair<ImageBitmap, TafelInfo>?>(null, baum, o, art, bilderStand, vorschauPx, gross) {
        delay(150)
        val b = baum ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                val (doc, info) = tafelPdf(b, o, { p -> p.thumb?.let(TafelBilder::bekannt) }, privat, fuss, art)
                val bytes = java.io.ByteArrayOutputStream().also { out -> doc.use { it.save(out) } }.toByteArray()
                org.apache.pdfbox.Loader.loadPDF(bytes).use {
                    val box = it.getPage(0).mediaBox
                    val passend = minOf(vorschauPx.first / box.width, vorschauPx.second / box.height)
                    // Gross: Personenrahmen etwa 180 Pixel breit, das Bild aber hoechstens 9000 Pixel an der langen Seite
                    val skala = if (gross) minOf(180f / (o.rahmenMm * 72f / 25.4f), 9000f / maxOf(box.width, box.height)) else passend * 1.5f
                    PDFRenderer(it).renderImage(0, skala.coerceIn(0.05f, 4f)).toComposeImageBitmap() to info
                }
            }.getOrNull()
        }
    }

    DialogWindow(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_chart_window),
        state = rememberDialogState(width = 1280.dp, height = 820.dp),
        onPreviewKeyEvent = { e -> if (e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Tafelarten
                Column(Modifier.width(190.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    ArtGruppe(stringResource(Res.string.desk_chart_group_ancestors))
                    ArtEintrag(stringResource(Res.string.desk_chart_ancestors), art == TafelArt.Ahnen) { art = TafelArt.Ahnen }
                    Spacer(Modifier.height(8.dp))
                    ArtGruppe(stringResource(Res.string.desk_chart_group_descendants))
                    ArtEintrag(stringResource(Res.string.desk_chart_descendants), art == TafelArt.Stamm) { art = TafelArt.Stamm }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Einstellungen
                Column(Modifier.width(330.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(if (art == TafelArt.Stamm) Res.string.desk_chart_descendants else Res.string.desk_chart_ancestors), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (art == TafelArt.Stamm) Res.string.desk_chart_descendants_hint else Res.string.desk_chart_ancestors_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Einstellung(stringResource(Res.string.desk_chart_person)) { Text(wurzelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    Einstellung(stringResource(Res.string.desk_chart_generations)) {
                        Auswahl(o.generationen.toString(), (2..maxGen(art)).map { it.toString() }) { o = o.copy(generationen = it.toInt()) }
                    }
                    Einstellung(stringResource(Res.string.desk_chart_style)) {
                        val namen = TafelStil.entries.associateWith { stringResource(stilNamen.getValue(it)) }
                        Auswahl(namen.getValue(o.stil), namen.values.toList()) { w -> o = o.copy(stil = namen.entries.first { it.value == w }.key) }
                    }
                    Einstellung(stringResource(Res.string.desk_chart_box_width)) {
                        Auswahl("${o.rahmenMm} mm", (20..60 step 5).map { "$it mm" }) { o = o.copy(rahmenMm = it.substringBefore(' ').toInt()) }
                    }
                    Row(Modifier.clickable { o = o.copy(bilder = !o.bilder) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = o.bilder, onCheckedChange = { o = o.copy(bilder = it) })
                        Text(stringResource(Res.string.desk_chart_photos), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (art == TafelArt.Ahnen) Row(Modifier.clickable { o = o.copy(nummern = !o.nummern) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = o.nummern, onCheckedChange = { o = o.copy(nummern = it) })
                        Text(stringResource(Res.string.desk_chart_numbers), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedTextField(o.titel, { o = o.copy(titel = it) }, label = { Text(stringResource(Res.string.desk_chart_heading)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    vorschau?.second?.let { info ->
                        Text(stringResource(Res.string.desk_chart_size, info.personen, info.breiteCm, info.hoeheCm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(Res.string.desk_chart_zoom_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val bereit = baum != null
                    val titel = o.titel.ifBlank { titelVorgabe }
                    // Beim Drucken bleibt das Blatt offen: der Druck laeuft im Hintergrund und greift auf seine Inhalte zu.
                    Knopf(stringResource(Res.string.desk_chart_print_one), bereit) { erzeugen()?.first?.let { drucken(aufEinBlatt(it), titel) } }
                    Knopf(stringResource(Res.string.desk_chart_print_tiles), bereit) { erzeugen()?.first?.let { drucken(aufA4Blaetter(it), titel) } }
                    Knopf(stringResource(Res.string.desk_chart_pdf_poster), bereit) { erzeugen()?.first?.let { alsPdf(it, titel) } }
                    Knopf(stringResource(Res.string.desk_chart_pdf_tiles), bereit) { erzeugen()?.first?.let { p -> p.use { alsPdf(aufA4Blaetter(it), "$titel A4") } } }
                    Spacer(Modifier.height(4.dp))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Vorschau
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF8C8F8E)).padding(16.dp), contentAlignment = Alignment.Center) {
                    val dichte = LocalDensity.current
                    val px = with(dichte) { maxWidth.roundToPx() to maxHeight.roundToPx() }
                    LaunchedEffect(px) { vorschauPx = px }
                    val fehler = daten?.exceptionOrNull()
                    when {
                        fehler != null -> Text(fehler.message ?: "?", color = androidx.compose.ui.graphics.Color.White)
                        vorschau == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                            Text(stringResource(Res.string.desk_chart_loading), Modifier.padding(top = 8.dp), color = androidx.compose.ui.graphics.Color.White)
                        }
                        gross -> Box(Modifier.fillMaxSize()) {
                            val quer = rememberScrollState(); val hoch = rememberScrollState()
                            val bild = vorschau!!.first
                            val dp = with(dichte) { bild.width.toDp() to bild.height.toDp() }
                            // Beginnt mittig oben - dort steht die Ausgangsperson
                            LaunchedEffect(bild.width, quer.maxValue) { if (quer.value == 0) quer.scrollTo(quer.maxValue / 2) }
                            Box(Modifier.fillMaxSize().horizontalScroll(quer).verticalScroll(hoch)) {
                                Image(bild, contentDescription = null, modifier = Modifier.size(dp.first, dp.second).clickable { gross = false })
                            }
                            SenkrechteLeiste(hoch)
                            WaagerechteLeiste(quer)
                        }
                        else -> Image(vorschau!!.first, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clickable { gross = true })
                    }
                }
            }
        }
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
