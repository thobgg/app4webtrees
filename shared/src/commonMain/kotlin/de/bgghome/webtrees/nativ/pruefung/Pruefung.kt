package de.bgghome.webtrees.nativ.pruefung

import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.data.hatPaten
import de.bgghome.webtrees.nativ.data.hauptHeirat
import de.bgghome.webtrees.nativ.api.TreeExport
import java.security.MessageDigest
import java.text.Normalizer

/*
 * Plausibilitaetspruefung (27.09.2026) ueber den ganzen Baum aus dem Export von api4webtrees (ab Stufe 17).
 * Stufe 1: die Regeln aus dem Pruefprogramm von db-blank (0xx, 1xx, 2xx, 319); 218 (Verweis ins Leere) fehlt, weil
 * der Export unsichtbare Personen weglaesst und webtrees Verweise selbst prueft.
 * Stufe 2: Taufe/Begraebnis als Schaetzung fuer fehlende Geburt/Tod, Paten und Zeugen, Lebende ohne Sterbeangabe,
 * unscharfe Dubletten, Namen, einfache Quellenregeln, Orte; je Treffer die betroffenen Fakten (zum Bearbeiten) und ein
 * Fingerabdruck der Daten (zum Abhaken: aendern sich die Daten, kommt der Treffer wieder).
 * Die Pruefung laeuft ohne Server-Anfrage; was der Benutzer nicht sehen darf, prueft sie nicht.
 */

/** Ein Fakt, den ein Treffer betrifft: Datensatz (Person oder Familie) und Fakt-ID aus dem Export. */
data class FaktRef(val record: String, val fakt: String)

/** Ein Treffer: Person und/oder Familie, kurze Begruendung, betroffene Fakten, Fingerabdruck (sprachunabhaengig). */
data class Treffer(
    val regel: String,
    val person: String?,
    val familie: String?,
    val text: String,
    val fakten: List<FaktRef> = emptyList(),
    val fingerabdruck: String = "",
) {
    /** Schluessel fuers Abhaken: Regel, Person, Familie und der Stand der Daten. */
    val schluessel: String get() = "$regel|${person.orEmpty()}|${familie.orEmpty()}|$fingerabdruck"
}

class PruefOptionen(
    /** Abweichende Grenzwerte je Regel. */
    val grenzwerte: Map<String, Double> = emptyMap(),
    /** Ausgeschaltete Regeln. */
    val aus: Set<String> = Regelkatalog.standardAus,
    /** Fehlende Geburt aus der Taufe, fehlenden Tod aus dem Begraebnis schaetzen. */
    val schaetzen: Boolean = true,
    /** Das laufende Jahr (Regeln 019, 127). */
    val jetzt: Int = java.time.Year.now().value,
    /** Bausteine der Begruendungen in der Sprache der Oberflaeche (PruefTexte.ausRessourcen); null = Deutsch. */
    val texte: ((String) -> String)? = null,
    /** Was nur die Datei zeigt (gedaudit, GedcomLeser): Regeln 610-612 und 616-618; null im Export von webtrees. */
    val datei: DateiBefund? = null,
)

/** Ein Verweis auf einen Datensatz, den es nicht gibt: an welchem Datensatz, mit welchem Tag, auf welche Kennung. */
class Verweis(val record: String, val tag: String, val ziel: String)

/** FAMC/FAMS/CHIL/HUSB/WIFE nur in einer Richtung: die Person, die Familie und das Tag, das fehlt. */
class Rueckverweis(val person: String, val familie: String, val tag: String)

/** Ein Tag, das nicht zum Standard gehoert: wie oft, und ein Datensatz, in dem es steht. */
class TagVorkommen(val anzahl: Int, val beispiel: String?)

/** Ein Fund zur Form der Datei (Regeln 620-625): Schluessel des Textbausteins, Einzelheit, Datensatz, Anzahl. */
class FormFund(val schluessel: String, val detail: String = "", val satz: String? = null, val anzahl: Int = 1)

/** Befund des Lesers ueber die Datei selbst (Form), fuer die Regeln 610-612 und 616-625. */
class DateiBefund(
    /** Zeilen, die kein GEDCOM sind: Zeilennummer und Text. */
    val uebersprungen: List<Pair<Int, String>> = emptyList(),
    val verweise: List<Verweis> = emptyList(),
    /** Kennungen, die mehr als einmal vergeben sind, mit Anzahl. */
    val doppelt: Map<String, Int> = emptyMap(),
    val rueckverweise: List<Rueckverweis> = emptyList(),
    val kopfFehlt: Boolean = false,
    /** CHAR im Kopf und der Zeichensatz, mit dem die Datei tatsaechlich gelesen wurde; null = keine Angabe. */
    val zeichensatzAngabe: String? = null,
    val zeichensatzErkannt: String = "",
    /** Tags ohne Unterstrich, die weder in 5.5.1 noch in 7 stehen. */
    val fremdeTags: Map<String, TagVorkommen> = emptyMap(),
    /** Geprueft als "5.5.1" oder "7.0"; dazu die Angabe im Kopf (GEDC VERS), null = keine. */
    val version: String = "5.5.1",
    val versionAngabe: String? = null,
    /** 620: Pflichtangaben, die im Kopf fehlen, und ein Einsender (SUBM), den es nicht gibt. */
    val kopf: List<FormFund> = emptyList(),
    /** 621: FILE im Kopf und der tatsaechliche Dateiname, wenn sie abweichen. */
    val dateiname: Pair<String, String>? = null,
    /** 622: Angabe zum GEDCOM-L-Addendum, wo sie von der Empfehlung abweicht. */
    val addendum: List<FormFund> = emptyList(),
    /** 623: DATE im Kopf, wenn es kein genaues Datum ist. */
    val kopfDatum: String? = null,
    /** 624: Verstoesse gegen GEDCOM 7 (nur wenn als 7.0 geprueft). */
    val sieben: List<FormFund> = emptyList(),
    /** 625: Text hinter einem Ereignis, wo nur Y erlaubt ist. */
    val ereignisText: List<FormFund> = emptyList(),
)

class PruefErgebnis(val treffer: Map<String, List<Treffer>>) {
    val anzahl: Int get() = treffer.values.sumOf { it.size }
}

// ── Modell ──

internal class PPerson(
    val xref: String,
    val name: String,
    val vn: String,
    val fn: String,
    /** m, w oder u */
    val geschlecht: Char,
    val privat: Boolean,
    val leer: Boolean,
    /** Wie webtrees die Person sieht (tot oder vermutlich lebend). */
    val tot: Boolean,
    val geb: PruefDatum?,
    val taufe: PruefDatum?,
    val tod: PruefDatum?,
    val begr: PruefDatum?,
    /** Die Fakten, aus denen geb/taufe/tod/begr stammen (bei Schaetzung Taufe bzw. Begraebnis). */
    val gebF: FactJson?,
    val taufeF: FactJson?,
    val todF: FactJson?,
    val begrF: FactJson?,
    /** Irgendein Sterbe- oder Begraebnisfakt, auch ohne Datum. */
    val hatTodesfakt: Boolean,
    val eltern: List<String>,
    val fakten: List<FactJson>,
) {
    /** Partnerfamilien, nach Heirat geordnet (ohne Datum zuletzt). */
    var ehen: List<String> = emptyList()
}

internal class PFamilie(
    val xref: String, val vater: String?, val mutter: String?, val heirat: PruefDatum?, val heiratF: FactJson?,
    val kinder: List<String>, val fakten: List<FactJson>,
)

