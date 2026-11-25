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
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.progressone.nativeapp.MainActivity
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The destination encoded in a reminder notification. */
enum class ReminderDestinationType(val route: String, internal val wireValue: String) {
    TRACKER("track", "tracker"),
    TASK("tasks", "task"),
}

data class ReminderDestination(
    val type: ReminderDestinationType,
    val id: String,
) {
    val route: String get() = type.route
    internal val stableKey: String get() = "${type.wireValue}:$id"
}

/** A reminder ready for delivery at a specific local date/time. */
internal data class ReminderDelivery(
    val destination: ReminderDestination,
    val name: String,
    val scheduledAt: LocalDateTime,
)

/**
 * Local reminders only; no cloud service, worker or account.
 * Uses inexact Android alarms, which are allowed without exact-alarm special access.
 * Android Doze and battery management may delay actual delivery.
 */
object ReminderScheduler {
    private const val CHANNEL = "progress_tracker_reminders"
    private const val TASK_CHANNEL = "progress_task_reminders"
    internal const val ACTION_REMIND = "com.progressone.nativeapp.REMIND"
    private const val ACTION_OPEN_DESTINATION = "com.progressone.nativeapp.action.OPEN_DESTINATION"
    private const val REQUEST_CODE = 31271
    private const val DEFAULT_TASK_REMINDER = "09:00"

    /** MainActivity can use this route to open the correct top-level destination. */
    const val EXTRA_DESTINATION = "com.progressone.nativeapp.extra.DESTINATION"
    /** MainActivity can use this type to distinguish tracker and task records. */
    const val EXTRA_DESTINATION_TYPE = "com.progressone.nativeapp.extra.DESTINATION_TYPE"
    /** The exact persisted tracker/task id encoded in the notification intent. */
    const val EXTRA_DESTINATION_ID = "com.progressone.nativeapp.extra.DESTINATION_ID"

    private val timePattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

