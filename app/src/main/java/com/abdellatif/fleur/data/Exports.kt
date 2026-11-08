package com.abdellatif.fleur.data

object Exports {
    fun csv(state: BudgetState, range: DateRange): String {
        require(state.accounts.map { it.name }.distinct().size == state.accounts.size) {
            "Give each account a unique name before exporting"
        }
        val possibleRows =
            state.entries.sumOf {
                it.repeat.count(it.date, range.end) -
                    it.repeat.count(it.date, range.start.minusDays(1))
            } + state.overrides.size + state.accounts.size
        require(possibleRows <= 100_000) { "CSV export exceeds 100,000 records" }
        fun quote(text: String) = "\"" + text.replace("\"", "\"\"") + "\""
        val header =
            listOf(
                "Date",
                "Type",
                "Category",
                "Account",
                "To Account",
                "Amount",
                "Currency",
                "Paid",
                "Notes",
                "Repeating",
                "Account Type",
            )
        val rows =
            state.occurrences(range.start, range.end).map { o ->
                val e = o.entry
                listOf(
                    o.date.toString(),
                    e.type.name,
                    state.category(e.categoryId)?.name.orEmpty(),
                    state.account(e.accountId)?.name.orEmpty(),
                    state.account(e.toAccountId)?.name.orEmpty(),
                    moneyInput(if (e.type == EntryType.EXPENSE) -e.amount else e.amount),
                    state.preferences.currency,
                    e.paid.toString(),
                    e.notes,
                    e.repeat.frequency.name,
                    state.account(e.accountId)?.type?.name.orEmpty(),
                )
            }
        val opening =
            state.accounts.map { account ->
                listOf(
                    range.start.toString(),
                    "OPENING_BALANCE",
                    "",
                    account.name,
                    "",
                    moneyInput(state.accountBalance(account.id, range.start.minusDays(1))),
                    state.preferences.currency,
                    "true",
                    "",
                    "NONE",
                    account.type.name,
                )
            }
        // Keep spreadsheet programs from evaluating untrusted notes as formulas.
        fun safe(text: String) =
            if (
                text.firstOrNull() in listOf('=', '+', '@', '\t', '\r', '\n', '\'') ||
                    text.startsWith("-") && text.drop(1).toBigDecimalOrNull() == null
            )
                "'" + text
            else text
        return ("\uFEFF" +
                (listOf(header) + opening + rows).joinToString("\r\n") { row ->
                    row.mapIndexed { i, v -> quote(if (i == 5) v else safe(v)) }.joinToString(",")
                } +
                "\r\n")
            .also { require(it.length <= 25_000_000) { "CSV export is too large" } }
    }
}
