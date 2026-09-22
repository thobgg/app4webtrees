package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.ui.tree.FamilyTreeView
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout

/**
 * Bereich "Baum", der Hauptbildschirm: Kopfzeile, Suchfeld, der Baum selbst - und je nach Breite das Profil
 * daneben (Tablet) oder, nach einem Tipp auf eine Karte, als eigene Seite (Handy).
 */
@Composable
fun TreeSection(state: UiState, viewModel: AppViewModel, wide: Boolean, openWeb: (String) -> Unit) {
    val detail = state.detail

    // Handy: das Profil ist eine eigene Seite; bis die Daten der angetippten Person da sind, ein Ladekreis.
    if (!wide && state.profileOpen) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(stringResource(R.string.action_profile), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (detail != null && detail.person.xref == state.selected) {
                ProfilePanel(state, detail, viewModel, openWeb, onClose = null)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (!state.treeFullscreen) {
            TreeTitleBar(state, viewModel, openWeb) {
                GenerationsChip(state, viewModel)
                TreeSettings(state, viewModel)
            }
            FindPersonField { viewModel.setSection(Section.Search) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(Modifier.fillMaxSize()) {
            // Tablet: Profil dauerhaft links neben dem Baum
            if (wide && !state.treeFullscreen && state.selected != null) {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    if (detail != null) {
                        ProfilePanel(state, detail, viewModel, openWeb, onClose = viewModel::closePanel)
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                TreeCanvas(state, viewModel, wide)
            }
        }
    }
}

/** "Person finden ..." - sieht aus wie ein Suchfeld, fuehrt aber in den Bereich Suche. */
@Composable
private fun FindPersonField(onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.tree_find), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TreeCanvas(state: UiState, viewModel: AppViewModel, wide: Boolean) {
    val pedigree = state.pedigree
    val descendants = state.descendants
    val canEdit = state.tree?.canEdit == true

    // Nach jedem Wechsel der Mittelperson und nach jedem Schreiben sind die Daten geleert -> neu laden.
    LaunchedEffect(state.root, pedigree == null || descendants == null) {
        if (state.root != null && (pedigree == null || descendants == null)) viewModel.loadChart()
    }

    val siblings = if (state.showSiblings) state.siblings.orEmpty() else emptyMap()
    val cousins = state.showSiblings && state.showCousins
    val layout = remember(pedigree, descendants, canEdit, siblings, cousins) {
        if (pedigree != null && descendants != null) TreeLayout.build(pedigree, descendants.tree, canEdit, siblings, cousins) else null
    }

    FamilyTreeView(
        layout = layout,
        selected = state.selected,
        fullscreen = state.treeFullscreen,
        initialScale = if (wide) 1f else 0.8f,
        compact = !wide,
        onToggleFullscreen = { viewModel.setTreeFullscreen(!state.treeFullscreen) },
        onPerson = { viewModel.openPerson(it.xref, wide) },
        onPlus = { viewModel.requestAddRelative(it.xref) },
        onExpand = viewModel::expandAncestors,
    )
}

/** Wie viele Ahnen-Generationen der Baum zeigt (2 bis 6). */
@Composable
private fun GenerationsChip(state: UiState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.tree_generations, state.ancestorGenerations))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (2..6).forEach { n ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tree_generations, n)) },
                    onClick = { open = false; viewModel.setAncestorGenerations(n) },
                )
            }
        }
    }
}

/** Zahnrad: Einstellungen der Baumansicht - wie das Einstellungsblatt beim Vorbild, nur kuerzer. */
@Composable
private fun TreeSettings(state: UiState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.tree_settings)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tree_show_siblings)) },
                leadingIcon = { Checkbox(checked = state.showSiblings, onCheckedChange = null) },
                onClick = { open = false; viewModel.setShowSiblings(!state.showSiblings) },
            )
            // Cousins gibt es nur mit Geschwistern - sie haengen unter den Geschwistern der Eltern
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tree_show_cousins)) },
                leadingIcon = { Checkbox(checked = state.showCousins && state.showSiblings, onCheckedChange = null, enabled = state.showSiblings) },
                enabled = state.showSiblings,
                onClick = { open = false; viewModel.setShowCousins(!state.showCousins) },
            )
        }
    }
}
