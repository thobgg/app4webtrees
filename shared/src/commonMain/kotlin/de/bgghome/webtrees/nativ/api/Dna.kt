package de.bgghome.webtrees.nativ.api

import kotlinx.serialization.Serializable

/*
 * JSON-Formen des webtrees-Moduls dna4webtrees (github.com/thobgg/dna4webtrees, docs/API.md): was sein Reiter "DNA" zeigt -
 * Tests, Haplogruppen, Treffer mit Einordnung - und wer fuer eine Linie testen koennte. Das Modul nennt Personen nur als
 * Kennung und liefert nie mehr, als seine Seiten demselben Benutzer zeigen. Die Linien selbst stehen in Models.kt (DnaLinien).
 */

/** Seite im Netz zu einer Haplogruppe; zum Oeffnen im Browser. */
@Serializable
data class DnaVerweis(val site: String = "", val url: String = "")

/** Aus einer SNP-Liste berechneter Zweig, mit der Fassung des Baums, nach dem gerechnet wurde. */
@Serializable
data class DnaBerechnet(val clade: String = "", val tree: String = "", val tie: Boolean = false)

/**
 * Ein DNA-Eintrag einer Person. [kind]: test, y-haplogroup, mt-haplogroup, y-other, mt-other, autosomal, other.
 * [restriction] (confidential, privacy, none) bekommt nur, wer den Eintrag aendern darf; sonst null oder leer.
 */
@Serializable
data class DnaEintrag(
    val xref: String = "",
    val factId: String = "",
    val kind: String = "",
    val value: String = "",
    val type: String = "",
    val provider: String = "",
    /** Datum, wie es im GEDCOM steht ("MAR 2022"). */
    val date: String = "",
    val calculated: DnaBerechnet? = null,
    val restriction: String? = null,
    val links: List<DnaVerweis> = emptyList(),
)

/** Haplogruppen einer Linie passen nicht zusammen: nach dem Baum ("tree") oder schon nach dem Anfangsbuchstaben ("letter"). */
@Serializable
data class DnaWiderspruch(val by: String = "", val names: List<String> = emptyList())

/** Eintraege anderer Personen auf derselben Y- bzw. mt-Linie. */
@Serializable
data class DnaLinienEintraege(val entries: List<DnaEintrag> = emptyList(), val contradiction: DnaWiderspruch? = null)

/** Verwandtschaft im Baum: [code] (1C, 1C1R, SIB, HSIB ...) und der Name, wie webtrees ihn bildet. */
@Serializable
data class DnaVerwandtschaft(val code: String? = null, val text: String = "", val half: Boolean = false, val multiple: Boolean = false, val distant: Boolean = false)

/** Uebliche Menge gemeinsamer DNA fuer eine Verwandtschaft (Shared cM Project 4.0), in Centimorgan. */
@Serializable
data class DnaUeblich(val average: Double = 0.0, val minimum: Double = 0.0, val maximum: Double = 0.0)

/** Koennen beide nach dem Baum X-DNA teilen, und ueber wen zuerst. */
@Serializable
data class DnaXPruefung(val possible: Boolean = false, val nearestCommonAncestor: String? = null)

@Serializable
data class DnaKandidat(val code: String = "", val text: String = "")

/** Warnungen des Moduls an einem Treffer. */
object DnaWarnung {
    const val OHNE_SCHUTZ = "unrestricted"
    const val AUSSERHALB = "outside-range"
    const val MEHRFACH = "multiple-relationships"
    const val ZWEITE = "distant-second-relationship"
    const val X_OHNE_VORFAHR = "x-segment-without-common-x-ancestor"
}

/**
 * Ein Treffer: die gemeinsame DNA zweier Personen, wie ein Anbieter sie meldet, und was der Baum dazu sagt.
 * [other] ist null bei jemandem ausserhalb des Baums ([name]) und bei einer Person, die der Benutzer nicht sehen darf ([private]).
 */
@Serializable
data class DnaTreffer(
    val recordedAt: String = "",
    val factId: String = "",
    val other: String? = null,
    val private: Boolean = false,
    val name: String? = null,
    val cm: Double = 0.0,
    val segments: Int? = null,
    val longest: Double? = null,
    val segmentsOnX: Int? = null,
    val hasSegmentTable: Boolean = false,
    val provider: String = "",
    val date: String = "",
    val relationship: DnaVerwandtschaft? = null,
    val commonAncestors: List<String> = emptyList(),
    /** null, wenn die beiden auf mehreren Wegen aehnlich nah verwandt sind oder die Verwandtschaft nicht in der Tabelle steht. */
    val usual: DnaUeblich? = null,
    val outsideRange: Boolean? = null,
    /** Ohne Verwandtschaft im Baum: die Verwandtschaften, fuer die dieser Wert gemeldet wurde. */
    val candidates: List<DnaKandidat> = emptyList(),
    val x: DnaXPruefung? = null,
    val warnings: List<String> = emptyList(),
    val restriction: String? = null,
    val sharedCmUrl: String? = null,
)

/** Antwort von ApiPerson. [lines]: "y" und "mt". */
@Serializable
data class DnaPerson(
    val xref: String = "",
    val canEdit: Boolean = false,
    val entries: List<DnaEintrag> = emptyList(),
    val lines: Map<String, DnaLinienEintraege> = emptyMap(),
    val matches: List<DnaTreffer> = emptyList(),
)

@Serializable
data class DnaTestKandidat(val xref: String = "", val hasEntry: Boolean = false)

/** Lebende Personen auf einer Linie, soweit der Benutzer sie sehen darf. */
@Serializable
data class DnaTesterLinie(val earliestAncestor: String? = null, val onLine: Int = 0, val living: List<DnaTestKandidat> = emptyList())

/** Antwort von ApiTesters. [lines]: "y" und "mt". */
@Serializable
data class DnaTester(val xref: String = "", val lines: Map<String, DnaTesterLinie> = emptyMap())

/** Alle Kennungen, zu denen die Ansicht einen Namen braucht. */
fun DnaPerson.kennungen(): Set<String> =
    (lines.values.flatMap { l -> l.entries.map { it.xref } } +
        matches.flatMap { m -> listOfNotNull(m.other, m.x?.nearestCommonAncestor) + m.commonAncestors }).filter { it.isNotEmpty() }.toSet()

fun DnaTester.kennungen(): Set<String> =
    lines.values.flatMap { l -> listOfNotNull(l.earliestAncestor) + l.living.map { it.xref } }.filter { it.isNotEmpty() }.toSet()
