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
        val (xref, gen) = (System.getenv("WT_BUCH") ?: "I1:7").split(':').let { it[0] to it[1].toInt() }
        val o = BuchOptionen(generationen = gen, vorwort = "Dieses Buch stellt die Vorfahren von Jonas Falkenrath zusammen.\n\nAlle Angaben sind erfunden.")
        val daten = runBlocking { vorfahrenbuchLaden(client, baum, xref, gen, true) }
        val buch = vorfahrenbuch(daten, o, titel, "wtTux")
        BuchFormat.entries.forEach { f -> buchSchreiben(buch, f, File(ziel, "vorfahrenbuch.${f.endung}")) }
        println("Buch: ${buch.bloecke.size} Bloecke, ${daten.details.size} Eintraege, ${daten.bilder.size} Bilder")
    }
}
