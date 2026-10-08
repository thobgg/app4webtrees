// Linux/Windows/macOS-Huelle von wtAnd: ein Fenster um den geteilten Kern
// (:shared), native Pakete via jpackage. Gebaut wird je System, auf dem
// es laeuft: deb unter Linux, exe unter Windows (Inno Setup, Task packageInno), dmg
// auf GitHub (.github/workflows/mac.yml, noch unsigniert).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}

tasks.withType<Test> { useJUnitPlatform() }

// wtAnd zaehlt zweistellig (1.18); Windows-Installer verlangen x.y.z. Die dritte Stelle zaehlt jetzt von selbst:
// die Zahl der Commits auf dem Stand, der gebaut wird. Windows Installer ersetzt eine installierte Fassung nur
// durch eine mit hoeherer Nummer - bei gleicher Nummer passiert auf Doppelklick schlicht nichts (24./25.09.2026,
// erst mit fester 0, dann mit dem Handzaehler desktopBuild, den vor dem Bauen niemand erhoeht hat). Ohne Git
// (z. B. Quelltext-Archiv) bleibt es beim Handzaehler.
val versionName = property("wtand.versionName") as String
val desktopBuild = property("wtand.desktopBuild") as String
val commitZahl: String = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
        .standardOutput.asText.get().trim().takeIf { it.toIntOrNull() != null }
}.getOrNull() ?: desktopBuild
val windowsVersion = if (versionName.count { it == '.' } == 1) "$versionName.$commitZahl" else versionName

// Ein Code, zwei Namen: wtWin unter Windows, wtTux unter Linux. Jedes Paket wird auf
// seinem eigenen System gebaut, darum entscheidet das System, auf dem Gradle laeuft.
// wtMac kam am 27.09.2026 dazu (versuchsweise, gebaut nur auf GitHub).
val osName = System.getProperty("os.name").orEmpty()
val appName = when {
    osName.startsWith("Windows") -> "wtWin"
    osName.startsWith("Mac") -> "wtMac"
    else -> "wtTux"
}

// Was "Neuen Stammbaum auf diesem PC anlegen" braucht, liegt im Paket (mitliefern, damit das Anlegen
// auch ohne Internet klappt). Landet unter resources/ (compose.application.resources.dir):
// common/webtrees/*.zip fuer alle, <system>/php/php(.exe) nur fuer das System, auf dem gebaut wird.
// Fuer macOS gibt es noch kein PHP - dort fehlt der Weg dann einfach (LokalBetrieb.verfuegbar).
val lokalOrdner = layout.buildDirectory.dir("lokal")
val phpSystem = when {
    osName.startsWith("Windows") -> "windows-x64"
    osName.startsWith("Mac") -> null
    else -> "linux-x64"
}
val lokalPaket by tasks.registering {
    val props = listOf("lokal.webtrees", "lokal.webtreesSha256", "lokal.api4webtrees", "lokal.api4webtreesSha256", "lokal.sammlungen", "lokal.sammlungenSha256") +
        listOfNotNull(phpSystem?.let { "lokal.phpSha256.$it" })
    props.forEach { inputs.property(it, project.property(it) as String) }
    outputs.dir(lokalOrdner)
    doLast {
        val basis = lokalOrdner.get().asFile
        val cache = layout.buildDirectory.dir("lokal-cache").get().asFile.apply { mkdirs() }
        fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes())
            .joinToString("") { "%02x".format(it) }
        fun laden(url: String, name: String, sha: String): File {
            val f = File(cache, name)
            if (f.isFile && sha256(f) == sha) return f
            logger.lifecycle("Lade $url")
            URI(url).toURL().openStream().use { i -> f.outputStream().use { i.copyTo(it) } }
            val ist = sha256(f)
            check(sha.isNotBlank() && ist == sha) { "Pruefsumme passt nicht: $name ist $ist, erwartet '$sha' (gradle.properties)" }
            return f
        }
        basis.deleteRecursively()
        val wt = project.property("lokal.webtrees") as String
        val api = project.property("lokal.api4webtrees") as String
        val web = File(basis, "common/webtrees").apply { mkdirs() }
        laden("https://github.com/fisharebest/webtrees/releases/download/$wt/webtrees-$wt.zip", "webtrees-$wt.zip",
            project.property("lokal.webtreesSha256") as String).copyTo(File(web, "webtrees-$wt.zip"))
        laden("https://github.com/thobgg/api4webtrees/releases/download/v$api/api4webtrees-v$api.zip", "api4webtrees-v$api.zip",
            project.property("lokal.api4webtreesSha256") as String).copyTo(File(web, "api4webtrees-v$api.zip"))
        val sam = project.property("lokal.sammlungen") as String
        laden("https://github.com/thobgg/webtrees-sammlungen/releases/download/v$sam/sammlungen-v$sam.zip", "sammlungen-v$sam.zip",
            project.property("lokal.sammlungenSha256") as String).copyTo(File(web, "sammlungen-v$sam.zip"))
        if (phpSystem != null) {
            val endung = if (phpSystem.startsWith("windows")) "zip" else "tar.gz"
            val archiv = laden("https://github.com/thobgg/app4webtrees/releases/download/php-8.4/php-$phpSystem.$endung",
                "php-$phpSystem.$endung", project.property("lokal.phpSha256.$phpSystem") as String)
            project.copy {
                from(if (endung == "zip") zipTree(archiv) else tarTree(resources.gzip(archiv)))
                into(File(basis, phpSystem))
            }
            File(basis, "$phpSystem/php/php").takeIf { it.isFile }?.setExecutable(true)
        }
    }
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(lokalPaket) }

