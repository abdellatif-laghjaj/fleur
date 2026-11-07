package com.abdellatif.fleur.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdellatif.fleur.data.*
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

@Composable
fun SettingsScreen(
    state: BudgetState,
    vm: BudgetViewModel,
    onClose: () -> Unit,
    onBudget: () -> Unit,
    onCategories: () -> Unit,
    onRepeating: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onLock: (Boolean) -> Unit,
    onReminders: (Boolean) -> Unit,
) {
    val p = state.preferences
    val context = LocalContext.current
    var picker by remember { mutableStateOf("") }
    PaperDialog(onClose) {
        SheetTopBar("Settings", onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            ScriptHeading("Fleur", Modifier.align(Alignment.CenterHorizontally), 70)
            SectionLabel("GENERAL")
            FormRow("Your name", p.name.ifBlank { "Your name" }) { picker = "name" }
            FormRow(
                "Budget",
                if (p.overallBudget > 0) formatMoney(p.overallBudget, p) else "Set budget",
                onClick = onBudget,
            )
            FormRow(
                "Budget Frequency",
                p.period.name.lowercase().replaceFirstChar { it.uppercase() },
            ) {
                picker = "period"
            }
            FormRow("Currency", p.currency) { picker = "currency" }
            FormRow("Categories", "", onClick = onCategories)
            FormRow("Repeating Transactions", "", onClick = onRepeating)
            FormRow("Default Account", state.account(p.defaultAccountId)?.name.orEmpty()) {
                picker = "account"
            }
            FormRow("Month Start Day", p.monthStart.toString()) { picker = "monthStart" }
            FormRow(
                "First Day of Week",
                java.time.DayOfWeek.of(p.weekStart)
                    .getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
            ) {
                picker = "weekStart"
            }
            FormRow(
                "Carry Over",
                trailing = {
                    FleurSwitch(
                        p.carryOver,
                        { vm.preferences { v -> v.copy(carryOver = it) } },
                        "Carry Over",
                    )
                },
            )
            Text(
                "Automatically carry the balance from the previous period into the next.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            FormRow(
                "Enable Decimal",
                trailing = {
                    FleurSwitch(
                        p.decimals,
                        { vm.preferences { v -> v.copy(decimals = it) } },
                        "Enable Decimal",
                    )
                },
            )
            FormRow(
                "Enable Paid / Not Paid",
                trailing = {
                    FleurSwitch(
                        p.showPaid,
                        { vm.preferences { v -> v.copy(showPaid = it) } },
                        "Enable Paid / Not Paid",
                    )
                },
            )
            if (p.showPaid)
                FormRow("Default option", if (p.defaultPaid) "Paid" else "Not paid") {
                    picker = "defaultPaid"
                }
            SectionLabel("APPEARANCE")
            FormRow("Dark Mode", p.darkMode) { picker = "dark" }
            FormRow(
                "Theme",
                onClick = { picker = "color" },
                trailing = {
                    WatercolorSpot(
                        Color(p.accent),
                        Modifier.size(44.dp).clickable { picker = "color" }.padding(9.dp),
                    )
                },
            )
            FormRow("Heading font", p.font) { picker = "font" }
            FormRow("Heading size", p.fontSize.toString()) { picker = "fontSize" }
            FormRow(
                "Show Overall Budget Bar",
                trailing = {
                    FleurSwitch(
                        p.showBudget,
                        { vm.preferences { v -> v.copy(showBudget = it) } },
                        "Show Overall Budget Bar",
                    )
                },
            )
            FormRow(
                "Show Debt Bar",
                trailing = {
                    FleurSwitch(
                        p.showDebt,
                        { vm.preferences { v -> v.copy(showDebt = it) } },
                        "Show Debt Bar",
                    )
                },
            )
            FormRow("Budget mode", if (p.budgetRemaining) "Remaining" else "Spent") {
                vm.preferences { it.copy(budgetRemaining = !it.budgetRemaining) }
            }
            FormRow(
                "Haptic Feedback",
                trailing = {
                    FleurSwitch(
                        p.haptics,
                        { vm.preferences { v -> v.copy(haptics = it) } },
                        "Haptic Feedback",
                    )
                },
            )
            SectionLabel("REMINDERS & SECURITY")
            FormRow(
                "Daily reminder",
                trailing = { FleurSwitch(p.reminders, onReminders, "Daily reminder") },
            )
            FormRow(
                "Reminder time",
                String.format(Locale.US, "%02d:%02d", p.reminderHour, p.reminderMinute),
            ) {
                TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            vm.preferences { it.copy(reminderHour = hour, reminderMinute = minute) }
                        },
                        p.reminderHour,
                        p.reminderMinute,
                        true,
                    )
                    .show()
            }
            FormRow("App Lock", trailing = { FleurSwitch(p.appLock, onLock, "App Lock") })
            SectionLabel("IMPORT & EXPORT")
            FormRow("Export CSV", "All history", onClick = onExport)
            FormRow("Import CSV", "Fleur / Cashew", onClick = onImport)
            Text(
                "Export includes history through today and opening balances. CSV imports replace your budget with individual transactions; repeating details stay in notes.",
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
            val hidden = state.accounts.filter { it.hidden }
            if (hidden.isNotEmpty()) {
                SectionLabel("HIDDEN ACCOUNTS")
                hidden.forEach { a ->
                    FormRow(a.name, "Unhide") {
                        vm.change {
                            it.copy(
                                accounts =
                                    it.accounts.map { v ->
                                        if (v.id == a.id) v.copy(hidden = false) else v
                                    },
                            )
                        }
                    }
                }
            }
            SectionLabel("HELP")
            FormRow("How to use Fleur", "Guide") { picker = "help" }
            Text(
                "Fleur Personal · 1.3\nYour budget is stored on this device. All features are available.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 26.dp, bottom = 36.dp),
                lineHeight = 19.sp,
            )
        }
    }
    when (picker) {
        "name" ->
            TextInputDialog("Your name", p.name, onClose = { picker = "" }) { v ->
                vm.preferences { it.copy(name = v.take(100)) }
                picker = ""
            }
        "dark" ->
            ChoiceDialog(
                "Dark Mode",
                listOf("System", "Light", "Dark"),
                p.darkMode,
                { picker = "" },
            ) { v ->
                vm.preferences { it.copy(darkMode = v) }
                picker = ""
            }
        "font" ->
            ChoiceDialog(
                "Heading font",
                listOf(
                    "Almondita",
                    "Bergenia",
                    "Baskerville",
                    "Gowun Batang",
                    "Kaisei Tokumin",
                    "Long Cang",
                    "Miss Katherine",
                    "Nanum Pen",
                ),
                p.font,
                { picker = "" },
            ) { v ->
                vm.preferences { it.copy(font = v) }
                picker = ""
            }
        "defaultPaid" ->
            ChoiceDialog(
                "Default option",
                listOf("Paid", "Not paid"),
                if (p.defaultPaid) "Paid" else "Not paid",
                { picker = "" },
            ) { v ->
                vm.preferences { it.copy(defaultPaid = v == "Paid") }
                picker = ""
            }
        "fontSize" ->
            ChoiceDialog(
                "Heading size",
                listOf("40", "46", "54", "62", "70"),
                p.fontSize.toString(),
                { picker = "" },
            ) { v ->
                vm.preferences { it.copy(fontSize = v.toInt()) }
                picker = ""
            }
        "color" ->
            ColorPicker(
                p.accent,
                { v ->
                    vm.preferences { it.copy(accent = v) }
                    picker = ""
                },
                { picker = "" },
            )
        "account" ->
            AccountPicker(state, p.defaultAccountId, { picker = "" }) { account ->
                vm.preferences { it.copy(defaultAccountId = account.id) }
                picker = ""
            }
        "monthStart" ->
            ChoiceDialog(
                "Month Start Day",
                (1..31).map { it.toString() },
                p.monthStart.toString(),
                { picker = "" },
            ) { v ->
                vm.preferences { it.copy(monthStart = v.toInt()) }
                picker = ""
            }
        "weekStart" ->
            ChoiceDialog(
                "First Day of Week",
                java.time.DayOfWeek.entries.map {
                    it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                },
                null,
                { picker = "" },
            ) { v ->
                vm.preferences {
                    it.copy(weekStart = java.time.DayOfWeek.valueOf(v.uppercase()).value)
                }
                picker = ""
            }
        "period" ->
            ChoiceDialog(
                "Budget Frequency",
                Period.entries
                    .filter { it != Period.CUSTOM }
                    .map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                null,
                { picker = "" },
            ) { v ->
                vm.preferences { it.copy(period = Period.valueOf(v.uppercase())) }
                picker = ""
            }
        "currency" ->
            CurrencyPicker(
                p.currency,
                { v ->
                    vm.preferences { it.copy(currency = v) }
                    picker = ""
                },
                { picker = "" },
            )
        "help" -> HelpScreen { picker = "" }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp, start = 8.dp),
    )
}

