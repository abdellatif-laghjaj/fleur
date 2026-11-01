package com.abdellatif.fleur.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdellatif.fleur.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.*

@Composable
fun HomeScreen(
    state: BudgetState,
    range: DateRange,
    accountIds: Set<String>,
    onCategory: (String) -> Unit,
    onAdd: (EntryType) -> Unit,
    onBudget: () -> Unit,
) {
    val p = state.preferences
    val occurrences =
        remember(state, range, accountIds) {
            state.occurrences(range.start, range.end).filter {
                accountIds.isEmpty() ||
                    it.entry.accountId in accountIds ||
                    it.entry.toAccountId in accountIds
            }
        }
    val standard =
        remember(occurrences, state.accounts) {
            occurrences.filter { state.account(it.entry.accountId)?.type != AccountType.DEBT }
        }
    val income = standard.filter { it.entry.type == EntryType.INCOME }.sumOf { it.entry.amount }
    val expenses = standard.filter { it.entry.type == EntryType.EXPENSE }.sumOf { it.entry.amount }
    val selected =
        state.accounts.filter {
            it.type != AccountType.DEBT &&
                it.includeInBalance &&
                (accountIds.isEmpty() || it.id in accountIds)
        }
    val carry =
        remember(state, range, accountIds) {
            if (p.carryOver)
                selected.sumOf { state.accountBalance(it.id, range.start.minusDays(1)) }
            else 0L
        }
    val transfer =
        occurrences
            .filter { it.entry.type == EntryType.TRANSFER }
            .sumOf { row -> selected.sumOf { row.entry.effectOn(it.id) } }
    val totalBudget = p.overallBudget.takeIf { it > 0 } ?: state.budgets.sumOf { it.amount }
    val incomeCategories =
        state.categories
            .filter { c ->
                c.type == EntryType.INCOME && standard.any { it.entry.categoryId == c.id }
            }
            .sortedBy { it.order }
    val expenseCategories =
        state.categories
            .filter { c ->
                c.type == EntryType.EXPENSE &&
                    (standard.any { it.entry.categoryId == c.id } ||
                        state.budgets.any { it.categoryId == c.id && it.amount > 0 })
            }
            .sortedWith(
                compareByDescending<Category> { c ->
                        standard
                            .filter {
                                it.entry.categoryId == c.id && it.entry.type == EntryType.EXPENSE
                            }
                            .sumOf { it.entry.amount }
                    }
                    .thenBy { it.order },
            )
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 110.dp),
    ) {
        item { ScriptHeading("Summary", Modifier.padding(top = 12.dp, bottom = 5.dp)) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SummaryAmount(
                    "BALANCE",
                    carry + income - expenses + transfer,
                    Color(0xFFE4EBFC),
                    p,
                    Modifier.weight(1f),
                )
                VerticalDash()
                SummaryAmount("INCOME", income, Color(0xFFCBECD7), p, Modifier.weight(1f))
                VerticalDash()
                SummaryAmount("EXPENSES", expenses, Color(0xFFFADCD9), p, Modifier.weight(1f))
            }
        }
        if (p.showBudget)
            item {
                Column(
                    Modifier.fillMaxWidth().clickable(onClick = onBudget).padding(vertical = 8.dp),
                ) {
                    if (totalBudget > 0) {
                        StripedBar(
                            (expenses.toDouble() / totalBudget).toFloat(),
                            color = Negative,
                            remainder = Positive,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            val remaining = totalBudget - expenses
                            Text(
                                formatMoney(abs(remaining), p),
                                fontSize = 15.sp,
                                color = if (remaining >= 0) Positive else Negative,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (remaining >= 0) " left out of " else " overspent of ",
                                fontSize = 14.sp,
                            )
                            Text(
                                formatMoney(totalBudget, p),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else
                        Text(
                            "Set your budget",
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                        )
                }
            }
        item { ScriptHeading("Income", Modifier.padding(top = 22.dp)) }
        if (incomeCategories.isEmpty())
            item {
                EmptyMessage(
                    "You don't have any income for this time period.",
                    "Add income",
                    { onAdd(EntryType.INCOME) },
                )
            }
        items(incomeCategories, key = { "income-${it.id}" }) { c ->
            CategoryAmountRow(
                c,
                standard
                    .filter { it.entry.type == EntryType.INCOME && it.entry.categoryId == c.id }
                    .sumOf { it.entry.amount },
                p,
                onClick = { onCategory(c.id) },
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScriptHeading("Expenses", Modifier.weight(1f))
                Text(
                    if (p.budgetRemaining) "REMAINING" else "SPENT",
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                    modifier = Modifier.clickable(onClick = onBudget).padding(vertical = 16.dp),
                )
            }
        }
        if (expenseCategories.isEmpty())
            item {
                EmptyMessage(
                    "You don't have any expenses for this time period.",
                    "Add expense",
                    { onAdd(EntryType.EXPENSE) },
                )
            }
        items(expenseCategories, key = { "expense-${it.id}" }) { c ->
            val spent =
                standard
                    .filter { it.entry.type == EntryType.EXPENSE && it.entry.categoryId == c.id }
                    .sumOf { it.entry.amount }
            val budget = state.budgets.find { it.categoryId == c.id }?.amount ?: 0
            CategoryAmountRow(
                c,
                if (p.budgetRemaining && budget > 0) budget - spent else spent,
                p,
                budget = budget,
                spent = spent,
                onClick = { onCategory(c.id) },
            )
        }
        if (p.showDebt) {
            val debt = occurrences.filter {
                state.account(it.entry.accountId)?.type == AccountType.DEBT ||
                    state.account(it.entry.toAccountId)?.type == AccountType.DEBT
            }
            if (debt.isNotEmpty()) {
                item { ScriptHeading("Debt", Modifier.padding(top = 24.dp)) }
                item {
                    val debtIds =
                        state.accounts.filter { it.type == AccountType.DEBT }.map { it.id }
                    val changes = debt.flatMap { row -> debtIds.map { row.entry.effectOn(it) } }
                    FormRow("New debt", formatMoney(-changes.filter { it < 0 }.sum(), p))
                    FormRow("Debt paid off", formatMoney(changes.filter { it > 0 }.sum(), p))
                }
                items(
                    state.accounts.filter { it.type == AccountType.DEBT && !it.hidden },
                    key = { "debt-${it.id}" },
                ) { a ->
                    FormRow(a.name, formatMoney(state.accountBalance(a.id, range.end), p))
                }
            }
        }
    }
}

@Composable
private fun SummaryAmount(
    label: String,
    amount: Long,
    highlight: Color,
    p: Preferences,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val color =
            highlight.copy(
                alpha = if (MaterialTheme.colorScheme.background.red < .3f) .26f else .75f,
            )
        Text(
            label,
            Modifier.drawBehind {
                    drawRect(
                        color,
                        Offset(0f, size.height * .56f),
                        Size(size.width, size.height * .41f),
                    )
                }
                .padding(horizontal = 3.dp),
            fontSize = 10.sp,
            letterSpacing = .3.sp,
        )
        val text = formatMoney(amount, p)
        Text(
            text,
            Modifier.padding(top = 9.dp),
            fontSize = (if (text.length > 11) 16 else if (text.length > 9) 18 else 23).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VerticalDash() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(Modifier.width(1.dp).height(39.dp)) {
        drawLine(
            color,
            Offset.Zero,
            Offset(0f, size.height),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
        )
    }
}

@Composable
fun CategoryAmountRow(
    category: Category,
    amount: Long,
    p: Preferences,
    budget: Long = 0,
    spent: Long = amount,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 8.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(category.icon, category.color)
            Text(
                category.name,
                Modifier.weight(1f).padding(start = 12.dp),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(amount, p), fontSize = 15.sp)
                if (budget > 0) {
                    Spacer(Modifier.height(5.dp))
                    Canvas(Modifier.width(64.dp).height(2.dp)) {
                        drawRect(Color(category.color).copy(alpha = .22f))
                        drawRect(
                            Color(category.color),
                            size =
                                Size(
                                    size.width * (spent.toFloat() / budget).coerceIn(0f, 1f),
                                    size.height,
                                ),
                        )
                    }
                }
            }
        }
        DashedDivider(Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
fun InsightsScreen(
    state: BudgetState,
    range: DateRange,
    accountIds: Set<String>,
    onCategory: (String) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(EntryType.EXPENSE) }
    val occurrences =
        remember(state, range, accountIds, type) {
            state.occurrences(range.start, range.end).filter {
                it.entry.type == type && (accountIds.isEmpty() || it.entry.accountId in accountIds)
            }
        }
    val totals =
        remember(occurrences, state.categories) {
            state.categories
                .map { c ->
                    c to
                        occurrences.filter { it.entry.categoryId == c.id }.sumOf { it.entry.amount }
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 110.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ChoicePill(
                    "Expense",
                    type == EntryType.EXPENSE,
                    { type = EntryType.EXPENSE },
                    "grocery",
                    Negative,
                )
                Spacer(Modifier.width(8.dp))
                ChoicePill(
                    "Income",
                    type == EntryType.INCOME,
                    { type = EntryType.INCOME },
                    "dollar",
                    Positive,
                )
            }
        }
        item { DonutChart(totals, Modifier.padding(top = 12.dp, bottom = 5.dp)) }
        item { ScriptHeading(if (type == EntryType.EXPENSE) "Expenses" else "Income") }
        if (totals.isEmpty())
            item { EmptyMessage("No ${type.name.lowercase()} transactions in this time period.") }
        items(totals, key = { it.first.id }) { (c, amount) ->
            CategoryAmountRow(c, amount, state.preferences, onClick = { onCategory(c.id) })
        }
        if (totals.isNotEmpty()) {
            item { ScriptHeading("Daily spending", Modifier.padding(top = 24.dp), 46) }
            item { SpendingChart(occurrences, range) }
            item { FormRow("Total", formatMoney(totals.sumOf { it.second }, state.preferences)) }
            item {
                FormRow(
                    "Daily average",
                    formatMoney(
                        totals.sumOf { it.second } /
                            (java.time.temporal.ChronoUnit.DAYS.between(range.start, range.end) +
                                1),
                        state.preferences,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DonutChart(totals: List<Pair<Category, Long>>, modifier: Modifier = Modifier) {
    val total = totals.sumOf { it.second }.coerceAtLeast(1)
    val textColor = MaterialTheme.colorScheme.onSurface
    val chartTotals =
        if (totals.size <= 8) totals
        else
            totals.take(7) +
                listOf(
                    Category(
                        id = "chart-others",
                        name = "Other categories",
                        icon = "other",
                        color = 0xFFA8A2A2,
                    ) to totals.drop(7).sumOf { it.second },
                )
    BoxWithConstraints(
        modifier.fillMaxWidth().height(300.dp),
        contentAlignment = Alignment.Center,
    ) {
        val chartSize = (maxWidth - 64.dp).coerceAtMost(236.dp)
        Canvas(
            Modifier.size(chartSize).semantics {
                contentDescription = totals.joinToString {
                    "${it.first.name}: ${(it.second*100.0/total).roundToInt()} percent"
                }
            },
        ) {
            val radius = size.minDimension / 2
            val inner = radius * .44f
            val center = Offset(size.width / 2, size.height / 2)
            var angle = -90f
            if (totals.isEmpty())
                drawCircle(
                    textColor.copy(alpha = .08f),
                    radius,
                    center,
                    style = Stroke((radius - inner)),
                )
            chartTotals.forEach { (c, value) ->
                val sweep = value.toFloat() / total * 360
                val path =
                    Path().apply {
                        arcTo(
                            Rect(
                                center.x - radius,
                                center.y - radius,
                                center.x + radius,
                                center.y + radius,
                            ),
                            angle + .5f,
                            (sweep - 1).coerceAtLeast(.1f),
                            true,
                        )
                        arcTo(
                            Rect(
                                center.x - inner,
                                center.y - inner,
                                center.x + inner,
                                center.y + inner,
                            ),
                            angle + sweep - .5f,
                            -(sweep - 1).coerceAtLeast(.1f),
                            false,
                        )
                        close()
                    }
                clipPath(path) {
                    drawRect(Color(c.color).copy(alpha = .13f))
                    val gap = 3.6.dp.toPx()
                    for (i in -120..120) {
                        val x = i * gap
                        drawLine(
                            Color(c.color),
                            Offset(x, 0f),
                            Offset(x + size.height, size.height),
                            .85.dp.toPx(),
                        )
                    }
                }
                angle += sweep
            }
        }
        var angle = -90f
        chartTotals.forEach { (c, value) ->
            val sweep = value.toFloat() / total * 360
            val middle = Math.toRadians((angle + sweep / 2).toDouble())
            angle += sweep
            val radius = chartSize.value / 2 + 17
            Column(
                Modifier.offset(x = (cos(middle) * radius).dp, y = (sin(middle) * radius).dp)
                    .width(46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CategoryBadge(c.icon, c.color, size = 22.dp)
                Text(
                    "${(value*100.0/total).roundToInt()}%",
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SpendingChart(rows: List<Occurrence>, range: DateRange) {
    val days = java.time.temporal.ChronoUnit.DAYS.between(range.start, range.end).toInt() + 1
    val groups = rows.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.entry.amount } }
    val max = (groups.values.maxOrNull() ?: 1L).coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(125.dp).padding(vertical = 10.dp)) {
        val bins = days.coerceAtMost(60)
        val width = size.width / bins
        for (i in 0 until bins) {
            val start = range.start.plusDays((i.toLong() * days) / bins)
            val end = range.start.plusDays(((i + 1L) * days) / bins - 1)
            val value = groups.filterKeys { it in start..end }.values.sum()
            val h = (value.toFloat() / max * size.height).coerceAtMost(size.height)
            drawRoundRect(
                color.copy(alpha = .65f),
                Offset(i * width + width * .2f, size.height - h),
                Size(width * .6f, h),
                androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
            )
        }
    }
}

data class TransactionFilter(
    val type: EntryType? = null,
    val categories: Set<String> = emptySet(),
    val accounts: Set<String> = emptySet(),
    val paid: Boolean? = null,
)

@Composable
fun TransactionsScreen(
    state: BudgetState,
    range: DateRange,
    filter: TransactionFilter,
    query: String,
    calendar: Boolean,
    onEdit: (Occurrence) -> Unit,
    onPaid: (Occurrence) -> Unit,
    onAdd: (LocalDate) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(calendar, range) { listState.scrollToItem(0) }
    var selectedEpoch by rememberSaveable(range.start) { mutableStateOf<Long?>(null) }
    val all =
        remember(state, range, filter, query) {
            state.occurrences(range.start, range.end).filter { o ->
                val e = o.entry
                (filter.type == null || filter.type == e.type) &&
                    (filter.categories.isEmpty() || e.categoryId in filter.categories) &&
                    (filter.accounts.isEmpty() ||
                        e.accountId in filter.accounts ||
                        e.toAccountId in filter.accounts) &&
                    (filter.paid == null || e.paid == filter.paid) &&
                    (query.isBlank() ||
                        listOf(
                                e.notes,
                                state.category(e.categoryId)?.name.orEmpty(),
                                state.account(e.accountId)?.name.orEmpty(),
                                state.account(e.toAccountId)?.name.orEmpty(),
                                moneyInput(e.amount),
                            )
                            .any { it.contains(query, true) })
            }
        }
    val rows =
        if (calendar && selectedEpoch != null) all.filter { it.date.toEpochDay() == selectedEpoch }
        else all
    val groups = rows.groupBy { it.date }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 110.dp),
    ) {
        if (calendar)
            item {
                CalendarMonth(
                    range.start,
                    state.preferences.weekStart,
                    all.map { it.date }.toSet(),
                    selectedEpoch?.let(LocalDate::ofEpochDay),
                ) {
                    selectedEpoch = if (selectedEpoch == it.toEpochDay()) null else it.toEpochDay()
                }
            }
        if (rows.isEmpty())
            item {
                EmptyMessage(
                    "You don't have any transactions for this time period.",
                    "Add transaction",
                    { onAdd(selectedEpoch?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) },
                )
            }
        groups.forEach { (date, transactions) ->
            item(key = "date-$date") { DateHeading(date) }
            items(transactions, key = { it.key }) { occurrence ->
                TransactionRow(occurrence, state, { onEdit(occurrence) }, { onPaid(occurrence) })
            }
        }
    }
}

@Composable
private fun DateHeading(date: LocalDate) {
    val colors = listOf(0xFFF4C9D4, 0xFFB9E4E4, 0xFFCEDEB2, 0xFFE1C9EC, 0xFFF8D9B1)
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            WatercolorSpot(Color(colors[date.dayOfMonth % colors.size]), Modifier.matchParentSize())
            Text(date.dayOfMonth.toString(), fontFamily = BaskervilleItalic, fontSize = 21.sp)
        }
        ScriptHeading(
            date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
            Modifier.padding(start = 10.dp),
            39,
        )
    }
}

@Composable
fun TransactionRow(
    occurrence: Occurrence,
    state: BudgetState,
    onClick: () -> Unit,
    onPaid: () -> Unit,
) {
    val e = occurrence.entry
    val c = state.category(e.categoryId)
    val p = state.preferences
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(
                if (e.type == EntryType.TRANSFER) "swap_horiz" else c?.icon ?: "other",
                if (e.type == EntryType.TRANSFER) 0xFF8DBDD7 else c?.color ?: 0xFFA7A7A7,
                size = 27.dp,
            )
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(
                    if (e.type == EntryType.TRANSFER) "Transfer" else c?.name ?: "Other",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (e.type == EntryType.TRANSFER)
                        "${state.account(e.accountId)?.name} → ${state.account(e.toAccountId)?.name}"
                    else state.account(e.accountId)?.name.orEmpty(),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (e.notes.isNotBlank())
                    Text(
                        e.notes,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
            Text(
                formatMoney(
                    if (e.type == EntryType.EXPENSE) -e.amount else e.amount,
                    p,
                    signed = e.type == EntryType.INCOME,
                ),
                fontSize = 12.sp,
            )
            if (p.showPaid)
                SmallIconButton(
                    if (e.paid) "paid" else "unpaid",
                    "Mark as ${if(e.paid) "not paid" else "paid"}",
                    onPaid,
                    Modifier.size(44.dp),
                    MaterialTheme.colorScheme.outline,
                )
            else Spacer(Modifier.width(8.dp))
        }
        DashedDivider(Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
fun CalendarMonth(
    anchor: LocalDate,
    weekStart: Int,
    dates: Set<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val month = YearMonth.from(anchor)
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - weekStart + 7) % 7
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        ScriptHeading(
            month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
            Modifier.align(Alignment.CenterHorizontally),
            43,
        )
        Row {
            for (i in 0..6) Text(
                java.time.DayOfWeek.of((weekStart - 1 + i) % 7 + 1)
                    .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                Modifier.weight(1f).padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        for (week in 0 until ceil((month.lengthOfMonth() + offset) / 7.0).toInt()) Row(
            Modifier.fillMaxWidth(),
        ) {
            for (day in 0..6) {
                val number = week * 7 + day - offset + 1
                Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.atDay(number)
                        val isSelected = date == selected
                        Column(
                            Modifier.size(44.dp)
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary.copy(alpha = .25f)
                                    else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { onSelect(date) }
                                .semantics { contentDescription = date.toString() },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                number.toString(),
                                fontSize = 11.sp,
                                color =
                                    if (date == LocalDate.now()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            Box(
                                Modifier.padding(top = 3.dp)
                                    .size(3.dp)
                                    .background(
                                        if (date in dates) MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountsScreen(
    state: BudgetState,
    onAccount: (Account) -> Unit,
    onNew: () -> Unit,
    onReorder: (String, String) -> Unit,
) {
    val listState = rememberLazyListState()
    val balances =
        remember(state) { state.accounts.associate { it.id to state.accountBalance(it.id) } }
    val net = state.visibleAccounts.filter { it.includeInBalance }.sumOf { balances[it.id] ?: 0 }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(24.dp, 18.dp, 24.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("NET WORTH", fontSize = 10.sp, letterSpacing = 1.2.sp)
                Text(
                    formatMoney(net, state.preferences),
                    fontFamily = BaskervilleItalic,
                    fontSize = 34.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
            }
        }
        items(state.visibleAccounts, key = { it.id }) { a ->
            val outline = MaterialTheme.colorScheme.outline.copy(alpha = .6f)
            Column(
                Modifier.fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(
                            outline,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()),
                            style =
                                Stroke(
                                    .7.dp.toPx(),
                                    pathEffect =
                                        PathEffect.dashPathEffect(
                                            floatArrayOf(3.dp.toPx(), 4.dp.toPx()),
                                        ),
                                ),
                        )
                    }
                    .dragReorder(a.id, listState, onReorder)
                    .clickable { onAccount(a) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(a.icon, a.color, size = 27.dp)
                    Text(a.name, Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp)
                    Text(formatMoney(balances[a.id] ?: 0, state.preferences), fontSize = 13.sp)
                }
                if (a.type != AccountType.SPENDING && a.goal > 0) {
                    val balance = balances[a.id] ?: 0
                    val progress =
                        if (a.type == AccountType.DEBT) (a.goal + balance).toFloat() / a.goal
                        else balance.toFloat() / a.goal
                    StripedBar(
                        progress,
                        Modifier.padding(top = 13.dp),
                        Color(a.color),
                        Color(a.color).copy(alpha = .2f),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                        Text(
                            formatMoney(
                                if (a.type == AccountType.DEBT) -a.goal else 0,
                                state.preferences,
                            ),
                            Modifier.weight(1f),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            "${(progress.coerceIn(0f,1f)*100).roundToInt()}%",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            formatMoney(
                                if (a.type == AccountType.DEBT) 0 else a.goal,
                                state.preferences,
                            ),
                            Modifier.weight(1f),
                            fontSize = 10.sp,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
        item {
            TextButton(
                onClick = onNew,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            ) {
                Text("+  Create new account", fontSize = 13.sp)
            }
        }
    }
}
