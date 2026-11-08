package app.filebrowser.personale

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Freccia verso l'alto (forma "arrow upward" di Material), disegnata qui per non usare material-icons-extended. */
val ArrowUpIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 12f)
            lineToRelative(1.41f, 1.41f)
            lineTo(11f, 7.83f)
            verticalLineTo(20f)
            horizontalLineToRelative(2f)
            verticalLineTo(7.83f)
            lineToRelative(5.58f, 5.59f)
            lineTo(20f, 12f)
            lineToRelative(-8f, -8f)
            close()
        }
    }.build()
}

/** Tre puntini verticali (forma "more vert" di Material): tre cerchi. */
val MoreVertIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MoreVert",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            for (startY in listOf(4f, 10f, 16f)) {
                moveTo(12f, startY)
                arcToRelative(2f, 2f, 0f, true, true, 0f, 4f)
                arcToRelative(2f, 2f, 0f, true, true, 0f, -4f)
                close()
            }
        }
    }.build()
}

/** Cestino (forma "delete" di Material). */
val TrashIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Trash",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(8f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f)
            horizontalLineTo(6f)
            verticalLineToRelative(12f)
            close()
            moveTo(19f, 4f)
            horizontalLineToRelative(-3.5f)
            lineToRelative(-1f, -1f)
            horizontalLineToRelative(-5f)
            lineToRelative(-1f, 1f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14f)
            verticalLineTo(4f)
            close()
        }
    }.build()
}

/** Croce di chiusura (forma "close" di Material). */
val CloseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Close",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 6.41f)
            lineTo(17.59f, 5f)
            lineTo(12f, 10.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(12f, 13.41f)
            lineTo(17.59f, 19f)
            lineTo(19f, 17.59f)
            lineTo(13.41f, 12f)
            close()
        }
    }.build()
}

// --- Icone dei tipi di file: stessa pagina (angolo tagliato, bordo colorato), stessa dimensione -----------------

private val PageFill = Color(0xFFF7F7F7)
private val PdfColor = Color(0xFFE53935)
private val ArchiveColor = Color(0xFF7B1FA2)
private val TextColor = Color(0xFF546E7A)
private val GenericColor = Color(0xFF90A4AE)
private val VideoColor = Color(0xFF00897B)
private val AudioColor = Color(0xFF3949AB)
private val ImageColor = Color(0xFF43A047)

private fun ImageVector.Builder.pageBase(border: Color) {
    path(fill = SolidColor(border)) {
        moveTo(3f, 0f)
        horizontalLineTo(15.5f)
        lineTo(24f, 8.5f)
        verticalLineTo(27f)
        arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
        horizontalLineTo(3f)
        arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
        verticalLineTo(3f)
        arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
        close()
    }
    path(fill = SolidColor(PageFill)) {
        moveTo(3f, 1.6f)
        horizontalLineTo(14.9f)
        lineTo(22.4f, 9.1f)
        verticalLineTo(27f)
        arcToRelative(1.4f, 1.4f, 0f, false, true, -1.4f, 1.4f)
        horizontalLineTo(3f)
        arcToRelative(1.4f, 1.4f, 0f, false, true, -1.4f, -1.4f)
        verticalLineTo(3f)
        arcToRelative(1.4f, 1.4f, 0f, false, true, 1.4f, -1.4f)
        close()
    }
}

private fun ImageVector.Builder.bar(x: Float, y: Float, w: Float, h: Float, color: Color) {
    path(fill = SolidColor(color)) {
        moveTo(x, y)
        horizontalLineToRelative(w)
        verticalLineToRelative(h)
        horizontalLineToRelative(-w)
        close()
    }
}

private fun ImageVector.Builder.circle(cx: Float, cy: Float, r: Float, color: Color) {
    path(fill = SolidColor(color)) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }
}

private fun pageIcon(name: String, border: Color, content: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 30.dp,
        viewportWidth = 24f,
        viewportHeight = 30f
    ).apply {
        pageBase(border)
        content()
    }.build()

