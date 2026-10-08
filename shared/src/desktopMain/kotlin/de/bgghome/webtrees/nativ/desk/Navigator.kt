package de.bgghome.webtrees.nativ.desk

import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.math.pow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import de.bgghome.webtrees.nativ.api.halfSiblings
import de.bgghome.webtrees.nativ.data.hauptHeirat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.*
import org.jetbrains.compose.resources.stringResource

/*
 * Aufbau "Navigator": links die Kinder als Spalte mit
 * Klammer, in der Mitte die Zentralperson mit den Partnern darunter, rechts die Vorfahren ueber die volle Hoehe -
 * Generation fuer Generation eine Spalte, unbekannte Eltern als leere Kaesten, Pfeile wo es weitergeht. Oben links
 * der Infokasten mit grossem Portraet und allen Ehen. Klick auf die Zentralperson oeffnet den Eingabedialog,
 * Klick auf jede andere Person macht sie zur Zentralperson; Doppelklick oeffnet, rechte Taste zeigt das Menue.
 */

private val BOX_W = 330.dp
private val BOX_H = 58.dp
private val GAP = 10.dp
/** Spaltenabstand kleiner als die Kastenbreite: die Spalten ueberlappen, die Linie zu den Eltern
 *  laeuft hinter dem Kasten des Kindes bei zwei Dritteln seiner Breite. */
private val COL = 245.dp
private val LINE_X = 222.dp
private val INFO_H = 236.dp
/** Breite des Infokastens - schmal genug, dass die Grosseltern-Spalte nicht unter ihn ausweichen muss. */
private val INFO_W = 620.dp
/** Zoom gegenueber dem Einpassen: bis 300 Prozent, damit auch die kleinen aeusseren Generationen lesbar werden. */
private const val ZOOM_MIN = 0.6f
private const val ZOOM_MAX = 3f
/** Dichte ausserhalb der eingepassten Tafel - der Infokasten wird damit gezeichnet und zoomt nicht mit. */
private val LocalBasisDichte = staticCompositionLocalOf<Density?> { null }
private val ICON_ROW = 32.dp

/** Farbkodierung nach Mary Hill: xref -> Farbe des Streifens am rechten Kastenrand (leer = aus). */
val LocalFarben = staticCompositionLocalOf { emptyMap<String, Color>() }

/**
 * Handschrift des Navigators, im Regler ueber der Tafel umschaltbar: [kante] helle Kaesten, Geschlecht (oder
 * Linienfarbe) als farbige Kante links statt Vollflaeche; [medaillon] rundes Portraet mit Luft; [weich] gebogene
 * Verbindungslinien zu den Eltern. Bleibt in den Desktop-Einstellungen.
 */
data class NavStil(val kante: Boolean = false, val medaillon: Boolean = false, val weich: Boolean = false) {
    fun speichern() {
        DeskLayout.prefs.putBoolean("nav_kante", kante); DeskLayout.prefs.putBoolean("nav_medaillon", medaillon); DeskLayout.prefs.putBoolean("nav_weich", weich)
    }
    companion object {
        fun laden() = NavStil(DeskLayout.prefs.getBoolean("nav_kante", false), DeskLayout.prefs.getBoolean("nav_medaillon", false), DeskLayout.prefs.getBoolean("nav_weich", false))
    }
}
val LocalNavStil = staticCompositionLocalOf { NavStil() }

/** Farben des Systems nach Mary Hill (allgemeiner Genealogie-Standard). */
object MaryHill {
    val start = Color(0xFF1F3A6B); val vaterVater = Color(0xFF3B6FB6); val vaterMutter = Color(0xFF3E9B4F)
    val mutterVater = Color(0xFFC8453B); val mutterMutter = Color(0xFFE0B82E); val nachkommen = Color(0xFFE48FB0)

    fun fuer(n: Int): Color {
        if (n == 1) return start
        val g = 31 - Integer.numberOfLeadingZeros(n)
        if (g == 1) return if (n == 2) vaterVater else mutterVater
        return when (n shr (g - 2)) { 4 -> vaterVater; 5 -> vaterMutter; 6 -> mutterVater; else -> mutterMutter }
    }
}

private data class BoxColors(val fill: Color, val border: Color)

@Composable
private fun boxColors(sex: String): BoxColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (sex) {
        "M" -> if (dark) BoxColors(Color(0xFF244B6B), Color(0xFF7FB2DA)) else BoxColors(Color(0xFFA9C3E2), Color(0xFF41709F))
        "F" -> if (dark) BoxColors(Color(0xFF5E2F36), Color(0xFFE09AA2)) else BoxColors(Color(0xFFF1B5AD), Color(0xFFB45F58))
        else -> if (dark) BoxColors(Color(0xFF3A4242), Color(0xFF8E9998)) else BoxColors(Color(0xFFDDE2E2), Color(0xFF8A9493))
    }
}

/** "Nachname, Vorname" wie in einem Register - mit Namenszusatz ("de' Medici, Cosimo I"), wenn der Server ihn liefert. */
internal fun registerName(person: Person, privateLabel: String, noName: String): String = when {
    person.isPrivate -> privateLabel
    person.surname.isNotBlank() || person.given.isNotBlank() -> listOf(person.surname, person.given).filter(String::isNotBlank).joinToString(", ")
    person.sortName.isNotBlank() -> person.sortName.replace(Regex(",(?=\\S)"), ", ")
    else -> person.name.ifBlank { noName }
}

private fun gen(n: Int) = 31 - Integer.numberOfLeadingZeros(n)

