package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import de.bgghome.webtrees.nativ.Sprache
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.pruefung.Abhakliste
import de.bgghome.webtrees.nativ.pruefung.Einheit
import de.bgghome.webtrees.nativ.pruefung.FaktRef
import de.bgghome.webtrees.nativ.pruefung.PruefErgebnis
import de.bgghome.webtrees.nativ.pruefung.PruefOptionen
import de.bgghome.webtrees.nativ.pruefung.PruefTexte
import de.bgghome.webtrees.nativ.pruefung.Regel
import de.bgghome.webtrees.nativ.pruefung.RegelGruppe
import de.bgghome.webtrees.nativ.pruefung.Regelkatalog
import de.bgghome.webtrees.nativ.pruefung.Schwere
import de.bgghome.webtrees.nativ.pruefung.Treffer
import de.bgghome.webtrees.nativ.pruefung.Voreinstellung
import de.bgghome.webtrees.nativ.pruefung.pruefen
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.FactDialog
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.placeSuggestions
import de.bgghome.webtrees.nativ.ui.saveFact
import de.bgghome.webtrees.nativ.ui.select
import de.bgghome.webtrees.nativ.ui.setRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/*
 * Fenster "Plausibilitaetspruefung" (27.09.2026): links Voreinstellung, Schaetzen und die Regeln (an/aus, Trefferzahl,
 * Grenzwert, Schwere), rechts die Treffer der gewaehlten Regel oder alle. Klick zeigt die Person im Hauptfenster,
 * Doppelklick oeffnet ihr Personenblatt, der Stift das betroffene Ereignis zum Bearbeiten (danach wird neu geprueft),
 * der Haken hakt einen Treffer als geprueft ab (lokal, siehe Abhakliste). Geprueft wird der ganze Baum aus dem Export -
 * ohne weitere Anfragen, darum rechnet jede Aenderung einer Einstellung sofort neu.
 */

/** Gewaehlte Regeln, Grenzwerte, Schwere, Schaetzen und Voreinstellung in einer Ablage (wtWin: "desk", gedaudit: eigene). */
internal class PruefWahl(private val prefs: de.bgghome.webtrees.nativ.data.Ablage) {
    fun aus(): Set<String> = prefs.getString("pruef_aus2", null)?.split(',')?.filter { it.isNotBlank() && it != "-" }?.toSet() ?: Regelkatalog.standardAus
    fun aus(s: Set<String>) = prefs.putString("pruef_aus2", s.sorted().joinToString(",").ifEmpty { "-" })
    fun grenzwerte(): Map<String, Double> = Regelkatalog.alle.mapNotNull { r -> prefs.getString("pruef_g_${r.id}", null)?.toDoubleOrNull()?.let { r.id to it } }.toMap()
    fun grenzwerte(m: Map<String, Double>) = Regelkatalog.alle.forEach { prefs.putString("pruef_g_${it.id}", m[it.id]?.toString()) }
    fun schwere(): Map<String, Schwere> = Regelkatalog.alle.mapNotNull { r -> prefs.getString("pruef_s_${r.id}", null)?.let { s -> Schwere.entries.firstOrNull { it.name == s } }?.let { r.id to it } }.toMap()
    fun schwere(m: Map<String, Schwere>) = Regelkatalog.alle.forEach { prefs.putString("pruef_s_${it.id}", m[it.id]?.name) }
    var schaetzen: Boolean
        get() = prefs.getBoolean("pruef_schaetzen", true)
        set(v) = prefs.putBoolean("pruef_schaetzen", v)
    var voreinstellung: String
        get() = prefs.getString("pruef_vorein", null) ?: "standard"
        set(v) = prefs.putString("pruef_vorein", v)
}

