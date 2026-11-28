package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.exportTree
import de.bgghome.webtrees.nativ.ui.tree.Sibling
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.Test

/**
 * Kein Test im ueblichen Lauf, sondern eine Probe gegen einen echten Server: baut fuer jede Person des Baums die
 * Baumansicht so auf wie die App (Ahnen 4 Generationen, Nachkommen 2 Reihen, Geschwister) und meldet, wo ein Vorfahr
 * oder ein Partner der Mittelperson, den der Server liefert, im Layout fehlt, ob Karten sich ueberlappen und ob eine Ehelinie
 * eines Vorfahrenpaars durch eine fremde Karte laeuft. WT_BAUMPROBE=1 WT_URL WT_BAUM WT_USER WT_PASS [WT_NUR=I1]
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
