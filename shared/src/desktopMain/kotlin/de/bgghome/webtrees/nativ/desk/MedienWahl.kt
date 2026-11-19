package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.bgghome.webtrees.nativ.api.MediaJson
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import de.bgghome.webtrees.nativ.api.ArchiveEntry
import de.bgghome.webtrees.nativ.api.ArchiveOverview
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.Field
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Medien an Quelle und Quellenverweis: wie in webtrees haengt ein Dokument entweder an der Quelle (das Digitalisat des
 * ganzen Kirchenbuchs) oder am einzelnen Verweis (der Scan dieser Taufe). Beides geht mit einer neuen Datei oder mit
 * einem Medium, das der Stammbaum schon hat.
 */

/**
 * Ein vorhandenes Medium des Stammbaums waehlen: alle Medien mit Vorschaubild und Suche im Titel. Laeuft das Modul
 * Sammlungen ([archive]), gibt es den Reiter "Aus dem Archiv": Ordner durchsehen, Datei waehlen - erst dann entsteht
 * dafuer ein Medienobjekt (oder das vorhandene wird genommen). Die Datei bleibt im Archiv.
 */
@Composable
fun MedienWahlDialog(
    tree: String, client: WtClient, schonDa: Set<String>, onDismiss: () -> Unit,
    archive: ArchiveOverview? = null, rechteXref: String = "", onWahl: (MediaJson) -> Unit,
) {
    var reiter by remember { mutableStateOf(0) }
    val archivDa = archive != null && archive.sammlungen.any { it.art == "ordner" || it.art == "thematisch" }
    val alle = remember { mutableStateListOf<MediaJson>() }
    var laden by remember { mutableStateOf(true) }
    var suche by remember { mutableStateOf("") }
    LaunchedEffect(tree) {
        var seite: Int? = 1
        while (seite != null) {
            val p = withContext(Dispatchers.IO) { runCatching { client.mediaList(tree, seite!!) }.getOrNull() } ?: break
            alle += p.data; seite = p.nextPage
        }
        laden = false
    }
    val treffer = alle.filter { it.xref !in schonDa && (suche.isBlank() || it.title.contains(suche.trim(), ignoreCase = true)) }.distinctBy { it.xref }
    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_media_pick_title)) },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (archivDa) Row(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
                    listOf(Res.string.desk_media_tab_tree, Res.string.desk_media_tab_archive).forEachIndexed { i, t ->
                        Text(stringResource(t), Modifier.weight(1f).background(if (reiter == i) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                            .clickable { reiter = i }.padding(vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = if (reiter == i) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                if (reiter == 1 && archive != null) {
                    ArchivWahl(tree, client, archive, schonDa, rechteXref) { onWahl(it); onDismiss() }
                    return@Column
                }
                Field(suche, { suche = it }, Res.string.desk_media_pick_search)
                Text(if (laden) stringResource(Res.string.desk_chart_loading_any) else stringResource(Res.string.desk_media_pick_count, treffer.size),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(360.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(treffer, key = { it.xref }) { m ->
                            Row(Modifier.fillMaxWidth().clickable { onWahl(m); onDismiss() }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
                                    if (m.thumb != null) AsyncImage(model = m.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
                                    else Text(m.mime.substringAfter('/').take(4), style = MaterialTheme.typography.labelSmall)
                                }
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text(m.title.ifBlank { m.xref }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(m.xref + (if (m.people.isNotEmpty()) " · " + m.people.joinToString(", ") { it.name } else ""), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Die Medien eines Verweises oder einer Quelle zum Bearbeiten: Vorschau, Entfernen (nur die Verknuepfung), Datei
 * hochladen (ohne Verknuepfung an die Person, die App haengt sie hier an) und vorhandenes Medium waehlen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedienBearbeiten(medien: List<MediaJson>, onEntfernen: (MediaJson) -> Unit, onDatei: () -> Unit, onVorhanden: () -> Unit, fehler: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.tab_media), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (medien.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            medien.forEach { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Tipp(m.title) {
                        Box(Modifier.size(72.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
                            if (m.thumb != null) AsyncImage(model = m.thumb, contentDescription = m.title, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
                            else Text(m.title, Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("✕", Modifier.clickable { onEntfernen(m) }.background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onDatei) { Text(stringResource(Res.string.desk_media_upload)) }
            TextButton(onClick = onVorhanden) { Text(stringResource(Res.string.desk_media_existing)) }
        }
        fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Der Reiter "Aus dem Archiv": links Ordner- und thematische Sammlungen, rechts die Dateien der gewaehlten mit Vorschau (Bilder und Dokumente). */
@Composable
private fun ArchivWahl(tree: String, client: WtClient, archive: ArchiveOverview, schonDa: Set<String>, rechteXref: String, onWahl: (MediaJson) -> Unit) {
    val ordner = archive.sammlungen.filter { it.art == "ordner" || it.art == "thematisch" }
    var gewaehlt by remember { mutableStateOf(ordner.firstOrNull()?.slug) }
    val eintraege = remember { mutableStateListOf<ArchiveEntry>() }
    var laden by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var beschaeftigt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(gewaehlt) {
        eintraege.clear(); fehler = null
        val slug = gewaehlt ?: return@LaunchedEffect
        laden = true
        var seite = 1
        while (true) {
            val p = withContext(Dispatchers.IO) { runCatching { client.collection(tree, slug, "", seite, 200) } }.getOrElse { fehler = meldung(it); null } ?: break
            // eintraege: Bilder; weitere: Dokumente (PDF ...) - fuer Quellen sind gerade die wichtig
            eintraege += p.eintraege + p.weitere
            if (seite >= p.seiten) break
            seite++
        }
        laden = false
    }
    Row(Modifier.fillMaxWidth().height(400.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(170.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
            LazyColumn { items(ordner, key = { it.slug }) { o ->
                Text((if (o.art == "thematisch") "★ " else "") + o.name.ifBlank { o.slug } + "  (${o.anzahl})", Modifier.fillMaxWidth().background(if (o.slug == gewaehlt) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { gewaehlt = o.slug }.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } }
        }
        Box(Modifier.weight(1f).height(400.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
            when {
                fehler != null -> Text(fehler!!, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                laden && eintraege.isEmpty() -> Text(stringResource(Res.string.desk_chart_loading_any), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(eintraege.filter { it.xref == null || it.xref !in schonDa }, key = { it.pfad ?: it.datei }) { e ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !beschaeftigt) {
                            beschaeftigt = true
                            scope.launch {
                                runCatching {
                                    val pfad = e.pfad ?: error("?")
                                    val xref = e.xref ?: client.mediaFromFile(tree, rechteXref, pfad, e.titel.ifBlank { e.datei.substringBeforeLast('.') }).media ?: error("?")
                                    MediaJson(xref = xref, title = e.titel.ifBlank { e.datei }, mime = if (e.istBild) "image/${e.format}" else "application/${e.format}", isImage = e.istBild, thumb = e.kachel)
                                }.onSuccess(onWahl).onFailure { fehler = meldung(it); beschaeftigt = false }
                            }
                        }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
                                if (e.kachel != null) AsyncImage(model = e.kachel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
                                else Text(e.format.take(4), style = MaterialTheme.typography.labelSmall)
                            }
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(e.titel.ifBlank { e.datei }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(e.datei, e.datum.takeIf { it.isNotBlank() }, if (e.xref != null) stringResource(Res.string.desk_media_in_tree) else null).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    Text(stringResource(Res.string.desk_media_archive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
