package de.bgghome.webtrees.nativ.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Eigene Symbole fuer die Navigationsleiste. Die Standardsammlung von Compose hat weder Baum noch Bild; das
// Teilen-Symbol als Baum und der Kopf im Kreis als Fotos waren Notloesungen.

/** Ein kleiner Stammbaum: eine Karte oben, zwei darunter, durch Linien verbunden. */
val TreeIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Tree", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            path(fill = SolidColor(Color.Black)) {
                // obere Karte
                moveTo(8f, 3f); lineTo(16f, 3f); lineTo(16f, 8f); lineTo(8f, 8f); close()
                // Stamm und Querbalken
                moveTo(11f, 8f); lineTo(13f, 8f); lineTo(13f, 12f); lineTo(11f, 12f); close()
                moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 13f); lineTo(5f, 13f); close()
                // zwei Beine
                moveTo(5f, 12f); lineTo(7f, 12f); lineTo(7f, 16f); lineTo(5f, 16f); close()
                moveTo(17f, 12f); lineTo(19f, 12f); lineTo(19f, 16f); lineTo(17f, 16f); close()
                // untere Karten
                moveTo(2f, 16f); lineTo(10f, 16f); lineTo(10f, 21f); lineTo(2f, 21f); close()
                moveTo(14f, 16f); lineTo(22f, 16f); lineTo(22f, 21f); lineTo(14f, 21f); close()
            }
        }
        .build()
}

/** Ein Bild: Rahmen mit Berg und Sonne (Umriss nach dem Material-Symbol "image"). */
val PhotoIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Photo", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(21f, 19f); verticalLineTo(5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f); horizontalLineTo(5f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f); verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f); horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f); close()
                moveTo(8.5f, 13.5f); lineTo(11f, 16.51f); lineTo(14.5f, 12f); lineTo(19f, 18f); horizontalLineTo(5f); close()
            }
        }
        .build()
}
