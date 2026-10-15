package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.data.Ortsanzeige
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.Ancestor
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.FamilyJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.halfSiblings
import de.bgghome.webtrees.nativ.data.hatPaten
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.Avatar
import de.bgghome.webtrees.nativ.ui.PatenZeilen
import de.bgghome.webtrees.nativ.ui.Section
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.faktLabelMitArt
import de.bgghome.webtrees.nativ.ui.forSex
import de.bgghome.webtrees.nativ.ui.requestAddRelative
import de.bgghome.webtrees.nativ.ui.select
import de.bgghome.webtrees.nativ.ui.setRoot
import de.bgghome.webtrees.nativ.ui.setSection
import de.bgghome.webtrees.nativ.ui.treeColors
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Constraints
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource as StringResourceRef
import org.jetbrains.compose.resources.stringResource

/*
 * Familienansicht: das Paar als grosse Karten in der Mitte, darueber die Vorfahren beider Partner in waehlbarer Tiefe
 * (2 bis 4 Generationen, oben die aeltesten als kleine Kaesten), darunter die Statuszeile der Partnerschaft (Heiraten,
 * Zahl der Kinder, Kind hinzufuegen) und die Kinder als Kaesten nebeneinander mit ihren Partnern und Enkeln. Hat der
 * Proband mehrere Ehen, steht jede auf einem eigenen Reiter. Ein Klick waehlt eine Person (Personentafel rechts), ein
 * Doppelklick macht sie zum Probanden - so geht man durch die Familien nach oben und unten.
 * Die Daten kommen aus der Personenabfrage des Probanden und seines Partners, den Ahnentafeln beider (Vorfahren) und
 * der Nachfahrenabfrage des Probanden (Partner und Kinder der Kinder) - nicht aus dem Baum-Export.
 */

/** Vorauswahl fuer den Dialog "Verwandte hinzufuegen", gesetzt vom leeren Feld "Vater/Mutter unbekannt". */
object Verwandtenwahl {
    var vorwahl: String? by mutableStateOf(null)
    var familie: String? by mutableStateOf(null)
    /** Geschlecht vorbelegen ("M"/"F"), von "+ Sohn" / "+ Tochter". */
    var geschlecht: String? by mutableStateOf(null)
    fun leeren() { vorwahl = null; familie = null; geschlecht = null }
}

/** Die Familie des Probanden auf dem gewaehlten Reiter; Eltern je Partner aus dessen erster Herkunftsfamilie. */
private class PaarDaten(
    val proband: IndividualDetail, val partner: IndividualDetail?,
    /** Vorfahren je Seite (Kekule-Nummern ab 2), Schluessel = XREF der Person unten. */
    val ahnen: Map<String, List<Ancestor>>,
    /** Die Kinder mit ihren Familien (Partner, Enkel), Schluessel = XREF des Kindes. */
    val nachfahren: Map<String, DescendantNode>,
)

private fun ereignisZeile(zeichen: String, e: EventJson?): String? {
    val datum = e?.date?.text.orEmpty(); val ort = Ortsanzeige.text(e?.place?.name)
    return if (datum.isBlank() && ort.isBlank()) null else "$zeichen " + listOf(datum, ort).filter(String::isNotBlank).joinToString(", ")
}

private const val GEN_MIN = 2
private const val GEN_MAX = 4

