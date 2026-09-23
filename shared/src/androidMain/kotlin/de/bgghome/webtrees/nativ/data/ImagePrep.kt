package de.bgghome.webtrees.nativ.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Macht ein Foto hochladefertig: Handybilder haben 5-12 MB, viele webtrees-Server nehmen aber nur
 * 2-8 MB an (upload_max_filesize). Also auf hoechstens MAX_SIDE Pixel verkleinern, nach den
 * EXIF-Angaben richtig herum drehen und als JPEG speichern.
 */
object ImagePrep {
    private const val MAX_SIDE = 2560
    private const val JPEG_QUALITY = 88
    private const val MIN_SIDE = 800

    /**
     * @param maxBytes groesste Datei, die der Server annimmt (Info.maxUpload); es wird so lange staerker komprimiert
     *                 und notfalls weiter verkleinert, bis das Bild hineinpasst.
     * @return null, wenn sich die Datei nicht als Bild lesen laesst - dann laedt der Aufrufer sie unveraendert hoch.
     */
    fun toUploadJpeg(resolver: ContentResolver, uri: Uri, maxBytes: Long): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Achtung: mit inJustDecodeBounds liefert decodeStream IMMER null - das Ergebnis darf nicht geprueft werden,
        // nur die gemessene Groesse. (Genau dieser Fehler hat anfangs jedes Verkleinern verhindert.)
        val input = resolver.openInputStream(uri) ?: return null
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Grob beim Lesen verkleinern (spart Speicher), den Rest danach exakt.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (!matrix.isIdentity) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        fun encode(source: Bitmap, quality: Int): ByteArray =
            ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

        // Erst die Qualitaet senken, dann die Kantenlaenge - bis das Bild unter dem Limit liegt.
        var current = bitmap
        while (true) {
            for (quality in listOf(JPEG_QUALITY, 80, 72, 64)) {
                val bytes = encode(current, quality)
                if (bytes.size <= maxBytes) return bytes
            }
            if (maxOf(current.width, current.height) <= MIN_SIDE) return encode(current, 60)
            current = Bitmap.createScaledBitmap(current, (current.width * 0.8f).toInt(), (current.height * 0.8f).toInt(), true)
        }
    }
}
