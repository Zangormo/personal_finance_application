package com.example.financeapplication.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.financeapplication.datastores.AttachmentType
import com.example.financeapplication.datastores.Note
import com.example.financeapplication.datastores.NoteAttachment
import com.example.financeapplication.datastores.NotesDataStore
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.ui.theme.Negative
import com.example.financeapplication.utils.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

const val NEW_NOTE = "new"

/* ------------------------------------------------------------------------------------------ */
/* List of notes                                                                              */
/* ------------------------------------------------------------------------------------------ */

@Composable
fun NotesScreen(onBackPress: () -> Unit, onOpenNote: (String) -> Unit) {
    val context = LocalContext.current
    val notes by NotesDataStore.getNotes(context).collectAsState(initial = null)
    var query by remember { mutableStateOf("") }

    val visible = notes.orEmpty()
        .filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) || it.text.contains(query, ignoreCase = true)
        }
        .sortedByDescending { it.updatedAt }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenNote(NEW_NOTE) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Note") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenHeader("Notes", onBack = onBackPress)
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (notes.orEmpty().size > 3) {
                    item(key = "search") {
                        AppTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = "Search",
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                if (notes != null && visible.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            if (query.isBlank()) "No notes yet. Tap \"Note\" to write one." else "Nothing found",
                            Modifier.animateItem()
                        )
                    }
                }
                items(visible, key = { it.id }) { note ->
                    NoteCard(note, Modifier.animateItem()) { onOpenNote(note.id) }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier.fillMaxWidth(), onClick = onClick) {
        Text(
            note.title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (note.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (note.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                note.text.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Dates.dateTime(note.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            val count = note.attachments.orEmpty().size
            if (count > 0) {
                Icon(
                    Icons.Default.AttachFile, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* One note                                                                                   */
/* ------------------------------------------------------------------------------------------ */

@Composable
fun NoteScreen(noteId: String, onBackPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = noteId == NEW_NOTE
    val id = remember { if (isNew) UUID.randomUUID().toString() else noteId }

    var loaded by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<NoteAttachment>>(emptyList()) }
    var editing by remember { mutableStateOf(isNew) }

    var addingLink by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<NoteAttachment?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val n = NotesDataStore.getNotes(context).first().find { it.id == id }
        if (n != null) {
            original = n
            title = n.title
            text = n.text
            attachments = n.attachments.orEmpty()
        }
        loaded = true
    }

    fun current(): Note {
        val base = original ?: Note(id = id)
        return base.copy(title = title, text = text, attachments = attachments)
    }

    /** Saves when something changed. An empty note is not kept. */
    suspend fun persist() {
        if (!loaded) return
        val note = current()
        val saved = original
        if (note.isEmpty) {
            if (saved != null) NotesDataStore.remove(context, saved)
            original = null
            return
        }
        if (saved != null && saved.title == note.title && saved.text == note.text && saved.attachments.orEmpty() == attachments) return
        val toSave = note.copy(updatedAt = System.currentTimeMillis())
        NotesDataStore.save(context, toSave)
        original = toSave
    }

    // Autosave while typing
    LaunchedEffect(title, text, attachments) {
        if (!loaded) return@LaunchedEffect
        delay(600)
        persist()
    }

    val leave: () -> Unit = {
        scope.launch {
            persist()
            onBackPress()
        }
    }
    BackHandler { if (editing && !isNew && original != null) editing = false else leave() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val imported = uris.mapNotNull { NotesDataStore.importFile(context, it) }
            importing = false
            if (imported.size < uris.size) Toast.makeText(context, "Some files could not be attached", Toast.LENGTH_SHORT).show()
            attachments = attachments + imported
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ScreenHeader("", onBack = leave) {
            IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Icon(Icons.Default.AttachFile, "Attach file") }
            IconButton(onClick = { addingLink = true }) { Icon(Icons.Default.Link, "Add link") }
            Crossfade(targetState = editing, label = "editToggle") { isEditing ->
                if (isEditing) {
                    IconButton(onClick = { editing = false }) { Icon(Icons.Default.Check, "Done", tint = MaterialTheme.colorScheme.primary) }
                } else {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit") }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete note", color = Negative) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Negative) },
                        onClick = { menu = false; deleting = true }
                    )
                }
            }
        }

        if (!loaded) return@Column

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            AnimatedContent(
                targetState = editing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "noteMode"
            ) { isEditing ->
                if (isEditing) {
                    NoteEditor(
                        title = title,
                        onTitle = { title = it },
                        text = text,
                        onText = { text = it },
                        focusTitle = isNew && original == null && title.isEmpty()
                    )
                } else {
                    NoteViewer(title = title, text = text, updatedAt = original?.updatedAt, onEdit = { editing = true })
                }
            }

            if (attachments.isNotEmpty() || importing) {
                Spacer(Modifier.height(24.dp))
                SectionTitle("Attachments", trailing = if (importing) "Copying…" else attachments.size.toString())
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachments.forEach { a ->
                        AttachmentRow(
                            attachment = a,
                            onOpen = { openAttachment(context, a) },
                            onRemove = { removing = a }
                        )
                    }
                }
            }
        }
    }

    if (addingLink) {
        LinkDialog(
            onDismiss = { addingLink = false },
            onSave = { url, name ->
                attachments = attachments + NoteAttachment(type = AttachmentType.LINK, name = name, url = url)
                addingLink = false
            }
        )
    }
    removing?.let { a ->
        ConfirmDialog(
            title = "Remove attachment?",
            text = "\"${a.name}\" will be removed from the note.",
            confirmText = "Remove",
            onConfirm = {
                attachments = attachments.filter { it.id != a.id }
                if (a.type == AttachmentType.FILE) NotesDataStore.deleteFile(context, a)
                removing = null
            },
            onDismiss = { removing = null }
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete note?",
            text = "The note and its attached files will be deleted.",
            onConfirm = {
                deleting = false
                scope.launch {
                    NotesDataStore.remove(context, current())
                    original = null
                    title = ""; text = ""; attachments = emptyList()
                    onBackPress()
                }
            },
            onDismiss = { deleting = false }
        )
    }
}

