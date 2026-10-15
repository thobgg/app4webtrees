package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.bgghome.webtrees.nativ.Sprache
import de.bgghome.webtrees.nativ.gedcom.GedcomLeser
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Kein Test im ueblichen Lauf, sondern ein Werkzeug: zeichnet den Dialog der CompGen-Meta-Suche fuer eine Person aus
 * einer GEDCOM-Datei als PNG (README, Homepage). Laeuft nur mit gesetztem WT_METABILD (Ordner fuer das Bild):
 *
 *   WT_METABILD=/pfad WT_GED=falkenrath.ged [WT_XREF=I130] [WT_SPRACHE=en] ./gradlew :shared:desktopTest --rerun --tests '*MetaSucheBildErzeugen*'
 */
class MetaSucheBildErzeugen {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun bild() {
        val ziel = System.getenv("WT_METABILD")?.let(::File) ?: return
        ziel.mkdirs()
        System.getenv("WT_SPRACHE")?.let { java.util.Locale.setDefault(java.util.Locale.forLanguageTag(it)); Sprache.setzen(it) }
        val baum = GedcomLeser.auswerten(GedcomLeser.lesen(File(System.getenv("WT_GED")!!))).first
        val d = baum.detail(System.getenv("WT_XREF") ?: "I130")!!
        val wahl = MetaSucheWahl().apply { fuer(d) }
        ImageComposeScene(width = 1100, height = 1100, density = Density(1.5f)) {
            DeskTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { MetaSucheDialog(wahl) } }
        }.use { scene ->
            // Die Einblend-Animation des Dialogs auslaufen lassen
            scene.render(); (1..40).forEach { scene.render(it * 25_000_000L) }
            File(ziel, "metasuche-${System.getenv("WT_SPRACHE") ?: "de"}.png").writeBytes(scene.render(1_100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