    fun hasPermission(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    internal fun notificationsEnabled(value: String): Boolean = value.trim().equals("true", ignoreCase = true)

    private fun pendingAlarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Find the next tracker or task reminder (up to one year ahead for recurring trackers). */
    internal fun nextWakeup(store: LocalStore, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? =
        nextWakeup(
            trackers = store.all("tracker"),
            tasks = store.all("task"),
            logs = store.entries(),
            now = now,
        )

    /** Pure scheduling logic used by [nextWakeup] and unit tests. */
    internal fun nextWakeup(
        trackers: List<JSONObject>,
        tasks: List<JSONObject>,
        logs: List<LocalStore.Entry>,
        now: LocalDateTime,
    ): LocalDateTime? {
        var earliest: LocalDateTime? = null

        for (tracker in trackers) {
            val at = parseReminderTime(tracker.optString("reminder")) ?: continue
            for (offset in 0L..366L) {
                val date = now.toLocalDate().plusDays(offset)
                val timestamp = LocalDateTime.of(date, at)
                if (!timestamp.isAfter(now)) continue
                if (!Metrics.due(tracker, date.toString())) continue
                if (Metrics.completion(tracker, logs, date.toString())) continue
                if (earliest == null || timestamp.isBefore(earliest)) earliest = timestamp
                break
            }
        }

        for (task in tasks) {
            if (task.optBoolean("done")) continue
            val schedule = taskSchedule(task) ?: continue
            val timestamp = LocalDateTime.of(schedule.dueDate, schedule.time)
            if (!timestamp.isAfter(now)) continue
            if (earliest == null || timestamp.isBefore(earliest)) earliest = timestamp
        }

        return earliest
    }

    fun reschedule(context: Context, store: LocalStore) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val alarm = pendingAlarm(context)
        // Cancel first so rescheduling is idempotent and never leaves a stale alarm behind.
        manager.cancel(alarm)
        if (!notificationsEnabled(store.setting("notifications_enabled", "true")) || !hasPermission(context)) return
        val due = nextWakeup(store) ?: return
        val millis = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarm)
        } catch (_: SecurityException) {
            // Some customized Android builds restrict alarm scheduling.
        }
    }

    /** Deliver only currently due, incomplete and not-yet-notified records. */
    fun deliverDue(context: Context, store: LocalStore) {
        if (!notificationsEnabled(store.setting("notifications_enabled", "true")) || !hasPermission(context)) return
        val now = LocalDateTime.now()
        val today = now.toLocalDate().toString()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Tracker reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Reminders for scheduled Progress 1% trackers" })
        manager.createNotificationChannel(NotificationChannel(
            TASK_CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Reminders for tasks and commitments due today" })

        val deliveries = dueReminders(
            trackers = store.all("tracker"),
            tasks = store.all("task"),
            logs = store.entries(),
            now = now,
        )
        for (delivery in deliveries) {
            val destination = delivery.destination
            val markerKey = notificationSettingKey(destination)
            if (alreadyNotified(destination, today, store.setting(markerKey))) continue
            val channel = if (destination.type == ReminderDestinationType.TRACKER) CHANNEL else TASK_CHANNEL
            val title = if (destination.type == ReminderDestinationType.TRACKER) {
                "Progress 1% · Reminder"
            } else {
                "Progress 1% · Commitment due"
            }
            val fallback = if (destination.type == ReminderDestinationType.TRACKER) {
                "Your tracker"
            } else {
                "Your task is due today"
            }
            val notification = Notification.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(delivery.name.ifBlank { fallback })
                .setContentIntent(destinationPendingIntent(context, destination))
                .setAutoCancel(true)
                .setShowWhen(true)
                .build()
            manager.notify(notificationId(destination), notification)
            store.setSetting(markerKey, today)
        }
    }

    /** Pure due-delivery selection; the persisted marker makes repeated calls idempotent. */
    internal fun dueReminders(
        trackers: List<JSONObject>,
        tasks: List<JSONObject>,
        logs: List<LocalStore.Entry>,
        now: LocalDateTime,
    ): List<ReminderDelivery> {
        val today = now.toLocalDate()
        val deliveries = mutableListOf<ReminderDelivery>()

        for (tracker in trackers) {
            val id = tracker.optString("id").trim()
            if (id.isEmpty()) continue
            val at = parseReminderTime(tracker.optString("reminder")) ?: continue
            if (at.isAfter(now.toLocalTime())) continue
            if (!Metrics.due(tracker, today.toString())) continue
            if (Metrics.completion(tracker, logs, today.toString())) continue
            deliveries += ReminderDelivery(
                destination = ReminderDestination(ReminderDestinationType.TRACKER, id),
                name = tracker.optString("name", "Your tracker"),
                scheduledAt = LocalDateTime.of(today, at),
            )
        }

        for (task in tasks) {
            if (task.optBoolean("done")) continue
            val id = task.optString("id").trim()
            if (id.isEmpty()) continue
            val schedule = taskSchedule(task) ?: continue
            if (schedule.dueDate != today) continue
            if (schedule.time.isAfter(now.toLocalTime())) continue
            deliveries += ReminderDelivery(
                destination = ReminderDestination(ReminderDestinationType.TASK, id),
                name = task.optString("name", "Your task is due today"),
                scheduledAt = LocalDateTime.of(schedule.dueDate, schedule.time),
            )
        }

        return deliveries.sortedWith(
            compareBy<ReminderDelivery> { it.scheduledAt }
                .thenBy { it.destination.type.ordinal }
                .thenBy { it.destination.stableKey },
        )
    }

    internal fun notificationSettingKey(destination: ReminderDestination): String = when (destination.type) {
        ReminderDestinationType.TRACKER -> "last_notified_${destination.id}"
        ReminderDestinationType.TASK -> "last_notified_task_${destination.id}"
    }

    internal fun alreadyNotified(
        destination: ReminderDestination,
        date: String,
        lastNotifiedValue: String?,
    ): Boolean = lastNotifiedValue == date

    internal fun notificationId(destination: ReminderDestination): Int = destination.stableKey.hashCode()

    /** Build the explicit Activity intent consumed by MainActivity. */
    internal fun destinationIntent(context: Context, destination: ReminderDestination): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = "$ACTION_OPEN_DESTINATION.${destination.type.wireValue}"
            data = Uri.Builder()
                .scheme("progressone")
                .authority(destination.type.wireValue)
                .appendPath(destination.id)
                .build()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DESTINATION, destination.route)
            putExtra(EXTRA_DESTINATION_TYPE, destination.type.wireValue)
            putExtra(EXTRA_DESTINATION_ID, destination.id)
        }

    internal fun destinationPendingIntent(
        context: Context,
        destination: ReminderDestination,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        destination.stableKey.hashCode(),
        destinationIntent(context, destination),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Parse a notification intent without coupling MainActivity to the wire format. */
    fun destinationFromIntent(intent: Intent?): ReminderDestination? {
        if (intent == null) return null
        val id = intent.getStringExtra(EXTRA_DESTINATION_ID)?.trim().orEmpty()
        if (id.isEmpty()) return null
        val type = when (intent.getStringExtra(EXTRA_DESTINATION_TYPE)) {
            ReminderDestinationType.TRACKER.wireValue -> ReminderDestinationType.TRACKER
            ReminderDestinationType.TASK.wireValue -> ReminderDestinationType.TASK
            else -> when (intent.getStringExtra(EXTRA_DESTINATION)) {
                ReminderDestinationType.TRACKER.route -> ReminderDestinationType.TRACKER
                ReminderDestinationType.TASK.route -> ReminderDestinationType.TASK
                else -> return null
            }
        }
        return ReminderDestination(type, id)
    }

    /** Rejects explicit or implicit broadcasts that are not part of the reminder contract. */
    internal fun isExpectedAction(action: String?): Boolean = action == ACTION_REMIND ||
        action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED ||
        action == Intent.ACTION_TIME_CHANGED ||
        action == Intent.ACTION_TIMEZONE_CHANGED

    private fun parseReminderTime(raw: String): LocalTime? {
        val value = raw.trim()
        if (!timePattern.matches(value)) return null
        return runCatching { LocalTime.parse(value) }.getOrNull()
    }

    private fun taskSchedule(task: JSONObject): TaskSchedule? {
        val due = runCatching { LocalDate.parse(task.optString("due").trim()) }.getOrNull() ?: return null
        val raw = task.optString("reminder", DEFAULT_TASK_REMINDER)
        val time = parseReminderTime(raw) ?: return null
        return TaskSchedule(due, time)
    }

    private data class TaskSchedule(
        val dueDate: LocalDate,
        val time: LocalTime,
    )
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
