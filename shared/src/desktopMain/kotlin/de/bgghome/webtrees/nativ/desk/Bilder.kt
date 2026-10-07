package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Desktop
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/*
 * Bilder fuer Buecher und Tafeln (07.10.2026, Issue #7): webtrees rechnet jedes Vorschaubild beim ersten Abruf auf dem
 * Server neu - bei 12 000 Personen dauert ein Familienbuch Stunden. Darum holt wtWin jedes Bild nur einmal und behaelt es
 * auf der Platte: ~/.cache/app4webtrees/medien/<server>/<baum>/<pfad wie in webtrees>.vorschau.jpg. Wer den Medienordner
 * von webtrees ohnehin auf dem PC hat (Hochladen per FTP), kann ihn je Baum angeben; dann werden die Originale von dort
 * gelesen und selbst verkleinert, ohne Server. Reihenfolge: Medienordner auf dem PC -> Zwischenspeicher -> Server.
 * webtrees bleibt die einzige Quelle; der Zwischenspeicher ist nur eine Kopie und darf jederzeit weg.
 */
object Bilder {
    /** Ein Bild: die Vorschau-Adresse der API und, wenn bekannt, der Pfad der Datei im Medienordner des Baums. */
    class Quelle(val url: String, val pfad: String? = null)

    /** Woher die Bilder eines Laufs kamen - fuer die Fortschrittszeile und die Zusammenfassung. */
    class Zaehler {
        val lokal = AtomicInteger(); val cache = AtomicInteger(); val server = AtomicInteger(); val fehlt = AtomicInteger()
        val geladen get() = lokal.get() + cache.get() + server.get() + fehlt.get()
        fun text(gesamt: Int): String = Texte.t(Res.string.desk_book_progress_images, geladen, gesamt, lokal.get(), cache.get(), server.get())
    }

    /** Lange Kante, auf die Originale aus dem Medienordner verkleinert werden (die API liefert 200 px, Buecher drucken klein). */
    private const val LANGE_KANTE = 400

    private fun schluessel(server: String): String =
        server.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/').replace(Regex("[^a-z0-9.-]"), "_").ifBlank { "server" }