internal class PModell(val p: Map<String, PPerson>, val f: Map<String, PFamilie>) {
    fun partner(f: PFamilie) = listOfNotNull(p[f.vater], p[f.mutter])
    fun kinder(f: PFamilie) = f.kinder.mapNotNull { p[it] }
    fun vater(f: PFamilie) = p[f.vater]
    fun mutter(f: PFamilie) = p[f.mutter]
}

internal fun datumAus(f: FactJson?): PruefDatum? {
    val d = f?.date ?: return null
    if (d.gedcom.isNotBlank()) return PruefDatum.aus(d.gedcom, d.text)
    // aeltere Server ohne GEDCOM-Form: nur das Jahr
    return if (d.year > 0) PruefDatum.aus(d.year.toString(), d.text) else null
}

/** Der erste Fakt der Tags (in dieser Reihenfolge), der ein lesbares Datum hat. */
private fun erstes(facts: List<FactJson>, vararg tags: String): Pair<PruefDatum, FactJson>? {
    for (t in tags) facts.filter { it.tag == t }.firstNotNullOfOrNull { f -> datumAus(f)?.let { it to f } }?.let { return it }
    return null
}

internal fun modellAus(b: TreeExport, schaetzen: Boolean): PModell {
    val personen = b.individuals.mapValues { (x, i) ->
        val p = i.person
        val geb = erstes(i.facts, "BIRT"); val taufe = erstes(i.facts, "CHR", "BAPM")
        val tod = erstes(i.facts, "DEAT"); val begr = erstes(i.facts, "BURI", "CREM")
        // Schaetzung: das Datum der Taufe bzw. des Begraebnisses, erkennbar an der Anzeige
        val gebS = geb ?: taufe?.takeIf { schaetzen }?.let { (d, f) -> d.mitText("~ ${d.text}") to f }
        val todS = tod ?: begr?.takeIf { schaetzen }?.let { (d, f) -> d.mitText("⚰ ${d.text}") to f }
        PPerson(
            xref = x, name = p.name, vn = p.given.trim(), fn = p.surname.trim(),
            geschlecht = when (p.sex) { "M" -> 'm'; "F" -> 'w'; else -> 'u' },
            privat = p.isPrivate,
            leer = !p.isPrivate && p.given.isBlank() && p.surname.isBlank() && i.facts.isEmpty(),
            tot = p.isDead,
            geb = gebS?.first, taufe = taufe?.first, tod = todS?.first, begr = begr?.first,
            gebF = gebS?.second, taufeF = taufe?.second, todF = todS?.second, begrF = begr?.second,
            hatTodesfakt = i.facts.any { it.tag in setOf("DEAT", "BURI", "CREM") },
            eltern = i.famc.filter { it in b.families },
            fakten = i.facts,
        )
    }
    val familien = b.families.mapValues { (x, f) ->
        // standesamtliche Heirat bevorzugt (bei zwei Heiraten), sonst die erste mit lesbarem Datum
        val marr = f.facts.hauptHeirat()?.let { h -> datumAus(h)?.let { it to h } } ?: erstes(f.facts, "MARR")
        PFamilie(x, f.husband?.takeIf { it in personen }, f.wife?.takeIf { it in personen },
            marr?.first ?: f.marriage?.date?.let { d -> PruefDatum.aus(d.gedcom.ifBlank { d.year.takeIf { it > 0 }?.toString().orEmpty() }, d.text) },
            marr?.second, f.children.filter { it in personen }, f.facts)
    }
    // Partnerfamilien aus Sicht der Familien (so sind sie vollstaendig), chronologisch
    val ehen = HashMap<String, MutableList<String>>()
    familien.values.forEach { f -> listOfNotNull(f.vater, f.mutter).distinct().forEach { ehen.getOrPut(it) { mutableListOf() } += f.xref } }
    personen.values.forEach { p ->
        p.ehen = ehen[p.xref].orEmpty().sortedWith(compareBy({ familien.getValue(it).heirat == null }, { familien.getValue(it).heirat?.min ?: 0L }))
    }
    return PModell(personen, familien)
}

// ── Pruefung ──

/**
 * Prueft den Baum. Die Begruendungen entstehen einmal deutsch (daraus der Fingerabdruck, damit das Abhaken nicht an
 * der Sprache haengt) und, falls noetig, noch einmal in der Sprache der Oberflaeche.
 */
fun pruefen(baum: TreeExport, o: PruefOptionen = PruefOptionen()): PruefErgebnis {
    val m = modellAus(baum, o.schaetzen)
    val de = Regeln(m, o.jetzt, BAUSTEINE_DE::getValue, o.datei)
    val en = o.texte?.let { Regeln(m, o.jetzt, it, o.datei) }
    val ergebnis = LinkedHashMap<String, List<Treffer>>()
    for (regel in Regelkatalog.alle) {
        if (regel.id in o.aus) continue
        val g = o.grenzwerte[regel.id] ?: regel.grenzwert
        val a = de.laufen(regel.id, g)
        val b = en?.laufen(regel.id, g)
        ergebnis[regel.id] = a.mapIndexed { i, t ->
            Treffer(regel.id, t.p, t.f, b?.getOrNull(i)?.text ?: t.text, t.fakten.distinct(), fingerabdruck(t.text))
        }
    }
    return PruefErgebnis(ergebnis)
}

/** Aeltere Form (Stufe 1). */
fun pruefen(
    baum: TreeExport, grenzwerte: Map<String, Double> = emptyMap(), aus: Set<String> = Regelkatalog.standardAus,
    jetzt: Int = java.time.Year.now().value, texte: ((String) -> String)? = null, schaetzen: Boolean = false,
): PruefErgebnis = pruefen(baum, PruefOptionen(grenzwerte, aus, schaetzen, jetzt, texte))

private fun fingerabdruck(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

/** Ein Treffer im Motor: Person, Familie, Text, Fakten. */
private class T(val p: String?, val f: String?, val text: String, vararg fakten: FaktRef?) {
    val fakten: List<FaktRef> = fakten.filterNotNull()
}

private fun ref(record: String?, f: FactJson?): FaktRef? = if (record == null || f == null || f.id.isEmpty()) null else FaktRef(record, f.id)

/** Fuer den Namensvergleich: klein, ohne Akzente, gaengige Schreibvarianten zusammengelegt (Catharina = Katharina). */
internal fun namensform(s: String): String {
    var t = s.lowercase().replace("ß", "ss").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
    t = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    t = t.replace("ae", "a").replace("oe", "o").replace("ue", "u")
        .replace("ph", "f").replace("th", "t").replace("dt", "t").replace("tz", "z").replace("ck", "k")
        .replace('c', 'k').replace('y', 'i').replace(Regex("[^a-z ]"), "")
    return t.replace(Regex("(.)\\1+"), "$1").trim()
}

/** Editierabstand, bei dem zwei vertauschte Nachbarbuchstaben als ein Fehler zaehlen (Hermannsbrug = Hermannsburg). */
internal fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val k = if (a[i - 1] == b[j - 1]) 0 else 1
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + k)
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
    }
    return d[a.length][b.length]
}

internal fun entfernungKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}

