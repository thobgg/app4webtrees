package de.bgghome.webtrees.nativ.data

import de.bgghome.webtrees.nativ.api.NameParts

/**
 * Die gespeicherten Teile eines Namens (2 GIVN, SURN, NSFX, NPFX, NICK, SPFX, _RUFNAME) neben dem Anzeigenamen
 * ("Hans /Beispiel/"). Meist passen beide zusammen; sie duerfen aber abweichen - GIVN "Hans Peter Josef" zur Anzeige
 * "Hans" -, und dann darf das Bearbeiten des Anzeigenamens die Teile nicht ueberschreiben (api4webtrees #28).
 * Regel: Vornamen, Familienname und Zusatz der Teile folgen den Feldern des Anzeigenamens, solange sie bisher zu ihnen
 * passten; weichen sie ab, bleiben sie, bis jemand sie selbst aendert. Gesendet wird nur, was sich geaendert hat.
 */
object Namensteile {

    /** Fuer das Formular: fehlende Teile als "". */
    fun felder(t: NameParts?): NameParts = NameParts(
        prefix = t?.prefix.orEmpty(), given = t?.given.orEmpty(), nickname = t?.nickname.orEmpty(),
        surnamePrefix = t?.surnamePrefix.orEmpty(), surname = t?.surname.orEmpty(), suffix = t?.suffix.orEmpty(),
        callName = t?.callName.orEmpty(),
    )

    /**
     * Was der Server als GIVN aus dem Anzeigenamen ableiten wuerde: die Vornamen ohne ein vorangestelltes Praefix
     * ("Dr."), ohne einen Spitznamen in Anfuehrungszeichen und ohne den Rufnamen-Stern.
     */
    fun vornamenOhneBeiwerk(vornamen: String, prefix: String?): String {
        var v = vornamen.replace(Regex("\\s*\"[^\"]*\"\\s*"), " ").replace("*", "").trim()
        val p = prefix?.trim().orEmpty()
        if (p.isNotEmpty() && (v == p || v.startsWith("$p "))) v = v.removePrefix(p).trim()
        return v.replace(Regex("\\s+"), " ")
    }

    /** Der Familienname ohne das Vorsatzwort ("von Berg" mit SPFX "von" -> "Berg"), so wie SURN ihn fuehrt. */
    fun familiennameOhneVorsatz(familienname: String, surnamePrefix: String?): String {
        val f = familienname.trim()
        val p = surnamePrefix?.trim().orEmpty()
        return if (p.isNotEmpty() && f.startsWith("$p ")) f.removePrefix(p).trim() else f
    }

    private fun gegeben(wert: String?): Boolean = !wert.isNullOrBlank()

    /** Passt GIVN zur Anzeige? Ein fehlendes GIVN passt immer - da gibt es nichts zu verlieren. */
    fun vornamenPassen(anzeige: GedcomName, t: NameParts?): Boolean =
        !gegeben(t?.given) || vornamenOhneBeiwerk(anzeige.vornamen, t?.prefix) == t?.given?.trim()

    fun familiennamePasst(anzeige: GedcomName, t: NameParts?): Boolean =
        !gegeben(t?.surname) || familiennameOhneVorsatz(anzeige.familienname, t?.surnamePrefix) == t?.surname?.trim()

    fun zusatzPasst(anzeige: GedcomName, t: NameParts?): Boolean =
        !gegeben(t?.suffix) || anzeige.zusatz.trim() == t?.suffix?.trim()

    /**
     * Die Teile, die vom Anzeigenamen abweichen, als Paare (Teil, gespeicherter Wert) - fuer die Warnung bei Servern,
     * die die Teile zwar liefern (Stufe 45), aber beim Speichern noch aus dem Wert ableiten (vor Stufe 46). Leer, wenn
     * alles passt oder der Server keine Teile liefert.
     */
    fun abweichungen(anzeige: GedcomName, t: NameParts?): List<Pair<Teil, String>> {
        t ?: return emptyList()
        return buildList {
            if (!vornamenPassen(anzeige, t)) add(Teil.Vornamen to t.given.orEmpty())
            if (!familiennamePasst(anzeige, t)) add(Teil.Familienname to t.surname.orEmpty())
            if (!zusatzPasst(anzeige, t)) add(Teil.Zusatz to t.suffix.orEmpty())
        }
    }

    enum class Teil { Vornamen, Familienname, Zusatz }

    /**
     * Der Anzeigename wechselt von [alt] nach [neu]: GIVN, SURN und NSFX in [teile] folgen, soweit sie zu [alt]
     * passten (oder fehlten); abweichende bleiben. Unveraenderte Felder lassen ihren Teil in Ruhe.
     */
    fun folgen(teile: NameParts, alt: GedcomName, neu: GedcomName): NameParts {
        var t = teile
        if (neu.vornamen != alt.vornamen && vornamenPassen(alt, t)) t = t.copy(given = vornamenOhneBeiwerk(neu.vornamen, t.prefix))
        if (neu.familienname != alt.familienname && familiennamePasst(alt, t)) t = t.copy(surname = familiennameOhneVorsatz(neu.familienname, t.surnamePrefix))
        if (neu.zusatz != alt.zusatz && zusatzPasst(alt, t)) t = t.copy(suffix = neu.zusatz.trim())
        return t
    }

    /**
     * Nur die geaenderten Teile gegenueber dem gespeicherten Stand [alt]: ein geleerter Teil als "" (der Server entfernt
     * die Zeile), ein unveraenderter als null (nicht gesendet, bleibt). null, wenn sich nichts geaendert hat.
     */
    fun geaendert(neu: NameParts, alt: NameParts?): NameParts? {
        fun d(n: String?, a: String?): String? {
            val nn = n?.trim().orEmpty(); val aa = a?.trim().orEmpty()
            return if (nn == aa) null else nn
        }
        val r = NameParts(
            prefix = d(neu.prefix, alt?.prefix), given = d(neu.given, alt?.given), nickname = d(neu.nickname, alt?.nickname),
            surnamePrefix = d(neu.surnamePrefix, alt?.surnamePrefix), surname = d(neu.surname, alt?.surname),
            suffix = d(neu.suffix, alt?.suffix), callName = d(neu.callName, alt?.callName),
        )
        return if (r == NameParts()) null else r
    }

    /**
     * Was beim Speichern eines bestehenden Namens an nameParts mitgeht (Server ab Stufe 46): die geaenderten Teile; aendert
     * sich nur der Anzeigename, ein leeres Objekt - dann leitet der Server nichts ab und abweichende Teile bleiben erhalten.
     * null, wenn weder Name noch Teile sich geaendert haben.
     */
    fun zumSenden(teile: NameParts, gespeichert: NameParts?, nameGeaendert: Boolean): NameParts? =
        geaendert(teile, gespeichert) ?: if (nameGeaendert) NameParts() else null
}
