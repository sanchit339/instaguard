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
        val queryStart = maxOf(0L, startEpochMs - LOOKBACK_MS)
        val events = usageStatsManager.queryEvents(queryStart, endEpochMs)
        val event = UsageEvents.Event()

        var foregroundSince: Long? = null
        var totalForegroundMs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != targetPackage) {
                continue
            }

            if (isForegroundEvent(event.eventType)) {
                if (foregroundSince == null) {
                    foregroundSince = event.timeStamp
                }
                continue
            }

            if (isBackgroundEvent(event.eventType)) {
                val startedAt = foregroundSince
                if (startedAt != null && event.timeStamp > startedAt) {
                    totalForegroundMs += overlapMs(startedAt, event.timeStamp, startEpochMs, endEpochMs)
                }
                foregroundSince = null
            }
        }

        val openSessionStart = foregroundSince
        if (openSessionStart != null && endEpochMs > openSessionStart) {
            totalForegroundMs += overlapMs(openSessionStart, endEpochMs, startEpochMs, endEpochMs)
        }

        return totalForegroundMs
    }

    private fun isForegroundEvent(eventType: Int): Boolean {
        return eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
    }

    private fun isBackgroundEvent(eventType: Int): Boolean {
        return eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
    }

    private fun overlapMs(start: Long, end: Long, windowStart: Long, windowEnd: Long): Long {
        val boundedStart = maxOf(start, windowStart)
        val boundedEnd = minOf(end, windowEnd)
        return maxOf(0L, boundedEnd - boundedStart)
    }

    private companion object {
        const val LOOKBACK_MS = 30 * 60 * 1000L
    }
}
