package com.example.financeapplication.ui.components

import androidx.compose.ui.res.stringResource
import com.example.financeapplication.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Calendar
import java.util.TimeZone

/** Local midnight of the given local timestamp, expressed as UTC midnight of the same calendar day (what DatePicker uses). */
private fun localToUtcDay(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Converts a DatePicker UTC day to a local timestamp keeping the hour/minute of [keepTimeFrom] (or midnight). */
private fun utcDayToLocal(utc: Long, keepTimeFrom: Long?): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    val t = keepTimeFrom?.let { Calendar.getInstance().apply { timeInMillis = it } }
    return Calendar.getInstance().apply {
        clear()
        set(
            u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH),
            t?.get(Calendar.HOUR_OF_DAY) ?: 0, t?.get(Calendar.MINUTE) ?: 0
        )
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initial: Long?,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onlyTodayOrLater: Boolean = false,
) {
    val todayUtc = remember { localToUtcDay(System.currentTimeMillis()) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = localToUtcDay(initial ?: System.currentTimeMillis()),
        selectableDates = if (onlyTodayOrLater) object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
        } else DatePickerDefaults.AllDates
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(utcDayToLocal(it, initial)) }
                onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(state = state)
    }
}

/** Picks a time for the day of [base] (or today). Returns a local timestamp. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePickerDialog(
    base: Long?,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    // false when [base] only carries a date: the picker then starts at 09:00 instead of 00:00
    baseHasTime: Boolean = base != null,
) {
    val c = remember { Calendar.getInstance().apply { timeInMillis = base ?: System.currentTimeMillis() } }
    val state = rememberTimePickerState(
        initialHour = if (base != null && baseHasTime) c.get(Calendar.HOUR_OF_DAY) else 9,
        initialMinute = if (base != null && baseHasTime) c.get(Calendar.MINUTE) else 0,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                val result = Calendar.getInstance().apply {
                    timeInMillis = base ?: System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, state.hour)
                    set(Calendar.MINUTE, state.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onPick(result)
                onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
