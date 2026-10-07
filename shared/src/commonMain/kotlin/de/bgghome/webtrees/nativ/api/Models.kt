package de.bgghome.webtrees.nativ.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// JSON-Formen des webtrees-Moduls "api4webtrees" (siehe README des Moduls: github.com/thobgg/api4webtrees).

@Serializable
data class Info(
    val api: Int = 0,
    val module: String = "",
    val webtrees: String = "",
    val baseUrl: String = "",
    val rewriteUrls: Boolean = false,
    val csrf: String = "",
    /** Groesste Datei in Bytes, die der Server beim Hochladen annimmt; 0 = unbekannt (Modul vor 0.6) */
    val maxUpload: Long = 0,
    val user: UserInfo = UserInfo(),
    val trees: List<TreeInfo> = emptyList(),
)

@Serializable
data class UserInfo(
    val loggedIn: Boolean = false,
    val userName: String = "",
    val realName: String = "",
    val isAdmin: Boolean = false,
)

@Serializable
data class TreeInfo(
    val name: String,
    val title: String = "",
    val individuals: Int = 0,
    val role: String = "visitor",
    val canEdit: Boolean = false,
    val canUpload: Boolean = false,
    val canModerate: Boolean = false,
    /** Datensaetze mit ausstehenden Aenderungen (nur fuer Moderatoren gefuellt) */
    val pending: Int = 0,
    val autoAccept: Boolean = false,
    val userXref: String = "",
    val defaultXref: String = "",
    /** Nummer der letzten Aenderung im Baum (ab API-Stufe 17), null bei aelteren Modulen. Nur auf Gleichheit vergleichen. */
    val lastChange: Long? = null,
    /** Mit wem webtrees fuer diesen Benutzer startet (ab API-Stufe 24): eigene Standardperson, "Das bin ich",
     *  Standardperson des Stammbaums, sonst die erste Person; leer, wenn er sie nicht sehen darf. */
    val startXref: String = "",
    /** Standardperson des Stammbaums (Verwaltung, ab API-Stufe 24). */
    val treeDefaultXref: String = "",
)

@Serializable
data class DateJson(
    val text: String = "",
    val year: Int = 0,
    val jd: Int = 0,
    /** Das Datum, wie es im GEDCOM steht ("ABT 1850") - nur bei Ereignissen und erst ab API-Stufe 8, sonst leer. */
    val gedcom: String = "",
)

