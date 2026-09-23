package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.*
import org.jetbrains.compose.resources.stringResource

/*
 * Aufbau "Navigator": die Zentralperson links mit Bild und Eckdaten, darunter Partner und Kinder, rechts daneben
 * ihre Vorfahren als waagerechte Ahnentafel - Generation fuer Generation eine Spalte. Ein Klick macht eine Person
 * zur Zentralperson, ein Doppelklick oeffnet den Eingabedialog, die rechte Taste das Kontextmenue.
 */

private val BOX_W = 190.dp
private val BOX_H = 38.dp
private val SLOT_GAP = 6.dp
private val COL_GAP = 28.dp

/** Farben der Kaesten nach Geschlecht - hell gefuellt, dunklerer Rand. */
private data class BoxColors(val fill: Color, val border: Color)

@Composable
private fun boxColors(sex: String): BoxColors {
    val dark = MaterialTheme.colorScheme.background.red < 0.5f
    return when (sex) {
        "M" -> if (dark) BoxColors(Color(0xFF1E3A4F), Color(0xFF5FA3C8)) else BoxColors(Color(0xFFD7E9F5), Color(0xFF7DAFD0))
        "F" -> if (dark) BoxColors(Color(0xFF4A2A30), Color(0xFFD08A94)) else BoxColors(Color(0xFFF7DCDF), Color(0xFFD9989F))
        else -> if (dark) BoxColors(Color(0xFF2E3434), Color(0xFF7D8887)) else BoxColors(Color(0xFFE8ECEC), Color(0xFFA3ACAB))
    }
}

/** "Nachname, Vorname" wie in einem Register; webtrees liefert das als sortName. */
internal fun registerName(person: Person, privateLabel: String, noName: String): String = when {
    person.isPrivate -> privateLabel
    person.sortName.isNotBlank() -> person.sortName.replace(Regex(",(?=\\S)"), ", ")
    else -> person.name.ifBlank { noName }
}

@Composable
fun Navigator(state: UiState, viewModel: AppViewModel, onOpenSheet: (String) -> Unit, openWeb: (String) -> Unit) {
    // Wie der Baum: nach jedem Wechsel der Zentralperson die Ahnen neu holen.
    LaunchedEffect(state.root, state.pedigree == null) {
        if (state.root != null && state.pedigree == null) viewModel.loadChart()
    }
    // Der Eingabedialog kann eine andere Person zeigen (Verwandte, Blaettern) - die Zentralperson bleibt hier stehen.
    var rootDetail by remember { mutableStateOf<IndividualDetail?>(null) }
    LaunchedEffect(state.detail) { state.detail?.takeIf { it.person.xref == state.root }?.let { rootDetail = it } }
    val detail = rootDetail?.takeIf { it.person.xref == state.root }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Links: Zentralperson, Partner, Kinder
        val links = rememberScrollState()
        Box(Modifier.width(300.dp).fillMaxHeight()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(links).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (detail != null) {
                    CentreCard(detail, onOpen = { onOpenSheet(detail.person.xref) })
                    FamilyList(detail, viewModel, onOpenSheet, openWeb)
                }
            }
            SenkrechteLeiste(links)
        }
        // Rechts: die Ahnentafel
        val quer = rememberScrollState()
        val hoch = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize().horizontalScroll(quer).verticalScroll(hoch).padding(12.dp)) {
                val pedigree = state.pedigree
                if (pedigree != null) AncestorChart(pedigree.ancestors.associateBy { it.n }.mapValues { it.value.person }, state.ancestorGenerations, state.root, viewModel, onOpenSheet, openWeb)
            }
            SenkrechteLeiste(hoch)
            WaagerechteLeiste(quer)
        }
    }
}

