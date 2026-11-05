package com.example.financeapplication.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financeapplication.classes.NecessityLevel
import com.example.financeapplication.datastores.SpendingDataStore
import com.example.financeapplication.datastores.IncomeDataStore
import com.example.financeapplication.datastores.UserPreferencesDataStore
import com.example.financeapplication.datastores.PlannedItemsDataStore
import com.example.financeapplication.datastores.PlannedType
import com.example.financeapplication.ui.components.ScreenHeader
import com.example.financeapplication.ui.theme.MediumColor
import com.example.financeapplication.ui.theme.NecessaryColor
import com.example.financeapplication.ui.theme.NotNeededColor
import com.example.financeapplication.utils.Money
import com.example.financeapplication.ui.theme.appColors
import java.util.Calendar

@SuppressLint("DefaultLocale")
@Composable
fun OverviewScreen(onBackPress: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = appColors()

    val balanceFlow = UserPreferencesDataStore.getOverallBalance(context)
    val currentBalance by balanceFlow.collectAsState(initial = 0f)

    val savingsFlow = UserPreferencesDataStore.getSavingsBalance(context)
    val currentSavings by savingsFlow.collectAsState(initial = 0f)

    val wishlistItemsFlow = PlannedItemsDataStore.getItems(context)
    val wishlistItems by wishlistItemsFlow.collectAsState(initial = emptyList())
    val wishlistTotal = wishlistItems.filter { it.type == PlannedType.WISH }.sumOf { it.price.toDouble() }.toFloat()

    val overallBalance = currentBalance + currentSavings

    val spendingsFlow = SpendingDataStore.getSpendings(context)
    val spendings by spendingsFlow.collectAsState(initial = emptyList())

    val incomesFlow = IncomeDataStore.getIncomes(context)
    val incomes by incomesFlow.collectAsState(initial = emptyList())

    // Period selection states
    var selectedPeriod by remember { mutableStateOf(PeriodType.MONTH) }
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }

    Column(Modifier.fillMaxSize()) {
    ScreenHeader("Overview", onBack = onBackPress)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {

        // Overall balance card (Savings + Balance)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Overall Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholderText
                )
                Text(
                    text = Money.format(overallBalance),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primaryText
                )
            }
        }

        // Balance card (current balance)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholderText
                )
                Text(
                    text = Money.format(currentBalance),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primaryText
                )
            }
        }

        // Savings balance card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Savings Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholderText
                )
                Text(
                    text = Money.format(currentSavings),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primaryText
                )
            }
        }

        // Wishlist total card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Wishlist Total",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholderText
                )
                Text(
                    text = Money.format(wishlistTotal),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primaryText
                )
            }
        }

        // Period selection
        Text(
            text = "Period Overview",
            style = MaterialTheme.typography.titleLarge,
            color = colors.primaryText,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Period buttons
        val periods = listOf(PeriodType.WEEK, PeriodType.MONTH, PeriodType.YEAR)
        com.example.financeapplication.ui.components.SegmentedSelector(
            options = listOf("7 Days", "Month", "Year"),
            selectedIndex = periods.indexOf(selectedPeriod),
            onSelect = { selectedPeriod = periods[it] },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Additional period selection based on type
        when (selectedPeriod) {
            PeriodType.MONTH -> {
                MonthYearSelector(
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    onMonthChange = { selectedMonth = it },
                    onYearChange = { selectedYear = it },
                    colors = colors,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            PeriodType.YEAR -> {
                YearSelector(
                    selectedYear = selectedYear,
                    onYearChange = { selectedYear = it },
                    colors = colors,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            PeriodType.WEEK -> {
                // No additional selector needed for 7 days
            }
        }

        // Calculate filtered spendings
        val (necessaryTotal, mediumTotal, notNeededTotal) = calculateSpendingByNecessity(
            spendings,
            selectedPeriod,
            selectedYear,
            selectedMonth
        )

        val grandTotal = necessaryTotal + mediumTotal + notNeededTotal

        // Pie chart
        if (grandTotal > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PieChart(
                        necessaryAmount = necessaryTotal,
                        mediumAmount = mediumTotal,
                        notNeededAmount = notNeededTotal,
                        modifier = Modifier
                            .size(200.dp)
                            .padding(top = 16.dp, bottom = 16.dp)
                    )

                    // Legend
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LegendItem(
                            label = "Necessary",
                            amount = necessaryTotal,
                            color = NecessaryColor,
                            total = grandTotal
                        )
                        LegendItem(
                            label = "Medium",
                            amount = mediumTotal,
                            color = MediumColor,
                            total = grandTotal
                        )
                        LegendItem(
                            label = "Not Needed",
                            amount = notNeededTotal,
                            color = NotNeededColor,
                            total = grandTotal
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No spendings in this period",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.placeholderText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Total spending
        if (grandTotal > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Total Spending",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.placeholderText
                    )
                    Text(
                        text = Money.format(grandTotal),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.primaryText
                    )
                }
            }
        }

        // Total income
        // Total income for selected period
        val totalIncome = calculateIncomeForPeriod(
            incomes,
            selectedPeriod,
            selectedYear,
            selectedMonth
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Income",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholderText
                )
                Text(
                    text = Money.format(totalIncome),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primaryText
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
    }
}

@Composable
fun PeriodButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: com.example.financeapplication.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) colors.accent else Color.Transparent,
            contentColor = if (isSelected) Color.White else colors.primaryText
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (!isSelected) {
            ButtonDefaults.outlinedButtonBorder(enabled = true)
        } else {
            null
        }
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MonthYearSelector(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthChange: (Int) -> Unit,
    onYearChange: (Int) -> Unit,
    colors: com.example.financeapplication.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month selector
        var expandedMonth by remember { mutableStateOf(false) }
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = months[selectedMonth],
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                readOnly = true,
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = colors.primaryText,
                    disabledBorderColor = colors.border,
                    disabledTrailingIconColor = colors.primaryText
                ),
                trailingIcon = {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expandedMonth = !expandedMonth }
            )
            DropdownMenu(
                expanded = expandedMonth,
                onDismissRequest = { expandedMonth = false }
            ) {
                months.forEachIndexed { index, month ->
                    DropdownMenuItem(
                        text = { Text(month) },
                        onClick = {
                            onMonthChange(index)
                            expandedMonth = false
                        }
                    )
                }
            }
        }

        // Year selector
        var expandedYear by remember { mutableStateOf(false) }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearRange = (currentYear - 10)..currentYear
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = selectedYear.toString(),
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                readOnly = true,
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = colors.primaryText,
                    disabledBorderColor = colors.border,
                    disabledTrailingIconColor = colors.primaryText
                ),
                trailingIcon = {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expandedYear = !expandedYear }
            )
            DropdownMenu(
                expanded = expandedYear,
                onDismissRequest = { expandedYear = false }
            ) {
                yearRange.reversed().forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = {
                            onYearChange(year)
                            expandedYear = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun YearSelector(
    selectedYear: Int,
    onYearChange: (Int) -> Unit,
    colors: com.example.financeapplication.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    var expandedYear by remember { mutableStateOf(false) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val yearRange = (currentYear - 10)..currentYear

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
            readOnly = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = colors.primaryText,
                disabledBorderColor = colors.border,
                disabledTrailingIconColor = colors.primaryText
            ),
            trailingIcon = {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expandedYear = !expandedYear }
        )
        DropdownMenu(
            expanded = expandedYear,
            onDismissRequest = { expandedYear = false }
        ) {
            yearRange.reversed().forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        onYearChange(year)
                        expandedYear = false
                    }
                )
            }
        }
    }
}