private val WEIBLICH = setOf(
    "Walburga", "Walpurga", "Maria", "Marie", "Anna", "Anne", "Catharina", "Katharina", "Catharine", "Margaretha", "Margarethe", "Margarete",
    "Barbara", "Elisabetha", "Elisabeth", "Magdalena", "Agnes", "Ursula", "Christina", "Christine", "Rosina", "Regina", "Dorothea",
    "Sophia", "Sophie", "Johanna", "Eva", "Susanna", "Agatha", "Apollonia", "Veronica", "Sibylla", "Salome", "Judith", "Justina",
    "Friederike", "Wilhelmine", "Louise", "Luise", "Caroline", "Karoline", "Charlotte", "Jacobina", "Philippina", "Christiane",
    "Helena", "Helene", "Ottilia", "Sabina", "Sara", "Rebecca", "Esther", "Hanna", "Lydia", "Martha", "Emilie", "Pauline", "Mathilde",
    "Auguste", "Ernestine", "Henriette", "Theresia", "Therese", "Frieda", "Emma", "Bertha", "Berta", "Gertrud", "Hedwig", "Ida", "Klara",
    "Clara", "Minna", "Lina", "Elise", "Else", "Erna", "Ilse", "Irmgard", "Ingrid", "Ursel", "Renate", "Ruth", "Ursula", "Mary", "Elizabeth",
    "Sarah", "Margaret", "Jane", "Ellen", "Alice", "Julia", "Amanda", "Lucy", "Nancy", "Emily", "Susan",
)
private val MAENNLICH = setOf(
    "Johann", "Johannes", "Hans", "Georg", "Jacob", "Jakob", "Michael", "Conrad", "Konrad", "Caspar", "Kaspar", "Martin", "Christoph",
    "Friedrich", "Ludwig", "Wilhelm", "Christian", "Gottlieb", "Andreas", "Philipp", "Heinrich", "Hermann", "Karl", "Carl", "Franz",
    "Joseph", "Josef", "Anton", "Peter", "Paul", "Matthias", "Mathias", "Adam", "Ernst", "August", "Gustav", "Otto", "Albert", "Richard",
    "Robert", "Walter", "Werner", "Kurt", "Fritz", "Heinz", "Klaus", "Jürgen", "Dieter", "Thomas", "Stefan", "Stephan", "Daniel",
    "David", "Samuel", "Simon", "Jonas", "Diedrich", "Dietrich", "Bernhard", "Gerhard", "Hinrich", "Jürgen", "Lorenz", "Valentin",
    "John", "William", "James", "Charles", "George", "Henry", "Edward", "Frederick", "Robert", "Joseph",
)
private val PLATZHALTER = Regex("(?i)^(unbekannt|unknown|n\\.?\\s?n\\.?|\\?+|keine angaben?|ohne namen|namenlos|xx+)$")
private val PATEN = Regex("(?i)^\\s*(paten|pate|patin|taufpaten|taufzeugen|zeugen|trauzeugen|godparents?|sponsors?|witnesses?)\\s*:\\s*(.+)$")

/**
 * Bausteine der Begruendungen auf Deutsch - im Code, weil daraus der Fingerabdruck entsteht und der Motor in Tests
 * ohne Ressourcen laeuft. Dieselben Schluessel stehen als pruef_<schluessel> in jeder Sprache in strings.xml.
 */
internal val BAUSTEINE_DE: Map<String, String> = mapOf(
    "jahre_kurz" to "%.0f J",
    "kind" to "Kind",
    "kind_klein" to "Kind",
    "mutter" to "Mutter",
    "mutter_klein" to "Mutter",
    "vater" to "Vater",
    "vater_klein" to "Vater",
    "eltern" to "Eltern",
    "steht_nach" to "steht nach",
    "spanne" to "Spanne",
    "j" to "J",
    "kinder" to "Kinder",
    "ehen" to "Ehen",
    "kinder_in" to "Kinder in",
    "ehe_n" to "Ehe(n)",
    "j_nach" to "J nach",
    "keine_sterbeangabe" to "keine Sterbeangabe",
    "lebend" to "webtrees hält die Person für lebend",
    "ohne_angaben" to "ohne Angaben",
    "familien" to "Familien",
    "kind_und_elternteil" to "Kind und Elternteil zugleich",
    "frau_als_vater" to "Frau als Vater",
    "mann_als_mutter" to "Mann als Mutter",
    "leere_familie" to "leere Familie",
    "eigener_vorfahr" to "eigener Vorfahr",
    "wie_familie" to "wie Familie",
    "nachname" to "Nachname",
    "maennlich" to "männlich",
    "weiblich" to "weiblich",
    "taufe" to "Taufe",
    "heirat" to "Heirat",
    "zeile" to "Zeile",
    "verweis_auf" to "Verweis auf",
    "kennung_doppelt" to "Kennung doppelt vergeben",
    "nicht_lesbar" to "nicht lesbar",
    "ohne_namen" to "ohne Namen",
    "geburten" to "Geburten",
    "sterbefaelle" to "Sterbefälle",
    "nur_hinweg" to "nur in einer Richtung",
    "kopf_fehlt" to "Kopfsatz (HEAD) fehlt",
    "kopf_ohne_sour" to "Programm (SOUR) fehlt",
    "kopf_ohne_subm" to "Einsender (SUBM) fehlt",
    "kopf_subm_leer" to "Einsender (SUBM) zeigt auf keinen Datensatz",
    "kopf_ohne_gedc" to "GEDCOM-Version (GEDC VERS) fehlt",
    "kopf_ohne_form" to "GEDC FORM fehlt",
    "kopf_ohne_char" to "Zeichensatz (CHAR) fehlt",
    "addendum_name" to "Name nicht wie empfohlen (GEDCOM-L Addendum)",
    "addendum_vers" to "Version nicht wie empfohlen, z. B. R2 (2020-12-01)",
    "addendum_www" to "Adresse nicht wie empfohlen (https://genealogy.net/GEDCOM)",
    "addendum_sieben" to "Das GEDCOM-L-Addendum gilt für GEDCOM 5.5.1, nicht für 7",
    "g7_conc" to "CONC gibt es in GEDCOM 7 nicht mehr",
    "g7_kopf_tag" to "Im Kopf von GEDCOM 7 nicht erlaubt",
    "g7_alt_tag" to "Gibt es nur in GEDCOM 5.5.1",
    "g7_medientyp" to "Kein Medientyp (z. B. image/jpeg)",
    "g7_aufzaehlung" to "Wert nicht aus der Liste von GEDCOM 7",
    "g7_exid_type" to "Externe Kennung ohne TYPE",
    "g7_sprache" to "Kein Sprachcode (z. B. de)",
    "ereignis_text" to "Text hinter dem Ereignis (erlaubt ist nur Y)",
    "datei" to "Datei",
    "geschlecht_unbekannt" to "Geschlecht unbekannt",
)

private class Regeln(val m: PModell, val jetzt: Int, val texte: (String) -> String, val datei: DateiBefund? = null) {
    fun w(schluessel: String) = texte(schluessel)
    fun d(x: PruefDatum?) = x?.text ?: "?"
    fun n(p: PPerson) = p.name.ifBlank { "?" }
    fun jahreText(a: Double) = w("jahre_kurz").format(java.util.Locale.ROOT, a)

    /** Personen mit Daten; private Platzhalter haben keine und bleiben aussen vor. */
    val personen = m.p.values.filter { !it.privat }
    val familien = m.f.values

    fun geb(p: PPerson) = ref(p.xref, p.gebF)
    fun tod(p: PPerson) = ref(p.xref, p.todF)
    fun heirat(f: PFamilie) = ref(f.xref, f.heiratF)
    fun nameF(p: PPerson) = ref(p.xref, p.fakten.firstOrNull { it.tag == "NAME" })

