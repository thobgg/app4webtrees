package de.bgghome.webtrees.nativ.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import de.bgghome.webtrees.nativ.data.ImagePrep
import java.io.File
import java.io.IOException

@Composable
actual fun rememberPhotoSources(onPhoto: (PhotoFile) -> Unit): PhotoSources {
    val context = LocalContext.current

    // Galerie: der Photo Picker des Systems braucht keine Berechtigung.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(photoFile(context, uri))
    }

    // Kamera: die Aufnahme landet in einer eigenen Datei im Cache, die nur die Kamera-App beschreiben darf (FileProvider).
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) onPhoto(photoFile(context, uri))
    }

    return remember {
        PhotoSources(
            pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            take = {
                val folder = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(folder, "aufnahme-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                cameraUri = uri
                camera.launch(uri)
            },
        )
    }
}

/** Eine content://-Adresse als [PhotoFile]: Name und Typ vom Content-Resolver, Verkleinern ueber [ImagePrep]. */
private fun photoFile(context: Context, uri: Uri): PhotoFile {
    val resolver = context.contentResolver
    var name = "foto.jpg"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.let { name = it }
    }
    return PhotoFile(
        name = name,
        mime = resolver.getType(uri).orEmpty(),
        preview = uri,
        prepareJpeg = { limit ->
            runCatching { ImagePrep.toUploadJpeg(resolver, uri, limit) }
                .onFailure { Log.w("wtAnd", "Bild liess sich nicht verkleinern", it) }
                .getOrNull()
                .also { Log.i("wtAnd", "Upload $name: ${it?.size} Bytes vorbereitet, Limit $limit") }
        },
        readBytes = { resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("file not readable") },
    )
}

@Composable
actual fun rememberNotificationPermission(onGranted: () -> Unit): () -> Unit {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted()
    }
    return {
        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else onGranted()
    }
}
