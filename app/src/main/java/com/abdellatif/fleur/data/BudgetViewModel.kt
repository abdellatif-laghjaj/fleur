package com.abdellatif.fleur.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BudgetViewModel
@JvmOverloads
constructor(application: Application, databaseName: String = "fleur-personal.db") :
    AndroidViewModel(application) {
    private val database = BudgetDatabase(application, databaseName)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<BudgetState?>(null)
    val state = mutableState.asStateFlow()
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors = mutableErrors.asSharedFlow()
    val loadError = MutableStateFlow<String?>(null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                mutableState.value = withContext(Dispatchers.IO) { database.load() }
                loadError.value = null
            } catch (e: Exception) {
                loadError.value = e.message ?: "Unable to open your budget"
            }
        }
    }

    fun change(onSuccess: () -> Unit = {}, transform: (BudgetState) -> BudgetState) {
        viewModelScope.launch {
            try {
                mutex.withLock {
                    val before = mutableState.value ?: error("Budget is still loading")
                    val next =
                        withContext(Dispatchers.Default) {
                            transform(before).also(StateCodec::validate)
                        }
                    withContext(Dispatchers.IO) { database.save(before, next) }
                    mutableState.value = next
                }
                onSuccess()
            } catch (e: Exception) {
                mutableErrors.emit(e.message ?: "Unable to save your changes")
            }
        }
    }

    fun preferences(transform: (Preferences) -> Preferences) = change {
        it.copy(preferences = transform(it.preferences))
    }

    fun saveEntry(
        entry: Entry,
        editing: Occurrence? = null,
        scope: SeriesScope = SeriesScope.ONLY,
        onSuccess: () -> Unit = {},
    ) = change(onSuccess) { BudgetActions.save(it, entry, editing, scope) }

    fun deleteEntry(occurrence: Occurrence, scope: SeriesScope, onSuccess: () -> Unit) =
        change(onSuccess) { BudgetActions.delete(it, occurrence, scope) }

    fun togglePaid(occurrence: Occurrence) = change { BudgetActions.togglePaid(it, occurrence) }

    fun saveAccount(account: Account, onSuccess: () -> Unit) =
        change(onSuccess) { s ->
            s.copy(accounts = s.accounts.filterNot { it.id == account.id } + account)
        }

    fun saveCategory(category: Category, onSuccess: () -> Unit) =
        change(onSuccess) { s ->
            require(
                s.categories.none {
                    it.id != category.id &&
                        it.type == category.type &&
                        it.name.equals(category.name, true)
                },
            ) {
                "This category already exists"
            }
            s.copy(categories = s.categories.filterNot { it.id == category.id } + category)
        }

    fun saveBudgets(amount: Long, budgets: List<Budget>, onSuccess: () -> Unit) =
        change(onSuccess) {
            it.copy(budgets = budgets, preferences = it.preferences.copy(overallBudget = amount))
        }

    override fun onCleared() {
        database.close()
    }
}
