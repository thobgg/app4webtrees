package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.Info
import de.bgghome.webtrees.nativ.api.hat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.API_LOC_HIERARCHY
import de.bgghome.webtrees.nativ.api.API_LOC_LINKS
import de.bgghome.webtrees.nativ.data.GovTypen
import de.bgghome.webtrees.nativ.api.PlaceDetail
import de.bgghome.webtrees.nativ.api.PlaceRequest
import de.bgghome.webtrees.nativ.api.WriteResult
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.Field
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.karte.GeoPunkt
import de.bgghome.webtrees.nativ.ui.karte.KachelEbene
import de.bgghome.webtrees.nativ.ui.karte.KachelKarte
import de.bgghome.webtrees.nativ.ui.karte.KartenPin
import de.bgghome.webtrees.nativ.ui.karte.KartenZustand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * Ortsdaten bearbeiten (ab API-Stufe 22): GOV-Kennung, Notiz und Koordinaten im Ortsdatensatz (_LOC). Die Koordinaten
 * lassen sich ueber OpenStreetMap (Nominatim) suchen - ein Klick auf einen Treffer uebernimmt Breite und Laenge.
 * Administratoren koennen sie zusaetzlich in die Geografischen Daten von webtrees schreiben, die die Karten im Browser
 * lesen.
 */

/** Ein Treffer der Ortssuche. */
internal data class OrtTreffer(val name: String, val lat: Double, val lng: Double, val plz: String? = null, val region: String? = null, val land: String? = null)

/** Nominatim (OpenStreetMap): hoechstens 10 Treffer, wahrscheinlichste zuerst. Ehrlicher User-Agent ist Pflicht. */
internal fun nominatim(frage: String, userAgent: String, sprache: String = Locale.getDefault().language): List<OrtTreffer> {
    val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
        .addQueryParameter("q", frage).addQueryParameter("format", "jsonv2").addQueryParameter("limit", "10")
        .addQueryParameter("accept-language", sprache).addQueryParameter("addressdetails", "1").build()
    val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { antwort ->
        if (!antwort.isSuccessful) error("OpenStreetMap: HTTP ${antwort.code}")
        return Json.parseToJsonElement(antwort.body?.string().orEmpty().ifEmpty { "[]" }).jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val lat = o["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val lng = o["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val name = o["display_name"]?.jsonPrimitive?.contentOrNull
            val adr = o["address"] as? kotlinx.serialization.json.JsonObject
            fun a(k: String) = adr?.get(k)?.jsonPrimitive?.contentOrNull
            if (lat == null || lng == null || name == null) null else OrtTreffer(name, lat, lng, a("postcode"), a("state"), a("country"))
        }
    }
}

/**
 * Eine Koordinate als Zahl: dezimal ("53.778417", "53,778417", "-8.5") oder in Grad, Minuten, Sekunden
 * ("53° 46′ 42,3″ N", "53 46 42.3", "N53.778"). Sueden und Westen (S, W, Süd, West) werden negativ. Leer -> null.
 */
internal fun grad(text: String): Double? {
    val t = text.trim()
    if (t.isEmpty()) return null
    t.replace(',', '.').toDoubleOrNull()?.let { return it }
    val zahlen = Regex("\\d+(?:[.,]\\d+)?").findAll(t).map { it.value.replace(',', '.').toDouble() }.toList()
    if (zahlen.isEmpty() || zahlen.size > 3) return null
    val wert = zahlen[0] + (zahlen.getOrNull(1) ?: 0.0) / 60 + (zahlen.getOrNull(2) ?: 0.0) / 3600
    val sued = Regex("(?i)(^|[^a-zäöü])(s|w|z|süd|sued|zuid|west|south)([^a-zäöü]|$)").containsMatchIn(t)
    return if (sued || t.startsWith("-")) -wert else wert
}

/**
 * Breite und Laenge aus einem kopierten Text: "53.778417, 20.480111", "53,7784; 20,4801" oder wie in der Wikipedia
 * "53° 46′ 42″ N, 20° 28′ 48″ O". Null, wenn es nicht genau zwei Koordinaten sind.
 */
internal fun koordinatenPaar(text: String): Pair<Double, Double>? =
    koordinatenPaarRoh(text)?.takeIf { (b, l) -> b in -90.0..90.0 && l in -180.0..180.0 }

