package com.abdellatif.fleur.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdellatif.fleur.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val chartDate
    get() = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

@Composable
fun InsightTransactionRow(
    row: Occurrence,
    state: BudgetState,
    modifier: Modifier,
    onEdit: (Occurrence) -> Unit,
) {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = .5f)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { onEdit(row) },
            )
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(36.dp).height(48.dp)) {
            drawLine(
                line,
                Offset(size.width / 2, 0f),
                Offset(size.width / 2, size.height),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Text(row.date.format(chartDate), fontSize = 14.sp)
            val detail =
                row.entry.notes.ifBlank { state.account(row.entry.accountId)?.name.orEmpty() }
            if (detail.isNotBlank())
                Text(
                    detail,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        Text(formatMoney(row.entry.amount, state.preferences), fontSize = 14.sp)
    }
}

@Composable
fun SpendingChart(rows: List<Occurrence>, range: DateRange) {
    val bins = remember(rows, range) { spendingBins(rows, range) }
    val max = (bins.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1)
    val p = LocalPreferences.current
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(formatMoney(max, p), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        Canvas(
            Modifier.fillMaxWidth().height(125.dp).padding(top = 8.dp).semantics {
                contentDescription = bins.joinToString {
                    "${it.start.format(chartDate)} to ${it.end.format(chartDate)}: ${formatMoney(it.amount, p)}"
                }
            },
        ) {
            drawLine(outline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            val width = size.width / bins.size
            bins.forEachIndexed { i, bin ->
                if (bin.amount > 0) {
                    val height = bin.amount.toFloat() / max * size.height
                    drawLine(
                        color.copy(alpha = .8f),
                        Offset((i + .5f) * width, size.height),
                        Offset((i + .5f) * width, size.height - height),
                        (width * .45f).coerceAtMost(7.dp.toPx()),
                        StrokeCap.Round,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                range.start.format(chartDate),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                range.end.format(chartDate),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            if (bins.any { it.start != it.end }) "Each bar combines nearby days."
            else "Each bar shows one day's total.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}

@Composable
fun CategoryForecast(
    category: Category,
    rows: List<Occurrence>,
    range: DateRange,
    budget: Long,
    p: Preferences,
    onTransactions: () -> Unit,
) {
    val today = LocalDate.now()
    val forecast = remember(rows, range, today) { budgetForecast(rows, range, today) }
    val actual =
        remember(rows, range, forecast.observedThrough) {
            spendingBins(rows.filter { it.date <= today }, range)
                .runningFold(0L) { sum, bin -> sum + bin.amount }
                .drop(1)
        }
    val color = Color(category.color)
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)
    val remaining = budget - forecast.expected
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text("Budget outlook", fontSize = 14.sp)
        Row(
            Modifier.fillMaxWidth().padding(top = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Spent so far", fontSize = 11.sp, color = muted)
                Text(
                    formatMoney(forecast.spent, p),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (today >= range.end) "Final spending"
                    else "Expected by ${range.end.format(chartDate)}",
                    fontSize = 11.sp,
                    color = muted,
                )
                Text(
                    formatMoney(forecast.expected, p),
                    fontSize = 15.sp,
                    color = if (remaining < 0) Negative else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        Text(
            "Budget ${formatMoney(budget, p)}",
            fontSize = 10.sp,
            color = muted,
            modifier = Modifier.padding(top = 18.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(132.dp).padding(vertical = 10.dp).semantics {
                contentDescription =
                    "${category.name} budget outlook. Spent ${formatMoney(forecast.spent, p)}. Expected ${formatMoney(forecast.expected, p)}. Budget ${formatMoney(budget, p)}."
            },
        ) {
            val maximum = maxOf(budget, forecast.expected, 1L).toFloat() * 1.12f
            fun y(value: Float) = size.height * (1 - value / maximum)
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            drawLine(
                muted.copy(alpha = .6f),
                Offset(0f, y(budget.toFloat())),
                Offset(size.width, y(budget.toFloat())),
                1.dp.toPx(),
                pathEffect = dash,
            )
            val observedX = size.width * forecast.elapsedDays.toFloat() / forecast.totalDays
            val path =
                Path().apply {
                    moveTo(0f, size.height)
                    actual.forEachIndexed { index, amount ->
                        val x = size.width * (index + 1).toFloat() / actual.size
                        if (x < observedX) lineTo(x, y(amount.toFloat()))
                    }
                    lineTo(observedX, y(forecast.spent.toFloat()))
                }
            if (forecast.elapsedDays > 0)
                drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            if (forecast.elapsedDays < forecast.totalDays)
                drawLine(
                    color,
                    Offset(observedX, y(forecast.spent.toFloat())),
                    Offset(size.width, y(forecast.expected.toFloat())),
                    2.dp.toPx(),
                    pathEffect = dash,
                )
            drawCircle(color, 3.dp.toPx(), Offset(observedX, y(forecast.spent.toFloat())))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(range.start.format(chartDate), fontSize = 10.sp, color = muted)
            Text(range.end.format(chartDate), fontSize = 10.sp, color = muted)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listOf("Recorded" to color, "Estimate" to color, "Budget" to muted).forEachIndexed {
                index,
                (label, tint) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Canvas(Modifier.size(14.dp, 8.dp)) {
                        drawLine(
                            tint,
                            Offset(0f, size.height / 2),
                            Offset(size.width, size.height / 2),
                            1.5.dp.toPx(),
                            pathEffect =
                                if (index == 0) null
                                else
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(3.dp.toPx(), 2.dp.toPx()),
                                    ),
                        )
                    }
                    Text(label, fontSize = 10.sp, color = muted)
                }
            }
        }
        Text(
            "${formatMoney(kotlin.math.abs(remaining), p)} ${if (remaining >= 0) "expected left" else "expected over budget"}",
            color = if (remaining >= 0) Positive else Negative,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (today < range.end)
            Text(
                if (forecast.elapsedDays == 0L)
                    "Based on scheduled expenses. This period hasn't started."
                else "Estimate from your daily pace, at least scheduled expenses. It may change.",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        TextButton(onClick = onTransactions, modifier = Modifier.align(Alignment.Start)) {
            Text("View transactions")
        }
        DashedDivider()
    }
}