@Composable
fun Navigator(
    state: UiState, viewModel: AppViewModel, onOpenSheet: (String) -> Unit, openWeb: (String) -> Unit,
    farben: Map<String, Color> = emptyMap(), zoom: Float = 1f, onZoom: (Float) -> Unit = {},
) {
    LaunchedEffect(state.root, state.pedigree == null || state.descendants == null) {
        if (state.root != null && (state.pedigree == null || state.descendants == null)) viewModel.loadChart()
    }
    // Der Eingabedialog kann eine andere Person zeigen - die Zentralperson bleibt hier stehen.
    var rootDetail by remember { mutableStateOf<IndividualDetail?>(null) }
    LaunchedEffect(state.detail) { state.detail?.takeIf { it.person.xref == state.root }?.let { rootDetail = it } }
    val detail = rootDetail?.takeIf { it.person.xref == state.root }
    val ahnen = state.pedigree?.ancestors?.associateBy { it.n }.orEmpty()
    val zentral = ahnen[1]?.person ?: detail?.person ?: return
    val canEdit = state.tree?.canEdit == true
    // Kinder, die selbst Kinder haben (aus dem Nachkommenbaum) - bekommen einen Pfeil nach links.
    val mitNachkommen = state.descendants?.tree?.families?.flatMap { it.children }?.filter { k -> k.families.any { it.children.isNotEmpty() } }?.map { it.person.xref }?.toSet().orEmpty()

    // Eine Partnerschaft auf einmal: Partner und deren Kinder; der Pfeil unter dem Partner wechselt.
    val familien = detail?.spouseFamilies.orEmpty()
    var gewaehlt by remember(state.root) { mutableStateOf(-1) }
    val fIndex = if (gewaehlt in familien.indices) gewaehlt else familien.indexOfFirst { it.spouse != null }.coerceAtLeast(0)
    val familie = familien.getOrNull(fIndex)
    val quer = rememberScrollState(); val hoch = rememberScrollState()
    val basis = LocalDensity.current
    val g = state.ancestorGenerations.coerceIn(2, 7)
    var stil by remember { mutableStateOf(NavStil.laden()) }
    // Tastatur: rechts Vater (mit Umschalt Mutter), links das erste Kind, hoch/runter die Geschwister, Eingabe oeffnet das
    // Blatt, Tab wechselt die Partnerschaft. Alt+Pfeile bleiben Zurueck/Vor.
    fun gehe(r: Richtung): Boolean { detail?.let { zielPerson(it, familie, r) }?.let { viewModel.setRoot(it) }; return true }
    val tasten = Modifier.tastenBereich(state.root) { e ->
        if (e.type != KeyEventType.KeyDown || e.isAltPressed || e.isCtrlPressed) false else when (e.key) {
            Key.DirectionRight -> gehe(if (e.isShiftPressed) Richtung.Mutter else Richtung.Vater)
            Key.DirectionLeft -> gehe(Richtung.Kind)
            Key.DirectionUp -> gehe(Richtung.GeschwisterZurueck)
            Key.DirectionDown -> gehe(Richtung.GeschwisterVor)
            Key.Enter -> { onOpenSheet(zentral.xref); true }
            Key.Tab -> { if (familien.size > 1) gewaehlt = (fIndex + 1) % familien.size; familien.size > 1 }
            else -> false
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(tasten)) {
    // Generationen, Stil und Zoom ueber der Tafel, rechts - im Navigator statt in der Symbolleiste
    // (dort fehlte bei 125 % Skalierung der Platz).
    TafelRegler(state.ancestorGenerations, viewModel::setAncestorGenerations, zoom, onZoom, stil) { stil = it; it.speichern() }
    // Einpassen: die Tafel fuellt das Fenster, der Zoom vergroessert oder verkleinert davon ausgehend.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val rand = 32.dp // Polster der Rollflaeche (2 x 12) plus etwas Luft gegen Rundung
        // Massgeblich sind Vorfahren und Infokasten; eine lange Kinderspalte rollt, statt alles zu verkleinern.
        // Bei wenigen Generationen darf die Tafel wachsen (bis 150 Prozent), bei vielen bis 45 Prozent schrumpfen - so passen
        // 5 Generationen auf einen Laptop-Bildschirm (60 Prozent reichten nicht, Einpassen tat dann nichts); darunter rollt die Tafel.
        fun passt(e: ChartMasse) = minOf((maxWidth - rand) / e.breite, (maxHeight - rand) / e.hoeheFit)
        // Zickzack, wenn die Hoehe knapp ist (viele Generationen): die aeusserste Generation in zwei versetzten Spalten
        // nutzt die freie Breite, die Tafel wird nur halb so hoch und kann groesser gezeichnet werden.
        // Abstufen nur so weit wie noetig: erst alle Kaesten voll gross; erst wenn die Tafel dann nicht mehr in voller
        // Groesse ins Fenster passt, werden die aeusseren Generationen schrittweise kleiner (bei 5 Generationen meist gar
        // nicht, bei 6 und 7 bis zur vollen Abstufung). Zickzack erst bei 7 Generationen und nur, wenn er spuerbar hilft.
        class Wahl(val t: Float, val zick: Boolean, val eng: ChartMasse, val eng2: ChartMasse, val fit: Float)
        fun waehle(t: Float): Wahl {
            val normal = chartMasse(g, familie, familien.isNotEmpty(), slotKlein(g, false, t), t = t)
            val zickzack = chartMasse(g, familie, familien.isNotEmpty(), slotKlein(g, true, t), zick = true, t = t)
            val z = g >= 7 && passt(zickzack) > passt(normal) * 1.15f
            val e = if (z) zickzack else normal
            val f0 = passt(e).coerceIn(0.45f, 1.5f)
            // Zweiter Schritt: der Infokasten behaelt seine Groesse, die Tafel muss ihm bei dieser Skala Platz lassen
            val e2 = chartMasse(g, familie, familien.isNotEmpty(), slotKlein(g, z, t), info = 1f / (f0 * zoom), zick = z, t = t)
            return Wahl(t, z, e, e2, passt(e2).coerceIn(0.45f, 1.5f))
        }
        // "Passt" heisst: mindestens 90 Prozent der vollen Groesse - lieber minimal kleiner als abgestuft
        val wahl = listOf(0f, 0.25f, 0.5f, 0.75f).map(::waehle).firstOrNull { it.fit >= 0.9f } ?: waehle(1f)
        val t = wahl.t; val zick = wahl.zick; val eng = wahl.eng; val eng2 = wahl.eng2; val fit = wahl.fit
        val slotMin = slotKlein(g, zick, t)
        val slotMax = slotGross(g, zick, t)
        val skala = fit * zoom
        // Bleibt Hoehe uebrig, ruecken die Zeilen der aeussersten Generation auseinander, bis die Tafel das Fenster fuellt.
        val hoeheFrei = (maxHeight - rand) / skala - eng2.hoeheFit
        var slotH = if (hoeheFrei > 0.dp) minOf(slotMin + hoeheFrei / eng.slots, slotMax) else slotMin
        // Bleibt Breite uebrig, ruecken die ueberlappenden Spalten auseinander, bis die Tafel das Fenster ausfuellt
        // (welche ueberlappen duerfen, haengt vom Zeilenabstand ab - darum erst mit dem endgueltigen).
        val ohne = chartMasse(g, familie, familien.isNotEmpty(), slotH, info = 1f / skala, zick = zick, t = t)
        val frei = (maxWidth - rand) / skala - ohne.breite
        val extra = if (ohne.ueberlappend > 0 && frei > 0.dp) frei / ohne.ueberlappend else 0.dp
        var masse = chartMasse(g, familie, familien.isNotEmpty(), slotH, extra, info = 1f / skala, zick = zick, t = t)
        // Rutscht die Tafel wegen des Infokastens nach unten, die Zeilen wieder etwas enger ziehen.
        val zuviel = masse.hoeheFit - (maxHeight - rand) / skala
        if (zuviel > 0.dp && slotH > slotMin) {
            slotH = maxOf(slotMin, slotH - zuviel / eng.slots)
            masse = chartMasse(g, familie, familien.isNotEmpty(), slotH, extra, info = 1f / skala, zick = zick, t = t)
        }
        // Was dann noch an Breite frei ist, bekommen die Kaesten der aeussersten Generation (lange Namen passen)
        val rest = (maxWidth - rand) / skala - masse.breite
        val zusatz = if (rest > 0.dp) minOf(rest / (if (zick) 2 else 1), kastenW(g - 1, t)) else 0.dp
        if (zusatz > 0.dp) masse = chartMasse(g, familie, familien.isNotEmpty(), slotH, extra, info = 1f / skala, zick = zick, aussenZusatz = zusatz, t = t)
        // Begrenzt die Breite, bleibt unten Hoehe frei: dann werden die Kaesten hoeher und die Schrift waechst mit (Kaesten bis
        // zur Hoehe der Zentralperson, Schrift bis ein Viertel) - so fuellt die Tafel das Fenster wie eine Ahnentafel, statt unten leer zu bleiben.
        val raum = (maxHeight - rand) / skala
        if (!zick && masse.hoeheFit < raum * 0.97f) {
            var hoch = (raum / masse.hoeheFit * 0.98f).coerceAtMost(1.45f)
            repeat(3) {
                val probe = chartMasse(g, familie, familien.isNotEmpty(), slotH * hoch, extra, info = 1f / skala, zick = zick, aussenZusatz = zusatz, t = t, hoch = hoch)
                if (probe.hoeheFit <= raum || hoch <= 1.02f) { masse = probe; return@repeat }
                hoch = maxOf(1f, hoch * (raum / probe.hoeheFit) * 0.99f)
            }
        }
        CompositionLocalProvider(LocalFarben provides farben, LocalNavStil provides stil, LocalBasisDichte provides basis, LocalDensity provides Density(basis.density * skala, basis.fontScale)) {
            // Strg+Mausrad zoomt (wie in der Tafel-Vorschau), ohne Strg rollt das Rad wie gewohnt
            val aktZoom by rememberUpdatedState(zoom)
            Box(Modifier.fillMaxSize().onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
                val c = e.changes.firstOrNull() ?: return@onPointerEvent
                if (!e.keyboardModifiers.isCtrlPressed || c.scrollDelta.y == 0f) return@onPointerEvent
                onZoom((aktZoom * 1.1f.pow(-c.scrollDelta.y)).coerceIn(ZOOM_MIN, ZOOM_MAX))
                c.consume()
            }) {
                Box(Modifier.fillMaxSize().horizontalScroll(quer).verticalScroll(hoch).padding(12.dp)) {
                    Chart(detail, zentral, ahnen, g, canEdit, mitNachkommen, viewModel, onOpenSheet, openWeb, masse,
                        familie, fIndex, familien.size, onNaechste = { gewaehlt = (fIndex + 1) % familien.size })
                }
                SenkrechteLeiste(hoch)
                WaagerechteLeiste(quer)
            }
        }
    }
    }
}

