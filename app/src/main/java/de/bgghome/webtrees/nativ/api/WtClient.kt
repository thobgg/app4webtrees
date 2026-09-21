package de.bgghome.webtrees.nativ.api

import android.content.Context
import de.bgghome.webtrees.nativ.BuildConfig
import de.bgghome.webtrees.nativ.R
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
 */
class WtClient(private val context: Context) {

    val cookieJar = PersistentCookieJar(context)

    /** Fuer Lesezugriffe und Bilder. Darf bei Verbindungsproblemen still wiederholen (OkHttp-Standard) - Schreibzugriffe nicht, siehe writeHttp. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
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

    // ── Lesen ────────────────────────────────────────────────────────

    suspend fun info(): Info = get("Info", null, emptyMap(), Info.serializer()).also { csrf = it.csrf }

    suspend fun individuals(tree: String, query: String, page: Int): PersonPage =
        get("Individuals", tree, mapOf("q" to query, "page" to page.toString()), PersonPage.serializer())

    /** relativeTo: Bezugsperson fuer die Verwandtschaftsangabe ("Urgrossmutter"); leer = eigene Person des Benutzers. */
    suspend fun individual(tree: String, xref: String, relativeTo: String = ""): IndividualDetail =
        get("Individual", tree, mapOf("xref" to xref, "relativeTo" to relativeTo), IndividualDetail.serializer())

    suspend fun mediaList(tree: String, page: Int): MediaPage =
        get("MediaList", tree, mapOf("page" to page.toString()), MediaPage.serializer())

    suspend fun pedigree(tree: String, xref: String, generations: Int): Pedigree =
        get("Pedigree", tree, mapOf("xref" to xref, "generations" to generations.toString()), Pedigree.serializer())

    suspend fun descendants(tree: String, xref: String, generations: Int): Descendants =
        get("Descendants", tree, mapOf("xref" to xref, "generations" to generations.toString()), Descendants.serializer())

    suspend fun pending(tree: String): PendingList = get("Pending", tree, emptyMap(), PendingList.serializer())

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
        val appLanguage = context.getString(R.string.api_language)
        val device = Locale.getDefault()

        return if (device.language == appLanguage) device.toLanguageTag() else appLanguage
    }

    private fun apiRoute(action: String, tree: String?): String =
        "/module/$MODULE/$action" + if (tree != null) "/$tree" else ""

    /**
     * Fuer POSTs: KEINE stille Wiederholung. OkHttp wiederholt sonst eine Anfrage, deren Verbindung nach dem Senden
     * abbrach - hat der Server sie schon verarbeitet, entstuende das Ereignis doppelt. Teilt Verbindungen und Cookies mit http.
     */
    private val writeHttp: OkHttpClient = http.newBuilder().retryOnConnectionFailure(false).build()

    private suspend fun <T> get(action: String, tree: String?, params: Map<String, String>, deserializer: DeserializationStrategy<T>): T {
        val request = Request.Builder().url(url(apiRoute(action, tree), params)).build()

        return decode(execute(request), deserializer)
    }

    private suspend fun post(action: String, tree: String, params: Map<String, String>, body: RequestBody): WriteResult =
        post(action, tree, params, body, WriteResult.serializer())

    private suspend fun <T> post(action: String, tree: String?, params: Map<String, String>, body: RequestBody, deserializer: DeserializationStrategy<T>): T {
        suspend fun attempt(): T {
            val request = Request.Builder()
                .url(url(apiRoute(action, tree), params))
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

            if (!type.contains("json")) {
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
        const val MODULE = "_webtreesand-api_"
        val USER_AGENT = "wtAnd/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.RELEASE})"

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
