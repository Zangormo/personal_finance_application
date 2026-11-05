package com.example.financeapplication.datastores

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

data class TodoCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val title: String,
    val comment: String? = null,
    /** Optional due date (and time if [hasTime]). */
    val dueAt: Long? = null,
    val hasTime: Boolean = false,
    /** null = one-time task. */
    val repeat: RepeatPeriod? = null,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object TodoDataStore {
    private val categories = JsonListStore("todo_categories", Array<TodoCategory>::class.java)
    private val items = JsonListStore("todo_items", Array<TodoItem>::class.java)

    fun getCategories(context: Context): Flow<List<TodoCategory>> = categories.flow(context)
    fun getItems(context: Context): Flow<List<TodoItem>> = items.flow(context)

    suspend fun addCategory(context: Context, name: String): TodoCategory {
        val c = TodoCategory(name = name)
        categories.update(context) { it.add(c) }
        return c
    }

    suspend fun renameCategory(context: Context, id: String, name: String) = categories.update(context) { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i != -1) list[i] = list[i].copy(name = name)
    }

    /** Deletes a category together with all its tasks. */
    suspend fun removeCategory(context: Context, id: String) {
        categories.update(context) { list -> list.removeAll { it.id == id } }
        items.update(context) { list -> list.removeAll { it.categoryId == id } }
    }

    suspend fun addItem(context: Context, item: TodoItem) = items.update(context) { it.add(item) }

    suspend fun updateItem(context: Context, item: TodoItem) = items.update(context) { list ->
        val i = list.indexOfFirst { it.id == item.id }
        if (i != -1) list[i] = item
    }

    suspend fun removeItem(context: Context, id: String) = items.update(context) { list -> list.removeAll { it.id == id } }

    /**
     * Toggles a task. A recurring task is never "done" – checking it moves it to the next occurrence.
     * Returns the updated item.
     */
    suspend fun toggle(context: Context, item: TodoItem): TodoItem {
        val now = System.currentTimeMillis()
        val updated = when {
            item.repeat != null && !item.done -> {
                val base = item.dueAt ?: startOfToday()
                var times = 1
                var next = item.repeat.shift(base, times)
                while (next <= now && times < 10_000) {
                    times++
                    next = item.repeat.shift(base, times)
                }
                item.copy(dueAt = next, completedAt = now)
            }
            item.done -> item.copy(done = false, completedAt = null)
            else -> item.copy(done = true, completedAt = now)
        }
        updateItem(context, updated)
        return updated
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
