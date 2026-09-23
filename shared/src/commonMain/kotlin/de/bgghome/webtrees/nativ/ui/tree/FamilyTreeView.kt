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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.Avatar
import de.bgghome.webtrees.nativ.ui.forSex
import de.bgghome.webtrees.nativ.ui.treeColors
import kotlinx.coroutines.launch

private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 2.5f

/**
 * Zoom nach Bedeutung: beim Herauszoomen wird nicht alles kleiner, sondern die Karte zeigt weniger - erst fallen
 * Portraet und Jahre weg, dann der Nachname, zuletzt bleibt ein Kasten in der Geschlechtsfarbe. Die Schrift bleibt
 * dabei auf dem Schirm lesbar, weil sie je Stufe im Baum groesser gesetzt wird.
 */
enum class DetailLevel(
    /** Schriftfaktor gegenueber der Vollkarte - hebt auf, was der Zoom wegnimmt */
    val textScale: Float,
) { Full(1f), Compact(1.5f), Mini(2.4f), Box(1f) }

fun levelFor(scale: Float): DetailLevel = when {
    scale >= 0.75f -> DetailLevel.Full
    scale >= 0.45f -> DetailLevel.Compact
    scale >= 0.28f -> DetailLevel.Mini
    else -> DetailLevel.Box
}

