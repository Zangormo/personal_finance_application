package com.example.financeapplication.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financeapplication.classes.NecessityLevel
import com.example.financeapplication.datastores.*
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.ui.theme.Negative
import com.example.financeapplication.ui.theme.Positive
import com.example.financeapplication.utils.Money
import kotlinx.coroutines.launch

/** Merged "Add spending" + "Add income" screen. */
@Composable
fun TransactionScreen(initialIsIncome: Boolean, onBackPress: () -> Unit) {
    var isIncome by rememberSaveable { mutableStateOf(initialIsIncome) }
    val context = LocalContext.current
    val balance by UserPreferencesDataStore.getOverallBalance(context).collectAsState(initial = 0f)
    val savings by UserPreferencesDataStore.getSavingsBalance(context).collectAsState(initial = 0f)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ScreenHeader(title = if (isIncome) "Add income" else "Add spending", onBack = onBackPress)
        Column(Modifier.padding(horizontal = 16.dp)) {
            SegmentedSelector(
                options = listOf("Spending", "Income"),
                selectedIndex = if (isIncome) 1 else 0,
                onSelect = { isIncome = it == 1 },
                selectedColors = listOf(Negative.copy(alpha = 0.85f), Positive.copy(alpha = 0.85f))
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                StatValue("Balance", Money.format(balance), Modifier.weight(1f), if (balance < 0) Negative else Color.Unspecified)
                StatValue("Savings", Money.format(savings), Modifier.weight(1f))
            }
        }
        AnimatedContent(
            targetState = isIncome,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                // Income is to the right of Spending, so slide accordingly
                val dir = if (targetState) 1 else -1
                (slideInHorizontally(tween(280)) { dir * it / 3 } + fadeIn(tween(280)))
                    .togetherWith(slideOutHorizontally(tween(280)) { -dir * it / 3 } + fadeOut(tween(200)))
            },
            label = "transactionForm"
        ) { income ->
            Column(Modifier.fillMaxSize()) {
                if (income) {
                    IncomeForm(onDone = onBackPress)
                } else {
                    SpendingForm(balance = balance, savings = savings, onDone = onBackPress)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.IncomeForm(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amountText by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .weight(1f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AmountField(
            value = amountText,
            onValueChange = { amountText = it; error = null },
            label = "Income amount",
            isError = error != null,
            errorText = error
        )
        AppTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description",
            placeholder = "Salary, bonus, ..."
        )
    }
    PrimaryButton(
        text = "Add income",
        containerColor = Positive.copy(alpha = 0.9f),
        modifier = Modifier.padding(16.dp),
        onClick = {
            val amount = Money.parse(amountText)
            when {
                amount == null -> error = "Enter a valid amount, e.g. 100,50 or 100.50"
                amount <= 0f -> error = "Amount must be greater than 0"
                else -> scope.launch {
                    IncomeDataStore.addIncome(context, amount, description.trim())
                    UserPreferencesDataStore.updateBalance(context, amount)
                    onDone()
                }
            }
        }
    )
}

@Composable
private fun ColumnScope.SpendingForm(balance: Float, savings: Float, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val planned by PlannedItemsDataStore.getItems(context).collectAsState(initial = emptyList())

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var amountEditedManually by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var necessity by rememberSaveable { mutableStateOf(NecessityLevel.NECESSARY) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingAmount by remember { mutableStateOf<Float?>(null) }

    val selectedItems = planned.filter { it.id in selectedIds }
    val selectedPrice = selectedItems.sumOf { it.price.toDouble() }.toFloat()

    // Selected planned items with a price fill in the amount (unless the user typed their own)
    LaunchedEffect(selectedPrice) {
        if (!amountEditedManually) amountText = Money.toInput(selectedPrice)
    }

    fun save(amount: Float, withdrawFromSavings: Float) {
        scope.launch {
            if (withdrawFromSavings > 0f) {
                UserPreferencesDataStore.updateSavingsBalance(context, -withdrawFromSavings)
                UserPreferencesDataStore.updateBalance(context, withdrawFromSavings)
            }
            val names = selectedItems.map { it.name } + listOfNotNull(note.trim().ifEmpty { null })
            SpendingDataStore.addSpending(context, amount, names, necessity)
            UserPreferencesDataStore.updateBalance(context, -amount)
            PlannedItemsDataStore.consume(context, selectedIds)
            onDone()
        }
    }

    val essentials = planned.filter { it.type == PlannedType.ESSENTIAL }
    val wishes = planned.filter { it.type == PlannedType.WISH }

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AmountField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    amountEditedManually = it.isNotEmpty()
                    error = null
                },
                label = "Amount spent",
                isError = error != null,
                errorText = error
            )
        }
        item {
            AppTextField(value = note, onValueChange = { note = it }, label = "What for? (optional)")
        }
        item {
            Text(
                "Necessity",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            NecessitySelector(value = necessity, onChange = { necessity = it })
        }

        if (essentials.isNotEmpty()) {
            item { SectionTitle("Essentials") }
            items(essentials, key = { it.id }) { item ->
                SelectablePlannedRow(item, item.id in selectedIds) { sel ->
                    selectedIds = if (sel) selectedIds + item.id else selectedIds - item.id
                }
            }
        }
        if (wishes.isNotEmpty()) {
            item { SectionTitle("Wishlist") }
            items(wishes, key = { it.id }) { item ->
                SelectablePlannedRow(item, item.id in selectedIds) { sel ->
                    selectedIds = if (sel) selectedIds + item.id else selectedIds - item.id
                }
            }
        }
        if (planned.isEmpty()) {
            item {
                Text(
                    "Tip: add essentials and wishlist items in \"Planned\" to pick them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    PrimaryButton(
        text = "Add spending",
        containerColor = Negative.copy(alpha = 0.9f),
        modifier = Modifier.padding(16.dp),
        onClick = {
            val amount = Money.parse(amountText)
            when {
                amount == null -> error = "Enter a valid amount, e.g. 100,50 or 100.50"
                amount <= 0f -> error = "Amount must be greater than 0"
                amount > balance -> pendingAmount = amount
                else -> save(amount, 0f)
            }
        }
    )

    pendingAmount?.let { amount ->
        val deficit = Money.round(amount - balance.coerceAtLeast(0f))
        val canCover = savings >= deficit
        AlertDialog(
            onDismissRequest = { pendingAmount = null },
            title = { Text("Not enough balance") },
            text = {
                Text(
                    "This spending is ${Money.format(amount)}, but the balance is ${Money.format(balance)}." +
                        if (canCover) "\n\nWithdraw ${Money.format(deficit)} from savings (${Money.format(savings)} available)?"
                        else "\n\nSavings (${Money.format(savings)}) are not enough to cover it. The balance will go negative."
                )
            },
            confirmButton = {
                if (canCover) {
                    TextButton(onClick = { pendingAmount = null; save(amount, deficit) }) {
                        Text("Withdraw & spend")
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingAmount = null }) { Text("Cancel") }
                    TextButton(onClick = { pendingAmount = null; save(amount, 0f) }) {
                        Text("Spend anyway", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}

@Composable
private fun SelectablePlannedRow(item: PlannedItem, selected: Boolean, onChange: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface, tween(200), label = "rowBg"
    )
    val borderColor by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.outlineVariant, tween(200), label = "rowBorder"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onChange(!selected) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) accent else Color.Transparent)
                .border(2.dp, if (selected) accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (item.reusable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Autorenew, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Reusable – stays in the list",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (item.price > 0f) {
            Text(Money.format(item.price), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
