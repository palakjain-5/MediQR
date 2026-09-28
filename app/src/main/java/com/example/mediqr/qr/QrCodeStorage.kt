package com.example.mediqr.qr

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SAVE_FOLDER = "MediQR"
private const val FILE_NAME = "MediQR_QR.png"

/**
 * True when saving into the shared Pictures folder needs the legacy storage
 * permission (Android 9 and below). From Android 10 on MediaStore needs none.
 */
fun hasWritePermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Saves [bitmap] as a PNG into the public `Pictures/MediQR` folder and returns
 * the human-readable location for the confirmation message.
 *
 * Android 10+: MediaStore (no permission required). Older: direct write to the
 * public Pictures directory (the caller must have requested
 * WRITE_EXTERNAL_STORAGE first, see [hasWritePermission]).
 */
suspend fun saveQrCode(context: Context, bitmap: Bitmap): String =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, bitmap)
        } else {
            saveToPicturesFolder(context, bitmap)
        }
    }

private fun saveWithMediaStore(context: Context, bitmap: Bitmap): String {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, FILE_NAME)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SAVE_FOLDER")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Could not create the image entry.")

    try {
        val stream = resolver.openOutputStream(uri) ?: throw IOException("Could not open the image stream.")
        stream.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IOException("Could not write the image data.")
            }
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (t: Throwable) {
        // Don't leave a half-written entry behind.
        resolver.delete(uri, null, null)
        throw t
    }
    return "Pictures/$SAVE_FOLDER"
}

private fun saveToPicturesFolder(context: Context, bitmap: Bitmap): String {
    val dir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        SAVE_FOLDER,
    )
    if (!dir.exists() && !dir.mkdirs()) {
        throw IOException("Could not create the Pictures/$SAVE_FOLDER folder.")
    }
    val file = File(dir, FILE_NAME)
    FileOutputStream(file).use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            throw IOException("Could not write the image data.")
        }
    }
    // Make the new file show up in the gallery / file managers immediately.
    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
    return "Pictures/$SAVE_FOLDER"
}

/**
 * Writes [bitmap] into the app cache and opens the system share sheet with the
 * QR image plus its public link as text. Must be called from the main thread
 * (the file work happens on IO internally).
 */
suspend fun shareQrCode(context: Context, bitmap: Bitmap, cardUrl: String) {
    val imageUri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, FILE_NAME)
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IOException("Could not write the image data.")
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, "Scan this QR code to open my emergency medical card: $cardUrl")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share QR code"))
}