@Serializable
data class PlaceJson(
    val name: String = "",
    val short: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class EventJson(val date: DateJson? = null, val place: PlaceJson? = null)

@Serializable
data class Person(
    val xref: String,
    val name: String = "",
    val sortName: String = "",
    /** Vor- und Nachname getrennt (Nachname samt Zusatz wie "de' Medici"), ab api4webtrees 1.6; sonst leer. */
    val given: String = "",
    val surname: String = "",
    val sex: String = "U",
    val isDead: Boolean = false,
    @SerialName("private") val isPrivate: Boolean = false,
    val lifespan: String = "",
    val birth: EventJson? = null,
    val death: EventJson? = null,
    val thumb: String? = null,
    val url: String = "",
    /** Nur bei Kindern in den Partnerfamilien (ab API-Stufe 10): ihre Heiraten, fuer die Lebenslinie der Eltern. */
    val marriages: List<MarriageJson> = emptyList(),
    /** Ab API-Stufe 14 (sonst leer/null): Rufname, Taufe (CHR, sonst BAPM), Begraebnis (BURI, sonst CREM), erster Beruf. */
    val call: String = "",
    val chr: EventJson? = null,
    val buri: EventJson? = null,
    val occupation: String? = null,
    /** Nur in der Merkliste ab API-Stufe 30: die Notiz zum Favoriten. */
    val note: String = "",
)

/** Eine Heirat eines Kindes: Partner (leer, wenn privat), Datum und Ort (beides kann fehlen). */
@Serializable
data class MarriageJson(val family: String = "", val spouse: String = "", val date: DateJson? = null, val place: PlaceJson? = null)

@Serializable
data class SourceRef(
    val xref: String = "",
    val title: String = "",
    /** Seitenangabe des Verweises (PAGE, "Taufen 1833, Nr. 19"), ab api4webtrees mit Seitenangabe; sonst leer. */
    val page: String = "",
    /** Ab API-Stufe 18: Qualitaet (QUAY 0 unzuverlaessig ... 3 Primaerquelle), Datum und Text der Fundstelle (DATA), Notizen, Medien. */
    val quality: Int? = null,
    val date: DateJson? = null,
    val text: String = "",
    val notes: List<String> = emptyList(),
    val media: List<MediaJson> = emptyList(),
) {
    /** Eine Quelle ohne Datensatz ("laut Martha Meier"): nur Text, keine Quellenverwaltung. */
    val istText: Boolean get() = xref.isEmpty()

    /** Titel und Seite in einer Zeile: "Kirchenbuch Bienenbuettel, Taufen 1833, Nr. 19". */
    fun mitSeite(): String = title.ifBlank { xref } + page.replace('\n', ' ').trim().let { if (it.isEmpty()) "" else ", $it" }
}

@Serializable
data class FactJson(
    val id: String,
    val tag: String = "",
    val label: String = "",
    /** false: Hersteller-Tag, das webtrees nicht kennt (z. B. _INET) - die App blendet es aus. */
    val known: Boolean = true,
    val value: String = "",
    val type: String = "",
    val date: DateJson? = null,
    val place: PlaceJson? = null,
    val notes: List<String> = emptyList(),
    val sources: List<SourceRef> = emptyList(),
    /** Ab API-Stufe 19 (sonst leer/null): verlinkte Paten/Zeugen, freie aus Notizen, Art je Notiz, TYPE uebersetzt. */
    val associates: List<Associate> = emptyList(),
    val freeAssociates: List<FreeAssociate> = emptyList(),
    /** Parallel zu [notes]: "note" oder "associates" (die Notiz steckt schon in [freeAssociates]); leer bei aelteren Modulen. */
    val noteKinds: List<String> = emptyList(),
    val typeLabel: String? = null,
    /** Ab API-Stufe 23: Notiz, die auf einen Notiz-Datensatz zeigt (1 NOTE @N1@) - nicht als Text aendern. */
    val noteXref: String? = null,
)

/**
 * Pate, Trauzeuge oder sonst Beteiligter mit eigenem Datensatz (2 _ASSO am Ereignis, 1 ASSO an der Person).
 * [role] ist normalisiert (godparent, witness, other), [rela] der Rohwert, [label] die Uebersetzung ("Patin").
 * [isPrivate]: Name und Geschlecht fehlen, die App zeigt nur "privat". [level1]: an der Person statt an der Taufe erfasst.
 */
@Serializable
data class Associate(
    val xref: String = "",
    val name: String? = null,
    val sex: String? = null,
    val rela: String = "",
    val role: String = "other",
    val label: String = "",
    @SerialName("private") val isPrivate: Boolean = false,
    val level1: Boolean = false,
    val notes: List<String> = emptyList(),
    val sources: List<SourceRef> = emptyList(),
)

/** Pate/Zeuge ohne Datensatz aus einer Notiz "Paten: A, Beruf zu Ort; B": [name] bis zum Komma, [detail] der Rest; alte Schreibweise ohne ";" nur [text]. */
@Serializable
data class FreeAssociate(
    val role: String = "other",
    val name: String? = null,
    val detail: String? = null,
    val text: String = "",
)

/** Wo eine Person Pate oder Zeuge ist (Gegenrichtung): das Ereignis ([tag], [label]) am Datensatz [record] (INDI oder FAM). */
@Serializable
data class AssociatedIn(
    val record: String = "",
    val recordType: String = "INDI",
    val name: String = "",
    val tag: String = "",
    val label: String = "",
    val factId: String = "",
    val date: DateJson? = null,
    val place: PlaceJson? = null,
    val rela: String = "",
    val role: String = "other",
    /** Die Rolle uebersetzt ("Pate", "Zeugin"). */
    val label2: String = "",
    val level1: Boolean = false,
    val url: String = "",
    /** Bei Familien (erbeten, noch nicht im Modul): die Partner, damit der Eintrag zu einer Person fuehrt. */
    val husband: String? = null,
    val wife: String? = null,
)

/** Ab dieser Stufe liefert api4webtrees Paten und Trauzeugen (associates, freeAssociates, associatedIn, typeLabel). */
const val API_ASSOCIATES = 19

@Serializable
data class MediaJson(
    val xref: String = "",
    val title: String = "",
    val mime: String = "",
    val isImage: Boolean = false,
    val thumb: String? = null,
    val file: String = "",
    val url: String = "",
    /** Nur in der Fotouebersicht (MediaList): bis zu drei verknuepfte Personen. */
    val people: List<PersonRef> = emptyList(),
    /** Pfad der Datei im Medienordner des Baums (ab API-Stufe 9); null bei Internetadressen oder aelterem Modul. */
    val path: String? = null,
    /** Art (photo, document ...) und Format (JPG, PDF) - ab API-Stufe 23. */
    val type: String? = null,
    val format: String? = null,
    /** Nur bei Medien eines Orts: Dateigroesse und Bildmasse, wie webtrees sie zeigt. */
    val info: List<String> = emptyList(),
)

@Serializable
data class PersonRef(val xref: String, val name: String = "")

@Serializable
data class MediaPage(val page: Int = 1, val nextPage: Int? = null, val data: List<MediaJson> = emptyList())

@Serializable
data class FamilyJson(
    val xref: String,
    val name: String = "",
    val url: String = "",
    val husband: Person? = null,
    val wife: Person? = null,
    val spouse: Person? = null,
    val marriage: EventJson? = null,
    val facts: List<FactJson> = emptyList(),
    val children: List<Person> = emptyList(),
    val media: List<MediaJson> = emptyList(),
    /** Nur in stepFamilies: der gemeinsame Elternteil (XREF). */
    val parent: String? = null,
    /** Ab API-Stufe 30 (nur in der Family-Antwort): Forschungsaufgaben und letzte Aenderung. */
    val tasks: List<TaskJson> = emptyList(),
    val lastChange: LastChange? = null,
)

@Serializable
data class IndividualDetail(
    val person: Person,
    /** "Urgrossmutter" ... - Verwandtschaft zur Bezugsperson, leer wenn unbekannt (Modul ab API 2). */
    val relationship: String = "",
    val canEdit: Boolean = false,
    val facts: List<FactJson> = emptyList(),
    val parentFamilies: List<FamilyJson> = emptyList(),
    val spouseFamilies: List<FamilyJson> = emptyList(),
    /** Familien der Eltern mit anderen Partnern, ihre Kinder sind die Halbgeschwister (api4webtrees ab 1.8.0, Stufe 12). */
    val stepFamilies: List<FamilyJson> = emptyList(),
    val media: List<MediaJson> = emptyList(),
    /** Wo die Person Pate oder Zeuge ist (ab API-Stufe 19, sonst leer), nach Datum. */
    val associatedIn: List<AssociatedIn> = emptyList(),
    /** Ab API-Stufe 30: Forschungsaufgaben und letzte Aenderung. */
    val tasks: List<TaskJson> = emptyList(),
    val lastChange: LastChange? = null,
)

/** Ein Halbgeschwister und ob es ueber den Vater verwandt ist (sonst ueber die Mutter). */
data class HalfSibling(val person: Person, val paternal: Boolean)

/** Halbgeschwister aus stepFamilies, vaeterlicherseits zuerst; leer bei Modulen vor 1.8.0. */
fun IndividualDetail.halfSiblings(): List<HalfSibling> {
    val fathers = parentFamilies.mapNotNull { it.husband?.xref }.toSet()
    val full = parentFamilies.flatMap { it.children }.map { it.xref }.toSet()
    return stepFamilies
        .flatMap { family -> family.children.map { HalfSibling(it, family.parent != null && family.parent in fathers) } }
        .filter { it.person.xref != person.xref && it.person.xref !in full }
        .distinctBy { it.person.xref }
        .sortedByDescending { it.paternal }
}

/** Antwort von Places: Ortsnamen des Baums als Vorschlaege beim Tippen. */
@Serializable
data class PlaceList(val query: String = "", val data: List<String> = emptyList())

/** Ab dieser Stufe: Ortsliste (Places?list=1) und ein Ort (Place). */
const val API_PLACE_LIST = 21

/** Ab dieser Stufe: Ortsdaten speichern (POST Place). */
const val API_PLACE_WRITE = 22

/** Ab dieser Stufe: Orte umbenennen und zusammenfuehren (POST PlaceRename). */
const val API_PLACE_RENAME = 23
/** Ab Stufe 27: _LOC-Hierarchie (Hoefe/Haeuser als Unterorte), TYPE und Ereignisse am Ort. */
const val API_LOC_HIERARCHY = 27

/** Ab Stufe 29: Personen zusammenfuehren (POST Merge, MergeUndo, Merges) - nur Verwalter des Stammbaums. */
const val API_MERGE = 29

/** Ein Fakt in der Vorschau des Zusammenfuehrens: [same] wortgleich auch bei der anderen Person, [link] bleibt immer, [keep] Vorschlag. */
@Serializable
data class MergeFact(val id: String, val tag: String = "", val label: String = "", val text: String = "", val same: Boolean = false, val link: Boolean = false, val keep: Boolean = true)

/** Ein Datensatz, der auf die zweite Person zeigt (Familie, Quelle, Notiz ...). */
@Serializable
data class MergeLink(val xref: String, val type: String = "", val name: String = "")

/** Ein weiteres Paar, das wahrscheinlich dieselbe Person ist (father, mother, spouse, child). */
@Serializable
data class MergeSuggestion(val role: String = "", val xref1: String, val name1: String = "", val xref2: String, val name2: String = "")

/** Antwort von Merge mit preview. */
@Serializable
data class MergePreview(
    val ok: Boolean = false,
    val person1: Person,
    val person2: Person,
    val facts1: List<MergeFact> = emptyList(),
    val facts2: List<MergeFact> = emptyList(),
    val links: List<MergeLink> = emptyList(),
    val suggestions: List<MergeSuggestion> = emptyList(),
)

/** Antwort von Merge ohne preview: [xref] bleibt, [removed] ist weg, [mergeId] fuer Rueckgaengig. */
@Serializable
data class MergeResult(val ok: Boolean = false, val xref: String = "", val removed: String = "", val mergeId: String = "", val records: Int = 0, val pending: Boolean = false)

/** Ein seitdem geaenderter Datensatz, der das Rueckgaengig verhindert. */
@Serializable
data class MergeChanged(val xref: String, val name: String = "")

/** Antwort von MergeUndo; bei ok = false mit [error] "changed-since" und [changed]. */
@Serializable
data class MergeUndoResult(val ok: Boolean = false, val preview: Boolean = false, val xref: String = "", val removed: String = "", val records: Int = 0,
    val pending: Boolean = false, val error: String? = null, val changed: List<MergeChanged> = emptyList())

/** Ein Eintrag im Protokoll der Zusammenfuehrungen (Merges), [undone] = Zeitpunkt des Rueckgaengig oder null. */
@Serializable
data class MergeEntry(val id: String, val time: String = "", val user: String = "", val xref: String = "", val name: String = "", val removed: String = "",
    val removedName: String = "", val records: Int = 0, val undone: String? = null)

@Serializable
data class MergeList(val ok: Boolean = false, val merges: List<MergeEntry> = emptyList())

@Serializable
data class PlaceRenameLocation(val from: String? = null, val to: String? = null, val conflicts: List<String> = emptyList())

/** Antwort von PlaceRename - mit preview nur die Zahlen. */
@Serializable
data class PlaceRenameResult(
    val ok: Boolean = false,
    val preview: Boolean = false,
    val from: String = "",
    val to: String = "",
    val merge: Boolean = false,
    val records: Int = 0,
    val events: Int = 0,
    val subPlaces: Int = 0,
    val skipped: Int = 0,
    val location: PlaceRenameLocation = PlaceRenameLocation(),
    val pending: Boolean = false,
)

/**
 * Ortsdaten speichern: null = nicht anfassen. Koordinaten nur mit [koordinatenAendern]; dann entfernt lat/lng null sie.
 * [mapData]: auch in die Geografischen Daten von webtrees (nur Administratoren).
 */
data class PlaceRequest(
    val name: String,
    val gov: String? = null,
    val note: String? = null,
    val koordinatenAendern: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val mapData: Boolean = false,
    /** Die verknuepften Medienobjekte (Kennungen); ersetzt die Liste. null = nicht anfassen. */
    val media: List<String>? = null,
    val postalCode: String? = null,
    val region: String? = null,
    val country: String? = null,
    val shortName: String? = null,
    /** Art des Orts (TYPE am _LOC: Hof, Haus, Gemeinde ...), ab Stufe 27; "" entfernt. */
    val type: String? = null,
    /** Uebergeordneter _LOC (Hierarchie "1 _LOC @L1@"), ab Stufe 27 - nur mit parentAendern; null loest. */
    val parentAendern: Boolean = false,
    val parent: String? = null,
)

/** Ein Ort der Ortsliste - der PLAC-Text, wie er an sichtbaren Ereignissen steht. */
@Serializable
data class PlaceSummary(
    val name: String,
    val events: Int = 0,
    val individuals: Int = 0,
    val families: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null,
    /** Herkunft der Koordinaten: "location" (_LOC), "mapData" (Geografische Daten), "event" (MAP am Ereignis). */
    val coordSource: String? = null,
    /** Kennung des _LOC-Datensatzes, wenn der Ort einen hat. */
    val location: String? = null,
    val gov: String? = null,
    /** Kurzname aus dem _LOC (NAME/ABBR) - fuer "Orte kuerzen" in Buechern. */
    val shortName: String? = null,
    /** Art des Orts aus dem _LOC (TYPE: Hof, Haus ...), ab Stufe 27. Orte nur aus der _LOC-Hierarchie haben events 0. */
    val type: String? = null,
    /** GOV-Typnummer der Art (GEDCOM-L 2 _GOVTYPE: 24 Hof, 55 Dorf ...), api4webtrees ab 1.18.1. */
    val govType: String? = null,
)

@Serializable
data class PlaceSummaryList(val total: Int = 0, val places: List<PlaceSummary> = emptyList())

@Serializable
data class PlaceEvent(val tag: String = "", val label: String = "", val date: DateJson? = null)

@Serializable
data class PlaceUsePerson(
    val xref: String,
    val name: String = "",
    val sex: String = "U",
    val isDead: Boolean = false,
    @SerialName("private") val isPrivate: Boolean = false,
    val lifespan: String = "",
    val thumb: String? = null,
    val url: String = "",
    val facts: List<PlaceEvent> = emptyList(),
) {
    fun person() = Person(xref = xref, name = name, sex = sex, isDead = isDead, isPrivate = isPrivate, lifespan = lifespan, thumb = thumb, url = url)
}

@Serializable
data class PlaceUseFamily(
    val xref: String,
    val name: String = "",
    val husband: String? = null,
    val wife: String? = null,
    val facts: List<PlaceEvent> = emptyList(),
)

/** Ort darunter; [location] und [type] (Hof, Haus ...) aus dem _LOC, ab Stufe 27. */
@Serializable
data class PlaceChild(val name: String, val events: Int = 0, val location: String? = null, val type: String? = null, val govType: String? = null)

/** Quellenangabe am _LOC: Quelle (xref null bei Text-Quelle) und Seite. */
@Serializable
data class LocationSource(val xref: String? = null, val title: String? = null, val page: String? = null)

/** Uebergeordneter Ort in der _LOC-Hierarchie ("1 _LOC @L1@" mit 2 TYPE POLI/RELI/GEOG/CULT und 2 DATE), ab Stufe 27. */
@Serializable
data class LocationParent(val xref: String, val name: String = "", val fullName: String = "", val type: String? = null, val date: DateJson? = null)

/** Ereignis am Ort selbst ("1 EVEN" am _LOC: Brand, Umbau, Besitzwechsel ...), ab Stufe 27. */
@Serializable
data class LocationEvent(
    val factId: String = "",
    val type: String? = null,
    val label: String = "",
    val value: String? = null,
    val date: DateJson? = null,
    val place: String? = null,
    val notes: List<String> = emptyList(),
    val sources: List<LocationSource> = emptyList(),
)

/** Der GEDCOM-L-Datensatz _LOC eines Orts. */
@Serializable
data class LocationJson(
    val xref: String,
    val name: String = "",
    /** Art des Orts (TYPE: Hof, Haus, Gemeinde ...), ab Stufe 27; bei mehreren datierten Arten die letzte. */
    val type: String? = null,
    /** GOV-Typnummer der Art (GEDCOM-L 2 _GOVTYPE), api4webtrees ab 1.18.1. */
    val govType: String? = null,
    val parents: List<LocationParent> = emptyList(),
    val events: List<LocationEvent> = emptyList(),
    val gov: String? = null,
    val shortName: String? = null,
    val postalCode: String? = null,
    val region: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val notes: List<String> = emptyList(),
    val sources: List<LocationSource> = emptyList(),
    val media: List<MediaJson> = emptyList(),
    val canEdit: Boolean = false,
    val url: String = "",
)

/** Ereignisse am Ort nach Art (Kacheln der Ortsansicht). */
@Serializable
data class PlaceEventCounts(val birth: Int = 0, val marriage: Int = 0, val death: Int = 0, val other: Int = 0)

@Serializable
data class PlaceDetail(
    val name: String,
    val eventCounts: PlaceEventCounts? = null,
    val levels: List<String> = emptyList(),
    val parent: String? = null,
    val children: List<PlaceChild> = emptyList(),
    val events: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null,
    val coordSource: String? = null,
    val location: LocationJson? = null,
    val individuals: List<PlaceUsePerson> = emptyList(),
    val families: List<PlaceUseFamily> = emptyList(),
    val moreIndividuals: Int = 0,
    val moreFamilies: Int = 0,
    val canEdit: Boolean = false,
)

@Serializable
data class PersonPage(
    val query: String = "",
    val page: Int = 1,
    val nextPage: Int? = null,
    val data: List<Person> = emptyList(),
)

@Serializable
data class Ancestor(
    val n: Int,
    val person: Person,
    val hasParents: Boolean = false,
    /** Nur mit siblings=1 ab API-Stufe 15: die anderen Kinder der Elternfamilie (ohne Halbgeschwister); sonst null. */
    val siblings: List<Person>? = null,
)

@Serializable
data class Pedigree(
    val root: String = "",
    val generations: Int = 0,
    val ancestors: List<Ancestor> = emptyList(),
)

@Serializable
data class DescendantFamily(
    val xref: String,
    val spouse: Person? = null,
    val marriage: EventJson? = null,
    val children: List<DescendantNode> = emptyList(),
)

@Serializable
data class DescendantNode(val person: Person, val families: List<DescendantFamily> = emptyList())

@Serializable
data class Descendants(val root: String = "", val generations: Int = 0, val tree: DescendantNode)

/** Merkliste (ab API-Stufe 11): die gemerkten Personen des Benutzers in diesem Baum. */
@Serializable
data class BookmarkList(val data: List<Person> = emptyList(), /** Ab Stufe 30: die Favoriten des Stammbaums (Verwalter setzen sie). */ val treeFavorites: List<Person> = emptyList())

@Serializable
data class BookmarkRequest(val xref: String, val add: Boolean, val note: String? = null, val forTree: Boolean = false)

/** Ab Stufe 30: Forschungsaufgaben (webtrees _TODO), Reihenfolge, Aenderungsverlauf, Merkliste in den Favoriten. */
const val API_TASKS = 30

/** Eine Forschungsaufgabe an Person oder Familie. */
@Serializable
data class TaskJson(val record: String, val recordType: String = "INDI", val name: String = "", val factId: String = "", val text: String = "",
    val date: DateJson? = null, val jd: Int? = null, val user: String = "", val note: String = "", val pending: Boolean = false)

@Serializable
data class TaskList(val ok: Boolean = false, val today: Int = 0, val tasks: List<TaskJson> = emptyList())

@Serializable
data class TaskRequest(val factId: String? = null, val text: String, val date: String? = null, val user: String? = null, val note: String? = null)

@Serializable
data class ReorderRequest(val type: String, val order: List<String>)

/** Vorhandene Person anfuegen (Route Link, ab Stufe 8): [individual] wird [relation] von [relativeTo]. */
@Serializable
data class LinkRequest(val individual: String, val relation: String, val relativeTo: String, val family: String? = null, val marriageDate: String? = null, val marriagePlace: String? = null)

/** Letzte Aenderung eines Datensatzes (CHAN). */
@Serializable
data class LastChange(val time: String = "", val user: String = "")

/** Ein Eintrag im Aenderungsverlauf des Baums. */
@Serializable
data class ChangeEntry(val time: String = "", val user: String = "", val xref: String = "", val type: String = "", val name: String = "", val action: String = "", val pending: Boolean = false)

@Serializable
data class ChangeList(val ok: Boolean = false, val changes: List<ChangeEntry> = emptyList())

/** Startperson festlegen (ab API-Stufe 24): ohne [forTree] die eigene Standardperson, mit die des Stammbaums. */
const val API_START_PERSON = 24

@Serializable
data class StartPersonRequest(val xref: String, val forTree: Boolean = false)

@Serializable
data class StartPersonResult(val ok: Boolean = false, val startXref: String = "", val defaultXref: String = "", val treeDefaultXref: String = "")

@Serializable
data class PendingRecord(
    val xref: String,
    val type: String = "",
    val name: String = "",
    /** new | changed | deleted */
    val kind: String = "changed",
    val changes: Int = 0,
    val users: List<String> = emptyList(),
    val time: String = "",
)

@Serializable
data class PendingList(val data: List<PendingRecord> = emptyList())

@Serializable
data class ModerationResult(val ok: Boolean = false, val pending: Int = 0)

@Serializable
data class Anniversary(
    val inDays: Int = 0,
    val tag: String = "",
    val label: String = "",
    val years: Int = 0,
    val date: DateJson? = null,
    val xref: String = "",
    val name: String = "",
    val person: Person? = null,
    val couple: List<Person> = emptyList(),
)

@Serializable
data class AnniversaryList(val days: Int = 0, val data: List<Anniversary> = emptyList())

@Serializable
data class TagInfo(val tag: String, val label: String = "", val isEvent: Boolean = false)

@Serializable
data class TagList(val type: String = "", val data: List<TagInfo> = emptyList())

@Serializable
data class WriteResult(
    val ok: Boolean = false,
    val xref: String = "",
    val pending: Boolean = false,
    val family: String? = null,
    val media: String? = null,
    /** Route Citation: die neue Kennung des Ereignisses (Hash des Inhalts, aendert sich mit jedem Schreiben). */
    val factId: String? = null,
    /** Route MediaFromFile: true, wenn es das Medienobjekt zu der Datei schon gab. */
    val existing: Boolean? = null,
    /** Route Place: so viele Ereignisse bekamen den Verweis auf den _LOC. */
    val linked: Int? = null,
    /** Route Place: Koordinaten auch in die Geografischen Daten von webtrees geschrieben. */
    val mapData: Boolean? = null,
)

@Serializable
data class MediaFromFileRequest(val file: String, val title: String? = null, val type: String? = null)

/**
 * Quellenverweis anlegen, aendern, loeschen oder verschieben (Route Citation, ab Stufe 18). Null = nicht anfassen:
 * nur genannte Teile werden ersetzt. [factId] null = allgemeiner Verweis am Datensatz; [index] null = neu anhaengen.
 */
@Serializable
data class CitationRequest(
    val factId: String? = null,
    val index: Int? = null,
    val delete: Boolean? = null,
    val moveTo: Int? = null,
    /** Kennung einer Quelle ("S1") oder freier Text ("laut Martha Meier"). */
    val source: String? = null,
    val page: String? = null,
    val quality: String? = null,
    val date: String? = null,
    val text: String? = null,
    val note: String? = null,
    val media: List<String>? = null,
)

// ── Schreib-Anfragen ─────────────────────────────────────────────────

/** null = Feld nicht anfassen; "" = Feld leeren. Deshalb werden null-Werte nicht mitgeschickt. */
@Serializable
data class FactRequest(
    val factId: String? = null,
    val tag: String? = null,
    val value: String? = null,
    val date: String? = null,
    val place: String? = null,
    val note: String? = null,
    /** Art (2 TYPE), ab API-Stufe 20; bei der Heirat civil, religious, partners oder "common law" - der Server schreibt webtrees' Form. */
    val type: String? = null,
)

/**
 * Paten, Trauzeugen und andere Beteiligte eines Ereignisses schreiben (Route Association, ab Stufe 20). null = nicht
 * anfassen. [linked] ersetzt die verknuepften Personen in dieser Reihenfolge, [free] die ohne Datensatz (je eine Zeile
 * _GODP/_WITN); [convertLevel1] holt "1 ASSO" der Person, die in [linked] stehen, in die Taufe.
 */
@Serializable
data class AssociationRequest(
    val factId: String,
    val linked: List<LinkedAssociateRequest>? = null,
    val free: List<FreeAssociateRequest>? = null,
    val convertLevel1: Boolean? = null,
)

/** [role]: godparent, witness oder other (dann [rela] als Text, z. B. "Hebamme"); [note] null = Notiz nicht anfassen. */
@Serializable
data class LinkedAssociateRequest(val xref: String, val role: String, val rela: String? = null, val note: String? = null)

/** [role]: godparent oder witness. */
@Serializable
data class FreeAssociateRequest(val text: String, val role: String)

/** Ab dieser Stufe schreibt api4webtrees Paten/Trauzeugen (Route Association) und die Art eines Ereignisses. */
const val API_ASSOCIATES_WRITE = 20

@Serializable
data class DeleteFactRequest(val factId: String)

@Serializable
data class UnlinkRequest(val family: String, val individual: String)

@Serializable
data class PairRequest(val code: String)

@Serializable
data class PairResult(val ok: Boolean = false, val tree: String = "", val user: String = "")

@Serializable
class EmptyRequest

@Serializable
data class AddIndividualRequest(
    val relation: String,
    val relativeTo: String? = null,
    val family: String? = null,
    val given: String = "",
    val surname: String = "",
    val sex: String = "U",
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val dead: Boolean = false,
    val deathDate: String? = null,
    val deathPlace: String? = null,
    val marriageDate: String? = null,
    val marriagePlace: String? = null,
)

// ── Quellen (ab API-Stufe 18) ─────────────────────────────────────────

/** Eine Quelle in der Liste: Kopfdaten, erstes Archiv mit Signatur und wie oft sie zitiert wird. */
@Serializable
data class SourceSummary(
    val xref: String,
    val title: String = "",
    val author: String = "",
    val publication: String = "",
    val abbreviation: String = "",
    val repository: String = "",
    val callNumber: String = "",
    val canEdit: Boolean = false,
    val url: String = "",
    val uses: Int = 0,
)

@Serializable
data class SourceList(val total: Int = 0, val sources: List<SourceSummary> = emptyList())

@Serializable
data class RepositoryRef(val xref: String = "", val name: String = "", val callNumber: String = "")

/** Wer eine Quelle zitiert: die Person bzw. Familie und die Ereignisse mit dem Verweis. */
@Serializable
data class SourceUseFamily(val xref: String, val name: String = "", val husband: String? = null, val wife: String? = null, val facts: List<String> = emptyList())

@Serializable
data class SourceUsePerson(
    val xref: String,
    val name: String = "",
    val sex: String = "U",
    val isDead: Boolean = false,
    @SerialName("private") val isPrivate: Boolean = false,
    val lifespan: String = "",
    val thumb: String? = null,
    val url: String = "",
    val facts: List<String> = emptyList(),
) {
    fun person() = Person(xref = xref, name = name, sex = sex, isDead = isDead, isPrivate = isPrivate, lifespan = lifespan, thumb = thumb, url = url)
}

@Serializable
data class SourceDetail(
    val xref: String,
    val title: String = "",
    val author: String = "",
    val publication: String = "",
    val abbreviation: String = "",
    val repository: String = "",
    val callNumber: String = "",
    val canEdit: Boolean = false,
    val url: String = "",
    val text: String = "",
    val notes: List<String> = emptyList(),
    val media: List<MediaJson> = emptyList(),
    val repositories: List<RepositoryRef> = emptyList(),
    val individuals: List<SourceUsePerson> = emptyList(),
    val families: List<SourceUseFamily> = emptyList(),
    val moreIndividuals: Int = 0,
    val moreFamilies: Int = 0,
)

/** Ab dieser Stufe kennt api4webtrees Quellen (Routen Sources/Source, vollstaendige Verweise). */
const val API_SOURCES = 18

/** Quelle anlegen (title Pflicht) oder aendern - null = nicht anfassen; repository "" = Archiv entfernen. */
@Serializable
data class SourceRequest(
    val title: String? = null,
    val author: String? = null,
    val publication: String? = null,
    val abbreviation: String? = null,
    val text: String? = null,
    val note: String? = null,
    val repository: String? = null,
    val callNumber: String? = null,
    /** Die verknuepften Medienobjekte (Kennungen); ersetzt die Liste. */
    val media: List<String>? = null,
)

@Serializable
data class RepositoryRequest(val name: String)

@Serializable
data class RepositorySummary(val xref: String, val name: String = "", val address: String = "", val canEdit: Boolean = false, val uses: Int = 0)

@Serializable
data class RepositoryList(val total: Int = 0, val repositories: List<RepositorySummary> = emptyList())