/** Der Kasten der Zentralperson: Bild, Name, Beruf, Geburt, Heirat, Tod - wie eine Karteikarte. */
@Composable
private fun CentreCard(detail: IndividualDetail, onOpen: () -> Unit) {
    val person = detail.person
    val colors = MaterialTheme.colorScheme
    val occupation = detail.facts.firstOrNull { it.tag == "OCCU" }?.value
    fun line(prefix: String, fact: FactJson?) = fact?.let { f ->
        listOfNotNull(f.date?.text?.takeIf { it.isNotBlank() }, f.place?.name?.takeIf { it.isNotBlank() }).joinToString(" ").takeIf { it.isNotBlank() }?.let { "$prefix $it" }
    }
    val birth = line("*", detail.facts.firstOrNull { it.tag == "BIRT" } ?: detail.facts.firstOrNull { it.tag == "CHR" })
    val death = line("†", detail.facts.firstOrNull { it.tag == "DEAT" } ?: detail.facts.firstOrNull { it.tag == "BURI" })
    val marriage = detail.spouseFamilies.firstOrNull()?.marriage?.let { m ->
        listOfNotNull(m.date?.text?.takeIf { it.isNotBlank() }, m.place?.name?.takeIf { it.isNotBlank() }).joinToString(" ").takeIf { it.isNotBlank() }?.let { "⚭ $it" }
    }
    Row(
        Modifier.fillMaxWidth().background(colors.surface).border(1.dp, colors.outline).combinedClickable(onClick = {}, onDoubleClick = onOpen).padding(8.dp),
    ) {
        Box(Modifier.size(84.dp, 104.dp).background(colors.surfaceVariant).border(1.dp, colors.outlineVariant)) {
            val url = person.thumb
            if (url != null) AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Avatar(person, 60.dp, Modifier.align(Alignment.Center))
        }
        Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            occupation?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            listOfNotNull(birth, marriage, death).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Partner und Kinder der Zentralperson, darueber die Eltern-Familie ist in der Ahnentafel rechts zu sehen. */
@Composable
private fun FamilyList(detail: IndividualDetail, viewModel: AppViewModel, onOpenSheet: (String) -> Unit, openWeb: (String) -> Unit) {
    if (detail.spouseFamilies.isEmpty()) return
    Text(stringResource(Res.string.desk_partners), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    detail.spouseFamilies.forEach { family ->
        family.spouse?.let { PersonBox(it, false, viewModel, onOpenSheet, openWeb, Modifier.fillMaxWidth()) }
        family.children.forEach { child ->
            Row {
                Spacer(Modifier.width(18.dp))
                PersonBox(child, false, viewModel, onOpenSheet, openWeb, Modifier.weight(1f))
            }
        }
    }
}

/** Die Vorfahren in Spalten: Generation g hat 2^g Plaetze, jeder mittig ueber seinen zwei Eltern-Plaetzen. */
@Composable
private fun AncestorChart(
    byNumber: Map<Int, Person>, generations: Int, root: String?,
    viewModel: AppViewModel, onOpenSheet: (String) -> Unit, openWeb: (String) -> Unit,
) {
    val slots = 1 shl (generations - 1)
    val slotH = BOX_H + SLOT_GAP
    val height = slotH * slots
    val width = BOX_W * generations + COL_GAP * (generations - 1)
    val line = MaterialTheme.colorScheme.outline

    fun top(n: Int): Dp {
        val g = 31 - Integer.numberOfLeadingZeros(n)
        val span = slots shr g
        val index = n - (1 shl g)
        return slotH * (index * span) + (slotH * span - BOX_H) / 2
    }
    fun left(n: Int): Dp = (BOX_W + COL_GAP) * (31 - Integer.numberOfLeadingZeros(n))

    Box(Modifier.size(width, height)) {
        // Verbindungslinien: vom Kind rechts heraus, senkrecht zu beiden Eltern
        Canvas(Modifier.fillMaxSize()) {
            byNumber.keys.filter { it * 2 in byNumber || it * 2 + 1 in byNumber }.forEach { n ->
                if (31 - Integer.numberOfLeadingZeros(n) >= generations - 1) return@forEach
                val x0 = (left(n) + BOX_W).toPx(); val y0 = (top(n) + BOX_H / 2).toPx()
                val xm = x0 + (COL_GAP / 2).toPx(); val x1 = left(n * 2).toPx()
                drawLine(line, Offset(x0, y0), Offset(xm, y0), 1.dp.toPx())
                listOf(n * 2, n * 2 + 1).filter { it in byNumber }.forEach { p ->
                    val y1 = (top(p) + BOX_H / 2).toPx()
                    drawLine(line, Offset(xm, y0), Offset(xm, y1), 1.dp.toPx())
                    drawLine(line, Offset(xm, y1), Offset(x1, y1), 1.dp.toPx())
                }
            }
        }
        byNumber.forEach { (n, person) ->
            if (31 - Integer.numberOfLeadingZeros(n) < generations) {
                PersonBox(person, person.xref == root, viewModel, onOpenSheet, openWeb, Modifier.offset(left(n), top(n)).width(BOX_W))
            }
        }
    }
}

/** Ein Personenkaesten: "Nachname, Vorname" und Jahre, gefuellt nach Geschlecht; die Zentralperson dick umrandet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonBox(person: Person, centre: Boolean, viewModel: AppViewModel, onOpenSheet: (String) -> Unit, openWeb: (String) -> Unit, modifier: Modifier) {
    val c = boxColors(person.sex)
    val name = registerName(person, stringResource(Res.string.person_private), stringResource(Res.string.person_no_name))
    val asCentre = stringResource(Res.string.desk_as_centre)
    val edit = stringResource(Res.string.desk_sheet)
    val web = stringResource(Res.string.chip_open_web)
    ContextMenuArea(items = {
        if (person.isPrivate) emptyList() else listOf(
            ContextMenuItem(asCentre) { viewModel.setRoot(person.xref) },
            ContextMenuItem(edit) { onOpenSheet(person.xref) },
            ContextMenuItem(web) { openWeb(person.url) },
        )
    }) {
        Row(
            modifier
                .height(BOX_H)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(c.fill)
                .border(if (centre) 2.dp else 1.dp, if (centre) MaterialTheme.colorScheme.onSurface else c.border, MaterialTheme.shapes.extraSmall)
                .fokusRahmen()
                .combinedClickable(enabled = !person.isPrivate, onClick = { viewModel.setRoot(person.xref) }, onDoubleClick = { onOpenSheet(person.xref) })
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (person.thumb != null) {
                AsyncImage(person.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(26.dp).clip(MaterialTheme.shapes.extraSmall))
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                if (person.lifespan.isNotBlank()) Text(person.lifespan, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
