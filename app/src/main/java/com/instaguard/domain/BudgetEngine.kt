package com.instaguard.domain

import com.instaguard.data.BudgetSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

object BudgetEngine {
    private const val BUDGET_PER_HOUR_MS = 5 * 60 * 1000L
    private const val HOUR_MS = 60 * 60 * 1000L

    fun advance(
        snapshot: BudgetSnapshot,
        nowEpochMs: Long,
        isConsuming: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BudgetSnapshot {
        if (nowEpochMs <= snapshot.lastUpdatedEpochMs) {
            return snapshot
        }

        val elapsedMs = nowEpochMs - snapshot.lastUpdatedEpochMs
        val accrualEligibleMs = calculateAccrualEligibleMs(
            startEpochMs = snapshot.lastUpdatedEpochMs,
            endEpochMs = nowEpochMs,
            zoneId = zoneId
        )
        val earnedMs = (accrualEligibleMs * BUDGET_PER_HOUR_MS) / HOUR_MS

        val adjusted = if (isConsuming) {
            snapshot.balanceMs + earnedMs - elapsedMs
        } else {
            snapshot.balanceMs + earnedMs
        }

        return BudgetSnapshot(
            balanceMs = max(0L, adjusted),
            lastUpdatedEpochMs = nowEpochMs
        )
    }

    fun millisToNextSecondOfBudget(snapshot: BudgetSnapshot): Long {
        return if (snapshot.balanceMs > 0L) 0L else 12_000L
    }

    private fun calculateAccrualEligibleMs(
        startEpochMs: Long,
        endEpochMs: Long,
        zoneId: ZoneId
    ): Long {
        var cursor = startEpochMs
        var eligibleMs = 0L

        while (cursor < endEpochMs) {
            val currentZoned = Instant.ofEpochMilli(cursor).atZone(zoneId)
            val date = currentZoned.toLocalDate()
            val nextDayStartMs = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val segmentEnd = min(endEpochMs, nextDayStartMs)
            val segmentDuration = segmentEnd - cursor

            val quietStartMs = date.atTime(2, 0).atZone(zoneId).toInstant().toEpochMilli()
            val quietEndMs = date.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()
            val quietOverlap = overlapMs(
                aStart = cursor,
                aEnd = segmentEnd,
                bStart = quietStartMs,
                bEnd = quietEndMs
            )

            eligibleMs += max(0L, segmentDuration - quietOverlap)
            cursor = segmentEnd
        }

        return eligibleMs
    }

    private fun overlapMs(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long {
        val start = max(aStart, bStart)
        val end = min(aEnd, bEnd)
        return max(0L, end - start)
    }
}
