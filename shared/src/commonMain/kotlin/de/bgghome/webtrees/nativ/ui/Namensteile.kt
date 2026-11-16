package de.bgghome.webtrees.nativ.ui

import androidx.compose.runtime.Composable
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Die Teile eines Namens, die nicht schon als Vornamen und Familienname im Namen stehen - Praefix, Spitzname,
 * Vorsatzwort, Zusatz und Rufname -, als beschriftete Zeilen. Server ab API-Stufe 45 (nameParts), sonst leer.
 */
@Composable
fun namensteilZeilen(fact: FactJson): List<Pair<String, String>> {
    val t = fact.nameParts ?: return emptyList()
    return listOf(
        Res.string.name_part_prefix to t.prefix,
        Res.string.name_part_nickname to t.nickname,
        Res.string.name_part_surname_prefix to t.surnamePrefix,
        Res.string.name_part_suffix to t.suffix,
        Res.string.name_part_call to t.callName,
    ).mapNotNull { (label, wert) -> wert?.takeIf(String::isNotBlank)?.let { stringResource(label) to it } }
}
