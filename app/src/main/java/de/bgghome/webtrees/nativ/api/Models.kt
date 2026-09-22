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
)

/** Eine Heirat eines Kindes: Partner (leer, wenn privat), Datum und Ort (beides kann fehlen). */
@Serializable
data class MarriageJson(val family: String = "", val spouse: String = "", val date: DateJson? = null, val place: PlaceJson? = null)

@Serializable
data class SourceRef(val xref: String = "", val title: String = "")

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
)

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
    val media: List<MediaJson> = emptyList(),
)

/** Antwort von Places: Ortsnamen des Baums als Vorschlaege beim Tippen. */
@Serializable
data class PlaceList(val query: String = "", val data: List<String> = emptyList())

@Serializable
data class PersonPage(
    val query: String = "",
    val page: Int = 1,
    val nextPage: Int? = null,
    val data: List<Person> = emptyList(),
)

@Serializable
data class Ancestor(val n: Int, val person: Person, val hasParents: Boolean = false)

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
)

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
