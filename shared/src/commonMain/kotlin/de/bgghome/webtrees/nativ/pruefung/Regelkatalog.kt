package de.bgghome.webtrees.nativ.pruefung

/*
 * Der Regelkatalog der Plausibilitaetspruefung. Kennnummern wie im Pruefprogramm von db-blank
 * (0xx Chronologie, 1xx Altersgrenzen, 2xx Struktur, 3xx Namen, 4xx Quellen); 5xx Orte ist neu (Stufe 2, 27.09.2026),
 * 6xx Form (13.10.2026, fuer gedaudit): Aufbau der Datei, Verweise, Datumsform, Pflichtangaben - was nur die Datei
 * selbst zeigt (610-612, 616-618), bekommt der Motor als [DateiBefund]; im Export von webtrees bleiben diese Regeln leer.
 * 620-625 (15.10.2026): Kopf (Pflichtangaben, FILE, GEDCOM-L-Addendum, Datum), Verstoesse gegen GEDCOM 7 und Text hinter
 * Ereignissen - ebenfalls nur aus der Datei.
 * Die Fragen zu den Regeln und die Namen der Voreinstellungen stehen in den Ressourcen (regel_<id>, preset_<id>,
 * siehe PruefTexte); der Motor selbst braucht sie nicht und laeuft in Tests ohne Compose-Ressourcen.
 */

enum class Schwere { Fehler, Warnung }

/** Einheit des Grenzwerts. */
enum class Einheit { Jahre, Monate, Anzahl, Kilometer }

enum class RegelGruppe { Chronologie, Alter, Struktur, Namen, Quellen, Orte, Form }

class Regel(
    val id: String,
    val gruppe: RegelGruppe,
    val schwere: Schwere,
    /** Voreingestellter Grenzwert; null: Regel ohne Grenzwert. */
    val grenzwert: Double?,
    val einheit: Einheit?,
    /** Von Haus aus ausgeschaltet (meldet in vielen Baeumen sehr viel). */
    val standardAus: Boolean = false,
)

object Regelkatalog {
    private val C = RegelGruppe.Chronologie
    private val A = RegelGruppe.Alter
    private val S = RegelGruppe.Struktur
    private val NA = RegelGruppe.Namen
    private val Q = RegelGruppe.Quellen
    private val O = RegelGruppe.Orte
    private val FO = RegelGruppe.Form
    private val F = Schwere.Fehler
    private val W = Schwere.Warnung
    private val J = Einheit.Jahre
    private val M = Einheit.Monate
    private val N = Einheit.Anzahl
    private val KM = Einheit.Kilometer

    val alle: List<Regel> = listOf(
        Regel("010", C, F, null, null),
        Regel("011", C, F, null, null),
        Regel("012", C, F, null, null),
        Regel("013", C, F, null, null),
        Regel("014", C, F, null, null),
        Regel("015", C, F, 0.0, M),
        Regel("016", C, F, 9.5, M),
        Regel("017", C, W, 1.0, J),
        Regel("018", C, F, null, null),
        Regel("019", C, F, null, null),
        Regel("020", C, F, null, null),
        Regel("021", C, F, null, null),
        Regel("022", C, W, null, null),
        Regel("023", C, W, null, null),
        Regel("024", C, W, null, null),

        Regel("110", A, F, 110.0, J),
        Regel("111", A, F, 14.0, J),
        Regel("112", A, F, 55.0, J),
        Regel("113", A, F, 14.0, J),
        Regel("114", A, W, 75.0, J),
        Regel("115", A, F, 15.0, J),
        Regel("116", A, W, 90.0, J),
        Regel("117", A, W, 40.0, J),
        Regel("118", A, W, 8.0, M),
        Regel("119", A, W, 30.0, J),
        Regel("120", A, W, 16.0, N),
        Regel("121", A, W, 3.0, N),
        Regel("122", A, W, 2.0, M),
        Regel("123", A, F, 20.0, N),
        Regel("124", A, W, 12.0, J),
        Regel("125", A, F, null, null),
        Regel("126", A, W, 12.0, J),
        Regel("127", A, W, 110.0, J),

        Regel("210", S, F, null, null),
        Regel("211", S, W, null, null),
        Regel("212", S, W, null, null),
        Regel("213", S, W, null, null),
        Regel("214", S, F, null, null),
        Regel("215", S, F, null, null),
        Regel("216", S, F, null, null),
        Regel("217", S, F, null, null),
        Regel("219", S, W, null, null),
        Regel("220", S, F, null, null),
        Regel("221", S, F, null, null),
        Regel("222", S, F, null, null),
        Regel("223", S, W, null, null),
        Regel("224", S, W, null, null),
        Regel("225", S, F, null, null),
        Regel("226", S, W, null, null),
        Regel("228", S, W, 2.0, J),
        Regel("319", S, W, null, null),

        Regel("310", NA, W, null, null),
        Regel("311", NA, W, null, null),
        Regel("315", NA, W, null, null),
        Regel("330", NA, W, null, null),

        Regel("420", Q, W, null, null, standardAus = true),
        Regel("421", Q, W, null, null, standardAus = true),

        Regel("510", O, W, null, null),
        Regel("511", O, W, null, null, standardAus = true),
        Regel("512", O, W, 100.0, KM),
        Regel("513", O, W, null, null),

        Regel("610", FO, F, null, null),
        Regel("611", FO, F, null, null),
        Regel("612", FO, F, null, null),
        Regel("613", FO, W, null, null),
        Regel("614", FO, W, null, null),
        Regel("615", FO, W, null, null),
        Regel("616", FO, F, null, null),
        Regel("617", FO, W, null, null),
        Regel("618", FO, W, null, null, standardAus = true),
        Regel("619", FO, W, null, null, standardAus = true),
        Regel("620", FO, F, null, null),
        Regel("621", FO, W, null, null),
        Regel("622", FO, W, null, null),
        Regel("623", FO, W, null, null),
        Regel("624", FO, F, null, null),
        Regel("625", FO, W, null, null),
    )

