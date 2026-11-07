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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.zIndex
import com.example.financeapplication.datastores.NoteSort
import com.example.financeapplication.datastores.NoteSpan
import com.example.financeapplication.datastores.NoteStyle
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

private fun Note.isSecret() = secret == true

private fun sortNotes(notes: List<Note>, sort: NoteSort): List<Note> = when (sort) {
    NoteSort.EDITED -> notes.sortedByDescending { it.updatedAt }
    NoteSort.CREATED -> notes.sortedByDescending { it.createdAt }
    NoteSort.TITLE -> notes.sortedWith(
        compareBy<Note> { it.title.isBlank() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
    )
    NoteSort.FILES -> notes.sortedWith(
        compareByDescending<Note> { it.attachments.orEmpty().any { a -> a.type == AttachmentType.FILE } }
            .thenByDescending { it.attachments.orEmpty().size }
            .thenByDescending { it.updatedAt }
    )
    // notes that were never placed (new ones) come first, newest on top
    NoteSort.CUSTOM -> notes.sortedWith(
        compareBy<Note> { it.order != null }.thenBy { it.order ?: 0 }.thenByDescending { it.createdAt }
    )
}

@Composable
fun NotesScreen(onBackPress: () -> Unit, onOpenNote: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val notes by NotesDataStore.getNotes(context).collectAsState(initial = null)
    val sort by NotesDataStore.getSort(context).collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = editMode) { editMode = false }

    val all = notes.orEmpty()
    val q = query.trim()
    // A secret note shows up only when its exact title is typed
    val sorted = sortNotes(all, sort ?: NoteSort.EDITED)
    val stored = sorted.filter { n ->
        if (n.isSecret()) q.isNotEmpty() && n.title.trim().equals(q, ignoreCase = true)
        else q.isEmpty() || n.title.contains(q, ignoreCase = true) || n.text.contains(q, ignoreCase = true)
    }

    // While dragging (and until the new order is saved) the list uses a local order of ids
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(stored.map { it.id }, draggingId) {
        if (draggingId == null && localOrder == stored.map { it.id }) localOrder = null
    }
    val visible: List<Note> = localOrder?.let { ids ->
        val byId = stored.associateBy { it.id }
        ids.mapNotNull { byId[it] } + stored.filter { it.id !in ids }
    } ?: stored
    val currentVisible by rememberUpdatedState(visible)
    val currentSorted by rememberUpdatedState(sorted)
    val currentEditMode by rememberUpdatedState(editMode)
    val currentStoredIds by rememberUpdatedState(stored.map { it.id })
    val currentSort by rememberUpdatedState(sort)
    val density = LocalDensity.current
    val canDrag by rememberUpdatedState(q.isEmpty())

    val listState = rememberLazyListState()
    var lastSwappedKey by remember { mutableStateOf<Any?>(null) }

    fun itemAt(y: Float): LazyListItemInfo? {
        val base = listState.layoutInfo.viewportStartOffset
        return listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset - base && y <= it.offset - base + it.size }
    }

    fun onDragMove() {
        val id = draggingId ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == NOTE_KEY + id } ?: return
        val center = dragTop + current.size / 2f
        fun LazyListItemInfo.hit() = center >= offset && center <= offset + size
        lastSwappedKey?.let { k -> if (items.none { it.key == k && it.hit() }) lastSwappedKey = null }
        val target = items.firstOrNull {
            it.key != current.key && it.key != lastSwappedKey && (it.key as? String)?.startsWith(NOTE_KEY) == true && it.hit()
        } ?: return
        lastSwappedKey = target.key
        val ids = localOrder ?: currentVisible.map { it.id }
        val from = ids.indexOf(id)
        val to = ids.indexOfFirst { NOTE_KEY + it == target.key }
        if (from == -1 || to == -1) return
        localOrder = ids.toMutableList().apply { add(to, removeAt(from)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun finishDrag() {
        val ids = localOrder
        if (draggingId != null && ids != null && ids != currentStoredIds) {
            // Hidden (secret) notes keep their places; the visible ones take the new order
            val visibleIds = ids.toSet()
            val iter = ids.iterator()
            val full = currentSorted.map { if (it.id in visibleIds && iter.hasNext()) iter.next() else it.id }
            scope.launch {
                NotesDataStore.reorder(context, full)
                if (currentSort != NoteSort.CUSTOM) NotesDataStore.setSort(context, NoteSort.CUSTOM)
            }
        }
        draggingId = null
        lastSwappedKey = null
    }

    // Scroll the list while a note is held near the top or bottom edge
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        while (true) {
            val info = listState.layoutInfo
            val current = info.visibleItemsInfo.firstOrNull { it.key == NOTE_KEY + draggingId }
            if (current != null) {
                val height = info.viewportSize.height
                // item offsets are relative to the viewport start (which is above the top padding)
                val top = dragTop - info.viewportStartOffset
                val bottom = top + current.size
                val speed = when {
                    top < edge -> -(edge - top) / edge * 24f
                    bottom > height - edge -> (bottom - (height - edge)) / edge * 24f
                    else -> 0f
                }
                if (speed != 0f && listState.scrollBy(speed.coerceIn(-24f, 24f)) != 0f) onDragMove()
            }
            withFrameNanos { }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            AnimatedVisibility(visible = !editMode, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenNote(NEW_NOTE) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Note") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenHeader("Notes", onBack = if (editMode) ({ editMode = false }) else onBackPress) {
                Crossfade(targetState = editMode, label = "notesHeader") { editing ->
                    if (editing) {
                        TextButton(onClick = { editMode = false }) { Text("Done", fontWeight = FontWeight.Bold) }
                    } else {
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                NoteSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        trailingIcon = {
                                            if (option == (sort ?: NoteSort.EDITED)) {
                                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            sortMenu = false
                                            scope.launch {
                                                // Starting a custom order: keep what is on screen now
                                                if (option == NoteSort.CUSTOM && all.none { it.order != null }) {
                                                    NotesDataStore.reorder(context, sorted.map { it.id })
                                                }
                                                NotesDataStore.setSort(context, option)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
            AnimatedVisibility(visible = editMode || (sort == NoteSort.CUSTOM && all.isNotEmpty())) {
                Text(
                    when {
                        !editMode -> "Long press a note to reorder or configure it"
                        q.isNotEmpty() -> "Clear the search to reorder · tap a note to configure it"
                        else -> "Drag notes to reorder · tap a note to configure it"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                )
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = draggingId == null,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val startItem = itemAt(down.position.y) ?: return@awaitEachGesture
                            val key = startItem.key as? String ?: return@awaitEachGesture
                            if (!key.startsWith(NOTE_KEY)) return@awaitEachGesture
                            val id = key.removePrefix(NOTE_KEY)
                            val wasEditMode = currentEditMode
                            var started = false
                            fun lift() {
                                started = true
                                localOrder = null
                                draggingId = id
                                dragTop = startItem.offset.toFloat()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (!wasEditMode) {
                                // Long press: enter the reorder mode and keep holding the note to drag it
                                val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    var moved = Offset.Zero
                                    var cancelled = false
                                    while (!cancelled) {
                                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            cancelled = true
                                        } else {
                                            moved += change.positionChange()
                                            if (moved.getDistance() > viewConfiguration.touchSlop) cancelled = true
                                        }
                                    }
                                    false
                                } ?: true
                                if (!longPressed) return@awaitEachGesture
                                editMode = true
                                if (canDrag) lift() else haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            var slop = Offset.Zero
                            var holding = !wasEditMode
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    // the long press must not also open the note
                                    if (holding) change.consume()
                                    break
                                }
                                val delta = change.positionChange()
                                if (!started) {
                                    slop += delta
                                    if (canDrag && slop.getDistance() > viewConfiguration.touchSlop) {
                                        lift()
                                        holding = true
                                        dragTop += slop.y
                                    }
                                } else {
                                    dragTop += delta.y
                                    onDragMove()
                                }
                                if (started || holding) change.consume()
                            }
                            if (started) finishDrag()
                        }
                    }
            ) {
                if (notes != null && visible.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            if (q.isEmpty()) "No notes yet. Tap \"Note\" to write one." else "Nothing found",
                            Modifier.animateItem()
                        )
                    }
                }
                items(visible, key = { NOTE_KEY + it.id }) { note ->
                    val isDragging = draggingId == note.id
                    val itemModifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == NOTE_KEY + note.id }
                                if (info != null) translationY = dragTop - info.offset
                                scaleX = 1.03f
                                scaleY = 1.03f
                                shadowElevation = 20f
                                shape = CardShape
                            }
                    } else {
                        Modifier.animateItem()
                    }
                    NoteCard(
                        note = note,
                        editMode = editMode,
                        modifier = itemModifier,
                        onConfigure = { configuring = note.id }
                    ) { if (editMode) configuring = note.id else onOpenNote(note.id) }
                }
            }
        }
    }

    configuring?.let { id ->
        val note = all.find { it.id == id }
        if (note == null) {
            configuring = null
        } else {
            NoteSettingsDialog(
                note = note,
                onSecret = { secret -> scope.launch { NotesDataStore.setSecret(context, id, secret) } },
                onDismiss = { configuring = null }
            )
        }
    }
}

