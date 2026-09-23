package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Kleinteile, die ein Windows-Nutzer erwartet: sichtbare Bildlaufleisten und ein Rahmen um das, was gerade den
 * Tastaturfokus hat (Tab-Taste). Compose zeichnet beides nicht von selbst.
 */

/** Senkrechte Bildlaufleiste am rechten Rand einer Liste. */
@Composable
fun BoxScope.ListenLeiste(state: LazyListState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
}

/** Senkrechte Bildlaufleiste fuer einen verticalScroll-Bereich. */
@Composable
fun BoxScope.SenkrechteLeiste(state: ScrollState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
}

/** Waagerechte Bildlaufleiste fuer einen horizontalScroll-Bereich. */
@Composable
fun BoxScope.WaagerechteLeiste(state: ScrollState) {
    HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.BottomCenter).fillMaxWidth())
}

/** Rahmen, solange das Element den Tastaturfokus hat. Vor clickable/combinedClickable setzen. */
fun Modifier.fokusRahmen(): Modifier = composed {
    var fokus by remember { mutableStateOf(false) }
    val farbe = if (fokus) MaterialTheme.colorScheme.primary else Color.Transparent
    this.onFocusChanged { fokus = it.isFocused }.border(1.5.dp, farbe, MaterialTheme.shapes.extraSmall)
}
