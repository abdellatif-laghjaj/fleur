package com.abdellatif.fleur.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.abdellatif.fleur.MainActivity
import com.abdellatif.fleur.R
import com.abdellatif.fleur.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BudgetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                BudgetDatabase(context).use { db -> render(context, db.load(), manager, ids) }
            } finally {
                pending.finish()
            }
        }
            .start()
    }

    companion object {
        fun update(context: Context, state: BudgetState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BudgetWidget::class.java))
            if (ids.isNotEmpty()) render(context, state, manager, ids)
        }

        private fun render(
            context: Context,
            state: BudgetState,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val p = state.preferences
            val range = periodRange(LocalDate.now(), p.period, p)
            val occurrences = state.occurrences(range.start, range.end)
            val rows = occurrences.filter {
                state.account(it.entry.accountId)?.type != AccountType.DEBT
            }
            val expenses =
                rows.filter { it.entry.type == EntryType.EXPENSE }.sumOf { it.entry.amount }
            val income = rows.filter { it.entry.type == EntryType.INCOME }.sumOf { it.entry.amount }
            val selected =
                state.accounts.filter {
                    it.type != AccountType.DEBT && it.includeInBalance
                }
            val transfers =
                occurrences
                    .filter { it.entry.type == EntryType.TRANSFER }
                    .sumOf { row -> selected.sumOf { row.entry.effectOn(it.id) } }
            val carry =
                if (p.carryOver)
                    selected.sumOf { state.accountBalance(it.id, range.start.minusDays(1)) }
                else 0L
            val view = RemoteViews(context.packageName, R.layout.budget_widget)
            view.setTextViewText(
                R.id.widget_month,
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(),
            )
            view.setTextViewText(
                R.id.widget_balance,
                if (p.appLock) "••••" else formatMoney(carry + income - expenses + transfers, p),
            )
            view.setTextViewText(
                R.id.widget_details,
                if (p.appLock) "Unlock Fleur to view your budget"
                else "Income ${formatMoney(income,p)}  ·  Spent ${formatMoney(expenses,p)}",
            )
            view.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(ids, view)
        }
    }
}
