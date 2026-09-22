package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.ArchiveCollection
import de.bgghome.webtrees.nativ.api.ArchiveEntry

// Das Archiv des Sammlungen-Moduls: die Uebersicht mit Karten je Sammlung und die Ansicht einer Sammlung.

/**
 * Uebersicht: Ordner-Sammlungen, thematische Sammlungen und Sammlungen nach Medientyp - in der Reihenfolge des Moduls -,
 * darunter die Medienobjekte ohne Person und der freie Bestand (nur gezaehlt, wie in der Galerie).
 */
@Composable
fun ArchiveOverviewList(state: UiState, viewModel: AppViewModel) {
    val archive = state.archive ?: return
    val groups = listOf(
        "ordner" to R.string.archive_folders,
        "thematisch" to R.string.archive_thematic,
        "medientyp" to R.string.archive_by_type,
    )

    LazyColumn(
        Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (archive.sammlungen.isEmpty()) {
            item { Text(stringResource(R.string.archive_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        groups.forEach { (art, heading) ->
            val collections = archive.sammlungen.filter { it.art == art }
            if (collections.isNotEmpty()) {
                item { SectionHeading(stringResource(heading)) }
                items(collections, key = { "$art/${it.slug}" }) { collection ->
                    CollectionCard(collection, onClick = { viewModel.openCollection(collection.slug) })
                }
            }
        }
        if (archive.unverknuepft.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.archive_unlinked)) }
            item {
                ArchiveCard(padding = false) {
                    archive.unverknuepft.forEachIndexed { index, type ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.openCollection("__unlinked__", type.typ) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(type.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                pluralStringResource(R.plurals.archive_files, type.anzahl, type.anzahl),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (archive.frei.gesamt > 0) {
            item { SectionHeading(stringResource(R.string.archive_free)) }
            item {
                ArchiveCard {
                    Text(
                        stringResource(R.string.archive_free_text, archive.frei.gesamt, archive.frei.dateien),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    archive.frei.jeOrdner.take(8).forEach { folder ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(
                                folder.ordner.ifEmpty { stringResource(R.string.archive_root_folder) }, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(folder.anzahl.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Karte einer Sammlung: Farbbalken, Name, Anzahl und Beschreibung, rechts bis zu drei Vorschaubilder. */
@Composable
private fun CollectionCard(collection: ArchiveCollection, onClick: () -> Unit) {
    val accent = collection.farbe?.let(::parseColor) ?: MaterialTheme.colorScheme.primary

    ArchiveCard(padding = false, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(72.dp).background(accent))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(collection.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = listOf(
                    pluralStringResource(R.plurals.archive_files, collection.anzahl, collection.anzahl),
                    collection.beschreibung,
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (collection.vorschau.isNotEmpty()) {
                Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    collection.vorschau.take(3).forEach { url ->
                        Thumbnail(url, collection.name, Modifier.size(52.dp))
                    }
                }
            }
        }
    }
}

/**
 * Eine Sammlung: Kopfzeile mit Zurueck-Pfeil, dann das Raster. Bilder als Kacheln mit Unterschrift; was kein Bild ist
 * (Dokumente eines gemischten Ordners, die "weiteren" Dateien eines Foto-Ordners), als Zeile ueber die ganze Breite.
 */
@Composable
fun CollectionView(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    val collection = state.collection
    val entries = state.collectionEntries
    val extra = collection?.weitere.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::closeCollection) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(Modifier.weight(1f)) {
                Text(collection?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (collection != null) {
                    Text(
                        pluralStringResource(R.plurals.archive_files, collection.anzahl, collection.anzahl),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.loadingCollection) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (collection != null && entries.isEmpty() && extra.isEmpty() && !state.loadingCollection) {
            Text(stringResource(R.string.collection_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        val dense = state.denseGrid
        val gap = if (dense) 2.dp else 8.dp

        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (dense) 110.dp else 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gap, end = gap, top = 4.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(entries, span = { if (it.istBild) GridItemSpan(1) else GridItemSpan(maxLineSpan) }) { entry ->
                if (entry === entries.last()) LaunchedEffect(entries.size) { viewModel.loadMoreCollection() }

                if (entry.istBild) {
                    Column(Modifier.clickable { viewModel.openArchiveViewer(entry) }) {
                        Box {
                            Thumbnail(entry.kachel, entry.bildunterschrift, rounded = !dense)
                            LinkBadge(entry, Modifier.align(Alignment.BottomEnd).padding(if (dense) 3.dp else 6.dp))
                        }
                        if (!dense) Text(
                            entry.bildunterschrift.ifEmpty { entry.datei }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                        )
                        val people = entry.personen.joinToString(", ") { it.name }.ifEmpty { entry.datum }
                        if (!dense && people.isNotEmpty()) {
                            Text(
                                people, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                } else {
                    FileRow(entry, onClick = { openFile(entry, viewModel, openWeb) })
                }
            }
            if (extra.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeading(stringResource(R.string.archive_more_files)) }
                items(extra, span = { GridItemSpan(maxLineSpan) }) { entry ->
                    FileRow(entry, onClick = { openFile(entry, viewModel, openWeb) })
                }
            }
        }
    }
}

/**
 * Kennzeichen auf der Kachel, wie das Bild am Stammbaum haengt: Personensymbol mit Zahl, "GEDCOM" fuer ein
 * Medienobjekt ohne Person, "frei" fuer eine Datei, die im Stammbaum gar nicht vorkommt - die Menge, um die es dem
 * Archiv geht.
 */
@Composable
fun LinkBadge(entry: ArchiveEntry, modifier: Modifier = Modifier) {
    val (text, tone) = when (linkState(entry)) {
        LinkState.Persons -> (entry.personenGesamt.takeIf { it > 0 } ?: entry.personen.size).toString() to MaterialTheme.colorScheme.primaryContainer
        LinkState.ObjectOnly -> stringResource(R.string.archive_badge_gedcom) to MaterialTheme.colorScheme.surfaceVariant
        LinkState.FileOnly -> stringResource(R.string.archive_badge_free) to MaterialTheme.colorScheme.tertiaryContainer
    }
    val onTone = when (linkState(entry)) {
        LinkState.Persons -> MaterialTheme.colorScheme.onPrimaryContainer
        LinkState.ObjectOnly -> MaterialTheme.colorScheme.onSurfaceVariant
        LinkState.FileOnly -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(modifier, shape = RoundedCornerShape(10.dp), color = tone.copy(alpha = 0.92f)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (linkState(entry) == LinkState.Persons) {
                Icon(Icons.Default.Person, contentDescription = null, tint = onTone, modifier = Modifier.size(14.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = onTone)
        }
    }
}

/** PDFs im eigenen Betrachter, alles andere (Ton, Video, Office) als webtrees-Seite bzw. Datei im Browser-Fenster. */
private fun openFile(entry: ArchiveEntry, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    if (entry.format == "pdf") viewModel.openPdf(entry.original, entry.titel.ifEmpty { entry.datei }, entry.seite)
    else openWeb(entry.seite ?: entry.original)
}

/** Eine Datei, die kein Bild ist: Formatkuerzel im Kasten, dann Titel oder Dateiname. */
@Composable
private fun FileRow(entry: ArchiveEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val chip: @Composable () -> Unit = {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    entry.format.uppercase().ifEmpty { "?" }, Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (entry.format == "pdf") {
            // Die erste Seite als Vorschau, dazu das Kennzeichen; ist die Datei zu gross fuer eine Vorschau, bleibt das Kuerzel.
            PdfThumbnail(entry.original, Modifier.width(64.dp).height(84.dp)) { bitmap ->
                if (bitmap == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { chip() }
                } else {
                    Image(
                        bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                    )
                    Surface(
                        Modifier.align(Alignment.BottomEnd).padding(3.dp), shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                    ) {
                        Text("PDF", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        } else {
            chip()
        }
        Column(Modifier.weight(1f)) {
            Text(entry.titel.ifEmpty { entry.datei }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.titel.isNotEmpty()) {
                Text(entry.datei, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArchiveCard(padding: Boolean = true, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(if (padding) Modifier.padding(16.dp) else Modifier) { content() }
    }
}

/** "#rrggbb" aus dem Modul; was sich nicht lesen laesst, wird null. */
private fun parseColor(hex: String): Color? =
    hex.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
