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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    data class DeleteMany(val files: List<File>) : DialogState
    data object NewFile : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Browser() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val start = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(start) }
    // Percorsi selezionati per l'eliminazione multipla; si azzera cambiando cartella. Non vuoto = modalità selezione.
    var selected by remember(current) { mutableStateOf(setOf<String>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    // File aperto nel lettore/editor di testo (percorso come stringa, così sopravvive alla rotazione).
    var editPath by rememberSaveable { mutableStateOf<String?>(null) }

    var filter by rememberSaveable { mutableStateOf(FileCategory.ALL) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    // Di default: per data, dal più recente. Con tutti i criteri disabilitati si ordina per nome crescente.
    var sortSpec by rememberSaveable { mutableStateOf(encodeSort(listOf(SortRule(SortKey.DATE, descending = true)))) }
    var menuOpen by remember { mutableStateOf(false) }
    val sortRules = remember(sortSpec) { decodeSort(sortSpec) }

    val entries = remember(current, refresh, sortSpec) { listEntries(current, buildComparator(sortRules)) }
    val visible = remember(entries, filter, showHidden) {
        entries?.filter { (showHidden || !it.isHidden()) && it.matchesFilter(filter) }
    }

    // Il tasto indietro risale di una cartella finché non si torna alla memoria interna, poi esce dall'app.
    BackHandler(enabled = editPath == null && selected.isEmpty() && current != start && current.parentFile != null) {
        current = current.parentFile ?: start
    }
    // Nella modalità selezione il tasto indietro esce dalla selezione.
    BackHandler(enabled = editPath == null && selected.isNotEmpty()) { selected = emptySet() }

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

    val editing = editPath
    if (editing != null) {
        TextEditor(File(editing), onClose = { editPath = null; refresh++ })
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { dialog = DialogState.NewFile }) {
                Text("+  Nuovo file di testo")
            }
        },
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selected.size} selezionati", fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(CloseIcon, contentDescription = "Annulla selezione")
                        }
                    },
                    actions = {
                        TextButton(onClick = { selected = visible.orEmpty().map { it.absolutePath }.toSet() }) {
                            Text("Tutti")
                        }
                        IconButton(onClick = { dialog = DialogState.DeleteMany(selected.map { File(it) }) }) {
                            Icon(TrashIcon, contentDescription = "Elimina selezionati")
                        }
                    }
                )
            } else TopAppBar(
                title = {
                    Text(
                        current.absolutePath,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        enabled = current.parentFile != null,
                        onClick = { current = current.parentFile ?: current },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Icon(ArrowUpIcon, contentDescription = "Cartella superiore") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(MoreVertIcon, contentDescription = "Altre opzioni")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Mostra file nascosti") },
                                trailingIcon = { if (showHidden) Text("✓") },
                                onClick = {
                                    showHidden = !showHidden
                                    menuOpen = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FILTER_CATEGORIES) { category ->
                    FilterChip(
                        selected = filter == category,
                        onClick = { filter = category },
                        label = { Text(if (category.icon.isEmpty()) category.label else "${category.icon} ${category.label}") }
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortKey.entries.forEach { key ->
                    val rule = sortRules.firstOrNull { it.key == key }
                    FilterChip(
                        selected = rule != null,
                        onClick = { sortSpec = encodeSort(cycleSort(sortRules, key)) },
                        label = {
                            Text(
                                if (rule == null) key.label
                                else key.label + if (rule.descending) " ↓" else " ↑"
                            )
                        }
                    )
                }
            }
            when {
                entries == null || visible == null -> Text("Cartella non leggibile", modifier = Modifier.padding(16.dp))
                entries.isEmpty() -> Text("Cartella vuota", modifier = Modifier.padding(16.dp))
                visible.isEmpty() -> Text("Nessun elemento con questi filtri", modifier = Modifier.padding(16.dp))
                // Il margine in fondo evita che il pulsante "Nuovo file" copra l'ultima riga.
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(visible, key = { it.absolutePath }) { file ->
                        FileRow(
                            file = file,
                            selectionMode = selected.isNotEmpty(),
                            isSelected = file.absolutePath in selected,
                            onToggleSelect = {
                                val path = file.absolutePath
                                selected = if (path in selected) selected - path else selected + path
                            },
                            onStartSelection = { selected = setOf(file.absolutePath) },
                            onOpen = {
                                when {
                                    file.isDirectory -> current = file
                                    file.category() == FileCategory.TEXT -> editPath = file.absolutePath
                                    else -> openFile(context, file)
                                }
                            },
                            onOpenAsText = { editPath = file.absolutePath },
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

        is DialogState.DeleteMany -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Elimina") },
            text = {
                Text(
                    if (d.files.size == 1) "Eliminare «${d.files[0].name}»?"
                    else "Eliminare ${d.files.size} elementi? Le cartelle saranno eliminate con tutto il contenuto."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) { d.files.count { it.deleteRecursively() } }
                        selected = emptySet()
                        refresh++
                        if (deleted < d.files.size) snackbar.showSnackbar("Eliminati $deleted di ${d.files.size}")
                    }
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annulla") } }
        )

        DialogState.NewFile -> {
            val suggested = remember { uniqueFileName(current, "nuovo", "txt") }
            // Preseleziona la parte prima dell'estensione, così si può scrivere subito il nome.
            var field by remember { mutableStateOf(TextFieldValue(suggested, TextRange(0, suggested.length - 4))) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Nuovo file di testo") },
                text = {
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        label = { Text("Nome del file") },
                        modifier = Modifier.focusRequester(focus)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = field.text.trim()
                        val target = File(current, name)
                        dialog = null
                        scope.launch {
                            if (name.isEmpty() || name == "." || name == ".." || name.contains('/')) {
                                snackbar.showSnackbar("Nome non valido")
                            } else if (target.exists()) {
                                snackbar.showSnackbar("Esiste già un elemento con questo nome")
                            } else {
                                val created = withContext(Dispatchers.IO) {
                                    runCatching { target.createNewFile() }.getOrDefault(false)
                                }
                                if (created) {
                                    refresh++
                                    editPath = target.absolutePath
                                } else {
                                    snackbar.showSnackbar("Impossibile creare il file")
                                }
                            }
                        }
                    }) { Text("Crea") }
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
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onStartSelection: () -> Unit,
    onOpen: () -> Unit,
    onOpenAsText: () -> Unit,
    onOpenWith: () -> Unit,
    onExtractHere: () -> Unit,
    onExtractToFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val icon = file.category().icon
    val details = remember(file) {
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.lastModified()))
        if (file.isDirectory) date else "${formatSize(file.length())} · $date"
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .combinedClickable(
                    onClick = if (selectionMode) onToggleSelect else onOpen,
                    onLongClick = if (selectionMode) onToggleSelect else { { menu = true } }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
            }
            val thumb = if (file.isImage()) {
                produceState<android.graphics.Bitmap?>(initialValue = null, file) {
                    value = withContext(Dispatchers.IO) { loadThumbnail(file, 160) }
                }.value
            } else null
            Box(modifier = Modifier.padding(end = 16.dp).size(48.dp), contentAlignment = Alignment.Center) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    when (file.category()) {
                        FileCategory.PDF -> TypeBadge("PDF", Color(0xFFD32F2F))
                        FileCategory.ARCHIVES -> Image(
                            imageVector = ArchiveBooksIcon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        else -> Text(icon, fontSize = 24.sp)
                    }
                }
            }
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
            DropdownMenuItem(text = { Text("Seleziona") }, onClick = { menu = false; onStartSelection() })
            if (file.isArchive()) {
                DropdownMenuItem(text = { Text("Estrai qui") }, onClick = { menu = false; onExtractHere() })
                DropdownMenuItem(
                    text = { Text("Estrai in «${file.nameWithoutExtension}»") },
                    onClick = { menu = false; onExtractToFolder() }
                )
            }
            DropdownMenuItem(text = { Text("Apri") }, onClick = { menu = false; onOpen() })
            if (file.isFile) {
                DropdownMenuItem(text = { Text("Leggi / modifica come testo") }, onClick = { menu = false; onOpenAsText() })
                DropdownMenuItem(text = { Text("Apri con...") }, onClick = { menu = false; onOpenWith() })
            }
            DropdownMenuItem(text = { Text("Rinomina") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Elimina") }, onClick = { menu = false; onDelete() })
        }
    }
}

/** Riquadro colorato con una sigla (PDF, ZIP, RAR...) al posto dell'emoji. */
@Composable
private fun TypeBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
