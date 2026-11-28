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

/** Antwort von ApiInfo: Fassung des Moduls, Stufe seiner JSON-Routen und die Auswahllisten seiner Formulare. */
@Serializable
data class DnaModulInfo(
    val version: String = "",
    val level: Int = 0,
    val providers: List<String> = emptyList(),
    val testTypes: List<String> = emptyList(),
    val restrictions: List<String> = emptyList(),
)

/** Eine Y- bzw. mt-Linie des Baums, auf der jemand einen Eintrag hat. */
@Serializable
data class DnaUebersichtLinie(val earliestAncestor: String? = null, val entries: List<DnaEintrag> = emptyList(), val contradiction: DnaWiderspruch? = null)

/** Antwort von ApiOverview: alles zu DNA, was der Benutzer in einem Baum sehen darf. [lines]: "y" und "mt". */
@Serializable
data class DnaUebersicht(
    val lines: Map<String, List<DnaUebersichtLinie>> = emptyMap(),
    val matches: List<DnaTreffer> = emptyList(),
    val entries: List<DnaEintrag> = emptyList(),
)

/** Antwort der Schreib-Routen; Fehler kommen als ok:false und werden vorher zur Ausnahme. */
@Serializable
data class DnaGeschrieben(val ok: Boolean = false)

/** Was das Formular "DNA-Test eintragen" an ApiAddTest schickt; leere Felder bleiben weg, der Schutz folgt dann der Regel des Moduls. */
data class DnaTestEingabe(val art: String, val anbieter: String = "", val datum: String = "", val haplogruppe: String = "", val notiz: String = "", val schutz: String = "") {
    fun felder(): Map<String, String> = mapOf("test_type" to art, "provider" to anbieter, "date" to datum, "haplogroup" to haplogruppe, "note" to notiz, "restriction" to schutz)
        .mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
}

/** Was das Formular "DNA-Treffer eintragen" an ApiAddMatch schickt: [andere] (Kennung im Baum) oder [name]. */
data class DnaTrefferEingabe(
    val andere: String = "", val name: String = "", val cm: String = "", val segmente: String = "", val laengstes: String = "",
    val anbieter: String = "", val datum: String = "", val notiz: String = "", val tabelle: String = "", val schutz: String = "",
) {
    /** Mindestens: wer (Person oder Name) und wie viel - oder eine Segmenttabelle, aus der das Modul die Menge liest. */
    val vollstaendig get() = (andere.isNotBlank() || name.isNotBlank()) && (zahl(cm) != null || tabelle.isNotBlank())

    fun felder(): Map<String, String> = mapOf(
        "other" to andere, "name" to if (andere.isBlank()) name else "", "cm" to zahl(cm).orEmpty(), "segments" to segmente.filter(Char::isDigit),
        "longest" to zahl(laengstes).orEmpty(), "provider" to anbieter, "date" to datum, "note" to notiz, "table" to tabelle, "restriction" to schutz,
    ).mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

    private companion object {
        /** Zahl mit Punkt, wie der Server sie liest; ein Komma aus der Eingabe wird zum Punkt ("849,9"). */
        fun zahl(text: String): String? = text.trim().replace(',', '.').takeIf { it.toDoubleOrNull() != null }
    }
}

fun DnaUebersicht.kennungen(): Set<String> =
    (entries.map { it.xref } + lines.values.flatten().flatMap { l -> listOfNotNull(l.earliestAncestor) + l.entries.map { it.xref } } +
        matches.flatMap { m -> listOfNotNull(m.recordedAt, m.other, m.x?.nearestCommonAncestor) + m.commonAncestors }).filter { it.isNotEmpty() }.toSet()

/** Ein lebender Nachkomme und wie viel sein Test zur Abdeckung beitruege (0 bis 1). */
@Serializable
data class DnaAbdeckungKandidat(val xref: String = "", val gain: Double = 0.0)

/**
 * Antwort von ApiCoverage: wie viel der autosomalen DNA einer Person durch getestete Nachkommen vertreten ist (0 bis 1,
 * geschaetzt aus Durchschnittswerten) und wessen Test am meisten braechte.
 */
@Serializable
data class DnaAbdeckung(
    val xref: String = "",
    val coverage: Double = 0.0,
    val descendants: Int = 0,
    val generations: Int = 0,
    val tested: List<String> = emptyList(),
    val candidates: List<DnaAbdeckungKandidat> = emptyList(),
)

/** Ein Treffer, der fuer eine Verbindung spricht; [relationship] als Code (1C, SIB ...). */
@Serializable
data class DnaStuetze(val recordedAt: String = "", val factId: String = "", val other: String? = null, val cm: Double = 0.0, val relationship: String? = null)

/** Eine Verbindung Kind - Elternteil unter den Vorfahren und ob Treffer sie stuetzen. Gestuetzt heisst nicht bewiesen. */
@Serializable
data class DnaVerbindung(val generation: Int = 0, val child: String = "", val parent: String = "", val supported: Boolean = false, val matches: List<DnaStuetze> = emptyList())

/** Antwort von ApiEvidence. */
@Serializable
data class DnaGestuetzt(val xref: String = "", val generations: Int = 0, val rule: String = "", val links: List<DnaVerbindung> = emptyList())

fun DnaAbdeckung.kennungen(): Set<String> = (tested + candidates.map { it.xref }).filter { it.isNotEmpty() }.toSet()

fun DnaGestuetzt.kennungen(): Set<String> =
    links.flatMap { l -> listOf(l.child, l.parent) + l.matches.flatMap { listOfNotNull(it.recordedAt, it.other) } }.filter { it.isNotEmpty() }.toSet()

/**
 * Datum, wie das Modul es liefert (GEDCOM: "2022", "MAR 2022", "12 MAR 2022"), in der Sprache der App ("März 2022",
 * "12. März 2022"). Alles andere (ABT, BET ...) bleibt, wie es ist.
 */
fun dnaDatum(gedcom: String, sprache: java.util.Locale = java.util.Locale.getDefault()): String {
    val teile = gedcom.trim().uppercase().split(Regex("\\s+"))
    val monate = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    fun monat(t: String) = monate.indexOf(t).takeIf { it >= 0 }?.let { java.time.Month.of(it + 1).getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, sprache) }
    return when {
        teile.size == 2 && teile[1].all(Char::isDigit) -> monat(teile[0])?.let { "$it ${teile[1]}" }
        teile.size == 3 && teile[0].all(Char::isDigit) && teile[2].all(Char::isDigit) -> monat(teile[1])?.let { m ->
            runCatching {
                java.time.LocalDate.of(teile[2].toInt(), monate.indexOf(teile[1]) + 1, teile[0].toInt())
                    .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(sprache))
            }.getOrNull() ?: "${teile[0]} $m ${teile[2]}"
        }
        else -> null
    } ?: gedcom.trim()
}

/**
 * Ist die Person selbst autosomal getestet? Dann gilt sie beim Modul als vollstaendig abgedeckt (ApiCoverage: coverage 1,
 * keine Kandidaten) - die Antwort sagt das nicht eigens, darum leitet die App es aus den Eintraegen ab: ein autosomaler
 * Test oder ein Treffer, wie in der Regel des Moduls (Coverage.php: "An individual who has tested is covered completely").
 */
fun DnaPerson.selbstGetestet(): Boolean =
    matches.isNotEmpty() || entries.any { it.xref == xref && (it.kind == "autosomal" || (it.kind == "test" && it.value.equals("Autosomal", ignoreCase = true))) }
