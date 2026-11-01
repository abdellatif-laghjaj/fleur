package com.abdellatif.fleur

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdellatif.fleur.data.*
import com.abdellatif.fleur.ui.FleurApp
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetWorkflowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: BudgetViewModel
    private val store = ViewModelStore()
    private val dbName = "fleur-workflow-${newId()}.db"
    private val application
        get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun open() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = BudgetViewModel(application, dbName)
            store.put("test", vm)
        }
        compose.setContent { FleurApp(vm, { _, _ -> }, {}, {}, {}) }
        compose.waitUntil(10000) { vm.state.value != null }
    }

    @After
    fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        application.deleteDatabase(dbName)
    }

    private fun addEntry(income: Boolean, amount: String, category: String, notes: String) {
        if (income) compose.onNodeWithText("Add income").performClick()
        else compose.onNodeWithContentDescription("Add transaction").performClick()
        compose.onNodeWithContentDescription("Amount").performTextInput(amount)
        compose.onNodeWithText("Category", useUnmergedTree = true).performClick()
        compose.onNodeWithText(category).performClick()
        compose.onNodeWithContentDescription("Notes").performTextInput(notes)
        compose.onNodeWithText("Save").performClick()
    }

    @Test
    fun incomeExpenseSearchCalendarEditAndPaidStatusWork() {
        addEntry(false, "25.50", "Groceries", "Weekly market")
        compose.waitUntil(10000) { vm.state.value!!.entries.size == 1 }
        addEntry(true, "500.20", "Salary", "November paycheck")
        compose.waitUntil(10000) { vm.state.value!!.entries.size == 2 }
        assertEquals(47470L, vm.state.value!!.accountBalance("default"))
        compose.onNodeWithContentDescription("Transactions").performClick()
        compose.onNodeWithContentDescription("Search transactions").performClick()
        compose.onNodeWithText("Search transactions").performTextInput("Weekly market")
        compose.onNode(hasText("Weekly market") and hasSetTextAction().not()).assertIsDisplayed()
        compose.onNodeWithText("November paycheck").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithContentDescription("Toggle calendar").performClick()
        compose.onNodeWithContentDescription(LocalDate.now().toString()).performClick()
        compose.onNodeWithText("Weekly market").assertIsDisplayed()
        compose.onNodeWithText("Weekly market").performClick()
        compose.onNodeWithContentDescription("Amount").performTextReplacement("30.50")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { vm.state.value!!.entries.any { it.amount == 3050L } }
        assertEquals(46970L, vm.state.value!!.accountBalance("default"))
        compose.onAllNodesWithContentDescription("Mark as not paid").onLast().performClick()
        compose.waitUntil(10000) {
            vm.state.value!!.entries.first { it.type == EntryType.EXPENSE }.paid == false
        }
        assertEquals(50020L, vm.state.value!!.accountBalance("default"))
        compose.onNodeWithText("Weekly market").performClick()
        compose.onNodeWithText("Delete transaction").performScrollTo().performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { vm.state.value!!.entries.size == 1 }
    }

    @Test
    fun savingsAccountAndTransferPreserveCombinedBalance() {
        compose.onNodeWithContentDescription("Accounts").performClick()
        compose.onNodeWithText("+  Create new account").performClick()
        compose.onNodeWithText("Name").performTextInput("Savings")
        compose.onNodeWithText("Account type").performClick()
        compose
            .onNode(hasText("Savings") and hasSetTextAction().not(), useUnmergedTree = true)
            .performClick()
        compose.onNodeWithText("Initial balance").performTextReplacement("100")
        compose.onNodeWithText("Goal amount").performTextReplacement("1000")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { vm.state.value!!.accounts.size == 2 }
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Add transaction").performClick()
        compose.onNodeWithText("TRANSFER").performClick()
        compose.onNodeWithContentDescription("Amount").performTextInput("25")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { vm.state.value!!.entries.size == 1 }
        val state = vm.state.value!!
        assertEquals(-2500L, state.accountBalance("default"))
        assertEquals(12500L, state.accountBalance(state.accounts.first { it.name == "Savings" }.id))
        assertEquals(10000L, state.accounts.sumOf { state.accountBalance(it.id) })
        compose.onNodeWithContentDescription("Accounts").performClick()
        val cashNode = compose.onNodeWithText("Cash")
        val cashHeight = cashNode.fetchSemanticsNode().size.height
        cashNode.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, cashHeight * 1.8f), 500)
            up()
        }
        compose.waitUntil(10000) {
            vm.state.value!!.accounts.sortedBy { it.order }.first().name == "Savings"
        }
    }

    @Test
    fun customCategoryCanChooseIconColorBeRenamedAndDeleted() {
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithText("+ New").performClick()
        compose.onNodeWithText("Name").performTextInput("Books")
        compose.onNodeWithContentDescription("Choose icon").performClick()
        compose.onNodeWithText("Search icons").performTextInput("book")
        compose.onNodeWithContentDescription("Book").performClick()
        compose.onNodeWithText("Color", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Sky color").performClick()
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.waitUntil(10000) { vm.state.value!!.categories.any { it.name == "Books" } }
        val id = vm.state.value!!.categories.first { it.name == "Books" }.id
        assertEquals("book", vm.state.value!!.category(id)!!.icon)
        assertEquals(0xFF85B9D9, vm.state.value!!.category(id)!!.color)
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText("Books"))
        compose.onNodeWithText("Books").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Bookshop")
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.waitUntil(10000) { vm.state.value!!.category(id)?.name == "Bookshop" }
        compose
            .onAllNodes(hasScrollToIndexAction())
            .onLast()
            .performScrollToNode(hasText("Bookshop"))
        compose.onNodeWithText("Bookshop").performClick()
        compose.onNodeWithText("Delete category").performScrollTo().performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { vm.state.value!!.category(id) == null }
    }

    @Test
    fun debtTransfersAffectCashSummaryAndSeparateDebtTotals() {
        compose.runOnIdle {
            vm.change { state ->
                state.copy(
                    accounts = state.accounts + Account("debt", "Card", AccountType.DEBT),
                    entries =
                        listOf(
                            Entry(
                                type = EntryType.TRANSFER,
                                amount = 2500,
                                accountId = "debt",
                                toAccountId = "default",
                            ),
                        ),
                )
            }
        }
        compose.waitUntil(10000) { vm.state.value!!.entries.size == 1 }
        compose.onAllNodesWithText("$25.00").onFirst().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Debt paid off"))
        compose.onNodeWithText("Debt paid off").assertIsDisplayed()
        assertEquals(2500L, vm.state.value!!.accountBalance("default"))
        assertEquals(-2500L, vm.state.value!!.accountBalance("debt"))
    }

    @Test
    fun monthlyRecurringTransactionCanBeCreatedAndDeletedAsASeries() {
        compose.onNodeWithContentDescription("Add transaction").performClick()
        compose.onNodeWithContentDescription("Amount").performTextInput("50")
        compose.onNodeWithText("Category", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Rent").performClick()
        compose.onNodeWithText("Repeating", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Repeats", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Monthly", useUnmergedTree = true).performClick()
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.waitUntil(10000) { vm.state.value!!.entries.size == 1 }
        assertEquals(Frequency.MONTHLY, vm.state.value!!.entries.single().repeat.frequency)
        compose.onNodeWithContentDescription("Transactions").performClick()
        compose.onNodeWithText("Rent").performClick()
        compose.onNodeWithText("Delete transaction").performScrollTo().performClick()
        compose
            .onNodeWithText("This and all following transactions", useUnmergedTree = true)
            .performClick()
        compose.waitUntil(10000) { vm.state.value!!.entries.isEmpty() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val folder =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {
                java.io.File(it)
            } ?: java.io.File(application.getExternalFilesDir(null), "qa")
        folder.mkdirs()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(200)
        java.io.File(folder, name).outputStream().use {
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .takeScreenshot()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun referenceScreensAndSettingsRenderWithRealAccounting() {
        val now = LocalDate.now().withDayOfMonth(1)
        val seed = initialState()
        val accounts =
            listOf(
                Account("cash", "Cash", initial = -29900),
                Account(
                    "savings",
                    "Savings",
                    AccountType.SAVINGS,
                    670500,
                    1000000,
                    "show_chart",
                    0xFF85B9D9,
                    order = 1,
                ),
                Account(
                    "wedding",
                    "Wedding",
                    AccountType.SAVINGS,
                    1140000,
                    2500000,
                    "diamond",
                    0xFFDF84A6,
                    order = 2,
                ),
                Account(
                    "credit",
                    "Credit Card",
                    AccountType.DEBT,
                    -80000,
                    500000,
                    "credit_card",
                    0xFF77C69A,
                    order = 3,
                ),
            )
        val rows =
            listOf(
                Entry(
                    type = EntryType.INCOME,
                    amount = 165400,
                    categoryId = "category-0",
                    accountId = "cash",
                    date = now,
                ),
                Entry(
                    type = EntryType.INCOME,
                    amount = 24000,
                    categoryId = "parents",
                    accountId = "cash",
                    date = now.plusDays(2),
                ),
                Entry(
                    type = EntryType.EXPENSE,
                    amount = 40000,
                    categoryId = "category-3",
                    accountId = "cash",
                    date = now,
                ),
                Entry(
                    type = EntryType.EXPENSE,
                    amount = 25000,
                    categoryId = "category-1",
                    accountId = "cash",
                    date = now.plusDays(1),
                ),
                Entry(
                    type = EntryType.EXPENSE,
                    amount = 15000,
                    categoryId = "category-7",
                    accountId = "cash",
                    date = now.plusDays(2),
                ),
                Entry(
                    type = EntryType.EXPENSE,
                    amount = 8000,
                    categoryId = "category-5",
                    accountId = "cash",
                    date = now.plusDays(2),
                ),
                Entry(
                    type = EntryType.EXPENSE,
                    amount = 5500,
                    categoryId = "category-8",
                    accountId = "cash",
                    date = now,
                ),
                Entry(
                    type = EntryType.EXPENSE,
                    amount = 4000,
                    categoryId = "category-12",
                    accountId = "cash",
                    date = now,
                ),
            )
        vm.change {
            seed.copy(
                accounts = accounts,
                entries = rows,
                budgets =
                    listOf(
                        Budget("category-3", 50000),
                        Budget("category-1", 30000),
                        Budget("category-7", 20000),
                    ),
                preferences =
                    seed.preferences.copy(
                        defaultAccountId = "cash",
                        overallBudget = 120000,
                        darkMode = "Light",
                    ),
            )
        }
        compose.waitUntil(10000) { vm.state.value!!.entries.size == 8 }
        screenshot("01-home.png")
        compose.onNodeWithContentDescription("Open budget").performClick()
        compose.onNodeWithText("Overall budget").performTextReplacement("1300")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { vm.state.value!!.preferences.overallBudget == 130000L }
        compose.onNodeWithContentDescription("Insights").performClick()
        screenshot("02-insights.png")
        compose.onNodeWithContentDescription("Transactions").performClick()
        screenshot("03-transactions.png")
        compose.onNodeWithContentDescription("Toggle calendar").performClick()
        screenshot("04-calendar.png")
        compose.onNodeWithContentDescription("Accounts").performClick()
        screenshot("05-accounts.png")
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Add transaction").performClick()
        compose.onNodeWithContentDescription("Amount").performTextInput("95")
        compose.onNodeWithText("Category", useUnmergedTree = true).performClick()
        screenshot("06-categories.png")
        compose.onAllNodesWithText("Groceries").onLast().performClick()
        screenshot("07-editor.png")
        compose.onAllNodesWithContentDescription("Close").onLast().performClick()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNodeWithText("Dark Mode").performScrollTo().performClick()
        compose.onNodeWithText("Dark", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { vm.state.value!!.preferences.darkMode == "Dark" }
        screenshot("08-settings-dark.png")
        compose.onAllNodesWithContentDescription("Close").onLast().performClick()
        screenshot("09-home-dark.png")
        assertEquals(
            1792500L,
            vm.state.value!!.accounts.sumOf {
                vm.state.value!!.accountBalance(it.id, now.plusMonths(1).minusDays(1))
            },
        )
    }
}
