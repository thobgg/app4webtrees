package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.ExportFamily
import de.bgghome.webtrees.nativ.api.ExportIndividual
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FamilyJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.Res
import de.bgghome.webtrees.nativ.res.person_private

/*
 * Buecher zum Weitergeben (Issue 21): nur, was Mitglieder bzw. Besucher in webtrees sehen duerften - auch wenn ein
 * Verwalter das Buch erzeugt. api4webtrees meldet ab API-Stufe 36 an jedem Ereignis, jeder Person, Familie und jedem
 * Paten, wer sie sehen darf (RESN, Datenschutzregeln des Baums, Regeln fuer Lebende). Ohne diese Angabe (aelteres
 * Modul) bleibt alles stehen.
 *
 * Eine Person, die der Leser nicht sehen darf, bleibt mit Namen stehen (oder "Privat", wenn auch der Name verborgen
 * ist), ohne Lebensdaten, Ereignisse und Bild; eine Familie ohne ihre Ereignisse. Verknuepfte Paten, die der Leser
 * nicht sehen darf, fallen weg.
 */

/** Fuer wen das Buch ist: alles (Sicht des Verwalters), was Mitglieder sehen duerfen, was Besucher sehen duerfen. */
enum class Sichtbarkeit { Alle, Mitglieder, Besucher }

internal fun darfSehen(access: String?, s: Sichtbarkeit) = when (s) {
    Sichtbarkeit.Alle -> true
    Sichtbarkeit.Mitglieder -> access != "manager"
    Sichtbarkeit.Besucher -> access == null || access == "visitor"
}

private val LEBENSDATEN = mapOf("BIRT" to "birth", "CHR" to "chr", "BAPM" to "chr", "DEAT" to "death", "BURI" to "buri", "CREM" to "buri", "OCCU" to "occupation")

/**
 * Der Filter fuer eine Stufe. `restricted` an der Person nennt nur, was Besucher nicht sehen duerften; fuer Mitglieder
 * zaehlt davon nur, was ausschliesslich Verwalter sehen. Das steht an den Ereignissen der Person - wo sie nicht geladen
 * sind (Kurzfassungen in Familien), bleibt die Angabe vorsichtshalber weg.
 */
internal class SichtFilter(private val stufe: Sichtbarkeit, private val fakten: Map<String, List<FactJson>> = emptyMap()) {

    private fun gesperrt(p: Person): Set<String> {
        val alle = p.restricted.mapNotNull { LEBENSDATEN[it] }.toSet()
        if (stufe != Sichtbarkeit.Mitglieder) return alle
        val f = fakten[p.xref] ?: return alle
        return p.restricted.filter { tag -> f.any { it.tag == tag && it.access == "manager" } }.mapNotNull { LEBENSDATEN[it] }.toSet()
    }

    /** Kurzfassung einer Person: eingeschraenkte Person nur mit Namen, sonst ohne die gesperrten Lebensdaten. */
    fun person(p: Person): Person {
        if (stufe == Sichtbarkeit.Alle) return p
        val neu = when {
            !darfSehen(p.access, stufe) -> p.copy(birth = null, death = null, chr = null, buri = null, occupation = null, thumb = null, marriages = emptyList())
            p.restricted.isEmpty() -> return p
            else -> {
                val weg = gesperrt(p)
                if (weg.isEmpty()) return p
                p.copy(birth = p.birth.takeUnless { "birth" in weg }, death = p.death.takeUnless { "death" in weg }, chr = p.chr.takeUnless { "chr" in weg },
                    buri = p.buri.takeUnless { "buri" in weg }, occupation = p.occupation.takeUnless { "occupation" in weg })
            }
        }
        // Den Namen Lebender zeigt webtrees je nach Baumeinstellung erst Mitgliedern - sonst "Privat" wie in webtrees
        val name = if (darfSehen(p.nameAccess, stufe)) neu else neu.copy(name = Texte.t(Res.string.person_private), sortName = Texte.t(Res.string.person_private), given = "", surname = "", call = "")
        return name.copy(lifespan = lebensjahre(neu), access = "visitor", restricted = emptyList(), nameAccess = "visitor")
    }

