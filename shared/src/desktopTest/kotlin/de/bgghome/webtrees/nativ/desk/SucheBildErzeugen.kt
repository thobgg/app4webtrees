package de.bgghome.webtrees.nativ.desk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.bgghome.webtrees.nativ.Desktop
import de.bgghome.webtrees.nativ.DesktopPlattform
import de.bgghome.webtrees.nativ.Sprache
import de.bgghome.webtrees.nativ.gedcom.GedcomLeser
import de.bgghome.webtrees.nativ.ui.AppViewModel
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Kein Test, sondern ein Bildwerkzeug: zeichnet das Suchfenster mit einer GEDCOM-Datei, ohne Server.
 *   WT_SUCHBILD=/pfad WT_GED=falkenrath.ged [WT_SPRACHE=en] ./gradlew :shared:desktopTest --rerun --tests '*SucheBildErzeugen*'
 */
class SucheBildErzeugen {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun erzeugen() {
        val ziel = System.getenv("WT_SUCHBILD")?.let(::File) ?: return
        ziel.mkdirs()
        System.getenv("WT_SPRACHE")?.let { java.util.Locale.setDefault(java.util.Locale.forLanguageTag(it)); Sprache.setzen(it) }
        System.setProperty("java.util.prefs.userRoot", File(ziel, "prefs").absolutePath)
        val baum = GedcomLeser.alsExport(GedcomLeser.lesen(File(System.getenv("WT_GED")!!)), jetzt = 2026)
        val plat = DesktopPlattform().also { Desktop.plattform = it }
        val vm = AppViewModel(plat)
        ImageComposeScene(width = 980, height = 760, density = Density(1f)) {
            DeskTheme { SuchInhalt(vm.state.value, vm, {}, {}, vorgabeBaum = baum, vorgabeText = System.getenv("WT_TEXT") ?: "") }
        }.use { scene ->
            scene.render(); (1..40).forEach { scene.render(it * 50_000_000L); Thread.sleep(40) }
            File(ziel, "suche-${Sprache.aktiv}.png").writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
