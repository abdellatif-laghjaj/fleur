package com.abdellatif.fleur

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdellatif.fleur.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetPersistenceTest {
    @Test
    fun durableRoundTripAndInvalidRestoreCannotReplaceExistingBudget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "fleur-persistence-${newId()}.db"
        try {
            val seed = BudgetDatabase(context, name).use { it.load() }
            val entry =
                Entry(
                    type = EntryType.EXPENSE,
                    amount = 1234,
                    accountId = "default",
                    categoryId = "category-1",
                    date = LocalDate.of(2026, 11, 1),
                    notes = "Coffee, \"cake\"\nand friends",
                    repeat = RepeatRule(Frequency.MONTHLY),
                )
            val state =
                seed.copy(
                    entries = listOf(entry),
                    budgets = listOf(Budget("category-1", 50000)),
                    preferences =
                        seed.preferences.copy(name = "Test", currency = "MAD", carryOver = true),
                )
            BudgetDatabase(context, name).use { db -> db.save(seed, state) }
            val reloaded = BudgetDatabase(context, name).use { it.load() }
            assertEquals(state, reloaded)
            val backup = StateCodec.encode(state)
            assertEquals(state, StateCodec.decode(backup))
            assertThrows(IllegalArgumentException::class.java) {
                StateCodec.decode(backup.replace("\"amount\": 1234", "\"amount\": 1234.75"))
            }
            assertThrows(IllegalArgumentException::class.java) {
                StateCodec.decode(backup.replace("\"amount\": 1234", "\"amount\": -1234"))
            }
            BudgetDatabase(context, name).use { db ->
                assertThrows(IllegalArgumentException::class.java) {
                    db.save(
                        reloaded,
                        state.copy(entries = listOf(entry.copy(accountId = "missing"))),
                    )
                }
                assertEquals(state, db.load())
            }
            val csv = Exports.csv(state, DateRange(entry.date, entry.date))
            assertTrue(csv.contains("\"-12.34\""))
            assertTrue(csv.contains("\"Coffee, \"\"cake\"\"\nand friends\""))
            val output = java.io.ByteArrayOutputStream()
            Exports.pdf(state, DateRange(entry.date, entry.date), output)
            assertTrue(
                output.toByteArray().take(4).toByteArray().contentEquals("%PDF".toByteArray()),
            )
            val longReport = java.io.File(context.cacheDir, "long-report-${newId()}.pdf")
            try {
                longReport.outputStream().use {
                    Exports.pdf(
                        state.copy(
                            entries =
                                listOf(
                                    entry.copy(
                                        notes =
                                            "A complete note that must wrap across pages. "
                                                .repeat(200),
                                    ),
                                ),
                        ),
                        DateRange(entry.date, entry.date),
                        it,
                    )
                }
                android.os.ParcelFileDescriptor.open(
                        longReport,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY,
                    )
                    .use { descriptor ->
                        android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                            assertTrue(renderer.pageCount > 1)
                        }
                    }
            } finally {
                longReport.delete()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
