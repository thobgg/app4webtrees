package de.bgghome.webtrees.nativ.lokal

import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Stufe 4 (27.09.2026): webtrees auf diesem PC, ohne sichtbaren Server. wtWin/wtTux startet das mitgelieferte PHP
 * als Kindprozess (`php -S`, nur 127.0.0.1) und beendet es wieder. Die Daten liegen im Benutzerordner, nicht im
 * Programmverzeichnis - webtrees aktualisiert sich selbst und muss dort schreiben.
 */
object LokalOrte {
    /** ~/.local/share/app4webtrees bzw. %LOCALAPPDATA%\app4webtrees (Tests: -Dwtand.lokal=…). */
    val basis: File get() = System.getProperty("wtand.lokal")?.let(::File) ?: standard
    private val standard: File by lazy {
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        val ordner = when {
            windows -> System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
            else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
        } ?: File(System.getProperty("user.home"), if (windows) "AppData/Local" else ".local/share")
        File(ordner, "app4webtrees")
    }
    val webtrees get() = File(basis, "webtrees")
    val router get() = File(basis, "router.php")
    val pidDatei get() = File(basis, "php.pid")
    val protokoll get() = File(basis, "php.log")
    /** Was beim Uebernehmen einer GEDCOM-Datei geschah: Zeichensatz, uebersprungene Datensaetze, Abbruchgrund. */
    val importProtokoll get() = File(basis, "import.log")
    /** Eingerichtet ist webtrees, sobald der Assistent die config.ini.php geschrieben hat. */
    val eingerichtet get() = File(webtrees, "data/config.ini.php").isFile
}

/**
 * Das mitgelieferte PHP: im Paket unter `resources/php` (compose.application.resources.dir, siehe
 * desktop/build.gradle.kts). Zum Entwickeln WTAND_PHP=/pfad/zu/php.
 */
fun mitgeliefertesPhp(): File? {
    System.getenv("WTAND_PHP")?.takeIf { it.isNotBlank() }?.let { return File(it).takeIf(File::canExecute) }
    val res = System.getProperty("compose.application.resources.dir") ?: return null
    val exe = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "php.exe" else "php"
    val paket = File(res, "php/$exe").takeIf { it.isFile } ?: return null
    if (paket.canExecute()) return paket
    // Beim Paketbau (Compose-Ressourcen, .deb unter /opt) geht das Ausfuehrungsrecht verloren und liesse sich dort
    // nicht nachsetzen: dann einmalig in den eigenen Datenordner kopieren und dort ausfuehrbar machen.
    val kopie = File(LokalOrte.basis, "php/$exe")
    if (!kopie.isFile || kopie.length() != paket.length() || kopie.lastModified() < paket.lastModified()) {
        kopie.parentFile.mkdirs()
        paket.copyTo(kopie, overwrite = true)
    }
    kopie.setExecutable(true, true)
    return kopie.takeIf { it.canExecute() }
}

class LokalerServer(private val php: File, private val webtrees: File = LokalOrte.webtrees) {
    private var prozess: Process? = null
    var port: Int = 0
        private set
    val adresse get() = "http://127.0.0.1:$port/"
    val laeuft get() = prozess?.isAlive == true

    /** Startet PHP und wartet, bis webtrees antwortet. Wirft bei Fehlschlag mit dem Ende des PHP-Protokolls. */
    @Synchronized
    fun starten(wunschPort: Int = 0, wartenMs: Long = 20_000): String {
        if (laeuft) return adresse
        require(File(webtrees, "index.php").isFile) { "webtrees fehlt in $webtrees" }
        LokalOrte.basis.mkdirs()
        altenBeenden()
        LokalOrte.router.writeText(ROUTER_PHP)
        port = if (wunschPort in 1024..65535 && frei(wunschPort)) wunschPort else freierPort()
        val befehl = listOf(
            php.absolutePath,
            // Das mitgelieferte PHP hat keine php.ini: alles Noetige hier. Grosse GEDCOM-Dateien und Fotos brauchen Luft.
            "-d", "memory_limit=1024M",
            "-d", "max_execution_time=0",
            "-d", "upload_max_filesize=256M",
            "-d", "post_max_size=256M",
            "-d", "display_errors=0",
            "-d", "log_errors=1",
            "-d", "error_log=${LokalOrte.protokoll.absolutePath}",
            "-d", "session.save_path=${File(webtrees, "data").absolutePath}",
            "-d", "date.timezone=${java.util.TimeZone.getDefault().id}",
            "-S", "127.0.0.1:$port",
            "-t", webtrees.absolutePath,
            LokalOrte.router.absolutePath,
        )
        val p = ProcessBuilder(befehl)
            .directory(webtrees)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(LokalOrte.protokoll))
            .start()
        prozess = p
        LokalOrte.pidDatei.writeText(p.pid().toString())
        Runtime.getRuntime().addShutdownHook(Thread { beenden() })

