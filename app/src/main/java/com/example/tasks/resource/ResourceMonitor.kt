package com.example.tasks.resource

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PerformancePreset {
    BATTERY_SAVER,
    BALANCED,
    PERFORMANCE
}

data class ResourceSnapshot(
    val availableRamMb: Long,
    val totalRamMb: Long,
    val isLowMemory: Boolean,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean,
    val thermalStatus: String,
    val performancePreset: PerformancePreset,
    val maxConcurrentHeavyTasks: Int,
    val maxConcurrentLightTasks: Int
)

class ResourceMonitor(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _preset = MutableStateFlow(PerformancePreset.BALANCED)
    val preset: StateFlow<PerformancePreset> = _preset.asStateFlow()

    private val _currentSnapshot = MutableStateFlow(captureSnapshot())
    val currentSnapshot: StateFlow<ResourceSnapshot> = _currentSnapshot.asStateFlow()

    fun setPerformancePreset(newPreset: PerformancePreset) {
        _preset.value = newPreset
        refresh()
    }

    fun refresh(): ResourceSnapshot {
        val snapshot = captureSnapshot()
        _currentSnapshot.value = snapshot
        return snapshot
    }

    private fun captureSnapshot(): ResourceSnapshot {
        // Memory
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val isLowMem = memInfo.lowMemory || (availMb < 200)

        // Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Power save
        val isPowerSave = powerManager.isPowerSaveMode

        // Thermal
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "NORMAL"
            }
        } else {
            "NORMAL"
        }

        // Concurrency limits based on preset & device state
        val (maxHeavy, maxLight) = when (_preset.value) {
            PerformancePreset.BATTERY_SAVER -> {
                Pair(1, 1)
            }
            PerformancePreset.BALANCED -> {
                if (isLowMem || isPowerSave || batteryPct < 15) {
                    Pair(1, 1)
                } else {
                    Pair(1, 3)
                }
            }
            PerformancePreset.PERFORMANCE -> {
                if (isLowMem) {
                    Pair(1, 2)
                } else {
                    Pair(1, 5)
                }
            }
        }

        return ResourceSnapshot(
            availableRamMb = availMb,
            totalRamMb = totalMb,
            isLowMemory = isLowMem,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSave,
            thermalStatus = thermal,
            performancePreset = _preset.value,
            maxConcurrentHeavyTasks = maxHeavy,
            maxConcurrentLightTasks = maxLight
        )
    }
}
