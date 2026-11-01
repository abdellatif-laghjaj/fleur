package com.abdellatif.fleur.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdellatif.fleur.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FleurApp(
    vm: BudgetViewModel,
    onExport: (String, DateRange) -> Unit,
    onRestore: () -> Unit,
    onLock: (Boolean) -> Unit,
    onReminders: (Boolean) -> Unit,
) {
    val state by vm.state.collectAsState()
    val loadError by vm.loadError.collectAsState()
    val prefs = state?.preferences ?: Preferences()
    FleurDesign(prefs) {
        if (state == null) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ScriptHeading("Fleur", size = 75)
                if (loadError == null)
                    CircularProgressIndicator(
                        Modifier.padding(25.dp).size(25.dp),
                        strokeWidth = 2.dp,
                    )
                else {
                    Text(loadError!!, Modifier.padding(24.dp), textAlign = TextAlign.Center)
                    TextButton(onClick = vm::load) { Text("Try again") }
                }
            }
        } else BudgetContent(state!!, vm, onExport, onRestore, onLock, onReminders)
    }
}

@Composable
private fun BudgetContent(
    state: BudgetState,
    vm: BudgetViewModel,
    onExport: (String, DateRange) -> Unit,
    onRestore: () -> Unit,
    onLock: (Boolean) -> Unit,
    onReminders: (Boolean) -> Unit,
) {
    val p = state.preferences
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var anchorText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var period by rememberSaveable(p.period) { mutableStateOf(p.period) }
    var customStart by rememberSaveable {
        mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())
    }
    var customEnd by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var accountFilters by rememberSaveable { mutableStateOf(listOf<String>()) }
    var categoryFilters by rememberSaveable { mutableStateOf(listOf<String>()) }
    var filterType by rememberSaveable { mutableStateOf<EntryType?>(null) }
    var filterPaid by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf("") }
    var backStack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var entryKey by rememberSaveable { mutableStateOf("") }
    var initialType by rememberSaveable { mutableStateOf(EntryType.EXPENSE) }
    var initialDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var initialAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAccount by rememberSaveable { mutableStateOf("") }
    val anchor = LocalDate.parse(anchorText)
    val range =
        if (period == Period.CUSTOM)
            DateRange(LocalDate.parse(customStart), LocalDate.parse(customEnd))
        else periodRange(anchor, period, p)
    val filter =
        TransactionFilter(filterType, categoryFilters.toSet(), accountFilters.toSet(), filterPaid)
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(vm) { vm.errors.collect { snackbar.showSnackbar(it) } }
    fun close() {
        route = backStack.lastOrNull().orEmpty()
        backStack = backStack.dropLast(1)
    }
    fun open(next: String) {
        backStack = backStack + route
        route = next
    }
    fun newEntry(
        type: EntryType = EntryType.EXPENSE,
        date: LocalDate = LocalDate.now(),
        account: String? = null,
    ) {
        entryKey = ""
        initialType = type
        initialDate = date.toString()
        initialAccount = account
        open("entry")
    }
    fun edit(o: Occurrence) {
        entryKey = o.key
        open("entry")
    }
    fun category(id: String) {
        categoryFilters = listOf(id)
        filterType = null
        filterPaid = null
        tab = 2
    }
    fun shift(direction: Long) {
        anchorText =
            when (period) {
                Period.DAY -> anchor.plusDays(direction)
                Period.WEEK -> anchor.plusWeeks(direction)
                Period.FORTNIGHT -> anchor.plusWeeks(direction * 2)
                Period.MONTH -> anchor.plusMonths(direction)
                Period.YEAR -> anchor.plusYears(direction)
                Period.CUSTOM -> anchor
            }.toString()
        if (period == Period.CUSTOM) {
            val n = java.time.temporal.ChronoUnit.DAYS.between(range.start, range.end) + 1
            customStart = range.start.plusDays(n * direction).toString()
            customEnd = range.end.plusDays(n * direction).toString()
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(55.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (tab) {
                    0 -> SmallIconButton("triangle", "Open budget", { open("budget") })
                    2 ->
                        SmallIconButton(
                            "calendar_month",
                            "Toggle calendar",
                            { calendar = !calendar },
                        )
                    else -> Spacer(Modifier.width(48.dp))
                }
                if (tab == 3)
                    Text(
                        "ACCOUNTS",
                        Modifier.weight(1f),
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                    )
                else
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SmallIconButton(
                            "back",
                            "Previous period",
                            { shift(-1) },
                            Modifier.size(44.dp),
                        )
                        Row(
                            Modifier.weight(1f, false)
                                .clickable { open("period") }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val title =
                                when (period) {
                                    Period.MONTH ->
                                        anchor.format(
                                            DateTimeFormatter.ofPattern(
                                                "MMMM yyyy",
                                                Locale.getDefault(),
                                            ),
                                        )
                                    Period.YEAR -> anchor.year.toString()
                                    Period.DAY ->
                                        anchor.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                    else ->
                                        "${range.start.format(DateTimeFormatter.ofPattern("MMM d"))} – ${range.end.format(DateTimeFormatter.ofPattern("MMM d"))}"
                                }
                            Text(
                                title.uppercase(),
                                fontSize = 11.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            if (tab == 0) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    Modifier.size(12.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FleurIcon("down", Modifier.size(8.dp), Color.White)
                                }
                            }
                        }
                        SmallIconButton("next", "Next period", { shift(1) }, Modifier.size(44.dp))
                    }
                if (tab == 2) {
                    SmallIconButton(
                        "filter",
                        "Transaction filters",
                        { open("filter") },
                        Modifier.size(44.dp),
                    )
                    SmallIconButton(
                        "search",
                        "Search transactions",
                        { searchVisible = !searchVisible },
                        Modifier.size(44.dp),
                    )
                } else SmallIconButton("settings", "Open settings", { open("settings") })
            }
            if (tab == 2 && searchVisible)
                OutlinedTextField(
                    query,
                    { query = it },
                    placeholder = { Text("Search transactions", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    trailingIcon = {
                        SmallIconButton(
                            "close",
                            "Clear search",
                            {
                                query = ""
                                searchVisible = false
                            },
                        )
                    },
                )
            if (
                tab == 2 &&
                    (categoryFilters.isNotEmpty() ||
                        accountFilters.isNotEmpty() ||
                        filterType != null ||
                        filterPaid != null)
            )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        listOfNotNull(
                                categoryFilters
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString { state.category(it)?.name.orEmpty() },
                                accountFilters
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString { state.account(it)?.name.orEmpty() },
                                filterType?.name?.lowercase(),
                                filterPaid?.let { if (it) "Paid" else "Not paid" },
                            )
                            .joinToString(" · "),
                        Modifier.weight(1f),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(
                        onClick = {
                            categoryFilters = emptyList()
                            accountFilters = emptyList()
                            filterType = null
                            filterPaid = null
                        },
                    ) {
                        Text("Clear", fontSize = 11.sp)
                    }
                }
            Box(Modifier.weight(1f)) {
                Crossfade(tab, label = "tab", modifier = Modifier.fillMaxSize()) { current ->
                    when (current) {
                        0 ->
                            HomeScreen(
                                state,
                                range,
                                accountFilters.toSet(),
                                ::category,
                                { newEntry(it) },
                                { open("budget") },
                            )
                        1 -> InsightsScreen(state, range, accountFilters.toSet(), ::category)
                        2 ->
                            TransactionsScreen(
                                state,
                                range,
                                filter,
                                query,
                                calendar,
                                ::edit,
                                vm::togglePaid,
                                { newEntry(date = it) },
                            )
                        3 ->
                            AccountsScreen(
                                state,
                                { a ->
                                    selectedAccount = a.id
                                    open("account")
                                },
                                {
                                    selectedAccount = ""
                                    open("account")
                                },
                                { from, to ->
                                    vm.change { BudgetActions.reorderAccounts(it, from, to) }
                                },
                            )
                    }
                }
                if (tab == 0)
                    FloatingActionButton(
                        onClick = {
                            if (p.haptics)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            newEntry()
                        },
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .padding(end = 23.dp, bottom = 20.dp)
                                .size(54.dp)
                                .semantics { contentDescription = "Add transaction" },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    ) {
                        FleurIcon("plus", Modifier.size(33.dp), Color.White)
                    }
            }
            Row(
                Modifier.fillMaxWidth()
                    .height(67.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = .6f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Home", "Insights", "Transactions", "Accounts").forEachIndexed { i, label ->
                    val isSelected = i == tab
                    val color =
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = .43f)
                    Column(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .clickable(role = Role.Tab) {
                                if (p.haptics)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                tab = i
                            }
                            .semantics {
                                selected = isSelected
                                contentDescription = label
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FleurIcon("tab_${label.lowercase()}", Modifier.size(23.dp), color)
                        Text(label, Modifier.padding(top = 5.dp), fontSize = 10.sp, color = color)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp))
    }
    when (route) {
        "settings" ->
            SettingsScreen(
                state,
                vm,
                ::close,
                { open("budget") },
                { open("categories") },
                { open("repeating") },
                { onExport(it, range) },
                onRestore,
                onLock,
                onReminders,
            )
        "budget" -> BudgetEditor(state, vm, ::close)
        "categories" -> CategoriesManager(state, vm, ::close)
        "repeating" -> RepeatingScreen(state, ::close, ::edit)
        "period" ->
            PeriodPicker(state, period, range, accountFilters.toSet(), ::close) { v, r, accounts ->
                period = v
                anchorText = r.start.toString()
                customStart = r.start.toString()
                customEnd = r.end.toString()
                accountFilters = accounts.toList()
                close()
            }
        "filter" ->
            TransactionFilterDialog(state, filter, ::close) { v ->
                filterType = v.type
                categoryFilters = v.categories.toList()
                accountFilters = v.accounts.toList()
                filterPaid = v.paid
                close()
            }
        "account" ->
            AccountEditor(
                state,
                vm,
                state.account(selectedAccount),
                ::close,
                {
                    accountFilters = listOf(selectedAccount)
                    categoryFilters = emptyList()
                    tab = 2
                    route = ""
                    backStack = emptyList()
                },
                { newEntry(EntryType.TRANSFER, account = selectedAccount) },
            )
        "entry" -> {
            val editing =
                remember(state, entryKey) {
                    if (entryKey.isEmpty()) null
                    else {
                        val id = entryKey.substringBeforeLast('@')
                        val date = LocalDate.ofEpochDay(entryKey.substringAfterLast('@').toLong())
                        val e = state.overrides[entryKey] ?: state.entries.find { it.id == id }
                        e?.let {
                            Occurrence(
                                it,
                                if (state.overrides.containsKey(entryKey)) it.date else date,
                                id,
                                date,
                            )
                        }
                    }
                }
            TransactionEditor(
                state,
                vm,
                editing,
                initialType,
                LocalDate.parse(initialDate),
                initialAccount,
                ::close,
            )
        }
    }
}
