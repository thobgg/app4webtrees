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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
    fun laden(url: String, tree: String): BufferedImage? {
        cache[url]?.let { return it }
        if (url in fehlt) return null
        // Ueber den Bildspeicher (Zwischenspeicher auf der Platte, Server); liefert RGB ohne Transparenz
        val rgb = Bilder.laden(Desktop.plattform.client, Desktop.plattform.client.baseUrl, tree, Bilder.Quelle(url))
        if (rgb != null) cache[url] = rgb else fehlt += url
        return rgb
    }
}

private fun nachfahrenPersonen(k: DescendantNode): List<Person> = listOf(k.person) + k.families.flatMap { it.children }.flatMap(::nachfahrenPersonen)

/** Datei fuer eine Tafel-Vorlage waehlen: [speichern] mit Vorschlag [name], sonst oeffnen; null bei Abbruch. */
private fun vorlageDatei(speichern: Boolean, name: String): java.io.File? {
    val d = java.awt.FileDialog(null as java.awt.Frame?, de.bgghome.webtrees.nativ.Texte.t(if (speichern) Res.string.desk_chart_template_save else Res.string.desk_chart_template_load),
        if (speichern) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD).apply {
        if (speichern) file = name.replace(Regex("[\\\\/:*?\"<>|]"), "_") else setFilenameFilter { _, n -> n.endsWith(".wtvorlage", true) }
        isVisible = true
    }
    val f = d.file ?: return null
    return java.io.File(d.directory, if (speichern && !f.endsWith(".wtvorlage", true)) "$f.wtvorlage" else f)
}

/** Bilddatei fuer den Tafelhintergrund waehlen (Wappen, Karte ...); null bei Abbruch. */
private fun bildWaehlen(): String? {
    val d = java.awt.FileDialog(null as java.awt.Frame?, de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_chart_bg_choose), java.awt.FileDialog.LOAD).apply {
        setFilenameFilter { _, n -> n.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } }
        isVisible = true
    }
    return d.file?.let { java.io.File(d.directory, it).absolutePath }
}

/** Alle geladenen Personen einer Tafel (fuer Bilder und Karteikarten). */
private fun datenPersonen(d: TafelDaten): List<Person> =
    d.ahnen.values.map { it.person } + (d.nachfahren?.let(::nachfahrenPersonen) ?: emptyList()) + (d.mutterseite?.let(::nachfahrenPersonen) ?: emptyList()) +
        d.partnerAhnen.values.map { it.person } + d.stammpaare.flatMap { nachfahrenPersonen(it.baum) } + d.geschwister.values.flatten()

private val stilNamen: Map<TafelStil, StringResource> = mapOf(
    TafelStil.Pergament to Res.string.desk_style_parchment, TafelStil.Klassisch to Res.string.desk_style_classic,
    TafelStil.Farbig to Res.string.desk_style_colour, TafelStil.Schwarzweiss to Res.string.desk_style_bw,
)

/** Name und Erklaerung je Tafelart. */
private val artTexte: Map<TafelArt, Pair<StringResource, StringResource>> = mapOf(
    TafelArt.Ahnen to (Res.string.desk_chart_ancestors to Res.string.desk_chart_ancestors_hint),
    TafelArt.AhnenSeiten to (Res.string.desk_chart_ancestors_pages to Res.string.desk_chart_ancestors_pages_hint),
    TafelArt.Faecher to (Res.string.desk_chart_fan to Res.string.desk_chart_fan_hint),
    TafelArt.Zeitleiste to (Res.string.desk_chart_timeline to Res.string.desk_chart_timeline_hint),
    TafelArt.Kreis to (Res.string.desk_chart_circle to Res.string.desk_chart_circle_hint),
    TafelArt.Stammlinie to (Res.string.desk_chart_paternal to Res.string.desk_chart_paternal_hint),
    TafelArt.Mutterstamm to (Res.string.desk_chart_maternal to Res.string.desk_chart_maternal_hint),
    TafelArt.Aeltester to (Res.string.desk_chart_oldest to Res.string.desk_chart_oldest_hint),
    TafelArt.Stamm to (Res.string.desk_chart_descendants to Res.string.desk_chart_descendants_hint),
    TafelArt.StammSeiten to (Res.string.desk_chart_descendants_pages to Res.string.desk_chart_descendants_pages_hint),
    TafelArt.Cousins to (Res.string.desk_chart_cousins to Res.string.desk_chart_cousins_hint),
    TafelArt.Sanduhr to (Res.string.desk_chart_hourglass to Res.string.desk_chart_hourglass_hint),
    TafelArt.Paar to (Res.string.desk_chart_couple to Res.string.desk_chart_couple_hint),
    TafelArt.Weg to (Res.string.desk_chart_way to Res.string.desk_chart_way_hint),
    TafelArt.Verwandt to (Res.string.desk_chart_relatives to Res.string.desk_chart_relatives_hint),
)

private val linien = setOf(TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester)

/** Faechertafel und Ahnenkreis: ohne Orte und volle Daten (dafuer ist in den Ringen kein Platz). */
private val kreise = setOf(TafelArt.Faecher, TafelArt.Kreis)

/** Tafeln ohne Kaesten (Ringe, Zeitleiste): keine Kastenform, kein Kasteninhalt. */
private val ohneKaesten = kreise + TafelArt.Zeitleiste

/** Tafeln mit Gitter, Personenverzeichnis und Kurven fuer Doppelte (nicht Kreise und die seitenweise Ahnentafel). */
private val mitGitterArten = setOf(TafelArt.Ahnen, TafelArt.Stamm, TafelArt.Cousins, TafelArt.Sanduhr, TafelArt.Paar, TafelArt.Verwandt, TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester)

/** Tafeln mit Kastenfarben (Regeln, Zweige); [linienArten] kennen Kekule-Nummern, [zweigArten] Nachfahren. */
private val farbArten = mitGitterArten + TafelArt.AhnenSeiten + TafelArt.StammSeiten + TafelArt.Paar
private val linienArten = setOf(TafelArt.Verwandt, TafelArt.Ahnen, TafelArt.AhnenSeiten, TafelArt.Sanduhr, TafelArt.Paar, TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester)
private val zweigArten = setOf(TafelArt.Verwandt, TafelArt.Stamm, TafelArt.StammSeiten, TafelArt.Cousins, TafelArt.Sanduhr, TafelArt.Paar)

/** Tafeln, deren Personen mittig, links- oder rechtsbuendig ueber den Kindern (bzw. unter den Eltern) stehen koennen. */
private val buendigArten = setOf(TafelArt.Ahnen, TafelArt.AhnenSeiten, TafelArt.Stamm, TafelArt.StammSeiten, TafelArt.Sanduhr)

