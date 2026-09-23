package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
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
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.*
import org.jetbrains.compose.resources.stringResource

/*
 * Aufbau "Navigator" (Fassung 2, 23.09.2026, nach dem Vergleich mit dem Vorbild): links die Kinder als Spalte mit
 * Klammer, in der Mitte die Zentralperson mit den Partnern darunter, rechts die Vorfahren ueber die volle Hoehe -
 * Generation fuer Generation eine Spalte, unbekannte Eltern als leere Kaesten, Pfeile wo es weitergeht. Oben links
 * der Infokasten mit grossem Portraet und allen Ehen. Klick auf die Zentralperson oeffnet den Eingabedialog,
 * Klick auf jede andere Person macht sie zur Zentralperson; Doppelklick oeffnet, rechte Taste zeigt das Menue.
 */

private val BOX_W = 290.dp
private val BOX_H = 58.dp
private val GAP = 10.dp
private val COL_GAP = 44.dp
private val INFO_H = 236.dp
private val ICON_ROW = 32.dp

/** Farbkodierung nach Mary Hill: xref -> Farbe des Streifens am rechten Kastenrand (leer = aus). */
val LocalFarben = staticCompositionLocalOf { emptyMap<String, Color>() }

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
        "M" -> if (dark) BoxColors(Color(0xFF244B6B), Color(0xFF7FB2DA)) else BoxColors(Color(0xFFB9D0E8), Color(0xFF4F7FAE))
        "F" -> if (dark) BoxColors(Color(0xFF5E2F36), Color(0xFFE09AA2)) else BoxColors(Color(0xFFF3C4BE), Color(0xFFC2706A))
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
    farben: Map<String, Color> = emptyMap(), zoom: Float = 1f,
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

    // Wie beim Vorbild eine Partnerschaft auf einmal: Partner und deren Kinder; der Pfeil unter dem Partner wechselt.
    val familien = detail?.spouseFamilies.orEmpty()
    var gewaehlt by remember(state.root) { mutableStateOf(-1) }
    val fIndex = if (gewaehlt in familien.indices) gewaehlt else familien.indexOfFirst { it.spouse != null }.coerceAtLeast(0)
    val familie = familien.getOrNull(fIndex)
    val quer = rememberScrollState(); val hoch = rememberScrollState()
    val basis = LocalDensity.current
    val g = state.ancestorGenerations.coerceIn(2, 7)
    val masse = chartMasse(g, familie, familien.isNotEmpty())
    // Einpassen wie beim Vorbild: die Tafel fuellt das Fenster, der Zoom vergroessert oder verkleinert davon ausgehend.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val rand = 24.dp
        // Massgeblich sind Vorfahren und Infokasten; eine lange Kinderspalte rollt, statt alles zu verkleinern. Nie unter 70 Prozent.
        val fit = minOf((maxWidth - rand) / masse.breite, (maxHeight - rand) / masse.hoeheFit, 1.15f).coerceIn(0.7f, 1.15f)
        val skala = fit * zoom
        CompositionLocalProvider(LocalFarben provides farben, LocalDensity provides Density(basis.density * skala, basis.fontScale)) {
            Box(Modifier.fillMaxSize()) {
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

/** Die Masse der Tafel in dp vor dem Zeichnen - fuer das Einpassen. */
private class ChartMasse(val slots: Int, val slotH: Dp, val ancH: Dp, val kinder: Int, val partner: Int, val xZentral: Dp, val centerY: Dp, val kinderTop: Dp, val breite: Dp, val hoehe: Dp, val hoeheFit: Dp)

private fun chartMasse(g: Int, familie: de.bgghome.webtrees.nativ.api.FamilyJson?, hatFamilien: Boolean): ChartMasse {
    val slots = 1 shl (g - 1)
    val slotH = BOX_H + GAP
    val ancH = slotH * slots
    val kinder = familie?.children?.size ?: 0
    val partner = if (hatFamilien) 1 else 0
    val xZentral = BOX_W + 56.dp
    // Die Zentralperson sitzt in der Mitte der Vorfahren; nur wenn ihr Kasten in den Infokasten ragt, rutscht alles nach unten.
    val centerY = maxOf(ancH / 2, INFO_H + ICON_ROW + BOX_H / 2)
    // Die Kinderspalte beginnt unter dem Infokasten und ist sonst um die Zentralperson zentriert.
    val kinderH = slotH * kinder
    val kinderTop = maxOf(centerY - kinderH / 2, INFO_H + ICON_ROW)
    val partnerH = (BOX_H + GAP) * partner
    val hoeheFit = maxOf(centerY + ancH / 2, centerY + BOX_H / 2 + partnerH + 30.dp) + 24.dp
    val hoehe = maxOf(hoeheFit, kinderTop + kinderH + 24.dp)
    val breite = xZentral + BOX_W + (BOX_W + COL_GAP) * (g - 1) + 24.dp
    return ChartMasse(slots, slotH, ancH, kinder, partner, xZentral, centerY, kinderTop, breite, hoehe, hoeheFit)
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

    fun top(n: Int): Dp { val gg = gen(n); val span = slots shr gg; val i = n - (1 shl gg); return centerY - ancH / 2 + slotH * (i * span) + (slotH * span - BOX_H) / 2 }
    fun left(n: Int): Dp = xZentral + (BOX_W + COL_GAP) * gen(n)
    val kinderTop = m.kinderTop

    Box(Modifier.size(breite, hoehe)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 1.2.dp.toPx()
            // Vorfahren: vom Kind nach rechts, senkrecht, zu beiden Eltern (auch zu leeren Kaesten)
            for (n in 1 until (1 shl (g - 1))) {
                if (n !in ahnen) continue
                val x0 = (left(n) + BOX_W).toPx(); val y0 = (top(n) + BOX_H / 2).toPx(); val xm = x0 + (COL_GAP / 2).toPx()
                drawLine(line, Offset(x0, y0), Offset(xm, y0), w)
                listOf(2 * n, 2 * n + 1).forEach { p ->
                    val y1 = (top(p) + BOX_H / 2).toPx()
                    drawLine(line, Offset(xm, y0), Offset(xm, y1), w); drawLine(line, Offset(xm, y1), Offset(left(p).toPx(), y1), w)
                }
            }
            // Kinder: Klammer links der Zentralperson
            if (kinder.isNotEmpty()) {
                val xb = (xZentral - 26.dp).toPx(); val yc = (centerY).toPx()
                drawLine(line, Offset(xb, yc), Offset(xZentral.toPx(), yc), w)
                val y0 = (kinderTop + BOX_H / 2).toPx(); val y1 = (kinderTop + slotH * (kinder.size - 1) + BOX_H / 2).toPx()
                drawLine(line, Offset(xb, minOf(y0, yc)), Offset(xb, maxOf(y1, yc)), w)
                kinder.indices.forEach { i -> val y = (kinderTop + slotH * i + BOX_H / 2).toPx(); drawLine(line, Offset(BOX_W.toPx(), y), Offset(xb, y), w) }
            }
            // Partner: senkrecht unter der Zentralperson
            if (familie != null) {
                val x = (xZentral + 40.dp).toPx()
                drawLine(line, Offset(x, (centerY + BOX_H / 2).toPx()), Offset(x, (centerY + BOX_H / 2 + GAP).toPx()), w * 2)
            }
        }

        // Infokasten oben links
        if (detail != null) InfoBox(detail, fIndex, Modifier.offset(0.dp, 0.dp).size(BOX_W * 2 + 56.dp, INFO_H - 12.dp), onOpen = { onOpenSheet(zentral.xref) })

        // Kinder
        kinder.forEachIndexed { i, k ->
            PersonBox(k, Art.Kind, viewModel, onOpenSheet, openWeb, Modifier.offset(0.dp, kinderTop + slotH * i), pfeilLinks = k.xref in mitNachkommen)
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
            else LeerBox(if (zentral.sex == "F") "M" else "F", Modifier.offset(xZentral + 40.dp, partnerY), onClick = if (canEdit) ({ viewModel.requestAddRelative(zentral.xref) }) else null, text = "…")
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
                PersonBox(a.person, Art.Ahn, viewModel, onOpenSheet, openWeb, Modifier.offset(left(n), top(n)), pfeilRechts = gen(n) == g - 1 && a.hasParents)
            } else if ((n / 2) in ahnen) {
                val kind = ahnen.getValue(n / 2).person
                LeerBox(if (n % 2 == 0) "M" else "F", Modifier.offset(left(n), top(n)), onClick = if (canEdit && !kind.isPrivate) ({ viewModel.requestAddRelative(kind.xref) }) else null)
            }
        }
    }
}