compose.desktop {
    application {
        mainClass = "de.bgghome.webtrees.nativ.desktop.MainKt"
        jvmArgs += "-Dwtand.desktopBuild=${property("wtand.desktopBuild")}"
        jvmArgs += "-Dwtand.versionName=$versionName"
        // Build-Nummer wie im exe-Namen (wtWin-1.26.157.exe), damit "Ueber" zeigt, welche exe laeuft
        jvmArgs += "-Dwtand.build=$commitZahl"

        nativeDistributions {
            appResourcesRootDir.set(lokalOrdner)
            // Windows: kein Msi/Exe mehr aus jpackage - den Installer baut Inno Setup (packageInno unten) aus dem
            // Programmverzeichnis von createDistributable.
            targetFormats(TargetFormat.Deb, TargetFormat.Dmg)
            // NIE mehr aendern, sobald das erste Paket verteilt ist: Installationsordner und
            // Startmenue haengen daran.
            packageName = appName
            modules("java.instrument", "java.prefs", "jdk.unsupported")
            // Windows-Zertifikatspeicher (KeyStore "Windows-ROOT", api/Vertrauen.desktop.kt) - das Modul gibt es nur im Windows-JDK
            if (osName.startsWith("Windows")) modules("jdk.crypto.mscapi")
            // Dieselbe Nummer wie die APK (gradle.properties). Windows
            // Installer verlangt rein numerisch x.y.z.
            packageVersion = versionName
            description = "$appName - client for webtrees (app4webtrees)"
            vendor = "bgg-home.de"

            linux {
                menuGroup = "Office"
                packageName = "wttux"
                appRelease = property("wtand.desktopBuild") as String
                // Dasselbe Symbol wie wtAnd; ohne Angabe zeigt das Menue das Java-Standardsymbol.
                iconFile.set(project.file("icons/app.png"))
            }
            windows {
                packageVersion = windowsVersion
                // Die jpackage-MSIs 1.21 bis 1.41 trugen die upgradeUuid b3f1d7a2-4c58-4e9b-8f60-1d2e7a9c5b41;
                // der Inno-Installer (installer/wtWin.iss) entfernt diese Fassungen beim Update selbst.
                iconFile.set(project.file("icons/app.ico"))
            }
            macOS {
                // NIE aendern (wie upgradeUuid): macOS erkennt das Programm an dieser Kennung.
                bundleID = "de.bgghome.webtrees.wtmac"
                packageVersion = windowsVersion
                dmgPackageVersion = windowsVersion
                appCategory = "public.app-category.productivity"
                iconFile.set(project.file("icons/app.icns"))
                // Verbinden-Links: wtmac:// und die Schemata der Geschwister, denn api4webtrees waehlt nach
                // Geraet und kennt den Mac (noch) nicht. Die Links kommen als Apple-Event, nicht als Argument (Main.kt).
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array>
                          <dict>
                            <key>CFBundleURLName</key>
                            <string>de.bgghome.webtrees.connect</string>
                            <key>CFBundleURLSchemes</key>
                            <array>
                              <string>wtmac</string>
                              <string>wtwin</string>
                              <string>wttux</string>
                            </array>
                          </dict>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}

// Windows-Installer mit Inno Setup (seit 1.42): Sprachwahl, Installation pro Benutzer, wtwin:// gleich eingetragen,
// ersetzt die jpackage-MSI-Fassungen beim Update. Braucht ISCC.exe (Inno Setup 6, windows-einrichten.ps1);
// ein anderer Ort geht ueber die Umgebungsvariable ISCC. Ergebnis: build/compose/binaries/main/inno/wtWin-x.y.z.exe
tasks.register<Exec>("packageInno") {
    group = "compose desktop"
    description = "Windows installer (Inno Setup) from the app image"
    dependsOn("createDistributable")
    onlyIf { osName.startsWith("Windows") }
    val quelle = layout.buildDirectory.dir("compose/binaries/main/app/$appName").get().asFile
    val ziel = layout.buildDirectory.dir("compose/binaries/main/inno").get().asFile
    val skript = project.file("installer/wtWin.iss")
    inputs.dir(quelle); inputs.file(skript); outputs.dir(ziel)
    doFirst {
        val kandidaten = listOfNotNull(
            System.getenv("ISCC"),
            System.getenv("ProgramFiles(x86)")?.let { "$it\\Inno Setup 6\\ISCC.exe" },
            System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Inno Setup 6\\ISCC.exe" },
        )
        val iscc = kandidaten.firstOrNull { File(it).isFile }
            ?: error("ISCC.exe (Inno Setup 6) nicht gefunden - windows-einrichten.cmd ausfuehren oder ISCC auf die Datei setzen. Gesucht: $kandidaten")
        ziel.mkdirs()
        commandLine(iscc, "/Q", "/DVersion=$windowsVersion", "/DQuelle=${quelle.path}", "/DZiel=${ziel.path}", skript.path)
    }
    doLast { ziel.listFiles()?.forEach { println("Installer: ${it.path}") } }
}
