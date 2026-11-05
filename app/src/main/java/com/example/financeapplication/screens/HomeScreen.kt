package com.example.financeapplication.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeapplication.datastores.*
import com.example.financeapplication.ui.components.AppCard
import com.example.financeapplication.ui.components.CardShape
import com.example.financeapplication.ui.theme.*
import com.example.financeapplication.utils.Dates
import com.example.financeapplication.utils.Money

enum class HomeDestination { ADD_SPENDING, ADD_INCOME, HISTORY, PLANNED, SAVINGS, OVERVIEW, SUBSCRIPTIONS, DEBTS, TODO }

@Composable
fun HomeScreen(onNavigate: (HomeDestination) -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Charge subscriptions whose payment date has come
        SubscriptionsDataStore.processDue(context)
    }

    val balance by UserPreferencesDataStore.getOverallBalance(context).collectAsState(initial = 0f)
    val savings by UserPreferencesDataStore.getSavingsBalance(context).collectAsState(initial = 0f)
    val debts by DebtsDataStore.getDebts(context).collectAsState(initial = emptyList())
    val subs by SubscriptionsDataStore.getSubscriptions(context).collectAsState(initial = emptyList())
    val todos by TodoDataStore.getItems(context).collectAsState(initial = emptyList())
    val spendings by SpendingDataStore.getSpendings(context).collectAsState(initial = emptyList())

    val iOwe = debts.filter { it.direction == DebtDirection.I_OWE }.sumOf { it.remaining.toDouble() }.toFloat()
    val owedToMe = debts.filter { it.direction == DebtDirection.OWED_TO_ME }.sumOf { it.remaining.toDouble() }.toFloat()
    val activeSubs = subs.filter { it.active }
    val nextSub = activeSubs.minByOrNull { it.nextChargeAt }
    val openTodos = todos.count { !it.done }

    val monthStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val spentThisMonth = spendings.filter { it.timestamp >= monthStart }.sumOf { it.amount.toDouble() }.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BalanceHero(
            balance = balance,
            savings = savings,
            spentThisMonth = spentThisMonth,
            onClick = { onNavigate(HomeDestination.OVERVIEW) }
        )

        // Quick actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction(
                text = "Spending",
                icon = Icons.Filled.Remove,
                color = Negative,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(HomeDestination.ADD_SPENDING) }
            )
            QuickAction(
                text = "Income",
                icon = Icons.Filled.Add,
                color = Positive,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(HomeDestination.ADD_INCOME) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeTile("History", "Spending & income", Icons.AutoMirrored.Filled.List, Modifier.weight(1f)) {
                onNavigate(HomeDestination.HISTORY)
            }
            HomeTile("Planned", "Essentials & wishlist", Icons.Filled.ShoppingCart, Modifier.weight(1f)) {
                onNavigate(HomeDestination.PLANNED)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeTile("Savings", Money.format(savings), Icons.Filled.Savings, Modifier.weight(1f)) {
                onNavigate(HomeDestination.SAVINGS)
            }
            HomeTile("Overview", "Stats by period", Icons.Filled.PieChart, Modifier.weight(1f)) {
                onNavigate(HomeDestination.OVERVIEW)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeTile(
                "Subscriptions",
                if (nextSub != null) "Next: ${nextSub.name}, ${Dates.date(nextSub.nextChargeAt)}" else "None yet",
                Icons.Filled.Autorenew,
                Modifier.weight(1f)
            ) { onNavigate(HomeDestination.SUBSCRIPTIONS) }
            HomeTile(
                "Debts",
                when {
                    iOwe > 0 && owedToMe > 0 -> "Owe ${Money.format(iOwe)} · Owed ${Money.format(owedToMe)}"
                    iOwe > 0 -> "I owe ${Money.format(iOwe)}"
                    owedToMe > 0 -> "Owed to me ${Money.format(owedToMe)}"
                    else -> "No debts"
                },
                Icons.Filled.Handshake,
                Modifier.weight(1f)
            ) { onNavigate(HomeDestination.DEBTS) }
        }
        HomeTile(
            "To-do",
            if (openTodos > 0) "$openTodos open task${if (openTodos == 1) "" else "s"}" else "All done",
            Icons.Filled.Checklist,
            Modifier.fillMaxWidth()
        ) { onNavigate(HomeDestination.TODO) }
    }
}

@Composable
private fun BalanceHero(balance: Float, savings: Float, spentThisMonth: Float, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF172554), Color(0xFF0F172A)))
                )
                .padding(20.dp)
        ) {
            Text("Balance", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
            Text(
                Money.format(balance),
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = if (balance < 0) Negative else Color.White
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroStat("Savings", Money.format(savings), Modifier.weight(1f))
                HeroStat("Total", Money.format(balance + savings), Modifier.weight(1f))
                HeroStat("Spent this month", Money.format(spentThisMonth), Modifier.weight(1.3f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun QuickAction(text: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.16f), contentColor = color)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier = modifier.heightIn(min = 104.dp), onClick = onClick) {
        Box(
            Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}
