package com.instaguard.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class InstagramUsageStatsReader(context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun queryForegroundMs(startEpochMs: Long, endEpochMs: Long, targetPackage: String): Long {
        if (endEpochMs <= startEpochMs) {
            return 0L
        }
        return try {
            queryForegroundMsUnsafe(startEpochMs, endEpochMs, targetPackage)
        } catch (_: SecurityException) {
            0L
        }
    }

    private fun queryForegroundMsUnsafe(startEpochMs: Long, endEpochMs: Long, targetPackage: String): Long {
        val events = usageStatsManager.queryEvents(startEpochMs, endEpochMs)
        val event = UsageEvents.Event()

        var foregroundSince: Long? = null
        var totalForegroundMs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != targetPackage) {
                continue
            }
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    foregroundSince = event.timeStamp
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val startedAt = foregroundSince
                    if (startedAt != null && event.timeStamp > startedAt) {
                        totalForegroundMs += event.timeStamp - startedAt
                    }
                    foregroundSince = null
                }
            }
        }

        val openSessionStart = foregroundSince
        if (openSessionStart != null && endEpochMs > openSessionStart) {
            totalForegroundMs += endEpochMs - openSessionStart
        }

        return totalForegroundMs
    }
}
