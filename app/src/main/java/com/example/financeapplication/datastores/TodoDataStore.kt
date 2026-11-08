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
    /**
     * Position within its category, set when the user reorders tasks (null = never reordered,
     * shown after the ordered tasks in the automatic order). It is kept while a task is done, so a
     * recurring task returns to the same place when its period resets.
     */
    val order: Int? = null,
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
            // their positions belonged to the deleted category, so they go after the ordered tasks
            for (i in list.indices) if (list[i].categoryId == id) list[i] = list[i].copy(categoryId = UNCATEGORIZED, order = null)
        }
    }

    /** New tasks go to the end of a category the user has already put in order. */
    suspend fun addItem(context: Context, item: TodoItem) = items.update(context) { list ->
        list.add(item.copy(order = nextOrder(list, item.categoryId)))
    }

    suspend fun updateItem(context: Context, item: TodoItem) = items.update(context) { list ->
        val i = list.indexOfFirst { it.id == item.id }
        if (i == -1) return@update
        // moved to another category: take the last place there
        list[i] = if (list[i].categoryId != item.categoryId) {
            item.copy(order = nextOrder(list.filter { it.id != item.id }, item.categoryId))
        } else item
    }

    /** Saves a new order of tasks (ids in the desired order); other tasks keep their positions. */
    suspend fun reorderItems(context: Context, orderedIds: List<String>) = items.update(context) { list ->
        val pos = orderedIds.withIndex().associate { it.value to it.index }
        for (i in list.indices) pos[list[i].id]?.let { list[i] = list[i].copy(order = it) }
    }

    private fun nextOrder(list: List<TodoItem>, categoryId: String): Int? =
        list.filter { it.categoryId == categoryId }.mapNotNull { it.order }.maxOrNull()?.plus(1)

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

/**
 * Tasks in the user's order: tasks with an [TodoItem.order] first, by that order; the rest keep
 * the order they come in (the automatic order of the screen) – `sortedBy` is stable.
 */
fun List<TodoItem>.inUserOrder(): List<TodoItem> = sortedBy { it.order ?: Int.MAX_VALUE }

/** The category a task is shown in (tasks of deleted categories fall back to "Uncategorized"). */
fun TodoItem.effectiveCategory(existing: Set<String>): String =
    if (categoryId.isNotEmpty() && categoryId in existing) categoryId else UNCATEGORIZED

internal fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