    /** Die Ereignisse, die der Leser sehen duerfte, ohne Paten, die er nicht sehen duerfte. */
    fun fakten(liste: List<FactJson>): List<FactJson> =
        if (stufe == Sichtbarkeit.Alle) liste
        else liste.filter { darfSehen(it.access, stufe) }.map { f ->
            if (f.associates.all { darfSehen(it.access, stufe) }) f else f.copy(associates = f.associates.filter { darfSehen(it.access, stufe) })
        }

    /** Bei verborgener Person bleibt nur ein Name, den der Leser sehen darf. */
    private fun faktenVon(p: Person, liste: List<FactJson>): List<FactJson> =
        if (darfSehen(p.access, stufe)) fakten(liste)
        else liste.filter { it.tag == "NAME" && darfSehen(it.access, stufe) && darfSehen(p.nameAccess, stufe) }

    private fun familie(f: FamilyJson): FamilyJson {
        val sichtbar = darfSehen(f.access, stufe)
        val fk = if (sichtbar) fakten(f.facts) else emptyList()
        return f.copy(
            husband = f.husband?.let(::person), wife = f.wife?.let(::person), spouse = f.spouse?.let(::person),
            children = f.children.map(::person),
            facts = fk,
            // Die Heirat der Kurzfassung nur, wenn ein Heiratsereignis sichtbar bleibt
            marriage = f.marriage?.takeIf { fk.any { it.tag == "MARR" } },
            media = if (sichtbar) f.media else emptyList(),
        )
    }

    fun detail(d: IndividualDetail): IndividualDetail {
        if (stufe == Sichtbarkeit.Alle) return d
        val sichtbar = darfSehen(d.person.access, stufe)
        return d.copy(
            person = person(d.person),
            facts = faktenVon(d.person, d.facts),
            parentFamilies = d.parentFamilies.map(::familie),
            spouseFamilies = d.spouseFamilies.map(::familie),
            stepFamilies = d.stepFamilies.map(::familie),
            media = if (sichtbar) d.media else emptyList(),
            associatedIn = if (sichtbar) d.associatedIn else emptyList(),
        )
    }

    fun baum(t: TreeExport): TreeExport {
        if (stufe == Sichtbarkeit.Alle) return t
        return TreeExport(
            t.lastChange,
            t.individuals.mapValues { (_, i) ->
                ExportIndividual(person(i.person), i.famc, i.fams, faktenVon(i.person, i.facts), if (darfSehen(i.person.access, stufe)) i.media else emptyList())
            },
            t.families.mapValues { (_, f) ->
                val sichtbar = darfSehen(f.access, stufe)
                val fk = if (sichtbar) fakten(f.facts) else emptyList()
                f.copy(facts = fk, marriage = f.marriage?.takeIf { fk.any { it.tag == "MARR" } }, media = if (sichtbar) f.media else emptyList())
            },
        )
    }
}

internal fun IndividualDetail.fuer(s: Sichtbarkeit) = SichtFilter(s, mapOf(person.xref to facts)).detail(this)

internal fun BuchDaten.fuer(s: Sichtbarkeit): BuchDaten {
    if (s == Sichtbarkeit.Alle) return this
    val f = SichtFilter(s, details.values.associate { it.person.xref to it.facts })
    return BuchDaten(ahnen.mapValues { (_, a) -> AhnenEintrag(f.person(a.person), a.hatEltern, a.geschwister?.map(f::person)) },
        details.mapValues { f.detail(it.value) }, bilder)
}

internal fun NachfahrenDaten.fuer(s: Sichtbarkeit): NachfahrenDaten {
    if (s == Sichtbarkeit.Alle) return this
    val f = SichtFilter(s, details.values.associate { it.person.xref to it.facts })
    return NachfahrenDaten(wurzel, details.mapValues { f.detail(it.value) }, bilder)
}

internal fun FamilienDaten.fuer(s: Sichtbarkeit): FamilienDaten {
    if (s == Sichtbarkeit.Alle) return this
    val f = SichtFilter(s, baum.individuals.mapValues { it.value.facts })
    return FamilienDaten(f.baum(baum), bilder, haeuser)
}
