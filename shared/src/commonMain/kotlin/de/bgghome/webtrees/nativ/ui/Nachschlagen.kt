package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.API_PLACE_LIST
import de.bgghome.webtrees.nativ.api.API_SOURCES
import de.bgghome.webtrees.nativ.api.MediaJson
import de.bgghome.webtrees.nativ.api.PlaceDetail
import de.bgghome.webtrees.nativ.api.PlaceSummaryList
import de.bgghome.webtrees.nativ.api.SourceDetail
import de.bgghome.webtrees.nativ.api.SourceList
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.karte.GeoPunkt
import de.bgghome.webtrees.nativ.ui.karte.KachelEbene
import de.bgghome.webtrees.nativ.ui.karte.KachelKarte
import de.bgghome.webtrees.nativ.ui.karte.KartenPin
import de.bgghome.webtrees.nativ.ui.karte.KartenZustand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Nachschlagen am Handy und Tablet: Quellen und Orte als eigene Seiten, nur lesend. Erreichbar ueber das Drei-Punkte-Menue
 * und aus dem Lebenslauf (Quellenverweise und Ortsnamen sind dort anklickbar). Die Seiten liegen als Stapel ueber dem
 * Hauptbildschirm; Zurueck nimmt die oberste weg. Bearbeiten bleibt am PC (wtWin/wtTux).
 */

/** Eine Nachschlage-Seite. */
sealed interface Nachschlag {
    data object QuellenListe : Nachschlag
    data class Quelle(val xref: String) : Nachschlag
    data object OrteListe : Nachschlag
    data class Ort(val name: String) : Nachschlag
    /** Posteingang aus webtrees' Nachrichtensystem (Nachrichten.kt) */
    data object Nachrichten : Nachschlag
}

/** Oeffnet eine Nachschlage-Seite (am Handy von AppRoot gesetzt, am Desktop null - dort gibt es eigene Fenster). */
val LocalNachschlagen = staticCompositionLocalOf<((Nachschlag) -> Unit)?> { null }

/** Ob der Server Quellen bzw. Orte zum Nachschlagen anbietet. */
val AppViewModel.quellenNachschlagbar: Boolean get() = (uiState.value.info?.api ?: 0) >= API_SOURCES
val AppViewModel.orteNachschlagbar: Boolean get() = (uiState.value.info?.api ?: 0) >= API_PLACE_LIST

@Composable
fun NachschlagSeite(
    seite: Nachschlag, state: UiState, viewModel: AppViewModel,
    onOeffnen: (Nachschlag) -> Unit, onZurueck: () -> Unit, onPerson: (String) -> Unit,
    /** Oeffnet eine webtrees-Seite im eingebetteten Browser (Links in Nachrichten auf Datensaetze ohne eigene Seite) */
    onWeb: (String) -> Unit = {},
) {
    val tree = state.tree?.name ?: return
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (seite) {
            Nachschlag.Nachrichten -> NachrichtenSeite(state, viewModel, onOeffnen, onZurueck, onPerson, onWeb)
            Nachschlag.QuellenListe -> QuellenListeSeite(tree, viewModel, onOeffnen, onZurueck)
            is Nachschlag.Quelle -> QuelleSeite(tree, seite.xref, viewModel, onZurueck, onPerson)
            Nachschlag.OrteListe -> OrteListeSeite(tree, viewModel, onOeffnen, onZurueck)
            is Nachschlag.Ort -> OrtSeite(tree, seite.name, viewModel, onOeffnen, onZurueck, onPerson)
        }
    }
}

@Composable
internal fun Kopf(titel: String, onZurueck: () -> Unit, rechts: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back)) }
        Text(titel, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        rechts?.invoke()
    }
}

