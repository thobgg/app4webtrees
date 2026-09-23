package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.Anniversary
import de.bgghome.webtrees.nativ.api.PendingRecord

/**
 * Bereich "Start": Begruessung, Baumkarte, eigene Person, ausstehende Aenderungen (nur Moderatoren),
 * Jahrestage der naechsten zwei Wochen und die zuletzt angesehenen Personen.
 */
@Composable
fun HomeSection(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    val user = state.info?.user
    val tree = state.tree

    Column(Modifier.fillMaxSize()) {
        TreeTitleBar(state, viewModel, openWeb)

        // Am Tablet nicht ueber die ganze Breite ziehen
        LazyColumn(
            Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val greeting = when {
                    user?.loggedIn == true -> stringResource(Res.string.home_greeting, user.realName.ifEmpty { user.userName })
                    else -> stringResource(Res.string.home_welcome)
                }
                Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            item {
                HomeCard {
                    Text(tree?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val facts = listOfNotNull(
                        tree?.individuals?.takeIf { it > 0 }?.let { stringResource(Res.string.tree_people_count, it) },
                        tree?.let { stringResource(Res.string.home_role, roleLabel(it.role)) },
                    )
                    Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.setSection(Section.Tree) }) { Text(stringResource(Res.string.home_open_tree)) }
                        OutlinedButton(onClick = { viewModel.setSection(Section.Search) }) { Text(stringResource(Res.string.nav_search)) }
                    }
                }
            }
            state.recent.firstOrNull { it.xref == state.home }?.let { home ->
                item {
                    HomeCard(padding = false) {
                        PersonRow(home, label = stringResource(Res.string.home_start_person), onClick = { viewModel.setRoot(home.xref) })
                    }
                }
            }
            if (state.pending.isNotEmpty()) {
                item { SectionHeading(stringResource(Res.string.home_pending, state.pending.size)) }
                item {
                    HomeCard(padding = false) {
                        state.pending.forEachIndexed { index, record ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PendingRow(record, onOpen = viewModel::setRoot, onDecide = { accept -> viewModel.moderate(record.xref, accept) })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.moderate(null, true) }) { Text(stringResource(Res.string.pending_accept_all)) }
                        }
                    }
                }
            }
            if (viewModel.anniversariesSupported) {
                item { SectionHeading(stringResource(Res.string.home_anniversaries)) }
                item {
                    HomeCard(padding = state.anniversaries.isEmpty()) {
                        if (state.anniversaries.isEmpty()) {
                            Text(stringResource(Res.string.anniv_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.anniversaries.take(8).forEachIndexed { index, anniversary ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            AnniversaryRow(anniversary, onOpen = viewModel::setRoot)
                        }
                    }
                }
            }
            item { SectionHeading(stringResource(Res.string.home_recent)) }
            if (state.recent.isEmpty()) {
                item { Text(stringResource(Res.string.home_recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item {
                    HomeCard(padding = false) {
                        state.recent.forEachIndexed { index, person ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PersonRow(person, onClick = { viewModel.setRoot(person.xref) })
                        }
                    }
                }
            }
        }
    }
}

/** Abschnittstitel in gesperrten Grossbuchstaben - wie beim Vorbild. */
@Composable
fun SectionHeading(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
    )
}

/** Ein Datensatz, dessen Aenderungen auf Freigabe warten - mit Annehmen/Verwerfen. */
@Composable
private fun PendingRow(record: PendingRecord, onOpen: (String) -> Unit, onDecide: (Boolean) -> Unit) {
    val kind = stringResource(
        when (record.kind) {
            "new" -> Res.string.pending_new
            "deleted" -> Res.string.pending_deleted
            else -> Res.string.pending_changed
        }
    )

    ListItem(
        overlineContent = { Text(listOf(kind, record.users.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ")) },
        headlineContent = { Text(record.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        // Knoepfe unter dem Namen: am Handy bliebe neben ihnen kein Platz fuer den Namen.
        supportingContent = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { onDecide(false) }) { Text(stringResource(Res.string.pending_reject)) }
                Button(onClick = { onDecide(true) }) { Text(stringResource(Res.string.pending_accept)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // Personen lassen sich vor der Entscheidung ansehen
        modifier = if (record.type == "INDI" && record.kind != "deleted") Modifier.clickable { onOpen(record.xref) } else Modifier,
    )
}

@Composable
private fun AnniversaryRow(anniversary: Anniversary, onOpen: (String) -> Unit) {
    val whoToOpen = anniversary.person ?: anniversary.couple.firstOrNull()
    val whenText = when (anniversary.inDays) {
        0 -> stringResource(Res.string.anniv_today)
        1 -> stringResource(Res.string.anniv_tomorrow)
        else -> stringResource(Res.string.anniv_in_days, anniversary.inDays)
    }
    val whenColor = if (anniversary.inDays == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    ListItem(
        leadingContent = whoToOpen?.let { { Avatar(it, 44.dp) } },
        overlineContent = { Text(whenText, color = whenColor) },
        headlineContent = { Text(anniversary.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(stringResource(Res.string.anniv_line, anniversary.label, anniversary.years)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (whoToOpen != null && !whoToOpen.isPrivate) Modifier.clickable { onOpen(whoToOpen.xref) } else Modifier,
    )
}

/** Weisse Karte mit Haarlinie auf dem hellgrauen Grund der Startseite. */
@Composable
private fun HomeCard(padding: Boolean = true, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Column(if (padding) Modifier.padding(16.dp) else Modifier) { content() }
    }
}