    fun elterKind(vater: Boolean) = sequence {
        for (f in familien) {
            val e = (if (vater) m.vater(f) else m.mutter(f)) ?: continue
            for (k in m.kinder(f)) yield(Triple(k, e, f))
        }
    }

    fun laufen(id: String, g: Double?): List<T> = when (id) {
        // ── 0xx Chronologie ──
        "010" -> personen.filter { vor(it.tod, it.geb) == true }.map { T(it.xref, null, "* ${d(it.geb)}  † ${d(it.tod)}", tod(it), geb(it)) }
        "011" -> familien.flatMap { f -> m.partner(f).filter { vor(f.heirat, it.geb) == true }.map { T(it.xref, f.xref, "⚭ ${d(f.heirat)}  * ${d(it.geb)}", heirat(f), geb(it)) } }
        "012" -> familien.flatMap { f -> m.partner(f).filter { vor(it.tod, f.heirat) == true }.map { T(it.xref, f.xref, "⚭ ${d(f.heirat)}  † ${d(it.tod)}", heirat(f), tod(it)) } }
        "013" -> elterKind(false).filter { (k, e) -> vor(k.geb, e.geb) == true }.map { (k, e, f) -> T(k.xref, f.xref, w("kind") + " * ${d(k.geb)}  " + w("mutter") + " * ${d(e.geb)}", geb(k), geb(e)) }.toList()
        "014" -> elterKind(true).filter { (k, e) -> vor(k.geb, e.geb) == true }.map { (k, e, f) -> T(k.xref, f.xref, w("kind") + " * ${d(k.geb)}  " + w("vater") + " * ${d(e.geb)}", geb(k), geb(e)) }.toList()
        "015" -> kindNachTod(false, g ?: 0.0)
        "016" -> kindNachTod(true, g ?: 9.5)
        "017" -> familien.filter { it.vater != null && it.mutter != null && it.heirat != null }.flatMap { f ->
            m.kinder(f).mapNotNull { k -> jahre(k.geb, f.heirat)?.takeIf { it > (g ?: 1.0) }?.let { T(k.xref, f.xref, "* ${d(k.geb)}  ⚭ ${w("eltern")} ${d(f.heirat)}", geb(k), heirat(f)) } }
        }
        "018" -> personen.flatMap { p ->
            p.fakten.mapNotNull { fa -> datumAus(fa)?.takeIf { it.unmoeglich }?.let { T(p.xref, null, "${fa.label.ifBlank { fa.tag }} ${it.text}", ref(p.xref, fa)) } }
        } + familien.flatMap { f -> f.fakten.mapNotNull { fa -> datumAus(fa)?.takeIf { it.unmoeglich }?.let { T(null, f.xref, "${fa.label.ifBlank { fa.tag }} ${it.text}", ref(f.xref, fa)) } } }
        "019" -> personen.flatMap { p ->
            p.fakten.mapNotNull { fa -> datumAus(fa)?.takeIf { (it.jahr ?: 0) > jetzt || it.min > PruefDatum.aus("31 DEC $jetzt")!!.max }?.let { T(p.xref, null, "${fa.label.ifBlank { fa.tag }} ${it.text}", ref(p.xref, fa)) } }
        } + familien.flatMap { f -> f.fakten.mapNotNull { fa -> datumAus(fa)?.takeIf { (it.jahr ?: 0) > jetzt }?.let { T(null, f.xref, "${fa.label.ifBlank { fa.tag }} ${it.text}", ref(f.xref, fa)) } } }
        "020" -> personen.filter { vor(it.taufe, it.geb) == true }.map { T(it.xref, null, "* ${d(it.geb)}  ~ ${d(it.taufe)}", ref(it.xref, it.taufeF), geb(it)) }
        "021" -> personen.filter { vor(it.begr, it.tod) == true }.map { T(it.xref, null, "† ${d(it.tod)}  ⚰ ${d(it.begr)}", ref(it.xref, it.begrF), tod(it)) }
        "022" -> familien.flatMap { f -> m.partner(f).filter { it.geb?.gleich(f.heirat) == true }.map { T(it.xref, f.xref, "* = ⚭ ${d(it.geb)}", geb(it), heirat(f)) } }
        "023" -> familien.mapNotNull { f ->
            val ks = m.kinder(f).filter { it.geb?.voll == true }
            ks.zipWithNext().firstOrNull { (a, b) -> a.geb!!.tage > b.geb!!.tage }?.let { (a, b) ->
                T(b.xref, f.xref, "* ${d(b.geb)}, " + w("steht_nach") + " ${n(a)} * ${d(a.geb)}", geb(b))
            }
        }
        "024" -> paten().mapNotNull { (ev, pate) ->
            // als Kind Gestorbene sind fast immer Namensvettern (Name eines verstorbenen Kindes weitergegeben)
            if ((jahre(pate.geb, pate.tod) ?: 99.0) < 14) return@mapNotNull null
            when {
                vor(pate.tod, ev.datum) == true -> T(ev.person, ev.familie, "${ev.art} ${d(ev.datum)}: ${n(pate)} † ${d(pate.tod)}", ref(ev.record, ev.fakt), tod(pate))
                vor(ev.datum, pate.geb) == true -> T(ev.person, ev.familie, "${ev.art} ${d(ev.datum)}: ${n(pate)} * ${d(pate.geb)}", ref(ev.record, ev.fakt), geb(pate))
                else -> null
            }
        }

        // ── 1xx Altersgrenzen ──
        "110" -> personen.mapNotNull { p -> jahre(p.geb, p.tod)?.takeIf { it > (g ?: 110.0) }?.let { T(p.xref, null, "* ${d(p.geb)}  † ${d(p.tod)} = ${jahreText(it)}", geb(p), tod(p)) } }
        "111" -> elterAlter(false, unter = g ?: 14.0)
        "112" -> elterAlter(false, ueber = g ?: 55.0)
        "113" -> elterAlter(true, unter = g ?: 14.0)
        "114" -> elterAlter(true, ueber = g ?: 75.0)
        "115" -> heiratAlter(unter = g ?: 15.0)
        "116" -> heiratAlter(ueber = g ?: 90.0)
        "117" -> familien.mapNotNull { f ->
            val v = m.vater(f) ?: return@mapNotNull null; val mu = m.mutter(f) ?: return@mapNotNull null
            jahre(v.geb, mu.geb)?.takeIf { kotlin.math.abs(it) > (g ?: 40.0) }?.let { T(v.xref, f.xref, "* ${d(v.geb)}, ${n(mu)} * ${d(mu.geb)}", geb(v), geb(mu)) }
        }
        "118" -> familien.flatMap { f ->
            val ks = m.kinder(f).filter { it.geb?.voll == true }
            ks.flatMapIndexed { i, a ->
                ks.drop(i + 1).mapNotNull { b ->
                    val x = kotlin.math.abs(monate(a.geb, b.geb)!!)
                    if (x >= 0.1 && x <= (g ?: 8.0)) T(a.xref, f.xref, "* ${d(a.geb)}, ${n(b)} * ${d(b.geb)}", geb(a), geb(b)) else null
                }
            }
        }
        "119" -> familien.mapNotNull { f ->
            val js = m.kinder(f).mapNotNull { it.geb?.jahr }
            if (js.isNotEmpty() && js.max() - js.min() > (g ?: 30.0)) T(null, f.xref, w("spanne") + " ${js.max() - js.min()} " + w("j")) else null
        }
        "120" -> familien.filter { it.kinder.size > (g ?: 16.0) }.map { T(null, it.xref, "${it.kinder.size} " + w("kinder")) }
        "121" -> personen.filter { it.ehen.size > (g ?: 3.0) }.map { T(it.xref, null, "${it.ehen.size} " + w("ehen")) }
        "122" -> personen.flatMap { p ->
            p.ehen.zipWithNext().mapNotNull { (f1, f2) ->
                val alt = m.partner(m.f.getValue(f1)).firstOrNull { it.xref != p.xref } ?: return@mapNotNull null
                val neu = m.f.getValue(f2)
                if (alt.tod?.voll != true || neu.heirat?.voll != true) return@mapNotNull null
                val x = monate(alt.tod, neu.heirat)!!
                if (x >= 0 && x <= (g ?: 2.0)) T(p.xref, f2, "† ${n(alt)} ${d(alt.tod)}  ⚭ ${d(neu.heirat)}", tod(alt), heirat(neu)) else null
            }
        }
        "123" -> personen.filter { it.geschlecht != 'm' }.mapNotNull { p ->
            val kids = p.ehen.map { m.f.getValue(it) }.filter { it.mutter == p.xref }.flatMap { it.kinder }.toSet()
            if (kids.size > (g ?: 20.0)) T(p.xref, null, "${kids.size} " + w("kinder_in") + " ${p.ehen.size} " + w("ehe_n")) else null
        }
        "124" -> familien.flatMap { f ->
            m.kinder(f).filter { it.geb?.jahr != null }.sortedBy { it.geb!!.jahr }.zipWithNext().mapNotNull { (a, b) ->
                val x = b.geb!!.jahr!! - a.geb!!.jahr!!
                if (x > (g ?: 12.0)) T(b.xref, f.xref, "* ${d(b.geb)}, $x " + w("j_nach") + " ${n(a)} * ${d(a.geb)}", geb(b), geb(a)) else null
            }
        }
        "125" -> personen.filter { it.tod?.voll == true }.flatMap { p ->
            p.ehen.map { m.f.getValue(it) }.filter { it.heirat?.voll == true && monate(p.tod, it.heirat)!! > 0 }.map { T(p.xref, it.xref, "† ${d(p.tod)}  ⚭ ${d(it.heirat)}", tod(p), heirat(it)) }
        }
        "126" -> paten().mapNotNull { (ev, pate) ->
            val a = jahre(pate.geb, ev.datum) ?: return@mapNotNull null
            // unter 5 Jahren ist es kein Pate, sondern ein gleichnamiges Kind - keine Meldung
            if (a >= 5 && a < (g ?: 12.0)) T(ev.person, ev.familie, "${ev.art} ${d(ev.datum)}: ${n(pate)} * ${d(pate.geb)} = ${jahreText(a)}", ref(ev.record, ev.fakt), geb(pate)) else null
        }
        "127" -> personen.filter { !it.hatTodesfakt && it.geb?.jahr != null && jetzt - it.geb.jahr > (g ?: 110.0) }.map { p ->
            T(p.xref, null, "* ${d(p.geb)}, " + w("keine_sterbeangabe") + if (!p.tot) " – " + w("lebend") else "", geb(p))
        }

        // ── 2xx Struktur ──
        "210" -> personen.filter { it.leer && it.ehen.isEmpty() && it.eltern.isEmpty() }.map { T(it.xref, null, it.xref) }
        "211" -> personen.filter { it.ehen.isEmpty() && it.eltern.isEmpty() }.map { T(it.xref, null, n(it)) }
        "212" -> familien.filter { it.kinder.isEmpty() }.flatMap { f -> m.partner(f).filter { it.leer }.map { T(it.xref, f.xref, w("ohne_angaben")) } }
        "213" -> personen.filter { it.eltern.size > 1 }.map { T(it.xref, it.eltern.first(), w("familien") + " " + it.eltern.joinToString(", ")) }
        "214" -> personen.flatMap { p -> p.eltern.filter { it in p.ehen }.map { T(p.xref, it, w("kind_und_elternteil")) } }
        "215" -> familien.mapNotNull { f ->
            val v = m.vater(f); val mu = m.mutter(f)
            if (v != null && mu != null && v.geschlecht != 'u' && v.geschlecht == mu.geschlecht) T(v.xref, f.xref, "${n(v)}, ${n(mu)}") else null
        }
        "216" -> familien.flatMap { f ->
            listOfNotNull(
                m.vater(f)?.takeIf { it.geschlecht == 'w' }?.let { T(it.xref, f.xref, w("frau_als_vater")) },
                m.mutter(f)?.takeIf { it.geschlecht == 'm' }?.let { T(it.xref, f.xref, w("mann_als_mutter")) },
            )
        }
        "217" -> familien.filter { it.vater == null && it.mutter == null && it.kinder.isEmpty() }.map { T(null, it.xref, w("leere_familie")) }
        // Gleicher Name und ein gleiches volles Ereignisdatum (Geburt, Taufe, Tod oder Begraebnis) - die Fakten beider
        // Personen haengen am Treffer, daraus kommt das Paar fuers Zusammenfuehren
        "219" -> personen.filter { it.fn.isNotEmpty() && it.vn.isNotEmpty() && (it.geb?.voll == true || it.taufe?.voll == true || it.tod?.voll == true || it.begr?.voll == true) }
            .groupBy { it.vn to it.fn }.values.flatMap { ps ->
                ps.flatMapIndexed { i, a -> ps.drop(i + 1).mapNotNull { b ->
                    val gleich = listOf("*" to (a.geb to b.geb), "~" to (a.taufe to b.taufe), "+" to (a.tod to b.tod), "⚰" to (a.begr to b.begr))
                        .firstOrNull { (_, paar) -> paar.first?.voll == true && paar.second?.voll == true && paar.first!!.tage == paar.second!!.tage } ?: return@mapNotNull null
                    val (sym, paar) = gleich
                    val fa = when (sym) { "*" -> a.gebF; "~" -> a.taufeF; "+" -> a.todF; else -> a.begrF }
                    val fb = when (sym) { "*" -> b.gebF; "~" -> b.taufeF; "+" -> b.todF; else -> b.begrF }
                    T(a.xref, null, "${n(a)} $sym ${d(paar.first)} = ${b.xref}", ref(a.xref, fa) ?: geb(a), ref(b.xref, fb) ?: geb(b))
                } }
            }
        "220" -> familien.mapNotNull { f ->
            val v = m.vater(f) ?: return@mapNotNull null; val mu = m.mutter(f) ?: return@mapNotNull null
            if (mu.xref in geschwister(v)) T(v.xref, f.xref, "${n(v)}, ${n(mu)}") else null
        }
        "221" -> familien.flatMap { f ->
            val v = m.vater(f); val mu = m.mutter(f)
            if (v == null || mu == null) return@flatMap emptyList()
            buildList {
                if (mu.eltern.any { v.xref in listOf(m.f.getValue(it).vater, m.f.getValue(it).mutter) }) add(T(v.xref, f.xref, "${n(v)} → ${n(mu)}"))
                if (v.eltern.any { mu.xref in listOf(m.f.getValue(it).vater, m.f.getValue(it).mutter) }) add(T(mu.xref, f.xref, "${n(mu)} → ${n(v)}"))
            }
        }
        "222" -> personen.flatMap { p ->
            p.ehen.zipWithNext().mapNotNull { (f1, f2) ->
                val alt = m.partner(m.f.getValue(f1)).firstOrNull { it.xref != p.xref } ?: return@mapNotNull null
                val neu = m.f.getValue(f2)
                if (vor(neu.heirat, alt.tod) == true) T(p.xref, f2, "⚭ ${d(neu.heirat)}  † ${n(alt)} ${d(alt.tod)}", heirat(neu), tod(alt)) else null
            }
        }
        "223" -> familien.mapNotNull { f ->
            val v = m.vater(f); val mu = m.mutter(f)
            if (v != null && mu != null && !v.privat && !mu.privat && v.fn.isNotEmpty() && v.fn == mu.fn) T(v.xref, f.xref, v.fn) else null
        }
        "224" -> familien.flatMap { f ->
            val ks = m.kinder(f).filter { !it.privat }
            ks.flatMapIndexed { i, a ->
                ks.drop(i + 1).mapNotNull { b ->
                    if (a.vn.isEmpty() || a.vn != b.vn || a.geb == null || b.geb == null) return@mapNotNull null
                    val (frueh, spaet) = if (vor(b.geb, a.geb) == true) b to a else a to b
                    if (frueh.tod == null || vor(spaet.geb, frueh.tod) == true)
                        T(spaet.xref, f.xref, "${a.vn}: * ${d(frueh.geb)}" + (frueh.tod?.let { " († ${it.text})" } ?: "") + ", * ${d(spaet.geb)}", geb(spaet), tod(frueh))
                    else null
                }
            }
        }
        "225" -> eigeneVorfahren().map { T(it, null, w("eigener_vorfahr")) }
        "226" -> {
            val paare = HashMap<Pair<String, String>, PFamilie>()
            familien.mapNotNull { f ->
                if (f.vater == null || f.mutter == null) return@mapNotNull null
                val a = paare.putIfAbsent(f.vater to f.mutter, f) ?: return@mapNotNull null
                if (a.heirat == null || f.heirat == null || a.heirat.gleich(f.heirat) || a.heirat.text == f.heirat.text)
                    T(null, f.xref, w("wie_familie") + " ${a.xref}") else null
            }
        }
        "228" -> dubletten(g ?: 2.0)
        "319" -> familien.flatMap { f -> m.partner(f).filter { it.geschlecht == 'u' && !it.privat }.map { T(it.xref, f.xref, n(it)) } }

        // ── 3xx Namen ──
        "310" -> personen.filter { it.fn in WEIBLICH }.map { T(it.xref, null, w("nachname") + " „${it.fn}“", nameF(it)) }
        "311" -> personen.filter { p -> p.vn.split(' ').any { PLATZHALTER.matches(it) } || PLATZHALTER.matches(p.fn) }.map { T(it.xref, null, "${it.vn} / ${it.fn}", nameF(it)) }
        "315" -> personen.filter { it.vn.any(Char::isDigit) || it.fn.any(Char::isDigit) }.map { T(it.xref, null, "${it.vn} / ${it.fn}", nameF(it)) }
        "330" -> personen.mapNotNull { p ->
            val ruf = p.vn.split(' ', '-').firstOrNull().orEmpty()
            when {
                p.geschlecht == 'm' && ruf in WEIBLICH -> T(p.xref, null, "„$ruf“, " + w("maennlich"), nameF(p))
                p.geschlecht == 'w' && ruf in MAENNLICH -> T(p.xref, null, "„$ruf“, " + w("weiblich"), nameF(p))
                else -> null
            }
        }

        // ── 4xx Quellen ──
        "420" -> {
            val ereignisse = setOf("BIRT", "CHR", "BAPM", "DEAT", "BURI", "CREM")
            personen.mapNotNull { p ->
                val ohne = p.fakten.filter { it.tag in ereignisse && it.sources.isEmpty() && (it.date != null || it.place != null) }
                if (ohne.isEmpty()) null else T(p.xref, null, ohne.joinToString(", ") { it.label.ifBlank { it.tag } }, *ohne.map { ref(p.xref, it) }.toTypedArray())
            } + familien.mapNotNull { f ->
                val ohne = f.fakten.filter { it.tag == "MARR" && it.sources.isEmpty() && (it.date != null || it.place != null) }
                if (ohne.isEmpty()) null else T(f.vater ?: f.mutter, f.xref, ohne.joinToString(", ") { it.label.ifBlank { it.tag } }, *ohne.map { ref(f.xref, it) }.toTypedArray())
            }
        }
        "421" -> personen.filter { p -> p.fakten.isNotEmpty() && p.fakten.none { it.sources.isNotEmpty() } }.map { T(it.xref, null, n(it)) }

        // ── 5xx Orte ──
        "510" -> ortsvarianten()
        "511" -> orte().filter { it.value.lat == null }.map { (name, o) -> T(o.person, o.familie, "$name (${o.anzahl}×)", o.beispiel) }
        "512" -> personen.flatMap { p ->
            val mitOrt = p.fakten.mapNotNull { fa ->
                val dd = datumAus(fa)?.takeIf { it.voll } ?: return@mapNotNull null
                val ort = fa.place ?: return@mapNotNull null
                val lat = ort.lat ?: return@mapNotNull null; val lng = ort.lng ?: return@mapNotNull null
                Triple(fa, dd, lat to lng)
            }
            mitOrt.flatMapIndexed { i, (fa, da, pa) ->
                mitOrt.drop(i + 1).mapNotNull { (fb, db, pb) ->
                    if (kotlin.math.abs(da.min - db.min) > 1) return@mapNotNull null
                    val km = entfernungKm(pa.first, pa.second, pb.first, pb.second)
                    if (km > (g ?: 100.0)) T(p.xref, null, "${fa.label} ${fa.place?.short ?: ""} ${d(da)} · ${fb.label} ${fb.place?.short ?: ""} ${d(db)}: %.0f km".format(java.util.Locale.ROOT, km), ref(p.xref, fa), ref(p.xref, fb)) else null
                }
            }
        }
        "513" -> orte().filter { (name, _) -> name.split(',').let { t -> t.size > 1 && t.any { it.isBlank() } } }.map { (name, o) -> T(o.person, o.familie, "„$name“ (${o.anzahl}×)", o.beispiel) }

        // ── 6xx Form: der Aufbau der Datei (nur gedaudit: 610-612, 616-618) und Pflichtangaben (auch wtWin) ──
        "610" -> datei?.uebersprungen.orEmpty().map { (nr, z) -> T(null, null, w("zeile") + " $nr: " + z.take(80)) }
        "611" -> datei?.verweise.orEmpty().map { v -> T(v.record.takeIf { it in m.p }, v.record.takeIf { it in m.f }, "${v.tag} → @${v.ziel}@") }
        "612" -> datei?.doppelt.orEmpty().map { (x, n) -> T(x.takeIf { it in m.p }, x.takeIf { it in m.f }, w("kennung_doppelt") + ": @$x@ ($n×)") }
        "613" -> personen.flatMap { p -> p.fakten.mapNotNull { fa -> unlesbar(fa)?.let { T(p.xref, null, "${fa.label.ifBlank { fa.tag }}: „$it“ " + w("nicht_lesbar"), ref(p.xref, fa)) } } } +
            familien.flatMap { f -> f.fakten.mapNotNull { fa -> unlesbar(fa)?.let { T(f.vater ?: f.mutter, f.xref, "${fa.label.ifBlank { fa.tag }}: „$it“ " + w("nicht_lesbar"), ref(f.xref, fa)) } } }
        "614" -> personen.filter { it.vn.isBlank() && it.fn.isBlank() }.map { T(it.xref, null, it.xref + " " + w("ohne_namen"), nameF(it)) }
        "615" -> personen.mapNotNull { p ->
            val g = p.fakten.count { it.tag == "BIRT" }; val d = p.fakten.count { it.tag == "DEAT" }
            if (g <= 1 && d <= 1) null else T(p.xref, null, listOfNotNull(g.takeIf { it > 1 }?.let { "$it " + w("geburten") }, d.takeIf { it > 1 }?.let { "$it " + w("sterbefaelle") }).joinToString(", "), geb(p), tod(p))
        }
        "616" -> datei?.rueckverweise.orEmpty().map { r -> T(r.person.takeIf { it in m.p }, r.familie, "${r.tag} " + w("nur_hinweg")) }
        "617" -> datei?.let { d ->
            listOfNotNull(
                if (d.kopfFehlt) T(null, null, w("kopf_fehlt")) else null,
                d.zeichensatzAngabe?.takeIf { it.isNotBlank() && !zeichensatzPasst(it, d.zeichensatzErkannt) }?.let { T(null, null, "CHAR $it, " + w("datei") + " ${d.zeichensatzErkannt}") },
            )
        }.orEmpty()
        "618" -> datei?.fremdeTags.orEmpty().entries.sortedByDescending { it.value.anzahl }.map { (tag, v) -> T(v.beispiel?.takeIf { it in m.p }, v.beispiel?.takeIf { it in m.f }, "$tag (${v.anzahl}×)") }
        "619" -> personen.filter { it.geschlecht == 'u' }.map { T(it.xref, null, w("geschlecht_unbekannt")) }
        "620" -> datei?.kopf.orEmpty().map { formTreffer(it) }
        "621" -> datei?.dateiname?.let { (angabe, ist) -> listOf(T(null, null, "FILE $angabe, " + w("datei") + " $ist")) }.orEmpty()
        "622" -> datei?.addendum.orEmpty().map { formTreffer(it) }
        "623" -> datei?.kopfDatum?.let { listOf(T(null, null, "DATE „$it“ " + w("nicht_lesbar"))) }.orEmpty()
        "624" -> datei?.sieben.orEmpty().sortedByDescending { it.anzahl }.map { formTreffer(it) }
        "625" -> datei?.ereignisText.orEmpty().map { formTreffer(it) }
        else -> emptyList()
    }

