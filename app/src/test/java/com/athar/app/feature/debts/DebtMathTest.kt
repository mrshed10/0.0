package com.athar.app.feature.debts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DebtMathTest {
    private val day = LocalDate.of(2026, 11, 14)
    private val ali = Person(1, "Ali")
    private val sara = Person(2, "Sara")
    private val omar = Person(3, "Omar")

    private fun e(person: Person, type: EntryType, sar: Long, back: Long = 0) =
        DebtEntry(personId = person.id, type = type, amountMinor = sar * 100, date = day.minusDays(back))

    private val entries = listOf(
        e(ali, EntryType.Gave, 500, back = 3),
        e(ali, EntryType.Took, 200, back = 1), // Ali repaid 200 -> owes me 300
        e(sara, EntryType.Took, 1_000),        // I borrowed 1000 from Sara
        e(omar, EntryType.Gave, 50),
        e(omar, EntryType.Took, 50),           // settled
    )

    @Test
    fun balancesAndOrder() {
        val balances = DebtMath.balances(listOf(ali, sara, omar), entries)
        // Open balances first by size, settled last
        assertEquals(listOf("Sara", "Ali", "Omar"), balances.map { it.person.name })
        assertEquals(listOf(-100_000L, 30_000L, 0L), balances.map { it.balanceMinor })
        assertEquals(day.minusDays(1), balances[1].lastActivity)
    }

    @Test
    fun totals() {
        val totals = DebtMath.totals(DebtMath.balances(listOf(ali, sara, omar), entries))
        assertEquals(DebtTotals(owedToMeMinor = 30_000, iOweMinor = 100_000, openPeople = 2), totals)
    }

    @Test
    fun personWithoutEntriesIsSettled() {
        val b = DebtMath.balances(listOf(Person(9, "New")), emptyList()).single()
        assertEquals(0L, b.balanceMinor)
        assertEquals(null, b.lastActivity)
    }

    @Test
    fun outstandingSinceIsFirstInFirstOut() {
        // Gave 100 (10 days ago), gave 100 (5 days ago), repaid 150 -> the second loan is still open
        val list = listOf(
            e(ali, EntryType.Gave, 100, back = 10),
            e(ali, EntryType.Gave, 100, back = 5),
            e(ali, EntryType.Took, 150, back = 1),
        )
        assertEquals(day.minusDays(5), DebtMath.outstandingSince(list))
        // Fully repaid -> nothing open
        assertEquals(null, DebtMath.outstandingSince(list + e(ali, EntryType.Took, 50)))
        // I borrowed: the borrowing date is the open one
        assertEquals(day, DebtMath.outstandingSince(listOf(e(sara, EntryType.Took, 1_000))))
        val balances = DebtMath.balances(listOf(ali, sara, omar), entries)
        assertEquals(day.minusDays(3), balances.first { it.person == ali }.since)
        assertEquals(null, balances.first { it.person == omar }.since)
    }

    @Test
    fun sortModes() {
        val balances = DebtMath.balances(listOf(ali, sara, omar), entries)
        assertEquals(listOf("Ali", "Omar", "Sara"), DebtMath.sort(balances, DebtSort.Name).map { it.person.name })
        assertEquals(listOf("Ali", "Sara", "Omar"), DebtMath.sort(balances, DebtSort.AmountAsc).map { it.person.name })
        assertEquals(listOf("Sara", "Ali", "Omar"), DebtMath.sort(balances, DebtSort.AmountDesc).map { it.person.name })
        assertEquals(listOf("Ali", "Sara", "Omar"), DebtMath.sort(balances, DebtSort.Overdue).map { it.person.name })
    }
}
