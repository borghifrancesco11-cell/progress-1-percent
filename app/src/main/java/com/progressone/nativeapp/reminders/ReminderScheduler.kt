package com.progressone.nativeapp.reminders

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
import androidx.core.content.ContextCompat
import com.progressone.nativeapp.MainActivity
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local reminders only; no cloud service, worker or account.
 * Uses inexact Android alarms, which are allowed without exact-alarm special access.
 * Android Doze and battery management may delay actual delivery.
 */
object ReminderScheduler {
    private const val CHANNEL = "progress_tracker_reminders"
    private const val TASK_CHANNEL = "progress_task_reminders"
    internal const val ACTION_REMIND = "com.progressone.nativeapp.REMIND"
    private const val REQUEST_CODE = 31271
    private val timePattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

    fun hasPermission(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun pendingAlarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Find the next tracker or task reminder (up to one year ahead). */
    internal fun nextWakeup(store: LocalStore, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        var earliest: LocalDateTime? = null

        for (tracker in store.all("tracker")) {
            val raw = tracker.optString("reminder")
            if (!timePattern.matches(raw)) continue
            val at = LocalTime.parse(raw)
            for (offset in 0L..366L) {
                val date = now.toLocalDate().plusDays(offset)
                val timestamp = LocalDateTime.of(date, at)
                if (!timestamp.isAfter(now)) continue
                if (!Metrics.due(tracker, date.toString())) continue
                if (earliest == null || timestamp.isBefore(earliest)) earliest = timestamp
                break
            }
        }

        for (task in store.all("task")) {
            if (task.optBoolean("done")) continue
            val due = runCatching { LocalDate.parse(task.optString("due")) }.getOrNull() ?: continue
            val raw = task.optString("reminder", "09:00")
            if (!timePattern.matches(raw)) continue
            val timestamp = LocalDateTime.of(due, LocalTime.parse(raw))
            if (!timestamp.isAfter(now)) continue
            if (earliest == null || timestamp.isBefore(earliest)) earliest = timestamp
        }

        return earliest
    }

    fun reschedule(context: Context, store: LocalStore) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val alarm = pendingAlarm(context)
        manager.cancel(alarm)
        if (store.setting("notifications_enabled", "true") != "true" || !hasPermission(context)) return
        val due = nextWakeup(store) ?: return
        val millis = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarm)
        } catch (_: SecurityException) {
            // Some customized Android builds restrict alarm scheduling.
        }
    }

    /** Deliver only currently due, incomplete and not-yet-notified trackers. */
    fun deliverDue(context: Context, store: LocalStore) {
        if (store.setting("notifications_enabled", "true") != "true" || !hasPermission(context)) return
        val today = LocalDate.now().toString()
        val currentTime = LocalTime.now()
        val logs = store.entries()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Tracker reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Reminders for scheduled Progress 1% trackers" })
        manager.createNotificationChannel(NotificationChannel(
            TASK_CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Reminders for tasks and commitments due today" })
        for (tracker in store.all("tracker")) {
            val raw = tracker.optString("reminder")
            if (!timePattern.matches(raw) || LocalTime.parse(raw).isAfter(currentTime)) continue
            if (!Metrics.due(tracker, today) || Metrics.completion(tracker, logs, today)) continue
            val id = tracker.optString("id")
            if (id.isBlank()) continue
            val key = "last_notified_$id"
            if (store.setting(key) == today) continue
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Progress 1% · Reminder")
                .setContentText(tracker.optString("name", "Your tracker"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setShowWhen(true)
                .build()
            manager.notify(id.hashCode(), notification)
            store.setSetting(key, today)
        }

        for (task in store.all("task")) {
            if (task.optBoolean("done") || task.optString("due") != today) continue
            val raw = task.optString("reminder", "09:00")
            if (!timePattern.matches(raw) || LocalTime.parse(raw).isAfter(currentTime)) continue
            val id = task.optString("id")
            if (id.isBlank()) continue
            val key = "last_notified_task_$id"
            if (store.setting(key) == today) continue
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, TASK_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Progress 1% · Commitment due")
                .setContentText(task.optString("name", "Your task is due today"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setShowWhen(true)
                .build()
            manager.notify("task:$id".hashCode(), notification)
            store.setSetting(key, today)
        }
    }

    /** Rejects explicit or implicit broadcasts that are not part of the reminder contract. */
    internal fun isExpectedAction(action: String?): Boolean = action == ACTION_REMIND ||
        action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED ||
        action == Intent.ACTION_TIME_CHANGED ||
        action == Intent.ACTION_TIMEZONE_CHANGED
}

/** Receives an inexact alarm and reschedules after reboot, package updates or clock changes. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ReminderScheduler.isExpectedAction(intent.action)) return
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                LocalStore(appContext).use { store ->
                    ReminderScheduler.deliverDue(appContext, store)
                    ReminderScheduler.reschedule(appContext, store)
                }
            } catch (_: Exception) {
                // Never crash the process because an OEM delayed an alarm or DB access.
            } finally {
                pending.finish()
            }
        }.start()
    }
}
