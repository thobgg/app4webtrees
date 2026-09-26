package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.ExportFamily
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*

/*
 * Listen ueber den ganzen Baum (26.09.2026), aus dem Export von api4webtrees (ab Stufe 17): Ereignisliste mit
 * waehlbaren Ereignissen und als Jahrestagskalender, Namensliste, Ortsliste, Familienliste, Faktenliste (Beruf,
 * Konfession) und Taufpaten. Tabellenzeilen wie in der Ahnenwertung.
 */

/** Der ganze Baum; aeltere Server ohne Export: verstaendliche Meldung statt leerer Liste. */
internal suspend fun ganzerBaum(client: WtClient, tree: String): TreeExport =
    BaumSpeicher.holen(client, tree, Int.MAX_VALUE / 64) ?: throw IllegalStateException(Texte.t(Res.string.desk_list_needs_export))

private fun ortPasst(name: String?, filter: String): Boolean =
    filter.isBlank() || name?.split(',')?.first()?.trim()?.lowercase()?.startsWith(filter.trim().lowercase()) == true

private fun ersterOrt(name: String?) = name?.split(',')?.first()?.trim().orEmpty()

/** Monat und Tag aus der GEDCOM-Form ("12 OCT 1801"); null, wenn das Datum nicht tagesgenau ist. */
private val MONATE_GEDCOM = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
private fun monatTag(d: DateJson?): Pair<Int, Int>? {
    val t = d?.gedcom?.trim()?.uppercase()?.split(Regex("\\s+")) ?: return null
    if (t.size != 3 || t[1] !in MONATE_GEDCOM) return null
    return (MONATE_GEDCOM.indexOf(t[1]) + 1) to (t[0].toIntOrNull() ?: return null)
}

private val EREIGNIS_NAME = mapOf("BIRT" to Res.string.desk_ev_birth, "CHR" to Res.string.desk_ev_baptism, "BAPM" to Res.string.desk_ev_baptism,
    "MARR" to Res.string.desk_ev_marriage, "DEAT" to Res.string.desk_ev_death, "BURI" to Res.string.desk_ev_burial)

private fun leben(p: Person): String = p.lifespan.ifBlank { listOfNotNull(p.birth?.date?.year?.takeIf { it > 0 }, p.death?.date?.year?.takeIf { it > 0 }).joinToString("–") }

// ── Ereignisliste ──

internal fun ereignislisteBaum(b: TreeExport, titel: String, o: ListenOptionen): List<Zeile> {
    class E(val jd: Int, val datum: DateJson?, val art: String, val wer: String, val ort: String)
    val liste = mutableListOf<E>()
    b.individuals.values.filter { !it.person.isPrivate }.forEach { i ->
        i.facts.filter { it.tag in o.ereignisse && it.tag != "MARR" && (it.date?.jd ?: 0) > 0 && ortPasst(it.place?.name, o.ortFilter) }.forEach { f ->
            liste += E(f.date!!.jd, f.date, f.tag, registerName(i.person), f.place?.name.orEmpty())
        }
    }
    if ("MARR" in o.ereignisse) b.families.values.filter { !it.isPrivate }.forEach { f ->
        val m = f.facts.firstOrNull { it.tag == "MARR" }?.let { it.date to it.place } ?: (f.marriage?.date to f.marriage?.place)
        if ((m.first?.jd ?: 0) > 0 && ortPasst(m.second?.name, o.ortFilter)) {
            val wer = listOfNotNull(b.person(f.husband)?.let(::registerName), b.person(f.wife)?.let(::registerName)).joinToString(" ∞ ")
            liste += E(m.first!!.jd, m.first, "MARR", wer, m.second?.name.orEmpty())
        }
    }
    val anteile = listOf(0.15f, 0.12f, 0.43f, 0.30f)
    return buildList {
        add(Zeile(Texte.t(if (o.kalender) Res.string.desk_title_calendar else Res.string.desk_title_events, titel), gross = true))
        add(Zeile(Texte.t(Res.string.desk_list_count, liste.size)))
        fun zeile(e: E) = add(Zeile("", spalten = listOf(buchDatum(e.datum), Texte.t(EREIGNIS_NAME[e.art] ?: Res.string.desk_ev_birth), e.wer, ersterOrt(e.ort)), anteile = anteile))
        if (o.kalender) {
            // Jahrestagskalender: nur tagesgenaue Daten, nach Monat und Tag, innerhalb nach Jahr
            val monate = java.text.DateFormatSymbols.getInstance(java.util.Locale.getDefault()).months
            liste.mapNotNull { e -> monatTag(e.datum)?.let { it to e } }.sortedWith(compareBy({ it.first.first }, { it.first.second }, { it.second.jd }))
                .groupBy { it.first.first }.forEach { (monat, eintraege) ->
                    add(Zeile("")); add(Zeile(monate[monat - 1], fett = true))
                    eintraege.forEach { zeile(it.second) }
                }
        } else {
            liste.sortedBy { it.jd }.groupBy { (it.datum?.year ?: 0) / 100 }.forEach { (jh, eintraege) ->
                add(Zeile("")); add(Zeile("${jh * 100}–${jh * 100 + 99}", fett = true))
                eintraege.forEach(::zeile)
            }
        }
    }
}

