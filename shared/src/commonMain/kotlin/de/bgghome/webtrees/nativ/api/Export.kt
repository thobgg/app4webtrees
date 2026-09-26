package de.bgghome.webtrees.nativ.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/*
 * Der ganze Baum am Stueck (api4webtrees ab Stufe 17, Route Export, 26.09.2026): erst alle Personen, dann alle
 * Familien, 250 Datensaetze je Seite, verknuepft nur ueber Kennungen. Lohnt sich fuer Buecher und Listen ueber viele
 * Personen und mit dem Zwischenspeicher; fuer wenige Personen sind Einzelabfragen schneller (Gramps-Beispiel:
 * 2157 Personen in 5 s, 3,6 MB). Aeltere Server laden weiter Person fuer Person.
 */

/** Seite der Route Export, wie sie vom Server kommt - die Personen erst roh, siehe WtClient.export(). */
@Serializable
internal data class ExportPageJson(
    val lastChange: Long = 0,
    val page: Int = 1,
    val nextPage: Int? = null,
    val total: ExportTotal = ExportTotal(),
    val individuals: List<JsonElement> = emptyList(),
    val families: List<ExportFamily> = emptyList(),
)

@Serializable
data class ExportTotal(val individuals: Int = 0, val families: Int = 0)

/** Was eine Person im Export ueber die Kurzfassung ([Person]) hinaus traegt. */
@Serializable
internal data class ExportLinks(
    val famc: List<String> = emptyList(),
    val fams: List<String> = emptyList(),
    val facts: List<FactJson> = emptyList(),
    val media: List<MediaJson> = emptyList(),
)

/** Eine Person des Exports. Platzhalter (person.isPrivate) haben famc/fams, aber keine Fakten und Medien. */
@Serializable
data class ExportIndividual(
    val person: Person,
    val famc: List<String> = emptyList(),
    val fams: List<String> = emptyList(),
    val facts: List<FactJson> = emptyList(),
    val media: List<MediaJson> = emptyList(),
)

/** Eine Familie des Exports. Platzhalter (isPrivate) haben Partner und Kinder, aber keine Heirat, Fakten und Medien. */
@Serializable
data class ExportFamily(
    val xref: String,
    @SerialName("private") val isPrivate: Boolean = false,
    val husband: String? = null,
    val wife: String? = null,
    val children: List<String> = emptyList(),
    val marriage: EventJson? = null,
    val facts: List<FactJson> = emptyList(),
    val media: List<MediaJson> = emptyList(),
)

class ExportPage(
    val lastChange: Long,
    val page: Int,
    val nextPage: Int?,
    val total: ExportTotal,
    val individuals: List<ExportIndividual>,
    val families: List<ExportFamily>,
)

/**
 * Der ganze sichtbare Baum eines Benutzers. [lastChange]: Stand des Servers beim Laden - stimmt er mit
 * Info.trees[].lastChange ueberein, ist der Baum aktuell (nur auf Gleichheit vergleichen).
 */
@Serializable
class TreeExport(val lastChange: Long, val individuals: Map<String, ExportIndividual>, val families: Map<String, ExportFamily>) {

    fun person(xref: String?): Person? = xref?.let { individuals[it]?.person }

    /** Die erste Elternfamilie (wie webtrees' primaere Herkunftsfamilie in den Diagrammen). */
    fun parentFamily(xref: String): ExportFamily? = individuals[xref]?.famc?.firstNotNullOfOrNull { families[it] }

    /** Vater und Mutter aus der ersten Elternfamilie - null, wo keiner steht oder er nicht sichtbar ist. */
    fun parents(xref: String): Pair<Person?, Person?> =
        parentFamily(xref).let { f -> person(f?.husband) to person(f?.wife) }

