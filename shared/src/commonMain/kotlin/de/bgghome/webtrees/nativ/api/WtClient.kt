package de.bgghome.webtrees.nativ.api

import de.bgghome.webtrees.nativ.data.Ablage
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Das Modul hat die Anfrage fachlich abgelehnt ({"ok":false,"error":..}). */
class ApiException(val code: String, val status: Int?) : Exception("API: $code")

/**
 * Die Antwort war kein JSON. Bei webtrees heisst das: nicht (mehr) angemeldet, Baum nicht
 * sichtbar, CSRF-Token abgelaufen - oder das Modul ist nicht installiert (404).
 */
class NotJsonException(val httpStatus: Int) : Exception("Keine JSON-Antwort (HTTP $httpStatus)")

/**
 * Ein Schreibzugriff wurde gesendet, aber die Verbindung brach ab, bevor eine Antwort kam. Ob der Server
 * die Aenderung verarbeitet hat, ist unbekannt - der Nutzer muss nachsehen, bevor er sie wiederholt.
 */
/**
 * Vor webtrees sitzt eine fremde Anmeldung (SSO wie Authelia, Authentik, oauth2-proxy, Cloudflare Access):
 * 401/407, eine Umleitung auf einen anderen Host oder JSON, das nicht von api4webtrees stammt. Frueher las
 * die App solches JSON als Info mit api=0 und meldete "Modul zu alt" (Rueckmeldung 24.09.2026).
 */
class LoginWallException(val httpStatus: Int, val host: String = "") : Exception("Anmeldung vor webtrees (HTTP $httpStatus $host)")

class WriteInterruptedException(cause: IOException) : IOException(cause.message, cause)

/**
 * Client fuer das webtrees-Modul "api4webtrees".
 *
 * Drei Dinge, die am echten Server gemessen wurden (17.09.2026):
 *  1. Liegt webtrees in einem Unterordner, gehoert dieser in den route-Parameter:
 *     https://host/webtrees/index.php?route=/webtrees/module/...
 *  2. Ehrlicher User-Agent. Wer sich als Chrome/Safari ausgibt und noch kein Cookie hat,
 *     bekommt vom BadBotBlocker einen "Cookie check" (406). Accept-Language immer mitsenden.
 *  3. Fachliche Fehler kommen als HTTP 200 mit {"ok":false,...}, weil Webserver wie der der
 *     Synology bei 4xx/5xx den Antwortinhalt durch ihre eigene Fehlerseite ersetzen.
 *
 * Dazu seit 21.09.2026: webtrees benennt ein Modul nach seinem Ordner. Seit Modul 1.3.0 heisst es
 * _api4webtrees_, bis 1.2 hiess es _webtreesand-api_. Welcher Name gilt, stellt info() fest (404 unter dem
 * neuen -> der alte); die Antwort merkt sich die App, damit auch der Hintergrunddienst sie kennt.
 */
/**
 * @param prefs     eigene Ablage des Clients (gewaehlter Modulname), Datei "wtclient" wie vor der Aufteilung
 * @param cookies   Ablage der Sitzungs-Cookies, Datei "cookies"
 * @param userAgent ehrliche Kennung gegenueber dem Server, z. B. "wtAnd/1.16 (Android 15)"
 */
class WtClient(private val prefs: Ablage, cookies: Ablage, val userAgent: String) {

    val cookieJar = PersistentCookieJar(cookies)

    /** Fuer Lesezugriffe und Bilder. Darf bei Verbindungsproblemen still wiederholen (OkHttp-Standard) - Schreibzugriffe nicht, siehe writeHttp. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", Locale.getDefault().toLanguageTag() + ",en;q=0.5")
                    .build()
            )
        }
        // webtrees beantwortet fehlende Bilddateien mit einem SVG-Platzhalter ("404") - aber mit Status 200 und
        // einem Jahr Cache-Erlaubnis. Ohne diesen Eingriff merkt sich der Bild-Cache den Platzhalter, auch wenn
        // die Datei laengst hochgeladen ist.
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.header("Content-Type").orEmpty().startsWith("image/svg")) {
                response.newBuilder().header("Cache-Control", "no-store").build()
            } else {
                response
            }
        }
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Basis-URL ohne abschliessenden Schraegstrich, z. B. https://example.org/webtrees */
    var baseUrl: String = ""
        set(value) {
            field = normalizeBaseUrl(value)
        }

