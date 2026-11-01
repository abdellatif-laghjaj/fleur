package com.abdellatif.fleur.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

object Exports {
    fun csv(state: BudgetState, range: DateRange): String {
        fun quote(text: String) = "\"" + text.replace("\"", "\"\"") + "\""
        val header =
            listOf(
                "Date",
                "Type",
                "Category",
                "Account",
                "To Account",
                "Amount",
                "Currency",
                "Paid",
                "Notes",
                "Repeating",
            )
        val rows =
            state.occurrences(range.start, range.end).map { o ->
                val e = o.entry
                listOf(
                    o.date.toString(),
                    e.type.name,
                    state.category(e.categoryId)?.name.orEmpty(),
                    state.account(e.accountId)?.name.orEmpty(),
                    state.account(e.toAccountId)?.name.orEmpty(),
                    moneyInput(if (e.type == EntryType.EXPENSE) -e.amount else e.amount),
                    state.preferences.currency,
                    e.paid.toString(),
                    e.notes,
                    e.repeat.frequency.name,
                )
            }
        // Keep spreadsheet programs from evaluating untrusted notes as formulas.
        fun safe(text: String) =
            if (
                text.firstOrNull() in listOf('=', '+', '@', '\t', '\r') ||
                    text.startsWith("-") && text.drop(1).toBigDecimalOrNull() == null
            )
                "'" + text
            else text
        return "\uFEFF" +
            (listOf(header) + rows).joinToString("\r\n") { row ->
                row.mapIndexed { i, v -> quote(if (i == 5) v else safe(v)) }.joinToString(",")
            } +
            "\r\n"
    }

    fun pdf(state: BudgetState, range: DateRange, output: OutputStream) {
        val document = PdfDocument()
        try {
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                }
            var pageNumber = 0
            var page: PdfDocument.Page? = null
            var y = 0f
            fun newPage() {
                page?.let(document::finishPage)
                page =
                    document.startPage(
                        PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create(),
                    )
                y = 45f
                paint.textSize = 26f
                paint.color = Color.rgb(207, 120, 155)
                page!!.canvas.drawText("Fleur", 36f, y, paint)
                y += 26
                paint.color = Color.DKGRAY
                paint.textSize = 11f
                page!!
                    .canvas
                    .drawText(
                        "${range.start} to ${range.end}  ·  ${state.preferences.currency}",
                        36f,
                        y,
                        paint,
                    )
                y += 32
                page!!
                    .canvas
                    .drawText(
                        "DATE                 CATEGORY / ACCOUNT                                  AMOUNT",
                        36f,
                        y,
                        paint,
                    )
                y += 20
            }
            fun wrapped(text: String, width: Float, lineHeight: Float) {
                for (paragraph in text.split('\n')) {
                    var remaining = paragraph
                    do {
                        if (y > 778) newPage()
                        paint.textSize = 9f
                        paint.color = Color.DKGRAY
                        val fit = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
                        val count =
                            if (fit >= remaining.length) remaining.length
                            else remaining.lastIndexOf(' ', fit).takeIf { it > 0 } ?: fit
                        page!!.canvas.drawText(remaining.take(count), 132f, y, paint)
                        y += lineHeight
                        remaining = remaining.drop(count).trimStart()
                    } while (remaining.isNotEmpty())
                }
            }
            newPage()
            val rows = state.occurrences(range.start, range.end).sortedBy { it.date }
            for (o in rows) {
                if (y > 750) newPage()
                val e = o.entry
                paint.textSize = 10f
                paint.color = Color.DKGRAY
                page!!.canvas.drawText(o.date.toString(), 36f, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                page!!
                    .canvas
                    .drawText(
                        formatMoney(
                            if (e.type == EntryType.EXPENSE) -e.amount else e.amount,
                            state.preferences,
                        ),
                        558f,
                        y,
                        paint,
                    )
                paint.textAlign = Paint.Align.LEFT
                wrapped(
                    if (e.type == EntryType.TRANSFER) "Transfer"
                    else state.category(e.categoryId)?.name ?: "Other",
                    290f,
                    14f,
                )
                wrapped(
                    state.account(e.accountId)?.name.orEmpty() +
                        (if (e.type == EntryType.TRANSFER)
                            " → " + state.account(e.toAccountId)?.name.orEmpty()
                        else "") +
                        (if (e.paid) "" else " · Not paid") +
                        (if (e.notes.isBlank()) "" else "\n" + e.notes),
                    426f,
                    12f,
                )
                y += 20
            }
            if (rows.isEmpty()) {
                paint.textSize = 12f
                page!!.canvas.drawText("No transactions in this date range.", 36f, y, paint)
            }
            page?.let(document::finishPage)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }
}
