package de.bgghome.webtrees.nativ.ui.tree

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.Avatar
import de.bgghome.webtrees.nativ.ui.forSex
import de.bgghome.webtrees.nativ.ui.relationLabel
import de.bgghome.webtrees.nativ.ui.treeColors
import kotlinx.coroutines.launch

private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 2.5f

/**
 * Der Baum als frei verschieb- und zoombare Flaeche, Karten nach dem Vorbild MyHeritage.
 *
 * Tipps werden hier selbst ausgewertet (Bildschirmpunkt -> Baumkoordinate -> Karte) statt ueber
 * clickable() an den Karten: Compose liefert Beruehrungen nicht an Kinder, die ausserhalb der
 * Grenzen ihres Elternelements liegen - und genau das sind bei einem grossen Baum die meisten.
 *
 * @param initialScale Start-Zoom: am Handy kleiner, damit mehr Baum auf den Bildschirm passt.
 */
@Composable
fun FamilyTreeView(
    layout: TreeLayout?,
    selected: String?,
    fullscreen: Boolean,
    initialScale: Float,
    /** Handy: nur die zwei Knoepfe, die keine Geste ersetzt (zur Mittelperson, alles zeigen). */
    compact: Boolean,
    onToggleFullscreen: () -> Unit,
    onPerson: (Person) -> Unit,
    onPlus: (Person) -> Unit,
    onPlaceholder: (Placeholder) -> Unit,
    onExpand: (n: Int, xref: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layout == null) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val density = LocalDensity.current.density
    val lineColor = treeColors.connector
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background)) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()

        // Neue Mittelperson -> in die Mitte ruecken, Zoom behalten.
        val focusKey = layout.focus.person?.xref
        var scale by remember { mutableFloatStateOf(initialScale) }
        var offset by remember(focusKey) {
            mutableStateOf(Offset(viewW / 2 - layout.focus.centerX * density * scale, viewH / 2 - layout.focus.centerY * density * scale))
        }

        // Gleiche Mittelperson, aber neuer Baum (jemand wurde hinzugefuegt): die Koordinaten verschieben sich,
        // weil der Baum breiter wird - so nachfuehren, dass die Mittelperson an ihrer Bildschirmstelle bleibt.
        var anchor by remember(focusKey) { mutableStateOf(Offset(layout.focus.centerX, layout.focus.centerY)) }
        val current = Offset(layout.focus.centerX, layout.focus.centerY)
        if (anchor != current) {
            offset += (anchor - current) * (density * scale)
            anchor = current
        }

        fun zoomBy(factor: Float, around: Offset) {
            val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            val applied = newScale / scale
            offset = (offset - around) * applied + around
            scale = newScale
        }

        fun fitAll() {
            val fit = minOf(viewW / (layout.width * density), viewH / (layout.height * density)).coerceIn(MIN_SCALE, 1f)
            scale = fit
            offset = Offset((viewW - layout.width * density * fit) / 2, (viewH - layout.height * density * fit) / 2)
        }

        /** Karte sanft in die Bildmitte holen (wie beim Vorbild nach einem Tipp). */
        fun centerOn(box: TreeBox) {
            val target = Offset(viewW / 2 - box.centerX * density * scale, viewH / 2 - box.centerY * density * scale)
            scope.launch {
                Animatable(offset, Offset.VectorConverter).animateTo(target) { offset = value }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        zoomBy(zoom, centroid)
                        offset += pan
                    }
                }
                .pointerInput(layout) {
                    detectTapGestures(
                        onDoubleTap = { zoomBy(1.6f, it) },
                        onTap = { tap ->
                            val x = (tap.x - offset.x) / (scale * density)
                            val y = (tap.y - offset.y) / (scale * density)

                            val expand = layout.expandAt(x, y)
                            val plus = layout.plusAt(x, y)
                            val box = layout.boxAt(x, y)
                            when {
                                expand?.person != null && expand.ahnen != null -> onExpand(expand.ahnen, expand.person.xref)
                                plus?.person != null -> onPlus(plus.person)
                                box?.placeholder != null -> onPlaceholder(box.placeholder)
                                box?.person != null && !box.person.isPrivate -> {
                                    centerOn(box)
                                    onPerson(box.person)
                                }
                            }
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredSize(layout.width.dp, layout.height.dp),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(
                        width = 1.3f * density, cap = StrokeCap.Round,
                        pathEffect = PathEffect.cornerPathEffect(10f * density),
                    )
                    layout.connectors.forEach { line ->
                        val path = Path()
                        line.points.forEachIndexed { index, (x, y) ->
                            if (index == 0) path.moveTo(x * density, y * density) else path.lineTo(x * density, y * density)
                        }
                        drawPath(path, lineColor, style = stroke)
                    }
                }

                layout.boxes.forEach { box ->
                    TreeCard(box, isSelected = box.person != null && box.person.xref == selected, showPlus = layout.hasPlus(box))
                }
            }
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!compact || fullscreen) ToolButton(if (fullscreen) "⤡" else "⤢", stringResource(R.string.tree_fullscreen), onToggleFullscreen)
            ToolButton("◎", stringResource(R.string.tree_center)) { centerOn(layout.focus) }
            ToolButton("▣", stringResource(R.string.tree_fit)) { fitAll() }
            if (!compact) {
                ToolButton("+", stringResource(R.string.tree_zoom_in)) { zoomBy(1.4f, Offset(viewW / 2, viewH / 2)) }
                ToolButton("−", stringResource(R.string.tree_zoom_out)) { zoomBy(1 / 1.4f, Offset(viewW / 2, viewH / 2)) }
            }
        }
    }
}

