package com.example.financeapplication.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financeapplication.classes.NecessityLevel
import com.example.financeapplication.datastores.RepeatPeriod
import com.example.financeapplication.datastores.Subscription
import com.example.financeapplication.datastores.SubscriptionsDataStore
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.utils.Dates
import com.example.financeapplication.utils.Money
import kotlinx.coroutines.launch

@Composable
fun SubscriptionsScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subs by SubscriptionsDataStore.getSubscriptions(context).collectAsState(initial = emptyList())
    val sorted = subs.sortedWith(compareBy<Subscription> { !it.active }.thenBy { it.nextChargeAt })
    val monthly = subs.filter { it.active }.sumOf { it.period.monthlyCost(it.amount).toDouble() }.toFloat()

    var editing by remember { mutableStateOf<Subscription?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Subscription?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Subscription") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenHeader("Subscriptions", onBack = onBackPress)
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Row {
                            StatValue("Per month", Money.format(monthly), Modifier.weight(1f))
                            StatValue("Per year", Money.format(monthly * 12), Modifier.weight(1f))
                            StatValue("Active", subs.count { it.active }.toString(), Modifier.weight(0.6f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Payments are charged automatically on their date and appear in History.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (subs.isEmpty()) item { EmptyState("No subscriptions yet") }
                items(sorted, key = { it.id }) { sub ->
                    AppCard(
                        Modifier.animateItem()
                            .fillMaxWidth()
                            .alpha(if (sub.active) 1f else 0.55f),
                        onClick = { editing = sub }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(necessityColor(sub.necessity))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sub.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    if (sub.active) "Next payment: ${Dates.date(sub.nextChargeAt)}" else "Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(sub.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    sub.period.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = sub.active,
                                onCheckedChange = { active ->
                                    scope.launch {
                                        // When resuming, start counting again from the next future date
                                        val updated = if (active) restartFromToday(sub) else sub.copy(active = false)
                                        SubscriptionsDataStore.update(context, updated)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        SubscriptionDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onDelete = editing?.let { e -> { deleting = e; editing = null } },
            onSave = { sub ->
                scope.launch {
                    if (editing == null) SubscriptionsDataStore.add(context, sub) else SubscriptionsDataStore.update(context, sub)
                    SubscriptionsDataStore.processDue(context)
                }
                adding = false; editing = null
            }
        )
    }
    deleting?.let { sub ->
        ConfirmDialog(
            title = "Delete subscription?",
            text = "\"${sub.name}\" will no longer be charged. Past payments stay in History.",
            onConfirm = {
                scope.launch { SubscriptionsDataStore.remove(context, sub.id) }
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

/** Resumes a paused subscription: next payment is the first occurrence that is today or later. */
private fun restartFromToday(sub: Subscription): Subscription {
    val today = startOfDay(System.currentTimeMillis())
    var s = sub.copy(active = true)
    var guard = 0
    while (s.nextChargeAt < today && guard < 5000) {
        s = s.copy(chargesMade = s.chargesMade + 1); guard++
    }
    return s
}

private fun startOfDay(ts: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = ts
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun SubscriptionDialog(
    initial: Subscription?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (Subscription) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var amountText by remember { mutableStateOf(initial?.let { Money.toInput(it.amount) }.orEmpty()) }
    var period by remember { mutableStateOf(initial?.period ?: RepeatPeriod.MONTHLY) }
    var nextDate by remember { mutableLongStateOf(initial?.nextChargeAt ?: startOfDay(System.currentTimeMillis())) }
    var necessity by remember { mutableStateOf(initial?.necessity ?: NecessityLevel.NECESSARY) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    val periods = listOf(RepeatPeriod.WEEKLY, RepeatPeriod.MONTHLY, RepeatPeriod.YEARLY)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New subscription" else "Edit subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(name, { name = it }, "Name", placeholder = "Netflix, gym, ...")
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = "Amount",
                    isError = error != null,
                    errorText = error
                )
                SegmentedSelector(
                    options = periods.map { it.label },
                    selectedIndex = periods.indexOf(period).coerceAtLeast(0),
                    onSelect = { period = periods[it] }
                )
                PickerField(Icons.Default.CalendarMonth, "Next payment", Dates.date(nextDate), onClick = { pickDate = true })
                NecessitySelector(necessity, { necessity = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val amount = Money.parse(amountText)
                    if (amount == null || amount <= 0f) {
                        error = "Enter a valid amount"
                        return@TextButton
                    }
                    val scheduleChanged = initial == null || initial.period != period ||
                        startOfDay(initial.nextChargeAt) != startOfDay(nextDate)
                    val base = initial ?: Subscription(name = "", amount = 0f, startAt = nextDate)
                    onSave(
                        base.copy(
                            name = name.trim(),
                            amount = amount,
                            period = period,
                            necessity = necessity,
                            startAt = if (scheduleChanged) nextDate else base.startAt,
                            chargesMade = if (scheduleChanged) 0 else base.chargesMade,
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (pickDate) {
        AppDatePickerDialog(
            initial = nextDate,
            onDismiss = { pickDate = false },
            onPick = { nextDate = startOfDay(it) },
            onlyTodayOrLater = true
        )
    }
}