    /** Ein Fund zur Form als Treffer: Baustein, Einzelheit, Anzahl; an Person oder Familie, wenn der Datensatz eine ist. */
    private fun formTreffer(f: FormFund): T {
        val text = w(f.schluessel) + (if (f.detail.isNotEmpty()) ": ${f.detail}" else "") + (if (f.anzahl > 1) " (${f.anzahl}×)" else "")
        return T(f.satz?.takeIf { it in m.p }, f.satz?.takeIf { it in m.f }, text)
    }

    /** Die GEDCOM-Form eines Datums, die der Motor nicht lesen kann (kein Datum, Freitext in Klammern ist erlaubt). */
    private fun unlesbar(fa: FactJson): String? {
        val g = fa.date?.gedcom?.trim().orEmpty()
        if (g.isEmpty() || g.startsWith("(") || g.uppercase().startsWith("INT ")) return null
        return if (PruefDatum.aus(g) == null) g else null
    }

    private fun zeichensatzPasst(angabe: String, erkannt: String): Boolean {
        val a = angabe.uppercase().replace("-", ""); val e = erkannt.uppercase().replace("-", "")
        return a == e || (a == "UNICODE" && e == "UTF16") || (a == "ASCII" && e == "UTF8") || (a in setOf("CP1252", "WINDOWS1252", "WIN1252") && e == "ANSI")
    }

