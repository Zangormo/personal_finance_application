package com.example.financeapplication.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.financeapplication.datastores.RepeatPeriod
import com.example.financeapplication.datastores.TodoCategory
import com.example.financeapplication.datastores.TodoDataStore
import com.example.financeapplication.datastores.TodoItem
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.ui.theme.Negative
import com.example.financeapplication.utils.Dates
import kotlinx.coroutines.launch

private const val ALL = "__all__"

@Composable
fun TodoScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by TodoDataStore.getCategories(context).collectAsState(initial = emptyList())
    val items by TodoDataStore.getItems(context).collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }

    var selectedCategory by rememberSaveable { mutableStateOf(ALL) }
    if (selectedCategory != ALL && categories.isNotEmpty() && categories.none { it.id == selectedCategory }) {
        selectedCategory = ALL
    }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }
    var addingItem by remember { mutableStateOf(false) }
    var addingCategory by remember { mutableStateOf(false) }
    var managingCategories by remember { mutableStateOf(false) }

    val inCategory = items.filter { selectedCategory == ALL || it.categoryId == selectedCategory }
    val pending = inCategory.filter { !it.done }
        .sortedWith(compareBy<TodoItem> { it.dueAt == null }.thenBy { it.dueAt }.thenBy { it.createdAt })
    val completed = inCategory.filter { it.done }.sortedByDescending { it.completedAt ?: 0L }
    val categoryName = categories.associate { it.id to it.name }

    fun toggle(item: TodoItem) {
        scope.launch {
            val updated = TodoDataStore.toggle(context, item)
            if (item.repeat != null && updated.dueAt != null) {
                snackbar.showSnackbar("Done! Next: " + formatDue(updated))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingItem = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Task") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenHeader("To-do", onBack = onBackPress) {
                IconButton(onClick = { managingCategories = true }) {
                    Icon(Icons.Default.Tune, contentDescription = "Manage categories")
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryChip("All (${items.count { !it.done }})", selectedCategory == ALL) { selectedCategory = ALL }
                }
                items(categories, key = { it.id }) { c ->
                    val count = items.count { it.categoryId == c.id && !it.done }
                    CategoryChip("${c.name} ($count)", selectedCategory == c.id) { selectedCategory = c.id }
                }
                item {
                    AssistChip(
                        onClick = { addingCategory = true },
                        label = { Text("Category") },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pending.isEmpty()) {
                    item { EmptyState(if (inCategory.isEmpty()) "No tasks yet. Tap \"Task\" to add one." else "Everything is done 🎉") }
                }
                items(pending, key = { it.id }) { item ->
                    TodoRow(
                        item = item,
                        categoryName = if (selectedCategory == ALL) categoryName[item.categoryId] else null,
                        onToggle = { toggle(item) },
                        onClick = { editingItem = item }
                    )
                }
                if (completed.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(FieldShape)
                                .clickable { showCompleted = !showCompleted }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionTitle("Completed (${completed.size})", Modifier.weight(1f))
                            Icon(
                                if (showCompleted) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (showCompleted) {
                        items(completed, key = { it.id }) { item ->
                            TodoRow(
                                item = item,
                                categoryName = if (selectedCategory == ALL) categoryName[item.categoryId] else null,
                                onToggle = { toggle(item) },
                                onClick = { editingItem = item }
                            )
                        }
                    }
                }
            }
        }
    }

    if (addingItem || editingItem != null) {
        TodoItemDialog(
            initial = editingItem,
            categories = categories,
            defaultCategoryId = selectedCategory.takeIf { it != ALL },
            onDismiss = { addingItem = false; editingItem = null },
            onDelete = editingItem?.let { e ->
                {
                    scope.launch { TodoDataStore.removeItem(context, e.id) }
                    editingItem = null
                }
            },
            onSave = { item, newCategoryName ->
                val isNew = editingItem == null
                scope.launch {
                    var toSave = item
                    if (newCategoryName != null) {
                        val c = TodoDataStore.addCategory(context, newCategoryName)
                        toSave = item.copy(categoryId = c.id)
                    }
                    if (isNew) TodoDataStore.addItem(context, toSave) else TodoDataStore.updateItem(context, toSave)
                }
                addingItem = false; editingItem = null
            }
        )
    }

    if (addingCategory) {
        CategoryNameDialog(
            title = "New category",
            initial = "",
            onDismiss = { addingCategory = false },
            onSave = { name ->
                scope.launch {
                    val c = TodoDataStore.addCategory(context, name)
                    selectedCategory = c.id
                }
                addingCategory = false
            }
        )
    }

    if (managingCategories) {
        ManageCategoriesDialog(
            categories = categories,
            itemCount = { id -> items.count { it.categoryId == id } },
            onDismiss = { managingCategories = false },
            onRename = { c, name -> scope.launch { TodoDataStore.renameCategory(context, c.id, name) } },
            onDelete = { c -> scope.launch { TodoDataStore.removeCategory(context, c.id) } }
        )
    }
}

private fun formatDue(item: TodoItem): String {
    val due = item.dueAt ?: return ""
    return if (item.hasTime) Dates.dateTime(due) else Dates.date(due)
}

private fun isOverdue(item: TodoItem): Boolean {
    val due = item.dueAt ?: return false
    if (item.done) return false
    val now = System.currentTimeMillis()
    return if (item.hasTime) due < now else {
        // a date-only task is overdue from the next day
        java.util.Calendar.getInstance().apply { timeInMillis = due; add(java.util.Calendar.DAY_OF_YEAR, 1) }.timeInMillis <= now
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun TodoRow(item: TodoItem, categoryName: String?, onToggle: () -> Unit, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (item.done) accent else Color.Transparent)
                    .border(2.dp, if (item.done) accent else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (item.done) Icon(Icons.Default.Check, "Done", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (!item.comment.isNullOrBlank()) {
                    Text(
                        item.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
                val hasMeta = item.dueAt != null || item.repeat != null || categoryName != null
                if (hasMeta) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.dueAt != null) {
                            Chip(formatDue(item), if (isOverdue(item)) Negative else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.repeat != null) {
                            Chip("↻ " + item.repeat.label, MaterialTheme.colorScheme.primary)
                        }
                        if (categoryName != null) {
                            Chip(categoryName, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoItemDialog(
    initial: TodoItem?,
    categories: List<TodoCategory>,
    defaultCategoryId: String?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (TodoItem, String?) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var comment by remember { mutableStateOf(initial?.comment.orEmpty()) }
    var categoryId by remember {
        mutableStateOf(initial?.categoryId ?: defaultCategoryId ?: categories.firstOrNull()?.id)
    }
    var dueAt by remember { mutableStateOf(initial?.dueAt) }
    var hasTime by remember { mutableStateOf(initial?.hasTime ?: false) }
    var repeat by remember { mutableStateOf(initial?.repeat) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    val repeatOptions: List<RepeatPeriod?> = listOf(null) + RepeatPeriod.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New task" else "Edit task") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(title, { title = it }, "Task")
                AppTextField(comment, { comment = it }, "Comment (optional)", singleLine = false, minLines = 2)

                // Category
                Box {
                    OutlinedCard(
                        Modifier
                            .fillMaxWidth()
                            .clickable { categoryMenu = true },
                        shape = FieldShape
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                categories.find { it.id == categoryId }?.name ?: "General",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; categoryMenu = false })
                        }
                        if (categories.isEmpty()) {
                            DropdownMenuItem(text = { Text("General") }, onClick = { categoryMenu = false })
                        }
                    }
                }

                // Date & time
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f), shape = FieldShape) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dueAt?.let { Dates.date(it) } ?: "Date", maxLines = 1)
                    }
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f), shape = FieldShape) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (dueAt != null && hasTime) Dates.time(dueAt!!) else "Time", maxLines = 1)
                    }
                    if (dueAt != null) {
                        IconButton(onClick = { dueAt = null; hasTime = false }) { Icon(Icons.Default.Close, "Clear date") }
                    }
                }

                // Repeat
                Text("Repeat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeatOptions.forEach { option ->
                        val selected = repeat == option
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(FieldShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { repeat = option }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when (option) {
                                    null -> "Once"
                                    RepeatPeriod.DAILY -> "Day"
                                    RepeatPeriod.WEEKLY -> "Week"
                                    RepeatPeriod.MONTHLY -> "Month"
                                    RepeatPeriod.YEARLY -> "Year"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (repeat != null) {
                    Text(
                        "Checking off a recurring task moves it to the next date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val existingCategory = categoryId?.takeIf { id -> categories.any { it.id == id } }
                    val base = initial ?: TodoItem(categoryId = existingCategory ?: "", title = "")
                    val item = base.copy(
                        title = title.trim(),
                        comment = comment.trim().ifEmpty { null },
                        categoryId = existingCategory ?: base.categoryId,
                        dueAt = dueAt,
                        hasTime = dueAt != null && hasTime,
                        repeat = repeat,
                        // a task that becomes recurring is active again
                        done = if (repeat != null) false else base.done
                    )
                    onSave(item, if (existingCategory == null) "General" else null)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (pickDate) {
        AppDatePickerDialog(
            initial = dueAt,
            onDismiss = { pickDate = false },
            onPick = { dueAt = it }
        )
    }
    if (pickTime) {
        AppTimePickerDialog(
            base = dueAt,
            onDismiss = { pickTime = false },
            onPick = { dueAt = it; hasTime = true }
        )
    }
}

@Composable
private fun CategoryNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { AppTextField(name, { name = it }, "Name") },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ManageCategoriesDialog(
    categories: List<TodoCategory>,
    itemCount: (String) -> Int,
    onDismiss: () -> Unit,
    onRename: (TodoCategory, String) -> Unit,
    onDelete: (TodoCategory) -> Unit,
) {
    var renaming by remember { mutableStateOf<TodoCategory?>(null) }
    var deleting by remember { mutableStateOf<TodoCategory?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (categories.isEmpty()) Text("No categories yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                categories.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${itemCount(c.id)} tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { renaming = c }) { Icon(Icons.Default.Edit, "Rename") }
                        IconButton(onClick = { deleting = c }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    renaming?.let { c ->
        CategoryNameDialog("Rename category", c.name, { renaming = null }) { name ->
            onRename(c, name); renaming = null
        }
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "Delete \"${c.name}\"?",
            text = "The category and its ${itemCount(c.id)} tasks will be deleted.",
            onConfirm = { onDelete(c); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}
