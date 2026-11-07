package com.example.financeapplication.screens

import androidx.compose.ui.res.stringResource
import com.example.financeapplication.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.financeapplication.datastores.*
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.ui.theme.Negative
import com.example.financeapplication.ui.theme.Positive
import com.example.financeapplication.ui.theme.PositiveContainer
import com.example.financeapplication.utils.Dates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CAT_KEY = "c:"
const val UNCATEGORIZED_ROUTE = "uncategorized"

/** Current time that updates every 30 s, so recurring tasks refresh at period boundaries while the screen is open. */
@Composable
private fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(30_000)
        value = System.currentTimeMillis()
    }
}

private fun LazyStaggeredGridItemInfo.contains(p: Offset): Boolean =
    p.x >= offset.x && p.x <= offset.x + size.width && p.y >= offset.y && p.y <= offset.y + size.height

/* ------------------------------------------------------------------------------------------ */
/* Category grid                                                                              */
/* ------------------------------------------------------------------------------------------ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoCategoriesScreen(onBackPress: () -> Unit, onOpenCategory: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val loadedCategories by TodoDataStore.getCategories(context).collectAsState(initial = null)
    val categories = loadedCategories.orEmpty()
    val loadedItems by TodoDataStore.getItems(context).collectAsState(initial = null)
    val items = loadedItems.orEmpty()
    val loaded = loadedCategories != null && loadedItems != null
    val now by rememberNow()

    var editMode by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = editMode) { editMode = false }

    // While dragging (and until the new order is saved) the grid uses a local order of ids;
    // names and other data always come from the latest stored categories.
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragInitialOffset by remember { mutableStateOf(IntOffset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    val stored = categories.ordered()
    LaunchedEffect(stored.map { it.id }, draggingId) {
        // the saved order caught up with the local one -> drop the local override
        if (draggingId == null && localOrder == stored.map { it.id }) localOrder = null
    }
    val order: List<TodoCategory> = localOrder?.let { ids ->
        val byId = stored.associateBy { it.id }
        ids.mapNotNull { byId[it] } + stored.filter { it.id !in ids }
    } ?: stored
    val currentOrder by rememberUpdatedState(order)

    val existing = categories.map { it.id }.toSet()
    fun stats(catId: String): Pair<Int, Int> {
        val inCat = items.filter { it.effectiveCategory(existing) == catId }
        val open = inCat.count { !it.isDoneNow(now) }
        return open to (inCat.size - open)
    }

    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<TodoCategory?>(null) }
    var deleting by remember { mutableStateOf<TodoCategory?>(null) }
    val gridState = rememberLazyStaggeredGridState()
    // Tiles have different heights, so right after a swap the finger can still be over the tile it
    // was swapped with; ignore that tile until the finger leaves it (prevents swapping back and forth).
    var lastSwappedKey by remember { mutableStateOf<Any?>(null) }

    fun onDragMove() {
        val id = draggingId ?: return
        val visible = gridState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == CAT_KEY + id } ?: return
        val center = Offset(
            dragInitialOffset.x + dragDelta.x + current.size.width / 2f,
            dragInitialOffset.y + dragDelta.y + current.size.height / 2f
        )
        lastSwappedKey?.let { k -> if (visible.none { it.key == k && it.contains(center) }) lastSwappedKey = null }
        val target = visible.firstOrNull {
            it.key != current.key && it.key != lastSwappedKey &&
                (it.key as? String)?.startsWith(CAT_KEY) == true && it.contains(center)
        } ?: return
        lastSwappedKey = target.key
        // localOrder is a State, so it is always fresh here (unlike values captured at composition)
        val ids = localOrder ?: currentOrder.map { it.id }
        val from = ids.indexOf(id)
        val to = ids.indexOfFirst { CAT_KEY + it == target.key }
        if (from == -1 || to == -1) return
        localOrder = ids.toMutableList().apply { add(to, removeAt(from)) }
        // keep the tile under the finger: it is now laid out in the target's slot
        dragDelta += Offset(
            (dragInitialOffset.x - target.offset.x).toFloat(),
            (dragInitialOffset.y - target.offset.y).toFloat()
        )
        dragInitialOffset = target.offset
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun finishDrag() {
        if (draggingId != null) {
            val ids = localOrder
            if (ids != null) scope.launch { TodoDataStore.reorderCategories(context, ids) }
        }
        draggingId = null
        dragDelta = Offset.Zero
        lastSwappedKey = null
    }

    // Wiggle of tiles in edit mode
    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val angle by wiggle.animateFloat(
        initialValue = -0.8f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse),
        label = "angle"
    )

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.todo), onBack = if (editMode) ({ editMode = false }) else onBackPress) {
            AnimatedVisibility(visible = editMode, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                TextButton(onClick = { editMode = false }) { Text(stringResource(R.string.done), fontWeight = FontWeight.Bold) }
            }
        }
        Text(
            if (editMode) stringResource(R.string.todo_categories_edit_hint) else stringResource(R.string.todo_categories_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        // The grid is built only once the data is there, so tiles do not animate into place on every open
        if (!loaded) return@Column
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            userScrollEnabled = draggingId == null,
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .pointerInput(editMode) {
                    if (!editMode) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contains(down.position) }
                        val key = startItem?.key as? String ?: return@awaitEachGesture
                        if (!key.startsWith(CAT_KEY)) return@awaitEachGesture
                        var slop = Offset.Zero
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            if (!started) {
                                slop += delta
                                if (slop.getDistance() > viewConfiguration.touchSlop) {
                                    started = true
                                    draggingId = key.removePrefix(CAT_KEY)
                                    dragInitialOffset = startItem.offset
                                    dragDelta = slop
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            } else {
                                dragDelta += delta
                                onDragMove()
                            }
                            if (started) change.consume()
                        }
                        if (started) finishDrag()
                    }
                },
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "uncategorized") {
                val (open, done) = stats(UNCATEGORIZED)
                CategoryTile(
                    name = stringResource(R.string.uncategorized),
                    icon = Icons.Default.Inbox,
                    open = open,
                    done = done,
                    editMode = editMode,
                    modifier = Modifier.animateItem(),
                    onClick = { if (!editMode) onOpenCategory(UNCATEGORIZED_ROUTE) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress); editMode = true
                    },
                    onDelete = null
                )
            }
            items(order.size, key = { CAT_KEY + order[it].id }) { index ->
                val c = order[index]
                val key = CAT_KEY + c.id
                val isDragging = draggingId == c.id
                val (open, done) = stats(c.id)
                val dragModifier = if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                            if (info != null) {
                                translationX = dragInitialOffset.x + dragDelta.x - info.offset.x
                                translationY = dragInitialOffset.y + dragDelta.y - info.offset.y
                            }
                            scaleX = 1.06f
                            scaleY = 1.06f
                            shadowElevation = 24f
                            shape = CardShape
                        }
                } else {
                    Modifier
                        .animateItem()
                        .graphicsLayer { rotationZ = if (editMode) angle * (if (index % 2 == 0) 1 else -1) else 0f }
                }
                CategoryTile(
                    name = c.name,
                    icon = Icons.Default.Folder,
                    open = open,
                    done = done,
                    editMode = editMode,
                    modifier = dragModifier,
                    onClick = { if (editMode) renaming = c else onOpenCategory(c.id) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress); editMode = true
                    },
                    onDelete = { deleting = c }
                )
            }
            item(key = "add") {
                AddCategoryTile(Modifier.animateItem()) { adding = true }
            }
        }
    }

    if (adding) {
        CategoryNameDialog(stringResource(R.string.new_category), "", { adding = false }) { name ->
            scope.launch { TodoDataStore.addCategory(context, name) }
            adding = false
        }
    }
    renaming?.let { c ->
        CategoryNameDialog(stringResource(R.string.rename_category), c.name, { renaming = null }) { name ->
            scope.launch { TodoDataStore.renameCategory(context, c.id, name) }
            renaming = null
        }
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = stringResource(R.string.delete_category_title, c.name),
            text = stringResource(R.string.delete_category_text),
            onConfirm = {
                scope.launch { TodoDataStore.removeCategory(context, c.id) }
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryTile(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    open: Int,
    done: Int,
    editMode: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val borderColor by animateColorAsState(
        if (editMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
        label = "tileBorder"
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        // The tile grows with the name, so the name is always shown in full
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = HOME_TILE_HEIGHT)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Column(Modifier.padding(16.dp)) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = editMode && onDelete != null, label = "tileIcon") { editing ->
                        Icon(
                            if (editing) Icons.Default.DragIndicator else icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        open == 0 && done == 0 -> stringResource(R.string.empty)
                        open == 0 -> stringResource(R.string.all_done)
                        else -> stringResource(R.string.open_count, open) + if (done > 0) " · " + stringResource(R.string.done_count, done) else ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (onDelete != null) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = editMode,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    IconButton(onClick = onDelete) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(Negative.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, stringResource(R.string.delete), tint = Negative, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCategoryTile(modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(HOME_TILE_HEIGHT),
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.new_category), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Tasks of one category                                                                      */
/* ------------------------------------------------------------------------------------------ */