@Composable
fun CurrencyPicker(current: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val currencies = remember { Currency.getAvailableCurrencies().sortedBy { it.currencyCode } }
    PaperDialog(onClose) {
        SheetTopBar("Currency", onClose)
        FleurInput(
            search,
            { search = it },
            label = "Search currencies",
            showLabel = false,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            items(
                currencies.filter {
                    it.currencyCode.contains(search, true) || it.displayName.contains(search, true)
                },
                key = { it.currencyCode },
            ) { c ->
                FormRow(
                    c.currencyCode,
                    c.displayName,
                    trailing = {
                        if (current == c.currencyCode)
                            FleurIcon("check", tint = MaterialTheme.colorScheme.primary)
                    },
                ) {
                    onSelect(c.currencyCode)
                }
            }
        }
    }
}

@Composable
fun PeriodPicker(
    state: BudgetState,
    initialPeriod: Period,
    range: DateRange,
    initialAccounts: Set<String>,
    onClose: () -> Unit,
    onSave: (Period, DateRange, Set<String>) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(initialPeriod) }
    var start by remember { mutableStateOf(range.start) }
    var end by remember { mutableStateOf(range.end) }
    var accounts by remember { mutableStateOf(initialAccounts) }
    var error by remember { mutableStateOf<String?>(null) }
    PaperDialog(onClose) {
        SheetTopBar(
            "Date range",
            onClose,
            "Done",
            {
                if (end < start) error = "End date must be after start date"
                else onSave(period, DateRange(start, end), accounts)
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            SectionLabel("DATE RANGE")
            Period.entries.forEach { v ->
                FormRow(
                    v.name.lowercase().replaceFirstChar { it.uppercase() },
                    trailing = {
                        RadioButton(
                            v == period,
                            {
                                period = v
                                if (v != Period.CUSTOM) {
                                    val r = periodRange(LocalDate.now(), v, state.preferences)
                                    start = r.start
                                    end = r.end
                                }
                            },
                        )
                    },
                ) {
                    period = v
                    if (v != Period.CUSTOM) {
                        val r = periodRange(LocalDate.now(), v, state.preferences)
                        start = r.start
                        end = r.end
                    }
                }
            }
            if (period == Period.CUSTOM) {
                DateRow("Start date", start) { start = it }
                DateRow("End date", end) { end = it }
            }
            SectionLabel("ACCOUNT FILTER")
            FormRow(
                "All accounts",
                trailing = { Checkbox(accounts.isEmpty(), { accounts = emptySet() }) },
            ) {
                accounts = emptySet()
            }
            state.visibleAccounts.forEach { a ->
                FormRow(
                    a.name,
                    trailing = {
                        Checkbox(
                            a.id in accounts,
                            { checked ->
                                accounts = if (checked) accounts + a.id else accounts - a.id
                            },
                        )
                    },
                ) {
                    accounts = if (a.id in accounts) accounts - a.id else accounts + a.id
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun TransactionFilterDialog(
    state: BudgetState,
    current: TransactionFilter,
    onClose: () -> Unit,
    onSave: (TransactionFilter) -> Unit,
) {
    var filter by remember { mutableStateOf(current) }
    PaperDialog(onClose) {
        SheetTopBar("Transaction filters", onClose, "Done", { onSave(filter) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            TextButton(onClick = { filter = TransactionFilter() }) { Text("Reset filters") }
            SectionLabel("TYPE")
            listOf<EntryType?>(null, EntryType.EXPENSE, EntryType.INCOME, EntryType.TRANSFER)
                .forEach { type ->
                    FormRow(
                        type?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "All",
                        trailing = {
                            RadioButton(filter.type == type, { filter = filter.copy(type = type) })
                        },
                    ) {
                        filter = filter.copy(type = type)
                    }
                }
            SectionLabel("PAID / NOT PAID")
            listOf<Boolean?>(null, true, false).forEach { paid ->
                FormRow(
                    if (paid == null) "All" else if (paid) "Paid" else "Not paid",
                    trailing = {
                        RadioButton(filter.paid == paid, { filter = filter.copy(paid = paid) })
                    },
                ) {
                    filter = filter.copy(paid = paid)
                }
            }
            SectionLabel("ACCOUNTS")
            state.accounts
                .sortedBy { it.order }
                .forEach { a ->
                    FormRow(
                        a.name,
                        trailing = {
                            Checkbox(
                                a.id in filter.accounts,
                                { checked ->
                                    filter =
                                        filter.copy(
                                            accounts =
                                                if (checked) filter.accounts + a.id
                                                else filter.accounts - a.id,
                                        )
                                },
                            )
                        },
                    ) {
                        filter =
                            filter.copy(
                                accounts =
                                    if (a.id in filter.accounts) filter.accounts - a.id
                                    else filter.accounts + a.id,
                            )
                    }
                }
            SectionLabel("CATEGORIES")
            state.categories
                .sortedBy { it.order }
                .forEach { c ->
                    FormRow(
                        c.name,
                        trailing = {
                            Checkbox(
                                c.id in filter.categories,
                                { checked ->
                                    filter =
                                        filter.copy(
                                            categories =
                                                if (checked) filter.categories + c.id
                                                else filter.categories - c.id,
                                        )
                                },
                            )
                        },
                    ) {
                        filter =
                            filter.copy(
                                categories =
                                    if (c.id in filter.categories) filter.categories - c.id
                                    else filter.categories + c.id,
                            )
                    }
                }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun CategoriesManager(state: BudgetState, vm: BudgetViewModel, onClose: () -> Unit) {
    val listState = rememberLazyListState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(EntryType.EXPENSE) }
    PaperDialog(onClose) {
        SheetTopBar("Categories", onClose, "+ New", { creating = true })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ChoicePill("Expense", type == EntryType.EXPENSE, { type = EntryType.EXPENSE })
            ChoicePill("Income", type == EntryType.INCOME, { type = EntryType.INCOME })
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), state = listState) {
            items(
                state.categories.filter { it.type == type }.sortedBy { it.order },
                key = { it.id },
            ) { c ->
                Column(
                    Modifier.dragReorder(c.id, listState) { from, to ->
                        vm.change { BudgetActions.reorderCategories(it, from, to) }
                    },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).clickable { editing = c }.padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryBadge(c.icon, c.color)
                            Text(c.name, Modifier.padding(start = 12.dp), fontSize = 14.sp)
                        }
                        SmallIconButton(
                            "back",
                            "Move ${c.name} up",
                            {
                                vm.change { s ->
                                    val list = s.categories.sortedBy { it.order }.toMutableList()
                                    val i = list.indexOfFirst { it.id == c.id }
                                    if (i > 0) java.util.Collections.swap(list, i, i - 1)
                                    s.copy(
                                        categories =
                                            list.mapIndexed { index, v -> v.copy(order = index) },
                                    )
                                }
                            },
                            Modifier.size(44.dp),
                        )
                        SmallIconButton(
                            "next",
                            "Move ${c.name} down",
                            {
                                vm.change { s ->
                                    val list = s.categories.sortedBy { it.order }.toMutableList()
                                    val i = list.indexOfFirst { it.id == c.id }
                                    if (i >= 0 && i < list.lastIndex)
                                        java.util.Collections.swap(list, i, i + 1)
                                    s.copy(
                                        categories =
                                            list.mapIndexed { index, v -> v.copy(order = index) },
                                    )
                                }
                            },
                            Modifier.size(44.dp),
                        )
                    }
                    DashedDivider()
                }
            }
        }
    }
    if (creating || editing != null)
        CategoryEditor(
            state,
            vm,
            editing,
            type,
            {
                creating = false
                editing = null
            },
        )
}

@Composable
fun RepeatingScreen(state: BudgetState, onClose: () -> Unit, onEdit: (Occurrence) -> Unit) {
    PaperDialog(onClose) {
        SheetTopBar("Repeating transactions", onClose)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            if (state.entries.none { it.repeat.repeats })
                item { EmptyMessage("No repeating transactions yet.") }
            items(
                state.entries.filter { it.repeat.repeats }.sortedBy { it.date },
                key = { it.id },
            ) { e ->
                Column {
                    TransactionRow(
                        Occurrence(e, e.date),
                        state,
                        { onEdit(Occurrence(e, e.date)) },
                        { onEdit(Occurrence(e, e.date)) },
                    )
                    Text(
                        repeatLabel(e.repeat) + (e.repeat.end?.let { " · Ends $it" } ?: ""),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 46.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun HelpScreen(onClose: () -> Unit) {
    val help =
        listOf(
            "Add a transaction" to
                "Tap the pink + button. Choose Expense, Income, or Transfer, enter an amount, select your category and account, then Save.",
            "Set a budget" to
                "Open Settings → Budget to set an overall spending limit and a limit for each category. Budget Frequency controls the period.",
            "Savings and debt" to
                "Create accounts in the Accounts tab. Savings accounts can have a goal. Debt accounts use a negative opening balance and an optional debt limit. Transfer money into a debt account to pay it down.",
            "Repeating transactions" to
                "Choose Repeating in the transaction editor. Set the frequency, interval, weekdays, optional last day of month, and end date. Editing a recurring transaction lets you change one occurrence or that occurrence and all following ones.",
            "Calendar, search, and filters" to
                "In Transactions, use the calendar icon to switch views. Tap a date to filter that day. Search finds notes, category names, accounts, and amounts. The filter button limits type, account, category, and paid status.",
            "Carry Over" to
                "Enable Carry Over to include the balances from before the selected period in the summary. Transfers within selected accounts cancel out. Debt-account activity is shown separately.",
            "Your data" to
                "Changes save locally. Use Export CSV to save transaction history through today and opening balances. Import CSV accepts Fleur and Cashew files, validates every row and shows a preview before replacing your budget. CSV rows import as individual transactions. Repeating descriptions stay in notes; budget limits and future repeat rules are not restored.",
            "Hide or delete" to
                "Hidden accounts retain their history. Deleting an account also deletes its transactions and transfers. A category with transaction history cannot be deleted until those entries are moved to another category.",
            "Privacy" to
                "The app has no ads, subscriptions, analytics, or server account. Exported files can contain financial information; save and share them only where you choose.",
        )
    PaperDialog(onClose) {
        SheetTopBar("How to use Fleur", onClose)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 35.dp),
        ) {
            items(help) { (title, body) ->
                Column(Modifier.padding(vertical = 15.dp)) {
                    Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Text(
                        body,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 9.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                    )
                }
            }
        }
    }
}