/** Die Regler ueber der Tafel: Generationen (2 bis 7), Stil, Zoom (60 bis 300 Prozent, auch Strg+Mausrad; Klick auf die Zahl: 100) und Einpassen (= 100). */
@Composable
private fun TafelRegler(generationen: Int, onGenerationen: (Int) -> Unit, zoom: Float, onZoom: (Float) -> Unit, stil: NavStil, onStil: (NavStil) -> Unit) {
    Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        var open by remember { mutableStateOf(false) }
        Box {
            Row(Modifier.clickable { open = true }.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.tree_generations, generationen), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                (2..7).forEach { n ->
                    DropdownMenuItem(text = { Text(stringResource(Res.string.tree_generations, n), fontWeight = if (n == generationen) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { open = false; onGenerationen(n) })
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        var stilOffen by remember { mutableStateOf(false) }
        Box {
            Row(Modifier.clickable { stilOffen = true }.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.desk_nav_style), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = stilOffen, onDismissRequest = { stilOffen = false }) {
                StilEintrag(stringResource(Res.string.desk_nav_style_edge), stil.kante) { onStil(stil.copy(kante = it)) }
                StilEintrag(stringResource(Res.string.desk_nav_style_medallion), stil.medaillon) { onStil(stil.copy(medaillon = it)) }
                StilEintrag(stringResource(Res.string.desk_nav_style_soft), stil.weich) { onStil(stil.copy(weich = it)) }
            }
        }
        Spacer(Modifier.width(16.dp))
        TextKnopf("−", stringResource(Res.string.tree_zoom_out)) { onZoom((zoom - 0.1f).coerceAtLeast(ZOOM_MIN)) }
        Text("${(zoom * 100).roundToInt()} %", Modifier.clickable { onZoom(1f) }.padding(horizontal = 6.dp).width(44.dp), style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, softWrap = false)
        TextKnopf("+", stringResource(Res.string.tree_zoom_in)) { onZoom((zoom + 0.1f).coerceAtMost(ZOOM_MAX)) }
        Spacer(Modifier.width(10.dp))
        TextKnopf(stringResource(Res.string.desk_nav_fit), stringResource(Res.string.desk_nav_fit_hint), breit = true) { onZoom(1f) }
    }
}

/** Ein an- und abschaltbarer Menuepunkt mit Haken. */
@Composable
private fun StilEintrag(text: String, an: Boolean, onWechsel: (Boolean) -> Unit) {
    DropdownMenuItem(text = { Text((if (an) "✓  " else "\u2007\u2007\u2007 ") + text) }, onClick = { onWechsel(!an) })
}

@Composable
private fun TextKnopf(zeichen: String, beschreibung: String, breit: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(24.dp).let { if (breit) it.padding(horizontal = 0.dp) else it.width(24.dp) }
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall).clickable(onClickLabel = beschreibung, onClick = onClick)
            .let { if (breit) it.padding(horizontal = 8.dp) else it },
        contentAlignment = Alignment.Center,
    ) { Text(zeichen, style = if (breit) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false) }
}

