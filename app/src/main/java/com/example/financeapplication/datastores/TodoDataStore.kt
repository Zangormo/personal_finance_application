package com.example.financeapplication.datastores

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

/** Category id used for tasks without a category. */
const val UNCATEGORIZED = ""

data class TodoCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Position in the category grid (user-defined). */
    val order: Int = 0,
)

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    /** [UNCATEGORIZED] or the id of a [TodoCategory]. */
    val categoryId: String = UNCATEGORIZED,
    val title: String,
    val comment: String? = null,
    /** One-time task: optional due date (+ time if [hasTime]). Recurring task: only the time of day is used. */
    val dueAt: Long? = null,
    val hasTime: Boolean = false,
    /** null = one-time task. */
    val repeat: RepeatPeriod? = null,
    /** One-time tasks only. Recurring tasks use [completedAt] + the current calendar period. */
    val done: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * One-time task: whether it is checked.
     * Recurring task: whether it was completed within the current calendar period
     * (today / this Mon–Sun week / this month / this year). It "refreshes" at 00:00
     * of the next day / Monday / 1st of the month / 1st of January.
     */
    fun isDoneNow(now: Long = System.currentTimeMillis()): Boolean {
        val period = repeat ?: return done
        val completed = completedAt ?: return false
        return completed >= period.periodStart(now)
    }
}

object TodoDataStore {
    private val categories = JsonListStore("todo_categories", Array<TodoCategory>::class.java)
    private val items = JsonListStore("todo_items", Array<TodoItem>::class.java)

    fun getCategories(context: Context): Flow<List<TodoCategory>> = categories.flow(context)
    fun getItems(context: Context): Flow<List<TodoItem>> = items.flow(context)

    suspend fun addCategory(context: Context, name: String): TodoCategory {
        var created: TodoCategory? = null
        categories.update(context) { list ->
            val c = TodoCategory(name = name, order = (list.maxOfOrNull { it.order } ?: -1) + 1)
            list.add(c)
            created = c
        }
        return created!!
    }

    suspend fun renameCategory(context: Context, id: String, name: String) = categories.update(context) { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i != -1) list[i] = list[i].copy(name = name)
    }

    /** Saves a new order of categories (ids in the desired order). */
    suspend fun reorderCategories(context: Context, orderedIds: List<String>) = categories.update(context) { list ->
        val pos = orderedIds.withIndex().associate { it.value to it.index }
        for (i in list.indices) list[i] = list[i].copy(order = pos[list[i].id] ?: (orderedIds.size + i))
    }

    /** Deletes a category; its tasks are moved to "Uncategorized". */
    suspend fun removeCategory(context: Context, id: String) {
        categories.update(context) { list -> list.removeAll { it.id == id } }
        items.update(context) { list ->
            for (i in list.indices) if (list[i].categoryId == id) list[i] = list[i].copy(categoryId = UNCATEGORIZED)
        }
    }

    suspend fun addItem(context: Context, item: TodoItem) = items.update(context) { it.add(item) }

    suspend fun updateItem(context: Context, item: TodoItem) = items.update(context) { list ->
        val i = list.indexOfFirst { it.id == item.id }
        if (i != -1) list[i] = item
    }

    suspend fun removeItem(context: Context, id: String) = items.update(context) { list -> list.removeAll { it.id == id } }

    /** Checks / unchecks a task (for recurring tasks – for the current period). */
    suspend fun toggle(context: Context, item: TodoItem) {
        val now = System.currentTimeMillis()
        val updated = when {
            item.repeat != null ->
                if (item.isDoneNow(now)) item.copy(completedAt = null) else item.copy(completedAt = now)
            item.done -> item.copy(done = false, completedAt = null)
            else -> item.copy(done = true, completedAt = now)
        }
        updateItem(context, updated)
    }
}

/** Categories in the user's order. */
fun List<TodoCategory>.ordered(): List<TodoCategory> = sortedWith(compareBy<TodoCategory> { it.order }.thenBy { it.createdAt })

/** The category a task is shown in (tasks of deleted categories fall back to "Uncategorized"). */
fun TodoItem.effectiveCategory(existing: Set<String>): String =
    if (categoryId.isNotEmpty() && categoryId in existing) categoryId else UNCATEGORIZED

internal fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