@Composable
private fun ToolButton(glyph: String, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Text(glyph, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TreeCard(box: TreeBox, isSelected: Boolean, showPlus: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val base = Modifier.offset(box.x.dp, box.y.dp).size(TreeLayout.BOX_W.dp, TreeLayout.BOX_H.dp)

    // Geisterkarte: "Vater hinzufuegen" ...
    val placeholder = box.placeholder
    if (placeholder != null) {
        Row(
            base.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlusBadge(28)
            Text(
                relationLabel(placeholder.relation),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge,
            )
        }
        return
    }

    val person = box.person ?: return
    val gender = treeColors.forSex(person.sex)

    Box(base) {
        Row(
            Modifier
                .fillMaxSize()
                // Mittelperson: hebt sich mit Schatten ab. Gewaehlte Karte (Profil-Panel): kraeftiger Rahmen.
                .then(if (box.isFocus) Modifier.shadow(10.dp, shape) else Modifier)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(if (isSelected) 2.5.dp else 1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else gender, shape)
                .clip(shape)
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(person, 42.dp)
            Column {
                Text(
                    if (person.isPrivate) stringResource(R.string.person_private) else person.name,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                )
                if (person.lifespan.isNotBlank() && !person.isPrivate) {
                    Text(person.lifespan, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Schwarze Eck-Schleife fuer Verstorbene
        if (person.isDead && !person.isPrivate) {
            Canvas(Modifier.size(20.dp).clip(RoundedCornerShape(topStart = 8.dp))) {
                val ribbon = Path().apply {
                    moveTo(size.width * 0.45f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); lineTo(0f, size.height * 0.45f); close()
                }
                drawPath(ribbon, Color(0xFF1B1B1B))
            }
        }

        // Zweig-Symbol: diese Person hat Eltern, die noch nicht geladen sind - Tipp klappt den Baum nach oben auf
        if (box.canExpand) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = (-TreeLayout.EXPAND_OFFSET - 11f).dp)) { ExpandBadge() }
        }

        // "+"-Lasche: haengt mittig an der Unterkante (halb ueber die Karte hinaus)
        if (showPlus) {
            Box(Modifier.align(Alignment.BottomCenter).offset(y = TreeLayout.PLUS_RADIUS.dp)) { PlusBadge((TreeLayout.PLUS_RADIUS * 2).toInt()) }
        }
    }
}

/** Zwei kleine Kaestchen (Vater blau, Mutter rosa) - das Zeichen fuer "hier geht es weiter nach oben". */
@Composable
private fun ExpandBadge() {
    val colors = treeColors
    Row(
        Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(width = 12.dp, height = 10.dp).background(colors.male, RoundedCornerShape(2.dp)))
        Box(Modifier.size(width = 12.dp, height = 10.dp).background(colors.female, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun PlusBadge(sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