    private var csrf: String = ""


    /** Name des Moduls in der Route - siehe Klassenkommentar. */
    var module: String
        get() = prefs.getString("module", MODULE) ?: MODULE
        private set(value) = prefs.putString("module", value)

    // ── Lesen ────────────────────────────────────────────────────────

    /** Erster Aufruf jeder Sitzung: liefert CSRF-Token und Serverstand - und findet den Modulnamen, falls der bisherige 404 gibt. */
    suspend fun info(): Info {
        val info = try {
            get("Info", null, emptyMap(), Info.serializer())
        } catch (e: NotJsonException) {
            if (e.httpStatus != 404) throw e
            val other = if (module == MODULE) LEGACY_MODULE else MODULE
            val previous = module
            module = other
            try {
                get("Info", null, emptyMap(), Info.serializer())
            } catch (retry: NotJsonException) {
                module = previous
                throw if (retry.httpStatus == 404) e else retry
            }
        }
        // Jede Info-Antwort von api4webtrees traegt api >= 1 und den Modulstand. Fehlt beides, kam das JSON von
        // etwas anderem vor webtrees - meist einer SSO-Anmeldung.
        if (info.api == 0 && info.module.isEmpty()) throw LoginWallException(200)
        csrf = info.csrf

        return info
    }

    suspend fun individuals(tree: String, query: String, page: Int): PersonPage =
        get("Individuals", tree, mapOf("q" to query, "page" to page.toString()), PersonPage.serializer())

    /** relativeTo: Bezugsperson fuer die Verwandtschaftsangabe ("Urgrossmutter"); leer = eigene Person des Benutzers. */
    suspend fun individual(tree: String, xref: String, relativeTo: String = ""): IndividualDetail =
        get("Individual", tree, mapOf("xref" to xref, "relativeTo" to relativeTo), IndividualDetail.serializer())

    suspend fun mediaList(tree: String, page: Int): MediaPage =
        get("MediaList", tree, mapOf("page" to page.toString()), MediaPage.serializer())

    /** Eine Seite des ganzen Baums (ab API-Stufe 17). Die Personen tragen die Kurzfassung und famc/fams/facts/media in einem Objekt. */
    suspend fun export(tree: String, page: Int): ExportPage {
        val raw = get("Export", tree, mapOf("page" to page.toString()), ExportPageJson.serializer())
        return ExportPage(
            raw.lastChange, raw.page, raw.nextPage, raw.total,
            raw.individuals.map { e ->
                val links = json.decodeFromJsonElement(ExportLinks.serializer(), e)
                ExportIndividual(json.decodeFromJsonElement(Person.serializer(), e), links.famc, links.fams, links.facts, links.media)
            },
            raw.families,
        )
    }

    // ── Archiv: Modul "Sammlungen" (eigene Routen, nicht Teil von api4webtrees) ──

    /** Uebersicht des Archivs. Kein JSON (404-Seite von webtrees): das Modul fehlt. JSON-Fehler 403: kein Zugriff. */
    suspend fun archive(tree: String): ArchiveOverview =
        getRoute("/tree/$tree/archiv/api/sammlungen", emptyMap(), ArchiveOverview.serializer())

    /** Eine Seite einer Sammlung. typ nur fuer nicht eingebundene Medien (kategorie "__unlinked__"). */
    suspend fun collection(tree: String, kategorie: String, typ: String, page: Int, perPage: Int): CollectionPage =
        getRoute(
            "/tree/$tree/archiv/api/sammlung",
            mapOf("kategorie" to kategorie, "typ" to typ, "seite" to page.toString(), "pro_seite" to perPage.toString()),
            CollectionPage.serializer(),
        )

    /** Ein Foto ins Archiv legen: Datei plus Beschriftung, Modul Sammlungen ab Stufe 2. Braucht wie jeder POST das CSRF-Token. */
    suspend fun uploadArchive(tree: String, request: ArchiveUploadRequest, bytes: ByteArray, fileName: String, mime: String): ArchiveUploadResult {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("ordner", request.ordner)
            .addFormDataPart("beschreibung", request.beschreibung)
            .addFormDataPart("datum", request.datum)
            .addFormDataPart("personen", request.personen.joinToString(", "))
            .addFormDataPart("keywords", request.keywords.joinToString(", "))
            .addFormDataPart("sammlung", request.sammlung)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
            .build()

        return postRoute("/tree/$tree/archiv/api/hochladen", body, ArchiveUploadResult.serializer())
    }

