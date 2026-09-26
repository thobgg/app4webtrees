package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/**
 * Kein Test, sondern ein Werkzeug: erzeugt das Vorfahrenbuch vom lokalen Testserver in allen Formaten.
 *   WT_BUCH_ZIEL=/pfad WT_BUCH="I1:7" ./gradlew :shared:desktopTest --rerun --tests '*BuchErzeugen*'
 */
class BuchErzeugen {
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
    fun erzeugen() {
        val ziel = System.getenv("WT_BUCH_ZIEL")?.let(::File) ?: return
        ziel.mkdirs()
        val baum = System.getenv("WT_BAUM") ?: "falkenrath"
        val client = WtClient(Speicher(), Speicher(), "wtTux/dev (Buch)").apply { baseUrl = System.getenv("WT_URL") ?: "http://127.0.0.1:8377" }
        val info = runBlocking { System.getenv("WT_USER")?.let { client.login(it, System.getenv("WT_PASS").orEmpty()) } ?: client.info() }
        val titel = info.trees.first { it.name == baum }.title
        // WT_BUCH: "I1:7" (Vorfahrenbuch) oder "Nachfahren:I52:5:Henry"
        val t = (System.getenv("WT_BUCH") ?: "I1:7").split(':')
        if (t[0] == "Familien") {
            val o = BuchOptionen(ortFilter = t.getOrNull(1).orEmpty(), familienChronologisch = t.getOrNull(2) == "chrono")
            val buch = familienbuch(runBlocking { familienbuchLaden(client, baum, true) { println(it) } }, o, titel, "wtTux")
            BuchFormat.entries.forEach { f -> buchSchreiben(buch, f, File(ziel, "familienbuch.${f.endung}")) }
            println("Buch: ${buch.bloecke.size} Bloecke")
            return
        }
        val nach = t[0] == "Nachfahren"
        val (xref, gen) = if (nach) t[1] to t[2].toInt() else t[0] to t[1].toInt()
        val o = BuchOptionen(generationen = gen, vorwort = "Alle Angaben dieses Buches sind erfunden.",
            nummerierung = t.getOrNull(3)?.let { Nummerierung.valueOf(it) } ?: Nummerierung.Saragossa)
        val name = if (nach) "nachfahrenbuch" else "vorfahrenbuch"
        val buch = if (nach) nachfahrenbuch(runBlocking { nachfahrenbuchLaden(client, baum, xref, gen, true) { println(it) } }, o, titel, "wtTux")
            else vorfahrenbuch(runBlocking { vorfahrenbuchLaden(client, baum, xref, gen, true) }, o, titel, "wtTux")
        BuchFormat.entries.forEach { f -> buchSchreiben(buch, f, File(ziel, "$name.${f.endung}")) }
        println("Buch: ${buch.bloecke.size} Bloecke")
    }
}
