package com.example.financeapplication.datastores

import android.content.Context
import com.example.financeapplication.classes.NecessityLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.UUID

enum class RepeatPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    /** Returns [start] shifted by [times] periods (month/year shifts are clamped to the month end, no drift). */
    fun shift(start: Long, times: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = start }
        when (this) {
            DAILY -> c.add(Calendar.DAY_OF_YEAR, times)
            WEEKLY -> c.add(Calendar.WEEK_OF_YEAR, times)
            MONTHLY -> c.add(Calendar.MONTH, times)
            YEARLY -> c.add(Calendar.YEAR, times)
        }
        return c.timeInMillis
    }

    /**
     * Start of the calendar period containing [now]: today 00:00, Monday 00:00 of this week,
     * the 1st of this month 00:00, or January 1st 00:00.
     */
    fun periodStart(now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
        }
        when (this) {
            DAILY -> Unit
            WEEKLY -> {
                val daysSinceMonday = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                c.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
            }
            MONTHLY -> c.set(Calendar.DAY_OF_MONTH, 1)
            YEARLY -> c.set(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    /** Start of the next calendar period – the moment a recurring task "refreshes". */
    fun nextPeriodStart(now: Long): Long = shift(periodStart(now), 1)

    /** Approximate cost per month of something charged [amount] every period. */
    fun monthlyCost(amount: Float): Float = when (this) {
        DAILY -> amount * 30.4f
        WEEKLY -> amount * 52f / 12f
        MONTHLY -> amount
        YEARLY -> amount / 12f
    }
}

/**
 * A recurring payment. It is charged automatically: when [nextChargeAt] is reached,
 * a spending record is created and the balance is reduced.
 */
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Float,
    val period: RepeatPeriod = RepeatPeriod.MONTHLY,
    /** Date of the first (anchor) charge. */
    val startAt: Long,
    /** How many charges were already made since [startAt]. */
    val chargesMade: Int = 0,
    val necessity: NecessityLevel = NecessityLevel.NECESSARY,
    val active: Boolean = true,
) {
    val nextChargeAt: Long get() = (period ?: RepeatPeriod.MONTHLY).shift(startAt, chargesMade)
}

object SubscriptionsDataStore {
    private val store = JsonListStore("subscriptions", Array<Subscription>::class.java)
    private val mutex = Mutex()

    fun getSubscriptions(context: Context): Flow<List<Subscription>> = store.flow(context)

    suspend fun add(context: Context, sub: Subscription) = store.update(context) { it.add(sub) }

    suspend fun update(context: Context, sub: Subscription) = store.update(context) { list ->
        val i = list.indexOfFirst { it.id == sub.id }
        if (i != -1) list[i] = sub
    }

    suspend fun remove(context: Context, id: String) = store.update(context) { list -> list.removeAll { it.id == id } }

    /**
     * Charges every active subscription whose next payment date has come.
     * Missed periods (app not opened for a while) are charged too, each with its own date.
     * Returns the number of charges made.
     */
    suspend fun processDue(context: Context): Int = mutex.withLock {
        val now = System.currentTimeMillis()
        data class Charge(val sub: Subscription, val at: Long)

        val charges = mutableListOf<Charge>()
        store.update(context) { list ->
            for (i in list.indices) {
                var sub = list[i]
                if (!sub.active) continue
                var guard = 0
                while (sub.nextChargeAt <= now && guard < 400) {
                    charges += Charge(sub, sub.nextChargeAt)
                    sub = sub.copy(chargesMade = sub.chargesMade + 1)
                    guard++
                }
                list[i] = sub
            }
        }
        if (charges.isEmpty()) return@withLock 0

        var total = 0f
        charges.forEach { (sub, at) ->
            SpendingDataStore.addSpending(
                context,
                amount = sub.amount,
                items = listOf(sub.name),
                necessity = sub.necessity ?: NecessityLevel.NECESSARY,
                timestamp = at,
                source = SOURCE_SUBSCRIPTION
            )
            total += sub.amount
        }
        UserPreferencesDataStore.updateBalance(context, -total)
        charges.size
    }
}