    fun kindNachTod(vater: Boolean, grenzeMonate: Double): List<T> = elterKind(vater).mapNotNull { (k, e, f) ->
        if (k.geb == null || e.tod == null) return@mapNotNull null
        val treffer = if (k.geb.voll && e.tod.voll) monate(e.tod, k.geb)!! > grenzeMonate else (jahre(e.tod, k.geb) ?: return@mapNotNull null) > 1
        if (treffer) T(k.xref, f.xref, "* ${d(k.geb)}  † " + (if (vater) w("vater_klein") else w("mutter_klein")) + " ${d(e.tod)}", geb(k), tod(e)) else null
    }.toList()

    fun elterAlter(vater: Boolean, unter: Double? = null, ueber: Double? = null): List<T> = elterKind(vater).mapNotNull { (k, e, f) ->
        val a = jahre(e.geb, k.geb) ?: return@mapNotNull null
        if ((unter != null && a >= 0 && a < unter) || (ueber != null && a > ueber))
            T(k.xref, f.xref, (if (vater) w("vater") else w("mutter")) + " * ${d(e.geb)}, " + w("kind_klein") + " * ${d(k.geb)} = ${jahreText(a)}", geb(k), geb(e))
        else null
    }.toList()

    fun heiratAlter(unter: Double? = null, ueber: Double? = null): List<T> = familien.flatMap { f ->
        m.partner(f).mapNotNull { p ->
            val a = jahre(p.geb, f.heirat) ?: return@mapNotNull null
            if ((unter != null && a >= 0 && a < unter) || (ueber != null && a > ueber)) T(p.xref, f.xref, "* ${d(p.geb)}  ⚭ ${d(f.heirat)} = ${jahreText(a)}", geb(p), heirat(f)) else null
        }
    }