private const val NOTE_KEY = "n:"

@Composable
private fun NoteSettingsDialog(note: Note, onSecret: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.title.ifBlank { "Untitled" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                SwitchRow(
                    text = "Secret note",
                    checked = note.isSecret(),
                    onCheckedChange = onSecret,
                    subtitle = "Hidden from the list. It appears only when its exact title is typed into the search."
                )
                if (note.isSecret() && note.title.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This note has no title, so it cannot be found. Give it a title to be able to open it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Negative
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun NoteCard(note: Note, editMode: Boolean, modifier: Modifier, onConfigure: () -> Unit, onClick: () -> Unit) {
    val borderColor by animateColorAsState(
        if (editMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
        label = "noteBorder"
    )
    val preview = remember(note.text, note.spans) {
        val full = richText(note.text, note.spans.orEmpty())
        val start = note.text.indexOfFirst { !it.isWhitespace() }
        if (start == -1) AnnotatedString("") else full.subSequence(start, note.text.indexOfLast { !it.isWhitespace() } + 1)
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(Modifier.padding(start = if (editMode) 4.dp else 16.dp, end = if (editMode) 4.dp else 16.dp)) {
            AnimatedVisibility(visible = editMode, modifier = Modifier.align(Alignment.CenterVertically)) {
                Icon(
                    Icons.Default.DragIndicator, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isSecret()) {
                        Icon(
                            Icons.Default.Lock, "Secret",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        note.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (note.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        preview,
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
            AnimatedVisibility(visible = editMode, modifier = Modifier.align(Alignment.CenterVertically)) {
                IconButton(onClick = onConfigure) {
                    Icon(Icons.Default.Tune, "Configure note", tint = MaterialTheme.colorScheme.primary)
                }
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
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var spans by remember { mutableStateOf<List<NoteSpan>>(emptyList()) }
    // Styles that newly typed text gets (toggled with an empty selection)
    var typing by remember { mutableStateOf<Set<NoteStyle>>(emptySet()) }
    var bodyFocused by remember { mutableStateOf(false) }
    val text = body.text
    var attachments by remember { mutableStateOf<List<NoteAttachment>>(emptyList()) }
    var editing by remember { mutableStateOf(isNew) }

    LaunchedEffect(editing) { if (!editing) bodyFocused = false }

    fun onBodyChange(nv: TextFieldValue) {
        val old = body
        if (nv.text == old.text) {
            if (nv.selection != old.selection && nv.selection.collapsed) typing = stylesAt(spans, nv.selection.start)
            body = nv
            return
        }
        var next = RichValue(nv, adjustSpans(spans, old.text, nv.text, nv.selection, typing))
        continueBullets(old.text, next)?.let { next = it }
        spans = next.spans
        body = next.value
    }

    fun toggle(style: NoteStyle) {
        val sel = body.selection
        if (sel.collapsed) {
            typing = if (style in typing) typing - style else typing + style
        } else {
            spans = toggleStyle(spans, style, sel.min, sel.max, body.text.length)
        }
    }

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
            body = TextFieldValue(n.text, TextRange(n.text.length))
            spans = normalizeSpans(n.spans.orEmpty(), n.text.length)
            attachments = n.attachments.orEmpty()
        }
        loaded = true
    }

    fun current(): Note {
        val base = original ?: Note(id = id)
        return base.copy(title = title, text = text, spans = spans.ifEmpty { null }, attachments = attachments)
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
        if (saved != null && saved.title == note.title && saved.text == note.text &&
            saved.spans.orEmpty() == spans && saved.attachments.orEmpty() == attachments) return
        val toSave = note.copy(updatedAt = System.currentTimeMillis())
        NotesDataStore.save(context, toSave)
        original = toSave
    }

    // Autosave while typing
    LaunchedEffect(title, text, spans, attachments) {
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
                        body = body,
                        onBody = ::onBodyChange,
                        spans = spans,
                        onBodyFocus = { bodyFocused = it },
                        focusTitle = isNew && original == null && title.isEmpty()
                    )
                } else {
                    NoteViewer(title = title, text = text, spans = spans, updatedAt = original?.updatedAt, onEdit = { editing = true })
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

        // Formatting bar right above the keyboard while the text is being edited
        AnimatedVisibility(
            visible = editing && bodyFocused,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val sel = body.selection
            FormatToolbar(
                isActive = { style ->
                    if (sel.collapsed) style in typing else isCovered(spans, style, sel.min, sel.max)
                },
                bulletsActive = run {
                    val lineStart = text.lastIndexOf('\n', (sel.min - 1).coerceAtLeast(-1)) + 1
                    text.startsWith(BULLET, lineStart)
                },
                onToggle = ::toggle,
                onBullets = {
                    val next = toggleBullets(RichValue(body, spans))
                    spans = next.spans
                    body = next.value
                },
                onClear = {
                    if (sel.collapsed) typing = emptySet()
                    else spans = clearStyles(spans, sel.min, sel.max, text.length)
                }
            )
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
                    title = ""; body = TextFieldValue(""); spans = emptyList(); attachments = emptyList()
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
    body: TextFieldValue,
    onBody: (TextFieldValue) -> Unit,
    spans: List<NoteSpan>,
    onBodyFocus: (Boolean) -> Unit,
    focusTitle: Boolean,
) {
    val text = body.text
    val transformation = remember(spans) { RichTransformation(spans) }
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
            value = body,
            onValueChange = onBody,
            textStyle = bodyStyle,
            cursorBrush = cursor,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .onFocusChanged { onBodyFocus(it.isFocused) },
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Start writing…", style = bodyStyle.copy(color = hint))
                inner()
            }
        )
    }
}

@Composable
private fun NoteViewer(title: String, text: String, spans: List<NoteSpan>, updatedAt: Long?, onEdit: () -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val body = remember(text, spans, linkColor) { richText(text, spans, linkColor) }
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

internal fun normalizeUrl(raw: String): String {
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