private fun koordinatenPaarRoh(text: String): Pair<Double, Double>? {
    val t = text.trim().replace('\n', ' ')
    Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,;\\s]\\s*(-?\\d+(?:\\.\\d+)?)\\s*$").find(t)?.let { m ->
        return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
    }
    Regex("^\\s*(-?\\d+(?:,\\d+)?)\\s*[;\\s]\\s*(-?\\d+(?:,\\d+)?)\\s*$").find(t)?.let { m ->
        return m.groupValues[1].replace(',', '.').toDouble() to m.groupValues[2].replace(',', '.').toDouble()
    }
    // Grad/Minuten/Sekunden: nach der Himmelsrichtung der Breite (N oder S) teilen
    val trenner = Regex("(?i)(^|[^a-zäöü])(n|s|z|nord|noord|süd|sued|zuid|north|south)([^a-zäöü]|$)").find(t) ?: return null
    val ende = trenner.range.last + if (trenner.groupValues[3].isEmpty()) 1 else 0
    val breite = grad(t.substring(0, ende)) ?: return null
    val laenge = grad(t.substring(ende).trim().trimStart(',', ';').trim()) ?: return null
    return if (breite in -90.0..90.0 && laenge in -180.0..180.0) breite to laenge else null
}

@Composable
fun OrtDialog(tree: String, ort: PlaceDetail, client: WtClient, istAdmin: Boolean, openWeb: (String) -> Unit,
              onDismiss: () -> Unit, info: Info? = null, onSaved: (WriteResult) -> Unit) {
    val loc = ort.location
    val altGov = loc?.gov.orEmpty()
    val altNotiz = loc?.notes?.firstOrNull().orEmpty()
    // Vorbelegt mit den Koordinaten des _LOC - fehlen sie dort, mit den gezeigten (Geografische Daten oder Ereignis)
    val altLat = loc?.lat ?: ort.lat
    val altLng = loc?.lng ?: ort.lng
    var gov by remember { mutableStateOf(altGov) }
    var kurz by remember { mutableStateOf(loc?.shortName.orEmpty()) }
    var art by remember { mutableStateOf(loc?.type.orEmpty()) }
    val altGovTyp = loc?.govType?.trim().orEmpty()
    var govTyp by remember { mutableStateOf(altGovTyp) }
    // Ab Stufe 32: Ereignisse am Ort ohne Verweis "3 _LOC" beim Speichern verknuepfen (vorgeschlagen, abwaehlbar)
    val unverknuepft = if (info.hat(API_LOC_LINKS)) ort.unlinked ?: 0 else 0
    var verknuepfen by remember { mutableStateOf(true) }
    var plz by remember { mutableStateOf(loc?.postalCode.orEmpty()) }
    var region by remember { mutableStateOf(loc?.region.orEmpty()) }
    var land by remember { mutableStateOf(loc?.country.orEmpty()) }
    var notiz by remember { mutableStateOf(altNotiz) }
    var lat by remember { mutableStateOf(altLat?.let { dezimalEinzeln(it) }.orEmpty()) }
    var lng by remember { mutableStateOf(altLng?.let { dezimalEinzeln(it) }.orEmpty()) }
    var mapData by remember { mutableStateOf(istAdmin) }
    var suche by remember { mutableStateOf<String?>(null) }
    var treffer by remember { mutableStateOf<List<OrtTreffer>?>(null) }
    // Was ein Treffer uebernimmt: Koordinaten immer vorgeschlagen, die Anschrift nur, wenn dort noch nichts steht
    var nimmKoord by remember { mutableStateOf(true) }
    var nimmAdresse by remember { mutableStateOf(loc?.postalCode.isNullOrBlank() && loc?.region.isNullOrBlank() && loc?.country.isNullOrBlank()) }
    var hinweis by remember { mutableStateOf<String?>(null) }
    val keineInAblage = stringResource(Res.string.desk_place_clipboard_none)
    var sucht by remember { mutableStateOf(false) }
    var speichert by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val b = grad(lat); val l = grad(lng)
    val koordOk = (lat.isBlank() && lng.isBlank()) || (b != null && l != null && b in -90.0..90.0 && l in -180.0..180.0)
    // Koordinaten zaehlen als geaendert, wenn sie vom _LOC abweichen (auch wenn sie bisher nur woanders standen)
    val koordGeaendert = koordOk && (b != loc?.lat || l != loc?.lng)

    fun sucheStarten(frage: String) {
        sucht = true; fehler = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { nominatim(frage, client.userAgent) } }
                .onSuccess { treffer = it }.onFailure { fehler = it.message ?: "?" }
            sucht = false
        }
    }

    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_place_edit) + " – " + ort.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            // Zwei Spalten: links Ortsangaben, rechts Koordinaten mit Karte und Suche
            Row(Modifier.width(1060.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Field(gov, { gov = it.trim() }, Res.string.desk_place_gov, hint = Res.string.desk_place_gov_hint) }
                    TextButton(onClick = { openWeb("https://gov.genealogy.net/search/name?name=" + java.net.URLEncoder.encode(ort.levels.firstOrNull() ?: ort.name, "UTF-8")) }) {
                        Text(stringResource(Res.string.desk_place_gov_search))
                    }
                }
                // Art des Orts (TYPE am _LOC, ab Stufe 27) und daneben die GOV-Typnummer (2 _GOVTYPE, ab Stufe 32) - die Wahl
                // fuellt eine leere oder automatisch gesetzte Art mit
                if (info.hat(API_LOC_HIERARCHY)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Field(art, { art = it }, Res.string.desk_place_type, hint = Res.string.desk_place_type_hint) }
                    if (info.hat(API_LOC_LINKS)) Box(Modifier.weight(1f)) {
                        GovTypWahl(govTyp) { nr ->
                            val sprache = Locale.getDefault().language
                            if (art.isBlank() || art == GovTypen.name(govTyp, sprache)) art = GovTypen.name(nr, sprache).orEmpty()
                            govTyp = nr
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Field(kurz, { kurz = it }, Res.string.desk_place_short, hint = Res.string.desk_place_short_hint) }
                    Box(Modifier.weight(1f)) { Field(plz, { plz = it }, Res.string.desk_place_postal) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Field(region, { region = it }, Res.string.desk_place_region) }
                    Box(Modifier.weight(1f)) { Field(land, { land = it }, Res.string.desk_place_country) }
                }
                Field(notiz, { notiz = it }, Res.string.fact_note, minLines = 8)
                if (unverknuepft > 0) Tipp(stringResource(Res.string.tipp_place_link_events)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(verknuepfen, { verknuepfen = it })
                        Text(stringResource(Res.string.desk_place_link_events, unverknuepft), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (istAdmin) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(mapData, { mapData = it })
                    Text(stringResource(Res.string.desk_place_mapdata), style = MaterialTheme.typography.bodyMedium)
                }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lat, { lat = it }, Modifier.weight(1f), label = { Text(stringResource(Res.string.desk_place_lat)) }, singleLine = true, isError = !koordOk)
                    OutlinedTextField(lng, { lng = it }, Modifier.weight(1f), label = { Text(stringResource(Res.string.desk_place_lng)) }, singleLine = true, isError = !koordOk)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (koordOk) Res.string.desk_place_coords_hint else Res.string.desk_place_invalid_coords), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = if (koordOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        val text = runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String }.getOrNull()
                        val paar = text?.let(::koordinatenPaar)
                        if (paar == null) hinweis = keineInAblage else { lat = dezimalEinzeln(paar.first); lng = dezimalEinzeln(paar.second); hinweis = null }
                    }) { Text(stringResource(Res.string.desk_place_from_clipboard)) }
                }
                hinweis?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (b != null && l != null && koordOk) {
                    val zustand = remember { KartenZustand() }
                    BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        val w = constraints.maxWidth; val h = constraints.maxHeight
                        LaunchedEffect(b, l, w, h) { zustand.passeEin(listOf(GeoPunkt(b, l)), w, h, einzelZoom = 11, maxZoom = 15) }
                        KachelKarte(zustand, KachelEbene.STANDARD, Modifier.fillMaxSize(), pins = listOf(KartenPin(b, l, farbe = MaterialTheme.colorScheme.primary, radiusDp = 8f)))
                    }
                }

                // ── Koordinaten suchen (OpenStreetMap) ──
                if (suche == null) {
                    OutlinedButton(onClick = { suche = ort.name; sucheStarten(ort.name) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_place_search_coords)) }
                    Text(stringResource(Res.string.desk_place_search_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(suche.orEmpty(), { suche = it }, Modifier.weight(1f), singleLine = true,
                            label = { Text(stringResource(Res.string.desk_place_search_title, ort.levels.firstOrNull() ?: ort.name)) })
                        OutlinedButton(onClick = { suche?.takeIf(String::isNotBlank)?.let(::sucheStarten) }, enabled = !sucht, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(Res.string.desk_place_search_button))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(nimmKoord, { nimmKoord = it }); Text(stringResource(Res.string.desk_place_take_coords), style = MaterialTheme.typography.bodySmall)
                        Checkbox(nimmAdresse, { nimmAdresse = it }); Text(stringResource(Res.string.desk_place_take_address), style = MaterialTheme.typography.bodySmall)
                    }
                    when {
                        sucht -> Text("…")
                        treffer?.isEmpty() == true -> Text(stringResource(Res.string.desk_place_search_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            treffer.orEmpty().forEach { t ->
                                val aktiv = grad(lat) == t.lat && grad(lng) == t.lng
                                Row(Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                    .fokusRahmen().clickable {
                                        if (nimmKoord) { lat = dezimalEinzeln(t.lat); lng = dezimalEinzeln(t.lng) }
                                        if (nimmAdresse) { t.plz?.let { plz = it }; t.region?.let { region = it }; t.land?.let { land = it } }
                                    }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                    Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(Res.string.desk_place_take), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            }
        },
        breite = 1120.dp,
        confirmButton = {
            val linkEvents = unverknuepft > 0 && verknuepfen
            val geaendert = linkEvents || govTyp != altGovTyp || gov != altGov || notiz.trim() != altNotiz.trim() || koordGeaendert || art.trim() != loc?.type.orEmpty() ||
                kurz.trim() != loc?.shortName.orEmpty() || plz.trim() != loc?.postalCode.orEmpty() || region.trim() != loc?.region.orEmpty() || land.trim() != loc?.country.orEmpty()
            TextButton(enabled = geaendert && koordOk && !speichert, onClick = {
                speichert = true; fehler = null
                val anfrage = PlaceRequest(
                    name = ort.name,
                    gov = gov.takeIf { it != altGov },
                    note = notiz.takeIf { it.trim() != altNotiz.trim() },
                    koordinatenAendern = koordGeaendert, lat = b, lng = l,
                    mapData = mapData && istAdmin && koordGeaendert,
                    shortName = kurz.trim().takeIf { it != loc?.shortName.orEmpty() },
                    type = art.trim().takeIf { it != loc?.type.orEmpty() },
                    postalCode = plz.trim().takeIf { it != loc?.postalCode.orEmpty() },
                    region = region.trim().takeIf { it != loc?.region.orEmpty() },
                    country = land.trim().takeIf { it != loc?.country.orEmpty() },
                    govTypeAendern = govTyp != altGovTyp, govType = govTyp.ifEmpty { null },
                    linkEvents = linkEvents,
                )
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { client.savePlace(tree, anfrage) } }
                        .onSuccess { onSaved(it); onDismiss() }
                        .onFailure { fehler = it.message ?: "?"; speichert = false }
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !speichert) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * GOV-Typ waehlen: Knopf mit Nummer und Name, darunter eine Liste zum Filtern (Name deutsch/englisch oder Nummer).
 * Ohne Filter stehen die Gebaeudetypen (Hof, Muehle, Gut ...) vorn. Leer = kein GOV-Typ.
 */
@Composable
private fun GovTypWahl(govTyp: String, onWahl: (String) -> Unit) {
    val sprache = Locale.getDefault().language
    var offen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    Box {
        // Ein Feld in der Hoehe der anderen; nur zum Anzeigen, ein Klick darauf oeffnet die Liste
        Tipp(stringResource(Res.string.tipp_place_gov_type)) {
            Box {
                OutlinedTextField(GovTypen.name(govTyp, sprache)?.let { "$govTyp · $it" } ?: govTyp, {}, Modifier.fillMaxWidth(), readOnly = true, singleLine = true,
                    label = { Text(stringResource(Res.string.desk_place_gov_type_pick)) }, trailingIcon = { Text("▾") })
                Box(Modifier.matchParentSize().clickable { filter = ""; offen = true })
            }
        }
        DropdownMenu(offen, { offen = false }) {
            OutlinedTextField(filter, { filter = it }, Modifier.padding(horizontal = 8.dp).width(280.dp), singleLine = true,
                label = { Text(stringResource(Res.string.desk_place_gov_type_filter)) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.desk_place_gov_type_none)) }, onClick = { onWahl(""); offen = false })
            val f = filter.trim().lowercase()
            fun name(n: Pair<String, String>) = if (sprache == "de") n.first else n.second
            // Veraltete Typen nur, wenn der Ort gerade einen traegt; Name vorn, Nummer grau dahinter
            val treffer = GovTypen.NAMEN.entries
                .filter { (nr, n) -> nr.toString() == govTyp || listOf(n.first, n.second).none { it.startsWith("veraltet") || it.startsWith("deprecated") } }
                .filter { (nr, n) -> f.isEmpty() || nr.toString() == f || n.first.lowercase().contains(f) || n.second.lowercase().contains(f) }
                .sortedBy { name(it.value).lowercase() }
            val (gebaeude, uebrige) = treffer.partition { it.key in GovTypen.HAUS }
            for ((titel, liste) in listOf(Res.string.desk_place_gov_type_buildings to gebaeude, Res.string.desk_place_gov_type_areas to uebrige.take(80))) {
                if (liste.isEmpty()) continue
                Text(stringResource(titel), Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                liste.forEach { (nr, n) ->
                    DropdownMenuItem(text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name(n), Modifier.weight(1f))
                            Text(nr.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, onClick = { onWahl(nr.toString()); offen = false })
                }
            }
        }
    }
}

/** 53.7784170 -> "53.778417" */
internal fun dezimalEinzeln(wert: Double): String = "%.6f".format(Locale.ROOT, wert).trimEnd('0').trimEnd('.')
