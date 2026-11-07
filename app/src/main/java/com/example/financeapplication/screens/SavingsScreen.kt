package com.example.financeapplication.screens

import androidx.compose.ui.res.stringResource
import com.example.financeapplication.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeapplication.datastores.UserPreferencesDataStore
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.ui.theme.Positive
import com.example.financeapplication.utils.Money
import kotlinx.coroutines.launch

@Composable
fun SavingsScreen(onBackPress: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val balance by UserPreferencesDataStore.getOverallBalance(context).collectAsState(initial = 0f)
    val savings by UserPreferencesDataStore.getSavingsBalance(context).collectAsState(initial = 0f)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isSave = tab == 0

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(stringResource(R.string.savings), onBack = onBackPress)
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.savings), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(savings), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Positive)
                Spacer(Modifier.height(8.dp))
                Row {
                    StatValue(stringResource(R.string.balance), Money.format(balance), Modifier.weight(1f))
                    StatValue(stringResource(R.string.total), Money.format(balance + savings), Modifier.weight(1f))
                }
            }

            SegmentedSelector(
                options = listOf(stringResource(R.string.save_money), stringResource(R.string.withdraw)),
                selectedIndex = tab,
                onSelect = { tab = it; error = null }
            )

            AmountField(
                value = amountText,
                onValueChange = { amountText = it; error = null },
                label = if (isSave) stringResource(R.string.amount_to_save) else stringResource(R.string.amount_to_withdraw),
                isError = error != null,
                errorText = error
            )

            // Quick fill: all available money
            val available = if (isSave) balance else savings
            if (available > 0f) {
                TextButton(onClick = { amountText = Money.toInput(available); error = null }) {
                    Text(stringResource(R.string.use_all, Money.format(available)))
                }
            }

            PrimaryButton(
                text = if (isSave) stringResource(R.string.add_to_savings) else stringResource(R.string.withdraw_from_savings),
                onClick = {
                    val amount = Money.parse(amountText)
                    when {
                        amount == null || amount <= 0f -> error = context.getString(R.string.amount_error)
                        isSave && amount > balance -> error = context.getString(R.string.exceeds_balance, Money.format(balance))
                        !isSave && amount > savings -> error = context.getString(R.string.exceeds_savings, Money.format(savings))
                        else -> scope.launch {
                            if (isSave) {
                                UserPreferencesDataStore.updateBalance(context, -amount)
                                UserPreferencesDataStore.updateSavingsBalance(context, amount)
                            } else {
                                UserPreferencesDataStore.updateSavingsBalance(context, -amount)
                                UserPreferencesDataStore.updateBalance(context, amount)
                            }
                            amountText = ""
                        }
                    }
                }
            )
        }
    }
}