@Composable
fun PieChart(
    necessaryAmount: Float,
    mediumAmount: Float,
    notNeededAmount: Float,
    modifier: Modifier = Modifier
) {
    val total = necessaryAmount + mediumAmount + notNeededAmount
    if (total == 0f) return

    val necessaryPercentage = necessaryAmount / total
    val mediumPercentage = mediumAmount / total
    val notNeededPercentage = notNeededAmount / total

    val gapAngle = 3f

    Canvas(modifier = modifier) {
        val outerRadius = size.minDimension / 2
        val innerRadius = outerRadius * 0.6f
        val centerX = size.width / 2
        val centerY = size.height / 2

        val strokeWidth = outerRadius - innerRadius

        // Draw Necessary (Green)
        if (necessaryAmount > 0) {
            drawArc(
                color = NecessaryColor,
                startAngle = -90f,
                sweepAngle = necessaryPercentage * 360f - gapAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    centerX - outerRadius,
                    centerY - outerRadius
                ),
                size = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }

        // Draw Medium (Yellow)
        if (mediumAmount > 0) {
            drawArc(
                color = MediumColor,
                startAngle = -90f + necessaryPercentage * 360f,
                sweepAngle = mediumPercentage * 360f - gapAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    centerX - outerRadius,
                    centerY - outerRadius
                ),
                size = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }

        // Draw Not Needed (Red)
        if (notNeededAmount > 0) {
            drawArc(
                color = NotNeededColor,
                startAngle = -90f + (necessaryPercentage + mediumPercentage) * 360f,
                sweepAngle = notNeededPercentage * 360f - gapAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    centerX - outerRadius,
                    centerY - outerRadius
                ),
                size = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun Canvas(modifier: Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(modifier = modifier, onDraw = onDraw)
}

@SuppressLint("DefaultLocale")
@Composable
fun LegendItem(
    label: String,
    amount: Float,
    color: Color,
    total: Float
) {
    val percentage = if (total > 0) (amount / total * 100) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${Money.format(amount)} (${String.format("%.1f", percentage)}%)",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

enum class PeriodType {
    WEEK, MONTH, YEAR
}

fun calculateSpendingByNecessity(
    spendings: List<com.example.financeapplication.datastores.SpendingRecord>,
    periodType: PeriodType,
    year: Int,
    month: Int
): Triple<Float, Float, Float> {
    val startOfPeriod = Calendar.getInstance()
    val endOfPeriod = Calendar.getInstance()

    when (periodType) {
        PeriodType.WEEK -> {
            // For week, use last 7 days ending today
            startOfPeriod.add(Calendar.DAY_OF_YEAR, -6)
            startOfPeriod.set(Calendar.HOUR_OF_DAY, 0)
            startOfPeriod.set(Calendar.MINUTE, 0)
            startOfPeriod.set(Calendar.SECOND, 0)
            startOfPeriod.set(Calendar.MILLISECOND, 0)

            endOfPeriod.set(Calendar.HOUR_OF_DAY, 23)
            endOfPeriod.set(Calendar.MINUTE, 59)
            endOfPeriod.set(Calendar.SECOND, 59)
            endOfPeriod.set(Calendar.MILLISECOND, 999)
        }
        PeriodType.MONTH -> {
            // Set to start of selected month
            startOfPeriod.set(year, month, 1, 0, 0, 0)
            startOfPeriod.set(Calendar.MILLISECOND, 0)

            // Set to end of selected month
            endOfPeriod.set(year, month, 1, 23, 59, 59)
            endOfPeriod.set(Calendar.MILLISECOND, 999)
            endOfPeriod.set(Calendar.DAY_OF_MONTH, endOfPeriod.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        PeriodType.YEAR -> {
            // Set to start of selected year
            startOfPeriod.set(year, 0, 1, 0, 0, 0)
            startOfPeriod.set(Calendar.MILLISECOND, 0)

            // Set to end of selected year
            endOfPeriod.set(year, 11, 31, 23, 59, 59)
            endOfPeriod.set(Calendar.MILLISECOND, 999)
        }
    }

    var necessaryTotal = 0f
    var mediumTotal = 0f
    var notNeededTotal = 0f

    spendings.forEach { spending ->
        val spendingCalendar = Calendar.getInstance().apply {
            timeInMillis = spending.timestamp
        }

        if (spendingCalendar.timeInMillis >= startOfPeriod.timeInMillis &&
            spendingCalendar.timeInMillis <= endOfPeriod.timeInMillis
        ) {
            when (spending.necessity) {
                NecessityLevel.NECESSARY -> necessaryTotal += spending.amount
                NecessityLevel.MEDIUM -> mediumTotal += spending.amount
                NecessityLevel.NOT_NEEDED -> notNeededTotal += spending.amount
            }
        }
    }

    return Triple(necessaryTotal, mediumTotal, notNeededTotal)
}

fun calculateIncomeForPeriod(
    incomes: List<com.example.financeapplication.datastores.IncomeRecord>,
    periodType: PeriodType,
    year: Int,
    month: Int
): Float {
    val startOfPeriod = Calendar.getInstance()
    val endOfPeriod = Calendar.getInstance()

    when (periodType) {
        PeriodType.WEEK -> {
            // For week, use last 7 days ending today
            startOfPeriod.add(Calendar.DAY_OF_YEAR, -6)
            startOfPeriod.set(Calendar.HOUR_OF_DAY, 0)
            startOfPeriod.set(Calendar.MINUTE, 0)
            startOfPeriod.set(Calendar.SECOND, 0)
            startOfPeriod.set(Calendar.MILLISECOND, 0)

            endOfPeriod.set(Calendar.HOUR_OF_DAY, 23)
            endOfPeriod.set(Calendar.MINUTE, 59)
            endOfPeriod.set(Calendar.SECOND, 59)
            endOfPeriod.set(Calendar.MILLISECOND, 999)
        }
        PeriodType.MONTH -> {
            // Set to start of selected month
            startOfPeriod.set(year, month, 1, 0, 0, 0)
            startOfPeriod.set(Calendar.MILLISECOND, 0)

            // Set to end of selected month
            endOfPeriod.set(year, month, 1, 23, 59, 59)
            endOfPeriod.set(Calendar.MILLISECOND, 999)
            endOfPeriod.set(Calendar.DAY_OF_MONTH, endOfPeriod.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        PeriodType.YEAR -> {
            // Set to start of selected year
            startOfPeriod.set(year, 0, 1, 0, 0, 0)
            startOfPeriod.set(Calendar.MILLISECOND, 0)

            // Set to end of selected year
            endOfPeriod.set(year, 11, 31, 23, 59, 59)
            endOfPeriod.set(Calendar.MILLISECOND, 999)
        }
    }

    var totalIncome = 0f

    incomes.forEach { income ->
        val incomeCalendar = Calendar.getInstance().apply {
            timeInMillis = income.timestamp
        }

        if (incomeCalendar.timeInMillis >= startOfPeriod.timeInMillis &&
            incomeCalendar.timeInMillis <= endOfPeriod.timeInMillis
        ) {
            totalIncome += income.amount
        }
    }

    return totalIncome
}