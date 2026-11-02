package com.abdellatif.fleur.data

fun updateDefaultCategories(state: BudgetState): BudgetState {
    var categories = state.categories
    val replacements = mutableMapOf<String, String>()
    fun merge(names: Set<String>, name: String, icon: String) {
        val members = categories.filter {
            it.type == EntryType.EXPENSE && (it.name in names || it.name.equals(name, true))
        }
        if (members.isEmpty()) return
        val target =
            members.firstOrNull { it.name.equals(name, true) } ?: members.minBy { it.order }
        members.forEach { replacements[it.id] = target.id }
        categories = categories.mapNotNull { category ->
            when {
                category.id == target.id ->
                    category.copy(
                        name = if (target.name.equals(name, true)) target.name else name,
                        icon = if (target.name.equals(name, true)) target.icon else icon,
                    )
                category.id in replacements && replacements[category.id] != category.id -> null
                else -> category
            }
        }
    }
    merge(setOf("Groceries", "Restaurant", "Coffee"), "Food & Drinks", "restaurant")
    merge(setOf("Travel", "Taxi"), "Transportation", "bus")
    merge(setOf("Internet"), "Communication & PC", "wifi")
    categories = categories.map {
        if (it.type == EntryType.EXPENSE && it.name == "Entertainment") it.copy(icon = "movie")
        else it
    }
    val additions = setOf("shopping", "health-care", "charity", "family-support")
    for (category in initialState().categories.filter { it.id in additions }) {
        if (categories.none { it.type == category.type && it.name.equals(category.name, true) }) {
            categories =
                categories +
                    category.copy(
                        id = if (categories.any { it.id == category.id }) newId() else category.id,
                        order = (categories.maxOfOrNull { it.order } ?: -1) + 1,
                    )
        }
    }
    fun remap(entry: Entry) =
        entry.copy(categoryId = replacements[entry.categoryId] ?: entry.categoryId)
    return state.copy(
        categories = categories,
        entries = state.entries.map(::remap),
        overrides = state.overrides.mapValues { (_, entry) -> entry?.let(::remap) },
        budgets =
            state.budgets
                .groupBy { replacements[it.categoryId] ?: it.categoryId }
                .map { (id, budgets) ->
                    Budget(
                        id,
                        budgets.fold(0L) { total, budget -> Math.addExact(total, budget.amount) },
                    )
                },
    )
}