/**
 * Der Baum als frei verschieb- und zoombare Flaeche, Karten im Stil gaengiger Stammbaum-Apps.
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
    onExpand: (n: Int, xref: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Desktop: Rechtsklick auf eine Karte, mit der Stelle im Baumfenster (fuer das Kontextmenue). */
    onSecondary: ((Person, Offset) -> Unit)? = null,
    /** Desktop: Doppelklick auf eine Karte; ohne diesen Weg zoomt der Doppeltipp wie bisher. */
    onOpen: ((Person) -> Unit)? = null,
    /** Desktop: kein Vollbild-Knopf, das Fenster selbst laesst sich maximieren. */
    showFullscreen: Boolean = true,
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
        // Nur ein Stufenwechsel setzt die Karten neu zusammen, nicht jeder Zoom-Schritt.
        val level by remember { derivedStateOf { levelFor(scale) } }
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

        fun boxAtScreen(p: Offset): TreeBox? =
            layout.boxAt((p.x - offset.x) / (scale * density), (p.y - offset.y) / (scale * density))?.takeIf { !it.person.isPrivate }

        Box(
            Modifier
                .fillMaxSize()
                // Maus: das Rad zoomt um den Zeiger, die rechte Taste oeffnet das Kontextmenue einer Karte.
                // Am Handy kommen diese Ereignisse nicht vor (nur mit angeschlossener Maus).
                .pointerInput(layout) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            when {
                                event.type == PointerEventType.Scroll -> {
                                    val dy = change.scrollDelta.y
                                    if (dy != 0f) zoomBy(if (dy < 0) 1.12f else 1 / 1.12f, change.position)
                                    change.consume()
                                }
                                event.type == PointerEventType.Press && event.buttons.isSecondaryPressed && onSecondary != null -> {
                                    boxAtScreen(change.position)?.let { onSecondary(it.person, change.position) }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
                .pointerInput(layout) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        zoomBy(zoom, centroid)
                        offset += pan
                    }
                }
                .pointerInput(layout) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val box = if (onOpen != null) boxAtScreen(tap) else null
                            if (box != null) onOpen?.invoke(box.person) else zoomBy(1.6f, tap)
                        },
                        onTap = { tap ->
                            val x = (tap.x - offset.x) / (scale * density)
                            val y = (tap.y - offset.y) / (scale * density)

                            val expand = layout.expandAt(x, y)
                            val plus = layout.plusAt(x, y)
                            val box = layout.boxAt(x, y)
                            when {
                                expand != null && expand.ahnen != null -> onExpand(expand.ahnen, expand.person.xref)
                                plus != null -> onPlus(plus.person)
                                box != null && !box.person.isPrivate -> {
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
                val bandColor = MaterialTheme.colorScheme.outlineVariant
                val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
                val ringFill = MaterialTheme.colorScheme.background

                Canvas(Modifier.fillMaxSize()) {
                    // Linien werden beim Herauszoomen nicht duenner als ein Pixel - sonst verschwindet der Baum vor den Karten.
                    val lineWidth = (1.3f * density / scale).coerceIn(1.3f * density, 4f * density)
                    val stroke = Stroke(width = lineWidth, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(10f * density))

                    // Generationsbaender: eine feine Linie zwischen je zwei Reihen, ueber die ganze Breite
                    layout.rows.zipWithNext { above, below ->
                        val y = (above + TreeLayout.BOX_H + (below - above - TreeLayout.BOX_H) / 2) * density
                        drawLine(bandColor, Offset(0f, y), Offset(size.width, y), strokeWidth = lineWidth * 0.8f)
                    }

                    layout.connectors.forEach { line ->
                        val path = Path()
                        line.points.forEachIndexed { index, (x, y) ->
                            if (index == 0) path.moveTo(x * density, y * density) else path.lineTo(x * density, y * density)
                        }
                        drawPath(path, lineColor, style = stroke)

                        // Ehe-Symbol: zwei Ringe auf der Linie zwischen Partnern - nicht mehr, wenn die Karten nur noch Kaesten sind
                        if (line.spouse && level != DetailLevel.Box) {
                            val (ax, ay) = line.points.first(); val (bx, by) = line.points.last()
                            val cx = (ax + bx) / 2 * density; val cy = (ay + by) / 2 * density
                            val r = 4.5f * density * level.textScale.coerceAtMost(1.5f)
                            val ring = Stroke(width = 1.2f * density)
                            drawCircle(ringFill, r * 1.9f, Offset(cx, cy))
                            drawCircle(ringColor, r, Offset(cx - r * 0.6f, cy), style = ring)
                            drawCircle(ringColor, r, Offset(cx + r * 0.6f, cy), style = ring)
                        }
                    }
                }

                layout.boxes.forEach { box ->
                    TreeCard(box, level, isSelected = box.person.xref == selected, showPlus = layout.hasPlus(box))
                }
            }
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showFullscreen && (!compact || fullscreen)) ToolButton(if (fullscreen) "⤡" else "⤢", stringResource(Res.string.tree_fullscreen), onToggleFullscreen)
            ToolButton("◎", stringResource(Res.string.tree_center)) { centerOn(layout.focus) }
            ToolButton("▣", stringResource(Res.string.tree_fit)) { fitAll() }
            if (!compact) {
                ToolButton("+", stringResource(Res.string.tree_zoom_in)) { zoomBy(1.4f, Offset(viewW / 2, viewH / 2)) }
                ToolButton("−", stringResource(Res.string.tree_zoom_out)) { zoomBy(1 / 1.4f, Offset(viewW / 2, viewH / 2)) }
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
private fun TreeCard(box: TreeBox, level: DetailLevel, isSelected: Boolean, showPlus: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val base = Modifier.offset(box.x.dp, box.y.dp).size(TreeLayout.BOX_W.dp, TreeLayout.BOX_H.dp)
    val person = box.person
    val gender = treeColors.forSex(person.sex)
    // Rahmen und Schrift wachsen mit der Stufe, damit sie auf dem Schirm gleich bleiben
    val borderWidth = (if (isSelected) 2.5f else 1.5f) * level.textScale
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else gender

    Box(base) {
        Box(
            Modifier
                .fillMaxSize()
                // Mittelperson: hebt sich mit Schatten ab. Gewaehlte Karte (Profil-Panel): kraeftiger Rahmen.
                .then(if (box.isFocus && level != DetailLevel.Box) Modifier.shadow(10.dp, shape) else Modifier)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(borderWidth.dp, borderColor, shape)
                .clip(shape),
        ) {
            when (level) {
                DetailLevel.Full -> Row(
                    Modifier.fillMaxSize().padding(start = 8.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Avatar(person, 42.dp)
                    Column {
                        Text(
                            if (person.isPrivate) stringResource(Res.string.person_private) else person.name,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                            lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                        )
                        if (person.lifespan.isNotBlank() && !person.isPrivate) {
                            Text(person.lifespan, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Ohne Portraet und Jahre: der Name in grosser Schrift, zwei Zeilen
                DetailLevel.Compact -> Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (person.isPrivate) stringResource(Res.string.person_private) else person.name,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize * level.textScale,
                        lineHeight = MaterialTheme.typography.labelMedium.fontSize * level.textScale * 1.15f,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // Nur der Rufname, eine Zeile
                DetailLevel.Mini -> Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (person.isPrivate) "" else givenName(person.name),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize * level.textScale,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                DetailLevel.Box -> Unit
            }
        }

        // Zweig-Symbol: diese Person hat Eltern, die noch nicht geladen sind - Tipp klappt den Baum nach oben auf
        val readable = level == DetailLevel.Full || level == DetailLevel.Compact
        if (box.canExpand && readable) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = (-TreeLayout.EXPAND_OFFSET - 11f).dp)) { ExpandBadge() }
        }

        // "+"-Lasche: haengt mittig unter der Karte, wie ein Reiter - nur, solange die Karte lesbar ist (nicht bei Rufname und Kasten)
        if (showPlus && readable) {
            Box(Modifier.align(Alignment.BottomCenter).offset(y = (TreeLayout.PLUS_RADIUS + 4f).dp)) { PlusTab() }
        }
    }
}

/** Der Rufname vor dem Familiennamen - "Lorenzo" aus "Lorenzo de' Medici"; bei einem einzigen Wort das Wort selbst. */
fun givenName(name: String): String = name.trim().split(' ').firstOrNull().orEmpty()

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
private fun PlusTab() {
    Box(
        Modifier.size(width = 30.dp, height = 20.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topStart = 2.dp, topEnd = 2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topStart = 2.dp, topEnd = 2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
