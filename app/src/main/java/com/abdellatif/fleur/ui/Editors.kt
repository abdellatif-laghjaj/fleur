package com.abdellatif.fleur.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.abdellatif.fleur.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
fun PaperDialog(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val prefs = LocalPreferences.current
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FleurDesign(prefs) {
            val view = LocalView.current
            val light = MaterialTheme.colorScheme.background.red > .5f
            SideEffect {
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
            }
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
                content = content,
            )
        }
    }
}

@Composable
fun ChoiceDialog(
    title: String,
    choices: List<String>,
    selected: String?,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
) {
    FleurActionSheet(
        actions = choices.map { choice -> FleurAction(choice) { onSelect(choice) } },
        onClose = onClose,
        title = title,
        selected = selected,
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    numeric: Boolean = false,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    FleurAlert(
        title = title,
        onClose = onClose,
        confirmLabel = "Save",
        onConfirm = {
            try {
                onSave(value)
            } catch (e: IllegalArgumentException) {
                error = e.message
            }
        },
    ) {
        FleurInput(
            value,
            { value = it },
            label = title,
            showLabel = false,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                ),
        )

        if (error != null)
            Text(
                error!!,
                color = FleurDestructive,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
    }
}

@Composable
fun DateRow(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    FormRow(label, date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))) {
        DatePickerDialog(
                context,
                { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            )
            .apply {
                datePicker.minDate = java.time.Instant.parse("1900-01-01T00:00:00Z").toEpochMilli()
                datePicker.maxDate = java.time.Instant.parse("2200-12-31T00:00:00Z").toEpochMilli()
            }
            .show()
    }
}