/** Anstelle eines fehlenden Partners: "Mutter unbekannt" bei einem Mann, "Vater unbekannt" bei einer Frau. */
@Composable
internal fun unbekannterPartner(sex: String): String = stringResource(when (sex) {
    "M" -> Res.string.desk_unknown_mother
    "F" -> Res.string.desk_unknown_father
    else -> Res.string.desk_unknown_partner
})

/**
 * Lebensdaten fuer den Navigator: ein genau bekanntes Datum (Tag, Monat, Jahr) in kurzer Form der Sprache
 * ("08.02.1928"), sonst das Jahr; ohne beide die Lebensjahre wie bisher. Genau ist ein Datum, dessen Text aus einer
 * Tageszahl, einem Monatswort und dem Jahr besteht - "about 1770" oder "between 1850 and 1860" bleiben beim Jahr.
 */
internal fun lebensdaten(p: Person): String {
    fun kurz(d: de.bgghome.webtrees.nativ.api.DateJson?): String? {
        if (d == null || d.year <= 0) return null
        val zahlen = Regex("\\d+").findAll(d.text).count()
        val woerter = Regex("\\p{L}+").findAll(d.text).count()
        if (zahlen != 2 || woerter != 1 || d.jd <= 0) return d.year.toString()
        // Fest je Sprache (die Systemvorgabe liefert je nach Einstellung auch 1866-11-19)
        val muster = when (java.util.Locale.getDefault().language) {
            "de" -> "dd.MM.yyyy"; "nl" -> "dd-MM-yyyy"; "fr", "es" -> "dd/MM/yyyy"; else -> "d MMM yyyy"
        }
        return runCatching { java.time.LocalDate.ofEpochDay(d.jd - 2440588L).format(java.time.format.DateTimeFormatter.ofPattern(muster)) }
            .getOrDefault(d.year.toString())
    }
    val geb = kurz(p.birth?.date); val tod = kurz(p.death?.date)
    return when {
        geb == null && tod == null -> jahre(p)
        tod == null -> if (p.isDead) "$geb –" else geb.orEmpty()
        else -> "${geb.orEmpty()} – $tod"
    }
}

/** Lebensjahre ohne den Platzhalter von webtrees ("…–…" bei fehlenden Daten wird leer). */
internal fun jahre(p: Person): String = p.lifespan.takeIf { l -> l.any(Char::isDigit) }.orEmpty()

/** Name in natuerlicher Folge ohne die Platzhalter von webtrees ("Henry II …" wird "Henry II"). */
internal fun klarName(p: Person): String =
    listOf(p.given, p.surname).filter(String::isNotBlank).joinToString(" ").ifBlank { p.name.replace("…", "").replace("@N.N.", "").trim() }.ifBlank { p.name }

/** Die Masse der Tafel in dp vor dem Zeichnen - fuer das Einpassen. [xs] ist der linke Rand je Generation. */
private class ChartMasse(
    val slots: Int, val slotH: Dp, val ancH: Dp, val kinder: Int, val partner: Int, val xZentral: Dp, val centerY: Dp, val kinderTop: Dp,
    val breite: Dp, val hoehe: Dp, val hoeheFit: Dp, val xs: List<Dp>, val zick: Boolean, val mutterVersatz: Dp, val ueberlappend: Int,
    /** Breite der Kaesten der aeussersten Generation - sie nehmen die Restbreite des Fensters mit. */
    val aussenW: Dp,
    /** Staerke der Abstufung (siehe [stufe]). */
    val t: Float = 1f,
    /** Hoehenfaktor der Kaesten (siehe [grundH]). */
    val hoch: Float = 1f,
)

/**
 * Groessenstufe je Generation: Zentralperson und Eltern in voller Groesse, nach aussen kleiner - so bleiben die nahen
 * Generationen gut lesbar, und die vielen fernen brauchen weniger Hoehe. Kasten, Schrift und Bild schrumpfen gemeinsam.
 */
private val STUFE = floatArrayOf(1f, 1f, 0.9f, 0.8f, 0.72f, 0.66f, 0.6f)
/** [t]: wie stark abgestuft wird - 0 alle voll gross, 1 die volle Abstufung; gewaehlt wird so wenig wie noetig. */
private fun stufe(gen: Int, t: Float = 1f): Float = 1f - t * (1f - STUFE[minOf(gen, STUFE.lastIndex)])
/** Ab den Grosseltern flache Kaesten: Name und Jahre fuellen die Hoehe fast ganz, die Zeilen liegen dichter. */
private val BOX_H_FLACH = 40.dp
/** [hoch]: bleibt im Fenster Hoehe uebrig, werden die Kaesten hoeher (die Schrift waechst mit). */
// Alle Vorfahren gleich hoch (ruhiger, wie eine Ahnentafel); nur die Zentralperson behaelt ihren festen Kasten.
private fun grundH(gen: Int, hoch: Float = 1f): Dp = if (gen >= 1) BOX_H_FLACH * hoch else BOX_H
private fun kastenW(gen: Int, t: Float = 1f): Dp = BOX_W * stufe(gen, t)
private fun kastenH(gen: Int, t: Float = 1f, hoch: Float = 1f): Dp = grundH(gen, hoch) * stufe(gen, t)

