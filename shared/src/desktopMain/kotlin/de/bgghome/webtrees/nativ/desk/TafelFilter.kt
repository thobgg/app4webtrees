package de.bgghome.webtrees.nativ.desk

/*
 * Filter fuer Tafeln (D2, 28.09.2026): per Rechtsklick ausgeblendete Zweige (je Stammbaum gespeichert, auch bei
 * Vorfahren), bei Nachfahren auf Wunsch nur Verheiratete oder Eltern und keine frueh Verstorbenen. Die Wurzel bleibt
 * immer; wer herausfaellt, nimmt alles mit, was auf der Tafel von ihm ausgeht.
 */

/** Ist die Person frueher als mit [jahre] Jahren gestorben (beide Jahre bekannt)? */
private fun fruehVerstorben(k: TafelPerson, jahre: Int): Boolean {
    if (jahre <= 0 || !k.person.isDead) return false
    val g = k.person.birth?.date?.year ?: 0; val t = k.person.death?.date?.year ?: 0
    return g > 0 && t > 0 && t - g < jahre
}

/** Der Baum ohne ausgeblendete Personen; [nachfahren]: dazu die Filter fuer Nachfahren. */
internal fun gefiltert(k: TafelPerson, o: TafelOptionen, nachfahren: Boolean): TafelPerson {
    if (o.ausgeblendet.isEmpty() && (!nachfahren || (!o.nurMitPartner && o.mindestalter <= 0))) return k
    fun bleibt(c: TafelPerson) = c.person.xref !in o.ausgeblendet &&
        (!nachfahren || ((!o.nurMitPartner || c.verheiratet || c.kinder.isNotEmpty()) && !fruehVerstorben(c, o.mindestalter)))
    fun neu(n: TafelPerson): TafelPerson {
        // Alte Kinder -> neue, damit die Ehen mit Partnerkasten auf die gefilterten Kinder zeigen
        val neueKinder = java.util.IdentityHashMap<TafelPerson, TafelPerson>()
        val kinder = n.kinder.filter(::bleibt).map { k -> neu(k).also { neueKinder[k] = it } }
        return TafelPerson(
            n.person, kinder, n.nummer, n.partner, n.verweis, n.hinweis, n.aufLinie,
            n.geschwister.filter { it.xref !in o.ausgeblendet }, n.geschwisterLinks, n.verheiratet,
            n.ehen.map { e -> TafelEhe(e.partner, e.kinder.mapNotNull { neueKinder[it] }) },
        )
    }
    return neu(k)
}
