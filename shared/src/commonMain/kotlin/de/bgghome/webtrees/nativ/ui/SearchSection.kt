package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.*

/** Bereich "Suche": Personenliste mit Suchfeld; die Liste laedt beim Scrollen nach. Ein Treffer wird zur Mittelperson. */
@Composable
fun SearchSection(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TreeTitleBar(state, viewModel, openWeb)

        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.widthIn(max = 720.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::search,
                    placeholder = { Text(stringResource(Res.string.tree_find)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.search("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.search_clear))
                            }
                        }
                    },
                    singleLine = true, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )

                if (state.people.isEmpty() && !state.loadingPeople) {
                    Text(
                        stringResource(if (state.query.isEmpty()) Res.string.list_empty else Res.string.list_nothing_found),
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.people, key = { _, person -> person.xref }) { index, person ->
                        // Kurz vor dem Ende die naechste Seite holen
                        if (index >= state.people.size - 10) LaunchedEffect(state.nextPage) { viewModel.loadMore() }

                        PersonRow(person, selected = person.xref == state.root, onClick = { viewModel.setRoot(person.xref) })
                    }
                    if (state.loadingPeople) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