/** Infokasten: grosses Portraet, Name in Registerform, Geburt, Ehen mit roemischer Nummer, Tod, Beruf. */
@Composable
private fun InfoBox(detail: IndividualDetail, gewaehlt: Int, modifier: Modifier, onOpen: () -> Unit) {
    val p = detail.person
    val colors = MaterialTheme.colorScheme
    fun ort(f: FactJson?) = f?.let { listOfNotNull(it.date?.text?.takeIf(String::isNotBlank), it.place?.short?.takeIf(String::isNotBlank)).joinToString(" ") }.orEmpty()
    val geburt = ort(detail.facts.firstOrNull { it.tag == "BIRT" } ?: detail.facts.firstOrNull { it.tag == "CHR" })
    val tod = ort(detail.facts.firstOrNull { it.tag == "DEAT" } ?: detail.facts.firstOrNull { it.tag == "BURI" })
    val beruf = listOfNotNull(detail.facts.firstOrNull { it.tag == "TITL" }?.value, detail.facts.firstOrNull { it.tag == "OCCU" }?.value).filter(String::isNotBlank).joinToString(" · ")
    val roemisch = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII")
    val priv = stringResource(Res.string.person_private); val none = stringResource(Res.string.person_no_name)
    Row(modifier.background(colors.surface).border(1.dp, colors.outline).combinedClickable(onClick = onOpen).padding(8.dp)) {
        Box(Modifier.width(160.dp).fillMaxHeight().background(colors.surfaceVariant).border(1.dp, colors.outlineVariant)) {
            if (p.thumb != null) AsyncImage(p.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Avatar(p, 96.dp, Modifier.align(Alignment.Center))
        }
        Column(Modifier.padding(start = 12.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(registerName(p, priv, none), fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
            if (beruf.isNotBlank()) Text(beruf, fontSize = 14.sp, color = colors.onSurfaceVariant)
            if (geburt.isNotBlank()) Text("*  $geburt", fontSize = 14.sp)
            detail.spouseFamilies.forEachIndexed { i, fam ->
                val wann = ort(fam.facts.firstOrNull { it.tag == "MARR" })
                val wer = fam.spouse?.name ?: "…"
                Text("⚭ ${roemisch.getOrElse(i) { "${i + 1}." }}  $wer" + (if (wann.isNotBlank()) "  ($wann)" else ""), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (i == gewaehlt) FontWeight.SemiBold else FontWeight.Normal)
            }
            if (tod.isNotBlank()) Text("†  $tod", fontSize = 14.sp)
        }
    }
}

private enum class Art { Zentral, Ahn, Kind, Partner }

/** Ein Personenkasten: Portraet ueber die ganze Hoehe, "Nachname, Vorname" gross, Jahre darunter, Pfeile wo es weitergeht. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonBox(
    person: Person, art: Art, viewModel: AppViewModel, onOpenSheet: (String) -> Unit, openWeb: (String) -> Unit, modifier: Modifier,
    pfeilLinks: Boolean = false, pfeilRechts: Boolean = false,
) {
    val c = boxColors(person.sex)
    val name = registerName(person, stringResource(Res.string.person_private), stringResource(Res.string.person_no_name))
    val asCentre = stringResource(Res.string.desk_as_centre); val edit = stringResource(Res.string.desk_sheet); val web = stringResource(Res.string.chip_open_web)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val klick: () -> Unit = if (art == Art.Zentral) ({ onOpenSheet(person.xref) }) else ({ viewModel.setRoot(person.xref) })
    ContextMenuArea(items = {
        if (person.isPrivate) emptyList() else listOfNotNull(
            if (art != Art.Zentral) ContextMenuItem(asCentre) { viewModel.setRoot(person.xref) } else null,
            ContextMenuItem(edit) { onOpenSheet(person.xref) },
            ContextMenuItem(web) { openWeb(person.url) },
        )
    }) {
        Row(
            modifier.size(BOX_W, BOX_H)
                .background(c.fill)
                .border(if (art == Art.Zentral) 2.5.dp else 1.dp, if (art == Art.Zentral) onSurface else c.border)
                .fokusRahmen()
                .combinedClickable(enabled = !person.isPrivate, onClick = klick, onDoubleClick = { onOpenSheet(person.xref) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pfeilLinks) Text("◀", fontSize = 11.sp, color = onSurface, modifier = Modifier.padding(start = 2.dp))
            if (person.thumb != null) {
                AsyncImage(person.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(BOX_H - 2.dp).padding(1.dp))
            } else Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f).padding(start = 6.dp, end = 4.dp)) {
                Text(name, fontSize = 15.sp, fontWeight = if (art == Art.Zentral) FontWeight.Bold else FontWeight.SemiBold, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface)
                Text(person.lifespan.ifBlank { " " }, fontSize = 12.sp, lineHeight = 15.sp, color = onSurface.copy(alpha = 0.75f), maxLines = 1)
            }
            LocalFarben.current[person.xref]?.let { farbe -> Box(Modifier.width(6.dp).fillMaxHeight().background(farbe)) }
            if (pfeilRechts) Text("▶", fontSize = 11.sp, color = onSurface, modifier = Modifier.padding(end = 2.dp))
        }
    }
}

/** Leerer Kasten fuer einen unbekannten Elternteil; mit Schreibrecht legt ein Klick die Person an. */
@Composable
private fun LeerBox(sex: String, modifier: Modifier, onClick: (() -> Unit)?, text: String = "") {
    val c = boxColors(sex)
    Box(
        modifier.size(BOX_W, BOX_H).background(c.fill.copy(alpha = 0.55f)).border(1.dp, c.border.copy(alpha = 0.6f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.CenterStart,
    ) { if (text.isNotEmpty()) Text(text, Modifier.padding(start = 10.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun KleinKnopf(icon: androidx.compose.ui.graphics.vector.ImageVector, beschreibung: String, onClick: () -> Unit) {
    Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = beschreibung, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
