package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.CitationRequest
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.SourceRef
import de.bgghome.webtrees.nativ.api.SourceSummary
import de.bgghome.webtrees.nativ.data.GedcomDate
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.Field
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.saveCitations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Quellenverweis zitieren, aendern und kopieren (Stufe 2 der Quellenverwaltung, API-Stufe 18).
 * Grundsatz: Es geht nur an den Server, was jemand geaendert hat; alles Uebrige am Verweis und am Ereignis bleibt.
 */

/** Was der Dialog schreibt: der Datensatz (Person oder Familie), das Ereignis und ggf. der bestehende Verweis. */
class ZitatZiel(val record: String, val factId: String?, val index: Int?, val alt: SourceRef?)

private val QUALITAETEN = listOf(null, 0, 1, 2, 3)

@Composable
private fun qualitaetText(q: Int?): String = qualitaet(q)?.let { stringResource(it) } ?: stringResource(Res.string.desk_quality_none)

/**
 * Verweis anlegen oder aendern. Quelle aus der Verwaltung waehlen (Suche) oder als freien Text; dazu Seite,
 * Qualitaet, Datum, Zitat und Notiz. Beim Aendern werden nur geaenderte Felder gesendet.
 */
@Composable
fun ZitatDialog(ziel: ZitatZiel, tree: String, viewModel: AppViewModel, onDismiss: () -> Unit) {
    val alt = ziel.alt
    var quellenNeu by remember { mutableStateOf(0) }
    var neueQuelle by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val quellen by produceState<List<SourceSummary>?>(null, tree, quellenNeu) {
        value = withContext(Dispatchers.IO) { runCatching { viewModel.client.sources(tree).sources }.getOrDefault(emptyList()) }
    }
    var textQuelle by remember { mutableStateOf(alt?.istText == true) }
    var quelle by remember { mutableStateOf(alt?.takeIf { !it.istText }?.xref.orEmpty()) }
    var freitext by remember { mutableStateOf(alt?.takeIf { it.istText }?.title.orEmpty()) }
    var suche by remember { mutableStateOf("") }
    var seite by remember { mutableStateOf(alt?.page.orEmpty()) }
    var qual by remember { mutableStateOf(alt?.quality) }
    var datum by remember { mutableStateOf(datumVorbelegen(alt?.date)) }
    var zitat by remember { mutableStateOf(alt?.text.orEmpty()) }
    var notiz by remember { mutableStateOf(alt?.notes?.firstOrNull().orEmpty()) }
    var speichert by remember { mutableStateOf(false) }
    // Medien des Verweises (der Scan dieser Taufe); hochgeladen wird ohne Verknuepfung an die Person, angehaengt hier
    val medien = remember { mutableStateListOf<de.bgghome.webtrees.nativ.api.MediaJson>().also { it.addAll(alt?.media.orEmpty()) } }
    var medienWahl by remember { mutableStateOf(false) }
    var medienFehler by remember { mutableStateOf<String?>(null) }
    val medienGeaendert = medien.map { it.xref } != alt?.media.orEmpty().map { it.xref }

    val quelleGewaehlt = if (textQuelle) freitext.isNotBlank() else quelle.isNotBlank()
    var datumAlsText by remember { mutableStateOf(false) }
    val datumOk = datumErkannt(datum) || datumAlsText

    fun anfrage(): CitationRequest {
        val neuQuelle = if (textQuelle) freitext.trim() else quelle
        val altQuelle = if (alt == null) null else (if (alt.istText) alt.title else alt.xref)
        return CitationRequest(
            factId = ziel.factId, index = ziel.index,
            source = neuQuelle.takeIf { alt == null || it != altQuelle },
            page = seite.trim().takeIf { alt == null || it != alt.page },
            quality = qual?.toString().orEmpty().takeIf { alt == null || qual != alt.quality },
            date = datumGedcom(datum).takeIf { alt == null || datum != datumVorbelegen(alt.date) },
            text = zitat.trim().takeIf { alt == null || it != alt.text },
            note = notiz.trim().takeIf { alt == null || it != alt.notes.firstOrNull().orEmpty() },
            media = medien.map { it.xref }.distinct().takeIf { alt == null && it.isNotEmpty() || alt != null && medienGeaendert },
        ).let { r -> if (alt == null) r.copy(page = r.page?.ifEmpty { null }, quality = r.quality?.ifEmpty { null }, date = r.date?.ifEmpty { null }, text = r.text?.ifEmpty { null }, note = r.note?.ifEmpty { null }) else r }
    }

    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (alt == null) Res.string.desk_cite_add else Res.string.desk_cite_edit)) },
        text = {
            // Zwei Spalten statt einer langen: links die Quelle, rechts die Angaben zum Verweis - so passt alles ohne Rollen
            Row(Modifier.width(1000.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !textQuelle, onClick = { textQuelle = false })
                    Text(stringResource(Res.string.desk_cite_from_list), Modifier.clickable { textQuelle = false })
                    Box(Modifier.width(16.dp))
                    RadioButton(selected = textQuelle, onClick = { textQuelle = true })
                    Text(stringResource(Res.string.desk_cite_as_text), Modifier.clickable { textQuelle = true })
                }
                if (textQuelle) {
                    Field(freitext, { freitext = it }, Res.string.desk_cite_text_source, hint = Res.string.desk_cite_text_source_hint)
                } else {
                    Field(suche, { suche = it }, Res.string.desk_sources_search)
                    val liste = quellen.orEmpty().filter { q -> suche.isBlank() || listOf(q.title, q.author, q.abbreviation).any { it.contains(suche.trim(), ignoreCase = true) } }
                    Box(Modifier.fillMaxWidth().height(300.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
                        if (quellen == null) Text(stringResource(Res.string.desk_chart_loading_any), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else LazyColumn(Modifier.fillMaxWidth()) {
                            items(liste, key = { it.xref }) { q ->
                                val aktiv = q.xref == quelle
                                Text(q.title.ifBlank { q.xref } + (q.author.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                        .clickable { quelle = q.xref }.padding(horizontal = 8.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { neueQuelle = true }) { Text(stringResource(Res.string.desk_cite_new_source)) }
                        Tipp(stringResource(Res.string.tipp_source_from_file)) {
                            TextButton(onClick = {
                                dateiOeffnen(scanDialogTitel())?.let { datei ->
                                    scope.launch {
                                        runCatching {
                                            val x = viewModel.client.saveSource(tree, null, de.bgghome.webtrees.nativ.api.SourceRequest(title = titelAusDatei(datei))).xref
                                            scanHochladen(viewModel.client, tree, x, datei); x
                                        }.onSuccess { quelle = it; quellenNeu++ }.onFailure { fehler = it.message }
                                    }
                                }
                            }) { Text(stringResource(Res.string.desk_cite_from_file)) }
                        }
                    }
                    fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(seite, { seite = it }, Res.string.desk_citation_page, hint = Res.string.desk_cite_page_hint)
                val qualNamen = QUALITAETEN.map { qualitaetText(it) }
                Einstellung(stringResource(Res.string.desk_citation_quality), stringResource(Res.string.tipp_quality)) {
                    Auswahl(qualNamen[QUALITAETEN.indexOf(qual)], qualNamen) { w -> qual = QUALITAETEN[qualNamen.indexOf(w)] }
                }
                Column {
                    Field(datum, { datum = it; datumAlsText = false }, Res.string.desk_citation_date, hint = Res.string.date_hint)
                    if (!datumErkannt(datum)) {
                        if (datumAlsText) Text(stringResource(Res.string.desk_simple_date_text), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.desk_date_invalid), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { datumAlsText = true }) { Text(stringResource(Res.string.desk_date_as_text), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                Field(zitat, { zitat = it }, Res.string.desk_citation_text, hint = Res.string.desk_cite_quote_hint, minLines = 3)
                Field(notiz, { notiz = it }, Res.string.fact_note, minLines = 2)
                MedienBearbeiten(medien, onEntfernen = { medien.remove(it) }, fehler = medienFehler,
                    onDatei = {
                        dateiOeffnen(scanDialogTitel())?.let { datei ->
                            scope.launch {
                                runCatching {
                                    val bytes = withContext(Dispatchers.IO) { datei.readBytes() }
                                    viewModel.client.uploadMedia(tree, ziel.record, bytes, datei.name, mimeVon(datei), datei.nameWithoutExtension, type = "document", link = false).media
                                }.onSuccess { x -> if (x != null) medien += de.bgghome.webtrees.nativ.api.MediaJson(xref = x, title = datei.nameWithoutExtension, mime = mimeVon(datei)) }
                                    .onFailure { medienFehler = it.message }
                            }
                        }
                    },
                    onVorhanden = { medienWahl = true })
            }
            }
        },
        breite = 1060.dp,
        confirmButton = {
            TextButton(enabled = quelleGewaehlt && datumOk && !speichert, onClick = {
                speichert = true
                viewModel.saveCitations(listOf(ziel.record to anfrage())) { onDismiss() }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !speichert) { Text(stringResource(Res.string.action_cancel)) } },
    )
    if (neueQuelle) QuelleDialog(tree, null, viewModel, onDismiss = { neueQuelle = false }, onSaved = { quelle = it; quellenNeu++ })
    if (medienWahl) MedienWahlDialog(tree, viewModel.client, medien.map { it.xref }.toSet(), onDismiss = { medienWahl = false },
        archive = viewModel.state.value.archive, rechteXref = ziel.record) { medien += it }
}

/**
 * Einen Verweis zu weiteren Ereignissen dieser Person und zu Verwandten kopieren. Bei Personen und Partnerschaften
 * landet er als allgemeiner Verweis am Datensatz. Kopiert werden Quelle, Seite, Qualitaet, Datum, Zitat und Notiz.
 */
@Composable
fun ZitatKopierenDialog(q: SourceRef, detail: IndividualDetail, eigenesFact: String, viewModel: AppViewModel, onDismiss: () -> Unit) {
    val ziele = remember { mutableStateOf(setOf<String>()) }
    var speichert by remember { mutableStateOf(false) }
    fun umschalten(k: String) { ziele.value = if (k in ziele.value) ziele.value - k else ziele.value + k }
    val vorlage = CitationRequest(
        source = if (q.istText) q.title else q.xref, page = q.page.ifEmpty { null }, quality = q.quality?.toString(),
        date = q.date?.gedcom?.ifEmpty { null }, text = q.text.ifEmpty { null }, note = q.notes.firstOrNull(),
    )
    // Ziele: "fact:<record>:<factId>" oder "record:<xref>"
    val ereignisse = detail.facts.filter { it.known && it.id != eigenesFact && it.tag !in setOf("SOUR", "NOTE", "SEX", "CHAN") }.map { detail.person.xref to it } +
        detail.spouseFamilies.flatMap { f -> f.facts.filter { it.known && it.id != eigenesFact }.map { f.xref to it } }
    val eltern = detail.parentFamilies.firstOrNull()
    val personen = listOfNotNull(eltern?.husband?.let { Res.string.rel_father to it }, eltern?.wife?.let { Res.string.rel_mother to it }) +
        detail.spouseFamilies.mapNotNull { f -> f.spouse?.let { Res.string.rel_partner to it } } +
        detail.spouseFamilies.flatMap { f -> f.children.map { Res.string.desk_cite_child to it } }
    val familien = detail.spouseFamilies.filter { it.spouse != null }

    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_cite_copy_title)) },
        text = {
            Column(Modifier.width(560.dp).height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(q.title.ifBlank { q.xref } + q.page.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(Res.string.desk_cite_copy_events), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                ereignisse.forEach { (record, f) ->
                    val k = "fact:$record:${f.id}"
                    val name = f.label + listOfNotNull(f.date?.text?.takeIf(String::isNotBlank), f.place?.name?.takeIf(String::isNotBlank)).joinToString(", ").let { if (it.isEmpty()) "" else "  ($it)" }
                    ZielZeile(name, k in ziele.value, f.sources.any { it.xref == q.xref && it.title == q.title }) { umschalten(k) }
                }
                Text(stringResource(Res.string.desk_cite_copy_persons), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                personen.filter { !it.second.isPrivate }.forEach { (rolle, p) ->
                    val k = "record:${p.xref}"
                    ZielZeile(stringResource(rolle) + ": " + klarName(p), k in ziele.value, false) { umschalten(k) }
                }
                familien.forEach { f ->
                    val k = "record:${f.xref}"
                    ZielZeile(stringResource(Res.string.desk_cite_copy_family, klarName(f.spouse!!)), k in ziele.value, false) { umschalten(k) }
                }
                Text(stringResource(Res.string.desk_cite_copy_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = ziele.value.isNotEmpty() && !speichert, onClick = {
                speichert = true
                val auftraege = ziele.value.map { k ->
                    val teile = k.split(":", limit = 3)
                    if (teile[0] == "fact") teile[1] to vorlage.copy(factId = teile[2]) else teile[1] to vorlage
                }
                viewModel.saveCitations(auftraege) { onDismiss() }
            }) { Text(stringResource(Res.string.desk_cite_copy_action, ziele.value.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !speichert) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun ZielZeile(text: String, gewaehlt: Boolean, schonDa: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = gewaehlt, onCheckedChange = { onClick() })
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (schonDa) Text(stringResource(Res.string.desk_cite_already), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Zum Aufraeumen: ein Verweis als Anfrage zum Loeschen bzw. Verschieben. */
fun zitatLoeschen(ziel: ZitatZiel) = CitationRequest(factId = ziel.factId, index = ziel.index, delete = true)
fun zitatVerschieben(ziel: ZitatZiel, nach: Int) = CitationRequest(factId = ziel.factId, index = ziel.index, moveTo = nach)
