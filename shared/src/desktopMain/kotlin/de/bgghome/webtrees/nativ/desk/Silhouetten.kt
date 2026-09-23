package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import de.bgghome.webtrees.nativ.api.Person

/*
 * Eigene Platzhalter-Portraets (23.09.2026, Wunsch Thomas): fuer Personen ohne Bild eine Silhouette nach Geschlecht
 * und Jahrhundert - 15. bis 20. Jahrhundert, je mit typischer Kopfbedeckung oder Frisur. Alles einfache Formen,
 * gezeichnet mit Kreisen, Ellipsen und Pfaden; keine fremden Grafiken.
 */

/** Jahrhundert einer Person (15..20) aus dem Geburtsjahr, sonst aus dem Sterbejahr; null = unbekannt. */
fun jahrhundert(p: Person): Int? {
    val geburt = p.birth?.date?.year?.takeIf { it > 0 }
    val tod = p.death?.date?.year?.takeIf { it > 0 }
    val jahr = geburt ?: tod?.minus(40) ?: return null
    return (jahr / 100 + 1).coerceIn(15, 20)
}

/** Portraet oder Silhouette, je nachdem, ob ein Bild da ist. */
@Composable
fun Portrait(person: Person, modifier: Modifier = Modifier) {
    if (person.thumb != null) {
        AsyncImage(person.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Silhouette(person.sex, jahrhundert(person), modifier)
    }
}

@Composable
fun Silhouette(sex: String, jh: Int?, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val grund = when (sex) { "M" -> if (dark) Color(0xFF2F4B66) else Color(0xFFDCE7F2); "F" -> if (dark) Color(0xFF5A3540) else Color(0xFFF6DEDF); else -> if (dark) Color(0xFF3A4242) else Color(0xFFE6EAEA) }
    val koerper = when (sex) { "M" -> Color(0xFF5B7796); "F" -> Color(0xFFA6697B); else -> Color(0xFF8A9493) }
    val hell = if (dark) Color(0xFFE8EEF3) else Color(0xFFFFFFFF)
    Box(modifier.background(grund)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension / 100f
            // Alles im 100x100-Raster, mittig; Rumpf unten, Kopf oben
            val dx = (size.width - 100f * s) / 2f; val dy = (size.height - 100f * s) / 2f
            fun p(x: Float, y: Float) = Offset(dx + x * s, dy + y * s)
            fun kreis(x: Float, y: Float, r: Float, c: Color = koerper) = drawCircle(c, r * s, p(x, y))
            fun oval(x: Float, y: Float, rx: Float, ry: Float, c: Color = koerper) = drawOval(c, p(x - rx, y - ry), Size(2 * rx * s, 2 * ry * s))
            fun rect(x: Float, y: Float, w: Float, h: Float, c: Color = koerper) = drawRect(c, p(x, y), Size(w * s, h * s))
            fun pfad(c: Color = koerper, bau: Path.() -> Unit) = drawPath(Path().apply(bau), c)

            // Rumpf: Schultern als flache Kuppel
            pfad { moveTo(p(8f, 100f).x, p(8f, 100f).y); cubicTo(p(10f, 74f).x, p(10f, 74f).y, p(90f, 74f).x, p(90f, 74f).y, p(92f, 100f).x, p(92f, 100f).y); close() }
            rect(43f, 55f, 14f, 20f)              // Hals
            val f = sex == "F"
            when (jh) {
                15 -> if (f) { oval(50f, 40f, 15f, 18f); pfad { moveTo(p(40f, 26f).x, p(40f, 26f).y); lineTo(p(60f, 26f).x, p(60f, 26f).y); lineTo(p(72f, -4f).x, p(72f, -4f).y); close() }; drawLine(koerper, p(72f, -4f), p(84f, 30f), 2f * s) }   // Hennin mit Schleier
                      else { oval(50f, 40f, 16f, 18f); oval(50f, 22f, 22f, 10f); pfad { moveTo(p(66f, 22f).x, p(66f, 22f).y); cubicTo(p(80f, 30f).x, p(80f, 30f).y, p(78f, 50f).x, p(78f, 50f).y, p(70f, 60f).x, p(70f, 60f).y); lineTo(p(64f, 40f).x, p(64f, 40f).y); close() } }  // Chaperon mit Zipfel
                16 -> if (f) { oval(50f, 40f, 15f, 18f); pfad { moveTo(p(33f, 40f).x, p(33f, 40f).y); cubicTo(p(33f, 12f).x, p(33f, 12f).y, p(67f, 12f).x, p(67f, 12f).y, p(67f, 40f).x, p(67f, 40f).y); lineTo(p(62f, 40f).x, p(62f, 40f).y); cubicTo(p(62f, 20f).x, p(62f, 20f).y, p(38f, 20f).x, p(38f, 20f).y, p(38f, 40f).x, p(38f, 40f).y); close() }; oval(50f, 64f, 22f, 6f, hell) }  // franzoesische Haube, Halskrause
                      else { oval(50f, 41f, 16f, 18f); oval(50f, 24f, 24f, 8f); oval(50f, 64f, 24f, 6f, hell); rect(46f, 52f, 8f, 8f) }  // flaches Barett, Halskrause, Bart
                17 -> if (f) { oval(50f, 40f, 15f, 18f); kreis(31f, 38f, 8f); kreis(69f, 38f, 8f); kreis(29f, 50f, 6f); kreis(71f, 50f, 6f); for (i in 0..6) kreis(38f + i * 4f, 68f, 1.6f, hell) }  // Korkenzieherlocken, Perlenkette
                      else { oval(50f, 42f, 16f, 18f); oval(50f, 24f, 32f, 5f); rect(37f, 8f, 26f, 16f); pfad { moveTo(p(34f, 36f).x, p(34f, 36f).y); lineTo(p(28f, 66f).x, p(28f, 66f).y); lineTo(p(40f, 60f).x, p(40f, 60f).y); close() }; pfad { moveTo(p(66f, 36f).x, p(66f, 36f).y); lineTo(p(72f, 66f).x, p(72f, 66f).y); lineTo(p(60f, 60f).x, p(60f, 60f).y); close() }; rect(36f, 72f, 28f, 6f, hell) }  // breitkrempiger Hut, lange Haare, Spitzenkragen
                18 -> if (f) { oval(50f, 42f, 15f, 18f); oval(50f, 22f, 19f, 22f); kreis(50f, 4f, 4f, hell) }  // hochgesteckte, gepuderte Frisur mit Schleife
                      else { oval(50f, 41f, 16f, 18f); kreis(31f, 40f, 9f); kreis(69f, 40f, 9f); kreis(31f, 52f, 7f); kreis(69f, 52f, 7f); oval(50f, 24f, 20f, 8f); rect(58f, 56f, 6f, 22f); rect(44f, 58f, 12f, 12f, hell) }  // Peruecke mit Zopf, Jabot
                19 -> if (f) { oval(50f, 42f, 15f, 18f); pfad { moveTo(p(28f, 44f).x, p(28f, 44f).y); cubicTo(p(24f, 8f).x, p(24f, 8f).y, p(76f, 8f).x, p(76f, 8f).y, p(72f, 44f).x, p(72f, 44f).y); lineTo(p(66f, 44f).x, p(66f, 44f).y); cubicTo(p(68f, 18f).x, p(68f, 18f).y, p(32f, 18f).x, p(32f, 18f).y, p(34f, 44f).x, p(34f, 44f).y); close() }; kreis(44f, 62f, 3f); kreis(56f, 62f, 3f) }  // Schute mit Schleife
                      else { oval(50f, 42f, 16f, 18f); rect(36f, 2f, 28f, 22f); oval(50f, 24f, 26f, 4f); oval(35f, 50f, 5f, 8f); oval(65f, 50f, 5f, 8f) }  // Zylinder, Backenbart
                20 -> if (f) { oval(50f, 42f, 15f, 18f); pfad { moveTo(p(32f, 58f).x, p(32f, 58f).y); lineTo(p(32f, 40f).x, p(32f, 40f).y); cubicTo(p(32f, 16f).x, p(32f, 16f).y, p(68f, 16f).x, p(68f, 16f).y, p(68f, 40f).x, p(68f, 40f).y); lineTo(p(68f, 58f).x, p(68f, 58f).y); lineTo(p(62f, 58f).x, p(62f, 58f).y); lineTo(p(62f, 40f).x, p(62f, 40f).y); cubicTo(p(62f, 26f).x, p(62f, 26f).y, p(38f, 26f).x, p(38f, 26f).y, p(38f, 40f).x, p(38f, 40f).y); lineTo(p(38f, 58f).x, p(38f, 58f).y); close() }; rect(40f, 72f, 20f, 5f, hell) }  // Bubikopf, heller Kragen
                      else { oval(50f, 42f, 16f, 18f); pfad { moveTo(p(34f, 40f).x, p(34f, 40f).y); cubicTo(p(34f, 20f).x, p(34f, 20f).y, p(66f, 20f).x, p(66f, 20f).y, p(66f, 40f).x, p(66f, 40f).y); lineTo(p(60f, 38f).x, p(60f, 38f).y); cubicTo(p(58f, 28f).x, p(58f, 28f).y, p(42f, 28f).x, p(42f, 28f).y, p(40f, 38f).x, p(40f, 38f).y); close() }; pfad(hell) { moveTo(p(44f, 74f).x, p(44f, 74f).y); lineTo(p(56f, 74f).x, p(56f, 74f).y); lineTo(p(50f, 96f).x, p(50f, 96f).y); close() } }  // kurzes Haar, Krawatte
                else -> { oval(50f, 40f, 16f, 18f); if (f) pfad { moveTo(p(30f, 62f).x, p(30f, 62f).y); cubicTo(p(28f, 20f).x, p(28f, 20f).y, p(72f, 20f).x, p(72f, 20f).y, p(70f, 62f).x, p(70f, 62f).y); lineTo(p(64f, 62f).x, p(64f, 62f).y); cubicTo(p(66f, 28f).x, p(66f, 28f).y, p(34f, 28f).x, p(34f, 28f).y, p(36f, 62f).x, p(36f, 62f).y); close() } }  // ohne Jahr: schlicht
            }
        }
    }
}
