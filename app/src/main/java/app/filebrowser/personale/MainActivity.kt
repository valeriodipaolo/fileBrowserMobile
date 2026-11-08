package app.filebrowser.personale

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    // Aumenta a ogni ritorno nell'app, così dopo aver concesso "Accesso a tutti i file" lo stato si aggiorna.
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(resumeTick)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

private fun hasStorageAccess(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun App(resumeTick: Int) {
    val context = LocalContext.current
    // resumeTick come chiave: il controllo si ripete ogni volta che l'app torna in primo piano.
    var granted by remember(resumeTick) { mutableStateOf(hasStorageAccess(context)) }

    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasStorageAccess(context)
    }

    if (granted) {
        Browser()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Serve l'accesso a tutti i file", style = MaterialTheme.typography.titleLarge)
            Text(
                "Per mostrare e gestire tutti i file Android richiede un permesso speciale, da concedere nelle impostazioni.",
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    legacyLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }) { Text("Concedi accesso") }
        }
    }
}

private sealed interface DialogState {
    data class Rename(val file: File) : DialogState
    data class Delete(val file: File) : DialogState
    data class Password(val archive: File, val destination: File, val wrong: Boolean) : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Browser() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val start = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(start) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    val entries = remember(current, refresh) { listEntries(current) }

    // Il tasto indietro risale di una cartella finché non si torna alla memoria interna, poi esce dall'app.
    BackHandler(enabled = current != start && current.parentFile != null) {
        current = current.parentFile ?: start
    }

    fun extract(archive: File, destination: File, password: String?) {
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { extractZip(archive, destination, password) }
            busy = false
            when (result) {
                ExtractResult.Ok -> {
                    refresh++
                    snackbar.showSnackbar("Estratto in ${destination.name}")
                }
                ExtractResult.NeedsPassword -> dialog = DialogState.Password(archive, destination, wrong = false)
                ExtractResult.WrongPassword -> dialog = DialogState.Password(archive, destination, wrong = true)
                is ExtractResult.Failed -> snackbar.showSnackbar("Estrazione non riuscita: ${result.message}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current.absolutePath,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                },
                navigationIcon = {
                    TextButton(
                        enabled = current.parentFile != null,
                        onClick = { current = current.parentFile ?: current }
                    ) { Text("↑", fontSize = 22.sp) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when {
                entries == null -> Text("Cartella non leggibile", modifier = Modifier.padding(16.dp))
                entries.isEmpty() -> Text("Cartella vuota", modifier = Modifier.padding(16.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.absolutePath }) { file ->
                        FileRow(
                            file = file,
                            onOpen = { if (file.isDirectory) current = file else openFile(context, file) },
                            onOpenWith = { openFile(context, file, forceChooser = true) },
                            onExtractHere = { extract(file, file.parentFile ?: current, null) },
                            onExtractToFolder = {
                                val parent = file.parentFile ?: current
                                extract(file, uniqueDir(parent, file.nameWithoutExtension), null)
                            },
                            onRename = { dialog = DialogState.Rename(file) },
                            onDelete = { dialog = DialogState.Delete(file) }
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is DialogState.Rename -> {
            var name by remember(d.file) { mutableStateOf(d.file.name) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Rinomina") },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        val target = File(d.file.parentFile, name.trim())
                        dialog = null
                        scope.launch {
                            if (name.isBlank() || target.exists()) {
                                snackbar.showSnackbar("Nome non valido o già esistente")
                            } else if (d.file.renameTo(target)) {
                                refresh++
                            } else {
                                snackbar.showSnackbar("Impossibile rinominare")
                            }
                        }
                    }) { Text("Rinomina") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annulla") } }
            )
        }

        is DialogState.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Elimina") },
            text = {
                Text(
                    if (d.file.isDirectory) "Eliminare la cartella «${d.file.name}» e tutto il suo contenuto?"
                    else "Eliminare «${d.file.name}»?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { d.file.deleteRecursively() }
                        refresh++
                        if (!ok) snackbar.showSnackbar("Eliminazione non riuscita")
                    }
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annulla") } }
        )

        is DialogState.Password -> {
            var password by remember(d) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Archivio protetto") },
                text = {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(if (d.wrong) "Password errata, riprova" else "Password") },
                        isError = d.wrong,
                        visualTransformation = PasswordVisualTransformation()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        extract(d.archive, d.destination, password)
                    }) { Text("Estrai") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annulla") } }
            )
        }

        null -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: File,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onExtractHere: () -> Unit,
    onExtractToFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val icon = when {
        file.isDirectory -> "📁"
        file.isArchive() -> "🗜️"
        else -> "📄"
    }
    val details = remember(file) {
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.lastModified()))
        if (file.isDirectory) date else "${formatSize(file.length())} · $date"
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp, modifier = Modifier.padding(end = 16.dp))
            Column {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (file.isArchive()) {
                DropdownMenuItem(text = { Text("Estrai qui") }, onClick = { menu = false; onExtractHere() })
                DropdownMenuItem(
                    text = { Text("Estrai in «${file.nameWithoutExtension}»") },
                    onClick = { menu = false; onExtractToFolder() }
                )
            }
            DropdownMenuItem(text = { Text("Apri") }, onClick = { menu = false; onOpen() })
            if (file.isFile) {
                DropdownMenuItem(text = { Text("Apri con...") }, onClick = { menu = false; onOpenWith() })
            }
            DropdownMenuItem(text = { Text("Rinomina") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Elimina") }, onClick = { menu = false; onDelete() })
        }
    }
}