        val ende = System.currentTimeMillis() + wartenMs
        var versuch = 0
        while (System.currentTimeMillis() < ende) {
            if (!p.isAlive) break
            versuch++
            val fehler = antwortFehler() ?: return adresse
            // Die ersten Versuche scheitern ueblich, solange PHP noch hochfaehrt - erst ab dem dritten mitschreiben
            if (versuch >= 3) LokalProtokoll.schreiben("Startpruefung, Versuch $versuch: $fehler")
            Thread.sleep(150)
        }
        LokalProtokoll.schreiben(if (!p.isAlive) "PHP beendet sich gleich nach dem Start (Port $port)" else "Startpruefung: keine Antwort nach ${wartenMs / 1000} s (Port $port)")
        beenden()
        val rest = runCatching { LokalOrte.protokoll.readLines().takeLast(15).joinToString("\n") }.getOrDefault("")
        error("PHP startet nicht (Port $port).\n$rest")
    }

    @Synchronized
    fun beenden() {
        prozess?.let { p ->
            p.destroy()
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        prozess = null
        LokalOrte.pidDatei.delete()
    }

    /**
     * Antwortet webtrees selbst? Eine CSS-Datei reichte nicht: die liefert der Server, ohne PHP auszufuehren - so galt
     * ein Server als bereit, der jede echte Anfrage abbrach (Issue 9). Gefragt wird Info von api4webtrees (200, auch
     * ohne Anmeldung); jede HTTP-Antwort zeigt, dass PHP laeuft - nur ein Abbruch oder keine Antwort zaehlt als Fehler.
     * (Erst index.php: das antwortet vor der Anmeldung mit 404, was im Protokoll wie ein Fehler aussah.)
     */
    /** null, wenn webtrees antwortet, sonst der Grund (fuer wtwin.log). */
    private fun antwortFehler(): String? = try {
        val c = URI("${adresse}index.php?route=%2Fmodule%2F_api4webtrees_%2FInfo").toURL().openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 500; c.readTimeout = 10_000
        try { c.responseCode.let { if (it in 100..599) null else "HTTP $it" } } finally { c.disconnect() }
    } catch (e: Exception) {
        e.toString()
    }

    /** Ist wtWin abgestuerzt (unter Windows ueberlebt das Kind dann), laeuft noch ein altes PHP - weg damit. */
    private fun altenBeenden() {
        val pid = runCatching { LokalOrte.pidDatei.readText().trim().toLong() }.getOrNull() ?: return
        ProcessHandle.of(pid).ifPresent { h ->
            val cmd = h.info().command().orElse("")
            if (cmd.endsWith(php.name)) { h.destroy(); h.onExit().get(3, TimeUnit.SECONDS) }
        }
        LokalOrte.pidDatei.delete()
    }

    companion object {
        fun freierPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        internal fun frei(port: Int) = runCatching { ServerSocket(port, 1, InetAddress.getLoopbackAddress()).close() }.isSuccess

        /**
         * PFLICHT: `php -S` kennt keine .htaccess. Ohne diese Sperre waere data/ (die SQLite-Datei, config.ini.php)
         * abrufbar. Vergleich ueber realpath, damit auch Windows-Schreibweisen wie /DATA./ oder /data%20/ nicht
         * durchrutschen. Ausfuehren darf nur index.php im Wurzelordner (vendor/ bringt eigene PHP-Skripte mit).
         */
        val ROUTER_PHP = """
            |<?php
            |// wtWin/wtTux: Router fuer php -S (von wtWin geschrieben, wird bei jedem Start ersetzt).
            |${'$'}root = realpath(${'$'}_SERVER['DOCUMENT_ROOT']);
            |${'$'}pfad = rawurldecode((string) parse_url(${'$'}_SERVER['REQUEST_URI'], PHP_URL_PATH));
            |${'$'}norm = static fn (string ${'$'}p): string => rtrim(strtolower(str_replace('\\', '/', ${'$'}p)), '/') . '/';
            |${'$'}verboten = static function () { http_response_code(403); header('Content-Type: text/plain'); echo "403\n"; return true; };
            |if (${'$'}root === false || str_contains(${'$'}pfad, "\0") || str_contains(${'$'}pfad, '..')) { return ${'$'}verboten(); }
            |// Jeder Pfadteil, der mit "data" beginnt oder mit einem Punkt (.htaccess, .git), ist tabu - auch wenn es ihn nicht gibt.
            |foreach (explode('/', str_replace('\\', '/', ${'$'}pfad)) as ${'$'}teil) {
            |    ${'$'}t = strtolower(rtrim(${'$'}teil, " ."));
            |    if (${'$'}t === 'data' || (${'$'}teil !== '' && ${'$'}teil[0] === '.')) { return ${'$'}verboten(); }
            |}
            |${'$'}datei = realpath(${'$'}root . ${'$'}pfad);
            |if (${'$'}datei === false) { return false; }
            |${'$'}d = ${'$'}norm(${'$'}datei);
            |if (!str_starts_with(${'$'}d, ${'$'}norm(${'$'}root))) { return ${'$'}verboten(); }
            |${'$'}daten = realpath(${'$'}root . '/data');
            |if (${'$'}daten !== false && str_starts_with(${'$'}d, ${'$'}norm(${'$'}daten))) { return ${'$'}verboten(); }
            |if (is_dir(${'$'}datei)) { return ${'$'}d === ${'$'}norm(${'$'}root) ? false : ${'$'}verboten(); }
            |if (preg_match('/\.(php|phtml|phar|inc|ini|sqlite|db|log)[\s.]*${'$'}/i', ${'$'}datei) && ${'$'}d !== ${'$'}norm(${'$'}root . '/index.php')) { return ${'$'}verboten(); }
            |return false;
            |""".trimMargin()

        /** Entpackt eine ZIP (webtrees, api4webtrees) - ohne Pfade ausserhalb des Ziels (Zip-Slip). */
        fun entpacken(zip: File, ziel: File, ohneOberordner: Boolean = true) {
            val zielPfad = ziel.canonicalFile.toPath()
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                generateSequence { zin.nextEntry }.forEach { e ->
                    val name = if (ohneOberordner) e.name.substringAfter('/', "") else e.name
                    if (name.isEmpty()) return@forEach
                    val f = File(ziel, name)
                    require(f.canonicalFile.toPath().startsWith(zielPfad)) { "Unzulaessiger Pfad in ${zip.name}: ${e.name}" }
                    if (e.isDirectory) f.mkdirs() else { f.parentFile.mkdirs(); f.outputStream().use { zin.copyTo(it) } }
                }
            }
        }
    }
}