@Composable
fun DeskFamilie(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, openSheet: ((String) -> Unit)? = null, onSuche: (() -> Unit)? = null) {
    val root = state.root
    val tree = state.tree?.name
    var neu by remember { mutableIntStateOf(0) }
    var generationen by remember { mutableIntStateOf((DeskLayout.prefs.getString("familie_gen", null)?.toIntOrNull() ?: 4).coerceIn(GEN_MIN, GEN_MAX)) }
    // Nach dem Speichern (Verwandte hinzufuegen, Ereignis) neu laden
    var warBeschaeftigt by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy) { if (warBeschaeftigt && !state.busy) neu++; warBeschaeftigt = state.busy }

    val proband by produceState<Result<IndividualDetail>?>(null, root, tree, neu) {
        if (root == null || tree == null) { value = null; return@produceState }
        if (value?.getOrNull()?.person?.xref != root) value = null
        value = withContext(Dispatchers.IO) { runCatching { viewModel.client.individual(tree, root) } }
    }
    var reiter by remember(root) { mutableIntStateOf(0) }
    val p = proband?.getOrNull()
    val familien = p?.spouseFamilies.orEmpty()
    val familie = familien.getOrNull(reiter.coerceAtMost((familien.size - 1).coerceAtLeast(0)))
    val partnerXref = familie?.let { f -> listOfNotNull(f.husband, f.wife).firstOrNull { it.xref != root }?.takeIf { !it.isPrivate }?.xref }
    val partner by produceState<IndividualDetail?>(null, partnerXref, tree, neu) {
        value = if (partnerXref == null || tree == null) null
        else withContext(Dispatchers.IO) { runCatching { viewModel.client.individual(tree, partnerXref) }.getOrNull() }
    }
    // Vorfahren beider Partner ab der Grosselterngeneration (die Eltern stehen schon in der Personenabfrage)
    val ahnen by produceState<Map<String, List<Ancestor>>>(emptyMap(), root, partnerXref, tree, neu, generationen) {
        if (root == null || tree == null || generationen < 3) { value = emptyMap(); return@produceState }
        value = withContext(Dispatchers.IO) {
            listOfNotNull(root, partnerXref).associateWith { x ->
                runCatching { viewModel.client.pedigree(tree, x, generationen).ancestors.filter { it.n >= 4 } }.getOrNull().orEmpty()
            }
        }
    }
    // Partner und Kinder der Kinder
    val nachfahren by produceState<Map<String, DescendantNode>>(emptyMap(), root, tree, neu) {
        if (root == null || tree == null) { value = emptyMap(); return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching { viewModel.client.descendants(tree, root, 3).tree.families.flatMap { it.children }.associateBy { it.person.xref } }.getOrNull().orEmpty()
        }
    }

    // Tastatur: hoch Vater (mit Umschalt Mutter), runter das erste Kind, links/rechts die Geschwister, Tab der naechste Reiter
    fun gehe(r: Richtung): Boolean { p?.let { zielPerson(it, familie, r) }?.let { viewModel.setRoot(it) }; return true }
    val tasten = Modifier.tastenBereich(root) { e ->
        if (e.type != KeyEventType.KeyDown || e.isAltPressed || e.isCtrlPressed) false else when (e.key) {
            Key.DirectionUp -> gehe(if (e.isShiftPressed) Richtung.Mutter else Richtung.Vater)
            Key.DirectionDown -> gehe(Richtung.Kind)
            Key.DirectionLeft -> gehe(Richtung.GeschwisterZurueck)
            Key.DirectionRight -> gehe(Richtung.GeschwisterVor)
            Key.Tab -> { if (familien.size > 1) reiter = (reiter + 1) % familien.size; familien.size > 1 }
            else -> false
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(tasten)) {
        val fehler = proband?.exceptionOrNull()
        when {
            root == null -> {}
            fehler != null -> Text(fehler.message ?: "?", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
            p == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> Familie(state, viewModel, openWeb, openSheet, onSuche, PaarDaten(p, partner?.takeIf { it.person.xref == partnerXref }, ahnen, nachfahren),
                familien, familie, reiter, onReiter = { reiter = it }, generationen = generationen,
                onGenerationen = { generationen = it.coerceIn(GEN_MIN, GEN_MAX); DeskLayout.prefs.putString("familie_gen", generationen.toString()) })
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Familie(
    state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, openSheet: ((String) -> Unit)?, onSuche: (() -> Unit)?, daten: PaarDaten,
    familien: List<FamilyJson>, familie: FamilyJson?, reiter: Int, onReiter: (Int) -> Unit, generationen: Int, onGenerationen: (Int) -> Unit,
) {
    val p = daten.proband.person
    val canEdit = state.tree?.canEdit == true
    // Mann links, Frau rechts; ohne Familie steht der Proband auf seiner Seite allein
    val mann = familie?.husband ?: p.takeIf { it.sex != "F" }
    val frau = familie?.wife ?: p.takeIf { it.sex == "F" }
    fun elternVon(x: Person?): FamilyJson? = when (x?.xref) {
        null -> null
        p.xref -> daten.proband.parentFamilies.firstOrNull()
        daten.partner?.person?.xref -> daten.partner.parentFamilies.firstOrNull()
        else -> null
    }
    val karte = KartenAktionen(state, viewModel, openWeb, openSheet, canEdit)
    val scroll = rememberScrollState()
    val farben = MaterialTheme.colorScheme
    val kinder = familie?.children.orEmpty()

    // Zoom: null = einpassen (Inhalt fuellt die Fensterhoehe, hoechstens 1,8-fach), sonst vom Benutzer per Strg+Mausrad
    var zoomWahl by remember { mutableStateOf<Float?>(null) }
    val zoomAkt = remember { mutableStateOf(1f) }
    Column(Modifier.fillMaxSize()) {
        // ── Kopfband: das Paar mit Namen und Kinderzahl ──
        val kinderZahl = familie?.children?.size ?: 0
        val titel = listOfNotNull(mann?.let { if (it.isPrivate) stringResource(Res.string.person_private) else klarName(it) }, frau?.let { if (it.isPrivate) stringResource(Res.string.person_private) else klarName(it) })
            .joinToString(" & ").ifBlank { klarName(p) }
        Row(Modifier.fillMaxWidth().background(farben.primaryContainer).padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = farben.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (familie != null) Text(stringResource(Res.string.desk_family_children, kinderZahl), style = MaterialTheme.typography.bodyMedium, color = farben.onPrimaryContainer)
        }
        // ── Zweite Leiste: Person hinzufuegen, Gehe zu, Generationen, Zoom ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (canEdit) {
                var offen by remember { mutableStateOf(false) }
                val eltern = daten.proband.parentFamilies.firstOrNull()
                Box {
                    TextButton(onClick = { offen = true }) { Text("+ " + stringResource(Res.string.desk_add_person) + " ▾") }
                    androidx.compose.material3.DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                        @Composable fun eintrag(text: StringResourceRef, vorwahl: String, fam: String? = null) = androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(text)) },
                            onClick = { offen = false; Verwandtenwahl.vorwahl = vorwahl; Verwandtenwahl.familie = fam; viewModel.requestAddRelative(p.xref) })
                        if (eltern?.husband == null) eintrag(Res.string.rel_father, "father")
                        if (eltern?.wife == null) eintrag(Res.string.rel_mother, "mother")
                        eintrag(Res.string.rel_partner, "spouse")
                        eintrag(Res.string.rel_child, "child", familie?.xref)
                        HorizontalDivider()
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(Res.string.desk_link_existing)) }, onClick = { offen = false; Anfuegewahl.person = p })
                    }
                }
            }
            onSuche?.let { TextButton(onClick = it) { Text(stringResource(Res.string.desk_goto) + " …") } }
            Spacer(Modifier.weight(1f))
            Text("${(zoomAkt.value * 100).roundToInt()} %", Modifier.clickable { zoomWahl = null }, style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            var genOffen by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { genOffen = true }) { Text(stringResource(Res.string.tree_generations, generationen) + " ▾") }
                androidx.compose.material3.DropdownMenu(expanded = genOffen, onDismissRequest = { genOffen = false }) {
                    (GEN_MIN..GEN_MAX).forEach { g -> androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(Res.string.tree_generations, g)) }, onClick = { genOffen = false; onGenerationen(g) }) }
                }
            }
        }
        val zoomJetzt by androidx.compose.runtime.rememberUpdatedState(zoomAkt.value)
        Box(Modifier.fillMaxSize().onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
            val c = e.changes.firstOrNull() ?: return@onPointerEvent
            if (!e.keyboardModifiers.isCtrlPressed || c.scrollDelta.y == 0f) return@onPointerEvent
            zoomWahl = (zoomJetzt * 1.1f.pow(-c.scrollDelta.y)).coerceIn(ZOOM_MIN, ZOOM_MAX)
            c.consume()
        }) {
          androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
          val fensterHoehe = maxHeight
          Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
          Eingepasst(zoomWahl, fensterHoehe, onZoom = { zoomAkt.value = it }) { gesamtBreite ->
          Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
          Column {

            // ── Vorfahren ab den Grosseltern: oben die aelteste Generation, je Seite 2^(g-1) Plaetze ──
            for (g in generationen downTo 3) {
                val von = 1 shl (g - 1); val bis = (1 shl g) - 1
                val zeile = listOf(mann, frau).map { s -> s?.let { daten.ahnen[it.xref] }.orEmpty().filter { it.n in von..bis } }
                if (zeile.all { it.isEmpty() }) continue
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    zeile.forEach { seite ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (n in von..bis) {
                                    val a = seite.firstOrNull { it.n == n }
                                    Box(Modifier.weight(1f)) { if (a != null) AhnKarte(a.person, g, karte) else Spacer(Modifier.fillMaxWidth()) }
                                }
                            }
                            // Jedes Elternpaar dieser Reihe laeuft in einer Linie zu seinem Kind in der Reihe darunter zusammen
                            if (seite.isNotEmpty()) Verbindungen(bis - von + 1, seite.map { it.n - von }.toSet())
                        }
                    }
                }
            }

            // ── Eltern beider Partner ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                listOf(mann, frau).forEach { kind ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (kind != null && !kind.isPrivate) {
                            val eltern = elternVon(kind)
                            Text(stringResource(Res.string.desk_family_parents_of, klarName(kind)), style = MaterialTheme.typography.labelMedium,
                                color = farben.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { PersonKarte(eltern?.husband, stringResource(Res.string.desk_unknown_father), karte, kind.xref, "father") }
                                Box(Modifier.weight(1f)) { PersonKarte(eltern?.wife, stringResource(Res.string.desk_unknown_mother), karte, kind.xref, "mother") }
                            }
                            Verbindungen(2, setOfNotNull(eltern?.husband?.let { 0 }, eltern?.wife?.let { 1 }), hoehe = 22.dp)
                        }
                    }
                }
            }

            // ── Geschwister des Probanden (Halbgeschwister mit ½) ──
            val voll = daten.proband.parentFamilies.flatMap { it.children }.filter { it.xref != p.xref }.distinctBy { it.xref }
            val halb = daten.proband.halfSiblings().map { it.person }.filter { h -> voll.none { it.xref == h.xref } }
            if (voll.isNotEmpty() || halb.isNotEmpty()) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.desk_family_siblings_of, klarName(p)), style = MaterialTheme.typography.labelMedium,
                        color = farben.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp, end = 4.dp))
                    (voll.map { it to false } + halb.map { it to true }).forEach { (g, istHalb) -> GeschwisterChip(g, istHalb, karte) }
                }
            }

            // ── Reiter: eine Ehe je Reiter ──
            if (familien.size > 1) {
                PrimaryScrollableTabRow(selectedTabIndex = reiter.coerceAtMost(familien.lastIndex), edgePadding = 0.dp, containerColor = farben.background) {
                    familien.forEachIndexed { i, f ->
                        val ander = f.spouse ?: listOfNotNull(f.husband, f.wife).firstOrNull { it.xref != p.xref }
                        val jahr = f.marriage?.date?.year?.takeIf { it > 0 }?.let { " oo $it" }.orEmpty()
                        Tab(selected = i == reiter, onClick = { onReiter(i) }, text = {
                            Text((ander?.let { if (it.isPrivate) stringResource(Res.string.person_private) else klarName(it) } ?: unbekannterPartner(p.sex)) + jahr, maxLines = 1)
                        })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

          }
          Column {
            // ── Das Paar als Karten ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { PaarKarte(mann, unbekannterPartner("F"), karte, if (familie == null) p.xref else null) }
                Box(Modifier.width(56.dp).heightIn(min = 170.dp), contentAlignment = Alignment.Center) {
                    Text(if (familie != null) "⚭" else "", style = MaterialTheme.typography.headlineSmall, color = farben.onSurfaceVariant)
                }
                Box(Modifier.weight(1f)) { PaarKarte(frau, unbekannterPartner("M"), karte, if (familie == null) p.xref else null) }
            }

            // ── Statuszeile der Partnerschaft: Heiraten, Zahl der Kinder, Kind hinzufuegen ──
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().background(farben.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    if (familie == null) Text(stringResource(Res.string.desk_family_no_partner), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                    else {
                        // Alle Heiraten der Familie (standesamtlich und kirchlich), nicht nur die erste; ohne MARR-Fakt die Kurzform
                        val heiraten = familie.facts.filter { it.tag == "MARR" }
                        if (heiraten.isEmpty()) {
                            val datum = familie.marriage?.date?.text.orEmpty(); val ort = Ortsanzeige.text(familie.marriage?.place?.name)
                            Text(if (datum.isBlank() && ort.isBlank()) stringResource(Res.string.desk_family_status_none)
                                else stringResource(Res.string.desk_family_status_married) + ": " + listOf(datum, ort).filter(String::isNotBlank).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall)
                        } else heiraten.forEach { h ->
                            val datum = h.date?.text.orEmpty(); val ort = Ortsanzeige.text(h.place?.name)
                            Text(faktLabelMitArt(h) + ": " + listOf(datum, ort).filter(String::isNotBlank).joinToString(", ").ifBlank { "–" },
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(stringResource(Res.string.desk_family_children, kinder.size), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                if (canEdit) {
                    // + Sohn / + Tochter: Kind dieser Partnerschaft mit vorbelegtem Geschlecht
                    TextButton(onClick = { Verwandtenwahl.vorwahl = "child"; Verwandtenwahl.familie = familie?.xref; Verwandtenwahl.geschlecht = "M"; viewModel.requestAddRelative(p.xref) }, modifier = Modifier.height(30.dp)) {
                        Text("+ " + stringResource(Res.string.rel_son), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { Verwandtenwahl.vorwahl = "child"; Verwandtenwahl.familie = familie?.xref; Verwandtenwahl.geschlecht = "F"; viewModel.requestAddRelative(p.xref) }, modifier = Modifier.height(30.dp)) {
                        Text("+ " + stringResource(Res.string.rel_daughter), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── Trauzeugen der Heiraten (ab API-Stufe 19), verlinkte anklickbar ──
            familie?.facts?.filter { it.tag == "MARR" && it.hatPaten }?.forEach { h ->
                PatenZeilen(h, onPerson = viewModel::select, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }

          }
          Column {
            // ── Kinder mit ihren Partnern als Karten nebeneinander, darunter die Enkel als eigene Reihe ──
            Spacer(Modifier.height(12.dp))
            if (kinder.isEmpty()) {
                Text(stringResource(Res.string.desk_family_no_children), style = MaterialTheme.typography.bodyMedium,
                    color = farben.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                val waagrecht = rememberScrollState()
                // So breit, dass alle Kinder nebeneinander passen; unter 250 dp je Kind wird waagrecht gerollt
                val breite = ((gesamtBreite - 48.dp - 14.dp * (kinder.size - 1)) / kinder.size).coerceAtLeast(250.dp)
                Column(Modifier.fillMaxWidth().horizontalScroll(waagrecht)) {
                    // Linie vom Paar herunter und ueber alle Kinder hinweg
                    val gesamt = breite * kinder.size + 14.dp * (kinder.size - 1)
                    KinderLinien(kinder.size, breite, 14.dp, mitte = minOf(gesamt / 2, (gesamtBreite - 48.dp) / 2))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        kinder.forEach { k -> KindFamilie(k, daten.nachfahren[k.xref], karte, breite) }
                    }
                }
            }
          }
          }
          }
          }
          SenkrechteLeiste(scroll)
          }
        }
    }
}

private const val ZOOM_MIN = 0.6f
private const val ZOOM_MAX = 1.8f

/**
 * Misst den Inhalt in voller Breite und skaliert ihn, bis er die Fensterhoehe fuellt (hoechstens [ZOOM_MAX]), oder auf den
 * vom Benutzer gewaehlten Faktor. Der Inhalt bekommt die durch den Faktor geteilte Breite, damit er skaliert wieder die
 * ganze Breite einnimmt; Rollen passt sich der skalierten Hoehe an.
 */
@Composable
private fun Eingepasst(zoomWahl: Float?, fenster: Dp, onZoom: (Float) -> Unit, inhalt: @Composable (breite: Dp) -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val fensterHoehe = fenster.roundToPx().takeIf { it > 0 }
        val breite = constraints.maxWidth
        // Erst messen, wie hoch der Inhalt ohne Zoom ist
        val mess = subcompose("mess") { inhalt(breite.toDp()) }.first().measure(Constraints(minWidth = breite, maxWidth = breite))
        val zoom = zoomWahl ?: if (fensterHoehe == null || mess.height == 0) 1f else (fensterHoehe.toFloat() / mess.height).coerceIn(1f, ZOOM_MAX)
        onZoom(zoom)
        val innen = (breite / zoom).roundToInt()
        val p = subcompose("inhalt") { inhalt(innen.toDp()) }.first().measure(Constraints(minWidth = innen, maxWidth = innen))
        layout(breite, (p.height * zoom).roundToInt()) {
            p.placeWithLayer(0, 0) { scaleX = zoom; scaleY = zoom; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
        }
    }
}

/**
 * Verbindungslinien unter einer Ahnenreihe mit [n] gleich breiten Plaetzen: Platz 2k und 2k+1 sind ein Elternpaar und
 * laufen zur Mitte zusammen, von dort geht es zum Kind in der Reihe darunter (dessen Platz liegt genau unter der Mitte).
 */
@Composable
private fun Verbindungen(n: Int, vorhanden: Set<Int>, hoehe: Dp = 18.dp) {
    val farbe = treeColors.connector
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(hoehe)) {
        val w = size.width; val h = size.height; val strich = 1.5.dp.toPx()
        for (k in 0 until n / 2) {
            val a = 2 * k; val b = 2 * k + 1
            if (a !in vorhanden && b !in vorhanden) continue
            val xa = w * (a + 0.5f) / n; val xb = w * (b + 0.5f) / n; val xm = (xa + xb) / 2
            if (a in vorhanden) drawLine(farbe, androidx.compose.ui.geometry.Offset(xa, 0f), androidx.compose.ui.geometry.Offset(xa, h / 2), strich)
            if (b in vorhanden) drawLine(farbe, androidx.compose.ui.geometry.Offset(xb, 0f), androidx.compose.ui.geometry.Offset(xb, h / 2), strich)
            drawLine(farbe, androidx.compose.ui.geometry.Offset(if (a in vorhanden) xa else xm, h / 2), androidx.compose.ui.geometry.Offset(if (b in vorhanden) xb else xm, h / 2), strich)
            drawLine(farbe, androidx.compose.ui.geometry.Offset(xm, h / 2), androidx.compose.ui.geometry.Offset(xm, h), strich)
        }
    }
}

/** Vom Paar eine Linie herunter, waagrecht ueber alle Kinder, je Kind ein Abzweig nach unten. */
@Composable
private fun KinderLinien(n: Int, breite: Dp, abstand: Dp, mitte: Dp) {
    val farbe = treeColors.connector
    androidx.compose.foundation.Canvas(Modifier.width(breite * n + abstand * (n - 1)).height(20.dp)) {
        val h = size.height; val strich = 1.5.dp.toPx()
        val xs = (0 until n).map { i -> (breite * i + abstand * i + breite / 2).toPx() }
        val xm = mitte.toPx()
        drawLine(farbe, androidx.compose.ui.geometry.Offset(xm, 0f), androidx.compose.ui.geometry.Offset(xm, h / 2), strich)
        drawLine(farbe, androidx.compose.ui.geometry.Offset(minOf(xs.first(), xm), h / 2), androidx.compose.ui.geometry.Offset(maxOf(xs.last(), xm), h / 2), strich)
        xs.forEach { x -> drawLine(farbe, androidx.compose.ui.geometry.Offset(x, h / 2), androidx.compose.ui.geometry.Offset(x, h), strich) }
    }
}

/** Was eine Karte beim Klicken, Doppelklicken und im Kontextmenue tut. */
private class KartenAktionen(val state: UiState, val viewModel: AppViewModel, val openWeb: (String) -> Unit, val openSheet: ((String) -> Unit)?, val canEdit: Boolean)

@Composable
private fun Linie() {
    Box(Modifier.padding(top = 4.dp, bottom = 4.dp).width(2.dp).height(12.dp).background(treeColors.connector))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Anklickbar(person: Person, a: KartenAktionen, inhalt: @Composable (Modifier) -> Unit) {
    ContextMenuArea(items = {
        buildList {
            add(ContextMenuItem(Texte.t(Res.string.action_make_root)) { a.viewModel.setRoot(person.xref) })
            a.openSheet?.let { s -> add(ContextMenuItem(Texte.t(Res.string.desk_sheet)) { s(person.xref) }) }
            if (a.canEdit) add(ContextMenuItem(Texte.t(Res.string.action_add_relative)) { a.viewModel.requestAddRelative(person.xref) })
            if (a.canEdit) add(ContextMenuItem(Texte.t(Res.string.desk_link_existing)) { Anfuegewahl.person = person })
            add(ContextMenuItem(Texte.t(Res.string.chip_open_web)) { a.openWeb(person.url) })
            add(ContextMenuItem(Texte.t(Res.string.desk_copy_text)) { personentextKopieren(a.viewModel, person.xref) })
            add(ContextMenuItem(Texte.t(Res.string.desk_meta_menu)) { MetaSuche.fuer(a.viewModel, person.xref) })
            if (a.canEdit) add(ContextMenuItem(Texte.t(Res.string.action_delete_person)) { Loeschwahl.person = person.xref to person.name })
        }
    }) {
        inhalt(Modifier.fokusRahmen().combinedClickable(onDoubleClick = { a.viewModel.setRoot(person.xref) }) { a.viewModel.select(person.xref) })
    }
}

/** Rahmen einer Personenkarte: Geschlechtsfarbe, Proband dicker, gewaehlte Person in der Hauptfarbe. */
@Composable
private fun Modifier.kartenRahmen(person: Person, a: KartenAktionen, shape: androidx.compose.ui.graphics.Shape, dick: Boolean = true): Modifier {
    val proband = person.xref == a.state.root
    val gewaehlt = person.xref == a.state.selected
    return background(MaterialTheme.colorScheme.surface, shape)
        .border(if (gewaehlt) 2.5.dp else if (proband && dick) 2.dp else 1.dp, if (gewaehlt) MaterialTheme.colorScheme.primary else treeColors.forSex(person.sex), shape)
}

/** Ein Vorfahr ab der Grosselterngeneration: je hoeher, desto knapper (Urgrosseltern nur Name und Jahre). */
@Composable
private fun AhnKarte(person: Person, generation: Int, a: KartenAktionen) {
    val shape = MaterialTheme.shapes.small
    val knapp = generation >= 4
    Anklickbar(person, a) { klick ->
        Row(Modifier.fillMaxWidth().height(if (knapp) 40.dp else 52.dp).kartenRahmen(person, a, shape, dick = false).then(klick).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (!knapp) { Avatar(person, 32.dp); Spacer(Modifier.width(6.dp)) }
            Column(Modifier.weight(1f)) {
                Text(if (person.isPrivate) stringResource(Res.string.person_private) else klarName(person), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!person.isPrivate && jahre(person).isNotEmpty()) Text(jahre(person), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * Ein Elternteil als kleine Karte. Fehlt er, steht dort [leer] ("Vater unbekannt") - mit Bearbeitungsrecht und
 * [ergaenzenFuer] als Knopf, der Verwandte hinzufuegen oeffnet.
 */
@Composable
private fun PersonKarte(person: Person?, leer: String, a: KartenAktionen, ergaenzenFuer: String?, beziehung: String? = null) {
    val shape = MaterialTheme.shapes.medium
    val hoehe: Dp = 64.dp
    if (person == null) {
        LeereKarte(hoehe, leer, a, ergaenzenFuer, beziehung)
        return
    }
    Anklickbar(person, a) { klick ->
        Row(Modifier.fillMaxWidth().height(hoehe).kartenRahmen(person, a, shape).then(klick).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(person, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (person.isPrivate) stringResource(Res.string.person_private) else person.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!person.isPrivate && jahre(person).isNotEmpty()) Text(jahre(person), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LeereKarte(hoehe: Dp, leer: String, a: KartenAktionen, ergaenzenFuer: String?, beziehung: String?) {
    Box(Modifier.fillMaxWidth().height(hoehe).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(leer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.canEdit && ergaenzenFuer != null) TextButton(onClick = { Verwandtenwahl.vorwahl = beziehung; a.viewModel.requestAddRelative(ergaenzenFuer) }, modifier = Modifier.height(30.dp)) {
                Text("+ " + stringResource(Res.string.desk_family_add), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Ein Partner des Paars als grosse Karte: Portraet, Name, Zeilen Geboren / Gestorben / Beruf mit Beschriftung, darunter
 * die Knoepfe Personenblatt und Fotos. Fehlt er, der leere Kasten mit "hinzufuegen".
 */
@Composable
private fun PaarKarte(person: Person?, leer: String, a: KartenAktionen, ergaenzenFuer: String?) {
    val shape = MaterialTheme.shapes.medium
    if (person == null) { LeereKarte(170.dp, leer, a, ergaenzenFuer, "spouse"); return }
    val farben = MaterialTheme.colorScheme
    val proband = person.xref == a.state.root
    Anklickbar(person, a) { klick ->
        Column(Modifier.fillMaxWidth().heightIn(min = 170.dp).kartenRahmen(person, a, shape).then(klick).padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Avatar(person, 96.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (person.isPrivate) stringResource(Res.string.person_private) else person.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (proband) FontWeight.Bold else FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!person.isPrivate) {
                        Spacer(Modifier.height(4.dp))
                        DatenZeile(stringResource(Res.string.desk_family_born), person.birth ?: person.chr)
                        DatenZeile(stringResource(Res.string.desk_family_died), person.death ?: person.buri)
                        person.occupation?.takeIf { it.isNotBlank() }?.let { DatenZeile(stringResource(Res.string.desk_list_fact_occupation), it) }
                    }
                }
            }
            if (!person.isPrivate) {
                Spacer(Modifier.weight(1f))
                HorizontalDivider(Modifier.padding(top = 6.dp), color = farben.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    a.openSheet?.let { s -> TextButton(onClick = { s(person.xref) }, modifier = Modifier.height(30.dp)) { Text(stringResource(Res.string.desk_sheet), style = MaterialTheme.typography.labelMedium) } }
                    TextButton(onClick = { a.viewModel.select(person.xref); a.viewModel.setSection(Section.Photos) }, modifier = Modifier.height(30.dp)) {
                        Text(stringResource(Res.string.nav_photos), style = MaterialTheme.typography.labelMedium)
                    }
                    if (a.canEdit) TextButton(onClick = { a.viewModel.requestAddRelative(person.xref) }, modifier = Modifier.height(30.dp)) {
                        Text(stringResource(Res.string.action_add_relative), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DatenZeile(beschriftung: String, e: EventJson?) {
    val datum = e?.date?.text.orEmpty(); val ort = Ortsanzeige.text(e?.place?.name)
    if (datum.isBlank() && ort.isBlank()) return
    DatenZeile(beschriftung, listOf(datum, ort).filter(String::isNotBlank).joinToString(", "))
}

@Composable
private fun DatenZeile(beschriftung: String, wert: String) {
    Row {
        Text(beschriftung, Modifier.width(78.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Ein Geschwister als kleiner Reiter: Name und Jahre, Halbgeschwister mit ½. */
@Composable
private fun GeschwisterChip(g: Person, halb: Boolean, a: KartenAktionen) {
    val text = (if (g.isPrivate) stringResource(Res.string.person_private) else klarName(g)) + (if (halb) " ½" else "") + jahre(g).let { if (it.isNotEmpty()) "  $it" else "" }
    if (g.isPrivate) {
        Text(text, Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Anklickbar(g, a) { klick ->
        Text(text, Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(1.dp, if (g.xref == a.state.selected) MaterialTheme.colorScheme.primary else treeColors.forSex(g.sex).copy(alpha = 0.6f), MaterialTheme.shapes.small)
            .then(klick).padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Ein Kind mit seiner Familie: das Kind und sein Partner als Karten nebeneinander (⚭ mit Jahr dazwischen), weitere
 * Partner als Zeilen, darunter die Enkel als kleine Kaesten. Ohne Nachfahrendaten nur die Ehen aus der Personenabfrage.
 * Doppelklick auf das Kind fuehrt in seine Familie.
 */
@Composable
private fun KindFamilie(k: Person, node: DescendantNode?, a: KartenAktionen, breite: Dp) {
    val farben = MaterialTheme.colorScheme
    val familien = node?.families.orEmpty()
    val erste = familien.firstOrNull()
    // Kind und Partner nebeneinander, wenn Platz ist, sonst untereinander (viele Kinder)
    val nebeneinander = breite >= 460.dp
    Column(Modifier.width(breite), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable
        fun partnerKarte(f: de.bgghome.webtrees.nativ.api.DescendantFamily) {
            val sp = f.spouse
            if (sp != null && !sp.isPrivate) KindKarte(sp, a)
            else Box(Modifier.fillMaxWidth().height(88.dp).border(1.dp, farben.outlineVariant, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                Text(if (sp?.isPrivate == true) stringResource(Res.string.person_private) else unbekannterPartner(k.sex), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            }
        }
        @Composable
        fun ringe(f: de.bgghome.webtrees.nativ.api.DescendantFamily, senkrecht: Boolean) {
            val jahr = f.marriage?.date?.year?.takeIf { it > 0 }?.toString().orEmpty()
            if (senkrecht) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("⚭ $jahr".trim(), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
            } else Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚭", style = MaterialTheme.typography.titleMedium, color = farben.onSurfaceVariant)
                if (jahr.isNotEmpty()) Text(jahr, style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { KindKarte(k, a) }
            if (erste != null && nebeneinander) {
                ringe(erste, false)
                Box(Modifier.weight(1f)) { partnerKarte(erste) }
            } else if (erste == null && k.marriages.isNotEmpty()) {
                // Nachfahrendaten fehlen (noch): die Ehen als Text
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    k.marriages.forEach { m -> Text("⚭ " + m.spouse.ifBlank { "?" } + (m.date?.year?.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()),
                        style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        if (erste != null && !nebeneinander) { ringe(erste, true); partnerKarte(erste) }
        // Weitere Partner als Zeilen
        familien.drop(1).forEach { f ->
            val sp = f.spouse
            val jahr = f.marriage?.date?.year?.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
            if (sp != null && !sp.isPrivate) Anklickbar(sp, a) { klick ->
                Text("⚭ " + klarName(sp) + jahr, Modifier.then(klick).padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Text("⚭ " + (if (sp?.isPrivate == true) stringResource(Res.string.person_private) else "?") + jahr, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        }
        // Enkel: eine Reihe kleiner Kaesten unter dem Paar
        val enkel = familien.flatMap { it.children }.map { it.person }.distinctBy { it.xref }
        if (enkel.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Linie() }
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            val spalten = if (nebeneinander) 3 else 2
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = spalten) {
                enkel.forEach { e -> Box(Modifier.width((breite - 6.dp * (spalten - 1)) / spalten)) { AhnKarte(e, 3, a) } }
            }
        }
    }
}

/** Ein Kind oder sein Partner als Karte: Bild, Name, Geburt und Tod. */
@Composable
private fun KindKarte(k: Person, a: KartenAktionen) {
    val shape = MaterialTheme.shapes.small
    Anklickbar(k, a) { klick ->
        Column(Modifier.fillMaxWidth().height(88.dp).kartenRahmen(k, a, shape, dick = false).then(klick).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(k, 36.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (k.isPrivate) stringResource(Res.string.person_private) else k.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!k.isPrivate) {
                Spacer(Modifier.height(3.dp))
                Text(ereignisZeile("*", k.birth ?: k.chr) ?: " ", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ereignisZeile("†", k.death ?: k.buri) ?: " ", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
