package com.example.financeapplication.screens

import androidx.compose.ui.res.stringResource
import com.example.financeapplication.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financeapplication.ui.components.AmountField
import com.example.financeapplication.ui.components.PrimaryButton
import com.example.financeapplication.utils.Money

@Composable
fun WelcomeScreen(onContinueClicked: (Float) -> Unit, onRestored: () -> Unit) {
    // A new device can start from a backup of another one
    val restore = com.example.financeapplication.backup.rememberBackupImport(onImported = onRestored)
    var balanceText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.welcome_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        AmountField(
            value = balanceText,
            onValueChange = { balanceText = it; isError = false },
            label = stringResource(R.string.current_balance),
            isError = isError,
            errorText = stringResource(R.string.amount_error),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.continue_text),
            onClick = {
                val balance = if (balanceText.isBlank()) 0f else Money.parse(balanceText)
                if (balance != null) onContinueClicked(balance) else isError = true
            }
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = restore) { Text(stringResource(R.string.backup_restore)) }
    }
}
