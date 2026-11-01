package com.abdellatif.fleur.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.abdellatif.fleur.MainActivity
import com.abdellatif.fleur.R
import com.abdellatif.fleur.data.BudgetDatabase
import com.abdellatif.fleur.data.Preferences
import java.time.ZonedDateTime

object Reminders {
    fun schedule(context: Context, p: Preferences) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, ReminderReceiver::class.java)
                    .setAction("com.abdellatif.fleur.REMIND"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.cancel(pending)
        if (!p.reminders) return
        val now = ZonedDateTime.now()
        var next =
            now.withHour(p.reminderHour).withMinute(p.reminderMinute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        manager.setWindow(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            15 * 60 * 1000L,
            pending,
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val p = BudgetDatabase(context).use { it.load().preferences }
                if (p.reminders && intent.action == "com.abdellatif.fleur.REMIND") {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(
                        NotificationChannel(
                            "daily-budget",
                            "Daily budget reminders",
                            NotificationManager.IMPORTANCE_DEFAULT,
                        ),
                    )
                    if (
                        Build.VERSION.SDK_INT < 33 ||
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED
                    ) {
                        val open =
                            PendingIntent.getActivity(
                                context,
                                2,
                                Intent(context, MainActivity::class.java),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                        manager.notify(
                            1,
                            Notification.Builder(context, "daily-budget")
                                .setSmallIcon(R.drawable.ios_asset_tab_home_95041abf)
                                .setContentTitle("A little moment for your budget")
                                .setContentText(
                                    "Add today's expenses and see how your budget is doing.",
                                )
                                .setContentIntent(open)
                                .setAutoCancel(true)
                                .build(),
                        )
                    }
                }
                Reminders.schedule(context, p)
            } finally {
                pending.finish()
            }
        }
            .start()
    }
}