/** Die Abhakliste liegt als Datei bei den Einstellungen des Programms (nicht im Zwischenspeicher, der darf weg). */
internal class AbhakAblage(private val programm: String = de.bgghome.webtrees.nativ.Variante.ordner) {
    val datei: File by lazy {
        val basis = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), ".config")
        File(basis, programm).apply { mkdirs() }.resolve("pruefung-abgehakt.json")
    }
    fun laden(): Abhakliste = runCatching { Abhakliste.ausJson(datei.readText()) }.getOrElse { Abhakliste() }
    fun sichern(l: Abhakliste) { runCatching { datei.writeText(l.alsJson()) } }
}

internal fun einheit(e: Einheit?, wert: Double = 2.0): String = when (e) {
    Einheit.Jahre -> Texte.t(if (wert == 1.0) Res.string.desk_check_unit_year else Res.string.desk_check_unit_years)
    Einheit.Monate -> Texte.t(if (wert == 1.0) Res.string.desk_check_unit_month else Res.string.desk_check_unit_months)
    Einheit.Kilometer -> "km"
    else -> ""
}

internal fun zahl(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

internal fun gruppenName(g: RegelGruppe) = when (g) {
    RegelGruppe.Chronologie -> Res.string.desk_check_group_chrono
    RegelGruppe.Alter -> Res.string.desk_check_group_age
    RegelGruppe.Struktur -> Res.string.desk_check_group_structure
    RegelGruppe.Namen -> Res.string.desk_check_group_names
    RegelGruppe.Quellen -> Res.string.desk_check_group_sources
    RegelGruppe.Orte -> Res.string.desk_check_group_places
    RegelGruppe.Form -> Res.string.desk_check_group_form
}

internal val WARN_FARBE = Color(0xFFB26A00)

/** Wer zu einem Treffer gehoert: die Person, sonst der erste Partner der Familie, sonst ein Kind. */
internal fun trefferPerson(t: Treffer, b: TreeExport): String? =
    t.person ?: b.families[t.familie]?.let { f -> f.husband ?: f.wife ?: f.children.firstOrNull() }

/** Der Fakt aus dem Export (Person oder Familie). */
internal fun faktAus(b: TreeExport, record: String, id: String): FactJson? =
    (b.individuals[record]?.facts ?: b.families[record]?.facts)?.firstOrNull { it.id == id }

internal fun schwereText(s: Schwere) = Texte.t(if (s == Schwere.Fehler) Res.string.desk_check_error else Res.string.desk_check_warning)

/** Die Pruefung als Liste fuer Druck und PDF: je Regel mit offenen Treffern eine Ueberschrift, darunter eine Tabelle. */
internal fun pruefZeilen(
    e: PruefErgebnis, b: TreeExport, baum: String, grenzwerte: Map<String, Double>,
    schwere: Map<String, Schwere> = emptyMap(), abgehakt: Set<String> = emptySet(),
): List<Zeile> = buildList {
    add(Zeile(Texte.t(Res.string.desk_check_title, baum), gross = true))
    val offen = e.treffer.mapValues { (_, l) -> l.filter { it.schluessel !in abgehakt } }
    val fehler = offen.filterKeys { (schwere[it] ?: Regelkatalog.nachId[it]?.schwere) == Schwere.Fehler }.values.sumOf { it.size }
    val anzahl = offen.values.sumOf { it.size }
    add(Zeile(Texte.t(Res.string.desk_check_summary, anzahl, fehler) +
        (e.anzahl - anzahl).takeIf { it > 0 }?.let { "  ·  " + Texte.t(Res.string.desk_check_ticked_count, it) }.orEmpty()))
    val anteile = listOf(0.34f, 0.16f, 0.50f)
    offen.filterValues { it.isNotEmpty() }.forEach { (id, liste) ->
        val r = Regelkatalog.nachId.getValue(id)
        add(Zeile(""))
        val g = (grenzwerte[id] ?: r.grenzwert)?.let { "  [" + listOf(zahl(it), einheit(r.einheit, it)).filter(String::isNotBlank).joinToString(" ") + "]" }.orEmpty()
        add(Zeile("$id  ${PruefTexte.frage(r)}$g  –  ${schwereText(schwere[id] ?: r.schwere)} (${liste.size})", fett = true))
        liste.forEach { t ->
            val p = b.person(trefferPerson(t, b))
            add(Zeile("", spalten = listOf(p?.let(::registerName) ?: t.familie.orEmpty(), p?.lifespan.orEmpty(), t.text), anteile = anteile))
        }
    }
}

internal fun dateiWaehlen(titel: String, speichern: Boolean, vorschlag: String = ""): File? {
    val dialog = FileDialog(null as Frame?, titel, if (speichern) FileDialog.SAVE else FileDialog.LOAD).apply {
        if (vorschlag.isNotEmpty()) file = vorschlag
        isVisible = true
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, if (speichern && !name.endsWith(".json", true)) "$name.json" else name)
}

/** Ein Ereignis, das gerade bearbeitet wird: der Fakt, sein Datensatz und die Person, zu der er gehoert. */
private class Bearbeitung(val fakt: FactJson, val record: String, val person: String, val familie: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PruefFenster(state: UiState, viewModel: AppViewModel, openSheet: (String) -> Unit, onClose: () -> Unit, onOrteBereinigen: (() -> Unit)? = null,
    onZusammenfuehren: ((de.bgghome.webtrees.nativ.data.Dublette) -> Unit)? = null, onAufgabe: ((AufgabeZiel) -> Unit)? = null) {
    val appName = LocalAppName.current
    val baumTitel = state.tree?.title.orEmpty()
    val PruefWahl = remember { PruefWahl(DeskLayout.prefs) }
    val AbhakAblage = remember { AbhakAblage() }
    var aus by remember { mutableStateOf(PruefWahl.aus()) }
    var grenzwerte by remember { mutableStateOf(PruefWahl.grenzwerte()) }
    var schwere by remember { mutableStateOf(PruefWahl.schwere()) }
    var schaetzen by remember { mutableStateOf(PruefWahl.schaetzen) }
    var voreinstellung by remember { mutableStateOf(PruefWahl.voreinstellung) }
    var gewaehlt by remember { mutableStateOf<String?>(null) }
    var neu by remember { mutableIntStateOf(0) }
    var fortschritt by remember { mutableStateOf(0 to 0) }
    var abhak by remember { mutableStateOf(AbhakAblage.laden()) }
    var zeigeAbgehakt by remember { mutableStateOf(false) }
    var bearbeiten by remember { mutableStateOf<Bearbeitung?>(null) }
    var nachSpeichern by remember { mutableStateOf(false) }
    val baumSchluessel = abhak.schluesselBaum(viewModel.client.baseUrl, state.tree?.name.orEmpty())
    val abgehakt = abhak.fuer(baumSchluessel)
    fun schwereVon(id: String) = schwere[id] ?: Regelkatalog.nachId.getValue(id).schwere
    fun eigene() { voreinstellung = "eigene"; PruefWahl.voreinstellung = "eigene" }

    // Der ganze Baum: aus dem Zwischenspeicher, solange sich auf dem Server nichts geaendert hat
    val baum by produceState<Result<TreeExport>?>(null, state.tree?.name, neu) {
        value = null
        val tree = state.tree ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                BaumSpeicher.holen(viewModel.client, tree.name, Int.MAX_VALUE / 64) { a, b -> fortschritt = a to b }
                    ?: throw IllegalStateException(Texte.t(Res.string.desk_list_needs_export))
            }
        }
    }
    val ergebnis by produceState<PruefErgebnis?>(null, baum, aus, grenzwerte, schaetzen) {
        val b = baum?.getOrNull() ?: run { value = null; return@produceState }
        value = withContext(Dispatchers.Default) { pruefen(b, PruefOptionen(grenzwerte, aus, schaetzen, texte = PruefTexte.ausRessourcen(Sprache.aktiv))) }
    }
    LaunchedEffect(aus) { PruefWahl.aus(aus) }
    // Nach dem Speichern eines Ereignisses: warten, bis das Programm fertig geschrieben hat, dann neu pruefen
    LaunchedEffect(state.busy, nachSpeichern) { if (nachSpeichern && !state.busy) { nachSpeichern = false; neu++ } }

    fun abhaken(t: Treffer) {
        abhak = abhak.umschalten(baumSchluessel, t, java.time.LocalDate.now().toString())
        AbhakAblage.sichern(abhak)
    }

    fun voreinstellungSetzen(v: Voreinstellung) {
        aus = v.aus; grenzwerte = v.grenzwerte; schwere = v.schwere; schaetzen = v.schaetzen; voreinstellung = v.id
        PruefWahl.grenzwerte(v.grenzwerte); PruefWahl.schwere(v.schwere); PruefWahl.schaetzen = v.schaetzen; PruefWahl.voreinstellung = v.id
    }

    androidx.compose.ui.window.Window(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_check_window),
        state = arbeitsfenster("fenster_pruefung", 1240, 860),
        onPreviewKeyEvent = { e -> when { e.key == Key.Escape && bearbeiten == null -> { onClose(); true }; e.key == Key.F1 && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> { Hilfe.oeffnen("pruefung"); true }; else -> false } },
    ) {
        DeskTheme {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // ── Einstellungen und Regeln ──
                val pruefBreite = rememberBreite("pruef_liste", 470f)
                Column(Modifier.width(pruefBreite.value.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Einstellung(stringResource(Res.string.desk_check_preset), stringResource(Res.string.tipp_preset)) {
                            val namen = Voreinstellung.alle.associate { it.id to PruefTexte.nameText(it) } + ("eigene" to stringResource(Res.string.desk_check_preset_own))
                            Auswahl(namen[voreinstellung] ?: namen.getValue("eigene"), Voreinstellung.alle.map { namen.getValue(it.id) }) { w ->
                                Voreinstellung.alle.firstOrNull { namen[it.id] == w }?.let(::voreinstellungSetzen)
                            }
                        }
                        Haken(stringResource(Res.string.desk_check_estimate), schaetzen, stringResource(Res.string.tipp_estimate)) { schaetzen = it; PruefWahl.schaetzen = it; eigene() }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val liste = rememberLazyListState()
                    Box(Modifier.weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp), state = liste) {
                            item {
                                val alle = ergebnis?.treffer?.values?.sumOf { l -> l.count { it.schluessel !in abgehakt } }
                                RegelZeile(null, stringResource(Res.string.desk_check_all), alle, false, gewaehlt == null, true, onHaken = null) { gewaehlt = null }
                            }
                            RegelGruppe.entries.forEach { g ->
                                item(key = g.name) { Spacer(Modifier.height(8.dp)); ArtGruppe(stringResource(gruppenName(g))) }
                                items(Regelkatalog.alle.filter { it.gruppe == g }, key = { it.id }) { r ->
                                    val an = r.id !in aus
                                    val zahl = if (an) ergebnis?.treffer?.get(r.id)?.count { it.schluessel !in abgehakt } else null
                                    RegelZeile(r, PruefTexte.frageText(r), zahl, schwereVon(r.id) == Schwere.Fehler, gewaehlt == r.id, an,
                                        onHaken = { aus = if (it) aus - r.id else aus + r.id; eigene() }) { gewaehlt = r.id }
                                    if (gewaehlt == r.id) RegelEinstellung(r, grenzwerte[r.id], schwereVon(r.id),
                                        onWert = { w ->
                                            grenzwerte = if (w == null) grenzwerte - r.id else grenzwerte + (r.id to w)
                                            PruefWahl.grenzwerte(grenzwerte); eigene()
                                        },
                                        onSchwere = { s ->
                                            schwere = if (s == r.schwere) schwere - r.id else schwere + (r.id to s)
                                            PruefWahl.schwere(schwere); eigene()
                                        })
                                }
                            }
                        }
                        ListenLeiste(liste)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(Res.string.desk_check_hint), Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            Knopf(stringResource(Res.string.desk_check_ticks_export), abhak.anzahl(baumSchluessel) > 0, stringResource(Res.string.tipp_ticks_export)) {
                                dateiWaehlen(Texte.t(Res.string.desk_check_ticks_export), true, "pruefung-abgehakt.json")?.let { runCatching { it.writeText(abhak.alsJson()) } }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            Knopf(stringResource(Res.string.desk_check_ticks_import), true, stringResource(Res.string.tipp_ticks_import)) {
                                dateiWaehlen(Texte.t(Res.string.desk_check_ticks_import), false)?.let { f ->
                                    runCatching { Abhakliste.dateiAusJson(f.readText()) }.getOrNull()?.let { abhak = abhak.zusammenfuehren(it); AbhakAblage.sichern(abhak) }
                                }
                            }
                        }
                    }
                }
                Trenner(pruefBreite, "pruef_liste", 300f, 900f)

                // ── Treffer ──
                Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val r = gewaehlt?.let { Regelkatalog.nachId[it] }
                    Text(r?.let { "${it.id}  ${PruefTexte.frageText(it)}" } ?: stringResource(Res.string.desk_check_title, baumTitel),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val e = ergebnis
                    val b = baum?.getOrNull()
                    if (e != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        val offen = e.treffer.mapValues { (_, l) -> l.count { it.schluessel !in abgehakt } }
                        val fehler = offen.filterKeys { schwereVon(it) == Schwere.Fehler }.values.sum()
                        val zahlAbgehakt = e.anzahl - offen.values.sum()
                        Text(stringResource(Res.string.desk_check_summary, offen.values.sum(), fehler) +
                            (b?.let { "  ·  " + stringResource(Res.string.desk_check_checked, it.individuals.count { (_, i) -> !i.person.isPrivate }, it.families.size) }.orEmpty()),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (zahlAbgehakt > 0) Haken(stringResource(Res.string.desk_check_show_ticked, zahlAbgehakt), zeigeAbgehakt) { zeigeAbgehakt = it }
                        // Schreibvarianten von Orten gleich im Ortsfenster zusammenfuehren
                        if (r?.id == "510" && onOrteBereinigen != null) TextButton(onClick = onOrteBereinigen) { Text(stringResource(Res.string.pruef_places_cleanup)) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val fehlerText = baum?.exceptionOrNull()
                        when {
                            fehlerText != null -> Text(fehlerText.message ?: "?", color = MaterialTheme.colorScheme.error)
                            e == null || b == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                val (x, n) = fortschritt
                                if (n > 0) LinearProgressIndicator(progress = { x.toFloat() / n }, modifier = Modifier.width(280.dp)) else CircularProgressIndicator()
                                Text(if (n > 0) stringResource(Res.string.desk_check_loading, x, n) else stringResource(Res.string.desk_chart_loading_any), Modifier.padding(top = 8.dp))
                            }
                            else -> {
                                val alle = if (r == null) e.treffer.values.flatten() else e.treffer[r.id].orEmpty()
                                val zeilen = if (zeigeAbgehakt) alle else alle.filter { it.schluessel !in abgehakt }
                                if (zeilen.isEmpty()) Text(stringResource(if (r != null && r.id in aus) Res.string.desk_check_off else Res.string.desk_check_none),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else {
                                    val trefferListe = rememberLazyListState()
                                    LazyColumn(Modifier.fillMaxSize(), state = trefferListe) {
                                        items(zeilen) { t ->
                                            TrefferZeile(t, b, r == null, schwereVon(t.regel), t.schluessel in abgehakt, state.root, state.tree?.canEdit == true,
                                                viewModel, openSheet, onAbhaken = { abhaken(t) }, onBearbeiten = { bearbeiten = it }, onZusammenfuehren = onZusammenfuehren, onAufgabe = onAufgabe)
                                        }
                                    }
                                    ListenLeiste(trefferListe)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bereit = e != null && b != null
                        fun doc() = listenPdf(pruefZeilen(e!!, b!!, baumTitel, grenzwerte, schwere, abgehakt), appName, baumTitel)
                        val titel = stringResource(Res.string.desk_check_title, baumTitel)
                        Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.desk_check_rerun), baum != null) { neu++ } }
                        Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.desk_print), bereit) { drucken(doc(), titel) } }
                        Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.desk_save_pdf), bereit) { alsPdf(doc(), titel) } }
                        Box(Modifier.weight(1f)) { Knopf(stringResource(Res.string.action_close), true, onClose) }
                    }
                }
            }
            // Ereignis bearbeiten: derselbe Dialog wie im Personenblatt; danach neu pruefen
            bearbeiten?.let { bb ->
                FactDialog(
                    fact = bb.fakt, tags = if (bb.familie) state.familyTags else state.tags, suggestPlaces = viewModel.placeSuggestions(),
                    onDismiss = { bearbeiten = null },
                    onSave = { req ->
                        bearbeiten = null
                        viewModel.select(bb.person)
                        viewModel.saveFact(req, bb.record)
                        nachSpeichern = true
                    },
                )
            }
        }
    }
}

