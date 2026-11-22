package com.progressone.nativeapp.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.roundToInt

data class SocialLimit(
    val packageName: String,
    val name: String,
    val limitMinutes: Int,
    val enabled: Boolean
)

/** Social detox: local daily limits checked through Android Digital Wellbeing usage stats. */
object SocialUtils {

    private val defaults = listOf(
        Triple("com.instagram.android", "Instagram", 30),
        Triple("com.zhiliaoapp.musically", "TikTok", 30),
        Triple("com.twitter.android", "X (Twitter)", 30),
        Triple("com.facebook.katana", "Facebook", 30),
        Triple("com.snapchat.android", "Snapchat", 30),
        Triple("com.reddit.frontpage", "Reddit", 30)
    )

    private fun defaultsJson(): JSONArray = JSONArray().apply {
        defaults.forEach { (pkg, name, limit) ->
            put(JSONObject().apply {
                put("pkg", pkg)
                put("name", name)
                put("limit", limit)
                put("enabled", true)
            })
        }
    }

    fun loadApps(store: LocalStore): List<SocialLimit> {
        val raw = store.setting("social_apps", "")
        val array = if (raw.isBlank()) defaultsJson()
        else runCatching { JSONArray(raw) }.getOrElse { defaultsJson() }
        val result = mutableListOf<SocialLimit>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val pkg = item.optString("pkg")
            if (pkg.isBlank()) continue
            result += SocialLimit(
                packageName = pkg,
                name = item.optString("name", pkg),
                limitMinutes = item.optInt("limit", 30).coerceIn(0, 600),
                enabled = item.optBoolean("enabled", true)
            )
        }
        return result.ifEmpty { loadDefaults() }
    }

    private fun loadDefaults(): List<SocialLimit> =
        defaults.map { SocialLimit(it.first, it.second, it.third, true) }

    fun saveApps(store: LocalStore, apps: List<SocialLimit>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(JSONObject().apply {
                put("pkg", app.packageName)
                put("name", app.name)
                put("limit", app.limitMinutes)
                put("enabled", app.enabled)
            })
        }
        store.setSetting("social_apps", array.toString())
    }

    fun hasUsagePermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun usageMinutesToday(context: Context, packageName: String): Int {
        if (!hasUsagePermission(context)) return 0
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return 0
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                calendar.timeInMillis,
                System.currentTimeMillis()
            ) ?: return 0
            val millis = stats.firstOrNull { it.packageName == packageName }?.totalTimeInForeground ?: 0L
            (millis / 60000L).toInt()
        } catch (_: Exception) {
            0
        }
    }

    /** null when the module must not contribute to the weekly score (off / no permission). */
    fun score(context: Context, store: LocalStore): Int? {
        if (store.setting("social_master", "true") != "true") return null
        if (!hasUsagePermission(context)) return null
        val apps = loadApps(store).filter { it.enabled && it.limitMinutes > 0 }
        if (apps.isEmpty()) return null
        val perApp = apps.map { app ->
            val used = usageMinutesToday(context, app.packageName)
            if (used <= app.limitMinutes) 100.0
            else (100.0 - (used - app.limitMinutes).toDouble() / app.limitMinutes * 100.0).coerceAtLeast(0.0)
        }
        return perApp.average().roundToInt().coerceIn(0, 100)
    }

    fun summary(store: LocalStore): String {
        if (store.setting("social_master", "true") != "true") return "Detox off"
        val apps = loadApps(store).count { it.enabled && it.limitMinutes > 0 }
        return if (apps == 0) "No limits set" else "$apps apps limited"
    }
}
