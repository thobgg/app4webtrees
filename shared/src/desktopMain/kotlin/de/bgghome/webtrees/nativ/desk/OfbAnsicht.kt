package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.hat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.API_LOC_HIERARCHY
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.select
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Ortsfamilienbuch als Aufbau des Hauptfensters: links der Ortsfilter und die nummerierten Familien, in der Mitte das
 * Buch - dieselben Eintraege wie im gedruckten Familienbuch (BuchFamilien.kt), nur lebend: Verweise <7>, [12] und H3
 * springen, Namen oeffnen das Personenblatt, nach dem Speichern baut sich alles neu. Rechts die Haeuser des Orts.
 */

/** Ein Eintrag des Buchs: die Nummer (Anker "n7" bzw. "h3") und seine Bloecke. */
internal class OfbEintrag(val anker: String, val marke: String, val titel: String, val bloecke: List<Block>)

internal class OfbInhalt(val titel: String, val familien: List<OfbEintrag>, val haeuser: List<OfbEintrag>, val gruppen: Map<String, String>)

/** Das Buch in Eintraege zerlegen: Familien (Anker n…) und Haeuser (Anker h…); Zwischenueberschriften je Familie merken. */
internal fun ofbInhalt(buch: Buch): OfbInhalt {
    val familien = ArrayList<OfbEintrag>(); val haeuser = ArrayList<OfbEintrag>()
    val gruppen = HashMap<String, String>()
    var liste: ArrayList<OfbEintrag>? = null
    var aktuell: ArrayList<Block>? = null; var anker = ""; var marke = ""; var titel = ""
    var gruppe = ""
    fun abschliessen() { aktuell?.let { b -> liste?.add(OfbEintrag(anker, marke, titel, b)) }; aktuell = null }
    buch.bloecke.forEach { b ->
        when (b) {
            is Ueberschrift -> {
                abschliessen()
                when {
                    b.id == "familien" -> liste = familien
                    b.id == "haeuser" || b.id == "weitere" -> liste = haeuser
                    b.id.startsWith("reg-") || b.id == "vorwort" -> liste = null
                    !b.neueSeite -> gruppe = b.text   // Buchstabe, Jahrhundert, Ortsteil
                }
            }
            is Absatz -> {
                val a = b.anker
                if (a != null && liste != null && (a.startsWith("n") || a.startsWith("h") || a.startsWith("w"))) {
                    abschliessen()
                    anker = a; marke = b.marke.orEmpty(); titel = b.laeufe.firstOrNull()?.text.orEmpty()
                    aktuell = arrayListOf(b)
                    if (gruppe.isNotEmpty()) gruppen[a] = gruppe
                } else aktuell?.add(b)
            }
            else -> {}
        }
    }
    abschliessen()
    return OfbInhalt(buch.titel, familien, haeuser, gruppen)
}