/** Einblattige Tafeln aus dem Zeichenkern: sie koennen Karteikarten tragen. */
private val kartenArten = mitGitterArten + TafelArt.Paar + TafelArt.AhnenSeiten + TafelArt.StammSeiten + TafelArt.Faecher + TafelArt.Kreis

/** Tafeln, die auch waagerecht gehen (Linien bleiben senkrecht). */
private val waagerechtMoeglich = setOf(TafelArt.Ahnen, TafelArt.AhnenSeiten, TafelArt.Stamm, TafelArt.StammSeiten, TafelArt.Cousins, TafelArt.Sanduhr, TafelArt.Paar)

/** Die Einstellungen bleiben je Tafelart zwischen den Aufrufen erhalten (Desktop-Einstellungen). */
private object TafelWahl {
    /** Beim Speichern und Laden einer Vorlage (E4) schreibt und liest TafelWahl kurz in eine Textablage statt in die Einstellungen. */
    private var andere: de.bgghome.webtrees.nativ.data.Ablage? = null
    private val prefs get() = andere ?: DeskLayout.prefs

    /** Einfache Ablage im Speicher fuer Vorlagen; Wahrheitswerte als "true"/"false". */
    private class TextAblage(val m: MutableMap<String, String> = sortedMapOf()) : de.bgghome.webtrees.nativ.data.Ablage {
        override fun getString(key: String, default: String?) = m[key] ?: default
        override fun putString(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key]?.toBooleanStrictOrNull() ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value.toString() }
        override fun alle(): Map<String, String> = m
        override fun leeren() = m.clear()
    }

    /** Alle Einstellungen dieser Tafelart als Text (Schluessel=Wert, wie eine .properties-Datei). */
    fun vorlageText(art: TafelArt, o: TafelOptionen): String {
        val t = TextAblage(); andere = t
        try { sichern(art, o) } finally { andere = null }
        return "# app4webtrees Tafel-Vorlage\n" + t.m.entries.joinToString("\n") { "${it.key}=${it.value.replace("\n", " ")}" } + "\n"
    }

    /** Eine Vorlage lesen: ihre Tafelart und Einstellungen - oder null, wenn der Text keine Vorlage ist. */
    fun vorlageLesen(text: String): Pair<TafelArt, TafelOptionen>? {
        val t = TextAblage()
        text.lines().filter { '=' in it && !it.startsWith("#") }.forEach { t.m[it.substringBefore('=').trim()] = it.substringAfter('=').trim() }
        val art = TafelArt.entries.firstOrNull { it.name == t.m["tafel_art"] } ?: return null
        andere = t
        try { return art to laden(art) } finally { andere = null }
    }
    private fun k(art: TafelArt, name: String) = when (art) {
        TafelArt.Stamm -> "tafel_$name"
        TafelArt.Ahnen -> "tafel_ahnen_$name"
        else -> "tafel_${art.name.lowercase()}_$name"
    }
    private fun vorgabe(art: TafelArt) = when (art) {
        TafelArt.Stamm, TafelArt.StammSeiten -> 6; TafelArt.Cousins -> 4; TafelArt.Ahnen, TafelArt.Faecher -> 5; TafelArt.Kreis -> 6; TafelArt.Sanduhr -> 3; TafelArt.Paar -> 4; TafelArt.Verwandt -> 3; TafelArt.Zeitleiste -> 5; TafelArt.Weg -> 10; TafelArt.AhnenSeiten -> 7; else -> 13
    }
    fun letzte(): TafelArt = TafelArt.entries.firstOrNull { it.name == prefs.getString("tafel_art", null) } ?: TafelArt.Stamm
    fun laden(art: TafelArt) = TafelOptionen(
        generationen = (prefs.getString(k(art, "gen"), null)?.toIntOrNull() ?: vorgabe(art)).coerceIn(minGen(art), maxGen(art)),
        stil = TafelStil.entries.firstOrNull { it.name == prefs.getString(k(art, "stil"), null) } ?: TafelStil.Pergament,
        rahmenMm = prefs.getString(k(art, "rahmen"), null)?.toIntOrNull() ?: 30,
        bilder = prefs.getBoolean(k(art, "bilder"), true),
        nummern = prefs.getBoolean(k(art, "nummern"), true),
        ausgangOben = prefs.getBoolean(k(art, "oben"), false),
        nachfahren = if (art == TafelArt.Verwandt) (prefs.getString(k(art, "nach"), null)?.toIntOrNull() ?: 1).coerceIn(0, 3)
            else (prefs.getString(k(art, "nach"), null)?.toIntOrNull() ?: 3).coerceIn(1, 9),
        namenstraeger = prefs.getBoolean(k(art, "namen"), false),
        partner = prefs.getBoolean(k(art, "partner"), art in linien || art == TafelArt.Cousins || art == TafelArt.Paar || art == TafelArt.Verwandt),
        orte = prefs.getBoolean(k(art, "orte"), false),
        volleDaten = prefs.getBoolean(k(art, "voll"), false),
        waagerecht = art in waagerechtMoeglich && prefs.getBoolean(k(art, "waagerecht"), art == TafelArt.Sanduhr),
        geschwister = (prefs.getString(k(art, "geschw"), null)?.toIntOrNull() ?: 0).coerceIn(0, 2),
        gitter = prefs.getBoolean(k(art, "gitter"), art == TafelArt.Verwandt), verzeichnis = prefs.getBoolean(k(art, "verz"), art == TafelArt.StammSeiten), kurven = prefs.getBoolean(k(art, "kurven"), false),
        farbe = FarbSchema.entries.firstOrNull { it.name == prefs.getString(k(art, "farbe"), null) } ?: FarbSchema.Geschlecht,
        jeSeite = (prefs.getString(k(art, "jeseite"), null)?.toIntOrNull() ?: 3).coerceIn(2, 5),
        karteikarten = prefs.getBoolean(k(art, "karten"), false),
        legende = prefs.getBoolean(k(art, "legende"), true),
        form = KastenForm.entries.firstOrNull { it.name == prefs.getString(k(art, "form"), null) } ?: KastenForm.Stil,
        schatten = prefs.getBoolean(k(art, "schatten"), false), fotoLinks = prefs.getBoolean(k(art, "fotolinks"), false),
        hintergrund = TafelHintergrund.entries.firstOrNull { it.name == prefs.getString(k(art, "hg"), null) } ?: TafelHintergrund.Stil,
        hintergrundBild = prefs.getString("tafel_hg_bild", null).orEmpty(),
        schmuck = Schmuckrahmen.entries.firstOrNull { it.name == prefs.getString(k(art, "schmuck"), null) } ?: Schmuckrahmen.Keiner,
        buendig = (prefs.getString(k(art, "buendig"), null)?.toIntOrNull() ?: 0).coerceIn(0, 2),
        datumsArt = (prefs.getString(k(art, "datum"), null)?.toIntOrNull() ?: 0).coerceIn(0, 2),
        ortTeile = (prefs.getString(k(art, "ortteile"), null)?.toIntOrNull() ?: 1).coerceIn(0, 2),
        ersatz = prefs.getBoolean(k(art, "ersatz"), false), alter = prefs.getBoolean(k(art, "alter"), false),
        mehrHinweise = prefs.getBoolean(k(art, "mehr"), true),
        nurMitPartner = prefs.getBoolean(k(art, "nurpartner"), false),
        mindestalter = (prefs.getString(k(art, "mindestalter"), null)?.toIntOrNull() ?: 0).coerceIn(0, 99),
        nummernArt = (prefs.getString(k(art, "nrart"), null)?.toIntOrNull() ?: 0).coerceIn(0, 2),
        zeitereignisse = prefs.getBoolean(k(art, "ereignisse"), true), zeitNachfahren = prefs.getBoolean(k(art, "zeitnach"), false),
        beruf = prefs.getBoolean(k(art, "beruf"), false), rufname = prefs.getBoolean(k(art, "rufname"), false),
        geschwungen = prefs.getBoolean(k(art, "geschwungen"), false), lebendeNurNamen = prefs.getBoolean(k(art, "lebende"), false),
        ersteller = prefs.getString("tafel_ersteller", null).orEmpty(),
        uebersicht = prefs.getBoolean(k(art, "uebersicht"), true),
    )
    fun sichern(art: TafelArt, o: TafelOptionen) {
        prefs.putString("tafel_art", art.name)
        prefs.putString(k(art, "gen"), o.generationen.toString()); prefs.putString(k(art, "stil"), o.stil.name)
        prefs.putString(k(art, "rahmen"), o.rahmenMm.toString()); prefs.putBoolean(k(art, "bilder"), o.bilder)
        prefs.putBoolean(k(art, "nummern"), o.nummern); prefs.putBoolean(k(art, "oben"), o.ausgangOben)
        prefs.putString(k(art, "nach"), o.nachfahren.toString()); prefs.putBoolean(k(art, "namen"), o.namenstraeger)
        prefs.putBoolean(k(art, "partner"), o.partner); prefs.putBoolean(k(art, "orte"), o.orte); prefs.putBoolean(k(art, "voll"), o.volleDaten)
        prefs.putBoolean(k(art, "waagerecht"), o.waagerecht); prefs.putString(k(art, "geschw"), o.geschwister.toString())
        prefs.putBoolean(k(art, "gitter"), o.gitter); prefs.putBoolean(k(art, "verz"), o.verzeichnis); prefs.putBoolean(k(art, "kurven"), o.kurven)
        prefs.putString(k(art, "farbe"), o.farbe.name)
        prefs.putString(k(art, "jeseite"), o.jeSeite.toString()); prefs.putBoolean(k(art, "uebersicht"), o.uebersicht)
        prefs.putBoolean(k(art, "karten"), o.karteikarten); prefs.putBoolean(k(art, "legende"), o.legende)
        prefs.putString(k(art, "hg"), o.hintergrund.name); prefs.putString("tafel_hg_bild", o.hintergrundBild); prefs.putString(k(art, "schmuck"), o.schmuck.name)
        prefs.putString(k(art, "datum"), o.datumsArt.toString()); prefs.putString(k(art, "ortteile"), o.ortTeile.toString())
        prefs.putBoolean(k(art, "mehr"), o.mehrHinweise); prefs.putBoolean(k(art, "nurpartner"), o.nurMitPartner); prefs.putString(k(art, "mindestalter"), o.mindestalter.toString()); prefs.putString(k(art, "nrart"), o.nummernArt.toString()); prefs.putBoolean(k(art, "ereignisse"), o.zeitereignisse); prefs.putBoolean(k(art, "zeitnach"), o.zeitNachfahren); prefs.putBoolean(k(art, "ersatz"), o.ersatz); prefs.putBoolean(k(art, "alter"), o.alter); prefs.putBoolean(k(art, "beruf"), o.beruf); prefs.putBoolean(k(art, "rufname"), o.rufname)
        prefs.putString(k(art, "buendig"), o.buendig.toString()); prefs.putBoolean(k(art, "geschwungen"), o.geschwungen); prefs.putBoolean(k(art, "lebende"), o.lebendeNurNamen)
        prefs.putString(k(art, "form"), o.form.name); prefs.putBoolean(k(art, "schatten"), o.schatten); prefs.putBoolean(k(art, "fotolinks"), o.fotoLinks)
        prefs.putString("tafel_ersteller", o.ersteller)
    }
}

