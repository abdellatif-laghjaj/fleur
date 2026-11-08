package com.abdellatif.fleur.data

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

object CsvImport {
    fun decode(text: String, preferences: Preferences): BudgetState {
        val table = parse(text)
        require(table.size > 1) { "CSV has no records" }
        val header = table.first().map { it.trim().lowercase(Locale.ROOT) }
        require(header.distinct().size == header.size) { "CSV contains duplicate column names" }
        val cashew = "category name" in header && "income" in header
        val required =
            if (cashew) listOf("account", "amount", "currency", "date", "income", "category name")
            else
                listOf(
                    "date",
                    "type",
                    "category",
                    "account",
                    "to account",
                    "amount",
                    "currency",
                    "paid",
                    "notes",
                )
        require(header.containsAll(required)) { "Choose a Fleur or Cashew transaction CSV" }
        val categories = initialState().categories.toMutableList()
        val accounts = linkedMapOf<String, Account>()
        val openings = mutableSetOf<String>()
        val entries = mutableListOf<Entry>()
        var currency: String? = null
        fun account(name: String): Account {
            require(name.isNotBlank() && name.length <= 100) { "Invalid account name" }
            return accounts.getOrPut(name) { Account(name = name, order = accounts.size) }
        }
        fun category(name: String, type: EntryType, color: String): Category {
            val mapped =
                when (name.lowercase(Locale.ROOT)) {
                    "food & drink",
                    "groceries",
                    "restaurant",
                    "coffee" -> "Food & Drinks"
                    "travel",
                    "taxi" -> "Transportation"
                    "internet" -> "Communication & PC"
                    else -> name.ifBlank { "Other" }
                }
            require(mapped.length <= 100) { "Category name is too long" }
            return categories.firstOrNull { it.type == type && it.name.equals(mapped, true) }
                ?: Category(
                        name = mapped,
                        type = type,
                        icon = if (type == EntryType.INCOME) "dollar" else "other",
                        color =
                            color.removePrefix("0x").removePrefix("0X").toLongOrNull(16)?.takeIf {
                                it in 0..0xffffffffL
                            } ?: 0xFF979EA3,
                        order = categories.size,
                    )
                    .also { categories += it }
        }
        table.drop(1).forEachIndexed { index, row ->
            try {
                require(row.size == header.size) { "Column count does not match the header" }
                fun value(key: String): String {
                    val value = header.indexOf(key).takeIf { it >= 0 }?.let { row[it] }.orEmpty()
                    return if (
                        !cashew &&
                            value.startsWith("'") &&
                            value.drop(1).firstOrNull() in
                                listOf('\'', '=', '+', '-', '@', '\t', '\r', '\n')
                    )
                        value.drop(1)
                    else value
                }
                val code = value("currency").trim().uppercase(Locale.ROOT)
                Currency.getInstance(code)
                require(currency == null || currency == code) {
                    "Mixed currencies cannot be imported into one Fleur budget"
                }
                currency = code
                val dateText = value("date").trim()
                val date =
                    LocalDate.parse(
                        if (cashew) dateText.substringBefore(' ').substringBefore('T')
                        else dateText,
                    )
                require(date.year in 1900..2200) { "Date is outside the supported range" }
                val source = account(value("account").trim())
                val signed = money(value("amount"))
                if (!cashew && value("type").uppercase(Locale.ROOT) == "OPENING_BALANCE") {
                    require(openings.add(source.id)) { "Duplicate opening balance" }
                    val type = value("account type").ifBlank { "SPENDING" }
                    accounts[source.name] =
                        source.copy(initial = signed, type = AccountType.valueOf(type))
                } else {
                    require(!cashew || value("type").let { it.isBlank() || it == "default" }) {
                        "Unsupported Cashew transaction type"
                    }
                    val unpaid =
                        if (cashew && value("amount unpaid").isNotBlank())
                            money(value("amount unpaid"))
                        else 0L
                    require(signed == 0L || unpaid == 0L) {
                        "Partially paid transactions need separate paid and unpaid rows"
                    }
                    val amount = if (signed == 0L) unpaid else signed
                    require(amount != 0L) { "Transaction amount must not be zero" }
                    val type =
                        if (cashew) {
                            val income = bool(value("income"))
                            require((amount > 0) == income) {
                                "Amount sign disagrees with income flag"
                            }
                            if (income) EntryType.INCOME else EntryType.EXPENSE
                        } else EntryType.valueOf(value("type").uppercase(Locale.ROOT))
                    require(type != EntryType.EXPENSE || amount < 0) {
                        "Expenses must have negative amounts"
                    }
                    require(type != EntryType.INCOME || amount > 0) {
                        "Income must have positive amounts"
                    }
                    require(type != EntryType.TRANSFER || amount > 0) {
                        "Transfer amounts must be positive"
                    }
                    val details =
                        if (cashew)
                            listOf(
                                value("title"),
                                value("note"),
                                value("subcategory name")
                                    .takeIf { it.isNotBlank() }
                                    ?.let { "Subcategory: $it" }
                                    .orEmpty(),
                                value("extra")
                                    .takeIf { it.isNotBlank() }
                                    ?.let { "Cashew: $it" }
                                    .orEmpty(),
                            )
                        else
                            listOf(
                                value("notes"),
                                value("repeating")
                                    .takeIf { it.isNotBlank() && it != "NONE" }
                                    ?.let { "Originally repeating: $it" }
                                    .orEmpty(),
                            )
                    entries +=
                        Entry(
                            type = type,
                            amount = abs(amount),
                            accountId = source.id,
                            toAccountId =
                                if (type == EntryType.TRANSFER)
                                    account(value("to account").trim()).id
                                else "",
                            categoryId =
                                if (type == EntryType.TRANSFER) ""
                                else
                                    category(
                                            value(if (cashew) "category name" else "category")
                                                .trim(),
                                            type,
                                            value("color"),
                                        )
                                        .id,
                            date = date,
                            paid = if (cashew) unpaid == 0L else bool(value("paid")),
                            notes = details.filter { it.isNotBlank() }.joinToString("\n"),
                        )
                }
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "CSV row ${index + 2}: ${e.message ?: "Invalid record"}",
                    e,
                )
            }
        }
        require(accounts.isNotEmpty()) { "CSV has no accounts" }
        return BudgetState(
                accounts.values.toList(),
                categories,
                entries,
                preferences =
                    preferences.copy(
                        currency = currency!!,
                        defaultAccountId = accounts.values.first().id,
                        overallBudget = 0,
                    ),
            )
            .also(StateCodec::validate)
    }

    private fun money(text: String): Long {
        require(text.trim().matches(Regex("-?\\d{1,12}(\\.\\d+)?"))) { "Invalid money amount" }
        return BigDecimal(text.trim()).movePointRight(2).longValueExact().also {
            require(it in -99_999_999_999_999L..99_999_999_999_999L) { "Money amount is too large" }
        }
    }

    private fun bool(text: String): Boolean =
        when (text.trim().lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> error("Expected true or false")
        }

    // CSV fields can contain quoted commas, doubled quotes and physical line breaks.
    internal fun parse(text: String): List<List<String>> {
        require(text.length <= 25_000_000) { "CSV is too large" }
        val input = text.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        fun finishField() {
            require(row.size < 100) { "CSV contains too many columns" }
            row += field.toString()
            field.setLength(0)
            closed = false
        }
        fun finishRow() {
            finishField()
            if (row.any { it.isNotBlank() }) rows += row.toList()
            require(rows.size <= 100_001) { "CSV contains too many records" }
            row.clear()
        }
        while (i < input.length) {
            val c = input[i]
            when {
                quoted && c == '"' -> {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                        closed = true
                    }
                }
                quoted -> field.append(c)
                c == ',' -> finishField()
                c == '\r' || c == '\n' -> {
                    finishRow()
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                }
                c == '"' -> {
                    require(field.isEmpty() && !closed) { "Invalid CSV quote" }
                    quoted = true
                }
                else -> {
                    require(!closed) { "Unexpected text after a quoted field" }
                    field.append(c)
                }
            }
            require(field.length <= 20_000) { "CSV field is too long" }
            i++
        }
        require(!quoted) { "CSV has an unclosed quote" }
        if (field.isNotEmpty() || row.isNotEmpty() || closed) finishRow()
        return rows
    }
}
