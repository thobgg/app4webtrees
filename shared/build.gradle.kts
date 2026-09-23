// Geteilter Kern von wtAnd: API-Client, Zustand, Oberflaeche.
// Wird von :app (Android) und :desktop (Linux/Windows) benutzt, damit der
// Code nur EINMAL existiert - Muster wie mpd-app (23.09.2026).
//
// Stand der Aufteilung: Alles liegt zunaechst in androidMain und verhaelt
// sich wie vor dem Umbau. Was ohne Android auskommt, wandert Stueck fuer
// Stueck nach commonMain; Plattform-Dinge bekommen expect/actual.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        // ui-backhandler ist in Compose Multiplatform noch experimentell; expect/actual-Klassen ebenso.
        freeCompilerArgs.addAll("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi", "-Xexpect-actual-classes")
    }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // api statt implementation: die Huellen (:app, :desktop) bauen
            // ihre Oberflaeche mit denselben Compose-Artefakten.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.components.resources)
            // Icons.Default.* (Close, Search, Edit ...) - Material-Grundsatz
            api("org.jetbrains.compose.material:material-icons-core:1.7.3")
            api(libs.kotlinx.serialization.json)
            // JVM-Bibliotheken duerfen hier stehen: beide Ziele (Android,
            // Desktop) sind JVM, commonMain darf JDK-APIs benutzen.
            api(libs.okhttp)
            // ViewModel + viewModelScope fuer beide Ziele
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            // Bilder ueber die Sitzung des API-Clients (Cookie), Coil 3 fuer beide Ziele
            api(libs.coil.compose)
            api(libs.coil.network.okhttp)
            // Zurueck: Android-Systemgeste, Desktop Escape
            api(libs.compose.ui.backhandler)
        }
        androidMain.dependencies {
            api(libs.androidx.core.ktx)
            api(libs.androidx.lifecycle.runtime)
            api(libs.androidx.lifecycle.viewmodel.compose)
            api(libs.androidx.activity.compose)
            api(libs.androidx.exifinterface)
            // Taegliche Erinnerung an Jahrestage
            api(libs.androidx.work)
            // Lebenskarte
            api(libs.osmdroid)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Dispatchers.Main auf der JVM (Swing/AWT-Thread). Android
                // bringt ihn mit, der Desktop nicht - ohne ihn stirbt der
                // erste viewModelScope.launch mit "Main dispatcher is missing".
                api(libs.kotlinx.coroutines.swing)
                // PDF-Seiten rendern (Android: PdfRenderer im System)
                implementation("org.apache.pdfbox:pdfbox:3.0.8")
            }
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
        }
    }
}

compose.resources {
    // Texte, Symbole: eine Res-Klasse fuer beide Ziele.
    packageOfResClass = "de.bgghome.webtrees.nativ.res"
    // Die Huellen (MainActivity, Desktop-Fenster) greifen auf dieselben Texte zu.
    publicResClass = true
}

android {
    // Eigener Namespace, sonst kollidiert die R-Klasse mit der der App.
    namespace = "de.bgghome.webtrees.nativ.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        // Fuer User-Agent und Ueber-Zeile; die Bibliothek bekommt sonst keine VERSION_NAME.
        buildConfigField("String", "VERSION_NAME", "\"${property("wtand.versionName")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
