package com.abdellatif.fleur.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Currency
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

enum class EntryType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

enum class AccountType {
    SPENDING,
    SAVINGS,
    DEBT,
}

enum class Frequency {
    NONE,
    DAILY,
    WEEKLY,
    FORTNIGHTLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
}

enum class Period {
    DAY,
    WEEK,
    FORTNIGHT,
    MONTH,
    YEAR,
    CUSTOM,
}

data class Account(
    val id: String = newId(),
    val name: String,
    val type: AccountType = AccountType.SPENDING,
    val initial: Long = 0,
    val goal: Long = 0,
    val icon: String = "wallet",
    val color: Long = 0xFF979EA3,
    val hidden: Boolean = false,
    val includeInBalance: Boolean = true,
    val order: Int = 0,
)

data class Category(
    val id: String = newId(),
    val name: String,
    val type: EntryType = EntryType.EXPENSE,
    val icon: String = "grocery",
    val color: Long = 0xFFF3BE7D,
    val order: Int = 0,
)

data class RepeatRule(
    val frequency: Frequency = Frequency.NONE,
    val interval: Int = 1,
    val weekDays: Set<DayOfWeek> = emptySet(),
    val lastDay: Boolean = false,
    val end: LocalDate? = null,
    val anchorDay: Int? = null,
) {
    val repeats
        get() = frequency != Frequency.NONE

    fun count(start: LocalDate, through: LocalDate): Long {
        val last = minOf(through, end ?: through)
        if (last < start) return 0
        val every = interval.coerceAtLeast(1).toLong()
        return when (frequency) {
            Frequency.NONE -> 1
            Frequency.DAILY -> ChronoUnit.DAYS.between(start, last) / every + 1
            Frequency.WEEKLY,
            Frequency.FORTNIGHTLY -> {
                val monday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val step = every * if (frequency == Frequency.FORTNIGHTLY) 14 else 7
                weekDays
                    .ifEmpty { setOf(start.dayOfWeek) }
                    .sumOf { weekday ->
                        var first = monday.plusDays(weekday.value - 1L)
                        if (first < start) first = first.plusDays(step)
                        if (first > last) 0L else ChronoUnit.DAYS.between(first, last) / step + 1
                    }
            }
            else -> {
                val step =
                    every *
                        when (frequency) {
                            Frequency.YEARLY -> 12
                            Frequency.QUARTERLY -> 3
                            else -> 1
                        }
                val cycles =
                    ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(last)) / step
                val month = YearMonth.from(start).plusMonths(cycles * step)
                val due =
                    month.atDay(
                        if (lastDay) month.lengthOfMonth()
                        else (anchorDay ?: start.dayOfMonth).coerceAtMost(month.lengthOfMonth()),
                    )
                val firstMonth = YearMonth.from(start)
                val firstDue =
                    firstMonth.atDay(
                        if (lastDay) firstMonth.lengthOfMonth()
                        else
                            (anchorDay ?: start.dayOfMonth).coerceAtMost(
                                firstMonth.lengthOfMonth(),
                            ),
                    )
                cycles + (if (due <= last) 1 else 0) - (if (firstDue < start) 1 else 0)
            }
        }
    }

    fun dates(start: LocalDate, from: LocalDate, through: LocalDate): Sequence<LocalDate> =
        sequence {
            val first = maxOf(start, from)
            val last = minOf(through, end ?: through)
            if (last < first) return@sequence
            val every = interval.coerceAtLeast(1).toLong()
            when (frequency) {
                Frequency.NONE -> if (start in first..last) yield(start)
                Frequency.DAILY -> {
                    val elapsed = ChronoUnit.DAYS.between(start, first)
                    var due = start.plusDays(((elapsed + every - 1) / every) * every)
                    while (due <= last) {
                        yield(due)
                        due = due.plusDays(every)
                    }
                }
                Frequency.WEEKLY,
                Frequency.FORTNIGHTLY -> {
                    val monday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val step = every * if (frequency == Frequency.FORTNIGHTLY) 2 else 1
                    var week =
                        monday.plusWeeks(ChronoUnit.WEEKS.between(monday, first) / step * step)
                    while (week <= last) {
                        for (day in
                            weekDays.ifEmpty { setOf(start.dayOfWeek) }.sortedBy { it.value }) {
                            val due = week.plusDays(day.value - 1L)
                            if (due in first..last) yield(due)
                        }
                        week = week.plusWeeks(step)
                    }
                }
                else -> {
                    val step =
                        every *
                            when (frequency) {
                                Frequency.YEARLY -> 12
                                Frequency.QUARTERLY -> 3
                                else -> 1
                            }
                    val month = YearMonth.from(start)
                    var current =
                        month.plusMonths(
                            ChronoUnit.MONTHS.between(month, YearMonth.from(first)) / step * step,
                        )
                    while (current <= YearMonth.from(last)) {
                        val due =
                            current.atDay(
                                if (lastDay) current.lengthOfMonth()
                                else
                                    (anchorDay ?: start.dayOfMonth).coerceAtMost(
                                        current.lengthOfMonth(),
                                    ),
                            )
                        if (due in first..last) yield(due)
                        current = current.plusMonths(step)
                    }
                }
            }
        }

    fun occurs(start: LocalDate, day: LocalDate): Boolean {
        if (day < start || (end != null && day > end)) return false
        val every = interval.coerceAtLeast(1).toLong()
        return when (frequency) {
            Frequency.NONE -> day == start
            Frequency.DAILY -> ChronoUnit.DAYS.between(start, day) % every == 0L
            Frequency.WEEKLY,
            Frequency.FORTNIGHTLY -> {
                val firstWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val week = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = every * if (frequency == Frequency.FORTNIGHTLY) 2 else 1
                ChronoUnit.WEEKS.between(firstWeek, week) % weeks == 0L &&
                    day.dayOfWeek in weekDays.ifEmpty { setOf(start.dayOfWeek) }
            }
            Frequency.MONTHLY,
            Frequency.QUARTERLY,
            Frequency.YEARLY -> {
                val months =
                    every *
                        when (frequency) {
                            Frequency.QUARTERLY -> 3
                            Frequency.YEARLY -> 12
                            else -> 1
                        }
                ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(day)) % months ==
                    0L &&
                    day.dayOfMonth ==
                        if (lastDay) day.lengthOfMonth()
                        else (anchorDay ?: start.dayOfMonth).coerceAtMost(day.lengthOfMonth())
            }
        }
    }
}