@Composable
private fun NoteEditor(
    title: String,
    onTitle: (String) -> Unit,
    text: String,
    onText: (String) -> Unit,
    focusTitle: Boolean,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusTitle) titleFocus.requestFocus() }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val hint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val cursor = SolidColor(MaterialTheme.colorScheme.primary)

    Column(Modifier.fillMaxWidth()) {
        val titleStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = onSurface)
        BasicTextField(
            value = title,
            onValueChange = onTitle,
            textStyle = titleStyle,
            cursorBrush = cursor,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(titleFocus)
                .padding(vertical = 8.dp),
            decorationBox = { inner ->
                if (title.isEmpty()) Text("Title", style = titleStyle.copy(color = hint))
                inner()
            }
        )
        Spacer(Modifier.height(8.dp))
        val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = onSurface)
        BasicTextField(
            value = text,
            onValueChange = onText,
            textStyle = bodyStyle,
            cursorBrush = cursor,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Start writing…", style = bodyStyle.copy(color = hint))
                inner()
            }
        )
    }
}

@Composable
private fun NoteViewer(title: String, text: String, updatedAt: Long?, onEdit: () -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val body = remember(text, linkColor) { linkify(text, linkColor) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (updatedAt != null) {
            Text(
                "Edited " + Dates.dateTime(updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        if (text.isBlank()) {
            Text(
                "Tap to start writing",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FieldShape)
                    .clickable(onClick = onEdit)
                    .padding(vertical = 12.dp)
            )
        } else {
            Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Makes web addresses in the text clickable. */
private fun linkify(text: String, color: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val matcher = Patterns.WEB_URL.matcher(text)
    val style = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    while (matcher.find()) {
        val raw = matcher.group() ?: continue
        addLink(LinkAnnotation.Url(normalizeUrl(raw), style), matcher.start(), matcher.end())
    }
}

private fun normalizeUrl(raw: String): String {
    val url = raw.trim()
    return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(url) || url.startsWith("mailto:")) url else "https://$url"
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        else -> "%.1f MB".format(bytes / (1024f * 1024f))
    }
}

private fun openAttachment(context: Context, a: NoteAttachment) {
    try {
        val intent = when (a.type) {
            AttachmentType.LINK -> Intent(Intent.ACTION_VIEW, Uri.parse(a.url ?: return))
            AttachmentType.FILE -> {
                val file = NotesDataStore.fileOf(context, a)?.takeIf { it.exists() } ?: run {
                    Toast.makeText(context, "The file is missing", Toast.LENGTH_SHORT).show()
                    return
                }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, a.mimeType ?: context.contentResolver.getType(uri) ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, a.name))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AttachmentRow(attachment: NoteAttachment, onOpen: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val isImage = attachment.type == AttachmentType.FILE && attachment.mimeType?.startsWith("image/") == true
    val image by produceState<ImageBitmap?>(null, attachment.id) {
        if (isImage) value = withContext(Dispatchers.IO) { loadPreview(context, attachment) }
    }

    Card(
        onClick = onOpen,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clipToBounds()
            )
        }
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        attachment.type == AttachmentType.LINK -> Icons.Default.Link
                        isImage -> Icons.Default.Image
                        else -> Icons.Default.InsertDriveFile
                    },
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(attachment.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when (attachment.type) {
                    AttachmentType.LINK -> attachment.url.orEmpty().takeIf { it != attachment.name }
                    AttachmentType.FILE -> formatSize(attachment.size).takeIf { it.isNotEmpty() }
                }
                if (sub != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Decodes a downscaled preview so big photos do not use much memory. */
private fun loadPreview(context: Context, a: NoteAttachment): ImageBitmap? = try {
    val file = NotesDataStore.fileOf(context, a)
    if (file == null || !file.exists()) null
    else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
} catch (_: Exception) {
    null
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onSave: (url: String, name: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val normalized = normalizeUrl(url)
    val valid = url.isNotBlank() && Patterns.WEB_URL.matcher(url.trim()).matches() || url.trim().contains("://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "Link",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
                AppTextField(value = name, onValueChange = { name = it }, label = "Name (optional)")
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(normalized, name.trim().ifEmpty { url.trim() }) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
