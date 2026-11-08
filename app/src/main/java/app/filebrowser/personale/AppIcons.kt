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

private fun ImageVector.Builder.book(x: Float, y: Float, w: Float, h: Float, color: Color) {
    val r = 1f
    path(fill = SolidColor(color)) {
        moveTo(x + r, y)
        horizontalLineToRelative(w - 2 * r)
        arcToRelative(r, r, 0f, false, true, r, r)
        verticalLineToRelative(h - 2 * r)
        arcToRelative(r, r, 0f, false, true, -r, r)
        horizontalLineToRelative(-(w - 2 * r))
        arcToRelative(r, r, 0f, false, true, -r, -r)
        verticalLineToRelative(-(h - 2 * r))
        arcToRelative(r, r, 0f, false, true, r, -r)
        close()
    }
    // Banda chiara sul dorso del libro
    path(fill = SolidColor(Color.White.copy(alpha = 0.55f))) {
        moveTo(x + 3f, y)
        horizontalLineToRelative(0.8f)
        verticalLineToRelative(h)
        horizontalLineToRelative(-0.8f)
        close()
    }
}

/** Tre tomi sovrapposti e sfalsati: icona degli archivi. Colorata, quindi da usare con Image e non con Icon. */
val ArchiveBooksIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArchiveBooks",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        book(x = 3f, y = 15.5f, w = 18f, h = 5.5f, color = Color(0xFFBF360C))
        book(x = 5f, y = 10f, w = 17f, h = 5f, color = Color(0xFFEF6C00))
        book(x = 2f, y = 4.5f, w = 17f, h = 5f, color = Color(0xFFFFB300))
    }.build()
}