    fun geschwister(p: PPerson): Set<String> = p.eltern.flatMap { m.f.getValue(it).kinder }.toSet() - p.xref

    // ── Paten und Zeugen: aus den Notizen an Taufe und Heirat ("Paten: Sophie Wulf, Ehefrau, Wilhelm Könecke, Tischler") ──

    class Ereignis(val person: String?, val familie: String?, val record: String, val fakt: FactJson, val datum: PruefDatum, val art: String)

    private val nachName: Map<String, List<PPerson>> by lazy {
        personen.filter { it.vn.isNotEmpty() && it.fn.isNotEmpty() }.groupBy { "${it.vn} ${it.fn}".lowercase() }
    }

    private val patenListe: List<Pair<Ereignis, PPerson>> by lazy {
        val out = ArrayList<Pair<Ereignis, PPerson>>()
        // Freitext "A, Beruf zu Ort; B" oder "A und B": Namen heraussuchen und nur eindeutige Treffer nehmen
        fun ausText(ev: Ereignis, selbst: Set<String>, namen: String) {
            namen.split(',', ';', '/').flatMap { it.split(" und ", " and ") }.map { it.trim().trimEnd('.') }.filter { it.contains(' ') }.forEach { n ->
                val kandidaten = nachName[n.lowercase()].orEmpty().filter { it.xref !in selbst }
                // nur eindeutige Namen: bei zwei gleichnamigen Personen bleibt offen, wer gemeint ist
                if (kandidaten.size == 1) out += ev to kandidaten.single()
            }
        }
        fun auswerten(ev: Ereignis, selbst: Set<String>) {
            if (ev.fakt.hatPaten) {
                // ab API-Stufe 19: verlinkte Paten direkt, freie ueber den Namen; private bleiben aussen vor
                ev.fakt.associates.filter { !it.isPrivate && it.xref !in selbst }.forEach { a -> m.p[a.xref]?.let { out += ev to it } }
                ev.fakt.freeAssociates.forEach { fa -> ausText(ev, selbst, fa.name ?: fa.text) }
                return
            }
            // aeltere Module: die Notiz "Paten: ..." selbst lesen
            ev.fakt.notes.forEach { note ->
                note.lines().forEach { zeile -> PATEN.find(zeile)?.groupValues?.get(2)?.let { ausText(ev, selbst, it) } }
            }
        }
        personen.forEach { p ->
            p.fakten.filter { it.tag in setOf("CHR", "BAPM") }.forEach { fa ->
                val dd = datumAus(fa) ?: return@forEach
                auswerten(Ereignis(p.xref, null, p.xref, fa, dd, w("taufe")), setOf(p.xref))
            }
        }
        familien.forEach { f ->
            f.fakten.filter { it.tag == "MARR" }.forEach { fa ->
                val dd = datumAus(fa) ?: return@forEach
                auswerten(Ereignis(f.vater ?: f.mutter, f.xref, f.xref, fa, dd, w("heirat")), setOfNotNull(f.vater, f.mutter))
            }
        }
        out
    }

