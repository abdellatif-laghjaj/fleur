package com.abdellatif.fleur

import com.abdellatif.fleur.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BudgetLogicTest {
    @Test
    fun reorderingChangesOrderWithoutChangingIdentityOrAmounts() {
        val state =
            initialState()
                .copy(
                    accounts =
                        listOf(
                            Account("cash", "Cash", initial = 500, order = 0),
                            Account("saving", "Savings", initial = 1000, order = 1),
                            Account("debt", "Debt", initial = -400, order = 2),
                        ),
                )
        val moved = BudgetActions.reorderAccounts(state, "cash", "debt")
        assertEquals(
            listOf("saving", "debt", "cash"),
            moved.accounts.sortedBy { it.order }.map { it.id },
        )
        assertEquals(state.accounts.map { it.initial }, moved.accounts.map { it.initial })
        assertEquals(state.accounts.size, moved.accounts.map { it.id }.distinct().size)
        val categories = BudgetActions.reorderCategories(state, "category-1", "category-3")
        assertEquals("category-1", categories.categories.sortedBy { it.order }[3].id)
    }

    private val jan31 = LocalDate.of(2026, 1, 31)

    @Test
    fun moneyIsExactAndRejectsAmbiguousOrExcessPrecision() {
        assertEquals(30L, Math.addExact(parseMoney("0.10"), parseMoney("0.20")))
        assertEquals(12345L, parseMoney("123,45"))
        assertEquals(-500L, parseMoney("-5", true))
        for (text in listOf("1.001", "NaN", "1,000.25", "-1", "1e3", "999999999999999999999999")) {
            assertThrows(IllegalArgumentException::class.java) { parseMoney(text) }
        }
    }

    @Test
    fun monthlyRepeatClampsWithoutDriftingAndHonorsEndDate() {
        val rule = RepeatRule(Frequency.MONTHLY, end = LocalDate.of(2026, 3, 31))
        assertTrue(rule.occurs(jan31, LocalDate.of(2026, 2, 28)))
        assertFalse(rule.occurs(jan31, LocalDate.of(2026, 3, 28)))
        assertTrue(rule.occurs(jan31, LocalDate.of(2026, 3, 31)))
        assertFalse(rule.occurs(jan31, LocalDate.of(2026, 4, 30)))
        assertTrue(
            RepeatRule(Frequency.YEARLY)
                .occurs(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28)),
        )
    }

    @Test
    fun customWeeksUseCalendarWeeksAndSelectedDays() {
        val start = LocalDate.of(2026, 11, 2)
        val rule = RepeatRule(Frequency.WEEKLY, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertTrue(rule.occurs(start, start.plusDays(4)))
        assertFalse(rule.occurs(start, start.plusWeeks(1)))
        assertTrue(rule.occurs(start, start.plusWeeks(2)))
    }

    @Test
    fun transfersKeepNetWorthAndDebtRepaymentOutOfSpending() {
        val state =
            initialState()
                .copy(
                    accounts =
                        listOf(
                            Account("cash", "Cash", initial = 10000),
                            Account("debt", "Credit Card", AccountType.DEBT, initial = -5000),
                        ),
                    entries =
                        listOf(
                            Entry(
                                "transfer",
                                EntryType.TRANSFER,
                                2500,
                                accountId = "cash",
                                toAccountId = "debt",
                                date = jan31,
                            ),
                        ),
                )
        assertEquals(7500L, state.accountBalance("cash", jan31))
        assertEquals(-2500L, state.accountBalance("debt", jan31))
        assertEquals(5000L, state.accounts.sumOf { state.accountBalance(it.id, jan31) })
    }

    @Test
    fun singleRecurringOccurrenceCanMoveAndBeDeletedWithoutChangingSeries() {
        val e =
            Entry(
                "rent",
                EntryType.EXPENSE,
                10000,
                accountId = "default",
                categoryId = "category-3",
                date = jan31,
                repeat = RepeatRule(Frequency.MONTHLY),
            )
        val s = initialState().copy(entries = listOf(e))
        val feb = s.occurrences(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).single()
        val changed =
            BudgetActions.save(
                s,
                e.copy(amount = 12000, date = LocalDate.of(2026, 3, 2)),
                feb,
                SeriesScope.ONLY,
            )
        assertTrue(
            changed.occurrences(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).isEmpty(),
        )
        val march = changed.occurrences(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        assertEquals(2, march.size)
        val moved = march.first { it.entry.amount == 12000L }
        assertEquals(feb.key, moved.key)
        val deleted = BudgetActions.delete(changed, moved, SeriesScope.ONLY)
        assertEquals(
            1,
            deleted.occurrences(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).size,
        )
        assertEquals(
            1,
            deleted.occurrences(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)).size,
        )
    }

    @Test
    fun followingSeriesEditPreservesPast() {
        val e =
            Entry(
                "rent",
                EntryType.EXPENSE,
                10000,
                accountId = "default",
                date = jan31,
                repeat = RepeatRule(Frequency.MONTHLY),
            )
        val s = initialState().copy(entries = listOf(e))
        val feb = s.occurrences(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).single()
        val changed =
            BudgetActions.save(
                s,
                e.copy(amount = 12000, date = feb.date),
                feb,
                SeriesScope.FOLLOWING,
            )
        assertEquals(10000L, changed.occurrences(jan31, jan31).single().entry.amount)
        assertEquals(
            12000L,
            changed
                .occurrences(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))
                .single()
                .entry
                .amount,
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            changed.occurrences(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).single().date,
        )
    }

    @Test
    fun monthStartClampsAndRangesRemainContinuous() {
        val p = Preferences(monthStart = 31)
        assertEquals(
            DateRange(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 27)),
            periodRange(LocalDate.of(2026, 2, 15), Period.MONTH, p),
        )
        assertEquals(
            DateRange(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 30)),
            periodRange(LocalDate.of(2026, 3, 1), Period.MONTH, p),
        )
    }

    @Test
    fun optimizedRepeatCountsAndDatesMatchDayByDayRules() {
        val start = LocalDate.of(2024, 1, 31)
        val through = LocalDate.of(2027, 3, 20)
        for (frequency in Frequency.entries) for (interval in 1..3) for (lastDay in
            listOf(false, true)) for (anchor in listOf(null, 1, 15, 31)) {
            val rule =
                RepeatRule(
                    frequency,
                    interval,
                    setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    lastDay,
                    through.minusDays(12),
                    anchor,
                )
            val expected =
                generateSequence(start) { it.plusDays(1) }
                    .takeWhile { it <= through }
                    .filter { rule.occurs(start, it) }
                    .toList()
            assertEquals("$rule", expected.size.toLong(), rule.count(start, through))
            assertEquals("$rule", expected, rule.dates(start, start, through).toList())
            val from = start.plusDays(75)
            assertEquals(expected.filter { it >= from }, rule.dates(start, from, through).toList())
        }
    }

    @Test
    fun deletingAccountDoesNotResurrectAnOccurrenceMovedToThatAccount() {
        val entry =
            Entry(
                "rent",
                EntryType.EXPENSE,
                10000,
                accountId = "default",
                date = jan31,
                repeat = RepeatRule(Frequency.MONTHLY),
            )
        val state =
            initialState()
                .copy(
                    accounts = initialState().accounts + Account(id = "other", name = "Other"),
                    entries = listOf(entry),
                )
        val feb = state.occurrences(jan31.plusDays(1), jan31.plusMonths(1)).single()
        val moved =
            BudgetActions.save(
                state,
                entry.copy(accountId = "other", date = feb.date),
                feb,
                SeriesScope.ONLY,
            )
        val deleted = BudgetActions.deleteAccount(moved, "other")
        assertTrue(deleted.occurrences(jan31.plusDays(1), jan31.plusMonths(1)).isEmpty())
        assertEquals(
            10000L,
            deleted
                .occurrences(
                    jan31.plusMonths(1).plusDays(1),
                    jan31.plusMonths(2),
                )
                .single()
                .entry
                .amount,
        )
        assertTrue(deleted.overrides.containsKey(feb.key))
        assertNull(deleted.overrides[feb.key])
    }
}