data class Entry(
    val id: String = newId(),
    val type: EntryType,
    val amount: Long,
    val categoryId: String = "",
    val accountId: String,
    val toAccountId: String = "",
    val date: LocalDate = LocalDate.now(),
    val paid: Boolean = true,
    val notes: String = "",
    val repeat: RepeatRule = RepeatRule(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class Occurrence(
    val entry: Entry,
    val date: LocalDate,
    val seriesId: String = entry.id,
    val sourceDate: LocalDate = date,
) {
    val key
        get() = "$seriesId@${sourceDate.toEpochDay()}"
}

data class Budget(val categoryId: String, val amount: Long)

data class Preferences(
    val name: String = "",
    val currency: String = "USD",
    val darkMode: String = "System",
    val accent: Long = 0xFFDF84A6,
    val font: String = "Almondita",
    val fontSize: Int = 54,
    val defaultAccountId: String = "default",
    val monthStart: Int = 1,
    val weekStart: Int = 7,
    val period: Period = Period.MONTH,
    val carryOver: Boolean = false,
    val decimals: Boolean = true,
    val showPaid: Boolean = true,
    val haptics: Boolean = true,
    val showBudget: Boolean = true,
    val showDebt: Boolean = true,
    val overallBudget: Long = 0,
    val budgetRemaining: Boolean = false,
    val reminders: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val appLock: Boolean = false,
    val defaultPaid: Boolean = true,
)

data class BudgetState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val overrides: Map<String, Entry?> = emptyMap(),
    val budgets: List<Budget> = emptyList(),
    val preferences: Preferences = Preferences(),
) {
    fun category(id: String) = categories.find { it.id == id }

    fun account(id: String) = accounts.find { it.id == id }

    val visibleAccounts
        get() = accounts.filterNot { it.hidden }.sortedBy { it.order }

    fun occurrences(start: LocalDate, end: LocalDate): List<Occurrence> {
        require(end >= start) { "End date must not be before start date" }
        val result = mutableListOf<Occurrence>()
        for (entry in entries) {
            if (!entry.repeat.repeats) {
                if (entry.date in start..end) result += Occurrence(entry, entry.date)
                continue
            }
            for (day in entry.repeat.dates(entry.date, start, end)) {
                val key = "${entry.id}@${day.toEpochDay()}"
                if (!overrides.containsKey(key)) result += Occurrence(entry, day)
                else
                    overrides[key]?.let {
                        if (it.date in start..end) result += Occurrence(it, it.date, entry.id, day)
                    }
            }
        }
        // An edited occurrence can move outside the original occurrence's date range.
        for ((key, entry) in overrides) {
            if (entry == null || entry.date !in start..end) continue
            val originalDay = LocalDate.ofEpochDay(key.substringAfterLast('@').toLong())
            if (originalDay !in start..end)
                result += Occurrence(entry, entry.date, key.substringBeforeLast('@'), originalDay)
        }
        return result.sortedWith(
            compareByDescending<Occurrence> { it.date }.thenByDescending { it.entry.createdAt },
        )
    }

    fun accountBalance(id: String, through: LocalDate = LocalDate.now()): Long {
        val account = account(id) ?: return 0
        var balance = account.initial
        for (entry in entries) if (entry.paid) {
            balance =
                Math.addExact(
                    balance,
                    Math.multiplyExact(entry.effectOn(id), entry.repeat.count(entry.date, through)),
                )
        }
        val parents = entries.associateBy { it.id }
        for ((key, replacement) in overrides) {
            val original = parents[key.substringBeforeLast('@')] ?: continue
            val originalDate = LocalDate.ofEpochDay(key.substringAfterLast('@').toLong())
            if (
                originalDate <= through &&
                    original.paid &&
                    original.repeat.occurs(original.date, originalDate)
            )
                balance = Math.subtractExact(balance, original.effectOn(id))
            if (replacement != null && replacement.paid && replacement.date <= through)
                balance = Math.addExact(balance, replacement.effectOn(id))
        }
        return balance
    }
}

fun Entry.effectOn(account: String): Long =
    when (type) {
        EntryType.EXPENSE -> if (accountId == account) -amount else 0
        EntryType.INCOME -> if (accountId == account) amount else 0
        EntryType.TRANSFER ->
            when (account) {
                accountId -> -amount
                toAccountId -> amount
                else -> 0
            }
    }

fun parseMoney(text: String, allowNegative: Boolean = false): Long {
    val cleaned = text.trim().replace(",", ".")
    require(
        cleaned.matches(
            Regex(if (allowNegative) "-?\\d{1,12}(\\.\\d{1,2})?" else "\\d{1,12}(\\.\\d{1,2})?"),
        ),
    ) {
        "Enter a valid amount with up to two decimal places"
    }
    return BigDecimal(cleaned).movePointRight(2).longValueExact()
}

fun moneyInput(amount: Long): String =
    BigDecimal.valueOf(amount, 2).stripTrailingZeros().toPlainString()

fun formatMoney(amount: Long, preferences: Preferences, signed: Boolean = false): String {
    val currency = runCatching {
        Currency.getInstance(preferences.currency)
    }
        .getOrDefault(Currency.getInstance("USD"))
    val formatter = java.text.NumberFormat.getCurrencyInstance(Locale.US)
    formatter.currency = currency
    val digits = if (preferences.decimals) currency.defaultFractionDigits.coerceIn(0, 2) else 0
    formatter.minimumFractionDigits = digits
    formatter.maximumFractionDigits = digits
    formatter.roundingMode = RoundingMode.HALF_UP
    return (if (signed && amount > 0) "+" else "") + formatter.format(BigDecimal.valueOf(amount, 2))
}

data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(end >= start)
    }
}

