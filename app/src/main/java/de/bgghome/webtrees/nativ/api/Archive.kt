package de.bgghome.webtrees.nativ.api

import kotlinx.serialization.Serializable

// JSON-Formen des webtrees-Moduls "Sammlungen" (github.com/thobgg/webtrees-sammlungen, Abschnitt "Schnittstelle fuer
// Apps"): das Archiv eines Baums - Fotos und Dokumente, die nicht unbedingt an einer Person haengen. Die Feldnamen sind
// die des Moduls, deshalb deutsch.

/** Antwort von /archiv/api/sammlungen: die Uebersicht des Archivs. */
@Serializable
data class ArchiveOverview(
    val api: Int = 0,
    val modul: String = "",
    val baum: String = "",
    val proSeite: Int = 50,
    /** Die Galerie des Moduls im Browser */
    val galerie: String = "",
    val sammlungen: List<ArchiveCollection> = emptyList(),
    /** Medienobjekte, an denen keine Person und keine Familie haengt - nach Medientyp */
    val unverknuepft: List<MediaTypeCount> = emptyList(),
    val frei: FreeFiles = FreeFiles(),
)

/** Eine Sammlung auf der Uebersicht. art: "ordner", "thematisch" oder "medientyp". */
@Serializable
data class ArchiveCollection(
    val slug: String,
    val art: String = "",
    val name: String = "",
    val beschreibung: String = "",
    /** "#rrggbb" bei Sammlungen aus der Verwaltung, sonst null */
    val farbe: String? = null,
    val icon: String = "",
    /** "foto", "raster", "gemischt" oder "dokument" */
    val ansicht: String = "foto",
    val ordner: String? = null,
    val anzahl: Int = 0,
    /** Bis zu drei Vorschaubilder */
    val vorschau: List<String> = emptyList(),
)

@Serializable
data class MediaTypeCount(val typ: String, val name: String = "", val anzahl: Int = 0)

/** Dateien im Medienordner, die im Stammbaum nirgends auftauchen - nur gezaehlt, je Ordner. */
@Serializable
data class FreeFiles(val gesamt: Int = 0, val dateien: Int = 0, val jeOrdner: List<FolderCount> = emptyList())

@Serializable
data class FolderCount(val ordner: String = "", val anzahl: Int = 0)

/** Antwort von /archiv/api/sammlung: eine Seite einer Sammlung. */
@Serializable
data class CollectionPage(
    val slug: String = "",
    val art: String = "",
    /** Nur bei nicht eingebundenen Medien: der Medientyp dieser Seite */
    val typ: String? = null,
    val name: String = "",
    val beschreibung: String = "",
    val farbe: String? = null,
    val icon: String = "",
    val ansicht: String = "foto",
    val anzahl: Int = 0,
    val dateien: Int = 0,
    val seite: Int = 1,
    val seiten: Int = 1,
    val proSeite: Int = 0,
    val typen: List<MediaTypeCount> = emptyList(),
    val eintraege: List<ArchiveEntry> = emptyList(),
    /** Foto-Ordner: Video, Audio und Dokumente als Liste unter den Bildern (nicht seitenweise) */
    val weitere: List<ArchiveEntry> = emptyList(),
)

/** Eine Datei im Archiv - mit oder ohne Medienobjekt (xref). */
@Serializable
data class ArchiveEntry(
    val pfad: String? = null,
    val datei: String = "",
    val format: String = "",
    val istBild: Boolean = false,
    val xref: String? = null,
    val titel: String = "",
    val notiz: String = "",
    /** EXIF-Beschreibung aus der Datei */
    val beschreibung: String = "",
    /** Beschreibung, sonst Titel, sonst Dateiname - was unter das Bild gehoert */
    val bildunterschrift: String = "",
    val datum: String = "",
    val datumIso: String = "",
    val exifPersonen: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val breite: Int = 0,
    val hoehe: Int = 0,
    val groesseKb: Int = 0,
    /** In webtrees verknuepfte Personen (hoechstens 25) */
    val personen: List<PersonRef> = emptyList(),
    val personenGesamt: Int = 0,
    val inSammlungen: List<String> = emptyList(),
    /** 400 Pixel breit - nur bei Bildern */
    val kachel: String? = null,
    /** 1600 Pixel breit - nur bei Bildern */
    val vollbild: String? = null,
    val original: String = "",
    /** Die Medienseite in webtrees - nur bei Medienobjekten */
    val seite: String? = null,
)