    /** Ein Eintrag zu einer Datei im Medienordner (Modul Sammlungen ab Stufe 3) - fuer Fotos, die die App aus dem Baum kennt. */
    suspend fun archiveEntry(tree: String, pfad: String): EntryResult =
        getRoute("/tree/$tree/archiv/api/eintrag", mapOf("pfad" to pfad), EntryResult.serializer())

    /** Beschriftung eines Archivbildes in die Datei schreiben - nur Verwalter (Modul Sammlungen ab Stufe 2). */
    suspend fun writeExif(tree: String, pfad: String, request: ExifRequest): ExifWriteResult {
        val form = FormBody.Builder()
            .add("pfad", pfad)
            .add("beschreibung", request.beschreibung)
            .add("datum", request.datum)
            .add("personen", request.personen.joinToString(", "))
            .add("keywords", request.keywords.joinToString(", "))
            .build()

        return postRoute("/tree/$tree/archiv/api/exif", form, ExifWriteResult.serializer())
    }

    /** Aeltere Module kappen generations selbst (bis 1.8: 7, ab 1.9: 12) und nennen die gelieferte Tiefe; siblings ab Stufe 15. */
    suspend fun pedigree(tree: String, xref: String, generations: Int, siblings: Boolean = false): Pedigree =
        get("Pedigree", tree, mapOf("xref" to xref, "generations" to generations.toString()) + (if (siblings) mapOf("siblings" to "1") else emptyMap()), Pedigree.serializer())

    suspend fun descendants(tree: String, xref: String, generations: Int): Descendants =
        get("Descendants", tree, mapOf("xref" to xref, "generations" to generations.toString()), Descendants.serializer())

    suspend fun pending(tree: String): PendingList = get("Pending", tree, emptyMap(), PendingList.serializer())

    suspend fun bookmarks(tree: String): BookmarkList = get("Bookmarks", tree, emptyMap(), BookmarkList.serializer())

    suspend fun setBookmark(tree: String, xref: String, add: Boolean): BookmarkList =
        post("Bookmarks", tree, emptyMap(), jsonBody(BookmarkRequest.serializer(), BookmarkRequest(xref, add)), BookmarkList.serializer())

    suspend fun anniversaries(tree: String, days: Int): AnniversaryList =
        get("Anniversaries", tree, mapOf("days" to days.toString()), AnniversaryList.serializer())

    suspend fun tags(tree: String, type: String): TagList =
        get("Tags", tree, mapOf("type" to type), TagList.serializer())

    /** Nur fuer Bearbeiter; "Wien, Ö" sucht je Ebene wie die Vorschlaege der webtrees-Website. */
    suspend fun places(tree: String, query: String): PlaceList =
        get("Places", tree, mapOf("q" to query), PlaceList.serializer())

    // ── Anmelden ─────────────────────────────────────────────────────