fun periodRange(anchor: LocalDate, period: Period, preferences: Preferences): DateRange =
    when (period) {
        Period.DAY -> DateRange(anchor, anchor)
        Period.WEEK,
        Period.FORTNIGHT -> {
            val first =
                anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(preferences.weekStart)))
            DateRange(first, first.plusDays(if (period == Period.WEEK) 6 else 13))
        }
        Period.MONTH -> {
            fun beginning(month: YearMonth) =
                month.atDay(preferences.monthStart.coerceIn(1, month.lengthOfMonth()))
            val month = YearMonth.from(anchor)
            val current = beginning(month)
            val first = if (anchor < current) beginning(month.minusMonths(1)) else current
            DateRange(first, beginning(YearMonth.from(first).plusMonths(1)).minusDays(1))
        }
        Period.YEAR -> DateRange(anchor.withDayOfYear(1), anchor.withMonth(12).withDayOfMonth(31))
        Period.CUSTOM ->
            DateRange(anchor.withDayOfMonth(1), anchor.withDayOfMonth(anchor.lengthOfMonth()))
    }

fun initialState(): BudgetState {
    val colors =
        listOf(
            0xFFB992DC,
            0xFFF2BC78,
            0xFF969CA4,
            0xFFDD86AC,
            0xFF71CA9B,
            0xFFB8A0DD,
            0xFFE9B66F,
            0xFFF28B88,
            0xFFB4A084,
            0xFFF17B7E,
            0xFFB1A080,
            0xFFC39DE2,
            0xFFB7A181,
            0xFFF17E7F,
            0xFFE8B268,
        )
    val names =
        listOf(
            "Salary",
            "Groceries",
            "Gas",
            "Rent",
            "Gym",
            "Restaurant",
            "Vacation",
            "Travel",
            "Gift",
            "Investments",
            "Savings",
            "Entertainment",
            "Coffee",
            "Internet",
            "Taxi",
        )
    val icons =
        listOf(
            "dollar",
            "grocery",
            "gas",
            "home",
            "fitness",
            "restaurant",
            "vacation",
            "bus",
            "gift",
            "percent",
            "savings",
            "phone",
            "coffee",
            "wifi",
            "taxi",
        )
    val categories =
        names.mapIndexed { i, name ->
            Category(
                "category-$i",
                name,
                if (i == 0) EntryType.INCOME else EntryType.EXPENSE,
                icons[i],
                colors[i],
                i,
            )
        } +
            listOf(
                Category("parents", "Parents", EntryType.INCOME, "heart", 0xFFE684AC, 15),
                Category("bonus", "Bonus", EntryType.INCOME, "gift", 0xFF85B9D9, 16),
                Category("other-income", "Other", EntryType.INCOME, "dollar", 0xFFA1B9A1, 17),
                Category("other-expense", "Other", EntryType.EXPENSE, "other", 0xFFA1B9A1, 18),
            )
    return BudgetState(accounts = listOf(Account("default", "Cash")), categories = categories)
}
