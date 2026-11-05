package com.example.financeapplication.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
fun WelcomeScreen(onContinueClicked: (Float) -> Unit) {
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
        Text("Welcome!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Enter your current balance to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        AmountField(
            value = balanceText,
            onValueChange = { balanceText = it; isError = false },
            label = "Current balance",
            isError = isError,
            errorText = "Enter a valid number, e.g. 100,50 or 100.50",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Continue",
            onClick = {
                val balance = if (balanceText.isBlank()) 0f else Money.parse(balanceText)
                if (balance != null) onContinueClicked(balance) else isError = true
            }
        )
    }
}
