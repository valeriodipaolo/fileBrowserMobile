package app.filebrowser.personale

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val BarColor = Color(0x99000000)

private fun Context.findActivity(): ComponentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Immagine ridotta a circa [maxDim] pixel sul lato lungo (per non esaurire la memoria con le foto grandi), con rotazione EXIF. */
private fun loadViewerBitmap(file: File, maxDim: Int = 2000): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
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
        bitmap
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

/** Barra in alto con il pulsante indietro e un titolo, su fondo scuro semitrasparente. */
@Composable
private fun ViewerBar(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(BarColor).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(ArrowBackIcon, contentDescription = "Indietro", tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Visualizzatore di foto a schermo intero: si scorre tra le immagini della cartella con un dito, si ingrandisce con
 * due dita (o con il doppio tocco) e, da ingrandite, si trascina. Un tocco mostra o nasconde la barra in alto.
 */
@Composable
fun ImageViewer(items: List<FileEntry>, startIndex: Int, onClose: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var barVisible by remember { mutableStateOf(true) }
    BackHandler { onClose() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].path }
        ) { page ->
            ZoomableImage(items[page].file, onTap = { barVisible = !barVisible })
        }
        if (barVisible) {
            val current = items[pagerState.currentPage]
            ViewerBar(
                title = "${pagerState.currentPage + 1} / ${items.size} · ${current.name}",
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun ZoomableImage(file: File, onTap: () -> Unit) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(file) {
        val loaded = withContext(Dispatchers.IO) { loadViewerBitmap(file) }
        bitmap = loaded
        failed = loaded == null
    }

    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged { box = it },
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        // Gesto personalizzato: con un dito e nessun ingrandimento non consuma nulla, così il
                        // pager può scorrere alla foto successiva; con due dita, o da ingrandita, ingrandisce e trascina.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1 || scale > 1f) {
                                    val newScale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                                    val pan = event.calculatePan()
                                    val maxX = box.width * (newScale - 1f) / 2f
                                    val maxY = box.height * (newScale - 1f) / 2f
                                    scale = newScale
                                    offset = if (newScale > 1f) {
                                        Offset(
                                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                                            (offset.y + pan.y).coerceIn(-maxY, maxY)
                                        )
                                    } else {
                                        Offset.Zero
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            failed -> Text("Impossibile aprire l'immagine", color = Color.White)
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

/**
 * Lettore di video e musica (Media3/ExoPlayer) con i file dello stesso tipo della cartella come elenco di
 * riproduzione: i pulsanti avanti e indietro passano al file successivo o precedente. Si ferma quando l'app esce
 * dallo schermo: niente riproduzione in background.
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(items: List<FileEntry>, startIndex: Int, isVideo: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(items) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(items.map { MediaItem.fromUri(Uri.fromFile(it.file)) }, startIndex, 0L)
            prepare()
            playWhenReady = true
        }
    }
    var title by remember(items) { mutableStateOf(items.getOrNull(startIndex)?.name.orEmpty()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = items.getOrNull(player.currentMediaItemIndex)?.name.orEmpty()
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "Impossibile riprodurre questo file", Toast.LENGTH_SHORT).show()
            }
        }
        player.addListener(listener)
        val activity = context.findActivity()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    BackHandler { onClose() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    if (!isVideo) {
                        // Per la musica i controlli restano sempre visibili.
                        controllerShowTimeoutMs = 0
                        controllerHideOnTouch = false
                    }
                }
            }
        )
        if (!isVideo) {
            Image(
                imageVector = AudioPageIcon,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(width = 96.dp, height = 120.dp)
            )
        }
        ViewerBar(title = title, onClose = onClose, modifier = Modifier.align(Alignment.TopCenter))
    }
}
