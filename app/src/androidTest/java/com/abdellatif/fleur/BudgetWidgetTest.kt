package com.abdellatif.fleur

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdellatif.fleur.data.*
import com.abdellatif.fleur.platform.BudgetWidget
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetWidgetTest {
    @Test
    fun boundWidgetShowsDebtTransferAndHidesAmountsWhenLocked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, (System.nanoTime() and 0x7fffffff).toInt())
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            "android.permission.BIND_APPWIDGET",
        )
        try {
            val id = host.allocateAppWidgetId()
            assertTrue(
                manager.bindAppWidgetIdIfAllowed(
                    id,
                    ComponentName(context, BudgetWidget::class.java),
                ),
            )
            lateinit var view: AppWidgetHostView
            instrumentation.runOnMainSync {
                host.startListening()
                view = host.createView(context, id, manager.getAppWidgetInfo(id))
            }
            val state =
                initialState().let { seed ->
                    seed.copy(
                        accounts = seed.accounts + Account("debt", "Card", AccountType.DEBT),
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
            fun expectBalance(expected: String) {
                val deadline = SystemClock.uptimeMillis() + 5000
                var actual = ""
                do {
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync {
                        actual =
                            view
                                .findViewById<TextView>(R.id.widget_balance)
                                ?.text
                                ?.toString()
                                .orEmpty()
                    }
                    if (actual == expected) break
                    SystemClock.sleep(50)
                } while (SystemClock.uptimeMillis() < deadline)
                assertEquals(expected, actual)
            }
            BudgetWidget.update(context, state)
            expectBalance("$25.00")
            BudgetWidget.update(
                context,
                state.copy(preferences = state.preferences.copy(appLock = true)),
            )
            expectBalance("••••")
            instrumentation.runOnMainSync {
                assertEquals(
                    "Unlock Fleur to view your budget",
                    view.findViewById<TextView>(R.id.widget_details).text.toString(),
                )
            }
        } finally {
            host.stopListening()
            host.deleteHost()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
