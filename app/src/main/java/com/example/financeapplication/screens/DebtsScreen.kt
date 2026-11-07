package com.example.financeapplication.screens

import androidx.compose.ui.res.stringResource
import com.example.financeapplication.R
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
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
    val loadedDebts by DebtsDataStore.getDebts(context).collectAsState(initial = null)
    val debts = loadedDebts.orEmpty()

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
                text = { Text(stringResource(R.string.debt)) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenHeader(stringResource(R.string.debts), onBack = onBackPress)
            if (loadedDebts == null) return@Column
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Row {
                            StatValue(stringResource(R.string.i_owe), Money.format(iOwe), Modifier.weight(1f), Negative)
                            StatValue(stringResource(R.string.owed_to_me), Money.format(owedToMe), Modifier.weight(1f), Positive)
                            val net = owedToMe - iOwe
                            StatValue(stringResource(R.string.net), Money.formatSigned(net), Modifier.weight(1f), if (net < 0) Negative else Positive)
                        }
                    }
                }
                item {
                    SegmentedSelector(
                        options = listOf(stringResource(R.string.i_owe), stringResource(R.string.owed_to_me)),
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        selectedColors = listOf(Negative.copy(alpha = 0.85f), Positive.copy(alpha = 0.85f))
                    )
                }
                item {
                    SwitchRow(stringResource(R.string.show_settled), showSettled, { showSettled = it })
                }
                if (visible.isEmpty()) {
                    item { EmptyState(if (tab == 0) stringResource(R.string.debts_empty_i_owe) else stringResource(R.string.debts_empty_owed)) }
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
            title = stringResource(R.string.delete_debt_title),
            text = stringResource(R.string.delete_debt_text, debt.person),
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
            Text(
                debt.person,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                Money.format(debt.remaining),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        if (!debt.note.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                debt.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (debt.paid > 0f) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (debt.paid / debt.amount).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    stringResource(R.string.returned_amount, Money.format(debt.paid)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.of_amount, Money.format(debt.amount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    debt.isSettled -> stringResource(R.string.settled)
                    debt.dueAt != null -> if (overdue) stringResource(R.string.overdue_since, Dates.date(debt.dueAt)) else stringResource(R.string.due_on, Dates.date(debt.dueAt))
                    else -> stringResource(R.string.since_date, Dates.date(debt.createdAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) Negative else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!debt.isSettled) {
                Text(
                    if (debt.direction == DebtDirection.I_OWE) stringResource(R.string.pay_back) else stringResource(R.string.got_back),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable(onClick = onRepay)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, color = valueColor)
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
        title = { Text(if (initial == null) stringResource(R.string.new_debt) else stringResource(R.string.edit_debt)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SegmentedSelector(
                    options = listOf(stringResource(R.string.i_borrowed), stringResource(R.string.i_lent)),
                    selectedIndex = if (direction == DebtDirection.I_OWE) 0 else 1,
                    onSelect = { direction = if (it == 0) DebtDirection.I_OWE else DebtDirection.OWED_TO_ME },
                    selectedColors = listOf(Negative.copy(alpha = 0.85f), Positive.copy(alpha = 0.85f))
                )
                AppTextField(person, { person = it }, stringResource(R.string.person))
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = stringResource(R.string.amount),
                    isError = error != null,
                    errorText = error
                )
                AppTextField(note, { note = it }, stringResource(R.string.comment_optional), singleLine = false)
                PickerField(
                    Icons.Default.CalendarMonth, stringResource(R.string.due_date), dueAt?.let { Dates.date(it) },
                    onClick = { pickDate = true }, onClear = { dueAt = null }, placeholder = stringResource(R.string.due_date_optional)
                )
                if (initial == null) {
                    SwitchRow(
                        text = stringResource(R.string.update_balance),
                        subtitle = if (direction == DebtDirection.I_OWE) stringResource(R.string.update_balance_borrowed)
                        else stringResource(R.string.update_balance_lent),
                        checked = affectBalance,
                        onCheckedChange = { affectBalance = it }
                    )
                }
            }
        },
        confirmButton = {
            val invalidAmountText = stringResource(R.string.invalid_amount)
            TextButton(
                enabled = person.isNotBlank(),
                onClick = {
                    val amount = Money.parse(amountText)
                    if (amount == null || amount <= 0f) {
                        error = invalidAmountText
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
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
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
        title = { Text(if (iOwe) stringResource(R.string.pay_back_to, debt.person) else stringResource(R.string.got_back_from, debt.person)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.remaining_amount, Money.format(debt.remaining)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = stringResource(R.string.amount),
                    isError = error != null,
                    errorText = error
                )
                SwitchRow(
                    text = stringResource(R.string.update_balance),
                    subtitle = if (iOwe) stringResource(R.string.subtract_from_balance) else stringResource(R.string.add_to_balance),
                    checked = affectBalance,
                    onCheckedChange = { affectBalance = it }
                )
            }
        },
        confirmButton = {
            val invalidAmountText = stringResource(R.string.invalid_amount)
            val moreThanText = stringResource(R.string.more_than_remaining, Money.format(debt.remaining))
            TextButton(onClick = {
                val amount = Money.parse(amountText)
                when {
                    amount == null || amount <= 0f -> error = invalidAmountText
                    amount > debt.remaining + 0.001f -> error = moreThanText
                    else -> onConfirm(amount, affectBalance)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val color = if (debt.direction == DebtDirection.I_OWE) Negative else Positive
                DetailRow(stringResource(R.string.type), if (debt.direction == DebtDirection.I_OWE) stringResource(R.string.i_borrowed) else stringResource(R.string.i_lent))
                DetailRow(stringResource(R.string.amount), Money.format(debt.amount))
                DetailRow(stringResource(R.string.remaining), Money.format(debt.remaining), color)
                DetailRow(stringResource(R.string.created), Dates.date(debt.createdAt))
                debt.dueAt?.let { DetailRow(stringResource(R.string.due), Dates.date(it)) }
                if (!debt.note.isNullOrBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(debt.note)
                }
                if (debt.payments.orEmpty().isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.payments), fontWeight = FontWeight.SemiBold)
                    debt.payments.orEmpty().sortedByDescending { it.timestamp }.forEach {
                        DetailRow(Dates.dateTime(it.timestamp), Money.format(it.amount))
                    }
                }
            }
        },
        confirmButton = {
            if (!debt.isSettled) TextButton(onClick = onRepay) { Text(if (debt.direction == DebtDirection.I_OWE) stringResource(R.string.pay_back) else stringResource(R.string.got_back)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
            }
        }
    )
}