@Composable
private fun Laden(ergebnis: Result<*>?, inhalt: @Composable () -> Unit) {
    when {
        ergebnis == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        ergebnis.isFailure -> Text((ergebnis.exceptionOrNull() as? Exception)?.let(::explain) ?: "?", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        else -> inhalt()
    }
}

@Composable
private fun Suchfeld(text: String, platzhalter: String, onText: (String) -> Unit) =
    OutlinedTextField(text, onText, placeholder = { Text(platzhalter) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))

@Composable
private fun Abschnitt(titel: String) =
    Text(titel, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Feld(titel: String, wert: String?) {
    if (wert.isNullOrBlank()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Zeile(oben: String, unten: String?, rechts: String? = null, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(oben, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!unten.isNullOrBlank()) Text(unten, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        rechts?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Fotos und Dokumente (Quelle, Ort): Bilder oeffnen im Betrachter, PDFs im PDF-Betrachter. */
@Composable
private fun Medien(medien: List<MediaJson>, viewModel: AppViewModel) {
    if (medien.isEmpty()) return
    Abschnitt(stringResource(Res.string.tab_media))
    MediaGrid(medien.filter { it.isImage }, onOpen = { m -> viewModel.openMediaViewer(ViewerSource.Profile, medien, m) },
        documents = medien.filter { !it.isImage },
        onOpenDocument = { m -> if (m.mime == "application/pdf") viewModel.openPdf(m.file, m.title, m.url) })
}

// ── Quellen ──

@Composable
private fun QuellenListeSeite(tree: String, viewModel: AppViewModel, onOeffnen: (Nachschlag) -> Unit, onZurueck: () -> Unit) {
    val liste by produceState<Result<SourceList>?>(null, tree) { value = withContext(Dispatchers.Default) { runCatching { viewModel.client.sources(tree) } } }
    var suche by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Kopf(stringResource(Res.string.desk_sources_window), onZurueck)
        Suchfeld(suche, stringResource(Res.string.desk_sources_search)) { suche = it }
        Laden(liste) {
            val alle = liste!!.getOrThrow().sources
            val q = suche.trim()
            val treffer = if (q.isEmpty()) alle else alle.filter { s -> listOf(s.title, s.author, s.publication, s.abbreviation, s.repository, s.callNumber).any { it.contains(q, ignoreCase = true) } }
            Text(stringResource(Res.string.desk_sources_count, treffer.size, alle.size), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(treffer, key = { it.xref }) { s ->
                    Zeile(s.title.ifBlank { s.xref }, listOf(s.author, listOf(s.repository, s.callNumber).filter { it.isNotBlank() }.joinToString(" · ")).filter { it.isNotBlank() }.joinToString("\n"),
                        rechts = s.uses.takeIf { it > 0 }?.toString()) { onOeffnen(Nachschlag.Quelle(s.xref)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun QuelleSeite(tree: String, xref: String, viewModel: AppViewModel, onZurueck: () -> Unit, onPerson: (String) -> Unit) {
    val quelle by produceState<Result<SourceDetail>?>(null, tree, xref) { value = withContext(Dispatchers.Default) { runCatching { viewModel.client.source(tree, xref) } } }
    Column(Modifier.fillMaxSize()) {
        Kopf(quelle?.getOrNull()?.title?.ifBlank { null } ?: stringResource(Res.string.desk_sources_window), onZurueck)
        Laden(quelle) {
            val q = quelle!!.getOrThrow()
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                item {
                    Feld(stringResource(Res.string.desk_source_author), q.author)
                    Feld(stringResource(Res.string.desk_source_publication), q.publication)
                    Feld(stringResource(Res.string.desk_source_abbreviation), q.abbreviation)
                    val archive = q.repositories.ifEmpty { null }?.joinToString("\n") { r -> listOf(r.name, r.callNumber).filter { it.isNotBlank() }.joinToString(" · ") }
                        ?: listOf(q.repository, q.callNumber).filter { it.isNotBlank() }.joinToString(" · ")
                    Feld(stringResource(Res.string.desk_source_repository), archive)
                    Feld(stringResource(Res.string.desk_source_text), q.text)
                    if (q.notes.isNotEmpty()) Feld(stringResource(Res.string.desk_tab_notes), q.notes.joinToString("\n\n"))
                    Medien(q.media, viewModel)
                    if (q.individuals.isNotEmpty() || q.families.isNotEmpty()) Abschnitt(stringResource(Res.string.desk_source_cited_by))
                }
                items(q.individuals, key = { "i" + it.xref }) { p ->
                    Zeile(p.name.ifBlank { p.xref }, listOf(p.lifespan, p.facts.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = if (p.isPrivate) null else ({ onPerson(p.xref) }))
                }
                items(q.families, key = { "f" + it.xref }) { f ->
                    Zeile(f.name.ifBlank { f.xref }, f.facts.joinToString(", "), onClick = f.husband?.let { h -> { onPerson(h) } } ?: f.wife?.let { w -> { onPerson(w) } })
                }
                if (q.moreIndividuals + q.moreFamilies > 0) item {
                    Text(stringResource(Res.string.desk_source_more, q.moreIndividuals + q.moreFamilies), Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Orte ──

@Composable
private fun OrteListeSeite(tree: String, viewModel: AppViewModel, onOeffnen: (Nachschlag) -> Unit, onZurueck: () -> Unit) {
    val liste by produceState<Result<PlaceSummaryList>?>(null, tree) { value = withContext(Dispatchers.Default) { runCatching { viewModel.client.placeList(tree) } } }
    var suche by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Kopf(stringResource(Res.string.desk_places_window), onZurueck)
        Suchfeld(suche, stringResource(Res.string.desk_places_search)) { suche = it }
        Laden(liste) {
            val alle = liste!!.getOrThrow().places
            val q = suche.trim()
            val treffer = (if (q.isEmpty()) alle else alle.filter { it.name.contains(q, ignoreCase = true) })
                .sortedWith(compareBy(de.bgghome.webtrees.nativ.data.Ortsnamen.NATUERLICH) { it.name })
            Text(stringResource(Res.string.desk_places_count, treffer.size, alle.size), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(treffer, key = { it.name }) { p ->
                    Zeile(p.name, p.type, rechts = p.events.takeIf { it > 0 }?.toString()) { onOeffnen(Nachschlag.Ort(p.name)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun OrtSeite(tree: String, name: String, viewModel: AppViewModel, onOeffnen: (Nachschlag) -> Unit, onZurueck: () -> Unit, onPerson: (String) -> Unit) {
    val ort by produceState<Result<PlaceDetail>?>(null, tree, name) { value = withContext(Dispatchers.Default) { runCatching { viewModel.client.place(tree, name) } } }
    Column(Modifier.fillMaxSize()) {
        Kopf(name, onZurueck)
        Laden(ort) {
            val o = ort!!.getOrThrow()
            val loc = o.location
            val lat = loc?.lat ?: o.lat
            val lng = loc?.lng ?: o.lng
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                item {
                    o.parent?.let { p -> Zeile(stringResource(Res.string.desk_place_parent) + ": " + p, null) { onOeffnen(Nachschlag.Ort(p)) } }
                    Feld(stringResource(Res.string.desk_place_type), loc?.type)
                    Feld(stringResource(Res.string.desk_place_gov), loc?.gov)
                    o.eventCounts?.let { k ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Res.string.desk_place_births to k.birth, Res.string.desk_place_marriages to k.marriage, Res.string.desk_place_deaths to k.death)
                                .forEach { (t, n) ->
                                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(n.toString(), style = MaterialTheme.typography.titleLarge)
                                            Text(stringResource(t), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                        }
                    }
                    if (lat != null && lng != null) {
                        val zustand = remember(name) { KartenZustand(lat, lng, 12) }
                        Box(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            KachelKarte(zustand, KachelEbene.STANDARD, Modifier.fillMaxSize(), pins = listOf(KartenPin(lat, lng, farbe = MaterialTheme.colorScheme.primary, radiusDp = 9f)))
                        }
                        LaunchedEffect(name) { zustand.setze(lat, lng, 12) }
                    }
                    loc?.notes?.takeIf { it.isNotEmpty() }?.let { Feld(stringResource(Res.string.desk_tab_notes), it.joinToString("\n\n")) }
                    if (!loc?.events.isNullOrEmpty()) {
                        Abschnitt(stringResource(Res.string.desk_place_tab_history))
                        loc!!.events.forEach { ev ->
                            Zeile(listOfNotNull(ev.date?.text, ev.label.ifBlank { ev.type }).joinToString(" · "), (listOfNotNull(ev.value) + ev.notes).joinToString("\n"), onClick = null)
                        }
                    }
                    Medien(loc?.media.orEmpty(), viewModel)
                    if (o.children.isNotEmpty()) {
                        Abschnitt(stringResource(Res.string.desk_place_children))
                        o.children.forEach { c -> Zeile(c.name, c.type, rechts = c.events.takeIf { it > 0 }?.toString()) { onOeffnen(Nachschlag.Ort(c.name)) } }
                    }
                    if (o.individuals.isNotEmpty()) Abschnitt(stringResource(Res.string.desk_place_tab_people))
                }
                items(o.individuals, key = { "i" + it.xref }) { p ->
                    Zeile(p.name.ifBlank { p.xref }, listOf(p.lifespan, p.facts.joinToString(", ") { f -> listOfNotNull(f.label, f.date?.text).joinToString(" ") }).filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = if (p.isPrivate) null else ({ onPerson(p.xref) }))
                }
                if (o.families.isNotEmpty()) item { Abschnitt(stringResource(Res.string.desk_place_families)) }
                items(o.families, key = { "f" + it.xref }) { f ->
                    Zeile(f.name.ifBlank { f.xref }, f.facts.joinToString(", ") { e -> listOfNotNull(e.label, e.date?.text).joinToString(" ") },
                        onClick = f.husband?.let { h -> { onPerson(h) } } ?: f.wife?.let { w -> { onPerson(w) } })
                }
                if (o.moreIndividuals + o.moreFamilies > 0) item {
                    Text(stringResource(Res.string.desk_source_more, o.moreIndividuals + o.moreFamilies), Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
