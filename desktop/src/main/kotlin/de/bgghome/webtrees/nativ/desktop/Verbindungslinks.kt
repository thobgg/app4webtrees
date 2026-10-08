package de.bgghome.webtrees.nativ.desktop

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import kotlin.concurrent.thread

/*
 * Verbinden-Links aus webtrees (wtwin://connect?… bzw. wttux://connect?…, api4webtrees ab 1.9.4): Das Programm meldet
 * sich beim Start selbst als Empfaenger an - ohne Installer-Aenderung und ohne Administratorrechte. Oeffnet der Browser
 * einen Link, startet ein zweites Programm; das reicht ihn an das laufende Fenster weiter und beendet sich.
 */

/** Nur ein Fenster: die erste Instanz haelt eine Dateisperre und lauscht auf einem Port nur fuer diesen Rechner. */
internal class Einzelinstanz(ordner: File) {
    private val portDatei = File(ordner, "instanz.port")
    private val sperrDatei = File(ordner, "instanz.lock")
    @Suppress("unused") private var sperre: FileLock? = null

    /**
     * true: dies ist das erste Fenster. false: ein anderes laeuft schon und hat [link] (oder nur "nach vorn")
     * bekommen - dann beendet sich dieses. Geht beim Weiterreichen etwas schief, startet es lieber doch.
     */
    fun erste(link: String?): Boolean {
        sperre = runCatching { RandomAccessFile(sperrDatei, "rw").channel.tryLock() }.getOrNull()
        if (sperre != null) return true
        val port = runCatching { portDatei.readText().trim().toInt() }.getOrNull() ?: return true
        return runCatching {
            Socket(InetAddress.getLoopbackAddress(), port).use { it.getOutputStream().write(((link ?: "") + "\n").toByteArray()) }
            false
        }.getOrDefault(true)
    }

    /** Nachrichten zweiter Instanzen: eine Zeile, leer = nur nach vorn holen. */
    fun lauschen(onNachricht: (String) -> Unit) {
        val server = runCatching { ServerSocket(0, 5, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        runCatching { portDatei.writeText(server.localPort.toString()) }
        thread(isDaemon = true, name = "einzelinstanz") {
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    s.use {
                        it.soTimeout = 2000
                        val zeile = it.getInputStream().bufferedReader().readLine().orEmpty().take(1000)
                        onNachricht(zeile)
                    }
                }
            }
        }
    }
}

/**
 * Meldet das installierte Programm fuer sein Schema an (wtwin bzw. wttux). Nur aus dem Paket heraus - aus Gradle
 * (`:desktop:run`) laeuft java, dann bleibt alles, wie es ist. Laeuft im Hintergrund und darf scheitern.
 */
internal fun schemaAnmelden(appName: String) {
    val programm = ProcessHandle.current().info().command().orElse(null) ?: return
    val name = File(programm).name.lowercase()
    thread(isDaemon = true, name = "schema") {
        runCatching {
            when {
                name == "wtwin.exe" -> windows(programm)
                name == "wttux" && appName == "wtTux" -> linux(programm)
            }
        }
    }
}

/**
 * Windows: Schluessel HKCU\Software\Classes\wtwin ueber eine .reg-Datei und `reg import`. Nicht per `reg add`: dessen
 * Wert `"C:\…\wtWin.exe" "%1"` traegt Anfuehrungszeichen im Argument, und Java (Temurin 21.0.2 und neuer, Vorgabe
 * jdk.lang.Process.allowAmbiguousCommands=false) weist so ein Argument ab ("Malformed argument has embedded quote").
 * Darum fehlte shell\open\command bisher auf jedem Windows-PC, nur das Schema selbst stand da (Befund eines Testers,
 * 08.10.2026). Der Installer traegt dasselbe seit 1.42 ein; hier bleibt es fuer Programme, die anders hingekommen sind.
 */
private fun windows(programm: String) {
    val befehl = "\"$programm\" \"%1\""
    // Schon eingetragen? Dann nichts anfassen.
    val vorhanden = runCatching {
        val p = ProcessBuilder("reg", "query", "HKCU\\Software\\Classes\\wtwin\\shell\\open\\command", "/ve").redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText(); p.waitFor(); text
    }.getOrDefault("")
    if (vorhanden.contains(befehl)) return
    val datei = File(System.getProperty("java.io.tmpdir"), "wtwin-schema.reg")
    try {
        // regedit verlangt bei Unicode UTF-16LE mit Byte-Order-Mark FF FE (Java schreibt bei "UTF-16" FE FF).
        datei.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + regDateiText(programm).toByteArray(Charsets.UTF_16LE))
        ProcessBuilder("reg", "import", datei.path).redirectErrorStream(true).start().waitFor()
    } finally {
        datei.delete()
    }
}

/** Inhalt der .reg-Datei fuer das Schema wtwin://; Backslash und Anfuehrungszeichen im Pfad sind zu maskieren. */
internal fun regDateiText(programm: String): String {
    val exe = programm.replace("\\", "\\\\").replace("\"", "\\\"")
    return listOf(
        "Windows Registry Editor Version 5.00",
        "",
        "[HKEY_CURRENT_USER\\Software\\Classes\\wtwin]",
        "@=\"URL:wtWin\"",
        "\"URL Protocol\"=\"\"",
        "",
        "[HKEY_CURRENT_USER\\Software\\Classes\\wtwin\\DefaultIcon]",
        "@=\"\\\"$exe\\\",0\"",
        "",
        "[HKEY_CURRENT_USER\\Software\\Classes\\wtwin\\shell\\open\\command]",
        "@=\"\\\"$exe\\\" \\\"%1\\\"\"",
        "",
    ).joinToString("\r\n")
}

private fun linux(programm: String) {
    val daten = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("user.home"), ".local/share")
    val ordner = File(daten, "applications").apply { mkdirs() }
    val datei = File(ordner, "wttux-url.desktop")
    val inhalt = """
        [Desktop Entry]
        Type=Application
        Name=wtTux
        Exec="$programm" %u
        NoDisplay=true
        MimeType=x-scheme-handler/wttux;
    """.trimIndent() + "\n"
    if (datei.takeIf { it.exists() }?.readText() == inhalt) return
    datei.writeText(inhalt)
    ProcessBuilder("xdg-mime", "default", datei.name, "x-scheme-handler/wttux").redirectErrorStream(true).start().waitFor()
    runCatching { ProcessBuilder("update-desktop-database", ordner.path).redirectErrorStream(true).start().waitFor() }
}
