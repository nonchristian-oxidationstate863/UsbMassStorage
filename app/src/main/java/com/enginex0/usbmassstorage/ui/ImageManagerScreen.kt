package com.enginex0.usbmassstorage.ui

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enginex0.usbmassstorage.R
import com.enginex0.usbmassstorage.daemon.DeviceInfo
import com.enginex0.usbmassstorage.daemon.DeviceType
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val IMAGES_DIR = "/data/adb/Usbmanagement/images"

private data class DiskImage(
    val path: String,
    val name: String,
    val size: Long,
    val extension: String,
    val lastModified: Long,
    val fsType: String?
)

private val SAFE_NAME_RE = Regex("[a-zA-Z0-9._-]+")
private val UNSAFE_CHAR_RE = Regex("[^a-zA-Z0-9._-]")
private val IMAGE_EXTENSIONS = setOf("img", "iso", "bin", "raw")

private fun scanImages(): List<DiskImage> {
    val r = Shell.cmd("ls -1 '$IMAGES_DIR' 2>/dev/null").exec()
    if (!r.isSuccess || r.out.isEmpty()) return emptyList()
    val names = r.out.filter { name ->
        name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS
            && name.matches(SAFE_NAME_RE)
    }
    if (names.isEmpty()) return emptyList()

    val script = names.joinToString("; ") { name ->
        "echo \"$(stat -c '%s %Y' '$IMAGES_DIR/$name' 2>/dev/null)|$(blkid -s TYPE -o value '$IMAGES_DIR/$name' 2>/dev/null)\""
    }
    val batch = try { Shell.cmd(script).exec() } catch (_: Exception) { null }
    val lines = batch?.out ?: emptyList()

    return names.zip(lines).mapNotNull { (name, line) ->
        val pipe = line.split("|", limit = 2)
        val statParts = (pipe.getOrNull(0) ?: "").split(" ", limit = 2)
        val size = statParts.getOrNull(0)?.toLongOrNull() ?: 0L
        val mtime = (statParts.getOrNull(1)?.toLongOrNull() ?: 0L) * 1000L
        if (size == 0L && mtime == 0L) return@mapNotNull null
        val ext = name.substringAfterLast('.').lowercase()
        val fsType = pipe.getOrNull(1)?.trim()?.uppercase()?.ifEmpty { null }
        DiskImage("$IMAGES_DIR/$name", name, size, ext, mtime, fsType)
    }.sortedByDescending { it.lastModified }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun formatDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy  h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageManagerScreen(
    mountedPaths: Set<String>,
    onBack: () -> Unit,
    onMount: (DeviceInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<DiskImage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DiskImage?>(null) }
    var exportingPath by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showBatchDelete by remember { mutableStateOf(false) }

    var importing by remember { mutableStateOf(false) }

    val inSelectionMode = selected.isNotEmpty()
    val exportSuccessMsg = stringResource(R.string.images_export_success)
    val exportFailedMsg = stringResource(R.string.images_export_failed)
    val importSuccessMsg = stringResource(R.string.images_import_success)
    val importFailedMsg = stringResource(R.string.images_import_failed)

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.img"
                    val safeName = name.replace(UNSAFE_CHAR_RE, "_")
                    // Avoid overwriting existing files — append counter on collision
                    val baseName = safeName.substringBeforeLast('.')
                    val ext = safeName.substringAfterLast('.', "img")
                    val existing = Shell.cmd("ls '$IMAGES_DIR' 2>/dev/null").exec()
                    val existingNames = if (existing.isSuccess) existing.out.toSet() else emptySet()
                    var finalName = safeName
                    var counter = 1
                    while (finalName in existingNames) {
                        finalName = "${baseName}_$counter.$ext"
                        counter++
                    }
                    val dest = "$IMAGES_DIR/$finalName"
                    val fd = context.contentResolver.openInputStream(uri)
                    if (fd != null) {
                        val tmpPath = "${context.cacheDir}/$safeName"
                        java.io.File(tmpPath).outputStream().use { out -> fd.use { it.copyTo(out) } }
                        Shell.cmd("mv '$tmpPath' '$dest' && chown system:system '$dest' && chmod 666 '$dest'").exec().isSuccess
                    } else false
                } catch (_: Exception) { false }
            }
            importing = false
            Toast.makeText(context, if (success) importSuccessMsg else importFailedMsg, Toast.LENGTH_SHORT).show()
            if (success) refreshTrigger++
        }
    }

    val mountedNames = remember(mountedPaths) {
        mountedPaths.mapNotNull { path ->
            if ("/images/" in path) path.substringAfterLast('/') else null
        }.toSet()
    }

    LaunchedEffect(refreshTrigger) {
        loading = true
        images = withContext(Dispatchers.IO) { scanImages() }
        loading = false
    }

    deleteTarget?.let { image ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.images_delete_title)) },
            text = { Text(stringResource(R.string.images_delete_message, image.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val targetPath = image.path
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) { Shell.cmd("rm -f '$targetPath'").exec() }
                        refreshTrigger++
                    }
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showClearConfirm) {
        val count = images.count { it.name !in mountedNames }
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.images_clear_unmounted_title)) },
            text = { Text(stringResource(R.string.images_clear_unmounted_message, count)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            images.filter { it.name !in mountedNames }
                                .forEach { Shell.cmd("rm -f '${it.path}'").exec() }
                        }
                        selected = emptySet()
                        refreshTrigger++
                    }
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showBatchDelete) {
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text(stringResource(R.string.images_batch_delete_title, selected.size)) },
            text = { Text(stringResource(R.string.images_batch_delete_message, selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = selected.toSet()
                    showBatchDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            toDelete.forEach { path ->
                                val name = path.substringAfterLast('/')
                                if (name !in mountedNames) Shell.cmd("rm -f '$path'").exec()
                            }
                        }
                        selected = emptySet()
                        refreshTrigger++
                    }
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (inSelectionMode) Text(stringResource(R.string.images_selected_count, selected.size))
                    else Text(stringResource(R.string.images_title))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (inSelectionMode) {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (!inSelectionMode) {
                        IconButton(onClick = { showCreateSheet = true }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.images_create_new))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.images_import)) },
                                    enabled = !importing,
                                    onClick = {
                                        showMenu = false
                                        importPicker.launch(arrayOf("application/octet-stream", "*/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.images_clear_unmounted)) },
                                    onClick = {
                                        showMenu = false
                                        if (images.isEmpty() || images.none { it.name !in mountedNames }) {
                                            Toast.makeText(context, context.getString(R.string.images_no_unmounted), Toast.LENGTH_SHORT).show()
                                        } else {
                                            showClearConfirm = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (inSelectionMode) {
                FloatingActionButton(
                    onClick = { showBatchDelete = true },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        }
    ) { padding ->
        when {
            loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            images.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.images_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            stringResource(R.string.images_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { showCreateSheet = true }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.images_create_new))
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(images, key = { it.path }) { image ->
                        val isMounted = image.name in mountedNames
                        ImageCard(
                            image = image,
                            isMounted = isMounted,
                            isExporting = exportingPath == image.path,
                            inSelectionMode = inSelectionMode,
                            isSelected = image.path in selected,
                            onMount = {
                                onMount(
                                    DeviceInfo(
                                        Uri.fromFile(java.io.File(image.path)),
                                        DeviceType.DISK_RW,
                                        image.fsType
                                    )
                                )
                            },
                            onExport = {
                                val dlDir = Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                                )
                                exportingPath = image.path
                                scope.launch {
                                    val success = withContext(Dispatchers.IO) {
                                        try {
                                            dlDir.mkdirs()
                                            Shell.cmd("cp '${image.path}' '${dlDir.absolutePath}/${image.name}'").exec().isSuccess
                                        } catch (_: Exception) { false }
                                    }
                                    exportingPath = null
                                    Toast.makeText(
                                        context,
                                        if (success) exportSuccessMsg else exportFailedMsg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDelete = { deleteTarget = image },
                            onToggleSelect = {
                                if (!isMounted) {
                                    selected = if (image.path in selected) selected - image.path else selected + image.path
                                }
                            }
                        )
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.images_storage_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateImageSheet(
            onDismiss = { showCreateSheet = false },
            onCreated = { deviceInfo ->
                showCreateSheet = false
                onMount(deviceInfo)
                refreshTrigger++
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageCard(
    image: DiskImage,
    isMounted: Boolean,
    isExporting: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onMount: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glow = rememberInfiniteTransition(label = "card")
    val borderAlpha by glow.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label = "border"
    )

    val tint = if (isMounted) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, tint.copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
            )
            .combinedClickable(
                onClick = { if (inSelectionMode && !isMounted) onToggleSelect() },
                onLongClick = { if (!isMounted) onToggleSelect() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    isSelected -> Icons.Filled.CheckCircle
                    image.extension == "iso" -> Icons.Filled.Album
                    else -> Icons.Filled.Storage
                }
                Icon(
                    icon, null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else tint
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            image.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isMounted) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.tertiary,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.images_mounted),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                        }
                    }

                    val subtitle = buildString {
                        append(formatSize(image.size))
                        if (image.fsType != null) append("  \u00b7  ${image.fsType}")
                        append("  \u00b7  ${formatDate(image.lastModified)}")
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            if (!inSelectionMode) {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMount,
                        enabled = !isMounted,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) { Text(stringResource(R.string.action_mount)) }

                    OutlinedButton(
                        onClick = onExport,
                        enabled = !isExporting,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(stringResource(R.string.action_export))
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !isMounted,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}
