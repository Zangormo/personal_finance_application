package com.example.financeapplication.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financeapplication.classes.NecessityLevel
import com.example.financeapplication.datastores.*
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.ui.theme.Negative
import com.example.financeapplication.ui.theme.Positive
import com.example.financeapplication.utils.Dates
import com.example.financeapplication.utils.Money
import kotlinx.coroutines.launch
import java.util.Calendar

private sealed interface HistoryEntry {
    val timestamp: Long
    val amount: Float

    data class Spend(val record: SpendingRecord) : HistoryEntry {
        override val timestamp get() = record.timestamp
        override val amount get() = record.amount
    }

    data class Earn(val record: IncomeRecord) : HistoryEntry {
        override val timestamp get() = record.timestamp
        override val amount get() = record.amount
    }
}

private fun dayKey(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Merged spending + income history. */
@Composable
fun HistoryScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spendings by SpendingDataStore.getSpendings(context).collectAsState(initial = emptyList())
    val incomes by IncomeDataStore.getIncomes(context).collectAsState(initial = emptyList())

    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 spending, 2 income
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<HistoryEntry?>(null) }
    var editing by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleting by remember { mutableStateOf<HistoryEntry?>(null) }

    val q = query.trim()
    val entries: List<HistoryEntry> = remember(spendings, incomes, filter, q) {
        val s = if (filter != 2) spendings.map { HistoryEntry.Spend(it) } else emptyList()
        val i = if (filter != 1) incomes.map { HistoryEntry.Earn(it) } else emptyList()
        (s + i)
            .filter { q.isEmpty() || it.matches(q) }
            .sortedByDescending { it.timestamp }
    }
    val grouped = remember(entries) { entries.groupBy { dayKey(it.timestamp) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("History", onBack = onBackPress)
        SegmentedSelector(
            options = listOf("All", "Spending", "Income"),
            selectedIndex = filter,
            onSelect = { filter = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        AppTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entries.isEmpty()) {
                item { EmptyState(if (q.isEmpty()) "No records yet" else "Nothing found") }
            }
            grouped.forEach { (day, dayEntries) ->
                item(key = "h$day") {
                    val net = dayEntries.sumOf {
                        (if (it is HistoryEntry.Earn) it.amount else -it.amount).toDouble()
                    }.toFloat()
                    SectionTitle(Dates.dayHeader(day), trailing = Money.formatSigned(net))
                }
                items(dayEntries) { entry ->
                    HistoryRow(entry, onClick = { selected = entry })
                }
            }
        }
    }

    selected?.let { entry ->
        HistoryDetailDialog(
            entry = entry,
            onDismiss = { selected = null },
            onEdit = { editing = entry; selected = null },
            onDelete = { deleting = entry; selected = null }
        )
    }

    editing?.let { entry ->
        HistoryEditDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSaveSpending = { old, amount, necessity ->
                scope.launch {
                    SpendingDataStore.updateSpending(context, old, amount, old.items, necessity)
                    UserPreferencesDataStore.updateBalance(context, -(amount - old.amount))
                }
                editing = null
            },
            onSaveIncome = { old, amount, description ->
                scope.launch {
                    IncomeDataStore.updateIncome(context, old, amount, description)
                    UserPreferencesDataStore.updateBalance(context, amount - old.amount)
                }
                editing = null
            }
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = "Delete record?",
            text = "The balance will be corrected by ${Money.format(entry.amount)}.",
            onConfirm = {
                scope.launch {
                    when (entry) {
                        is HistoryEntry.Spend -> {
                            SpendingDataStore.deleteSpending(context, entry.record)
                            UserPreferencesDataStore.updateBalance(context, entry.record.amount)
                        }
                        is HistoryEntry.Earn -> {
                            IncomeDataStore.deleteIncome(context, entry.record)
                            UserPreferencesDataStore.updateBalance(context, -entry.record.amount)
                        }
                    }
                }
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

/** Search by any part of the name / items / description, or of the amount. */
private fun HistoryEntry.matches(q: String): Boolean {
    val texts = when (this) {
        is HistoryEntry.Spend -> record.items.orEmpty()
        is HistoryEntry.Earn -> listOfNotNull(record.description)
    }
    if (texts.any { it.contains(q, ignoreCase = true) }) return true
    // "12,5" and "12.5" both find 12.50
    val number = q.replace(',', '.')
    return Money.format(amount).replace(',', '.').contains(number) || amount.toString().contains(number)
}

private fun HistoryEntry.title(): String = when (this) {
    is HistoryEntry.Spend -> record.items.orEmpty().joinToString(", ").ifEmpty { "Spending" }
    is HistoryEntry.Earn -> record.description.orEmpty().ifEmpty { "Income" }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (entry) {
                is HistoryEntry.Spend -> Dot(necessityColor(entry.record.necessity))
                is HistoryEntry.Earn -> Dot(Positive)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Dates.time(entry.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry is HistoryEntry.Spend && entry.record.source == SOURCE_SUBSCRIPTION) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Autorenew, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            " subscription",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            val isIncome = entry is HistoryEntry.Earn
            Text(
                text = if (isIncome) "+${Money.format(entry.amount)}" else "−${Money.format(entry.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isIncome) Positive else Negative
            )
        }
    }
}

@Composable
private fun HistoryDetailDialog(entry: HistoryEntry, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry is HistoryEntry.Earn) "Income" else "Spending") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (entry) {
                    is HistoryEntry.Spend -> {
                        DetailLine("Items", entry.record.items.orEmpty().joinToString("\n").ifEmpty { "—" })
                        DetailLine("Necessity", necessityLabel(entry.record.necessity))
                        if (entry.record.source == SOURCE_SUBSCRIPTION) DetailLine("Source", "Subscription (automatic)")
                    }
                    is HistoryEntry.Earn -> DetailLine("Description", entry.record.description.orEmpty().ifEmpty { "—" })
                }
                DetailLine("Amount", Money.format(entry.amount))
                DetailLine("Date", Dates.dateTime(entry.timestamp))
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HistoryEditDialog(
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onSaveSpending: (SpendingRecord, Float, NecessityLevel) -> Unit,
    onSaveIncome: (IncomeRecord, Float, String) -> Unit,
) {
    var amountText by remember { mutableStateOf(Money.toInput(entry.amount)) }
    var error by remember { mutableStateOf<String?>(null) }
    var necessity by remember {
        mutableStateOf((entry as? HistoryEntry.Spend)?.record?.necessity ?: NecessityLevel.NECESSARY)
    }
    var description by remember { mutableStateOf((entry as? HistoryEntry.Earn)?.record?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry is HistoryEntry.Earn) "Edit income" else "Edit spending") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = "Amount",
                    isError = error != null,
                    errorText = error
                )
                when (entry) {
                    is HistoryEntry.Spend -> NecessitySelector(necessity, { necessity = it })
                    is HistoryEntry.Earn -> AppTextField(description, { description = it }, "Description")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = Money.parse(amountText)
                when {
                    amount == null -> error = "Enter a valid amount"
                    amount <= 0f -> error = "Amount must be greater than 0"
                    else -> when (entry) {
                        is HistoryEntry.Spend -> onSaveSpending(entry.record, amount, necessity)
                        is HistoryEntry.Earn -> onSaveIncome(entry.record, amount, description.trim())
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