    /**
     * webtrees-Login: erst Info (setzt das Sitzungs-Cookie und liefert das CSRF-Token - ohne
     * vorhandenes Cookie lehnt webtrees die Anmeldung ab), dann das normale Login-Formular.
     * Ob es geklappt hat, zeigt das anschliessende Info.
     */
    suspend fun login(user: String, password: String): Info {
        info()

        val form = FormBody.Builder()
            .add("_csrf", csrf)
            .add("username", user)
            .add("password", password)
            .build()

        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url("/login", emptyMap())).post(form).build()).execute().close()
        }

        return info()
    }

    /**
     * Koppeln: den Einmal-Code von der Seite "App" in webtrees einloesen. Danach ist diese Sitzung angemeldet,
     * ohne dass die App je ein Passwort gesehen hat.
     */
    suspend fun pair(code: String): PairResult {
        info()
        return post("Pair", null, emptyMap(), jsonBody(PairRequest.serializer(), PairRequest(code)), PairResult.serializer())
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(url("/logout", emptyMap())).post(FormBody.Builder().build()).build())
                    .execute().close()
            }
        }
        cookieJar.clear()
        csrf = ""
    }

    // ── Schreiben ────────────────────────────────────────────────────

    suspend fun saveFact(tree: String, xref: String, request: FactRequest): WriteResult =
        post("Fact", tree, mapOf("xref" to xref), jsonBody(FactRequest.serializer(), request))

    suspend fun deleteFact(tree: String, xref: String, factId: String): WriteResult =
        post("DeleteFact", tree, mapOf("xref" to xref), jsonBody(DeleteFactRequest.serializer(), DeleteFactRequest(factId)))

    /** Loescht den Datensatz mit der Logik von webtrees (Verweise werden entfernt, leere Familien mit geloescht). */
    suspend fun deleteRecord(tree: String, xref: String): WriteResult =
        post("DeleteRecord", tree, mapOf("xref" to xref), jsonBody(EmptyRequest.serializer(), EmptyRequest()))

    /** Loest nur die Verknuepfung zwischen Person und Familie - beide Datensaetze bleiben. */
    suspend fun unlink(tree: String, family: String, individual: String): WriteResult =
        post("Unlink", tree, emptyMap(), jsonBody(UnlinkRequest.serializer(), UnlinkRequest(family, individual)))

    /** Ausstehende Aenderungen annehmen oder verwerfen - eines Datensatzes oder (xref = null) des ganzen Baums. */
    suspend fun moderate(tree: String, xref: String?, accept: Boolean): ModerationResult {
        val params = if (xref == null) emptyMap() else mapOf("xref" to xref)

        return post(if (accept) "Accept" else "Reject", tree, params, jsonBody(EmptyRequest.serializer(), EmptyRequest()), ModerationResult.serializer())
    }

    suspend fun addIndividual(tree: String, request: AddIndividualRequest): WriteResult =
        post("AddIndividual", tree, emptyMap(), jsonBody(AddIndividualRequest.serializer(), request))

    suspend fun uploadMedia(tree: String, xref: String, bytes: ByteArray, fileName: String, mime: String, title: String): WriteResult {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("title", title)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
            .build()

        return post("Media", tree, mapOf("xref" to xref), body)
    }

    // ── Transport ────────────────────────────────────────────────────

    /** Adresse einer webtrees-Route. Der Pfad der Basis-URL (Unterordner) gehoert in den route-Parameter. */
    fun url(route: String, params: Map<String, String>): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull() ?: throw IOException("invalid address: $baseUrl")
        val basePath = base.encodedPath.trimEnd('/')

        return base.newBuilder()
            .addPathSegment("index.php")
            .addQueryParameter("route", basePath + route)
            .addQueryParameter("lang", apiLanguage())
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
    }

    /**
     * In dieser Sprache soll der Server antworten: die Sprache, in der die App gerade ihre eigenen Texte zeigt.
     * (Nicht einfach die Geraetesprache - auf einem franzoesischen Geraet spricht die App Deutsch, siehe strings.xml.)
     * Passt die Geraetesprache dazu, geht die volle Kennung mit (en-GB statt en), sonst nur die Sprache.
     */
    private fun apiLanguage(): String {
        val appLanguage = Texte.t(Res.string.api_language)
        val device = Locale.getDefault()

        return if (device.language == appLanguage) device.toLanguageTag() else appLanguage
    }

    private fun apiRoute(action: String, tree: String?): String =
        "/module/$module/$action" + if (tree != null) "/$tree" else ""

    /**
     * Fuer POSTs: KEINE stille Wiederholung. OkHttp wiederholt sonst eine Anfrage, deren Verbindung nach dem Senden
     * abbrach - hat der Server sie schon verarbeitet, entstuende das Ereignis doppelt. Teilt Verbindungen und Cookies mit http.
     */
    private val writeHttp: OkHttpClient = http.newBuilder().retryOnConnectionFailure(false).build()

    private suspend fun <T> get(action: String, tree: String?, params: Map<String, String>, deserializer: DeserializationStrategy<T>): T {
        val request = Request.Builder().url(url(apiRoute(action, tree), params)).build()

        return decode(execute(request), deserializer)
    }

    /** Wie get(), aber fuer eine beliebige webtrees-Route statt einer Aktion von api4webtrees. */
    private suspend fun <T> getRoute(route: String, params: Map<String, String>, deserializer: DeserializationStrategy<T>): T {
        val request = Request.Builder().url(url(route, params)).build()

        return decode(execute(request), deserializer)
    }

    private suspend fun post(action: String, tree: String, params: Map<String, String>, body: RequestBody): WriteResult =
        post(action, tree, params, body, WriteResult.serializer())

    private suspend fun <T> post(action: String, tree: String?, params: Map<String, String>, body: RequestBody, deserializer: DeserializationStrategy<T>): T =
        postUrl(url(apiRoute(action, tree), params), body, deserializer)

    /** POST an eine beliebige webtrees-Route (Modul Sammlungen) - dieselbe CSRF-Behandlung wie bei api4webtrees. */
    private suspend fun <T> postRoute(route: String, body: RequestBody, deserializer: DeserializationStrategy<T>): T =
        postUrl(url(route, emptyMap()), body, deserializer)

    private suspend fun <T> postUrl(target: HttpUrl, body: RequestBody, deserializer: DeserializationStrategy<T>): T {
        suspend fun attempt(): T {
            val request = Request.Builder()
                .url(target)
                .header("X-CSRF-TOKEN", csrf)
                .post(body)
                .build()

            val response = try {
                execute(request, writeHttp)
            } catch (e: IOException) {
                // Verbindung kam gar nicht zustande: nichts gesendet, normaler Fehler. Sonst: Ausgang unbekannt.
                if (e is ConnectException || e is UnknownHostException) throw e else throw WriteInterruptedException(e)
            }

            return decode(response, deserializer)
        }

        return try {
            if (csrf.isEmpty()) info()
            attempt()
        } catch (e: NotJsonException) {
            // Abgelaufenes CSRF-Token: webtrees leitet um statt JSON zu liefern. Token erneuern, einmal wiederholen.
            info()
            attempt()
        }
    }

    private suspend fun execute(request: Request, client: OkHttpClient = http): Pair<Int, String> = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val type = response.header("Content-Type").orEmpty()
            val text = response.body?.string().orEmpty()

            // webtrees selbst antwortet nie mit 401/407 - das tut eine vorgeschaltete Anmeldung (SSO, Basic-Auth, Proxy).
            if (response.code == 401 || response.code == 407) throw LoginWallException(response.code)

            if (!type.contains("json")) {
                // Umleitung auf einen anderen Host und dort kein JSON: die Anmeldeseite eines SSO-Dienstes.
                // (example.org -> www.example.org mit JSON-Antwort ist dagegen in Ordnung und kommt hier nicht an.)
                val finalHost = response.request.url.host
                if (!finalHost.equals(request.url.host, ignoreCase = true)) throw LoginWallException(response.code, finalHost)
                throw NotJsonException(response.code)
            }

            response.code to text
        }
    }

    private fun <T> decode(response: Pair<Int, String>, deserializer: DeserializationStrategy<T>): T {
        val element = json.parseToJsonElement(response.second)

        if (element is JsonObject && element["ok"]?.jsonPrimitive?.booleanOrNull == false) {
            throw ApiException(
                element["error"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                element["status"]?.jsonPrimitive?.intOrNull,
            )
        }

        return json.decodeFromJsonElement(deserializer, element)
    }

    private fun <T> jsonBody(serializer: SerializationStrategy<T>, value: T): RequestBody =
        json.encodeToString(serializer, value).toRequestBody("application/json".toMediaType())

    companion object {
        /** Modul ab 1.3.0 (Ordner api4webtrees) */
        const val MODULE = "_api4webtrees_"

        /** Modul bis 1.2 (Ordner webtreesand-api) */
        const val LEGACY_MODULE = "_webtreesand-api_"

        /**
         * Unverschluesselte Adresse (http://)? Android blockiert Klartext ohnehin - die App lehnt sie
         * schon bei der Eingabe ab, damit der Nutzer einen verstaendlichen Hinweis statt eines
         * Systemfehlers bekommt.
         */
        fun isCleartext(input: String): Boolean = input.trim().startsWith("http://", ignoreCase = true)

        /** "example.org/webtrees/" -> "https://example.org/webtrees" */
        fun normalizeBaseUrl(input: String): String {
            var url = input.trim()

            if (url.isEmpty()) return ""

            url = when {
                url.startsWith("https://", ignoreCase = true) -> "https://" + url.drop(8)
                url.startsWith("http://", ignoreCase = true) -> "http://" + url.drop(7)
                else -> "https://$url"
            }

            // Wer die Adresse aus dem Browser kopiert, bringt oft index.php?route=... mit.
            url = url.substringBefore("/index.php").substringBefore('?')

            return url.trimEnd('/')
        }
    }
}
