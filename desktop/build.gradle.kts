// Linux/Windows-Huelle von wtAnd: ein Fenster um den geteilten Kern
// (:shared), native Pakete via jpackage. Gebaut wird je System, auf dem
// es laeuft: deb hier, msi/exe auf dem Windows-Laptop (WiX noetig).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
}

// wtAnd zaehlt zweistellig (1.16); Windows-Installer verlangen x.y.z, also 1.16.0.
val versionName = property("wtand.versionName") as String
val windowsVersion = if (versionName.count { it == '.' } == 1) "$versionName.0" else versionName

compose.desktop {
    application {
        mainClass = "de.bgghome.webtrees.nativ.desktop.MainKt"
        jvmArgs += "-Dwtand.desktopBuild=${property("wtand.desktopBuild")}"
        jvmArgs += "-Dwtand.versionName=$versionName"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Exe)
            // Arbeitsname; der endgueltige Name des Windows-Programms ist noch
            // offen (Thomas, 23.09.2026) - vor dem ERSTEN Windows-Paket festlegen,
            // denn upgradeUuid und Paketname duerfen sich danach nicht mehr aendern.
            packageName = "wtAnd"
            modules("java.instrument", "java.prefs", "jdk.unsupported")
            // Dieselbe Nummer wie die APK (gradle.properties). Windows
            // Installer verlangt rein numerisch x.y.z.
            packageVersion = versionName
            description = "wtAnd - webtrees client"
            vendor = "bgg-home.de"

            linux {
                menuGroup = "Office"
                packageName = "wtand"
                appRelease = property("wtand.desktopBuild") as String
            }
            windows {
                packageVersion = windowsVersion
                perUserInstall = true
                menuGroup = "wtAnd"
                shortcut = true
                dirChooser = true
                // NIE aendern: nur mit gleicher Kennung ersetzt eine neue
                // Version die alte, statt zweimal im Startmenue zu stehen.
                upgradeUuid = "b3f1d7a2-4c58-4e9b-8f60-1d2e7a9c5b41"
            }
        }
    }
}
