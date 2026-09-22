package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.Person

// Bausteine, die mehrere Bereiche teilen: die Kopfzeile mit dem Baumnamen und die Personenzeile.

/** Kopfzeile: Baumname mit Wechsel-Pfeil und Personenzahl - wie beim Vorbild. Rechts das Drei-Punkte-Menue. */
@Composable
fun TreeTitleBar(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, trailing: @Composable () -> Unit = {}) {
    val tree = state.tree
    val trees = state.info?.trees.orEmpty()
    val canSwitch = trees.size > 1
    // Baumwechsel als Aufklappliste direkt unter dem Namen - keine eigene Seite, kein Weg, der nicht zurueckfuehrt
    var pickerOpen by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Column(if (canSwitch) Modifier.clickable { pickerOpen = true } else Modifier) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tree?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (canSwitch) Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.menu_switch_tree))
                    }
                    if ((tree?.individuals ?: 0) > 0) {
                        Text(
                            stringResource(R.string.tree_people_count, tree!!.individuals),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    trees.forEach { candidate ->
                        val current = candidate.name == tree?.name
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(candidate.title, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                                    Text(roleLabel(candidate.role), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = if (current) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                            onClick = { pickerOpen = false; if (!current) viewModel.chooseTree(candidate) },
                        )
                    }
                }
            }
            trailing()
            MainMenu(state, viewModel, openWeb)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Eine Person als Listenzeile: Portraet, Name, Lebensdaten und Geburtsort. Private Personen (webtrees zeigt sie
 * Besuchern nur als "Privat") sind nicht antippbar - es gaebe nichts zu sehen.
 */
@Composable
fun PersonRow(
    person: Person,
    selected: Boolean = false,
    label: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val name = when {
        person.isPrivate -> stringResource(R.string.person_private)
        else -> person.name.ifEmpty { stringResource(R.string.person_no_name) }
    }
    val place = person.birth?.place?.short.orEmpty()

    ListItem(
        leadingContent = { Avatar(person, 44.dp) },
        trailingContent = trailing,
        overlineContent = label?.let { { Text(it) } },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(listOf(person.lifespan, place).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = if (onClick != null && !person.isPrivate) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