@Composable
fun TodoListScreen(categoryRoute: String, onBackPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val loadedCategories by TodoDataStore.getCategories(context).collectAsState(initial = null)
    val categories = loadedCategories.orEmpty()
    val loadedItems by TodoDataStore.getItems(context).collectAsState(initial = null)
    val items = loadedItems.orEmpty()
    val loaded = loadedCategories != null && loadedItems != null
    val now by rememberNow()

    val categoryId = if (categoryRoute == UNCATEGORIZED_ROUTE) UNCATEGORIZED else categoryRoute
    val title = categories.find { it.id == categoryId }?.name ?: stringResource(R.string.uncategorized)
    val existing = categories.map { it.id }.toSet()
    val inCategory = items.filter { it.effectiveCategory(existing) == categoryId }

    val pending = inCategory.filter { !it.isDoneNow(now) }.sortedWith(
        compareBy<TodoItem> { it.repeat != null }
            .thenBy { it.dueAt == null }
            .thenBy { it.dueAt }
            .thenBy { it.createdAt }
    )
    val doneThisPeriod = inCategory.filter { it.repeat != null && it.isDoneNow(now) }.sortedBy { it.title.lowercase() }
    val completed = inCategory.filter { it.repeat == null && it.done }.sortedByDescending { it.completedAt ?: 0L }

    var editMode by rememberSaveable { mutableStateOf(false) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TodoItem?>(null) }
    var viewingComment by remember { mutableStateOf<TodoItem?>(null) }
    BackHandler(enabled = editMode) { editMode = false }

    val onLongPress: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        editMode = true
    }

    // A checked task moves to another place in the list. The list normally scrolls after its first
    // visible item, which makes the whole screen follow the task; instead the scroll position is
    // remembered on the tap and put back in the same frame the new order is shown.
    val listState = rememberLazyListState()
    var keepScroll by remember { mutableStateOf<Triple<List<TodoItem>, Int, Int>?>(null) }
    keepScroll?.let { (before, index, offset) ->
        if (items !== before) {
            SideEffect {
                listState.requestScrollToItem(index, offset)
                keepScroll = null
            }
        }
    }
    val toggle: (TodoItem) -> Unit = { item ->
        keepScroll = Triple(items, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        scope.launch { TodoDataStore.toggle(context, item) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            AnimatedVisibility(visible = !editMode, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.task)) }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenHeader(title, onBack = if (editMode) ({ editMode = false }) else onBackPress) {
                AnimatedVisibility(visible = editMode, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    TextButton(onClick = { editMode = false }) { Text(stringResource(R.string.done), fontWeight = FontWeight.Bold) }
                }
            }
            Text(
                if (editMode) stringResource(R.string.todo_tasks_edit_hint) else stringResource(R.string.todo_tasks_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            )

            if (!loaded) return@Column
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (inCategory.isEmpty()) {
                    item(key = "empty") { EmptyState(stringResource(R.string.todo_empty), Modifier.animateItem()) }
                } else if (pending.isEmpty() && doneThisPeriod.isEmpty() && !showCompleted) {
                    item(key = "alldone") { EmptyState(stringResource(R.string.everything_done), Modifier.animateItem()) }
                }
                items(pending + doneThisPeriod, key = { it.id }) { item ->
                    TodoRow(
                        item = item,
                        now = now,
                        editMode = editMode,
                        modifier = Modifier.animateItem(fadeInSpec = null),
                        onClick = {
                            if (editMode) editing = item
                            else toggle(item)
                        },
                        onLongClick = onLongPress,
                        onDelete = { deleting = item },
                        onOpenComment = { viewingComment = item }
                    )
                }
                if (completed.isNotEmpty()) {
                    item(key = "completedHeader") {
                        val rotation by animateFloatAsState(if (showCompleted) 180f else 0f, label = "chevron")
                        Row(
                            Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clip(FieldShape)
                                .clickable { showCompleted = !showCompleted }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionTitle(stringResource(R.string.completed_count, completed.size), Modifier.weight(1f))
                            Icon(
                                Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { rotationZ = rotation }
                            )
                        }
                    }
                    if (showCompleted) {
                        items(completed, key = { it.id }) { item ->
                            TodoRow(
                                item = item,
                                now = now,
                                editMode = editMode,
                                modifier = Modifier.animateItem(fadeInSpec = null),
                                onClick = {
                                    if (editMode) editing = item
                                    else toggle(item)
                                },
                                onLongClick = onLongPress,
                                onDelete = { deleting = item },
                        onOpenComment = { viewingComment = item }
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        TodoItemDialog(
            initial = editing,
            categories = categories.ordered(),
            defaultCategoryId = categoryId,
            onDismiss = { adding = false; editing = null },
            onSave = { item ->
                val isNew = editing == null
                scope.launch {
                    if (isNew) TodoDataStore.addItem(context, item) else TodoDataStore.updateItem(context, item)
                }
                adding = false; editing = null
            }
        )
    }
    viewingComment?.let { item ->
        AlertDialog(
            onDismissRequest = { viewingComment = null },
            title = { Text(item.title) },
            text = {
                SelectionContainer {
                    Text(
                        item.comment.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewingComment = null }) { Text(stringResource(R.string.close)) } },
            dismissButton = {
                TextButton(onClick = { editing = item; viewingComment = null }) { Text(stringResource(R.string.edit)) }
            }
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.delete_task_title),
            text = stringResource(R.string.will_be_deleted, item.title),
            onConfirm = {
                scope.launch { TodoDataStore.removeItem(context, item.id) }
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

private fun formatDue(item: TodoItem): String {
    val due = item.dueAt ?: return ""
    return if (item.hasTime) Dates.dateTime(due) else Dates.date(due)
}

private fun isOverdue(item: TodoItem, now: Long): Boolean {
    if (item.repeat != null || item.done) return false
    val due = item.dueAt ?: return false
    return if (item.hasTime) due < now
    else java.util.Calendar.getInstance().apply {
        timeInMillis = due; add(java.util.Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis <= now
}

@Composable
private fun resetLabel(period: RepeatPeriod): String = when (period) {
    RepeatPeriod.DAILY -> stringResource(R.string.resets_daily)
    RepeatPeriod.WEEKLY -> stringResource(R.string.resets_weekly)
    RepeatPeriod.MONTHLY -> stringResource(R.string.resets_monthly)
    RepeatPeriod.YEARLY -> stringResource(R.string.resets_yearly)
}

@Composable
private fun periodWord(period: RepeatPeriod): String = when (period) {
    RepeatPeriod.DAILY -> stringResource(R.string.period_word_daily)
    RepeatPeriod.WEEKLY -> stringResource(R.string.period_word_weekly)
    RepeatPeriod.MONTHLY -> stringResource(R.string.period_word_monthly)
    RepeatPeriod.YEARLY -> stringResource(R.string.period_word_yearly)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TodoRow(
    item: TodoItem,
    now: Long,
    editMode: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onOpenComment: () -> Unit,
) {
    val doneNow = item.isDoneNow(now)
    val recurringDone = item.repeat != null && doneNow
    val accent = MaterialTheme.colorScheme.primary
    val checkColor = if (recurringDone) Positive else accent

    val container by animateColorAsState(
        if (recurringDone) PositiveContainer else MaterialTheme.colorScheme.surface,
        tween(300), label = "rowBg"
    )
    val border by animateColorAsState(
        when {
            recurringDone -> Positive.copy(alpha = 0.7f)
            editMode -> accent.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        tween(300), label = "rowBorder"
    )
    val checkFill by animateColorAsState(if (doneNow) checkColor else Color.Transparent, tween(200), label = "checkFill")
    val checkScale by animateFloatAsState(
        if (doneNow) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(checkFill)
                    .border(2.dp, if (doneNow) checkColor else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check, null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .scale(checkScale)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val oneTimeDone = item.repeat == null && item.done
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (oneTimeDone) TextDecoration.LineThrough else null,
                    color = if (oneTimeDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (!item.comment.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val repeat = item.repeat
                if (repeat != null || item.dueAt != null) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (repeat != null) {
                            val time = if (item.hasTime && item.dueAt != null) " · " + Dates.time(item.dueAt) else ""
                            Chip("↻ " + stringResource(repeat.label) + time, if (recurringDone) Positive else accent)
                            if (recurringDone) {
                                Chip(stringResource(R.string.done_in_period, periodWord(repeat), resetLabel(repeat)), Positive)
                            }
                        } else if (item.dueAt != null) {
                            Chip(
                                formatDue(item),
                                if (isOverdue(item, now)) Negative else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (!item.comment.isNullOrBlank()) {
                AnimatedVisibility(
                    visible = !editMode,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(onClick = onOpenComment),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes, stringResource(R.string.open_comment),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = editMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Edit, stringResource(R.string.edit),
                        tint = accent,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(22.dp)
                    )
                    Box(
                        Modifier
                            .padding(start = 6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Negative, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Dialogs                                                                                    */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun TodoItemDialog(
    initial: TodoItem?,
    categories: List<TodoCategory>,
    defaultCategoryId: String,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var comment by remember { mutableStateOf(initial?.comment.orEmpty()) }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: defaultCategoryId) }
    var dueAt by remember { mutableStateOf(initial?.dueAt) }
    var hasTime by remember { mutableStateOf(initial?.hasTime ?: false) }
    var repeat by remember { mutableStateOf(initial?.repeat) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    val repeatOptions: List<RepeatPeriod?> = listOf(null) + RepeatPeriod.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.new_task) else stringResource(R.string.edit_task)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(title, { title = it }, stringResource(R.string.task))
                AppTextField(comment, { comment = it }, stringResource(R.string.comment_optional), singleLine = false, minLines = 2)

                Box {
                    OutlinedCard(
                        onClick = { categoryMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = FieldShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            Modifier
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                categories.find { it.id == categoryId }?.name ?: stringResource(R.string.uncategorized),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.uncategorized)) }, onClick = { categoryId = UNCATEGORIZED; categoryMenu = false })
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; categoryMenu = false })
                        }
                    }
                }

                Text(stringResource(R.string.repeat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedSelector(
                    options = listOf(stringResource(R.string.repeat_once), stringResource(R.string.repeat_day), stringResource(R.string.repeat_week), stringResource(R.string.repeat_month), stringResource(R.string.repeat_year)),
                    selectedIndex = repeatOptions.indexOf(repeat),
                    onSelect = { repeat = repeatOptions[it] }
                )

                AnimatedContent(targetState = repeat == null, label = "dateOrTime") { oneTime ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (oneTime) {
                            PickerField(
                                Icons.Default.CalendarMonth, stringResource(R.string.date), dueAt?.let { Dates.date(it) },
                                onClick = { pickDate = true },
                                onClear = { dueAt = null; hasTime = false },
                                placeholder = stringResource(R.string.date_optional)
                            )
                        }
                        PickerField(
                            Icons.Default.Schedule, stringResource(R.string.time),
                            placeholder = stringResource(R.string.time_optional),
                            value =
                            dueAt?.takeIf { hasTime }?.let { Dates.time(it) },
                            onClick = { pickTime = true },
                            onClear = {
                                hasTime = false
                                // a recurring task keeps only a time, so without it there is nothing left
                                if (!oneTime) dueAt = null
                                else dueAt = dueAt?.let { startOfDay(it) }
                            }
                        )
                        if (!oneTime) {
                            val p = repeat
                            if (p != null) {
                                Text(
                                    stringResource(R.string.can_be_checked_once, periodWord(p), resetLabel(p)) +
                                        if (p == RepeatPeriod.DAILY) "." else stringResource(R.string.at_midnight_suffix),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val base = initial ?: TodoItem(title = "")
                    val recurring = repeat != null
                    // recurring tasks keep only a time of day
                    val savedDue = if (recurring && !hasTime) null else dueAt
                    onSave(
                        base.copy(
                            title = title.trim(),
                            comment = comment.trim().ifEmpty { null },
                            categoryId = categoryId,
                            dueAt = savedDue,
                            hasTime = savedDue != null && hasTime,
                            repeat = repeat,
                            done = if (recurring) false else base.done,
                            completedAt = if (recurring != (base.repeat != null)) null else base.completedAt
                        )
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (pickDate) {
        AppDatePickerDialog(initial = dueAt, onDismiss = { pickDate = false }, onPick = { dueAt = it })
    }
    if (pickTime) {
        AppTimePickerDialog(base = dueAt, baseHasTime = hasTime, onDismiss = { pickTime = false }, onPick = { dueAt = it; hasTime = true })
    }
}

@Composable
private fun CategoryNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { AppTextField(name, { name = it }, stringResource(R.string.name)) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