@Composable
fun DeskOfb(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, openSheet: ((String) -> Unit)? = null) {
    val tree = state.tree?.name
    val prefs = DeskLayout.prefs
    var ort by remember(tree) { mutableStateOf(prefs.getString("ofb_ort_$tree", null).orEmpty()) }
    var eingabe by remember(tree) { mutableStateOf(ort) }
    var chrono by remember { mutableStateOf(prefs.getBoolean("ofb_chrono", false)) }
    // Am Bildschirm kurz: Quellen und Paten erst auf Wunsch (das gedruckte Buch hat sie immer)
    var quellen by remember { mutableStateOf(prefs.getBoolean("ofb_quellen", false)) }
    var paten by remember { mutableStateOf(prefs.getBoolean("ofb_paten", false)) }
    var neu by remember { mutableIntStateOf(0) }
    var warBeschaeftigt by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy) { if (warBeschaeftigt && !state.busy) neu++; warBeschaeftigt = state.busy }
    var fortschritt by remember { mutableStateOf("") }
    val appName = de.bgghome.webtrees.nativ.ui.LocalAppName.current
    val inhalt by produceState<Result<OfbInhalt>?>(null, tree, ort, chrono, quellen, paten, neu) {
        value = null
        if (tree == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val haeuser = state.info.hat(API_LOC_HIERARCHY)
                val d = familienbuchLaden(viewModel.client, tree, bilder = false, haeuser = haeuser, fortschritt = { fortschritt = it })
                OrtsKurznamen.laden(viewModel.client, tree)
                val o = BuchOptionen(bilder = false, ortFilter = ort, familienChronologisch = chrono, haeuser = haeuser, quellen = quellen, paten = paten)
                ofbInhalt(familienbuch(d, o, state.tree?.title.orEmpty(), appName))
            }
        }
    }
    val farben = MaterialTheme.colorScheme
    val mitte = rememberLazyListState()
    val rechts = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var gewaehlt by remember { mutableStateOf<String?>(null) }
    var hausGewaehlt by remember { mutableStateOf<String?>(null) }
    val i = inhalt?.getOrNull()
    // Sprung zu einem Anker: Familie in der Mitte, Haus rechts
    fun springe(ziel: String) {
        val f = i?.familien?.indexOfFirst { it.anker == ziel } ?: -1
        if (f >= 0) { gewaehlt = ziel; scope.launch { mitte.animateScrollToItem(f) }; return }
        val h = i?.haeuser?.indexOfFirst { it.anker == ziel } ?: -1
        if (h >= 0) { hausGewaehlt = ziel; scope.launch { rechts.animateScrollToItem(h) } }
    }
    // Klick auf einen Namen zeigt die Person rechts in der Tafel, ein zweiter Klick kurz danach oeffnet das Personenblatt
    var letzterKlick by remember { mutableStateOf("" to 0L) }
    val personWaehlen: (String) -> Unit = { xref ->
        val jetzt = System.currentTimeMillis()
        if (letzterKlick.first == xref && jetzt - letzterKlick.second < 450) { openSheet?.invoke(xref); letzterKlick = "" to 0L }
        else { viewModel.select(xref); letzterKlick = xref to jetzt }
    }
    val listeBreite = rememberBreite("ofb_liste", 300f)
    val hausBreite = rememberBreite("ofb_haeuser", 340f)

    Row(Modifier.fillMaxSize().background(farben.background)) {
        // ── Links: Ort, Sortierung, Familienliste ──
        Column(Modifier.width(listeBreite.value.dp).fillMaxHeight().background(farben.surface)) {
            // Der Filter folgt dem Tippen (kurze Pause), kein Enter, kein Knopf
            LaunchedEffect(eingabe) { kotlinx.coroutines.delay(400); val neuOrt = eingabe.trim(); if (neuOrt != ort) { ort = neuOrt; prefs.putString("ofb_ort_$tree", ort) } }
            OutlinedTextField(eingabe, { eingabe = it }, label = { Text(stringResource(Res.string.desk_book_place_filter)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Haken(stringResource(Res.string.desk_book_sources), quellen) { quellen = it; prefs.putBoolean("ofb_quellen", it) }
                Haken(stringResource(Res.string.desk_col_godparents), paten) { paten = it; prefs.putBoolean("ofb_paten", it) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { chrono = !chrono; prefs.putBoolean("ofb_chrono", chrono) }) {
                    Text(stringResource(if (chrono) Res.string.desk_book_sort_chrono else Res.string.desk_book_sort_alpha), style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(color = farben.outlineVariant)
            when {
                inhalt == null -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Text(fortschritt, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant) }
                i == null -> Text((inhalt?.exceptionOrNull() as? Exception)?.let { de.bgghome.webtrees.nativ.ui.explain(it) } ?: "?", Modifier.padding(12.dp), color = farben.error)
                i.familien.isEmpty() -> Text(stringResource(Res.string.desk_ofb_empty), Modifier.padding(12.dp), color = farben.onSurfaceVariant)
                else -> {
                    Text(stringResource(Res.string.desk_book_families) + " (${i.familien.size})", Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
                    val ls = rememberLazyListState()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(state = ls) {
                            itemsIndexed(i.familien, key = { _, e -> e.anker }) { idx, e ->
                                val vorher = i.familien.getOrNull(idx - 1)?.let { i.gruppen[it.anker] }
                                i.gruppen[e.anker]?.takeIf { it != vorher }?.let { g ->
                                    Text(g, Modifier.fillMaxWidth().background(farben.surfaceVariant).padding(horizontal = 12.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                                }
                                Row(Modifier.fillMaxWidth().background(if (gewaehlt == e.anker) farben.secondaryContainer else farben.surface)
                                    .clickable { springe(e.anker) }.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text(e.marke, Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                                    Text(e.titel, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                }
                            }
                        }
                        ListenLeiste(ls)
                    }
                }
            }
        }
        Trenner(listeBreite, "ofb_liste", 200f, 520f)
        // ── Mitte: das Buch ──
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (i != null) {
                LazyColumn(state = mitte, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
                    itemsIndexed(i.familien, key = { _, e -> e.anker }) { idx, e ->
                        val vorher = i.familien.getOrNull(idx - 1)?.let { i.gruppen[it.anker] }
                        i.gruppen[e.anker]?.takeIf { it != vorher }?.let { g -> Text(g, Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        OfbEintragAnsicht(e, gewaehlt == e.anker, ::springe, personWaehlen)
                    }
                }
                ListenLeiste(mitte)
            }
        }
        // ── Rechts: Haeuser des Orts ──
        if (i != null && i.haeuser.isNotEmpty()) {
            Trenner(hausBreite, "ofb_haeuser", 240f, 600f, rechts = true)
            Column(Modifier.width(hausBreite.value.dp).fillMaxHeight().background(farben.surface)) {
                Text(stringResource(Res.string.desk_book_houses) + " (${i.haeuser.size})", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(color = farben.outlineVariant)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = rechts, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        itemsIndexed(i.haeuser, key = { _, e -> e.anker }) { _, e -> OfbEintragAnsicht(e, hausGewaehlt == e.anker, ::springe, personWaehlen) }
                    }
                    ListenLeiste(rechts)
                }
            }
        }
    }
}

/** Ein Eintrag als Text: Nummer am Rand, Absaetze mit Einzug, Verweise und Namen anklickbar. */
@Composable
private fun OfbEintragAnsicht(e: OfbEintrag, hervorgehoben: Boolean, springe: (String) -> Unit, openSheet: (String) -> Unit) {
    val farben = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(if (hervorgehoben) farben.secondaryContainer.copy(alpha = 0.35f) else farben.background).padding(vertical = 6.dp)) {
        e.bloecke.forEach { b ->
            if (b !is Absatz) return@forEach
            Row(Modifier.fillMaxWidth().padding(start = (16 * b.einzug).dp, top = if (b.abstandVor) 4.dp else 1.dp)) {
                Text(b.marke.orEmpty(), Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = farben.onSurfaceVariant)
                Text(absatzText(b, farben.primary, springe, openSheet), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun absatzText(b: Absatz, linkFarbe: androidx.compose.ui.graphics.Color, springe: (String) -> Unit, openSheet: (String) -> Unit) = buildAnnotatedString {
    val link = TextLinkStyles(SpanStyle(color = linkFarbe), hoveredStyle = SpanStyle(color = linkFarbe, textDecoration = TextDecoration.Underline))
    b.laeufe.forEach { l ->
        val stil = SpanStyle(fontWeight = if (l.stil == Stil.Fett) FontWeight.Bold else null, fontStyle = if (l.stil == Stil.Kursiv) FontStyle.Italic else null)
        when {
            l.ziel != null -> withLink(LinkAnnotation.Clickable(l.ziel, link) { springe(l.ziel) }) { withStyle(stil) { append(l.text) } }
            l.xref != null -> withLink(LinkAnnotation.Clickable(l.xref, TextLinkStyles(hoveredStyle = SpanStyle(textDecoration = TextDecoration.Underline))) { openSheet(l.xref) }) { withStyle(stil) { append(l.text) } }
            else -> withStyle(stil) { append(l.text) }
        }
    }
}