    fun paten() = patenListe

    // ── Dubletten: aehnlicher Name, gleiches Geschlecht, Geburtsjahre nahe beieinander, keine Geschwister ──

    fun dubletten(spanne: Double): List<T> {
        val gruppen = personen.filter { it.vn.isNotEmpty() && it.fn.isNotEmpty() && it.geb?.jahr != null }
            .groupBy { namensform(it.vn.split(' ').first()) + "|" + namensform(it.fn) }
        val out = ArrayList<T>()
        for (ps in gruppen.values) {
            if (ps.size < 2) continue
            for (i in ps.indices) for (j in i + 1 until ps.size) {
                val a = ps[i]; val b = ps[j]
                if (a.geschlecht != 'u' && b.geschlecht != 'u' && a.geschlecht != b.geschlecht) continue
                if (kotlin.math.abs(a.geb!!.jahr!! - b.geb!!.jahr!!) > spanne) continue
                if (a.eltern.any { it in b.eltern }) continue          // Geschwister mit Namen des verstorbenen Kindes
                if (a.vn == b.vn && a.fn == b.fn && a.geb.gleich(b.geb)) continue   // das meldet schon 219
                out += T(a.xref, null, "≈ ${n(b)} (${b.xref}) * ${d(b.geb)}, * ${d(a.geb)}", geb(a), geb(b))
            }
        }
        return out
    }

    // ── Orte ──

    class Ort(val anzahl: Int, val lat: Double?, val person: String?, val familie: String?, val beispiel: FaktRef?)

    private val ortListe: Map<String, Ort> by lazy {
        val zahl = HashMap<String, Int>(); val erst = HashMap<String, Triple<Double?, Pair<String?, String?>, FaktRef?>>()
        personen.forEach { p -> p.fakten.forEach { fa -> fa.place?.name?.takeIf(String::isNotBlank)?.let { o ->
            zahl.merge(o, 1, Int::plus); erst.putIfAbsent(o, Triple(fa.place.lat, p.xref to null, ref(p.xref, fa)))
        } } }
        familien.forEach { f -> f.fakten.forEach { fa -> fa.place?.name?.takeIf(String::isNotBlank)?.let { o ->
            zahl.merge(o, 1, Int::plus); erst.putIfAbsent(o, Triple(fa.place.lat, (f.vater ?: f.mutter) to f.xref, ref(f.xref, fa)))
        } } }
        zahl.keys.sorted().associateWith { o -> val (lat, wer, bsp) = erst.getValue(o); Ort(zahl.getValue(o), lat, wer.first, wer.second, bsp) }
    }

    fun orte() = ortListe

    /** Gleicher Ort, anders geschrieben: gleiche uebergeordnete Teile und fast gleicher erster Teil, oder derselbe Ort einmal ohne Gliederung. */
    fun ortsvarianten(): List<T> {
        val namen = ortListe.keys.toList()
        fun teile(o: String) = o.split(',').map(String::trim)
        val out = ArrayList<T>()
        val nachRest = namen.groupBy { teile(it).drop(1).joinToString(",") { t -> namensform(t) } }
        for (gruppe in nachRest.values) for (i in gruppe.indices) for (j in i + 1 until gruppe.size) {
            val a = namensform(teile(gruppe[i]).first()); val b = namensform(teile(gruppe[j]).first())
            if (a.isEmpty() || b.isEmpty()) continue
            val gleich = a == b || (minOf(a.length, b.length) >= 5 && levenshtein(a, b) <= 1)
            if (gleich) out += ortTreffer(gruppe[i], gruppe[j])
        }
        // "Hermannsburg" neben "Hermannsburg, Celle, Niedersachsen": der kurze ist wohl derselbe Ort ohne Gliederung
        val nachErstem = namen.groupBy { namensform(teile(it).first()) }
        for (gruppe in nachErstem.values) {
            val kurz = gruppe.filter { teile(it).size == 1 }
            val lang = gruppe.filter { teile(it).size > 1 }
            if (lang.size == 1) kurz.forEach { out += ortTreffer(it, lang.single()) }
        }
        return out
    }

    private fun ortTreffer(a: String, b: String): T {
        val oa = ortListe.getValue(a); val ob = ortListe.getValue(b)
        val (selten, oft) = if (oa.anzahl <= ob.anzahl) (a to oa) to (b to ob) else (b to ob) to (a to oa)
        return T(selten.second.person, selten.second.familie, "„${selten.first}“ (${selten.second.anzahl}×) ≈ „${oft.first}“ (${oft.second.anzahl}×)", selten.second.beispiel)
    }

    /** Alle, die in einem Kreis der Abstammung liegen (starke Zusammenhangskomponenten, iterativ nach Tarjan). */
    fun eigeneVorfahren(): List<String> {
        val eltern = m.p.mapValues { (_, p) -> p.eltern.flatMap { listOfNotNull(m.f.getValue(it).vater, m.f.getValue(it).mutter) } }
        val index = HashMap<String, Int>(); val low = HashMap<String, Int>()
        val aufStapel = HashSet<String>(); val stapel = ArrayDeque<String>()
        val kreis = ArrayList<String>()
        var zaehler = 0
        for (start in m.p.keys) {
            if (start in index) continue
            val arbeit = ArrayDeque<Pair<String, Int>>()
            arbeit.addLast(start to 0)
            while (arbeit.isNotEmpty()) {
                val (v, i) = arbeit.removeLast()
                if (i == 0) { index[v] = zaehler; low[v] = zaehler; zaehler++; stapel.addLast(v); aufStapel += v }
                val nachbarn = eltern[v].orEmpty()
                if (i < nachbarn.size) {
                    arbeit.addLast(v to i + 1)
                    val w = nachbarn[i]
                    if (w !in index) arbeit.addLast(w to 0)
                    else if (w in aufStapel) low[v] = minOf(low.getValue(v), index.getValue(w))
                    continue
                }
                // v fertig: low an den Aufrufer weitergeben, Komponente abschliessen
                arbeit.lastOrNull()?.let { (u, _) -> low[u] = minOf(low.getValue(u), low.getValue(v)) }
                if (low[v] == index[v]) {
                    val komp = ArrayList<String>()
                    do { val x = stapel.removeLast(); aufStapel -= x; komp += x } while (x != v)
                    if (komp.size > 1 || v in eltern[v].orEmpty()) kreis += komp
                }
            }
        }
        return kreis
    }
}
