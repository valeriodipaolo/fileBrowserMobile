package app.filebrowser.personale

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.File

/** Estensioni per cui il menu offre "Estrai". Per ora solo ZIP; gli altri formati arriveranno dopo. */
private val ARCHIVE_EXTENSIONS = setOf("zip")

fun File.isArchive(): Boolean = isFile && extension.lowercase() in ARCHIVE_EXTENSIONS

/** Elenco ordinato (cartelle prima, poi per nome); null se la cartella non è leggibile. */
fun listEntries(dir: File): List<File>? =
    dir.listFiles()?.sortedWith(
        compareByDescending<File> { it.isDirectory }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    )

/** Cartella di destinazione che non esiste ancora: "nome", "nome (2)", "nome (3)"... */
fun uniqueDir(parent: File, base: String): File {
    var candidate = File(parent, base)
    var i = 2
    while (candidate.exists()) {
        candidate = File(parent, "$base ($i)")
        i++
    }
    return candidate
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(java.util.Locale.ITALY, "%.1f %s", value, units[unit])
}

/** Apre con l'app predefinita di Android; con forceChooser mostra sempre l'elenco delle app. */
fun openFile(context: Context, file: File, forceChooser: Boolean = false) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(if (forceChooser) Intent.createChooser(intent, "Apri con") else intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Nessuna app può aprire questo file", Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, "Impossibile aprire il file", Toast.LENGTH_SHORT).show()
    }
}

sealed interface ExtractResult {
    data object Ok : ExtractResult
    data object NeedsPassword : ExtractResult
    data object WrongPassword : ExtractResult
    data class Failed(val message: String) : ExtractResult
}

/** Da chiamare fuori dal thread principale. zip4j blocca i percorsi con "../" (zip slip). */
fun extractZip(archive: File, destination: File, password: String?): ExtractResult {
    return try {
        val zip = ZipFile(archive)
        if (zip.isEncrypted) {
            if (password.isNullOrEmpty()) return ExtractResult.NeedsPassword
            zip.setPassword(password.toCharArray())
        }
        destination.mkdirs()
        zip.extractAll(destination.absolutePath)
        ExtractResult.Ok
    } catch (e: ZipException) {
        if (e.type == ZipException.Type.WRONG_PASSWORD) ExtractResult.WrongPassword
        else ExtractResult.Failed(e.message ?: "Archivio non valido")
    } catch (e: Exception) {
        ExtractResult.Failed(e.message ?: "Errore durante l'estrazione")
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

fun File.isImage(): Boolean = isFile && extension.lowercase() in IMAGE_EXTENSIONS

// Cache delle miniature: circa 1/8 della memoria dell'app, misurata in KB.
private val thumbnailCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

/** Miniatura quadrata-ish di lato massimo [sizePx]; da chiamare fuori dal thread principale. Null se non decodificabile. */
fun loadThumbnail(file: File, sizePx: Int): Bitmap? {
    val key = "${file.absolutePath}|${file.lastModified()}|$sizePx"
    thumbnailCache.get(key)?.let { return it }
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        // Le foto del telefono hanno spesso l'orientamento nei metadati EXIF.
        val rotation = try {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
        if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        thumbnailCache.put(key, bitmap)
        bitmap
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
