package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.ArchiveCollection
import de.bgghome.webtrees.nativ.api.ArchiveEntry
import de.bgghome.webtrees.nativ.api.ArchivEinstellungen
import de.bgghome.webtrees.nativ.api.PostkartenAnfrage
import de.bgghome.webtrees.nativ.api.SammlungDaten
import de.bgghome.webtrees.nativ.api.VerwaltungsAuswahl
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.explain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Verwaltung des Archivs aus wtWin (Modul Sammlungen ab Stufe 6) - dieselben Handgriffe wie im Browser: Sammlung anlegen,
 * aendern, ausblenden, loeschen (Administrator), Datei umbenennen, Bilder thematischen Sammlungen zuordnen (Verwalter),
 * Postkarten beschriften, Einstellungen des Moduls (Administrator). Rechte prueft das Modul; die App blendet nur aus.
 */

private fun fehlerText(e: Throwable): String = meldung(e)

/** Sammlung anlegen ([alt] null) oder aendern. [onGespeichert] bekommt den Slug. */
@Composable
fun SammlungDialog(
    tree: String, client: WtClient, alt: SammlungDaten?, auswahl: VerwaltungsAuswahl,
    onDismiss: () -> Unit, onGespeichert: (String) -> Unit,
) {
    var name by remember { mutableStateOf(alt?.name.orEmpty()) }
    var beschreibung by remember { mutableStateOf(alt?.beschreibung.orEmpty()) }
    var symbol by remember { mutableStateOf(alt?.symbol ?: auswahl.symbole.firstOrNull()?.wert.orEmpty()) }
    var farbe by remember { mutableStateOf(alt?.farbe.orEmpty()) }
    var ansicht by remember { mutableStateOf(alt?.ansicht ?: "foto") }
    var ordner by remember { mutableStateOf(alt?.ordner.orEmpty()) }
    var aktiv by remember { mutableStateOf(alt?.aktiv ?: true) }
    var reihenfolge by remember { mutableStateOf((alt?.reihenfolge ?: 0).toString()) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var speichert by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val farbeOk = farbe.isBlank() || Regex("#[0-9a-fA-F]{6}").matches(farbe.trim())
    val keinOrdner = stringResource(Res.string.desk_archive_folder_none)
    val ordnerListe = (auswahl.ordner + listOfNotNull(alt?.ordner?.takeIf { it.isNotBlank() })).distinct().sorted()

    WtAlertDialog(
        onDismissRequest = { if (!speichert) onDismiss() },
        title = { Text(stringResource(if (alt == null) Res.string.desk_archive_collection_new_title else Res.string.desk_archive_collection_edit_title)) },
        breite = 600.dp,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.desk_archive_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(beschreibung, { beschreibung = it }, label = { Text(stringResource(Res.string.field_description)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text(stringResource(Res.string.desk_archive_folder_field), style = MaterialTheme.typography.labelMedium)
                Auswahl(ordner.ifBlank { keinOrdner }, listOf(keinOrdner) + ordnerListe) { ordner = if (it == keinOrdner) "" else it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.desk_archive_view), style = MaterialTheme.typography.labelMedium)
                        Auswahl(auswahl.ansichten.firstOrNull { it.wert == ansicht }?.name ?: ansicht, auswahl.ansichten.map { it.name }) { w ->
                            ansicht = auswahl.ansichten.firstOrNull { it.name == w }?.wert ?: ansicht
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.desk_archive_symbol), style = MaterialTheme.typography.labelMedium)
                        Auswahl(auswahl.symbole.firstOrNull { it.wert == symbol }?.name ?: symbol, auswahl.symbole.map { it.name }) { w ->
                            symbol = auswahl.symbole.firstOrNull { it.name == w }?.wert ?: symbol
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(farbe, { farbe = it }, label = { Text(stringResource(Res.string.desk_archive_color)) }, singleLine = true,
                        isError = !farbeOk, modifier = Modifier.weight(1f))
                    Box(Modifier.size(28.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        .background(farbeAus(farbe) ?: Color.Transparent))
                    OutlinedTextField(reihenfolge, { reihenfolge = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(Res.string.desk_archive_order)) },
                        singleLine = true, modifier = Modifier.width(140.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(aktiv, { aktiv = it })
                    Text(stringResource(Res.string.desk_archive_visible))
                }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && farbeOk && !speichert, onClick = {
                speichert = true; fehler = null
                val daten = (alt ?: SammlungDaten()).copy(
                    name = name.trim(), beschreibung = beschreibung.trim(), symbol = symbol, farbe = farbe.trim().ifBlank { null },
                    ansicht = ansicht, ordner = ordner, aktiv = aktiv, reihenfolge = reihenfolge.toIntOrNull() ?: 0,
                )
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { client.archiveSaveCollection(tree, daten) } }
                        .onSuccess { r -> onGespeichert(r.sammlung?.slug ?: daten.slug) }
                        .onFailure { fehler = fehlerText(it); speichert = false }
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(enabled = !speichert, onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

internal fun farbeAus(text: String?): Color? =
    text?.trim()?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** Datei umbenennen: nur der Dateiname, gleicher Ordner und gleiche Endung (prueft das Modul). */
@Composable
fun UmbenennenDialog(tree: String, client: WtClient, e: ArchiveEntry, onDismiss: () -> Unit, onFertig: (ArchiveEntry?) -> Unit) {
    val alt = e.datei.ifBlank { e.pfad.orEmpty().substringAfterLast('/') }
    var name by remember { mutableStateOf(alt) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    WtAlertDialog(
        onDismissRequest = { if (!laeuft) onDismiss() },
        title = { Text(stringResource(Res.string.desk_archive_rename_title)) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.pfad.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.desk_archive_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(Res.string.desk_archive_rename_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !laeuft && name.isNotBlank() && name.trim() != alt, onClick = {
                laeuft = true; fehler = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { client.archiveRename(tree, e.pfad.orEmpty(), name.trim()) } }
                        .onSuccess { onFertig(it.eintrag) }
                        .onFailure { fehler = fehlerText(it); laeuft = false }
                }
            }) { Text(stringResource(Res.string.desk_archive_rename).removeSuffix(" …")) }
        },
        dismissButton = { TextButton(enabled = !laeuft, onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Bilder thematischen Sammlungen (Alben) zuordnen: je Album ein Haken, der sofort wirkt. Bei mehreren Bildern ist der Haken
 * gesetzt, wenn alle drin sind; ein Klick legt alle hinein oder nimmt alle heraus.
 */
@Composable
fun AlbenDialog(tree: String, client: WtClient, bilder: List<ArchiveEntry>, alben: List<ArchiveCollection>, onDismiss: () -> Unit, onGeaendert: () -> Unit) {
    // Startzustand aus inSammlungen (das Modul nennt dort Namen oder Slugs)
    val drin = remember { mutableStateMapOf<String, Boolean>().apply {
        alben.forEach { a -> put(a.slug, bilder.all { b -> a.slug in b.inSammlungen || a.name in b.inSammlungen }) }
    } }
    var fehler by remember { mutableStateOf<String?>(null) }
    var geaendert by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    WtAlertDialog(
        onDismissRequest = { if (geaendert) onGeaendert(); onDismiss() },
        title = { Text(stringResource(Res.string.desk_archive_albums).removeSuffix(" …")) },
        text = {
            Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (bilder.size > 1) Text(stringResource(Res.string.desk_archive_selected, bilder.size), style = MaterialTheme.typography.labelMedium)
                if (alben.isEmpty()) Text(stringResource(Res.string.desk_archive_albums_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(alben, key = { it.slug }) { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(drin[a.slug] == true, { an ->
                                drin[a.slug] = an
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { bilder.forEach { b -> b.pfad?.let { client.archiveAssign(tree, it, a.slug, an) } } } }
                                        .onSuccess { geaendert = true }
                                        .onFailure { drin[a.slug] = !an; fehler = fehlerText(it) }
                                }
                            })
                            Box(Modifier.size(10.dp).background(farbeAus(a.farbe) ?: Color.Transparent))
                            Text(a.name.ifBlank { a.slug } + "  (${a.anzahl})", Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(stringResource(Res.string.desk_archive_albums_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { if (geaendert) onGeaendert(); onDismiss() }) { Text(stringResource(Res.string.action_close)) } },
    )
}


/** Postkarte beschriften (Stufe 6): Motiv, Transkription, Notiz, Datum mit „unsicher“; das Modul schreibt beide Seiten. */
@Composable
fun PostkarteDialog(e: ArchiveEntry, onDismiss: () -> Unit, onSave: (PostkartenAnfrage) -> Unit) {
    var motiv by remember { mutableStateOf(e.motiv) }
    var transkription by remember { mutableStateOf(e.transkription) }
    var notiz by remember { mutableStateOf(e.notiz) }
    var datum by remember { mutableStateOf(e.datumIso.ifEmpty { e.datum }) }
    var unsicher by remember { mutableStateOf(e.datumUnsicher) }
    val datumOk = datum.isBlank() || Regex("""\d{4}(-\d{2}(-\d{2})?)?""").matches(datum.trim())
    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_postcard_edit).removeSuffix(" …")) },
        breite = 640.dp,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.datei, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(motiv, { motiv = it }, label = { Text(stringResource(Res.string.postcard_motif)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(transkription, { transkription = it }, label = { Text(stringResource(Res.string.postcard_transcription)) }, minLines = 5, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notiz, { notiz = it }, label = { Text(stringResource(Res.string.postcard_note)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(datum, { datum = it }, label = { Text(stringResource(Res.string.field_date)) }, supportingText = { Text(stringResource(Res.string.hint_iso_date)) },
                        isError = !datumOk, singleLine = true, modifier = Modifier.width(240.dp))
                    Checkbox(unsicher, { unsicher = it })
                    Text(stringResource(Res.string.postcard_date_uncertain))
                }
                Text(stringResource(Res.string.desk_postcard_both), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = datumOk, onClick = {
                // nur Geaendertes senden - was fehlt, laesst das Modul in der Datei
                onSave(PostkartenAnfrage(
                    motiv = motiv.takeIf { it != e.motiv }, transkription = transkription.takeIf { it != e.transkription },
                    notiz = notiz.takeIf { it != e.notiz }, datum = datum.trim().takeIf { it != e.datumIso.ifEmpty { e.datum } },
                    datumUnsicher = unsicher.takeIf { it != e.datumUnsicher },
                ))
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Einstellungen des Moduls (nur Administrator): Zwischenspeicher-Dauer, Seitengroesse, Zwischenspeicher leeren. */
@Composable
fun ArchivEinstellungenDialog(tree: String, client: WtClient, onDismiss: () -> Unit) {
    var daten by remember { mutableStateOf<ArchivEinstellungen?>(null) }
    var ttl by remember { mutableStateOf("") }
    var seite by remember { mutableStateOf("") }
    var meldung by remember { mutableStateOf<String?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tree) {
        runCatching { withContext(Dispatchers.IO) { client.archiveSettings(tree) } }
            .onSuccess { daten = it; ttl = it.cacheTtl.toString(); seite = it.seitenGroesse.toString() }
            .onFailure { fehler = fehlerText(it) }
    }
    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_archive_settings).removeSuffix(" …")) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                daten?.let { d ->
                    OutlinedTextField(ttl, { ttl = it.filter(Char::isDigit).take(6) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.desk_archive_cache_ttl, d.grenzen.cacheTtl.min, d.grenzen.cacheTtl.max)) })
                    OutlinedTextField(seite, { seite = it.filter(Char::isDigit).take(4) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.desk_archive_page_size, d.grenzen.seitenGroesse.min, d.grenzen.seitenGroesse.max)) })
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { client.archiveClearCache(tree) } }
                                .onSuccess { meldung = Texte.t(Res.string.desk_archive_cache_cleared) }.onFailure { fehler = fehlerText(it) }
                        }
                    }) { Text(stringResource(Res.string.desk_archive_cache_clear)) }
                }
                meldung?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = daten != null, onClick = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { client.archiveSaveSettings(tree, ttl.toIntOrNull() ?: 0, seite.toIntOrNull() ?: 0) } }
                        .onSuccess { onDismiss() }.onFailure { fehler = fehlerText(it) }
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
