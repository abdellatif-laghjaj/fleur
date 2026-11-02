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
    fun versionOneUpgradeMergesCategoriesAndPersistsWithoutReaddingDeletedDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "fleur-upgrade-${newId()}.db"
        try {
            val seed = BudgetDatabase(context, name).use { it.load() }
            val addedIds = setOf("shopping", "health-care", "charity", "family-support")
            val legacyCategories =
                seed.categories
                    .filter { it.id !in addedIds }
                    .map {
                        when (it.id) {
                            "category-1" -> it.copy(name = "Groceries", icon = "grocery")
                            "category-7" -> it.copy(name = "Travel")
                            "category-13" -> it.copy(name = "Internet")
                            "category-11" -> it.copy(icon = "phone")
                            else -> it
                        }
                    } +
                    listOf(
                        Category(
                            "category-5",
                            "Restaurant",
                            EntryType.EXPENSE,
                            "restaurant",
                            0xFFDF84A6,
                            5,
                        ),
                        Category(
                            "category-12",
                            "Coffee",
                            EntryType.EXPENSE,
                            "coffee",
                            0xFFDF84A6,
                            12,
                        ),
                        Category("category-14", "Taxi", EntryType.EXPENSE, "taxi", 0xFFDF84A6, 14),
                    )
            val date = LocalDate.of(2026, 11, 1)
            val recurring =
                Entry(
                    id = "monthly-meal",
                    type = EntryType.EXPENSE,
                    amount = 1234,
                    accountId = "default",
                    categoryId = "category-5",
                    date = date,
                    repeat = RepeatRule(Frequency.MONTHLY),
                )
            val key = "${recurring.id}@${date.toEpochDay()}"
            val legacy =
                seed.copy(
                    categories = legacyCategories,
                    entries =
                        listOf(
                            recurring,
                            recurring.copy(
                                id = "ride",
                                categoryId = "category-14",
                                amount = 650,
                                repeat = RepeatRule(),
                            ),
                        ),
                    overrides =
                        mapOf(key to recurring.copy(categoryId = "category-12", amount = 725)),
                    budgets =
                        listOf(
                            Budget("category-1", 10000),
                            Budget("category-5", 20000),
                            Budget("category-12", 3000),
                        ),
                )
            BudgetDatabase(context, name).use {
                it.save(seed, legacy)
                it.writableDatabase.version = 1
            }
            val upgraded =
                BudgetDatabase(context, name).use {
                    val state = it.load()
                    assertEquals(2, it.readableDatabase.version)
                    state
                }
            assertEquals("Food & Drinks", upgraded.category("category-1")!!.name)
            assertEquals("Transportation", upgraded.category("category-7")!!.name)
            assertEquals("Communication & PC", upgraded.category("category-13")!!.name)
            assertTrue(
                upgraded.categories.none {
                    it.id in setOf("category-5", "category-12", "category-14")
                },
            )
            assertEquals("category-1", upgraded.entries.first().categoryId)
            assertEquals("category-7", upgraded.entries.last().categoryId)
            assertEquals("category-1", upgraded.overrides[key]!!.categoryId)
            assertEquals(33000L, upgraded.budgets.single().amount)
            assertEquals(
                legacy.accountBalance("default", date.plusMonths(2)),
                upgraded.accountBalance("default", date.plusMonths(2)),
            )
            assertEquals(upgraded, StateCodec.decode(StateCodec.encode(upgraded)))
            val withoutCharity =
                upgraded.copy(categories = upgraded.categories.filterNot { it.name == "Charity" })
            BudgetDatabase(context, name).use { it.save(upgraded, withoutCharity) }
            assertEquals(withoutCharity, BudgetDatabase(context, name).use { it.load() })
        } finally {
            context.deleteDatabase(name)
        }
    }

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
