package app.filebrowser.personale

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

/** Elenco ordinato secondo [comparator]; null se la cartella non è leggibile. */
fun listEntries(dir: File, comparator: Comparator<File>): List<File>? =
    dir.listFiles()?.sortedWith(comparator)

/** L'ordine di dichiarazione è quello dei chip nella schermata. */
enum class SortKey(val label: String) {
    DATE("Data"),
    NAME("Nome"),
    TYPE("Tipo")
}

/** Un criterio di ordinamento; più criteri attivi si applicano nell'ordine in cui sono stati attivati. */
data class SortRule(val key: SortKey, val descending: Boolean)

/**
 * I criteri sono alternativi: ne è attivo al massimo uno.
 * Tocco sul criterio: disabilitato -> crescente -> decrescente -> disabilitato.
 * Toccare un criterio diverso disattiva quello attivo e parte da crescente.
 */
fun cycleSort(rules: List<SortRule>, key: SortKey): List<SortRule> {
    val current = rules.firstOrNull { it.key == key }
    return when {
        current == null -> listOf(SortRule(key, descending = false))
        !current.descending -> listOf(SortRule(key, descending = true))
        else -> emptyList()
    }
}

// Lo stato è salvato come testo (es. "NAME:A,DATE:D") per sopravvivere alla rotazione dello schermo.
fun encodeSort(rules: List<SortRule>): String =
    rules.joinToString(",") { "${it.key.name}:${if (it.descending) "D" else "A"}" }

fun decodeSort(spec: String): List<SortRule> =
    spec.split(",").mapNotNull { part ->
        val pieces = part.split(":")
        val key = SortKey.entries.firstOrNull { it.name == pieces.getOrNull(0) } ?: return@mapNotNull null
        SortRule(key, descending = pieces.getOrNull(1) == "D")
    }

/** Le cartelle restano sempre prima dei file. Senza criteri attivi si ordina per nome crescente; a parità, per nome. */
fun buildComparator(rules: List<SortRule>): Comparator<File> {
    val byName = Comparator<File> { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
    var result: Comparator<File> = Comparator { a, b ->
        // true prima di false: le cartelle vengono per prime
        b.isDirectory.compareTo(a.isDirectory)
    }
    val effective = rules.ifEmpty { listOf(SortRule(SortKey.NAME, descending = false)) }
    for (rule in effective) {
        val base: Comparator<File> = when (rule.key) {
            SortKey.NAME -> byName
            SortKey.DATE -> Comparator { a, b -> a.lastModified().compareTo(b.lastModified()) }
            SortKey.TYPE -> Comparator { a, b -> a.extension.compareTo(b.extension, ignoreCase = true) }
        }
        result = result.then(if (rule.descending) base.reversed() else base)
    }
    return result.then(byName)
}

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

/** Nome libero nella cartella: "base.ext", "base (2).ext", "base (3).ext"... */
fun uniqueFileName(dir: File, base: String, ext: String): String {
    var name = "$base.$ext"
    var i = 2
    while (File(dir, name).exists()) {
        name = "$base ($i).$ext"
        i++
    }
    return name
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
        // Per installare un APK Android richiede il permesso nel manifest e il consenso dell'utente alle "app sconosciute".
        if (file.extension.equals("apk", ignoreCase = true) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                context,
                "Consenti a FileBrowser di installare app, poi tocca di nuovo l'APK",
                Toast.LENGTH_LONG
            ).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
            return
        }
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

enum class FileCategory(val label: String, val icon: String) {
    ALL("Tutti", "📂"),
    FOLDERS("Cartelle", "📁"),
    ARCHIVES("Archivi", ""), // senza emoji: nell'elenco si disegna un riquadro arancione con l'estensione
    TEXT("Testo", "📝"),
    PDF("PDF", ""), // senza emoji: nell'elenco si disegna un riquadro rosso "PDF"
    VIDEO("Video", "🎬"),
    AUDIO("Musica", "🎵"),
    IMAGES("Immagini", "🖼️"),
    OTHER("Altri", "📄")
}

/** Categorie mostrate come filtri nella barra in alto ("Altri" ha solo l'icona). */
val FILTER_CATEGORIES = listOf(
    FileCategory.ALL, FileCategory.FOLDERS, FileCategory.ARCHIVES, FileCategory.TEXT,
    FileCategory.PDF, FileCategory.VIDEO, FileCategory.AUDIO, FileCategory.IMAGES
)

// Per l'icona e il filtro contano tutti questi formati; l'estrazione (isArchive) per ora solo lo ZIP.
private val ARCHIVE_TYPES = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
private val TEXT_TYPES = setOf(
    "txt", "md", "log", "csv", "json", "xml", "html", "htm", "ini", "conf", "cfg",
    "yml", "yaml", "kt", "java", "js", "css", "sql", "srt", "rtf"
)
private val VIDEO_TYPES = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "flv", "wmv")
private val AUDIO_TYPES = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "amr")

fun File.category(): FileCategory {
    if (isDirectory) return FileCategory.FOLDERS
    val ext = extension.lowercase()
    return when {
        ext in ARCHIVE_TYPES -> FileCategory.ARCHIVES
        ext in TEXT_TYPES -> FileCategory.TEXT
        ext == "pdf" -> FileCategory.PDF
        ext in VIDEO_TYPES -> FileCategory.VIDEO
        ext in AUDIO_TYPES -> FileCategory.AUDIO
        ext in IMAGE_EXTENSIONS -> FileCategory.IMAGES
        else -> FileCategory.OTHER
    }
}

/** Le cartelle restano sempre visibili (per poter navigare), tranne nel filtro "Cartelle" che mostra solo quelle. */
fun File.matchesFilter(filter: FileCategory): Boolean = when (filter) {
    FileCategory.ALL -> true
    FileCategory.FOLDERS -> isDirectory
    else -> isDirectory || category() == filter
}

fun File.isHidden(): Boolean = name.startsWith(".")
