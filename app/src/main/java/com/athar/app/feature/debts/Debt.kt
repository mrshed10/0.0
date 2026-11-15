package com.athar.app.feature.debts

import java.time.LocalDate
import kotlin.math.abs

data class Person(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val createdAt: Long = 0,
    val address: String = "",
) {
    val isNew: Boolean get() = id == 0L
}

/**
 * Classic ledger: [Gave] = I gave them money/goods (they owe me more),
 * [Took] = I received from them (a repayment, or I borrowed).
 */
enum class EntryType { Gave, Took }

data class DebtEntry(
    val id: Long = 0,
    val personId: Long,
    val type: EntryType,
    val amountMinor: Long,
    val note: String = "",
    val date: LocalDate,
    val createdAt: Long = 0,
) {
    val isNew: Boolean get() = id == 0L
}

/** Positive balance = they owe me; negative = I owe them. */
data class PersonBalance(
    val person: Person,
    val balanceMinor: Long,
    val lastActivity: LocalDate?,
    val entryCount: Int,
    /** Date of the oldest record still not settled (null when settled). */
    val since: LocalDate? = null,
)

/** How the people list is ordered. */
enum class DebtSort { AmountDesc, AmountAsc, Overdue, Name, Recent }

data class DebtTotals(val owedToMeMinor: Long, val iOweMinor: Long, val openPeople: Int) {
    companion object {
        val Empty = DebtTotals(0, 0, 0)
    }
}

object DebtMath {
    fun balance(entries: List<DebtEntry>): Long =
        entries.sumOf { if (it.type == EntryType.Gave) it.amountMinor else -it.amountMinor }

    /** Open balances first (largest first), settled people after, by name. */
    fun balances(people: List<Person>, entries: List<DebtEntry>): List<PersonBalance> {
        val byPerson = entries.groupBy { it.personId }
        return people.map { person ->
            val own = byPerson[person.id].orEmpty()
            PersonBalance(person, balance(own), own.maxOfOrNull { it.date }, own.size, outstandingSince(own))
        }.sortedWith(
            compareBy<PersonBalance> { it.balanceMinor == 0L }
                .thenByDescending { abs(it.balanceMinor) }
                .thenBy { it.person.name.lowercase() },
        )
    }

    /**
     * First-in-first-out: repayments settle the oldest records first, so the
     * date of the oldest record still open shows how long a debt is overdue.
     */
    fun outstandingSince(entries: List<DebtEntry>): LocalDate? {
        val gave = ArrayDeque<Pair<LocalDate, Long>>()
        val took = ArrayDeque<Pair<LocalDate, Long>>()
        for (e in entries.sortedWith(compareBy<DebtEntry> { it.date }.thenBy { it.createdAt })) {
            val (mine, other) = if (e.type == EntryType.Gave) gave to took else took to gave
            var left = e.amountMinor
            while (left > 0 && other.isNotEmpty()) {
                val (date, amount) = other.removeFirst()
                if (amount > left) {
                    other.addFirst(date to amount - left)
                    left = 0
                } else {
                    left -= amount
                }
            }
            if (left > 0) mine.addLast(e.date to left)
        }
        return (gave.firstOrNull() ?: took.firstOrNull())?.first
    }

    fun sort(list: List<PersonBalance>, sort: DebtSort): List<PersonBalance> = when (sort) {
        DebtSort.AmountDesc -> list.sortedWith(compareByDescending<PersonBalance> { abs(it.balanceMinor) }.thenBy { it.person.name.lowercase() })
        DebtSort.AmountAsc -> list.sortedWith(compareBy<PersonBalance> { it.balanceMinor == 0L }.thenBy { abs(it.balanceMinor) })
        DebtSort.Overdue -> list.sortedWith(compareBy<PersonBalance> { it.since == null }.thenBy { it.since })
        DebtSort.Name -> list.sortedBy { it.person.name.lowercase() }
        DebtSort.Recent -> list.sortedWith(compareBy<PersonBalance> { it.lastActivity == null }.thenByDescending { it.lastActivity })
    }

    fun totals(balances: List<PersonBalance>) = DebtTotals(
        owedToMeMinor = balances.filter { it.balanceMinor > 0 }.sumOf { it.balanceMinor },
        iOweMinor = balances.filter { it.balanceMinor < 0 }.sumOf { -it.balanceMinor },
        openPeople = balances.count { it.balanceMinor != 0L },
    )
}
