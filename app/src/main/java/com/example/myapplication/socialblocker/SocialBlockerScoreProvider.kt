package com.example.myapplication.socialblocker

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.SocialBlockerData
import com.example.myapplication.score.ModuleScoreProvider
import java.util.Calendar

class SocialBlockerScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "social_blocker"
    override val displayName: String = "Digital Detox (Social)"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<SocialBlockerData>(context, "social_blocker_data") ?: return 90f
        if (!data.enabled) return 100f

        val enabledApps = data.blockedApps.filter { it.enabled }
        if (enabledApps.isEmpty()) return 100f

        val hasPerm = checkPermission()
        if (!hasPerm) {
            // Se non c'è il permesso garantisce un punteggio incoraggiante
            return 85f
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 85f
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        var respectedCount = 0

        for (app in enabledApps) {
            val appStat = stats.find { it.packageName == app.packageName }
            val minutes = ((appStat?.totalTimeInForeground ?: 0L) / (1000 * 60)).toInt()
            if (minutes <= app.dailyLimitMinutes) {
                respectedCount++
            }
        }

        return (respectedCount.toFloat() / enabledApps.size.toFloat()) * 100f
    }

    private fun checkPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