/** PDF: righe di testo in alto; la scritta "PDF" si aggiunge in Compose nella metà bassa, senza toccarle. */
val PdfPageIcon: ImageVector by lazy {
    pageIcon("PdfPage", PdfColor) {
        bar(6f, 5.5f, 10f, 1.4f, PdfColor)
        bar(6f, 8.5f, 12f, 1.4f, PdfColor)
        bar(6f, 11.5f, 8f, 1.4f, PdfColor)
    }
}

/** Archivi: una cerniera al centro (simbolo universale degli archivi compressi); l'estensione si aggiunge in basso. */
val ArchivePageIcon: ImageVector by lazy {
    pageIcon("ArchivePage", ArchiveColor) {
        for (i in 0..3) {
            val y = 3f + i * 2f
            if (i % 2 == 0) bar(10.2f, y, 1.8f, 1.2f, ArchiveColor) else bar(12f, y, 1.8f, 1.2f, ArchiveColor)
        }
        bar(10.4f, 11f, 3.2f, 3.2f, ArchiveColor)
        circle(12f, 12.8f, 0.7f, PageFill)
    }
}

/** File di testo: pagina con molte righe. */
val TextPageIcon: ImageVector by lazy {
    pageIcon("TextPage", TextColor) {
        bar(5f, 6f, 12f, 1.4f, TextColor)
        bar(5f, 9.5f, 14f, 1.4f, TextColor)
        bar(5f, 13f, 12f, 1.4f, TextColor)
        bar(5f, 16.5f, 14f, 1.4f, TextColor)
        bar(5f, 20f, 8f, 1.4f, TextColor)
    }
}

/** File generici: pagina vuota; l'estensione, se c'è, si aggiunge in basso. */
val GenericPageIcon: ImageVector by lazy { pageIcon("GenericPage", GenericColor) { } }

val VideoPageIcon: ImageVector by lazy {
    pageIcon("VideoPage", VideoColor) {
        path(fill = SolidColor(VideoColor)) {
            moveTo(9f, 9f)
            lineTo(9f, 21f)
            lineTo(18.5f, 15f)
            close()
        }
    }
}

val AudioPageIcon: ImageVector by lazy {
    pageIcon("AudioPage", AudioColor) {
        circle(9.5f, 21f, 2.6f, AudioColor)
        bar(11.4f, 8f, 1.4f, 13f, AudioColor)
        path(fill = SolidColor(AudioColor)) {
            moveTo(12.8f, 8f)
            lineTo(18.5f, 10.5f)
            lineTo(18.5f, 13.5f)
            lineTo(12.8f, 11f)
            close()
        }
    }
}

/** Segnaposto delle immagini mentre la miniatura si carica: sole e montagne. */
val ImagePageIcon: ImageVector by lazy {
    pageIcon("ImagePage", ImageColor) {
        circle(16f, 8f, 1.9f, ImageColor)
        path(fill = SolidColor(ImageColor)) {
            moveTo(4.5f, 24f)
            lineTo(10f, 15f)
            lineTo(14f, 21f)
            lineTo(16.5f, 18f)
            lineTo(19.5f, 24f)
            close()
        }
    }
}

/** Cartella blu a due toni, la stessa dell'icona dell'app, per le cartelle nell'elenco. */
val FolderIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Folder",
        defaultWidth = 24.dp,
        defaultHeight = 20.dp,
        viewportWidth = 24f,
        viewportHeight = 20f
    ).apply {
        path(fill = SolidColor(Color(0xFF0D47A1))) {
            moveTo(0f, 3f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
            horizontalLineToRelative(6f)
            lineToRelative(2.5f, 2.5f)
            horizontalLineToRelative(9.5f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
            verticalLineTo(17f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
            horizontalLineTo(3f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
            close()
        }
        path(fill = SolidColor(Color(0xFF1E88E5))) {
            moveTo(0f, 6.5f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
            horizontalLineToRelative(18f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
            verticalLineTo(17f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
            horizontalLineTo(3f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
            close()
        }
    }.build()
}
