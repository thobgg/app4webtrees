package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.bgghome.webtrees.nativ.api.AddIndividualRequest
import de.bgghome.webtrees.nativ.api.FactRequest
import de.bgghome.webtrees.nativ.api.PlaceRequest
import de.bgghome.webtrees.nativ.api.WriteResult
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Wegwerf-Probe (nicht einchecken): Ortsdialog und Orte bereinigen mit API-Stufe 32 gegen den NAS-Testserver. */
class OrtVerweisProbe {
    private class Speicher : Ablage {
        private val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as? String ?: default
        override fun putString(key: String, value: String?) { m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
        override fun leeren() = m.clear()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun zeichne(datei: File, klicks: List<Offset>, inhalt: @androidx.compose.runtime.Composable () -> Unit) {
        ImageComposeScene(width = 1300, height = 900, density = Density(1f)) {
            DeskTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { inhalt() } }
        }.use { scene ->
            var t = 0L
            fun laufen(n: Int) { repeat(n) { t += 25_000_000L; scene.render(t) } }
            laufen(40)
            klicks.forEachIndexed { i, k ->
                scene.sendPointerEvent(PointerEventType.Move, k)
                scene.sendPointerEvent(PointerEventType.Press, k)
                scene.sendPointerEvent(PointerEventType.Release, k)
                laufen(40)
                Thread.sleep(1500); laufen(10)
                File(datei.parentFile, datei.nameWithoutExtension + "-k${i + 1}.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
            System.getenv("WT_ZIEHEN")?.split(',')?.map { it.toFloat() }?.let { (x1, y1, x2, y2) ->
                scene.sendPointerEvent(PointerEventType.Move, Offset(x1, y1)); laufen(2)
                scene.sendPointerEvent(PointerEventType.Press, Offset(x1, y1)); laufen(2)
                for (i in 1..12) { scene.sendPointerEvent(PointerEventType.Move, Offset(x1 + (x2 - x1) * i / 12, y1 + (y2 - y1) * i / 12)); laufen(1) }
                scene.sendPointerEvent(PointerEventType.Release, Offset(x2, y2)); laufen(10)
            }
            datei.writeBytes(scene.render(t + 1).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    private fun klicks(name: String): List<Offset> = System.getenv(name)?.split(';')?.filter { it.isNotBlank() }
        ?.map { it.split(',').let { (x, y) -> Offset(x.toFloat(), y.toFloat()) } }.orEmpty()

    @Test
    fun probe() {
        val ziel = System.getenv("WT_ORTPROBE")?.let(::File) ?: return
        ziel.mkdirs()
        val baum = "falkenrath14"
        val c = WtClient(Speicher(), Speicher(), "wtTux/dev (Ortprobe)").apply { baseUrl = System.getenv("WT_URL") ?: "http://192.168.178.73:8095" }
        val info = runBlocking { c.login(System.getenv("WT_USER").orEmpty(), System.getenv("WT_PASS").orEmpty()) }
        println("API ${info.api}")
        val hof = System.getenv("WT_ORT") ?: "Probehof, Bienenbüttel, Uelzen, Niedersachsen, Deutschland"
        val schritt = System.getenv("WT_SCHRITT") ?: "anlegen"
        runBlocking {
            when (schritt) {
                "anlegen" -> {
                    // Ereignis zuerst (noch kein _LOC: kein Verweis), dann den _LOC - so entsteht ein Ereignis ohne Verweis
                    val x = c.addIndividual(baum, AddIndividualRequest(relation = "none", given = "Probe", surname = "Ortverweis", sex = "M", dead = true)).xref
                    c.saveFact(baum, x, FactRequest(tag = "RESI", date = "1850", place = hof))
                    val r = c.savePlace(baum, PlaceRequest(hof, type = "Hof"))
                    File(ziel, "daten.txt").writeText("$x ${r.xref}\n")
                    println("Person $x, _LOC ${r.xref}, linked ${r.linked}")
                    val d = c.place(baum, hof)
                    println("unlinked=${d.unlinked} events=${d.events} loc=${d.location?.xref} type=${d.location?.type} govType=${d.location?.govType}")
                }
                "dialog" -> {
                    val d = c.place(baum, hof)
                    println("vorher unlinked=${d.unlinked} govType=${d.location?.govType} parents=${d.location?.parents?.map { it.xref }}")
                    var ergebnis: WriteResult? = null
                    zeichne(File(ziel, "ortdialog.png"), klicks("WT_KLICKS")) {
                        OrtDialog(baum, d, c, istAdmin = true, openWeb = {}, onDismiss = {}, info = info) { ergebnis = it }
                    }
                    Thread.sleep(2000)
                    println("gespeichert: $ergebnis")
                    val n = c.place(baum, hof)
                    println("nachher unlinked=${n.unlinked} type=${n.location?.type} govType=${n.location?.govType}")
                }
                "bereinigen" -> {
                    val l = c.placeList(baum).places
                    zeichne(File(ziel, "bereinigen.png"), klicks("WT_KLICKS")) {
                        OrteBereinigenDialog(baum, l, onZeigen = {}, onZusammenfuehren = { _, _ -> }, onDismiss = {}, onVerweise = { println("Verweise fuer ${it.map { o -> o.name to o.unlinked }}") })
                    }
                }
                "oberort" -> {
                    val l1 = c.place(baum, "Bienenbüttel, Uelzen, Niedersachsen, Deutschland").location!!.xref
                    c.savePlace(baum, PlaceRequest(hof, parentAendern = true, parent = l1))
                    println("parents=" + c.place(baum, hof).location?.parents?.map { "${it.xref} ${it.type}" })
                }
                "aufraeumen" -> {
                    val (x, loc) = File(ziel, "daten.txt").readText().trim().split(' ')
                    println(runCatching { c.deleteRecord(baum, x) })
                    println(runCatching { c.deleteRecord(baum, loc) })
                }
            }
        }
    }
}
