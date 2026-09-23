package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.bgghome.webtrees.nativ.api.Person

/**
 * Rundes Portraet. Ohne Foto eine graue Silhouette (Kopf und Schultern) mit duennem Ring in der
 * Geschlechtsfarbe - wie beim Vorbild. Das Foto liegt ueber der Silhouette: fehlt die Bilddatei auf
 * dem Server, bleibt kein leerer Kreis.
 */
@Composable
fun Avatar(person: Person, size: Dp, modifier: Modifier = Modifier) {
    val colors = treeColors
    val ring = colors.forSex(person.sex)

    Box(
        modifier.size(size).clip(CircleShape).background(colors.silhouetteBackground).border(if (size > 60.dp) 2.dp else 1.5.dp, ring, CircleShape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            // Kopf
            drawCircle(colors.silhouette, radius = w * 0.19f, center = Offset(w / 2, w * 0.40f))
            // Schultern: ein breites Oval, das unten aus dem Kreis laeuft
            drawOval(colors.silhouette, topLeft = Offset(w * 0.17f, w * 0.64f), size = Size(w * 0.66f, w * 0.70f))
        }

        if (person.thumb != null) {
            AsyncImage(model = person.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
