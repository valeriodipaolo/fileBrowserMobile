package app.filebrowser.personale

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_TEXT_BYTES = 2L * 1024 * 1024

private sealed interface TextLoad {
    data class Ok(val content: String) : TextLoad
    data class Failed(val message: String) : TextLoad
}

/** Da chiamare fuori dal thread principale. Rifiuta file troppo grandi o che non sembrano testo. */
private fun readTextFile(file: File): TextLoad {
    return try {
        if (file.length() > MAX_TEXT_BYTES) {
            return TextLoad.Failed("File troppo grande per il lettore (massimo 2 MB). Usa \"Apri con...\".")
        }
        val bytes = file.readBytes()
        val head = minOf(bytes.size, 8000)
        for (i in 0 until head) {
            if (bytes[i] == 0.toByte()) return TextLoad.Failed("Il file non sembra un file di testo.")
        }
        TextLoad.Ok(String(bytes, Charsets.UTF_8))
    } catch (e: Exception) {
        TextLoad.Failed(e.message ?: "Impossibile leggere il file")
    }
}

/** Lettore (sola lettura, di default) ed editor di testo a schermo intero. Assume la codifica UTF-8. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditor(file: File, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var original by remember(file) { mutableStateOf<String?>(null) }
    var text by remember(file) { mutableStateOf("") }
    var error by remember(file) { mutableStateOf<String?>(null) }
    // Un file vuoto (per esempio appena creato) si apre subito in modifica: non c'è nulla da leggere.
    var editMode by remember(file) { mutableStateOf(file.length() == 0L) }
    var askDiscard by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        when (val result = withContext(Dispatchers.IO) { readTextFile(file) }) {
            is TextLoad.Ok -> {
                original = result.content
                text = result.content
            }
            is TextLoad.Failed -> error = result.message
        }
    }

    val modified = original != null && text != original

    fun save(closeAfter: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { file.writeText(text) }.isSuccess }
            if (ok) {
                original = text
                if (closeAfter) onClose() else snackbar.showSnackbar("Salvato")
            } else {
                snackbar.showSnackbar("Salvataggio non riuscito")
            }
        }
    }

    fun requestClose() {
        if (modified) askDiscard = true else onClose()
    }

    BackHandler { requestClose() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        file.name + if (modified) " •" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    TextButton(onClick = { requestClose() }) { Text("←", fontSize = 22.sp) }
                },
                actions = {
                    if (original != null) {
                        if (editMode) {
                            TextButton(enabled = modified, onClick = { save(closeAfter = false) }) { Text("Salva") }
                        }
                        TextButton(onClick = { editMode = !editMode }) {
                            Text(if (editMode) "Lettura" else "Modifica")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val message = error
        when {
            message != null -> Text(message, modifier = Modifier.padding(padding).padding(16.dp))
            original == null -> LinearProgressIndicator(modifier = Modifier.padding(padding).fillMaxWidth())
            else -> TextField(
                value = text,
                onValueChange = { text = it },
                readOnly = !editMode,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.padding(padding).fillMaxSize()
            )
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Modifiche non salvate") },
            text = { Text("Salvare le modifiche a «${file.name}»?") },
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    save(closeAfter = true)
                }) { Text("Salva") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askDiscard = false; onClose() }) { Text("Scarta") }
                    TextButton(onClick = { askDiscard = false }) { Text("Annulla") }
                }
            }
        )
    }
}
