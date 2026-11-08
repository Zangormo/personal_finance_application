package com.example.financeapplication

import com.example.financeapplication.datastores.RepeatPeriod
import com.example.financeapplication.datastores.TodoItem
import com.example.financeapplication.datastores.appGson
import com.example.financeapplication.datastores.inUserOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoOrderTest {

    private fun task(id: String, order: Int? = null, repeat: RepeatPeriod? = null) =
        TodoItem(id = id, title = id, order = order, repeat = repeat, createdAt = 1L)

    @Test
    fun withoutUserOrderTheAutomaticOrderIsKept() {
        val auto = listOf(task("a"), task("b"), task("c"))
        assertEquals(listOf("a", "b", "c"), auto.inUserOrder().map { it.id })
    }

    @Test
    fun orderedTasksComeFirstAndTheRestKeepsTheAutomaticOrder() {
        val auto = listOf(task("new1"), task("x", order = 2), task("new2"), task("y", order = 0), task("z", order = 1))
        assertEquals(listOf("y", "z", "x", "new1", "new2"), auto.inUserOrder().map { it.id })
    }

    @Test
    fun recurringTaskReturnsToItsPlaceAfterTheReset() {
        // "rent" is done for this month, so the screen lists it after the open tasks...
        val whileDone = listOf(task("a", 0), task("c", 2)) + listOf(task("rent", 1, RepeatPeriod.MONTHLY))
        // ...but the full (edit mode) order and the order after the reset put it back between a and c
        assertEquals(listOf("a", "rent", "c"), whileDone.inUserOrder().map { it.id })
    }

    @Test
    fun tasksSavedByOlderVersionsHaveNoOrder() {
        val json = """[{"id":"t1","categoryId":"","title":"Old task","hasTime":false,"done":false,"createdAt":5}]"""
        val item = appGson.fromJson(json, Array<TodoItem>::class.java).single()
        assertNull(item.order)
    }

    @Test
    fun orderSurvivesJson() {
        val items = listOf(task("a", 3, RepeatPeriod.DAILY), task("b"))
        val back = appGson.fromJson(appGson.toJson(items), Array<TodoItem>::class.java).toList()
        assertEquals(items, back)
    }
}
