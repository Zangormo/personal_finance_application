package com.example.financeapplication.datastores

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class DebtDirection {
    /** I borrowed money from someone. */
    I_OWE,
    /** I lent money to someone. */
    OWED_TO_ME
}

data class DebtPayment(val amount: Float, val timestamp: Long)

data class Debt(
    val id: String = UUID.randomUUID().toString(),
    val person: String,
    val amount: Float,
    val direction: DebtDirection,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val payments: List<DebtPayment>? = emptyList(),
) {
    val paid: Float get() = payments.orEmpty().sumOf { it.amount.toDouble() }.toFloat()
    val remaining: Float get() = (amount - paid).coerceAtLeast(0f)
    val isSettled: Boolean get() = remaining < 0.005f
}

object DebtsDataStore {
    private val store = JsonListStore("debts", Array<Debt>::class.java)

    fun getDebts(context: Context): Flow<List<Debt>> = store.flow(context)

    /** @param affectBalance borrowing adds money to the balance, lending takes it from the balance. */
    suspend fun add(context: Context, debt: Debt, affectBalance: Boolean) {
        store.update(context) { it.add(debt) }
        if (affectBalance) {
            val change = if (debt.direction == DebtDirection.I_OWE) debt.amount else -debt.amount
            UserPreferencesDataStore.updateBalance(context, change)
        }
    }

    suspend fun update(context: Context, debt: Debt) = store.update(context) { list ->
        val i = list.indexOfFirst { it.id == debt.id }
        if (i != -1) list[i] = debt
    }

    /** Records a (partial) repayment. Paying my debt reduces the balance, getting money back increases it. */
    suspend fun addPayment(context: Context, debt: Debt, amount: Float, affectBalance: Boolean) {
        store.update(context) { list ->
            val i = list.indexOfFirst { it.id == debt.id }
            if (i != -1) {
                val d = list[i]
                list[i] = d.copy(payments = d.payments.orEmpty() + DebtPayment(amount, System.currentTimeMillis()))
            }
        }
        if (affectBalance) {
            val change = if (debt.direction == DebtDirection.I_OWE) -amount else amount
            UserPreferencesDataStore.updateBalance(context, change)
        }
    }

    suspend fun remove(context: Context, id: String) = store.update(context) { list -> list.removeAll { it.id == id } }
}
