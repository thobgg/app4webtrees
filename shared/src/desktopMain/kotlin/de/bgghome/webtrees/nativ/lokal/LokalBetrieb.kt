package de.bgghome.webtrees.nativ.lokal

import de.bgghome.webtrees.nativ.DesktopPlattform
import de.bgghome.webtrees.nativ.api.WtClient
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Stammbaum auf diesem PC (Stufe 4): haelt den PHP-Server fuer die Laufzeit von wtWin. Der Rest des Programms merkt
 * davon nichts - fuer ihn ist das ein webtrees unter http://127.0.0.1:<port>/, bei dem man schon angemeldet ist.
 */
object LokalBetrieb : WtClient.LokalerDienst {
    @Volatile private var server: LokalerServer? = null
    private var letzterNeustart = 0L

    override fun betrifft(host: String, port: Int): Boolean = host == "127.0.0.1" && port == server?.port

    /**
     * Antwortet der Server nicht mehr (abgestuerzt, haengt an einer Anfrage): beenden und unter demselben Port neu
     * starten - die Anmeldung bleibt, die Sitzung liegt in webtrees/data. Mehrere Anfragen, die zugleich scheitern,
     * loesen nur einen Neustart aus.
     */
    @Synchronized
    override fun neustarten(): Boolean {
        val s = server ?: return false
        val jetzt = System.currentTimeMillis()
        if (jetzt - letzterNeustart < 10_000) return s.laeuft
        letzterNeustart = jetzt
        val port = s.port
        LokalProtokoll.schreiben("Server antwortet nicht – Neustart auf Port $port")
        s.beenden()
        return runCatching { s.starten(wunschPort = port); s.port == port }
            .onFailure { LokalProtokoll.schreiben("Neustart gescheitert: ${it.message?.lineSequence()?.firstOrNull()}") }
            .getOrDefault(false)
    }

    override fun protokoll(text: String) = LokalProtokoll.schreiben(text)

