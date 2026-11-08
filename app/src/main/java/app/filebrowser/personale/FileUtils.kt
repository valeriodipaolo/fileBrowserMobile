package app.filebrowser.personale

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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

fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Apri con"))
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