@Composable
fun TransactionEditor(
    state: BudgetState,
    vm: BudgetViewModel,
    editing: Occurrence?,
    initialType: EntryType,
    initialDate: LocalDate,
    initialAccount: String?,
    onClose: () -> Unit,
) {
    val base = editing?.entry
    val focusManager = LocalFocusManager.current
    var amountFocused by remember { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(base?.type ?: initialType) }
    var amount by rememberSaveable { mutableStateOf(base?.amount?.let(::moneyInput).orEmpty()) }
    var category by rememberSaveable { mutableStateOf(base?.categoryId.orEmpty()) }
    var account by rememberSaveable {
        mutableStateOf(base?.accountId ?: initialAccount ?: state.preferences.defaultAccountId)
    }
    var toAccount by rememberSaveable {
        mutableStateOf(
            base?.toAccountId
                ?: state.visibleAccounts.firstOrNull { it.id != account }?.id.orEmpty(),
        )
    }
    var date by rememberSaveable { mutableStateOf((editing?.date ?: initialDate).toString()) }
    var paid by rememberSaveable { mutableStateOf(base?.paid ?: state.preferences.defaultPaid) }
    var notes by rememberSaveable { mutableStateOf(base?.notes.orEmpty()) }
    var repeat by remember { mutableStateOf(base?.repeat ?: RepeatRule()) }
    var picker by remember { mutableStateOf("") }
    LaunchedEffect(picker) { if (picker.isNotEmpty()) focusManager.clearFocus() }
    var error by remember { mutableStateOf<String?>(null) }
    var saveScope by remember { mutableStateOf(false) }
    var deleteScope by remember { mutableStateOf(false) }
    val isRecurring =
        editing != null && state.entries.find { it.id == editing.seriesId }?.repeat?.repeats == true
    fun save(scope: SeriesScope) {
        try {
            val minor = parseMoney(amount)
            require(minor > 0) { "Please enter an amount greater than zero" }
            require(type == EntryType.TRANSFER || state.category(category)?.type == type) {
                "Please select a category"
            }
            require(account.isNotBlank()) { "Please select an account" }
            require(type != EntryType.TRANSFER || toAccount.isNotBlank() && toAccount != account) {
                "Choose two different accounts"
            }
            val entry =
                Entry(
                    base?.id ?: newId(),
                    type,
                    minor,
                    if (type == EntryType.TRANSFER) "" else category,
                    account,
                    if (type == EntryType.TRANSFER) toAccount else "",
                    LocalDate.parse(date),
                    paid,
                    notes,
                    repeat,
                    base?.createdAt ?: System.currentTimeMillis(),
                )
            vm.saveEntry(entry, editing, scope, onClose)
        } catch (e: IllegalArgumentException) {
            error = e.message
        }
    }
    PaperDialog(onClose) {
        SheetTopBar(
            if (editing == null) "New transaction" else "Edit transaction",
            onClose,
            "Save",
            { if (isRecurring) saveScope = true else save(SeriesScope.ONLY) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 23.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ChoicePill(
                    "Expense",
                    type == EntryType.EXPENSE,
                    { type = EntryType.EXPENSE },
                    "grocery",
                    Negative,
                )
                ChoicePill(
                    "Income",
                    type == EntryType.INCOME,
                    { type = EntryType.INCOME },
                    "dollar",
                    Positive,
                )
                ChoicePill(
                    "Transfer",
                    type == EntryType.TRANSFER,
                    { type = EntryType.TRANSFER },
                    "swap_horiz",
                    Color(0xFF8AB9D9),
                )
            }
            val symbol = runCatching {
                java.util.Currency.getInstance(state.preferences.currency).getSymbol(Locale.US)
            }
                .getOrDefault("$")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(symbol, fontSize = 33.sp)
                BasicTextField(
                    if (amountFocused || amount.isBlank()) amount
                    else
                        runCatching {
                                java.math
                                    .BigDecimal(amount.replace(',', '.'))
                                    .setScale(
                                        if (state.preferences.decimals) 2 else 0,
                                        java.math.RoundingMode.HALF_UP,
                                    )
                                    .toPlainString()
                            }
                            .getOrDefault(amount),
                    { if (it.length <= 16) amount = it },
                    Modifier.widthIn(min = 42.dp, max = 260.dp)
                        .onFocusChanged { amountFocused = it.isFocused }
                        .semantics {
                            contentDescription = "Amount"
                        },
                    textStyle =
                        TextStyle(
                            fontFamily = Inter,
                            fontSize = 35.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    decorationBox = { inner ->
                        if (amount.isEmpty())
                            Text(
                                "0.00",
                                fontSize = 35.sp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        inner()
                    },
                    cursorBrush =
                        androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
            if (error != null)
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            if (type != EntryType.TRANSFER)
                FormRow(
                    "Category",
                    state.category(category)?.takeIf { it.type == type }?.name ?: "Select",
                ) {
                    picker = "category"
                }
            DateRow("Date", LocalDate.parse(date)) { date = it.toString() }
            FormRow(
                if (type == EntryType.TRANSFER) "From Account" else "Account",
                state.account(account)?.name ?: "Select",
            ) {
                picker = "account"
            }
            if (type == EntryType.TRANSFER)
                FormRow("To Account", state.account(toAccount)?.name ?: "Select") {
                    picker = "toAccount"
                }
            FormRow("Repeating", repeatLabel(repeat)) { picker = "repeat" }
            if (state.preferences.showPaid)
                FormRow(
                    "Paid",
                    trailing = {
                        SmallIconButton(
                            if (paid) "paid" else "unpaid",
                            "Toggle paid status",
                            { paid = !paid },
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    },
                )
            BasicTextField(
                notes,
                { if (it.length <= 10000) notes = it },
                Modifier.fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .padding(top = 20.dp, start = 8.dp, end = 8.dp)
                    .semantics { contentDescription = "Notes" },
                textStyle =
                    TextStyle(
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                decorationBox = { inner ->
                    if (notes.isEmpty())
                        Text("Notes", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                    inner()
                },
                cursorBrush =
                    androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            )
            if (editing != null)
                TextButton(
                    onClick = { deleteScope = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                ) {
                    Text("Delete transaction", color = MaterialTheme.colorScheme.error)
                }
        }
    }
    when (picker) {
        "category" ->
            CategoryPicker(
                state,
                type,
                {
                    category = it.id
                    picker = ""
                },
                { picker = "" },
                vm,
            )
        "account",
        "toAccount" ->
            AccountPicker(
                state,
                if (picker == "account") account else toAccount,
                { picker = "" },
            ) { selected ->
                if (picker == "account") account = selected.id else toAccount = selected.id
                picker = ""
            }
        "repeat" ->
            RepeatEditor(
                repeat,
                LocalDate.parse(date),
                {
                    repeat = it
                    picker = ""
                },
                { picker = "" },
            )
    }
    if (saveScope)
        ChoiceDialog(
            "Apply changes to",
            listOf("This transaction", "This and all following transactions"),
            null,
            { saveScope = false },
        ) {
            saveScope = false
            save(if (it == "This transaction") SeriesScope.ONLY else SeriesScope.FOLLOWING)
        }
    if (deleteScope) {
        if (isRecurring)
            ChoiceDialog(
                "Delete",
                listOf("This transaction", "This and all following transactions"),
                null,
                { deleteScope = false },
            ) {
                deleteScope = false
                vm.deleteEntry(
                    editing!!,
                    if (it == "This transaction") SeriesScope.ONLY else SeriesScope.FOLLOWING,
                    onClose,
                )
            }
        else
            ConfirmDelete("Do you want to delete this transaction?", { deleteScope = false }) {
                vm.deleteEntry(editing!!, SeriesScope.ONLY, onClose)
            }
    }
}

fun repeatLabel(rule: RepeatRule): String =
    when {
        !rule.repeats -> "No"
        rule.interval > 1 ->
            "Every ${rule.interval} ${if(rule.frequency in listOf(Frequency.WEEKLY,Frequency.FORTNIGHTLY)) "weeks" else if(rule.frequency==Frequency.DAILY) "days" else "months"}"
        else -> rule.frequency.name.lowercase().replaceFirstChar { it.uppercase() }
    }

@Composable
fun RepeatEditor(
    initial: RepeatRule,
    start: LocalDate,
    onSave: (RepeatRule) -> Unit,
    onClose: () -> Unit,
) {
    var frequency by rememberSaveable { mutableStateOf(initial.frequency) }
    var interval by rememberSaveable { mutableStateOf(initial.interval.toString()) }
    var weekdays by remember { mutableStateOf(initial.weekDays.ifEmpty { setOf(start.dayOfWeek) }) }
    var lastDay by rememberSaveable { mutableStateOf(initial.lastDay) }
    var end by remember { mutableStateOf(initial.end) }
    var chooseFrequency by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    PaperDialog(onClose) {
        SheetTopBar(
            "Repeating",
            onClose,
            "Save",
            {
                val n = interval.toIntOrNull()
                if (n == null || n !in 1..365) error = "Choose an interval from 1 to 365"
                else if (end != null && end!! < start)
                    error = "End date must be after the start date"
                else if (
                    frequency in listOf(Frequency.WEEKLY, Frequency.FORTNIGHTLY) &&
                        weekdays.isEmpty()
                )
                    error = "Select at least one weekday"
                else onSave(RepeatRule(frequency, n, weekdays, lastDay, end, initial.anchorDay))
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            FormRow("Repeats", frequency.name.lowercase().replaceFirstChar { it.uppercase() }) {
                chooseFrequency = true
            }
            if (frequency != Frequency.NONE) {
                FleurInput(
                    interval,
                    { interval = it },
                    label = "Every",
                    trailing = {
                        Text(
                            when (frequency) {
                                Frequency.DAILY -> "day(s)"
                                Frequency.WEEKLY,
                                Frequency.FORTNIGHTLY -> "week(s)"
                                Frequency.YEARLY -> "year(s)"
                                else -> "month(s)"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
                )
                if (frequency in listOf(Frequency.WEEKLY, Frequency.FORTNIGHTLY)) {
                    Text(
                        "Repeats on",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DayOfWeek.entries.forEach { day ->
                            Box(
                                Modifier.size(44.dp)
                                    .background(
                                        if (day in weekdays)
                                            MaterialTheme.colorScheme.primary.copy(alpha = .25f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    )
                                    .clickable {
                                        weekdays =
                                            if (day in weekdays) weekdays - day else weekdays + day
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
                if (frequency in listOf(Frequency.MONTHLY, Frequency.QUARTERLY, Frequency.YEARLY))
                    FormRow("Last day of month", trailing = { Switch(lastDay, { lastDay = it }) })
                FormRow("End date", end?.toString() ?: "Never") {
                    end = if (end == null) start.plusYears(1) else null
                }
                end?.let { DateRow("Ends on", it) { end = it } }
            }
            if (error != null)
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 20.dp),
                )
        }
    }
    if (chooseFrequency)
        ChoiceDialog(
            "Repeats",
            Frequency.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            null,
            { chooseFrequency = false },
        ) {
            frequency = Frequency.valueOf(it.uppercase())
            chooseFrequency = false
        }
}

@Composable
fun CategoryPicker(
    state: BudgetState,
    type: EntryType,
    onSelect: (Category) -> Unit,
    onClose: () -> Unit,
    vm: BudgetViewModel,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    PaperDialog(onClose) {
        SheetTopBar("Category", onClose, "+ New", { create = true })
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp)
                .height(44.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FleurIcon("search", Modifier.size(16.dp), MaterialTheme.colorScheme.outline)
            BasicTextField(
                query,
                { query = it },
                Modifier.weight(1f).padding(start = 8.dp).semantics {
                    contentDescription = "Search categories"
                },
                textStyle =
                    TextStyle(
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty())
                        Text("Search", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    inner()
                },
                cursorBrush =
                    androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 30.dp),
        ) {
            itemsIndexed(
                state.categories
                    .filter { it.type == type && it.name.contains(query, true) }
                    .sortedBy { it.order },
                key = { _, c -> c.id },
            ) { index, c ->
                val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = .35f)
                Column(
                    Modifier.heightIn(min = 132.dp)
                        .clickable { onSelect(c) }
                        .drawBehind {
                            val dash =
                                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                            if (index % 3 < 2)
                                drawLine(
                                    lineColor,
                                    Offset(size.width, 0f),
                                    Offset(size.width, size.height),
                                    .5.dp.toPx(),
                                    pathEffect = dash,
                                )
                            drawLine(
                                lineColor,
                                Offset(0f, size.height),
                                Offset(size.width, size.height),
                                .5.dp.toPx(),
                                pathEffect = dash,
                            )
                        }
                        .padding(horizontal = 4.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CategoryBadge(c.icon, c.color, size = 58.dp, filled = false)
                    Text(
                        c.name,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }
            }
        }
    }
    if (create) CategoryEditor(state, vm, null, type, { create = false })
}

val FleurColors =
    listOf(
        0xFFDF84A6,
        0xFFE68AB1,
        0xFFF28B88,
        0xFFF0A58B,
        0xFFF3BE7D,
        0xFFE8CF8C,
        0xFFB5CC91,
        0xFF77C69A,
        0xFF6ABFAE,
        0xFF87C9CE,
        0xFF85B9D9,
        0xFF91A8D6,
        0xFFB8A0DD,
        0xFFC39DE2,
        0xFFC798C3,
        0xFFB49CA6,
        0xFFA1B9A1,
        0xFFB7A181,
        0xFF969CA4,
        0xFF787C84,
        0xFFDBA98F,
        0xFFD9C3A8,
        0xFFCFB8D9,
        0xFFB8D3D7,
        0xFFDC8A8E,
        0xFFB4A084,
        0xFF658B99,
        0xFF8A9F70,
        0xFFD6AA5E,
        0xFFAE87AD,
    )

private val FleurColorNames =
    listOf(
        "Rose",
        "Pink",
        "Coral",
        "Peach",
        "Apricot",
        "Sand",
        "Sage",
        "Mint",
        "Teal",
        "Aqua",
        "Sky",
        "Periwinkle",
        "Lavender",
        "Lilac",
        "Mauve",
        "Dusty rose",
        "Soft green",
        "Taupe",
        "Gray",
        "Slate",
        "Terracotta",
        "Linen",
        "Orchid",
        "Ice blue",
        "Salmon",
        "Mocha",
        "Blue gray",
        "Olive",
        "Ochre",
        "Plum",
    )

@Composable
fun ColorPicker(current: Long, onSelect: (Long) -> Unit, onClose: () -> Unit) {
    FleurAlert(
        title = "Choose a color",
        onClose = onClose,
        confirmLabel = "Close",
        onConfirm = onClose,
        cancelLabel = null,
    ) {
        Column {
            FleurColors.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { color ->
                        Box(
                            Modifier.padding(3.dp)
                                .size(44.dp)
                                .clickable(onClick = { onSelect(color) })
                                .semantics {
                                    contentDescription =
                                        FleurColorNames[FleurColors.indexOf(color)] + " color"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            WatercolorSpot(Color(color), Modifier.matchParentSize())
                            if (current == color) FleurIcon("check", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IconPicker(onSelect: (String) -> Unit, onClose: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    PaperDialog(onClose) {
        SheetTopBar("Choose an icon", onClose)
        FleurInput(
            search,
            { search = it },
            label = "Search icons",
            showLabel = false,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
        LazyVerticalGrid(
            GridCells.Adaptive(76.dp),
            Modifier.fillMaxSize().padding(horizontal = 15.dp),
        ) {
            items(CategoryIcons.filter { it.name.contains(search, true) }, key = { it.key }) { icon
                ->
                Box(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clickable { onSelect(icon.key) }
                        .semantics { contentDescription = icon.name },
                    contentAlignment = Alignment.Center,
                ) {
                    FleurIcon(icon.key, Modifier.size(40.dp), MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun CategoryEditor(
    state: BudgetState,
    vm: BudgetViewModel,
    category: Category?,
    initialType: EntryType = EntryType.EXPENSE,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(category?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(category?.type ?: initialType) }
    var icon by rememberSaveable { mutableStateOf(category?.icon ?: "grocery") }
    var color by rememberSaveable { mutableStateOf(category?.color ?: FleurColors.first()) }
    var picker by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    PaperDialog(onClose) {
        SheetTopBar(
            if (category == null) "New category" else "Edit category",
            onClose,
            "Save",
            {
                if (name.isBlank()) error = "Please enter a name"
                else
                    vm.saveCategory(
                        Category(
                            category?.id ?: newId(),
                            name.trim(),
                            type,
                            icon,
                            color,
                            category?.order ?: state.categories.size,
                        ),
                        onClose,
                    )
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CategoryBadge(icon, color, size = 58.dp)
            }
            FleurInput(
                name,
                { if (it.length <= 100) name = it },
                label = "Name",
                centered = true,
                showLabel = false,
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .fillMaxWidth(.6f)
                        .padding(bottom = 24.dp),
            )
            FormRow("Type", type.name.lowercase().replaceFirstChar { it.uppercase() }) {
                picker = "type"
            }
            FormRow(
                "Icon",
                trailing = {
                    SmallIconButton(icon, "Choose icon", { picker = "icon" }, tint = Color(color))
                },
            )
            FormRow(
                "Color",
                onClick = { picker = "color" },
                trailing = {
                    WatercolorSpot(
                        Color(color),
                        Modifier.size(44.dp).clickable { picker = "color" }.padding(8.dp),
                    )
                },
            )
            if (error != null)
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            if (category != null)
                TextButton(
                    onClick = { delete = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 25.dp),
                ) {
                    Text("Delete category", color = MaterialTheme.colorScheme.error)
                }
        }
    }
    when (picker) {
        "icon" ->
            IconPicker(
                {
                    icon = it
                    picker = ""
                },
                { picker = "" },
            )
        "color" ->
            ColorPicker(
                color,
                {
                    color = it
                    picker = ""
                },
                { picker = "" },
            )
        "type" ->
            ChoiceDialog("Type", listOf("Expense", "Income"), null, { picker = "" }) {
                type = EntryType.valueOf(it.uppercase())
                picker = ""
            }
    }
    if (delete)
        ConfirmDelete("Delete this category?", { delete = false }) {
            vm.change(onClose) { BudgetActions.deleteCategory(it, category!!.id) }
        }
}

@Composable
fun ConfirmDelete(
    title: String,
    onClose: () -> Unit,
    message: String? = null,
    onConfirm: () -> Unit,
) {
    FleurAlert(
        title = title,
        message = message,
        onClose = onClose,
        confirmLabel = "Delete",
        onConfirm = onConfirm,
        destructive = true,
    )
}

@Composable
fun AccountActions(
    state: BudgetState,
    vm: BudgetViewModel,
    account: Account,
    onClose: () -> Unit,
    onEdit: () -> Unit,
) {
    var action by rememberSaveable(account.id) { mutableStateOf("") }
    when (action) {
        "" ->
            FleurActionSheet(
                listOf(
                    FleurAction("Edit Account", onClick = onEdit),
                    FleurAction("Update Balance") { action = "balance" },
                    FleurAction("Hide") {
                        vm.change(onClose) { current ->
                            current.copy(
                                accounts =
                                    current.accounts.map {
                                        if (it.id == account.id) it.copy(hidden = true) else it
                                    },
                            )
                        }
                    },
                    FleurAction("Delete", destructive = true) {
                        action = if (state.accounts.size > 1) "delete" else "last-account"
                    },
                ),
                onClose,
            )
        "balance" ->
            TextInputDialog(
                "Update balance",
                moneyInput(state.accountBalance(account.id)),
                numeric = true,
                onClose = onClose,
            ) { value ->
                val target = parseMoney(value, true)
                vm.change(onClose) { current ->
                    val difference = Math.subtractExact(target, current.accountBalance(account.id))
                    current.copy(
                        accounts =
                            current.accounts.map {
                                if (it.id == account.id)
                                    it.copy(initial = Math.addExact(it.initial, difference))
                                else it
                            },
                    )
                }
            }
        "delete" ->
            ConfirmDelete(
                "Delete this account?",
                onClose,
                message =
                    "Deleting this account permanently deletes its transactions and transfers. Hide the account to keep its history.",
            ) {
                vm.change(onClose) { BudgetActions.deleteAccount(it, account.id) }
            }
        "last-account" ->
            FleurAlert(
                "Keep at least one account",
                onClose = onClose,
                confirmLabel = "Close",
                onConfirm = onClose,
                cancelLabel = null,
                message =
                    "Create another account before deleting this one. You can edit its name or update its balance instead.",
            )
    }
}

@Composable
fun AccountEditor(
    state: BudgetState,
    vm: BudgetViewModel,
    account: Account?,
    onClose: () -> Unit,
    onTransactions: () -> Unit,
    onTransfer: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(account?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(account?.type ?: AccountType.SPENDING) }
    var initial by rememberSaveable { mutableStateOf(account?.initial?.let(::moneyInput) ?: "0") }
    var goal by rememberSaveable { mutableStateOf(account?.goal?.let(::moneyInput) ?: "0") }
    var icon by rememberSaveable { mutableStateOf(account?.icon ?: "wallet") }
    var color by rememberSaveable { mutableStateOf(account?.color ?: 0xFF969CA4) }
    var include by rememberSaveable { mutableStateOf(account?.includeInBalance ?: true) }
    var hidden by rememberSaveable { mutableStateOf(account?.hidden ?: false) }
    var picker by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    val balance = remember(state, account) { account?.let { state.accountBalance(it.id) } ?: 0 }
    PaperDialog(onClose) {
        SheetTopBar(
            if (account == null) "New account" else "Edit account",
            onClose,
            "Save",
            {
                try {
                    require(name.isNotBlank()) { "Please enter a name" }
                    val amount = parseMoney(initial, true)
                    val target = parseMoney(goal.ifBlank { "0" })
                    vm.saveAccount(
                        Account(
                            account?.id ?: newId(),
                            name.trim(),
                            type,
                            if (type == AccountType.DEBT && account == null)
                                -kotlin.math.abs(amount)
                            else amount,
                            target,
                            icon,
                            color,
                            hidden,
                            include,
                            account?.order ?: state.accounts.size,
                        ),
                        onClose,
                    )
                } catch (e: IllegalArgumentException) {
                    error = e.message
                }
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            FleurInput(
                name,
                { if (it.length <= 100) name = it },
                label = "Name",
                centered = true,
                showLabel = false,
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .fillMaxWidth(.6f)
                        .padding(top = 30.dp, bottom = 28.dp),
            )
            FormRow("Account type", type.name.lowercase().replaceFirstChar { it.uppercase() }) {
                picker = "type"
            }
            FleurInput(
                initial,
                { initial = it },
                label =
                    if (type == AccountType.DEBT && account == null) "Initial debt amount"
                    else "Initial balance",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            if (type != AccountType.SPENDING)
                FleurInput(
                    goal,
                    { goal = it },
                    label = if (type == AccountType.DEBT) "Debt limit" else "Goal amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            FormRow(
                "Icon",
                trailing = {
                    SmallIconButton(
                        icon,
                        "Choose account icon",
                        { picker = "icon" },
                        tint = Color(color),
                    )
                },
            )
            FormRow(
                "Color",
                onClick = { picker = "color" },
                trailing = {
                    WatercolorSpot(
                        Color(color),
                        Modifier.size(44.dp).clickable { picker = "color" }.padding(8.dp),
                    )
                },
            )
            FormRow("Include in balance", trailing = { Switch(include, { include = it }) })
            FormRow("Hidden", trailing = { Switch(hidden, { hidden = it }) })
            if (account != null) {
                FormRow("Current balance", formatMoney(balance, state.preferences)) {
                    picker = "balance"
                }
                FormRow("View transactions", "") { onTransactions() }
                FormRow("Transfer balance", "") { onTransfer() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(
                        onClick = {
                            vm.change(onClose) { s ->
                                val list = s.accounts.sortedBy { it.order }.toMutableList()
                                val index = list.indexOfFirst { it.id == account.id }
                                if (index > 0) {
                                    java.util.Collections.swap(list, index, index - 1)
                                }
                                s.copy(accounts = list.mapIndexed { i, a -> a.copy(order = i) })
                            }
                        },
                    ) {
                        Text("Move up")
                    }
                    TextButton(
                        onClick = {
                            vm.change(onClose) { s ->
                                val list = s.accounts.sortedBy { it.order }.toMutableList()
                                val index = list.indexOfFirst { it.id == account.id }
                                if (index >= 0 && index < list.lastIndex) {
                                    java.util.Collections.swap(list, index, index + 1)
                                }
                                s.copy(accounts = list.mapIndexed { i, a -> a.copy(order = i) })
                            }
                        },
                    ) {
                        Text("Move down")
                    }
                }
                TextButton(
                    onClick = { delete = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
            }
            if (error != null)
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 15.dp),
                )
        }
    }
    when (picker) {
        "type" ->
            ChoiceDialog(
                "Account type",
                listOf("Spending", "Savings", "Debt"),
                null,
                { picker = "" },
            ) {
                type = AccountType.valueOf(it.uppercase())
                picker = ""
            }
        "icon" ->
            IconPicker(
                {
                    icon = it
                    picker = ""
                },
                { picker = "" },
            )
        "color" ->
            ColorPicker(
                color,
                {
                    color = it
                    picker = ""
                },
                { picker = "" },
            )
        "balance" ->
            TextInputDialog("Update balance", moneyInput(balance), true, { picker = "" }) { value ->
                try {
                    val target = parseMoney(value, true)
                    initial =
                        moneyInput(
                            Math.addExact(account!!.initial, Math.subtractExact(target, balance)),
                        )
                    picker = ""
                } catch (e: Exception) {
                    error = e.message
                    picker = ""
                }
            }
    }
    if (delete)
        ConfirmDelete(
            "Delete this account?",
            { delete = false },
            message =
                "Deleting this account permanently deletes its transactions and transfers. Hide the account to keep its history.",
        ) {
            vm.change(onClose) { BudgetActions.deleteAccount(it, account!!.id) }
        }
}

@Composable
fun AccountPicker(
    state: BudgetState,
    current: String,
    onClose: () -> Unit,
    onSelect: (Account) -> Unit,
) {
    PaperDialog(onClose) {
        SheetTopBar("Account", onClose)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            items(state.visibleAccounts, key = { it.id }) { account ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onSelect(account) }
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(account.icon, account.color)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(account.name, fontSize = 14.sp)
                        Text(
                            account.type.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (account.id == current)
                        FleurIcon("check", tint = MaterialTheme.colorScheme.primary)
                }
                DashedDivider()
            }
        }
    }
}

@Composable
fun BudgetEditor(state: BudgetState, vm: BudgetViewModel, onClose: () -> Unit) {
    var overall by rememberSaveable { mutableStateOf(moneyInput(state.preferences.overallBudget)) }
    val limits = remember {
        mutableStateMapOf<String, String>().apply {
            state.categories
                .filter { it.type == EntryType.EXPENSE }
                .forEach { c ->
                    put(c.id, moneyInput(state.budgets.find { it.categoryId == c.id }?.amount ?: 0))
                }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    PaperDialog(onClose) {
        SheetTopBar(
            "Budget",
            onClose,
            "Save",
            {
                try {
                    vm.saveBudgets(
                        parseMoney(overall.ifBlank { "0" }),
                        limits
                            .map { Budget(it.key, parseMoney(it.value.ifBlank { "0" })) }
                            .filter { it.amount > 0 },
                        onClose,
                    )
                } catch (e: Exception) {
                    error = e.message
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 30.dp),
        ) {
            item {
                ScriptHeading("Your budget", size = 49)
                Text(
                    "Set spending limits for each budget period.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                FleurInput(
                    overall,
                    { overall = it },
                    label = "Overall budget",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                )
            }
            item {
                FormRow(
                    "Budget mode",
                    if (state.preferences.budgetRemaining) "Remaining" else "Spent",
                ) {
                    vm.preferences { it.copy(budgetRemaining = !it.budgetRemaining) }
                }
            }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            items(
                state.categories.filter { it.type == EntryType.EXPENSE }.sortedBy { it.order },
                key = { it.id },
            ) { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(c.icon, c.color)
                    Text(c.name, Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 13.sp)
                    FleurInput(
                        limits[c.id] ?: "0",
                        { limits[c.id] = it },
                        label = "${c.name} budget",
                        showLabel = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(115.dp),
                    )
                }
            }
        }
    }
}
