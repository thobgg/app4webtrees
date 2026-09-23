import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release-Signierung: liest Keystore-Angaben aus keystore.properties (nicht
// einchecken). Fehlt die Datei, wird mit dem Debug-Key signiert statt den Build
// zu brechen — gleiches Muster wie mpd-app.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "de.bgghome.webtrees.nativ"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.bgghome.webtrees.nativ"
        minSdk = 26
        targetSdk = 36
        // Zentral in gradle.properties, weil der Desktop-Client dieselbe Nummer traegt.
        versionCode = (property("wtand.versionCode") as String).toInt()
        versionName = property("wtand.versionName") as String
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug mit dem Release-Key signieren → In-place-Updates ohne Datenverlust
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
}

dependencies {
    // Der gesamte Code liegt im geteilten Modul; die App-Huelle hat nur
    // MainActivity, Manifest, Launcher-Symbol und Signatur. Alle
    // Bibliotheken kommen als api-Abhaengigkeiten aus :shared.
    implementation(project(":shared"))
}
