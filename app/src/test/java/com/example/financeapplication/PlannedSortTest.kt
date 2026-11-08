package com.example.financeapplication

import com.example.financeapplication.datastores.PlannedItem
import com.example.financeapplication.datastores.PlannedSort
import com.example.financeapplication.datastores.sortedFor
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannedSortTest {

    private val items = listOf(
        PlannedItem(id = "soap", name = "soap", price = 3f, createdAt = 2L),
        PlannedItem(id = "bike", name = "Bike", price = 450f, createdAt = 4L),
        PlannedItem(id = "wash", name = "Car wash", price = 15f, reusable = true, createdAt = 1L),
        PlannedItem(id = "book", name = "Book", price = 0f, createdAt = 3L),
        PlannedItem(id = "apple", name = "  apples", price = 3f, reusable = true, createdAt = 5L),
    )

    private fun ids(sort: PlannedSort) = items.sortedFor(sort).map { it.id }

    @Test
    fun alphabeticalIgnoresCaseAndLeadingSpaces() {
        assertEquals(listOf("apple", "bike", "book", "wash", "soap"), ids(PlannedSort.NAME_AZ))
        assertEquals(listOf("soap", "wash", "book", "bike", "apple"), ids(PlannedSort.NAME_ZA))
    }

    @Test
    fun byDateAdded() {
        assertEquals(listOf("apple", "bike", "book", "soap", "wash"), ids(PlannedSort.NEWEST))
        assertEquals(listOf("wash", "soap", "book", "bike", "apple"), ids(PlannedSort.OLDEST))
    }

    @Test
    fun byPriceWithoutPriceLastAndTiesAlphabetical() {
        assertEquals(listOf("bike", "wash", "apple", "soap", "book"), ids(PlannedSort.PRICE_HIGH))
        assertEquals(listOf("apple", "soap", "wash", "bike", "book"), ids(PlannedSort.PRICE_LOW))
    }

    @Test
    fun byReusable() {
        assertEquals(listOf("apple", "wash", "bike", "book", "soap"), ids(PlannedSort.REUSABLE_FIRST))
        assertEquals(listOf("bike", "book", "soap", "apple", "wash"), ids(PlannedSort.REUSABLE_LAST))
    }
}
