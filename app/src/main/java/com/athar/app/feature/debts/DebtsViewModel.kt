package com.athar.app.feature.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.core.data.AppSettings
import com.athar.app.core.data.DebtRepository
import com.athar.app.core.data.DebtsSession
import com.athar.app.core.data.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DebtsUiState(
    val balances: List<PersonBalance> = emptyList(),
    val totals: DebtTotals = DebtTotals.Empty,
    val entriesByPerson: Map<Long, List<DebtEntry>> = emptyMap(),
    val query: String = "",
    val sort: DebtSort = DebtSort.AmountDesc,
    val locked: Boolean = false,
    val loaded: Boolean = false,
) {
    val visible: List<PersonBalance>
        get() {
            val q = query.trim()
            val filtered = if (q.isEmpty()) {
                balances
            } else {
                balances.filter {
                    it.person.name.contains(q, ignoreCase = true) || it.person.phone.contains(q) ||
                        it.person.address.contains(q, ignoreCase = true)
                }
            }
            return DebtMath.sort(filtered, sort)
        }

    fun balanceOf(personId: Long): PersonBalance? = balances.firstOrNull { it.person.id == personId }
}

class DebtsViewModel(
    private val repository: DebtRepository,
    private val settings: AppSettings,
    private val session: DebtsSession,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(DebtSort.AmountDesc)
    private val filters = combine(query, sort) { q, s -> q to s }
    private val lockState = combine(settings.debtsLock, session.unlocked) { enabled, unlocked -> enabled && !unlocked }

    val uiState: StateFlow<DebtsUiState> =
        combine(repository.people, repository.entries, filters, lockState) { people, entries, (q, s), locked ->
            val balances = DebtMath.balances(people, entries)
            DebtsUiState(
                balances = balances,
                totals = DebtMath.totals(balances),
                entriesByPerson = entries.groupBy { it.personId },
                query = q,
                sort = s,
                locked = locked,
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsUiState(locked = settings.debtsLock.value))

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: DebtSort) {
        sort.value = value
    }

    fun unlock() = session.unlock()

    fun savePerson(person: Person, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.savePerson(person)
            onSaved(if (person.isNew) id else person.id)
        }
    }

    fun deletePerson(id: Long) {
        viewModelScope.launch { repository.deletePerson(id) }
    }

    fun saveEntry(entry: DebtEntry) {
        viewModelScope.launch { repository.saveEntry(entry) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                DebtsViewModel(c.debtRepository, c.settings, c.debtsSession)
            }
        }
    }
}