// ── Namen und Orte ──

internal fun namensliste(b: TreeExport, titel: String): List<Zeile> {
    val personen = b.individuals.values.map { it.person }.filter { !it.isPrivate && it.surname.isNotBlank() }
    val anteile = listOf(0.28f, 0.14f, 0.18f, 0.40f)
    return buildList {
        add(Zeile(Texte.t(Res.string.desk_title_names, titel), gross = true))
        add(Zeile(Texte.t(Res.string.desk_list_count_names, personen.map { it.surname }.distinct().size, personen.size)))
        add(Zeile(""))
        add(Zeile("", spalten = listOf(Texte.t(Res.string.desk_col_surname), Texte.t(Res.string.desk_col_persons), Texte.t(Res.string.desk_col_period), Texte.t(Res.string.desk_col_places)), anteile = anteile, fett = true))
        personen.groupBy { it.surname }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (name, ps) ->
            val jahre = ps.flatMap { listOfNotNull(it.birth?.date?.year?.takeIf { y -> y > 0 }, it.death?.date?.year?.takeIf { y -> y > 0 }) }
            val orte = ps.flatMap { listOfNotNull(it.birth?.place?.name, it.death?.place?.name) }.map(::ersterOrt).filter(String::isNotBlank)
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).joinToString(", ") { it.key }
            add(Zeile("", spalten = listOf(name, "${ps.size}", if (jahre.isEmpty()) "" else "${jahre.min()}–${jahre.max()}", orte), anteile = anteile))
        }
    }
}

internal fun ortsliste(b: TreeExport, titel: String, o: ListenOptionen): List<Zeile> {
    // Ort -> Nachname -> (Personen, Jahre der Ereignisse dort)
    val orte = sortedMapOf<String, MutableMap<String, Pair<MutableSet<String>, MutableList<Int>>>>(String.CASE_INSENSITIVE_ORDER)
    b.individuals.values.filter { !it.person.isPrivate }.forEach { i ->
        i.facts.filter { it.place != null && ortPasst(it.place?.name, o.ortFilter) }.forEach { f ->
            val ort = ersterOrt(f.place?.name).ifBlank { return@forEach }
            val e = orte.getOrPut(ort) { sortedMapOf(String.CASE_INSENSITIVE_ORDER) }.getOrPut(i.person.surname.ifBlank { "?" }) { mutableSetOf<String>() to mutableListOf() }
            e.first += i.person.xref; e.second += (f.date?.year ?: 0)
        }
    }
    val anteile = listOf(0.06f, 0.40f, 0.20f, 0.34f)
    return buildList {
        add(Zeile(Texte.t(Res.string.desk_title_places, titel), gross = true))
        add(Zeile(Texte.t(Res.string.desk_list_count_places, orte.size)))
        add(Zeile(""))
        add(Zeile("", spalten = listOf("", Texte.t(Res.string.desk_col_surname), Texte.t(Res.string.desk_col_persons), Texte.t(Res.string.desk_col_period)), anteile = anteile, fett = true))
        orte.forEach { (ort, namen) ->
            add(Zeile("")); add(Zeile(ort, fett = true))
            namen.forEach { (name, e) ->
                val j = e.second.filter { it > 0 }
                val zeit = if (j.isEmpty()) "" else if (j.min() == j.max()) "${j.min()}" else "${j.min()}–${j.max()}"
                add(Zeile("", spalten = listOf("", name, "${e.first.size}", zeit), anteile = anteile))
            }
        }
    }
}

// ── Familien, Fakten, Paten ──

