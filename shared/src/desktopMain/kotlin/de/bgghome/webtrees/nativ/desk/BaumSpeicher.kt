package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Desktop
import de.bgghome.webtrees.nativ.api.API_EXPORT
import de.bgghome.webtrees.nativ.api.ExportCache
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.api.exportTree
import java.io.File

/*
 * Der ganze Baum fuer Buecher und Listen (26.09.2026): aus dem Zwischenspeicher, solange lastChange in Info gleich
 * geblieben ist, sonst ueber die Route Export (ab Stufe 17) - aber nur, wenn das Werk einen guten Teil des Baums
 * braucht. Fuer wenige Personen und bei aelteren Servern bleibt es bei Einzelabfragen (Ergebnis null).
 */
object BaumSpeicher {
    /** Export lohnt sich ab etwa jeder 15. Person des Baums: 250 je Seite gegen 8 Einzelabfragen gleichzeitig. */
    private const val ANTEIL = 15

    private val cache: ExportCache? by lazy {
        runCatching { ExportCache(File(Desktop.plattform.cacheOrdner, "baeume")) }.getOrNull()
    }

    /**
     * [personen]: wie viele Personen das Werk hoechstens braucht (Schaetzung). Fragt Info neu, damit Aenderungen seit
     * dem Start - auch aus diesem Programm - erkannt werden. Fehler beim Export: null, der Aufrufer laedt einzeln.
     */
    suspend fun holen(client: WtClient, tree: String, personen: Int, fortschritt: (Int, Int) -> Unit = { _, _ -> }): TreeExport? {
        val info = runCatching { client.info() }.getOrNull() ?: return null
        val t = info.trees.firstOrNull { it.name == tree } ?: return null
        val stand = t.lastChange
        if (info.api < API_EXPORT || stand == null) return null
        val c = cache
        val key = c?.schluessel(client.baseUrl, tree, info.user.userName, t.role)
        if (c != null && key != null) c.laden(key, stand)?.let { return it }
        if (personen.toLong() * ANTEIL < t.individuals) return null
        val baum = runCatching { client.exportTree(tree, fortschritt) }.getOrNull() ?: return null
        if (c != null && key != null) c.sichern(key, baum)
        return baum
    }
}

/** Vorfahren mit Kekule-Nummern aus dem ganzen Baum - dieselbe Form wie ahnenLaden, ohne weitere Anfragen. */
fun ahnenAusBaum(baum: TreeExport, xref: String, generationen: Int): Map<Long, AhnenEintrag> {
    val alle = HashMap<Long, AhnenEintrag>()
    fun hatEltern(x: String) = baum.parents(x).let { (v, m) -> v != null || m != null }
    var reihe = listOfNotNull(baum.person(xref)?.let { 1L to it })
    var g = 0
    while (reihe.isNotEmpty() && g < generationen) {
        reihe.forEach { (n, p) -> alle[n] = AhnenEintrag(p, hatEltern(p.xref)) }
        reihe = if (g + 1 >= generationen) emptyList() else reihe.flatMap { (n, p) ->
            val (v, m) = baum.parents(p.xref)
            listOfNotNull(v?.let { 2 * n to it }, m?.let { 2 * n + 1 to it })
        }
        g++
    }
    return alle
}
