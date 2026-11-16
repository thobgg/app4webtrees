package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import de.bgghome.webtrees.nativ.api.MergeFact
import de.bgghome.webtrees.nativ.api.MergeLink
import de.bgghome.webtrees.nativ.api.MergePreview
import de.bgghome.webtrees.nativ.api.MergeSuggestion
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.data.vorschlag
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Kein Test, sondern ein Werkzeug fuer README und Homepage: zeichnet die Vorschau des Zusammenfuehrens mit erdachten
 * Daten im Stil des Demo-Baums als PNG. Laeuft nur mit gesetztem WT_MERGEBILD (Zielordner):
 *
 *   WT_MERGEBILD=/pfad ./gradlew :shared:desktopTest --rerun --tests '*ZusammenfuehrenBildErzeugen*'
 */
class ZusammenfuehrenBildErzeugen {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun erzeugen() {
        val ziel = System.getenv("WT_MERGEBILD")?.let(::File) ?: return
        ziel.mkdirs()
        val p1 = Person("I52", "Johann Heinrich Falkenrath", given = "Johann Heinrich", surname = "Falkenrath", sex = "M", isDead = true, lifespanServer = "1801–1869")
        val p2 = Person("I412", "Johann Heinrich Falkenrath", given = "Johann Heinrich", surname = "Falkenrath", sex = "M", isDead = true, lifespanServer = "1801–")
        val v = MergePreview(
            ok = true, person1 = p1, person2 = p2,
            facts1 = listOf(
                MergeFact("a1", "NAME", "Name", "Johann Heinrich Falkenrath", same = true),
                MergeFact("a2", "SEX", "Geschlecht", "männlich", same = true),
                MergeFact("a3", "BIRT", "Geburt", "3. Mai 1801 · Celle", same = true),
                MergeFact("a4", "CHR", "Taufe", "6. Mai 1801 · Celle"),
                MergeFact("a5", "OCCU", "Beruf", "Ackermann"),
                MergeFact("a6", "DEAT", "Tod", "12. Februar 1869 · Celle"),
                MergeFact("a7", "FAMS", "Familie", "Johann Heinrich Falkenrath + Anna Dorothea Wichmann", link = true),
                MergeFact("a8", "FAMC", "Elternfamilie", "Hinrich Falkenrath + Catharina Meyer", link = true),
            ),
            facts2 = listOf(
                MergeFact("b1", "NAME", "Name", "Johann Heinrich Falkenrath", same = true, keep = false),
                MergeFact("b2", "SEX", "Geschlecht", "männlich", same = true, keep = false),
                MergeFact("b3", "BIRT", "Geburt", "3. Mai 1801 · Celle", same = true, keep = false),
                MergeFact("b4", "RESI", "Wohnort", "ab 1830 · Bienenbüttel"),
                MergeFact("b5", "DEAT", "Tod", "Y", same = true, keep = false),
                MergeFact("b6", "FAMS", "Familie", "Johann Heinrich Falkenrath + Anna Dorothea Wichmann", link = true),
            ),
            links = listOf(MergeLink("F201", "FAM", "Johann Heinrich Falkenrath + Anna Dorothea Wichmann"), MergeLink("S5", "SOUR", "Kirchenbuch Celle, Taufen 1790–1820")),
            suggestions = listOf(MergeSuggestion("spouse", "I53", "Anna Dorothea Wichmann", "I413", "Anna Dorothea Wichmann")),
        )
        ImageComposeScene(width = 1800, height = 760, density = Density(2f)) {
            DeskTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    ZusammenfuehrenVorschau(v, vorschlag(v.facts1), vorschlag(v.facts2), setOf("I53|I413"), { _, _ -> }, { _, _ -> }, {}, {})
                }
            }
        }.use { scene ->
            scene.render(); (1..5).forEach { scene.render(it * 50_000_000L); Thread.sleep(30) }
            File(ziel, "zusammenfuehren.png").writeBytes(scene.render(600_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