internal fun familienliste(b: TreeExport, titel: String, o: ListenOptionen): List<Zeile> {
    fun heirat(f: ExportFamily) = f.facts.firstOrNull { it.tag == "MARR" }?.let { it.date to it.place } ?: (f.marriage?.date to f.marriage?.place)
    val familien = b.families.values.filter { !it.isPrivate && (it.husband != null || it.wife != null) }
        .filter { f -> o.ortFilter.isBlank() || ortPasst(heirat(f).second?.name, o.ortFilter) }
    fun name(f: ExportFamily) = (b.person(f.husband) ?: b.person(f.wife))?.let(::registerName).orEmpty()
    val sortiert = if (o.chronologisch) familien.sortedBy { heirat(it).first?.jd?.takeIf { j -> j > 0 } ?: Int.MAX_VALUE }
        else familien.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { name(it) })
    val anteile = listOf(0.34f, 0.34f, 0.25f, 0.07f)
    return buildList {
        add(Zeile(Texte.t(Res.string.desk_title_families_list, titel), gross = true))
        add(Zeile(Texte.t(Res.string.desk_list_count, sortiert.size)))
        add(Zeile(""))
        add(Zeile("", spalten = listOf(Texte.t(Res.string.desk_col_husband), Texte.t(Res.string.desk_col_wife), Texte.t(Res.string.desk_ev_marriage), Texte.t(Res.string.desk_col_children)), anteile = anteile, fett = true))
        sortiert.forEach { f ->
            val (d, p) = heirat(f)
            add(Zeile("", spalten = listOf(b.person(f.husband)?.let { "${registerName(it)} (${leben(it)})" } ?: "?", b.person(f.wife)?.let { "${registerName(it)} (${leben(it)})" } ?: "?",
                listOf(buchDatum(d), ersterOrt(p?.name)).filter(String::isNotBlank).joinToString(" "), if (f.children.isEmpty()) "" else "${f.children.size}"), anteile = anteile))
        }
    }
}

internal fun faktenliste(b: TreeExport, titel: String, o: ListenOptionen): List<Zeile> {
    val werte = sortedMapOf<String, MutableList<Person>>(String.CASE_INSENSITIVE_ORDER)
    b.individuals.values.filter { !it.person.isPrivate }.forEach { i ->
        i.facts.filter { it.tag == o.fakt && it.value.isNotBlank() }.map { it.value.trim() }.distinct().forEach { werte.getOrPut(it) { mutableListOf() } += i.person }
    }
    val anteile = listOf(0.06f, 0.50f, 0.20f, 0.24f)
    return buildList {
        add(Zeile(Texte.t(if (o.fakt == "RELI") Res.string.desk_title_religions else Res.string.desk_title_occupations, titel), gross = true))
        werte.forEach { (wert, ps) ->
            add(Zeile("")); add(Zeile("$wert (${ps.size})", fett = true))
            ps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { registerName(it) }).forEach { p ->
                add(Zeile("", spalten = listOf("", registerName(p), leben(p), ersterOrt(p.birth?.place?.name ?: p.death?.place?.name)), anteile = anteile))
            }
        }
    }
}

/** Taufen mit Paten: vorerst aus dem Pateneintrag der Taufe ("Paten: ..."), chronologisch. */
internal fun taufpaten(b: TreeExport, titel: String, o: ListenOptionen): List<Zeile> {
    class T(val f: FactJson, val p: Person, val paten: String)
    val taufen = b.individuals.values.filter { !it.person.isPrivate }.flatMap { i ->
        i.facts.filter { it.tag in setOf("CHR", "BAPM") && ortPasst(it.place?.name, o.ortFilter) }.mapNotNull { f ->
            f.notes.firstOrNull { it.startsWith("Paten") }?.let { T(f, i.person, it.substringAfter(':').trim()) }
        }
    }.sortedBy { it.f.date?.jd?.takeIf { j -> j > 0 } ?: Int.MAX_VALUE }
    return buildList {
        add(Zeile(Texte.t(Res.string.desk_title_godparents, titel), gross = true))
        add(Zeile(Texte.t(Res.string.desk_list_count, taufen.size)))
        // Fliesstext statt Tabelle: die Patenangaben sind oft laenger als eine Spalte
        taufen.forEach { t ->
            add(Zeile(""))
            add(Zeile("${buchDatum(t.f.date)}   ${registerName(t.p)}" + ersterOrt(t.f.place?.name).let { if (it.isNotBlank()) ", $it" else "" }, fett = true))
            add(Zeile("${Texte.t(Res.string.desk_col_godparents)}: ${t.paten}", 1))
        }
    }
}