@Composable
fun TafelFenster(state: UiState, viewModel: AppViewModel, start: TafelArt?, onClose: () -> Unit) {
    val appName = LocalAppName.current
    val tree = state.tree
    val root = state.root
    val wurzelName = state.detail?.takeIf { it.person.xref == root }?.person?.name ?: state.people.firstOrNull { it.xref == root }?.name.orEmpty()
    var art by remember { mutableStateOf(start ?: TafelWahl.letzte()) }
    // Paar: welcher Ehepartner (Familie der Ausgangsperson in Folge), gilt nur fuer diesen Aufruf
    var paarFamilie by remember(root) { mutableStateOf(0) }
    var partnerName by remember(root) { mutableStateOf("") }
    val titelVorgabe = remember(art, wurzelName, partnerName) { tafelTitel(art, wurzelName, partnerName) }
    var o by remember(art) { mutableStateOf(TafelWahl.laden(art).copy(titel = titelVorgabe, untertitel = tree?.title.orEmpty())) }
    // Der Titel nennt beim Paar beide Namen - er steht erst fest, wenn der Partner geladen ist
    LaunchedEffect(titelVorgabe) { if (art == TafelArt.Paar) o = o.copy(titel = titelVorgabe) }
    LaunchedEffect(art, o) { TafelWahl.sichern(art, o) }
    // Farbregeln und gefaerbte Zweige gelten fuer alle Tafeln des Stammbaums
    val baumName = tree?.name.orEmpty()
    var regeln by remember(baumName) { mutableStateOf(TafelFarbSpeicher.regeln(baumName)) }
    var zweige by remember(baumName) { mutableStateOf(TafelFarbSpeicher.zweige(baumName)) }
    LaunchedEffect(baumName, regeln, zweige) { if (baumName.isNotEmpty()) TafelFarbSpeicher.sichern(baumName, regeln, zweige) }
    // Verwandtschaftstafel: abgewaehlte Stammpaare (nur fuer diesen Aufruf)
    var ohneStamm by remember(root) { mutableStateOf(emptySet<String>()) }
    // Ausgeblendete Zweige (Rechtsklick) gelten je Stammbaum
    var ausgeblendet by remember(baumName) { mutableStateOf(TafelFarbSpeicher.ausgeblendet(baumName)) }
    LaunchedEffect(baumName, ausgeblendet) { if (baumName.isNotEmpty()) TafelFarbSpeicher.ausgeblendetSichern(baumName, ausgeblendet) }
    val oVoll = o.copy(regeln = regeln, zweige = zweige, ohneStamm = ohneStamm, ausgeblendet = ausgeblendet)
    var druck by remember { mutableStateOf(DruckWahl.laden()) }
    LaunchedEffect(druck) { DruckWahl.sichern(druck) }
    val privat = stringResource(Res.string.person_private)
    val fuss = remember(tree) { fusszeile(appName, tree?.title.orEmpty()) }

    // Daten: Nachfahren einmal in voller Tiefe (Umstellen der Generationen kuerzt nur); Vorfahren so tief wie
    // eingestellt, weil jede Generation ueber sieben weitere Anfragen kostet.
    val ladeTiefe = if (art == TafelArt.Stamm || art == TafelArt.StammSeiten || art == TafelArt.Cousins) maxGen(art) else o.generationen
    val geschwisterLaden = if (art == TafelArt.Ahnen) o.geschwister else 0
    val paarLaden = if (art == TafelArt.Paar) paarFamilie else 0
    val nachLaden = if (art == TafelArt.Verwandt) o.nachfahren else 0
    // Verwandtschaftsweg: die zweite Person, vorbelegt mit der Bezugsperson des Benutzers
    var zweiter by remember(root) { mutableStateOf<Person?>(state.home?.takeIf { it != root }?.let { Person(xref = it) }) }
    val zweiterLaden = if (art == TafelArt.Weg) zweiter?.xref else null
    val daten by produceState<Result<TafelDaten>?>(null, tree?.name, root, art, ladeTiefe, geschwisterLaden, paarLaden, nachLaden, zweiterLaden) {
        value = null
        value = if (tree == null || root == null) null else withContext(Dispatchers.IO) {
            runCatching { tafelDatenLaden(viewModel.client, tree.name, root, art, ladeTiefe, geschwisterLaden, paarLaden, nachLaden, zweiterLaden) }
        }
    }
    // Titel des Wegs: beide Namen und die Verwandtschaft, sobald gerechnet
    LaunchedEffect(daten) {
        val w = daten?.getOrNull()?.weg ?: return@LaunchedEffect
        if (art == TafelArt.Weg) { if (zweiter?.name.isNullOrBlank()) zweiter = w.personB; o = o.copy(titel = "${w.personA.name} und ${w.personB.name}", untertitel = wegeText(daten?.getOrNull()?.wege.orEmpty())) }
    }
    LaunchedEffect(daten) { daten?.getOrNull()?.takeIf { art == TafelArt.Paar }?.let { d -> partnerName = d.partnerNamen.getOrNull(d.paarFamilie).orEmpty() } }
    // Bilder im Hintergrund laden; jedes fertige Buendel zaehlt hoch und zeichnet die Vorschau neu.
    var bilderStand by remember { mutableStateOf(0) }
    LaunchedEffect(daten, o.bilder) {
        val d = daten?.getOrNull() ?: return@LaunchedEffect
        if (!o.bilder) return@LaunchedEffect
        val urls = datenPersonen(d).mapNotNull { it.thumb }.distinct().filter { TafelBilder.bekannt(it) == null }
        urls.chunked(8).forEach { gruppe ->
            withContext(Dispatchers.IO) { gruppe.forEach { TafelBilder.laden(it, tree?.name.orEmpty()) } }
            bilderStand++
        }
    }
    // Karteikarten: die ausfuehrlichen Daten aller geladenen Personen, im Hintergrund; nur fuer Druck und PDF, nicht fuer die Vorschau
    val mitKarten = o.karteikarten && art in kartenArten
    val details by produceState<Map<String, de.bgghome.webtrees.nativ.api.IndividualDetail>?>(null, daten, mitKarten) {
        value = null
        val d = daten?.getOrNull() ?: return@produceState
        if (!mitKarten || tree == null) return@produceState
        value = withContext(Dispatchers.IO) { runCatching { kartenLaden(viewModel.client, tree.name, datenPersonen(d).filter { !it.isPrivate }.map { it.xref }) }.getOrElse { emptyMap() } }
    }
    fun erzeugen(ausgabe: Boolean = false) = daten?.getOrNull()?.let { d ->
        tafelErzeugen(art, d, oVoll, { p -> p.thumb?.let(TafelBilder::bekannt) }, privat, fuss, if (ausgabe && mitKarten) details else null)
    }

    // Vorschau: das Blatt als Bild, kurz verzoegert, damit schnelles Umstellen nicht jedes Mal rendert. Das PDF wird
    // dafuer einmal gespeichert und neu geladen - erst beim Speichern bettet PDFBox die Schriften ein, vorher zeichnet
    // der Renderer den Titel in einer Ersatzschrift. Zoom (TafelVorschau.kt): die Aufloesung waechst in Stufen mit.
    var vorschauPx by remember { mutableStateOf(1000 to 800) }
    var zoom by remember(art) { mutableStateOf(1f) }
    var versatz by remember(art) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val stufe = zoomStufe(zoom)
    val vorschau by produceState<Result<VorschauBild?>?>(null, daten, oVoll, art, bilderStand, vorschauPx, stufe) {
        delay(150)
        if (daten?.getOrNull() == null) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val (doc, info) = erzeugen() ?: return@runCatching null
                val bytes = java.io.ByteArrayOutputStream().also { out -> doc.use { it.save(out) } }.toByteArray()
                org.apache.pdfbox.Loader.loadPDF(bytes).use {
                    val box = it.getPage(0).mediaBox
                    val passend = minOf(vorschauPx.first / box.width, vorschauPx.second / box.height)
                    // Je Zoomstufe doppelt so fein, das Bild aber hoechstens 9000 Pixel an der langen Seite
                    val skala = minOf(passend * 1.5f * (1 shl stufe), 9000f / maxOf(box.width, box.height)).coerceIn(0.05f, 4f)
                    VorschauBild(PDFRenderer(it).renderImage(0, skala).toComposeImageBitmap(), skala, info)
                }
            }
        }
    }

    DialogWindow(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_chart_window),
        state = rememberDialogState(width = 1280.dp, height = 860.dp),
        onPreviewKeyEvent = { e -> when { e.key == Key.Escape -> { onClose(); true }; e.key == Key.F1 && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> { Hilfe.oeffnen("tafeln"); true }; else -> false } },
    ) {
        DeskTheme {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Tafelarten
                val artBreite = rememberBreite("tafel_arten", 210f)
                Column(Modifier.width(artBreite.value.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    listOf(
                        Res.string.desk_chart_group_ancestors to listOf(TafelArt.Ahnen, TafelArt.AhnenSeiten, TafelArt.Faecher, TafelArt.Kreis, TafelArt.Zeitleiste, TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester),
                        Res.string.desk_chart_group_descendants to listOf(TafelArt.Stamm, TafelArt.StammSeiten, TafelArt.Cousins),
                        Res.string.desk_chart_group_both to listOf(TafelArt.Sanduhr, TafelArt.Paar, TafelArt.Verwandt, TafelArt.Weg),
                    ).forEachIndexed { i, (gruppe, arten) ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        ArtGruppe(stringResource(gruppe))
                        arten.forEach { a -> ArtEintrag(stringResource(artTexte.getValue(a).first), art == a) { art = a } }
                    }
                }
                Trenner(artBreite, "tafel_arten", 160f, 400f)
                // Einstellungen: oben das Noetigste, darunter aufklappbare Gruppen (28.09.2026)
                val einstBreite = rememberBreite("tafel_einstellungen", 330f)
                Column(Modifier.width(einstBreite.value.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(artTexte.getValue(art).first), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(artTexte.getValue(art).second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Einstellung(stringResource(Res.string.desk_chart_person)) { Text(wurzelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    // Paar: mit mehreren Ehen waehlbar, welcher Partner neben der Person steht
                    val partnerNamen = daten?.getOrNull()?.takeIf { art == TafelArt.Paar }?.partnerNamen.orEmpty()
                    if (partnerNamen.size > 1) Einstellung(stringResource(Res.string.desk_chart_couple_partner)) {
                        val werte = partnerNamen.mapIndexed { i, n -> "${i + 1}. $n" }
                        Auswahl(werte[paarFamilie.coerceIn(0, werte.size - 1)], werte) { w -> paarFamilie = werte.indexOf(w) }
                    }
                    if (art == TafelArt.Weg) Einstellung(stringResource(Res.string.desk_chart_way_second)) {
                        PersonSuche(zweiter, { zweiter = it }) { q -> tree?.let { t -> runCatching { viewModel.client.individuals(t.name, q, 1).data }.getOrNull() }.orEmpty() }
                    }
                    if (art == TafelArt.Zeitleiste) {
                        Einstellung(stringResource(Res.string.desk_chart_timeline_of)) {
                            val namen = listOf(stringResource(Res.string.desk_chart_group_ancestors), stringResource(Res.string.desk_chart_group_descendants))
                            Auswahl(namen[if (o.zeitNachfahren) 1 else 0], namen) { w -> o = o.copy(zeitNachfahren = namen.indexOf(w) == 1) }
                        }
                        Haken(stringResource(Res.string.desk_chart_events), o.zeitereignisse, stringResource(Res.string.tipp_events)) { o = o.copy(zeitereignisse = it) }
                    }
                    if (art == TafelArt.Verwandt) {
                        // Stammpaare aus Generation 1 (Eltern), 2 (Grosseltern) ...; die Namen helfen beim Waehlen
                        Einstellung(stringResource(Res.string.desk_chart_roots_generation), stringResource(Res.string.tipp_roots)) {
                            val namen = listOf(Res.string.desk_chart_gen_parents, Res.string.desk_chart_gen_grandparents, Res.string.desk_chart_gen_great).map { stringResource(it) }
                            val werte = (minGen(art)..maxGen(art)).map { g -> namen.getOrNull(g - 1) ?: stringResource(Res.string.desk_chart_gen_nth, g - 2) }
                            Auswahl(werte[o.generationen - minGen(art)], werte) { w -> o = o.copy(generationen = werte.indexOf(w) + minGen(art)) }
                        }
                        Einstellung(stringResource(Res.string.desk_chart_below_root), stringResource(Res.string.tipp_below_root)) {
                            Auswahl(o.nachfahren.toString(), (0..3).map { it.toString() }) { o = o.copy(nachfahren = it.toInt()) }
                        }
                        daten?.getOrNull()?.stammpaare?.takeIf { it.isNotEmpty() }?.let { paare ->
                            Text(stringResource(Res.string.desk_chart_roots), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            paare.forEach { sp ->
                                val name = sp.wurzel.name + (sp.partner?.let { " ⚭ ${it.name}" } ?: "")
                                Haken(name, sp.wurzel.xref !in ohneStamm) { an -> ohneStamm = if (an) ohneStamm - sp.wurzel.xref else ohneStamm + sp.wurzel.xref }
                            }
                        }
                    } else Einstellung(stringResource(if (art == TafelArt.Sanduhr || art == TafelArt.Paar) Res.string.desk_chart_generations_anc else Res.string.desk_chart_generations)) {
                        Auswahl(o.generationen.toString(), (minGen(art)..maxGen(art)).map { it.toString() }) { o = o.copy(generationen = it.toInt()) }
                    }
                    if (art == TafelArt.StammSeiten) {
                        Einstellung(stringResource(Res.string.desk_chart_per_page), stringResource(Res.string.tipp_per_page)) {
                            Auswahl(o.jeSeite.toString(), (2..5).map { it.toString() }) { o = o.copy(jeSeite = it.toInt()) }
                        }
                        Haken(stringResource(Res.string.desk_chart_overview), o.uebersicht, stringResource(Res.string.tipp_overview)) { o = o.copy(uebersicht = it) }
                        Haken(stringResource(Res.string.desk_chart_index), o.verzeichnis, stringResource(Res.string.tipp_index)) { o = o.copy(verzeichnis = it) }
                    }
                    if (art == TafelArt.Sanduhr || art == TafelArt.Paar) Einstellung(stringResource(Res.string.desk_chart_generations_desc)) {
                        Auswahl(o.nachfahren.toString(), (1..9).map { it.toString() }) { o = o.copy(nachfahren = it.toInt()) }
                    }
                    Gruppe("inhalt", stringResource(Res.string.desk_group_content), true) {
                        if (art in kreise) Haken(stringResource(Res.string.desk_chart_numbers), o.nummern, stringResource(Res.string.tipp_kekule)) { o = o.copy(nummern = it) }
                        else if (art != TafelArt.Zeitleiste) Einstellung(stringResource(Res.string.desk_chart_numbering), stringResource(Res.string.tipp_numbering)) {
                            // Stammtafeln haben keine Kekule-Nummern: dort "keine" statt "Kekule"
                            val namen = listOf(Res.string.desk_chart_num_none, Res.string.desk_chart_num_kekule, Res.string.desk_chart_num_chronik, Res.string.desk_chart_num_aboville).map { stringResource(it) }
                            val jetzt = if (!o.nummern || (o.nummernArt == 0 && art in setOf(TafelArt.Stamm, TafelArt.StammSeiten))) 0 else o.nummernArt + 1
                            Auswahl(namen[jetzt], namen) { w -> val i = namen.indexOf(w); o = if (i == 0) o.copy(nummern = false) else o.copy(nummern = true, nummernArt = i - 1) }
                        }
                        if (art == TafelArt.Ahnen) Haken(stringResource(if (o.waagerecht) Res.string.desk_chart_root_right else Res.string.desk_chart_root_top), o.ausgangOben, stringResource(Res.string.tipp_root_top)) { o = o.copy(ausgangOben = it) }
                        if (art == TafelArt.Ahnen) Einstellung(stringResource(Res.string.desk_chart_siblings), stringResource(Res.string.tipp_siblings)) {
                            val werte = listOf(Res.string.desk_chart_siblings_none, Res.string.desk_chart_siblings_root, Res.string.desk_chart_siblings_all).map { stringResource(it) }
                            Auswahl(werte[o.geschwister], werte) { w -> o = o.copy(geschwister = werte.indexOf(w)) }
                        }
                        if (art == TafelArt.Stamm || art == TafelArt.StammSeiten || art == TafelArt.Sanduhr) Haken(stringResource(Res.string.desk_chart_name_bearers), o.namenstraeger, stringResource(Res.string.tipp_name_bearers)) { o = o.copy(namenstraeger = it) }
                        if (art == TafelArt.Stamm || art == TafelArt.StammSeiten || art == TafelArt.Sanduhr || art == TafelArt.Cousins || art == TafelArt.Paar || art == TafelArt.Verwandt) Haken(stringResource(Res.string.desk_chart_spouses), o.partner) { o = o.copy(partner = it) }
                        if (art in linien) Haken(stringResource(Res.string.desk_chart_both_parents), o.partner, stringResource(Res.string.tipp_both_parents)) { o = o.copy(partner = it) }
                        Haken(stringResource(Res.string.desk_chart_living_names), o.lebendeNurNamen, stringResource(Res.string.tipp_living_names)) { o = o.copy(lebendeNurNamen = it) }
                        if (art !in ohneKaesten) {
                            if (art in setOf(TafelArt.Stamm, TafelArt.StammSeiten, TafelArt.Sanduhr, TafelArt.Paar, TafelArt.Verwandt)) {
                                Haken(stringResource(Res.string.desk_chart_only_married), o.nurMitPartner, stringResource(Res.string.tipp_only_married)) { o = o.copy(nurMitPartner = it) }
                                Einstellung(stringResource(Res.string.desk_chart_early_dead), stringResource(Res.string.tipp_early_dead)) {
                                    val werte = listOf(0, 1, 5, 15)
                                    val namen = werte.map { if (it == 0) stringResource(Res.string.desk_chart_early_show) else stringResource(Res.string.desk_chart_early_under, it) }
                                    Auswahl(namen[werte.indexOf(o.mindestalter).coerceAtLeast(0)], namen) { w -> o = o.copy(mindestalter = werte[namen.indexOf(w)]) }
                                }
                            }
                            if (ausgeblendet.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.desk_chart_hidden, ausgeblendet.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                androidx.compose.material3.TextButton(onClick = { ausgeblendet = emptySet() }) { Text(stringResource(Res.string.desk_chart_hidden_clear)) }
                            }
                            if (art !in setOf(TafelArt.AhnenSeiten, TafelArt.StammSeiten, TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester))
                                Haken(stringResource(Res.string.desk_chart_more_hints), o.mehrHinweise, stringResource(Res.string.tipp_more_hints)) { o = o.copy(mehrHinweise = it) }
                        }
                        if (art in kartenArten) {
                            Haken(stringResource(Res.string.desk_chart_cards), o.karteikarten, stringResource(Res.string.tipp_cards)) { o = o.copy(karteikarten = it) }
                            if (mitKarten && details == null) Text(stringResource(Res.string.desk_chart_cards_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (art !in ohneKaesten) Gruppe("kasten", stringResource(Res.string.desk_group_box), false) {
                        // Kasteninhalt (D4)
                        Einstellung(stringResource(Res.string.desk_chart_date_form)) {
                            val namen = listOf(Res.string.desk_chart_date_year, Res.string.desk_chart_date_short, Res.string.desk_chart_date_long).map { stringResource(it) }
                            val jetzt = if (o.datumsArt != 0) o.datumsArt else if (o.volleDaten) 2 else 0
                            Auswahl(namen[jetzt], namen) { w -> o = o.copy(datumsArt = namen.indexOf(w), volleDaten = false) }
                        }
                        Haken(stringResource(Res.string.desk_chart_places), o.orte) { o = o.copy(orte = it) }
                        if (o.orte) Einstellung(stringResource(Res.string.desk_chart_place_parts)) {
                            val namen = listOf(Res.string.desk_chart_place_full, Res.string.desk_chart_place_one, Res.string.desk_chart_place_two).map { stringResource(it) }
                            Auswahl(namen[o.ortTeile.coerceIn(0, 2)], namen) { w -> o = o.copy(ortTeile = namen.indexOf(w)) }
                        }
                        Haken(stringResource(Res.string.desk_chart_substitute), o.ersatz, stringResource(Res.string.tipp_substitute)) { o = o.copy(ersatz = it) }
                        Haken(stringResource(Res.string.desk_chart_age), o.alter) { o = o.copy(alter = it) }
                        Haken(stringResource(Res.string.desk_chart_occupation), o.beruf) { o = o.copy(beruf = it) }
                        Haken(stringResource(Res.string.desk_chart_call_name), o.rufname, stringResource(Res.string.tipp_call_name)) { o = o.copy(rufname = it) }
                    }
                    Gruppe("gestaltung", stringResource(Res.string.desk_group_design), true) {
                        Einstellung(stringResource(Res.string.desk_chart_style)) {
                            val namen = TafelStil.entries.associateWith { stringResource(stilNamen.getValue(it)) }
                            Auswahl(namen.getValue(o.stil), namen.values.toList()) { w -> o = o.copy(stil = namen.entries.first { it.value == w }.key) }
                        }
                        Einstellung(stringResource(Res.string.desk_chart_box_width)) {
                            Auswahl("${o.rahmenMm} mm", (20..60 step 5).map { "$it mm" }) { o = o.copy(rahmenMm = it.substringBefore(' ').toInt()) }
                        }
                        if (art in waagerechtMoeglich) Haken(stringResource(Res.string.desk_chart_horizontal), o.waagerecht, stringResource(Res.string.tipp_horizontal)) { o = o.copy(waagerecht = it) }
                        Haken(stringResource(Res.string.desk_chart_photos), o.bilder) { o = o.copy(bilder = it) }
                        if (art !in ohneKaesten) {
                            Einstellung(stringResource(Res.string.desk_chart_box_form)) {
                                val namen = listOf(Res.string.desk_chart_form_style, Res.string.desk_chart_form_square, Res.string.desk_chart_form_round,
                                    Res.string.desk_chart_form_oval, Res.string.desk_chart_form_shield).map { stringResource(it) }
                                Auswahl(namen[o.form.ordinal], namen) { w -> o = o.copy(form = KastenForm.entries[namen.indexOf(w)]) }
                            }
                            Haken(stringResource(Res.string.desk_chart_shadow), o.schatten) { o = o.copy(schatten = it) }
                            Haken(stringResource(Res.string.desk_chart_curved), o.geschwungen) { o = o.copy(geschwungen = it) }
                            if (art in buendigArten) Einstellung(stringResource(Res.string.desk_chart_align)) {
                                val namen = listOf(Res.string.desk_chart_align_center, Res.string.desk_chart_align_left, Res.string.desk_chart_align_right).map { stringResource(it) }
                                Auswahl(namen[o.buendig], namen) { w -> o = o.copy(buendig = namen.indexOf(w)) }
                            }
                        }
                        Einstellung(stringResource(Res.string.desk_chart_background)) {
                            val namen = listOf(Res.string.desk_chart_form_style, Res.string.desk_chart_bg_white, Res.string.desk_chart_bg_parchment,
                                Res.string.desk_chart_bg_paper, Res.string.desk_chart_bg_gradient, Res.string.desk_chart_bg_image).map { stringResource(it) }
                            Auswahl(namen[o.hintergrund.ordinal], namen) { w ->
                                val hg = TafelHintergrund.entries[namen.indexOf(w)]
                                // Eigenes Bild: gleich die Datei waehlen lassen
                                val datei = if (hg == TafelHintergrund.Bild) bildWaehlen() ?: o.hintergrundBild else o.hintergrundBild
                                o = if (hg == TafelHintergrund.Bild && datei.isBlank()) o else o.copy(hintergrund = hg, hintergrundBild = datei)
                            }
                        }
                        if (o.hintergrund == TafelHintergrund.Bild) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(java.io.File(o.hintergrundBild).name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            androidx.compose.material3.TextButton(onClick = { bildWaehlen()?.let { o = o.copy(hintergrundBild = it) } }) { Text(stringResource(Res.string.desk_chart_bg_choose)) }
                        }
                        Einstellung(stringResource(Res.string.desk_chart_frame)) {
                            val namen = listOf(Res.string.desk_chart_frame_none, Res.string.desk_chart_frame_double, Res.string.desk_chart_frame_corners,
                                Res.string.desk_chart_frame_vines).map { stringResource(it) }
                            Auswahl(namen[o.schmuck.ordinal], namen) { w -> o = o.copy(schmuck = Schmuckrahmen.entries[namen.indexOf(w)]) }
                        }
                        if (!o.waagerecht && o.bilder && art !in ohneKaesten) Haken(stringResource(Res.string.desk_chart_photo_left), o.fotoLinks) { o = o.copy(fotoLinks = it) }
                    }
                    if (art in mitGitterArten) Gruppe("gross", stringResource(Res.string.desk_group_large), false) {
                        if (art in mitGitterArten) {
                            Haken(stringResource(Res.string.desk_chart_grid), o.gitter, stringResource(Res.string.tipp_grid)) { o = o.copy(gitter = it) }
                            Haken(stringResource(Res.string.desk_chart_index), o.verzeichnis, stringResource(Res.string.tipp_index)) { o = o.copy(verzeichnis = it) }
                            Haken(stringResource(Res.string.desk_chart_curves), o.kurven, stringResource(Res.string.tipp_curves)) { o = o.copy(kurven = it) }
                        }
                    }
                    if (art in farbArten) Gruppe("farben", stringResource(Res.string.desk_group_colors), false) {
                        if (art in farbArten) FarbEinstellungen(
                            o, art in linienArten, art in zweigArten, { o = o.copy(farbe = it) },
                            regeln, { regeln = it }, zweige.size, { zweige = emptyMap() },
                        )
                    }
                    Gruppe("titel", stringResource(Res.string.desk_group_title), false) {
                        OutlinedTextField(o.titel, { o = o.copy(titel = it) }, label = { Text(stringResource(Res.string.desk_chart_heading)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        run {
                            OutlinedTextField(o.untertitel, { o = o.copy(untertitel = it) }, label = { Text(stringResource(Res.string.desk_chart_subtitle)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(o.ersteller, { o = o.copy(ersteller = it) }, label = { Text(stringResource(Res.string.desk_chart_author)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Haken(stringResource(Res.string.desk_chart_legend), o.legende, stringResource(Res.string.tipp_legend)) { o = o.copy(legende = it) }
                        }
                    }
                    val info = vorschau?.getOrNull()?.info
                    info?.let {
                        Text(if (it.seiten > 0) stringResource(Res.string.desk_chart_size_pages, it.personen, it.seiten) else stringResource(Res.string.desk_chart_size, it.personen, it.breiteCm, it.hoeheCm),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(Res.string.desk_chart_zoom_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val bereit = info != null && (!mitKarten || details != null)
                    val titel = o.titel.ifBlank { titelVorgabe }
                    // Beim Drucken bleibt das Blatt offen: der Druck laeuft im Hintergrund und greift auf seine Inhalte zu.
                    if (art == TafelArt.AhnenSeiten || art == TafelArt.StammSeiten) {
                        Knopf(stringResource(Res.string.desk_chart_print_pages), bereit) { erzeugen(true)?.first?.let { drucken(it, titel) } }
                        Knopf(stringResource(Res.string.desk_chart_pdf_pages), bereit) { erzeugen(true)?.first?.let { alsPdf(it, titel) } }
                    } else {
                        Knopf(stringResource(Res.string.desk_chart_print_one), bereit) { erzeugen(true)?.first?.let { drucken(aufEinBlatt(it), titel) } }
                        Knopf(stringResource(Res.string.desk_chart_pdf_poster), bereit) { erzeugen(true)?.first?.let { alsPdf(it, titel) } }
                        Knopf(stringResource(Res.string.desk_chart_png), bereit) { erzeugen()?.first?.let { alsPng(it, titel) } }
                    }
                    if (art != TafelArt.AhnenSeiten && art != TafelArt.StammSeiten) Gruppe("grossdruck", stringResource(Res.string.desk_group_bigprint), false) {
                        GrossdruckWahl(druck, { druck = it }, info, bereit,
                            onBlaetterDrucken = { erzeugen(true)?.let { (p, i) -> p.use { drucken(aufBlaetter(it, druck.groesse(), i.bereich), titel) } } },
                            onBlaetterPdf = { erzeugen(true)?.let { (p, i) -> p.use { alsPdf(aufBlaetter(it, druck.groesse(), i.bereich), "$titel A4") } } },
                            onPlotter = {
                                val rolle = druck.rolleCm() ?: return@GrossdruckWahl
                                erzeugen()?.let { (p, i) -> p.use {
                                    val s = it.getPage(0); val ber = druckBereich(s, i.bereich)
                                    alsPdf(aufRolle(it, rollenPlan(ber.b, ber.h, s.userUnit, rolle, druck.groesse(), druck.einpassen), i.bereich), "$titel Plotter")
                                } }
                            })
                    }
                    Gruppe("vorlagen", stringResource(Res.string.desk_group_templates), false) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.desk_chart_template_save), true, stringResource(Res.string.tipp_template_save)) {
                                vorlageDatei(true, "$titel.wtvorlage")?.writeText(TafelWahl.vorlageText(art, o))
                            } }
                            Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.desk_chart_template_load), true, stringResource(Res.string.tipp_template_load)) {
                                vorlageDatei(false, "")?.let { f -> runCatching { f.readText() }.getOrNull() }?.let(TafelWahl::vorlageLesen)?.let { (neueArt, neu) ->
                                    // Titel und Zeile unter dem Titel gehoeren zur Person, nicht zur Vorlage
                                    TafelWahl.sichern(neueArt, neu)
                                    if (neueArt == art) o = neu.copy(titel = o.titel, untertitel = o.untertitel) else art = neueArt
                                }
                            } }
                        }
                    }
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
                Trenner(einstBreite, "tafel_einstellungen", 240f, 600f)
                // Vorschau
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF8C8F8E)).padding(16.dp), contentAlignment = Alignment.Center) {
                    val dichte = LocalDensity.current
                    val px = with(dichte) { maxWidth.roundToPx() to maxHeight.roundToPx() }
                    LaunchedEffect(px) { vorschauPx = px }
                    val fehler = daten?.exceptionOrNull() ?: vorschau?.exceptionOrNull()
                    val vb = vorschau?.getOrNull()
                    val bild = vb?.bild
                    val info = vb?.info
                    // Rechtsklick auf einen Kasten: Punkt auf der Seite (in ihren Einheiten) -> Karte darunter
                    var menue by remember { mutableStateOf<Pair<KartenOrt, androidx.compose.ui.unit.DpOffset>?>(null) }
                    fun klick(seite: androidx.compose.ui.geometry.Offset, anker: androidx.compose.ui.geometry.Offset) {
                        if (info == null || info.karten.isEmpty() || art !in farbArten) return
                        val karte = info.karten.firstOrNull { it.enthaelt(seite.x, seite.y) && it.person.xref.isNotEmpty() && !it.person.isPrivate } ?: return
                        menue = karte to with(dichte) { androidx.compose.ui.unit.DpOffset(anker.x.toDp(), anker.y.toDp()) }
                    }
                    Box(Modifier.align(Alignment.TopStart)) {
                        ZweigMenue(menue?.first, menue?.second ?: androidx.compose.ui.unit.DpOffset.Zero, menue?.first?.person?.xref in zweige,
                            onWahl = { f -> menue?.first?.person?.xref?.let { x -> zweige = if (f == null) zweige - x else zweige + (x to f) }; menue = null },
                            onZu = { menue = null },
                            onAusblenden = { menue?.first?.person?.xref?.let { x -> ausgeblendet = ausgeblendet + x }; menue = null })
                    }
                    when {
                        fehler != null -> Text(fehler.message ?: "?", color = androidx.compose.ui.graphics.Color.White)
                        vorschau != null && bild == null -> Text(stringResource(Res.string.desk_chart_empty), color = androidx.compose.ui.graphics.Color.White)
                        bild == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                            Text(stringResource(Res.string.desk_chart_loading_any), Modifier.padding(top = 8.dp), color = androidx.compose.ui.graphics.Color.White)
                        }
                        else -> ZoomVorschau(vb!!, zoom, versatz, { z, v -> zoom = z; versatz = v }, ::klick)
                    }
                }
            }
        }
    }
}

/** Aufklappbare Gruppe im Einstellungsfenster; ob offen, merkt sich das Programm je [name]. */
@Composable
internal fun Gruppe(name: String, titel: String, vorgabe: Boolean, tipp: String? = null, inhalt: @Composable () -> Unit) {
    var offen by remember { mutableStateOf(DeskLayout.prefs.getBoolean("tafel_gruppe_$name", vorgabe)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tipp(tipp) {
            Row(Modifier.fillMaxWidth().clickable { offen = !offen; DeskLayout.prefs.putBoolean("tafel_gruppe_$name", offen) }.padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (offen) "▾" else "▸", Modifier.width(18.dp), style = MaterialTheme.typography.titleSmall)
                Text(titel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        if (offen) inhalt()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun Haken(text: String, wert: Boolean, tipp: String? = null, onWechsel: (Boolean) -> Unit) {
    Tipp(tipp) {
        Row(Modifier.clickable { onWechsel(!wert) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = wert, onCheckedChange = onWechsel, modifier = Modifier.size(32.dp))
            Text(text, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun ArtGruppe(text: String) {
    Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ArtEintrag(text: String, aktiv: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .fokusRahmen().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium, fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal)
}

@Composable
internal fun Einstellung(label: String, tipp: String? = null, inhalt: @Composable () -> Unit) {
    Tipp(tipp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f)) { inhalt() }
        }
    }
}

@Composable
internal fun Auswahl(wert: String, werte: List<String>, onWahl: (String) -> Unit) {
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
internal fun Knopf(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

/** Knopf mit Erklaerung beim Ueberfahren; eigene Ueberladung, weil viele Aufrufe onClick als dritten Wert uebergeben. */
@Composable
internal fun Knopf(text: String, enabled: Boolean, tipp: String, onClick: () -> Unit) {
    Tipp(tipp) { Knopf(text, enabled, onClick) }
}
