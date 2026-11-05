package com.example.financeapplication.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financeapplication.datastores.Debt
import com.example.financeapplication.datastores.DebtDirection
import com.example.financeapplication.datastores.DebtsDataStore
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.ui.theme.Negative
import com.example.financeapplication.ui.theme.Positive
import com.example.financeapplication.utils.Dates
import com.example.financeapplication.utils.Money
import kotlinx.coroutines.launch

@Composable
fun DebtsScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debts by DebtsDataStore.getDebts(context).collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettled by rememberSaveable { mutableStateOf(false) }
    val direction = if (tab == 0) DebtDirection.I_OWE else DebtDirection.OWED_TO_ME

    val iOwe = debts.filter { it.direction == DebtDirection.I_OWE }.sumOf { it.remaining.toDouble() }.toFloat()
    val owedToMe = debts.filter { it.direction == DebtDirection.OWED_TO_ME }.sumOf { it.remaining.toDouble() }.toFloat()
    val visible = debts
        .filter { it.direction == direction && (showSettled || !it.isSettled) }
        .sortedWith(compareBy<Debt> { it.isSettled }.thenBy { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.createdAt })

    var adding by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Debt?>(null) }
    var repaying by remember { mutableStateOf<Debt?>(null) }
    var editing by remember { mutableStateOf<Debt?>(null) }
    var deleting by remember { mutableStateOf<Debt?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Debt") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenHeader("Debts", onBack = onBackPress)
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Row {
                            StatValue("I owe", Money.format(iOwe), Modifier.weight(1f), Negative)
                            StatValue("Owed to me", Money.format(owedToMe), Modifier.weight(1f), Positive)
                            val net = owedToMe - iOwe
                            StatValue("Net", Money.formatSigned(net), Modifier.weight(1f), if (net < 0) Negative else Positive)
                        }
                    }
                }
                item {
                    SegmentedSelector(
                        options = listOf("I owe", "Owed to me"),
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        selectedColors = listOf(Negative.copy(alpha = 0.85f), Positive.copy(alpha = 0.85f))
                    )
                }
                item {
                    SwitchRow("Show settled", showSettled, { showSettled = it })
                }
                if (visible.isEmpty()) {
                    item { EmptyState(if (tab == 0) "You don't owe anyone" else "Nobody owes you") }
                }
                items(visible, key = { it.id }) { debt ->
                    DebtCard(debt, Modifier.animateItem(), onClick = { details = debt }, onRepay = { repaying = debt })
                }
            }
        }
    }

    if (adding || editing != null) {
        DebtDialog(
            initial = editing,
            defaultDirection = direction,
            onDismiss = { adding = false; editing = null },
            onSave = { debt, affectBalance ->
                scope.launch {
                    if (editing == null) DebtsDataStore.add(context, debt, affectBalance)
                    else DebtsDataStore.update(context, debt)
                }
                adding = false; editing = null
            }
        )
    }

    details?.let { d ->
        val debt = debts.find { it.id == d.id } ?: d
        DebtDetailsDialog(
            debt = debt,
            onDismiss = { details = null },
            onEdit = { editing = debt; details = null },
            onDelete = { deleting = debt; details = null },
            onRepay = { repaying = debt; details = null }
        )
    }

    repaying?.let { debt ->
        RepayDialog(
            debt = debt,
            onDismiss = { repaying = null },
            onConfirm = { amount, affectBalance ->
                scope.launch { DebtsDataStore.addPayment(context, debt, amount, affectBalance) }
                repaying = null
            }
        )
    }

    deleting?.let { debt ->
        ConfirmDialog(
            title = "Delete debt?",
            text = "The record for ${debt.person} will be removed. The balance is not changed.",
            onConfirm = {
                scope.launch { DebtsDataStore.remove(context, debt.id) }
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun DebtCard(debt: Debt, modifier: Modifier, onClick: () -> Unit, onRepay: () -> Unit) {
    val color = if (debt.direction == DebtDirection.I_OWE) Negative else Positive
    val overdue = !debt.isSettled && debt.dueAt != null && debt.dueAt < System.currentTimeMillis()
    AppCard(
        modifier
            .fillMaxWidth()
            .alpha(if (debt.isSettled) 0.55f else 1f),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(debt.person, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!debt.note.isNullOrBlank()) {
                    Text(debt.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(debt.remaining), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                if (debt.paid > 0f) {
                    Text(
                        "of ${Money.format(debt.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (debt.paid > 0f) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (debt.paid / debt.amount).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    debt.isSettled -> "Settled"
                    debt.dueAt != null -> (if (overdue) "Overdue since " else "Due ") + Dates.date(debt.dueAt)
                    else -> "Since " + Dates.date(debt.createdAt)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) Negative else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!debt.isSettled) {
                TextButton(onClick = onRepay) {
                    Text(if (debt.direction == DebtDirection.I_OWE) "Pay back" else "Got back")
                }
            }
        }
    }
}

@Composable
private fun DebtDialog(
    initial: Debt?,
    defaultDirection: DebtDirection,
    onDismiss: () -> Unit,
    onSave: (Debt, Boolean) -> Unit,
) {
    var direction by remember { mutableStateOf(initial?.direction ?: defaultDirection) }
    var person by remember { mutableStateOf(initial?.person.orEmpty()) }
    var amountText by remember { mutableStateOf(initial?.let { Money.toInput(it.amount) }.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var dueAt by remember { mutableStateOf(initial?.dueAt) }
    var affectBalance by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New debt" else "Edit debt") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SegmentedSelector(
                    options = listOf("I borrowed", "I lent"),
                    selectedIndex = if (direction == DebtDirection.I_OWE) 0 else 1,
                    onSelect = { direction = if (it == 0) DebtDirection.I_OWE else DebtDirection.OWED_TO_ME },
                    selectedColors = listOf(Negative.copy(alpha = 0.85f), Positive.copy(alpha = 0.85f))
                )
                AppTextField(person, { person = it }, "Person")
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = "Amount",
                    isError = error != null,
                    errorText = error
                )
                AppTextField(note, { note = it }, "Comment (optional)", singleLine = false)
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickDate = true },
                    shape = FieldShape
                ) {
                    Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            dueAt?.let { "Due " + Dates.date(it) } ?: "Due date (optional)",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (dueAt != null) {
                            IconButton(onClick = { dueAt = null }) { Icon(Icons.Default.Close, "Clear") }
                        } else {
                            Spacer(Modifier.height(48.dp))
                        }
                    }
                }
                if (initial == null) {
                    SwitchRow(
                        text = "Update balance",
                        subtitle = if (direction == DebtDirection.I_OWE) "Add the borrowed money to the balance"
                        else "Take the lent money from the balance",
                        checked = affectBalance,
                        onCheckedChange = { affectBalance = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = person.isNotBlank(),
                onClick = {
                    val amount = Money.parse(amountText)
                    if (amount == null || amount <= 0f) {
                        error = "Enter a valid amount"
                        return@TextButton
                    }
                    val base = initial ?: Debt(person = "", amount = 0f, direction = direction)
                    onSave(
                        base.copy(
                            person = person.trim(),
                            amount = amount,
                            direction = direction,
                            note = note.trim().ifEmpty { null },
                            dueAt = dueAt
                        ),
                        affectBalance
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickDate) {
        AppDatePickerDialog(initial = dueAt, onDismiss = { pickDate = false }, onPick = { dueAt = it })
    }
}

@Composable
private fun RepayDialog(debt: Debt, onDismiss: () -> Unit, onConfirm: (Float, Boolean) -> Unit) {
    var amountText by remember { mutableStateOf(Money.toInput(debt.remaining)) }
    var affectBalance by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val iOwe = debt.direction == DebtDirection.I_OWE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (iOwe) "Pay back ${debt.person}" else "Got back from ${debt.person}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Remaining: ${Money.format(debt.remaining)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = "Amount",
                    isError = error != null,
                    errorText = error
                )
                SwitchRow(
                    text = "Update balance",
                    subtitle = if (iOwe) "Subtract from the balance" else "Add to the balance",
                    checked = affectBalance,
                    onCheckedChange = { affectBalance = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = Money.parse(amountText)
                when {
                    amount == null || amount <= 0f -> error = "Enter a valid amount"
                    amount > debt.remaining + 0.001f -> error = "More than the remaining ${Money.format(debt.remaining)}"
                    else -> onConfirm(amount, affectBalance)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DebtDetailsDialog(
    debt: Debt,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRepay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(debt.person) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (debt.direction == DebtDirection.I_OWE) "I borrowed" else "I lent", fontWeight = FontWeight.SemiBold)
                Text("Amount: ${Money.format(debt.amount)}")
                Text("Remaining: ${Money.format(debt.remaining)}")
                Text("Created: ${Dates.date(debt.createdAt)}")
                debt.dueAt?.let { Text("Due: ${Dates.date(it)}") }
                if (!debt.note.isNullOrBlank()) Text("Comment: ${debt.note}")
                if (debt.payments.orEmpty().isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Payments", fontWeight = FontWeight.SemiBold)
                    debt.payments.orEmpty().sortedByDescending { it.timestamp }.forEach {
                        Text("${Dates.dateTime(it.timestamp)} — ${Money.format(it.amount)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (!debt.isSettled) TextButton(onClick = onRepay) { Text(if (debt.direction == DebtDirection.I_OWE) "Pay back" else "Got back") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }
    )
}
