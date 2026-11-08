package com.abdellatif.fleur

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdellatif.fleur.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvPersistenceTest {
    @Test
    fun freshCsvReplacesRatherThanAppendsAndPersistsAfterReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "csv-import-${newId()}.db"
        try {
            val seed = BudgetDatabase(context, name).use { it.load() }
            val previous =
                seed.copy(
                    entries =
                        listOf(
                            Entry(
                                type = EntryType.EXPENSE,
                                amount = 777,
                                accountId = "default",
                                categoryId = "category-1",
                            ),
                        ),
                    budgets = listOf(Budget("category-1", 10000)),
                )
            val text =
                "account,amount,currency,date,income,category name\nMain,50.25,MAD,2026-11-01,true,Salary\nMain,-10.01,MAD,2026-11-02,false,Food & Drink\n"
            val candidate = CsvImport.decode(text, previous.preferences)
            BudgetDatabase(context, name).use {
                it.save(seed, previous)
                it.save(previous, candidate)
            }
            val loaded = BudgetDatabase(context, name).use { it.load() }
            assertEquals(candidate, loaded)
            assertEquals(2, loaded.entries.size)
            assertTrue(loaded.budgets.isEmpty())
            assertEquals(
                4024L,
                loaded.accountBalance(loaded.accounts.single().id, LocalDate.of(2026, 11, 7)),
            )
            val again = CsvImport.decode(text, loaded.preferences)
            BudgetDatabase(context, name).use { it.save(loaded, again) }
            assertEquals(2, BudgetDatabase(context, name).use { it.load() }.entries.size)
            try {
                CsvImport.decode(text.replace("-10.01", "-10.001"), loaded.preferences)
                fail("Invalid amount accepted")
            } catch (_: IllegalArgumentException) {}
            assertEquals(again, BudgetDatabase(context, name).use { it.load() })
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun suppliedCsvReconcilesWhenAFileIsProvided() {
        val args = InstrumentationRegistry.getArguments()
        val path = args.getString("csvPath") ?: return
        val expected = args.getString("csvExpectedBalance")!!.toLong()
        val count = args.getString("csvExpectedRows")!!.toInt()
        val candidate = CsvImport.decode(java.io.File(path).readText(), Preferences())
        assertEquals(count, candidate.entries.size)
        assertEquals(
            expected,
            candidate.accounts.sumOf { candidate.accountBalance(it.id, LocalDate.of(2026, 11, 7)) },
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "supplied-csv-${newId()}.db"
        try {
            BudgetDatabase(context, name).use {
                val old = it.load()
                it.save(old, candidate)
            }
            assertEquals(candidate, BudgetDatabase(context, name).use { it.load() })
        } finally {
            context.deleteDatabase(name)
        }
    }
}