    /**
     * Dieselbe Form wie die Route Individual, damit Buecher und Listen nicht wissen muessen, woher die Daten kommen.
     * canEdit und relationship bleiben leer; Kinder und Partner nur als Kurzfassung (ohne marriages).
     */
    fun detail(xref: String): IndividualDetail? {
        val i = individuals[xref] ?: return null
        fun familie(f: ExportFamily, selbst: String?): FamilyJson {
            val mann = person(f.husband); val frau = person(f.wife)
            return FamilyJson(
                xref = f.xref, husband = mann, wife = frau,
                spouse = if (selbst == null) null else if (f.husband == selbst) frau else mann,
                marriage = f.marriage, facts = f.facts, media = f.media,
                children = f.children.mapNotNull(::person),
            )
        }
        val eltern = i.famc.mapNotNull { families[it] }
        // Halbgeschwister: andere Partnerfamilien der Eltern, mit dem gemeinsamen Elternteil (wie stepFamilies)
        val stief = eltern.flatMap { f -> listOfNotNull(f.husband, f.wife) }.distinct().flatMap { p ->
            individuals[p]?.fams.orEmpty().filter { it !in i.famc }.mapNotNull { families[it] }.map { familie(it, p).copy(parent = p) }
        }
        return IndividualDetail(
            person = i.person,
            facts = i.facts,
            parentFamilies = eltern.map { familie(it, null) },
            spouseFamilies = i.fams.mapNotNull { families[it] }.map { familie(it, xref) },
            stepFamilies = stief,
            media = i.media,
        )
    }
}

/** Ab dieser API-Stufe kennt das Modul die Route Export und lastChange in Info. */
const val API_EXPORT = 17

/**
 * Alle Seiten laden. Aendert sich lastChange zwischen zwei Seiten, beginnt es von vorn (hoechstens dreimal, dann
 * IOException - der Aufrufer faellt auf Einzelabfragen zurueck). [fortschritt]: geladene und alle Datensaetze.
 */
suspend fun WtClient.exportTree(tree: String, fortschritt: (Int, Int) -> Unit = { _, _ -> }): TreeExport {
    repeat(3) {
        val individuals = LinkedHashMap<String, ExportIndividual>()
        val families = LinkedHashMap<String, ExportFamily>()
        var page: Int? = 1
        var stand: Long? = null
        while (page != null) {
            val p = export(tree, page)
            if (stand != null && p.lastChange != stand) break
            stand = p.lastChange
            p.individuals.forEach { individuals[it.person.xref] = it }
            p.families.forEach { families[it.xref] = it }
            val alle = p.total.individuals + p.total.families
            fortschritt(minOf(alle, p.page * EXPORT_PAGE_SIZE), alle)
            page = p.nextPage
        }
        if (page == null && stand != null) return TreeExport(stand, individuals, families)
    }
    throw IOException("tree changed while exporting")
}

/** So viele Datensaetze liefert der Server je Seite (nur fuer die Fortschrittsanzeige). */
private const val EXPORT_PAGE_SIZE = 250

/**
 * Zwischenspeicher fuer exportierte Baeume, eine Datei je Server, Baum und Benutzer (was jemand sehen darf, haengt
 * an Benutzer und Rolle). Aktuell ist ein Eintrag, solange lastChange gleich geblieben ist.
 */
class ExportCache(private val ordner: File) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private var zuletzt: Pair<String, TreeExport>? = null

    fun schluessel(baseUrl: String, tree: String, user: String, role: String): String =
        MessageDigest.getInstance("SHA-256").digest("$baseUrl\n$tree\n$user\n$role".toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    /** Der gespeicherte Baum, wenn er zum Stand [lastChange] gehoert; sonst null. */
    @Synchronized
    fun laden(schluessel: String, lastChange: Long): TreeExport? {
        zuletzt?.let { (k, t) -> if (k == schluessel && t.lastChange == lastChange) return t }
        val datei = File(ordner, "$schluessel.json")
        val t = runCatching { json.decodeFromString(TreeExport.serializer(), datei.readText()) }.getOrNull() ?: return null
        if (t.lastChange != lastChange) return null
        zuletzt = schluessel to t
        return t
    }

    @Synchronized
    fun sichern(schluessel: String, t: TreeExport) {
        zuletzt = schluessel to t
        runCatching {
            ordner.mkdirs()
            val tmp = File(ordner, "$schluessel.tmp")
            tmp.writeText(json.encodeToString(TreeExport.serializer(), t))
            tmp.renameTo(File(ordner, "$schluessel.json")) || run { File(ordner, "$schluessel.json").delete(); tmp.renameTo(File(ordner, "$schluessel.json")) }
        }
    }
}
