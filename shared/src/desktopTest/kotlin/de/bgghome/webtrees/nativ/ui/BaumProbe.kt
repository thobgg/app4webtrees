package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.exportTree
import de.bgghome.webtrees.nativ.ui.tree.Sibling
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import androidx.compose.ui.use
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.Test

/**
 * Kein Test im ueblichen Lauf, sondern eine Probe gegen einen echten Server: baut fuer jede Person des Baums die
 * Baumansicht so auf wie die App (Ahnen 4 Generationen, Nachkommen 2 Reihen, Geschwister) und meldet, wo ein Vorfahr
 * oder ein Partner der Mittelperson, den der Server liefert, im Layout fehlt, ob Karten sich ueberlappen und ob eine Ehelinie
 * eines Vorfahrenpaars durch eine fremde Karte laeuft, und ob Vater und Mutter nebeneinander stehen. WT_BAUMPROBE=1 WT_URL WT_BAUM WT_USER WT_PASS [WT_NUR=I1]
 */
class BaumProbe {
    private class Speicher : Ablage {
        private val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as? String ?: default
        override fun putString(key: String, value: String?) { m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
        override fun leeren() = m.clear()
    }

    /** Zeichnet den Baum einer Person wie die App ohne Bildschirm: WT_BAUMBILD=/pfad WT_NUR=I1 [WT_BILD=2400:1400:1.0] */
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test
    fun bild() {
        val ziel = System.getenv("WT_BAUMBILD")?.let { java.io.File(it) } ?: return
        ziel.mkdirs()
        System.setProperty("java.util.prefs.userRoot", java.io.File(ziel, "prefs").absolutePath)
        val client = WtClient(Speicher(), Speicher(), "wtTux/dev (Baumbild)").apply { baseUrl = System.getenv("WT_URL")!! }
        val tree = System.getenv("WT_BAUM")!!; val x = System.getenv("WT_NUR")!!
        val layout = runBlocking {
            client.login(System.getenv("WT_USER")!!, System.getenv("WT_PASS").orEmpty())
            val p = client.pedigree(tree, x, 4)
            val d = client.descendants(tree, x, 3).tree.bisTiefe(2)
            TreeLayout.build(p, d, false, geschwister(client, tree, p), System.getenv("WT_COUSINS") != null, false)
        }
        val (bw, bh, dichte) = (System.getenv("WT_BILD") ?: "2400:1400:1.0").split(':')
        androidx.compose.ui.ImageComposeScene(width = bw.toInt(), height = bh.toInt(), density = androidx.compose.ui.unit.Density(dichte.toFloat())) {
            de.bgghome.webtrees.nativ.desk.DeskTheme {
                de.bgghome.webtrees.nativ.ui.tree.FamilyTreeView(layout, null, false, 0.55f, false, {}, {}, {}, { _, _ -> })
            }
        }.use { scene ->
            scene.render(); (1..40).forEach { scene.render(it * 50_000_000L); Thread.sleep(50) }
            java.io.File(ziel, "baum-$x.png").writeBytes(scene.render(3_000_000_000L).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }
    }

    private suspend fun geschwister(client: WtClient, tree: String, p: de.bgghome.webtrees.nativ.api.Pedigree): Map<String, List<Sibling>> {
        val byNumber = p.ancestors.associate { it.n to it.person }
        val siblings = HashMap<String, List<Sibling>>()
        byNumber.keys.filter { n -> (31 - Integer.numberOfLeadingZeros(n)) < 3 && (2 * n in byNumber || 2 * n + 1 in byNumber) }.forEach { n ->
            val self = byNumber.getValue(n); val father = byNumber[2 * n]; val mother = byNumber[2 * n + 1]
            val parent = father ?: mother!!
            val other = if (parent === father) mother else null
            siblings[self.xref] = siblingsAusNachkommen(client.descendants(tree, parent.xref, if (n == 2 || n == 3) 4 else 3).tree.families, self, other)
        }
        return siblings
    }

    @Test
    fun alleLayouts() {
        System.getenv("WT_BAUMPROBE") ?: return
        Locale.setDefault(Locale.GERMANY)
        val client = WtClient(Speicher(), Speicher(), "wtTux/dev (Baumprobe)").apply { baseUrl = System.getenv("WT_URL")!! }
        val tree = System.getenv("WT_BAUM")!!
        runBlocking {
            client.login(System.getenv("WT_USER")!!, System.getenv("WT_PASS").orEmpty())
            val alle = client.exportTree(tree).individuals.keys.sorted()
            var fehler = 0
            for (x in alle.filter { it == (System.getenv("WT_NUR") ?: it) }) {
                val p = runCatching { client.pedigree(tree, x, 4) }.getOrNull() ?: continue
                val d = client.descendants(tree, x, 3).tree.bisTiefe(2)
                val byNumber = p.ancestors.associate { it.n to it.person }
                val siblings = HashMap<String, List<Sibling>>()
                byNumber.keys.filter { n -> (31 - Integer.numberOfLeadingZeros(n)) < 3 && (2 * n in byNumber || 2 * n + 1 in byNumber) }.forEach { n ->
                    val self = byNumber.getValue(n); val father = byNumber[2 * n]; val mother = byNumber[2 * n + 1]
                    val parent = father ?: mother!!
                    val other = if (parent === father) mother else null
                    val depth = if (n == 2 || n == 3) 4 else 3
                    siblings[self.xref] = siblingsAusNachkommen(client.descendants(tree, parent.xref, depth).tree.families, self, other)
                }
                for (mitGeschwistern in listOf(false, true)) for (cousins in listOf(false, true)) {
                    val l = TreeLayout.build(p, d, false, if (mitGeschwistern) siblings else emptyMap(), mitGeschwistern && cousins, mitGeschwistern && cousins)
                    val ahnenImBild = l.boxes.mapNotNull { b -> b.ahnen?.let { it to b.person.xref } }.toMap()
                    val fehltAhn = p.ancestors.filter { it.n > 1 && ahnenImBild[it.n] != it.person.xref }.map { "${it.n}=${it.person.xref}" }
                    val imBild = l.boxes.map { it.person.xref }.toSet()
                    val fehltPartner = d.families.mapNotNull { it.spouse?.xref }.filter { it !in imBild }
                    // Kaesten, die sich ueberlappen: der obere verdeckt den unteren
                    val ueber = l.boxes.indices.flatMap { i -> (i + 1 until l.boxes.size).map { j -> i to j } }.filter { (i, j) ->
                        val a = l.boxes[i]; val b = l.boxes[j]
                        a.x < b.right - 1 && b.x < a.right - 1 && a.y < b.bottom - 1 && b.y < a.bottom - 1
                    }.map { (i, j) -> "${l.boxes[i].person.xref}/${l.boxes[j].person.xref}" }
                    // Ehelinien, die durch eine fremde Karte laufen
                    // nur die Ehelinien der Vorfahrenpaare (oberhalb der Mittelperson)
                    val focusY = l.boxes.first { it.isFocus }.y
                    val quer = l.connectors.filter { it.spouse && it.points.first().second < focusY }.flatMap { c ->
                        val (ax, ay) = c.points.first(); val (bx, _) = c.points.last()
                        l.boxes.filter { b -> ay > b.y && ay < b.bottom && b.x < maxOf(ax, bx) - 1 && b.right > minOf(ax, bx) + 1 }
                            .map { b -> "${b.person.xref}@(${b.x},${b.y}) Linie ${c.points} " + l.boxes.filter { o -> o.y == b.y }.sortedBy { it.x }.joinToString { "${it.person.xref}:${it.x}/${it.ahnen}" } }
                    }
                    if (quer.isNotEmpty()) { fehler++; println("EHELINIE DURCH KARTE bei $x (Geschwister=$mitGeschwistern, Cousins=$cousins): $quer") }
                    // Vater und Mutter eines Kindes stehen nebeneinander wie ein Paar
                    val ahnenBox = l.boxes.filter { it.ahnen != null }.associateBy { it.ahnen!! }
                    val getrennt = ahnenBox.keys.filter { it % 2 == 0 && it + 1 in ahnenBox }
                        .filter { kotlin.math.abs(ahnenBox.getValue(it + 1).x - ahnenBox.getValue(it).x - (TreeLayout.BOX_W + TreeLayout.SPOUSE_GAP)) > 0.5f }
                    if (getrennt.isNotEmpty()) { fehler++; println("PAAR GETRENNT bei $x: $getrennt") }
                    if (ueber.isNotEmpty()) { fehler++; println("UEBERLAPPT bei $x (Geschwister=$mitGeschwistern, Cousins=$cousins): $ueber") }
                    if (fehltAhn.isNotEmpty() || fehltPartner.isNotEmpty()) {
                        fehler++
                        println("FEHLT bei $x (Geschwister=$mitGeschwistern): Ahnen $fehltAhn Partner $fehltPartner")
                    }
                }
            }
            println("Geprueft: ${alle.size} Personen, Abweichungen: $fehler")
        }
    }
}
