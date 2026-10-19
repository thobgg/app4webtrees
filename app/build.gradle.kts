import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release-Signierung: liest Keystore-Angaben aus keystore.properties (nicht
// einchecken). Fehlt die Datei, bleibt der Release-Build unsigniert
// (app-release-unsigned.apk) - so erwartet es der F-Droid-Buildserver, der das
// APK selbst gegen die veroeffentlichte Signatur prueft.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    // Kein verschluesselter Block "Dependency metadata" (fuer Google Play) in der Signatur: F-Droid lehnt
    // APKs damit ab, weil niemand pruefen kann, was darin steht.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
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
            isMinifyEnabled = true
            // Kein META-INF/version-control-info.textproto (Commit-Hash des Bauplatzes) ins APK:
            // sonst ist der F-Droid-Bau nie byte-gleich zum veroeffentlichten APK.
            vcsInfo { include = false }
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
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