    /** Der Cache der App; ohne laufendes Fenster (Werkzeuge, Tests) derselbe Ort wie in DesktopPlattform. */
    private fun basisOrdner(): File = runCatching { Desktop.plattform.cacheOrdner }.getOrElse {
        val basis = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), ".cache")
        File(basis, "app4webtrees")
    }

    fun cacheOrdner(server: String, tree: String): File = File(basisOrdner(), "medien/${schluessel(server)}/${tree.replace(Regex("[^A-Za-z0-9._-]"), "_")}")

    private fun ordnerSchluessel(server: String, tree: String) = "medien_ordner:${schluessel(server)}/$tree"

    /**
     * Der Medienordner auf diesem PC fuer diesen Baum: eingestellt und vorhanden - oder beim Stammbaum auf diesem PC der
     * Medienordner des lokalen webtrees, ohne dass jemand etwas eintragen muss.
     */
    fun medienOrdner(server: String, tree: String): File? =
        DeskLayout.prefs.getString(ordnerSchluessel(server, tree), null)?.takeIf(String::isNotBlank)?.let(::File)?.takeIf { it.isDirectory }
            ?: if (de.bgghome.webtrees.nativ.lokal.LokalBetrieb.istLokal(server)) File(de.bgghome.webtrees.nativ.lokal.LokalOrte.webtrees, "data/media").takeIf { it.isDirectory } else null

    fun medienOrdnerPfad(server: String, tree: String): String = DeskLayout.prefs.getString(ordnerSchluessel(server, tree), null).orEmpty()

    fun medienOrdnerSetzen(server: String, tree: String, pfad: String?) = DeskLayout.prefs.putString(ordnerSchluessel(server, tree), pfad?.trim().orEmpty())

    /** Zwischenspeicher dieses Baums loeschen; Rueckgabe: Zahl der entfernten Dateien. */
    fun cacheLeeren(server: String, tree: String): Int {
        val o = cacheOrdner(server, tree)
        if (!o.isDirectory) return 0
        val n = o.walkBottomUp().count { it.isFile }
        o.deleteRecursively()
        return n
    }

    /** Groesse des Zwischenspeichers dieses Baums in Bytes. */
    fun cacheGroesse(server: String, tree: String): Long = cacheOrdner(server, tree).takeIf { it.isDirectory }?.walk()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    /** Pfad aus der API so, dass er im Ordner bleibt: Rueckwaertsschraegstriche, "..", Laufwerke und fuehrende Striche weg. */
    internal fun sicher(pfad: String?): String? {
        val p = pfad?.trim()?.replace('\\', '/')?.trimStart('/') ?: return null
        if (p.isEmpty() || p.contains(':') || p.split('/').any { it == ".." || it.isEmpty() }) return null
        return p
    }

    private fun cacheDatei(ordner: File, q: Quelle): File {
        val p = sicher(q.pfad)
        if (p != null) return File(ordner, "$p.vorschau.jpg")
        val hash = MessageDigest.getInstance("SHA-1").digest(q.url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(ordner, "_url/$hash.jpg")
    }

    /** In RGB umkopieren (JPEG und PDF verlangen Bilder ohne Transparenz). */
    fun rgb(b: BufferedImage): BufferedImage = if (b.type == BufferedImage.TYPE_INT_RGB) b else
        BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { color = Color.WHITE; fillRect(0, 0, b.width, b.height); drawImage(b, 0, 0, null); dispose() } }

    /** Auf die lange Kante [max] verkleinern (nie vergroessern). */
    fun verkleinert(b: BufferedImage, max: Int = LANGE_KANTE): BufferedImage {
        val f = max.toDouble() / maxOf(b.width, b.height)
        if (f >= 1.0) return rgb(b)
        val w = (b.width * f).toInt().coerceAtLeast(1); val h = (b.height * f).toInt().coerceAtLeast(1)
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { z ->
            z.createGraphics().apply { color = Color.WHITE; fillRect(0, 0, w, h); drawImage(b.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null); dispose() }
        }
    }

    private fun lesen(f: File): BufferedImage? = runCatching { if (f.isFile && f.length() > 0) ImageIO.read(f) else null }.getOrNull()

    /** Ein Bild holen: Medienordner auf dem PC, Zwischenspeicher, sonst Server (und dann in den Zwischenspeicher). */
    fun laden(client: WtClient, server: String, tree: String, q: Quelle, z: Zaehler? = null): BufferedImage? {
        val pfad = sicher(q.pfad)
        if (pfad != null) medienOrdner(server, tree)?.let { o -> lesen(File(o, pfad))?.let { z?.lokal?.incrementAndGet(); return verkleinert(it) } }
        val ordner = cacheOrdner(server, tree)
        val datei = cacheDatei(ordner, q)
        lesen(datei)?.let { z?.cache?.incrementAndGet(); return it }
        val bild = runCatching {
            client.http.newCall(Request.Builder().url(q.url).build()).execute().use { r -> if (r.isSuccessful) r.body?.byteStream()?.use { ImageIO.read(it) } else null }
        }.getOrNull()?.let(::rgb)
        if (bild == null) { z?.fehlt?.incrementAndGet(); return null }
        runCatching { datei.parentFile.mkdirs(); ImageIO.write(bild, "jpg", datei) }
        z?.server?.incrementAndGet()
        return bild
    }

    /**
     * Alle Bilder eines Buchs oder einer Tafel, sechs gleichzeitig, mit Fortschritt; [abbruch] beendet den Lauf, was fehlt,
     * bleibt ohne Bild. Ergebnis: Adresse -> Bild.
     */
    suspend fun alle(client: WtClient, server: String, tree: String, quellen: List<Quelle>, fortschritt: (String) -> Unit = {},
                     abbruch: () -> Boolean = { false }): Map<String, BufferedImage> = coroutineScope {
        val liste = quellen.distinctBy { it.url }
        if (liste.isEmpty()) return@coroutineScope emptyMap()
        val z = Zaehler()
        fortschritt(z.text(liste.size))
        val schleuse = Semaphore(6)
        withContext(Dispatchers.IO) {
            liste.map { q ->
                async {
                    if (abbruch()) return@async q.url to null
                    schleuse.withPermit {
                        if (abbruch()) return@withPermit q.url to null
                        val b = laden(client, server, tree, q, z)
                        fortschritt(z.text(liste.size))
                        q.url to b
                    }
                }
            }.awaitAll()
        }.mapNotNull { (u, b) -> b?.let { u to it } }.toMap()
    }
}
