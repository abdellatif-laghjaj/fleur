package com.abdellatif.fleur.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SpendingBin(val start: LocalDate, val end: LocalDate, val amount: Long)

// Long ranges combine adjacent days; scale against the combined values, not individual days.
fun spendingBins(rows: List<Occurrence>, range: DateRange): List<SpendingBin> {
    val days = ChronoUnit.DAYS.between(range.start, range.end) + 1
    val count = minOf(days, 60).toInt()
    val groups =
        rows
            .filter { it.date in range.start..range.end }
            .groupBy { it.date }
            .mapValues { (_, values) -> values.sumOf { it.entry.amount } }
    return List(count) { index ->
        val start = range.start.plusDays(index * days / count)
        val end = range.start.plusDays((index + 1) * days / count - 1)
        SpendingBin(start, end, groups.filterKeys { it in start..end }.values.sum())
    }
}

data class BudgetForecast(
    val spent: Long,
    val expected: Long,
    val elapsedDays: Long,
    val totalDays: Long,
    val observedThrough: LocalDate?,
)

fun budgetForecast(rows: List<Occurrence>, range: DateRange, today: LocalDate): BudgetForecast {
    val days = ChronoUnit.DAYS.between(range.start, range.end) + 1
    val elapsed = (ChronoUnit.DAYS.between(range.start, today) + 1).coerceIn(0, days)
    val inRange = rows.filter {
        it.date in range.start..range.end && it.entry.type == EntryType.EXPENSE
    }
    val spent = inRange.filter { it.date <= today }.sumOf { it.entry.amount }
    val scheduled = inRange.sumOf { it.entry.amount }
    val pace =
        if (elapsed == 0L) 0L
        else
            BigDecimal.valueOf(spent)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(elapsed), 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(Long.MAX_VALUE))
                .longValueExact()
    return BudgetForecast(
        spent,
        maxOf(pace, scheduled),
        elapsed,
        days,
        if (elapsed == 0L) null else minOf(today, range.end),
    )
}
