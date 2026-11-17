package com.example.myapplication.socialblocker

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.AppBlock
import com.example.myapplication.data.model.SocialBlockerData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

class SocialBlockerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<SocialBlockerData> = _data.asStateFlow()

    private fun loadData(): SocialBlockerData {
        return JsonStorage.load<SocialBlockerData>(context, "social_blocker_data") ?: SocialBlockerData(
            enabled = true
        )
    }

    private fun saveData(newData: SocialBlockerData) {
        _data.value = newData
        JsonStorage.save(context, "social_blocker_data", newData)
    }

    fun hasUsageStatsPermission(): Boolean {
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

    fun openUsageStatsSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun getUsageMinutesToday(packageName: String): Int {
        if (!hasUsageStatsPermission()) return 10 // Valore simulato di default se il permesso non è concesso

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            val appStat = stats.find { it.packageName == packageName }
            val millis = appStat?.totalTimeInForeground ?: 0L
            (millis / (1000 * 60)).toInt()
        } catch (e: Exception) {
            0
        }
    }

    fun updateLimit(packageName: String, newLimitMinutes: Int) {
        val updated = _data.value.blockedApps.map {
            if (it.packageName == packageName) it.copy(dailyLimitMinutes = newLimitMinutes) else it
        }
        saveData(_data.value.copy(blockedApps = updated))
    }

    fun toggleAppBlock(packageName: String) {
        val updated = _data.value.blockedApps.map {
            if (it.packageName == packageName) it.copy(enabled = !it.enabled) else it
        }
        saveData(_data.value.copy(blockedApps = updated))
    }

    fun toggleMasterBlock(enabled: Boolean) {
        saveData(_data.value.copy(enabled = enabled))
    }

    fun addCustomApp(packageName: String, appName: String, limit: Int) {
        val newApp = AppBlock(packageName, appName, limit, true)
        saveData(_data.value.copy(blockedApps = _data.value.blockedApps + newApp))
    }
}
