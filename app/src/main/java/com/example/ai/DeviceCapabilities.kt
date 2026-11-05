package com.example.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceCapabilities(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowRamDevice: Boolean,
    val cpuCores: Int,
    val apiLevel: Int
)

fun getDeviceCapabilities(context: Context): DeviceCapabilities {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    return DeviceCapabilities(
        totalRamBytes = memoryInfo.totalMem,
        availableRamBytes = memoryInfo.availMem,
        isLowRamDevice = activityManager.isLowRamDevice,
        cpuCores = Runtime.getRuntime().availableProcessors(),
        apiLevel = Build.VERSION.SDK_INT
    )
}

enum class AiStrategy {
    LIGHT,
    BALANCED,
    ADVANCED
}

fun determineAiStrategy(capabilities: DeviceCapabilities): AiStrategy {
    val gbRam = capabilities.totalRamBytes / (1024 * 1024 * 1024)
    return when {
        capabilities.isLowRamDevice || gbRam <= 3 -> AiStrategy.LIGHT
        gbRam in 4..6 -> AiStrategy.BALANCED
        else -> AiStrategy.ADVANCED
    }
}
