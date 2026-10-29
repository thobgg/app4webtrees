package de.bgghome.webtrees.nativ.pruefung

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Abgehakte Treffer: lokal im Programm, nicht auf dem Server (die API bleibt so klein wie moeglich).
 * Je Baum (Server + Baumname) die Schluessel der Treffer; der Schluessel enthaelt einen Fingerabdruck der Daten -
 * aendert sich an der Stelle etwas, passt er nicht mehr und der Treffer erscheint wieder. Austausch zwischen zwei
 * Rechnern ueber eine kleine JSON-Datei (Export/Import).
 */

@Serializable
data class Abgehakt(val schluessel: String, val text: String = "", val am: String = "")

@Serializable
data class Abhakdatei(val version: Int = 1, val baeume: Map<String, List<Abgehakt>> = emptyMap())

class Abhakliste(private var datei: Abhakdatei = Abhakdatei()) {

    fun schluesselBaum(baseUrl: String, tree: String) = "${baseUrl.trimEnd('/')}|$tree"

    fun fuer(baum: String): Set<String> = datei.baeume[baum].orEmpty().map { it.schluessel }.toSet()

    fun anzahl(baum: String) = datei.baeume[baum]?.size ?: 0

    fun umschalten(baum: String, t: Treffer, heute: String): Abhakliste {
        val alt = datei.baeume[baum].orEmpty()
        val neu = if (alt.any { it.schluessel == t.schluessel }) alt.filter { it.schluessel != t.schluessel }
                  else alt + Abgehakt(t.schluessel, "${t.regel} ${t.text}", heute)
        return Abhakliste(datei.copy(baeume = datei.baeume + (baum to neu)))
    }

    /** Eintraege unter dem alten Schluessel auf den neuen umziehen, wenn dort noch nichts liegt (gedaudit bis 0.6: Pfad der Datei). */
    fun uebernehmen(alt: String, neu: String): Abhakliste {
        val eintraege = datei.baeume[alt]
        if (alt == neu || eintraege.isNullOrEmpty() || !datei.baeume[neu].isNullOrEmpty()) return this
        return Abhakliste(datei.copy(baeume = datei.baeume - alt + (neu to eintraege)))
    }

    /** Eintraege, deren Treffer es nicht mehr gibt (Daten geaendert oder Person weg), verwerfen. */
    fun aufraeumen(baum: String, vorhanden: Set<String>): Abhakliste {
        val alt = datei.baeume[baum] ?: return this
        val neu = alt.filter { it.schluessel in vorhanden }
        return if (neu.size == alt.size) this else Abhakliste(datei.copy(baeume = datei.baeume + (baum to neu)))
    }

    /** Import: vorhandene Eintraege bleiben, neue kommen dazu. */
    fun zusammenfuehren(andere: Abhakdatei): Abhakliste {
        val alle = (datei.baeume.keys + andere.baeume.keys).associateWith { b ->
            (datei.baeume[b].orEmpty() + andere.baeume[b].orEmpty()).distinctBy { it.schluessel }
        }
        return Abhakliste(Abhakdatei(baeume = alle))
    }

    fun alsJson(): String = json.encodeToString(Abhakdatei.serializer(), datei)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        fun ausJson(text: String): Abhakliste = Abhakliste(json.decodeFromString(Abhakdatei.serializer(), text))
        fun dateiAusJson(text: String): Abhakdatei = json.decodeFromString(Abhakdatei.serializer(), text)
    }
}