    /** webtrees-ZIP und api4webtrees-ZIP liegen im Paket unter resources/webtrees (desktop/build.gradle.kts). */
    private fun paketDatei(praefix: String, env: String): File? {
        System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return File(it).takeIf(File::isFile) }
        val res = System.getProperty("compose.application.resources.dir") ?: return null
        return File(res, "webtrees").listFiles()?.filter { it.name.startsWith(praefix) && it.name.endsWith(".zip") }?.maxByOrNull { it.name }
    }
    private fun webtreesZip() = paketDatei("webtrees-", "WTAND_WEBTREES_ZIP")
    private fun apiZip() = paketDatei("api4webtrees-", "WTAND_API_ZIP")
    /** Modul Sammlungen (Archiv): mitgeliefert, damit Fotos und Dokumente nicht am Baum haengen muessen. */
    private fun sammlungenZip() = paketDatei("sammlungen-", "WTAND_SAMMLUNGEN_ZIP")

    /** Bringt dieses Paket alles mit, um einen Stammbaum auf dem PC anzulegen? */
    val verfuegbar: Boolean get() = mitgeliefertesPhp() != null && webtreesZip() != null

    /** Fuer "Ueber": die mitgelieferte webtrees-Version ("2.2.6"), oder null, wenn der Weg fehlt. */
    fun webtreesVersion(): String? =
        if (verfuegbar) webtreesZip()?.name?.removePrefix("webtrees-")?.removeSuffix(".zip") else null

    /** Fuer "Ueber", wenn der Weg fehlt: was im Paket nicht gefunden wurde und wo gesucht wurde. */
    fun fehlt(): String = listOfNotNull(
        "PHP".takeIf { runCatching { mitgeliefertesPhp() }.getOrNull() == null },
        "webtrees".takeIf { webtreesZip() == null },
    ).joinToString() + " – " + (System.getProperty("compose.application.resources.dir") ?: "-")

    /** Das Protokoll, auf das eine Fehlermeldung verweist. */
    val protokoll: String get() = LokalOrte.protokoll.absolutePath +
        (if (LokalOrte.importProtokoll.isFile) " (+ ${LokalOrte.importProtokoll.name})" else "")

    fun istLokal(baseUrl: String): Boolean = baseUrl.startsWith("http://127.0.0.1:")

    /**
     * "Neuen Stammbaum auf diesem PC anlegen", mit [gedcom] aus einer GEDCOM-Datei - blockiert (Auspacken, Datenbank anlegen), also nicht auf dem
     * Hauptthread. Danach zeigt [LokalerZugang] auf den laufenden Server; anmelden macht der Aufrufer.
     */
    fun anlegen(titel: String, gedcom: File? = null, neu: Boolean = false, schritt: (String) -> Unit): Pair<String, LokalerZugang> {
        server?.beenden()
        val (s, z) = einrichtung().einrichten(titel, gedcom, neu = neu, schritt = schritt)
        server = s
        return s.adresse to z
    }

    private fun einrichtung(): LokaleEinrichtung {
        val php = checkNotNull(mitgeliefertesPhp()) { "PHP fehlt im Paket" }
        val zip = checkNotNull(webtreesZip()) { "webtrees fehlt im Paket" }
        return LokaleEinrichtung(php, zip, apiZip(), sammlungenZip())
    }

    /** Die Baeume auf diesem PC (Datei › Stammbaum auf diesem PC › Stammbäume verwalten). Blockiert kurz. */
    fun baeume(): List<LokalerBaum> = einrichtung().baeumeListe()

    fun umbenennen(name: String, titel: String) = einrichtung().baumTitel(name, titel)

    /**
     * Baum loeschen - nie den letzten. War es der gemerkte, zeigt der Zugang danach auf den groessten der uebrigen,
     * der auch Standard in webtrees wird. Liefert den Namen, der jetzt gemerkt ist.
     */
    fun loeschen(name: String): String {
        val e = einrichtung()
        val zugang = checkNotNull(LokalerZugang.laden()) { "Kein Stammbaum auf diesem PC" }
        val uebrig = e.baeumeListe().filter { it.name != name }
        check(uebrig.isNotEmpty()) { "Der letzte Stammbaum kann nicht gelöscht werden" }
        e.baumLoeschen(name, zugang.benutzer)
        return nachAufraeumen(e, zugang, uebrig)
    }

    /** Zugang und webtrees-Standard auf einen vorhandenen Baum richten, falls der gemerkte weg ist. */
    private fun nachAufraeumen(e: LokaleEinrichtung, zugang: LokalerZugang, uebrig: List<LokalerBaum>): String {
        if (uebrig.isEmpty() || uebrig.any { it.name == zugang.baum }) return zugang.baum
        val ziel = uebrig.maxBy { it.personen }.name
        (LokalerZugang.laden() ?: zugang).copy(baum = ziel).sichern()
        runCatching { e.standardBaum(ziel) }
        return ziel
    }

    /**
     * Beim Programmstart, vor dem ersten Blick auf den Server: war zuletzt der Stammbaum auf diesem PC gewaehlt,
     * PHP starten und still anmelden. Scheitert das, bleibt es beim normalen Weg (Startbildschirm mit Fehler).
     */
    fun beimStart(plattform: DesktopPlattform) {
        val settings = plattform.settings
        if (!istLokal(settings.baseUrl)) return
        val zugang = LokalerZugang.laden() ?: return
        val php = mitgeliefertesPhp() ?: return
        runCatching {
            // Neue Programmversion, neue Module: vor dem Start auffrischen (nur wenn sich das Paket geaendert hat)
            LokaleEinrichtung.moduleAuffrischen(LokalOrte.webtrees, apiZip(), sammlungenZip())
            val s = LokalerServer(php).also { server = it }
            s.starten(wunschPort = zugang.port)
            LokalProtokoll.schreiben("Start: Server bereit auf Port ${s.port}")
            if (s.port != zugang.port) zugang.copy(port = s.port).sichern()
            // Leere Reste alter Importversuche weg; zeigte die Merkung auf so einen, den Baum mit den Daten oeffnen
            runCatching {
                val e = einrichtung()
                val vorher = settings.tree
                val uebrig = e.aufraeumen(zugang.benutzer, zugang.baum)
                val gemerkt = nachAufraeumen(e, zugang, uebrig)
                if (vorher.isNotEmpty() && uebrig.none { it.name == vorher }) settings.tree = gemerkt
            }.onFailure { System.err.println("Lokaler Stammbaum, Aufräumen: ${it.message}") }
            settings.baseUrl = s.adresse.trimEnd('/')
            plattform.client.baseUrl = settings.baseUrl
            runBlocking { plattform.client.login(zugang.benutzer, zugang.passwort) }
            settings.userName = zugang.benutzer
            LokalProtokoll.schreiben("Start: angemeldet")
        }.onFailure {
            System.err.println("Lokaler Stammbaum: ${it.message}")
            LokalProtokoll.schreiben("Start gescheitert: $it")
        }
    }

    fun beenden() {
        server?.beenden()
        server = null
    }
}