    val nachId: Map<String, Regel> = alle.associateBy { it.id }

    /** Was ohne eigene Wahl ausgeschaltet ist. */
    val standardAus: Set<String> = alle.filter { it.standardAus }.map { it.id }.toSet()
}

/** Eine Voreinstellung: Grenzwerte, ausgeschaltete Regeln und abweichende Schwere; Schaetzen an oder aus. */
class Voreinstellung(
    val id: String,
    val grenzwerte: Map<String, Double>,
    val aus: Set<String>,
    val schwere: Map<String, Schwere> = emptyMap(),
    val schaetzen: Boolean = true,
) {
    companion object {
        val alle = listOf(
            Voreinstellung("standard", emptyMap(), Regelkatalog.standardAus),
            // eng: meldet frueh, auch Ungewoehnliches (Werte angelehnt an verbreitete Werkseinstellungen)
            Voreinstellung("streng",
                mapOf("110" to 100.0, "111" to 16.0, "112" to 50.0, "113" to 16.0, "114" to 70.0, "115" to 16.0, "116" to 80.0,
                    "117" to 25.0, "118" to 9.0, "119" to 25.0, "120" to 14.0, "124" to 10.0, "126" to 14.0, "127" to 100.0, "228" to 3.0, "512" to 50.0),
                setOf("511", "618"), mapOf("017" to Schwere.Fehler, "222" to Schwere.Fehler, "330" to Schwere.Fehler)),
            // weit: nur, was wirklich nicht sein kann
            Voreinstellung("grosszuegig",
                mapOf("110" to 120.0, "111" to 10.0, "112" to 58.0, "113" to 10.0, "114" to 90.0, "115" to 12.0, "116" to 110.0,
                    "117" to 60.0, "119" to 40.0, "120" to 20.0, "121" to 5.0, "124" to 20.0, "126" to 7.0, "127" to 120.0, "512" to 300.0),
                setOf("017", "022", "023", "211", "223", "224", "228", "310", "311", "420", "421", "511", "513", "618", "619")),
            // Quellen: wie Ausgewogen, dazu die Quellenregeln (Ereignisse und Personen ohne Beleg), die sonst aus sind
            Voreinstellung("quellen", emptyMap(), Regelkatalog.standardAus - setOf("420", "421")),
            // Kirchenbuch (18./19. Jh.): Taufe steht fuer die Geburt, Paten zaehlen, Reihenfolge der Kinder ist Darstellung
            Voreinstellung("kirchenbuch",
                mapOf("111" to 14.0, "112" to 52.0, "113" to 16.0, "114" to 80.0, "118" to 9.0, "122" to 1.0, "126" to 12.0),
                setOf("023", "223", "420", "421", "511", "618", "619"), mapOf("024" to Schwere.Fehler)),
        )
    }
}