/** Kleinster Zeilenabstand der aeussersten Generation; im Zickzack nur die halbe Kastenhoehe plus Luft fuer die Linie. */
private fun slotKlein(g: Int, zick: Boolean, t: Float = 1f, hoch: Float = 1f): Dp = if (zick) kastenH(g - 1, t, hoch) / 2 + 6.dp else kastenH(g - 1, t, hoch) + 3.dp
private fun slotGross(g: Int, zick: Boolean, t: Float = 1f): Dp = if (zick) kastenH(g - 1, t) + 6.dp else kastenH(g - 1, t) * 2 + 8.dp

/**
 * Die Masse der Tafel fuer [slotH] als Zeilenabstand der aeussersten Generation. Eine Spalte darf die vorige waagerecht
 * ueberlappen (Abstand drei Viertel der Kastenbreite plus [extra]), wenn jeder Kasten senkrecht zwischen seine beiden
 * Eltern passt; sonst steht sie ganz daneben. Im Zickzack ([zick]) stehen die Muetter der aeussersten Generation um
 * [ChartMasse.mutterVersatz] weiter rechts, dafuer duerfen sich die Zeilen dort zur Haelfte ueberlappen.
 */
private fun chartMasse(g: Int, familie: de.bgghome.webtrees.nativ.api.FamilyJson?, hatFamilien: Boolean, slotH: Dp, extra: Dp = 0.dp, info: Float = 1f, zick: Boolean = false, aussenZusatz: Dp = 0.dp, t: Float = 1f, hoch: Float = 1f): ChartMasse {
    // Der Infokasten zoomt nicht mit: in Tafel-Massen braucht er bei kleiner Skala entsprechend mehr Platz ([info] = 1 / Skala)
    val infoH = INFO_H * info
    val slots = 1 shl (g - 1)
    val ancH = slotH * slots
    val kinder = familie?.children?.size ?: 0
    val partner = if (hatFamilien) 1 else 0
    val xZentral = BOX_W + 56.dp
    val xs = mutableListOf(xZentral)
    var ueberlappend = 0
    for (k in 1 until g) {
        val wVor = kastenW(k - 1, t)
        val elternAbstand = slotH * (slots shr k)
        // Die Mutter der Zentralperson darf zudem nicht in den Partner (unter der Zentralperson) ragen
        val unterPartner = k != 1 || slotH * slots / 4 - kastenH(1, t, hoch) / 2 >= BOX_H * 1.5f + GAP + 24.dp
        val darf = !(zick && k == g - 1) && unterPartner && elternAbstand / 2 >= (kastenH(k, t, hoch) + kastenH(k - 1, t, hoch)) / 2 + 4.dp
        var schritt = if (darf) { ueberlappend++; minOf(wVor * (COL / BOX_W) + extra, wVor + 40.dp * stufe(k - 1, t)) } else wVor + 36.dp * stufe(k - 1, t)
        // Steht die Elternspalte ganz daneben, dann auch rechts vom Partner (der um 40 dp eingerueckt ist)
        if (k == 1 && !darf) schritt = maxOf(schritt, BOX_W + 40.dp + 16.dp)
        xs += xs.last() + schritt
    }
    val aussenW = kastenW(g - 1, t) + aussenZusatz
    val mutterVersatz = if (zick) aussenW + 36.dp * stufe(g - 1, t) else 0.dp
    // Die Zentralperson sitzt in der Mitte der Vorfahren; nur wenn ihr Kasten in den Infokasten ragt, rutscht alles nach unten.
    var centerY = maxOf(ancH / 2, infoH + ICON_ROW + BOX_H / 2)
    // Ebenso jede Vorfahrenspalte, die waagerecht noch in den Infokasten reicht (bei wenig Platz die Eltern):
    // ihr oberster Kasten beginnt erst unter dem Infokasten.
    val infoW = INFO_W * info
    for (k in 1 until g) {
        if (xs[k] >= infoW) continue
        val oben = (slotH * (slots shr k) - kastenH(k, t, hoch)) / 2
        centerY = maxOf(centerY, infoH + ancH / 2 - oben)
    }
    // Die Kinderspalte beginnt unter dem Infokasten und ist sonst um die Zentralperson zentriert.
    val kinderH = (BOX_H + GAP) * kinder
    val kinderTop = maxOf(centerY - kinderH / 2, infoH + ICON_ROW)
    val partnerH = (BOX_H + GAP) * partner
    val hoeheFit = maxOf(centerY + ancH / 2, centerY + BOX_H / 2 + partnerH + 30.dp) + 24.dp
    // Die Kinderspalte rollt in ihrem eigenen Bereich; die Tafel wird durch sie nicht hoeher.
    val hoehe = hoeheFit
    val breite = xs.last() + mutterVersatz + aussenW + 24.dp
    return ChartMasse(slots, slotH, ancH, kinder, partner, xZentral, centerY, kinderTop, breite, hoehe, hoeheFit, xs, zick, mutterVersatz, ueberlappend, aussenW, t, hoch)
}

/** Inhalt in der Groessenstufe [f] zeichnen: Kasten, Schrift und Bild schrumpfen gemeinsam. */
@Composable
private fun Gestuft(f: Float, modifier: Modifier, inhalt: @Composable () -> Unit) {
    val d = LocalDensity.current
    Box(modifier) { CompositionLocalProvider(LocalDensity provides Density(d.density * f, d.fontScale)) { inhalt() } }
}

