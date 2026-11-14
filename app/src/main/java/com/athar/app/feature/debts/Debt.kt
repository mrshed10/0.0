package com.athar.app.feature.debts

import java.time.LocalDate
import kotlin.math.abs

data class Person(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val createdAt: Long = 0,
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
)

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
            PersonBalance(person, balance(own), own.maxOfOrNull { it.date }, own.size)
        }.sortedWith(
            compareBy<PersonBalance> { it.balanceMinor == 0L }
                .thenByDescending { abs(it.balanceMinor) }
                .thenBy { it.person.name.lowercase() },
        )
    }

    fun totals(balances: List<PersonBalance>) = DebtTotals(
        owedToMeMinor = balances.filter { it.balanceMinor > 0 }.sumOf { it.balanceMinor },
        iOweMinor = balances.filter { it.balanceMinor < 0 }.sumOf { -it.balanceMinor },
        openPeople = balances.count { it.balanceMinor != 0L },
    )
}
