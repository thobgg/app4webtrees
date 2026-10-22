package de.bgghome.webtrees.nativ.lokal

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Variante wtTux7/wtWin7: "Stammbaum auf diesem PC" mit webtrees7. Eine GEDCOM-5.5.1-Datei wird eingelesen und muss
 * danach als GEDCOM 7.0 gespeichert und bearbeitbar sein. Nur mit WTAND_PHP und WTAND_WEBTREES_ZIP (webtrees7-ZIP).
 */
class Webtrees7LokalTest {
    private val php = (System.getenv("WTAND_PHP") ?: "/usr/bin/php").let(::File)
    private val zip = System.getenv("WTAND_WEBTREES_ZIP")?.let(::File)
    private val api = System.getenv("WTAND_API_ZIP")?.let(::File)
    private val basis = createTempDirectory("wt7lokal").toFile()

    private fun sql(anweisung: String): String = ProcessBuilder(
        php.path, "-r", "\$d=new PDO('sqlite:data/webtrees.sqlite');foreach(\$d->query(\$argv[1],PDO::FETCH_NUM) as \$r)echo implode('|',\$r),PHP_EOL;", anweisung,
    ).directory(LokalOrte.webtrees).redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().readText().also { p.waitFor() } }.trim()

    @Test fun gedcom551WirdGedcom7() {
        val ged = File("../testdaten/falkenrath-1.3.ged").takeIf { it.isFile } ?: File("testdaten/falkenrath-1.3.ged")
        if (!php.canExecute() || zip?.isFile != true || !zip.name.contains("webtrees7") || !ged.isFile) return
        System.setProperty("wtand.lokal", basis.absolutePath)
        try {
            val (server, zugang) = LokaleEinrichtung(php, zip, api).einrichten("falkenrath", ged)
            try {
                // Der Kern hat nach dem Einlesen umgewandelt: Kopf 7.0, keine 5.5.1-Paten mehr
                assertEquals("1", sql("SELECT s.setting_value FROM wt_gedcom_setting s JOIN wt_gedcom g ON g.gedcom_id=s.gedcom_id WHERE g.gedcom_name='${zugang.baum}' AND s.setting_name='GEDCOM7'"))
                assertTrue(sql("SELECT o_gedcom FROM wt_other WHERE o_type='HEAD'").contains("2 VERS 7.0"))
                // instr statt LIKE: dort waere "_" ein Platzhalter
                assertEquals("0", sql("SELECT COUNT(*) FROM wt_individuals WHERE instr(i_gedcom, '_ASSO @') > 0 OR instr(i_gedcom, ' RELA ') > 0"))
                assertTrue(sql("SELECT COUNT(*) FROM wt_individuals WHERE instr(i_gedcom, '3 ROLE GODP') > 0").toInt() > 0)

                val client = de.bgghome.webtrees.nativ.api.WtClient(Speicher7(), Speicher7(), userAgent = "test")
                client.baseUrl = server.adresse
                kotlinx.coroutines.runBlocking {
                    val info = client.login(zugang.benutzer, zugang.passwort)
                    val baum = info.trees.first { it.name == zugang.baum }
                    assertTrue(baum.canEdit, "Baum nicht bearbeitbar: $baum")
                    assertTrue(info.api >= 38, "api ${info.api}")
                    assertTrue(client.individuals(zugang.baum, "Falkenrath", 1).data.size > 5)
                }
                println("WEBTREES7 LOKAL OK: api, Baum ${zugang.baum} bearbeitbar, GEDCOM 7.0")
            } finally { server.beenden() }
        } finally {
            basis.deleteRecursively()
            System.clearProperty("wtand.lokal")
        }
    }
}

private class Speicher7 : de.bgghome.webtrees.nativ.data.Ablage {
    private val m = mutableMapOf<String, Any>()
    override fun getString(key: String, default: String?) = m[key] as? String ?: default
    override fun putString(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    override fun getBoolean(key: String, default: Boolean) = m[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { m[key] = value }
    override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
    override fun leeren() = m.clear()
}
