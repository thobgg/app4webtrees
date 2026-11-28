package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.bgghome.webtrees.nativ.Sprache
import de.bgghome.webtrees.nativ.api.DnaLinie
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import de.bgghome.webtrees.nativ.ui.DnaAnsicht
import de.bgghome.webtrees.nativ.ui.DnaBereich
import de.bgghome.webtrees.nativ.ui.DnaLinienInhalt
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Kein Test, sondern ein Werkzeug: zeichnet die Ansicht der DNA-Vererbungslinien ohne Bildschirm (Server mit dem Modul dna4webtrees ab 0.11.0).
 *
 *   WT_DNABILD=/pfad WT_URL=http://... WT_TREE=tree1 WT_XREF=I21 [WT_LINIE=y|mt|x] [WT_RICHTUNG=nachkommen] [WT_BEREICH=eintraege|treffer|tester] [WT_USER=.. WT_PASS=..] [WT_SPRACHE=en] [WT_BILD=640:780:1.25]
 *   ./gradlew :shared:desktopTest --rerun --tests '*DnaBildErzeugen*'
 */
class DnaBildErzeugen {
    private class Speicher : Ablage {
        private val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as? String ?: default
        override fun putString(key: String, value: String?) { m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
        override fun leeren() = m.clear()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun erzeugen() {
        val ziel = System.getenv("WT_DNABILD")?.let(::File) ?: return
        ziel.mkdirs()
        System.setProperty("java.util.prefs.userRoot", File(ziel, "prefs").absolutePath)
        System.getenv("WT_SPRACHE")?.let { java.util.Locale.setDefault(java.util.Locale.forLanguageTag(it)); Sprache.setzen(it) }
        val client = WtClient(Speicher(), Speicher(), "wtTux/dev (DNA)").apply { baseUrl = System.getenv("WT_URL")!! }
        runBlocking { System.getenv("WT_USER")?.let { client.login(it, System.getenv("WT_PASS").orEmpty()) } ?: client.info() }
        val tree = System.getenv("WT_TREE")!!; val xref = System.getenv("WT_XREF")!!
        val linie = DnaLinie.entries.firstOrNull { it.code == System.getenv("WT_LINIE") } ?: DnaLinie.Y
        val nachkommen = System.getenv("WT_RICHTUNG") == "nachkommen"
        val bereich = System.getenv("WT_BEREICH")?.let { b -> DnaBereich.entries.firstOrNull { it.name.equals(b, ignoreCase = true) } }
        val (bw, bh, dichte) = (System.getenv("WT_BILD") ?: "640:780:1.25").split(':')
        ImageComposeScene(width = bw.toInt(), height = bh.toInt(), density = Density(dichte.toFloat())) {
            DeskTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { bereich?.let { DnaAnsicht(client, tree, xref, onPerson = {}, zuerst = it) } ?: DnaLinienInhalt(client, tree, xref, onPerson = {}, zuerst = linie, zuerstNachkommen = nachkommen) } }
        }.use { scene ->
            scene.render(); (1..60).forEach { scene.render(it * 50_000_000L); Thread.sleep(60) }
            File(ziel, bereich?.let { "dna-${it.name.lowercase()}.png" } ?: "dna-${linie.code}-${if (nachkommen) "nachkommen" else "vorfahren"}.png").writeBytes(scene.render(4_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