@Composable
internal fun RegelZeile(r: Regel?, text: String, zahl: Int?, fehler: Boolean, aktiv: Boolean, an: Boolean, onHaken: ((Boolean) -> Unit)?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .fokusRahmen().clickable(onClick = onClick).padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onHaken != null) Checkbox(checked = an, onCheckedChange = onHaken, modifier = Modifier.size(32.dp)) else Spacer(Modifier.width(32.dp))
        Text(r?.id.orEmpty(), Modifier.width(34.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
            color = if (an) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        val farbe = when {
            zahl == null || zahl == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
            fehler -> MaterialTheme.colorScheme.error
            else -> WARN_FARBE
        }
        Text(zahl?.toString() ?: "–", Modifier.width(44.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = farbe,
            fontWeight = if ((zahl ?: 0) > 0) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.End)
    }
}

/** Unter der gewaehlten Regel: Grenzwert (wenn es einen gibt) und Schwere. */
@Composable
internal fun RegelEinstellung(r: Regel, wert: Double?, schwere: Schwere, onWert: (Double?) -> Unit, onSchwere: (Schwere) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 70.dp, end = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val vorgabe = r.grenzwert
        if (vorgabe != null) {
            var text by remember(r.id) { mutableStateOf(zahl(wert ?: vorgabe)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tipp(stringResource(Res.string.tipp_limit)) { Text(stringResource(Res.string.desk_check_limit), Modifier.width(70.dp), style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(text, { neu ->
                    text = neu
                    val d = neu.replace(',', '.').toDoubleOrNull()
                    if (d != null && d >= 0) onWert(if (d == vorgabe) null else d)
                }, singleLine = true, modifier = Modifier.width(100.dp), textStyle = MaterialTheme.typography.bodyMedium)
                Text(einheit(r.einheit, wert ?: vorgabe), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(stringResource(Res.string.desk_check_default, zahl(vorgabe)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tipp(stringResource(Res.string.tipp_severity)) { Text(stringResource(Res.string.desk_check_severity), Modifier.width(70.dp), style = MaterialTheme.typography.bodyMedium) }
            Box(Modifier.width(160.dp)) {
                Auswahl(schwereText(schwere), Schwere.entries.map(::schwereText)) { w -> Schwere.entries.firstOrNull { schwereText(it) == w }?.let(onSchwere) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrefferZeile(
    t: Treffer, b: TreeExport, mitRegel: Boolean, schwere: Schwere, abgehakt: Boolean, root: String?, darfBearbeiten: Boolean,
    viewModel: AppViewModel, openSheet: (String) -> Unit, onAbhaken: () -> Unit, onBearbeiten: (Bearbeitung) -> Unit,
    onZusammenfuehren: ((de.bgghome.webtrees.nativ.data.Dublette) -> Unit)? = null,
    onAufgabe: ((AufgabeZiel) -> Unit)? = null,
) {
    val xref = trefferPerson(t, b)
    val p = b.person(xref)
    val blass = if (abgehakt) 0.45f else 1f
    Row(
        Modifier.fillMaxWidth().fokusRahmen()
            .combinedClickable(enabled = xref != null, onClick = { xref?.let { viewModel.setRoot(it) } }, onDoubleClick = { xref?.let(openSheet) })
            .padding(start = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mitRegel) Text(t.regel, Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = (if (schwere == Schwere.Fehler) MaterialTheme.colorScheme.error else WARN_FARBE).copy(alpha = blass))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p?.let(::registerName) ?: t.familie.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = blass),
                    fontWeight = if (xref == root) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                p?.lifespan?.takeIf(String::isNotBlank)?.let { Text("  ($it)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = blass)) }
                Text("  " + listOfNotNull(xref, t.familie).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = blass))
            }
            Text(t.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = blass), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Stift: das betroffene Ereignis bearbeiten (bei mehreren eine Auswahl)
        val fakten = t.fakten.mapNotNull { ref -> faktAus(b, ref.record, ref.fakt)?.let { ref to it } }
        if (darfBearbeiten && fakten.isNotEmpty()) {
            var offen by remember { mutableStateOf(false) }
            fun oeffnen(ref: FaktRef, f: FactJson) {
                val familie = ref.record in b.families
                onBearbeiten(Bearbeitung(f, ref.record, if (familie) xref ?: ref.record else ref.record, familie))
            }
            Box {
                IconButton(onClick = { if (fakten.size == 1) fakten.single().let { (r, f) -> oeffnen(r, f) } else offen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, stringResource(Res.string.desk_check_edit), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                    fakten.forEach { (ref, f) ->
                        val wer = b.person(ref.record)?.name ?: b.families[ref.record]?.let { fam -> listOfNotNull(b.person(fam.husband)?.name, b.person(fam.wife)?.name).joinToString(" ⚭ ") }.orEmpty()
                        DropdownMenuItem(text = { Text("${f.label.ifBlank { f.tag }}  ${f.date?.text.orEmpty()}  ·  $wer") }, onClick = { offen = false; oeffnen(ref, f) })
                    }
                }
            }
        } else Spacer(Modifier.width(32.dp))
        // Zwei Pfeile: die beiden Personen eines Dubletten-Treffers zusammenfuehren (nur Verwalter, ab API-Stufe 29)
        val paar = if (onZusammenfuehren != null && t.regel in de.bgghome.webtrees.nativ.data.DUBLETTEN_REGELN) de.bgghome.webtrees.nativ.data.paarAus(t) else null
        if (paar != null) Tipp(stringResource(Res.string.desk_merge_do)) {
            IconButton(onClick = { onZusammenfuehren!!(paar) }, modifier = Modifier.size(32.dp)) {
                Icon(de.bgghome.webtrees.nativ.ui.MergeIcon, stringResource(Res.string.desk_merge_do), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (onZusammenfuehren != null) Spacer(Modifier.width(32.dp))
        // Aufgabe: den Treffer als Forschungsaufgabe an die Person haengen (ab API-Stufe 30, mit Bearbeitungsrecht)
        if (onAufgabe != null && xref != null) Tipp(stringResource(Res.string.desk_task_tip)) {
            IconButton(onClick = { onAufgabe(AufgabeZiel(xref, p?.name ?: xref, vorgabe = Texte.t(Res.string.desk_task_from_check, t.regel, t.text))) }, modifier = Modifier.size(32.dp)) {
                Icon(de.bgghome.webtrees.nativ.ui.TaskIcon, stringResource(Res.string.desk_task_tip), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Haken: als geprueft abhaken (oder wieder oeffnen)
        IconButton(onClick = onAbhaken, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Check, stringResource(if (abgehakt) Res.string.desk_check_untick else Res.string.desk_check_tick), Modifier.size(18.dp),
                tint = if (abgehakt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
    }
}