@Composable
private fun Chart(
    detail: IndividualDetail?, zentral: Person, ahnen: Map<Int, de.bgghome.webtrees.nativ.api.Ancestor>, g: Int, canEdit: Boolean,
    mitNachkommen: Set<String>, viewModel: AppViewModel, onOpenSheet: (String) -> Unit, openWeb: (String) -> Unit, m: ChartMasse,
    familie: de.bgghome.webtrees.nativ.api.FamilyJson?, fIndex: Int, anzahlFamilien: Int, onNaechste: () -> Unit,
) {
    val slots = m.slots; val slotH = m.slotH; val ancH = m.ancH
    val kinder = familie?.children.orEmpty()
    val partner = listOfNotNull(familie?.spouse)
    val xZentral = m.xZentral; val centerY = m.centerY
    val hoehe = m.hoehe; val breite = m.breite
    val line = MaterialTheme.colorScheme.outline
    val weich = LocalNavStil.current.weich

    fun top(n: Int): Dp { val gg = gen(n); val span = slots shr gg; val i = n - (1 shl gg); return centerY - ancH / 2 + slotH * (i * span) + (slotH * span - kastenH(gg, m.t, m.hoch)) / 2 }
    // Im Zickzack stehen die Muetter der aeussersten Generation eine Spalte weiter rechts
    fun left(n: Int): Dp = m.xs[gen(n)] + if (m.zick && gen(n) == g - 1 && n % 2 == 1) m.mutterVersatz else 0.dp
    fun mitte(n: Int): Dp = top(n) + kastenH(gen(n), m.t, m.hoch) / 2
    // Kastenbreite in ungestuften dp (der Kasten wird in seiner Stufe gezeichnet); aussen mit der Restbreite
    fun breiteIn(n: Int): Dp = if (gen(n) == g - 1) m.aussenW / stufe(gen(n), m.t) else BOX_W
    val kinderTop = m.kinderTop
    val kinderBereich = hoehe - kinderTop - 12.dp

    Box(Modifier.size(breite, hoehe)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 1.2.dp.toPx()
            // Vorfahren: senkrechte Linie hinter dem Kasten des Kindes (bei zwei Dritteln), von Vater zu Mutter,
            // von dort waagerecht zu den Elternkaesten - auch zu leeren. Weich: je Elternteil ein Bogen, der hinter dem
            // Kasten des Kindes senkrecht startet und waagerecht in den Elternkasten muendet.
            for (n in 1 until (1 shl (g - 1))) {
                if (n !in ahnen) continue
                val xm = minOf(left(n) + LINE_X * stufe(gen(n), m.t), left(2 * n) - 12.dp).toPx()
                val yV = mitte(2 * n).toPx(); val yM = mitte(2 * n + 1).toPx()
                if (weich) {
                    val yK = mitte(n).toPx()
                    for ((xe, ye) in listOf(left(2 * n).toPx() to yV, left(2 * n + 1).toPx() to yM)) {
                        val pfad = Path().apply { moveTo(xm, yK); cubicTo(xm, ye, xm, ye, xe, ye) }
                        drawPath(pfad, line, style = Stroke(w * 1.25f, cap = StrokeCap.Round))
                    }
                } else {
                    drawLine(line, Offset(xm, yV), Offset(xm, yM), w)
                    drawLine(line, Offset(xm, yV), Offset(left(2 * n).toPx(), yV), w); drawLine(line, Offset(xm, yM), Offset(left(2 * n + 1).toPx(), yM), w)
                }
            }
            // Kinder: von der Klammer waagerecht zur Zentralperson (die Klammer selbst liegt im rollbaren Kinderbereich)
            if (kinder.isNotEmpty()) {
                val xb = (BOX_W + 16.dp).toPx(); val yc = centerY.toPx()
                drawLine(line, Offset(xb, yc), Offset(xZentral.toPx(), yc), w)
            }
            // Partner: senkrecht unter der Zentralperson
            if (familie != null) {
                val x = (xZentral + 40.dp).toPx()
                drawLine(line, Offset(x, (centerY + BOX_H / 2).toPx()), Offset(x, (centerY + BOX_H / 2 + GAP).toPx()), w * 2)
            }
        }

        // Infokasten oben links
        // ... in normaler Groesse, wie weit die Tafel auch eingepasst oder gezoomt ist
        if (detail != null) CompositionLocalProvider(LocalDensity provides (LocalBasisDichte.current ?: LocalDensity.current)) {
            InfoBox(detail, fIndex, Modifier.offset(0.dp, 0.dp).size(INFO_W, INFO_H - 12.dp), onOpen = { onOpenSheet(zentral.xref) },
                onPerson = { viewModel.setRoot(it) })
        }

        // Kinder in eigenem Rollbereich: viele Kinder rollen, statt die Tafel zu verkleinern
        if (kinder.isNotEmpty()) {
            val roll = rememberScrollState()
            val kslot = BOX_H + GAP
            val innen = kslot * kinder.size
            Box(Modifier.offset(0.dp, kinderTop).size(BOX_W + 32.dp, minOf(innen, kinderBereich))) {
                Box(Modifier.fillMaxSize().verticalScroll(roll)) {
                    Box(Modifier.size(BOX_W + 32.dp, innen)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = 1.2.dp.toPx(); val xb = (BOX_W + 16.dp).toPx()
                            val y0 = (BOX_H / 2).toPx(); val y1 = (kslot * (kinder.size - 1) + BOX_H / 2).toPx()
                            val yc = (centerY - kinderTop).toPx().coerceIn(y0, y1)
                            drawLine(line, Offset(xb, minOf(y0, yc)), Offset(xb, maxOf(y1, yc)), w)
                            kinder.indices.forEach { i -> val y = (kslot * i + BOX_H / 2).toPx(); drawLine(line, Offset(BOX_W.toPx(), y), Offset(xb, y), w) }
                        }
                        kinder.forEachIndexed { i, k ->
                            PersonBox(k, Art.Kind, viewModel, onOpenSheet, openWeb, Modifier.offset(0.dp, kslot * i), pfeilLinks = k.xref in mitNachkommen)
                        }
                    }
                }
                if (innen > kinderBereich) SenkrechteLeiste(roll)
            }
        }
        // Zentralperson mit Stift und Verwandte-hinzufuegen
        if (canEdit) {
            Row(Modifier.offset(xZentral, centerY - BOX_H / 2 - ICON_ROW + 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KleinKnopf(Icons.Default.Edit, stringResource(Res.string.desk_sheet)) { onOpenSheet(zentral.xref) }
                KleinKnopf(Icons.Default.Add, stringResource(Res.string.action_add_relative)) { viewModel.requestAddRelative(zentral.xref) }
            }
        }
        PersonBox(zentral, Art.Zentral, viewModel, onOpenSheet, openWeb, Modifier.offset(xZentral, centerY - BOX_H / 2))
        val partnerY = centerY + BOX_H / 2 + GAP
        if (familie != null) {
            val p = familie.spouse
            if (p != null) PersonBox(p, Art.Partner, viewModel, onOpenSheet, openWeb, Modifier.offset(xZentral + 40.dp, partnerY))
            else LeerBox(if (zentral.sex == "F") "M" else "F", Modifier.offset(xZentral + 40.dp, partnerY), onClick = if (canEdit) ({ viewModel.requestAddRelative(zentral.xref) }) else null, text = unbekannterPartner(zentral.sex))
            // Mehrere Partnerschaften: Pfeil wechselt zur naechsten, mit Zaehler
            if (anzahlFamilien > 1) {
                Row(Modifier.offset(xZentral + 40.dp, partnerY + BOX_H + 4.dp).clickable(onClick = onNaechste).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("➜", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("  ${fIndex + 1} / $anzahlFamilien", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Vorfahren, leere Kaesten fuer unbekannte Eltern bekannter Personen
        for (n in 2 until (1 shl g)) {
            val a = ahnen[n]
            if (a != null) {
                Gestuft(stufe(gen(n), m.t), Modifier.offset(left(n), top(n))) {
                    PersonBox(a.person, Art.Ahn, viewModel, onOpenSheet, openWeb, Modifier, pfeilRechts = gen(n) == g - 1 && a.hasParents,
                        breite = breiteIn(n), hoehe = grundH(gen(n), m.hoch), flach = true, schrift = minOf(m.hoch, 1.25f))
                }
            } else if ((n / 2) in ahnen) {
                val kind = ahnen.getValue(n / 2).person
                Gestuft(stufe(gen(n), m.t), Modifier.offset(left(n), top(n))) {
                    LeerBox(if (n % 2 == 0) "M" else "F", Modifier, onClick = if (canEdit && !kind.isPrivate) ({ viewModel.requestAddRelative(kind.xref) }) else null,
                        breite = breiteIn(n), hoehe = grundH(gen(n), m.hoch), flach = true, schrift = minOf(m.hoch, 1.25f))
                }
            }
        }
    }
}

/** Infokasten: grosses Portraet, Name in Registerform, Geburt, Ehen mit roemischer Nummer, Tod, Beruf. */
@Composable
private fun InfoBox(detail: IndividualDetail, gewaehlt: Int, modifier: Modifier, onOpen: () -> Unit, onPerson: (String) -> Unit) {
    val p = detail.person
    val colors = MaterialTheme.colorScheme
    fun ort(f: FactJson?) = f?.let { listOfNotNull(it.date?.text?.takeIf(String::isNotBlank), it.place?.short?.takeIf(String::isNotBlank)).joinToString(" ") }.orEmpty()
    val geburt = ort(detail.facts.firstOrNull { it.tag == "BIRT" } ?: detail.facts.firstOrNull { it.tag == "CHR" })
    val tod = ort(detail.facts.firstOrNull { it.tag == "DEAT" } ?: detail.facts.firstOrNull { it.tag == "BURI" })
    val beruf = listOfNotNull(detail.facts.firstOrNull { it.tag == "TITL" }?.value, detail.facts.firstOrNull { it.tag == "OCCU" }?.value).filter(String::isNotBlank).joinToString(" · ")
    val roemisch = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII")
    val priv = stringResource(Res.string.person_private); val none = stringResource(Res.string.person_no_name)
    Row(modifier.background(colors.surface).border(1.dp, colors.outline).combinedClickable(onClick = onOpen).padding(8.dp)) {
        Box(Modifier.width(160.dp).fillMaxHeight().border(1.dp, colors.outlineVariant)) {
            Portrait(p, Modifier.fillMaxSize())
        }
        Column(Modifier.padding(start = 12.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(registerName(p, priv, none), fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
            if (beruf.isNotBlank()) Text(beruf, fontSize = 14.sp, color = colors.onSurfaceVariant)
            if (geburt.isNotBlank()) Text("*  $geburt", fontSize = 14.sp)
            detail.spouseFamilies.forEachIndexed { i, fam ->
                val wann = ort(fam.facts.hauptHeirat())
                val sp = fam.spouse
                val nummer = "⚭ ${roemisch.getOrElse(i) { "${i + 1}." }}  "
                val zusatz = if (wann.isNotBlank()) "  ($wann)" else ""
                val gewicht = if (i == gewaehlt) FontWeight.SemiBold else FontWeight.Normal
                if (sp != null) Text(nummer + klarName(sp) + zusatz, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = gewicht)
                else Text(androidx.compose.ui.text.buildAnnotatedString {
                    append(nummer)
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = colors.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    append(unbekannterPartner(p.sex)); pop(); append(zusatz)
                }, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = gewicht)
            }
            if (tod.isNotBlank()) Text("†  $tod", fontSize = 14.sp)
            // Geschwister (Halbgeschwister mit ½): ein Klick macht sie zur Zentralperson
            val voll = detail.parentFamilies.flatMap { it.children }.filter { it.xref != p.xref }.distinctBy { it.xref }
            val halb = detail.halfSiblings().map { it.person }.filter { h -> voll.none { it.xref == h.xref } }
            if (voll.isNotEmpty() || halb.isNotEmpty()) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.desk_siblings) + ":", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    (voll.map { it to false } + halb.map { it to true }).forEach { (g, istHalb) ->
                        val text = (if (g.isPrivate) priv else klarName(g)) + (if (istHalb) " ½" else "") + jahre(g).let { if (it.isNotEmpty()) " ($it)" else "" }
                        Text(text, fontSize = 13.sp, color = if (g.isPrivate) colors.onSurfaceVariant else colors.primary,
                            modifier = if (g.isPrivate) Modifier else Modifier.clickable { onPerson(g.xref) })
                    }
                }
            }
        }
    }
}

private enum class Art { Zentral, Ahn, Kind, Partner }

/** Ein Personenkasten: Portraet ueber die ganze Hoehe, "Nachname, Vorname" gross, Jahre darunter, Pfeile wo es weitergeht. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonBox(
    person: Person, art: Art, viewModel: AppViewModel, onOpenSheet: (String) -> Unit, openWeb: (String) -> Unit, modifier: Modifier,
    pfeilLinks: Boolean = false, pfeilRechts: Boolean = false, breite: Dp = BOX_W, hoehe: Dp = BOX_H,
    flach: Boolean = hoehe < BOX_H, schrift: Float = 1f,
) {
    val c = boxColors(person.sex)
    val name = registerName(person, stringResource(Res.string.person_private), stringResource(Res.string.person_no_name))
    val asCentre = stringResource(Res.string.desk_as_centre); val edit = stringResource(Res.string.desk_sheet); val web = stringResource(Res.string.chip_open_web)
    val merken = stringResource(Res.string.desk_bookmark_add); val merkWeg = stringResource(Res.string.desk_bookmark_remove)
    val loeschen = stringResource(Res.string.action_delete_person)
    val anfuegen = stringResource(Res.string.desk_link_existing)
    val darfLoeschen = viewModel.uiState.value.tree?.canEdit == true
    val onSurface = MaterialTheme.colorScheme.onSurface
    val stil = LocalNavStil.current
    val linie = LocalFarben.current[person.xref]
    val fill = if (stil.kante) MaterialTheme.colorScheme.surface else c.fill
    val rand = when { art == Art.Zentral -> onSurface; stil.kante -> MaterialTheme.colorScheme.outlineVariant; else -> c.border }
    val klick: () -> Unit = if (art == Art.Zentral) ({ onOpenSheet(person.xref) }) else ({ viewModel.setRoot(person.xref) })
    // Die Lage (offset) gehoert an die aeussere Box: ContextMenuArea ist selbst eine Box mit Zeigerabfrage. Saesse der offset nur
    // am Kasten darin, laegen die Menuebereiche aller Kinder oben in der Spalte uebereinander, und der des letzten Kindes
    // verschluckte die Klicks auf das erste (Issue 8).
    Box(modifier) {
    ContextMenuArea(items = {
        if (person.isPrivate) emptyList() else listOfNotNull(
            if (art != Art.Zentral) ContextMenuItem(asCentre) { viewModel.setRoot(person.xref) } else null,
            ContextMenuItem(edit) { onOpenSheet(person.xref) },
            if (viewModel.bookmarksSupported) ContextMenuItem(if (viewModel.isBookmarked(person.xref)) merkWeg else merken) { viewModel.toggleBookmark(person.xref) } else null,
            ContextMenuItem(web) { openWeb(person.url) },
            if (darfLoeschen) ContextMenuItem(anfuegen) { Anfuegewahl.person = person } else null,
            if (darfLoeschen) ContextMenuItem(loeschen) { Loeschwahl.person = person.xref to person.name } else null,
        )
    }) {
        Row(
            Modifier.size(breite, hoehe)
                .background(fill)
                .border(if (art == Art.Zentral) 2.5.dp else 1.dp, rand)
                .fokusRahmen()
                .combinedClickable(enabled = !person.isPrivate, onClick = klick, onDoubleClick = { onOpenSheet(person.xref) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Kante: Geschlechtsfarbe (oder die Linienfarbe der Farbkodierung) als schmaler Balken links
            if (stil.kante) Box(Modifier.width(5.dp).fillMaxHeight().background(linie ?: c.border))
            if (pfeilLinks) Text("◀", fontSize = 11.sp, color = onSurface, modifier = Modifier.padding(start = 2.dp))
            if (stil.medaillon) Portrait(person, Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp).size(hoehe - 8.dp).clip(CircleShape).border(1.dp, c.border.copy(alpha = 0.7f), CircleShape))
            else Portrait(person, Modifier.padding(1.dp).size(hoehe - 2.dp))
            Column(Modifier.weight(1f).padding(start = 6.dp, end = 4.dp)) {
                // Name und Lebensdaten fuellen den Kasten: grosse Schrift, kaum Rand, kraeftiges Schwarz
                Text(name, fontSize = (if (flach) 16.sp else 19.sp) * schrift, fontWeight = if (art == Art.Zentral) FontWeight.Bold else FontWeight.Medium,
                    lineHeight = (if (flach) 18.sp else 22.sp) * schrift, maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface)
                Text(lebensdaten(person).ifBlank { " " }, fontSize = (if (flach) 12.5.sp else 15.sp) * schrift, lineHeight = (if (flach) 14.sp else 18.sp) * schrift,
                    color = onSurface.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!stil.kante) linie?.let { farbe -> Box(Modifier.width(6.dp).fillMaxHeight().background(farbe)) }
            if (pfeilRechts) Text("▶", fontSize = 11.sp, color = onSurface, modifier = Modifier.padding(end = 2.dp))
        }
    }
    }
}

/** Leerer Kasten fuer einen unbekannten Elternteil; mit Schreibrecht legt ein Klick die Person an. */
@Composable
private fun LeerBox(sex: String, modifier: Modifier, onClick: (() -> Unit)?, text: String = "", breite: Dp = BOX_W, hoehe: Dp = BOX_H,
    @Suppress("UNUSED_PARAMETER") flach: Boolean = false, @Suppress("UNUSED_PARAMETER") schrift: Float = 1f) {
    val c = boxColors(sex)
    val kante = LocalNavStil.current.kante
    Box(
        modifier.size(breite, hoehe)
            .background(if (kante) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) else c.fill.copy(alpha = 0.55f))
            .border(1.dp, if (kante) MaterialTheme.colorScheme.outlineVariant else c.border.copy(alpha = 0.6f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (kante) Box(Modifier.width(5.dp).fillMaxHeight().background(c.border.copy(alpha = 0.45f)))
        if (text.isNotEmpty()) Text(text, Modifier.padding(start = if (kante) 14.dp else 10.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KleinKnopf(icon: androidx.compose.ui.graphics.vector.ImageVector, beschreibung: String, onClick: () -> Unit) {
    Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = beschreibung, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
