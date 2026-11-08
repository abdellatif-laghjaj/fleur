package com.abdellatif.fleur

import com.abdellatif.fleur.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CsvImportTest {
    private val header =
        "account,amount,amount unpaid,currency,title,note,date,income,type,category name,subcategory name,color,icon,emoji,budget,objective,extra"
    private val cashew =
        header +
            "\r\n" +
            "Main,1000.5,,MAD,,\"Balance, adjusted\nMain: 50.25\",2026-11-07 17:33:31.000,true,default,Balance Correction,,0XFF607D8B,charts,,,,\r\n" +
            "Main,-950.25,,MAD,Market,,2026-11-05 17:29:38.000,false,default,Food & Drink,,0XFFEF5350,cutlery,,,,repeat every 1 month\r\n"

    @Test
    fun cashewImportsExactMoneyMultilineNotesAndDoesNotInventRepeats() {
        val state = CsvImport.decode(cashew, Preferences(darkMode = "Dark", overallBudget = 99000))
        assertEquals(2, state.entries.size)
        assertEquals("MAD", state.preferences.currency)
        assertEquals("Dark", state.preferences.darkMode)
        assertEquals(0L, state.preferences.overallBudget)
        assertEquals(
            5025L,
            state.accountBalance(state.accounts.single().id, LocalDate.of(2026, 11, 7)),
        )
        assertTrue(state.entries.all { !it.repeat.repeats })
        assertTrue(state.entries.first().notes.contains("Balance, adjusted\nMain: 50.25"))
        val expense = state.entries.last()
        assertEquals("Food & Drinks", state.category(expense.categoryId)!!.name)
        assertTrue(expense.notes.contains("repeat every 1 month"))
    }

    @Test
    fun fleurCsvRoundTripPreservesOpeningBalancesTransfersUnpaidAndEscapedNotes() {
        val day = LocalDate.of(2026, 11, 7)
        val seed = initialState()
        val notes = "=SUM(A1)\n\"Cake\", tea"
        val state =
            seed.copy(
                accounts =
                    listOf(
                        Account("a", "=Cash", initial = 1234),
                        Account("b", "Savings", type = AccountType.SAVINGS, initial = -200),
                    ),
                entries =
                    listOf(
                        Entry(
                            type = EntryType.EXPENSE,
                            amount = 111,
                            accountId = "a",
                            categoryId = "category-1",
                            date = day,
                            notes = notes,
                        ),
                        Entry(
                            type = EntryType.TRANSFER,
                            amount = 99,
                            accountId = "a",
                            toAccountId = "b",
                            date = day,
                        ),
                        Entry(
                            type = EntryType.INCOME,
                            amount = 900,
                            accountId = "a",
                            categoryId = "category-0",
                            date = day,
                            paid = false,
                            notes = "'=literal",
                        ),
                    ),
                preferences = seed.preferences.copy(defaultAccountId = "a"),
            )
        val csv = Exports.csv(state, DateRange(day, day))
        assertTrue(csv.contains("'="))
        val imported = CsvImport.decode(csv, Preferences())
        for (account in state.accounts) {
            val actual = imported.accounts.single { it.name == account.name }
            assertEquals(account.type, actual.type)
            assertEquals(
                state.accountBalance(account.id, day),
                imported.accountBalance(actual.id, day),
            )
        }
        assertEquals(notes, imported.entries.first { it.type == EntryType.EXPENSE }.notes)
        assertEquals("'=literal", imported.entries.first { it.type == EntryType.INCOME }.notes)
        assertFalse(imported.entries.first { it.type == EntryType.INCOME }.paid)
    }

    @Test
    fun malformedMoneyCurrencyQuotesAndPartialPaymentsAreRejected() {
        val invalid =
            listOf(
                cashew.replace("-950.25", "-950.251"),
                cashew.replace("Main,-950.25,,MAD", "Main,-950.25,,EUR"),
                cashew.replace("Main,-950.25,,MAD", "Main,-950.25,-10,MAD"),
                cashew.replace("2026-11-05", "2026-02-30"),
                cashew.replace("false,default", "true,default"),
                cashew + "Main,\"unclosed",
                "bad,header\na,b",
            )
        invalid.forEach { text ->
            try {
                CsvImport.decode(text, Preferences())
                fail("Invalid CSV accepted")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test
    fun csvParserHandlesEscapedQuotesEmptyFieldsBomAndLineEndings() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("comma, quote \"ok\"", "line1\r\nline2", "")),
            CsvImport.parse("\uFEFFa,b,c\r\n\"comma, quote \"\"ok\"\"\",\"line1\r\nline2\",\r\n"),
        )
    }
}
