package com.abdellatif.fleur.data

enum class SeriesScope {
    ONLY,
    FOLLOWING,
}

object BudgetActions {
    private fun reorder(ids: List<String>, from: String, to: String): Map<String, Int> {
        val list = ids.toMutableList()
        val target = list.indexOf(to)
        if (target >= 0 && from in list) {
            list.remove(from)
            list.add(target, from)
        }
        return list.mapIndexed { index, id -> id to index }.toMap()
    }

    fun reorderAccounts(state: BudgetState, from: String, to: String): BudgetState {
        val order = reorder(state.accounts.sortedBy { it.order }.map { it.id }, from, to)
        return state.copy(accounts = state.accounts.map { it.copy(order = order.getValue(it.id)) })
    }

    fun reorderCategories(state: BudgetState, from: String, to: String): BudgetState {
        val order = reorder(state.categories.sortedBy { it.order }.map { it.id }, from, to)
        return state.copy(
            categories = state.categories.map { it.copy(order = order.getValue(it.id)) },
        )
    }

    fun save(
        state: BudgetState,
        entry: Entry,
        editing: Occurrence?,
        scope: SeriesScope,
    ): BudgetState {
        if (editing == null) return state.copy(entries = state.entries + entry)
        val original =
            state.entries.find { it.id == editing.seriesId }
                ?: error("Transaction no longer exists")
        if (!original.repeat.repeats)
            return state.copy(
                entries =
                    state.entries.map {
                        if (it.id == original.id) entry.copy(id = original.id) else it
                    },
            )
        if (scope == SeriesScope.ONLY)
            return state.copy(
                overrides =
                    state.overrides +
                        (editing.key to entry.copy(id = original.id, repeat = RepeatRule())),
            )
        val truncated = delete(state, editing, SeriesScope.FOLLOWING)
        val anchor =
            if (entry.date == editing.date && entry.repeat.frequency == original.repeat.frequency)
                original.repeat.anchorDay ?: original.date.dayOfMonth
            else entry.date.dayOfMonth
        return truncated.copy(
            entries =
                truncated.entries +
                    entry.copy(id = newId(), repeat = entry.repeat.copy(anchorDay = anchor)),
        )
    }

    fun delete(state: BudgetState, occurrence: Occurrence, scope: SeriesScope): BudgetState {
        val original = state.entries.find { it.id == occurrence.seriesId } ?: return state
        if (
            !original.repeat.repeats ||
                scope == SeriesScope.FOLLOWING && occurrence.sourceDate <= original.date
        ) {
            return state.copy(
                entries = state.entries.filterNot { it.id == original.id },
                overrides = state.overrides.filterKeys { !it.startsWith("${original.id}@") },
            )
        }
        if (scope == SeriesScope.ONLY)
            return state.copy(overrides = state.overrides + (occurrence.key to null))
        return state.copy(
            entries =
                state.entries.map {
                    if (it.id == original.id)
                        it.copy(repeat = it.repeat.copy(end = occurrence.sourceDate.minusDays(1)))
                    else it
                },
            overrides =
                state.overrides.filterKeys {
                    !it.startsWith("${original.id}@") ||
                        it.substringAfterLast('@').toLong() < occurrence.sourceDate.toEpochDay()
                },
        )
    }

    fun togglePaid(state: BudgetState, occurrence: Occurrence): BudgetState =
        save(
            state,
            occurrence.entry.copy(date = occurrence.date, paid = !occurrence.entry.paid),
            occurrence,
            SeriesScope.ONLY,
        )

    fun deleteAccount(state: BudgetState, id: String): BudgetState {
        require(state.accounts.size > 1) { "Keep at least one account" }
        val deletedEntries =
            state.entries
                .filter { it.accountId == id || it.toAccountId == id }
                .map { it.id }
                .toSet()
        val accounts = state.accounts.filterNot { it.id == id }
        return state.copy(
            accounts = accounts,
            entries = state.entries.filterNot { it.id in deletedEntries },
            overrides =
                state.overrides
                    .filterKeys { it.substringBeforeLast('@') !in deletedEntries }
                    .mapValues { (_, entry) ->
                        entry?.takeUnless { it.accountId == id || it.toAccountId == id }
                    },
            preferences =
                state.preferences.let {
                    if (it.defaultAccountId == id) it.copy(defaultAccountId = accounts.first().id)
                    else it
                },
        )
    }

    fun deleteCategory(state: BudgetState, id: String): BudgetState {
        require(
            state.entries.none { it.categoryId == id } &&
                state.overrides.values.none { it?.categoryId == id },
        ) {
            "This category has transactions. Move them to another category before deleting it."
        }
        return state.copy(
            categories = state.categories.filterNot { it.id == id },
            budgets = state.budgets.filterNot { it.categoryId == id },
        )
    }
}
