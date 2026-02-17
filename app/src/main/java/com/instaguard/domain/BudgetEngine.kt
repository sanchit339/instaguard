package com.instaguard.domain

import com.instaguard.data.BudgetSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

object BudgetEngine {
    const val BASE_HOURLY_BUDGET_MS = 5 * 60 * 1000L
    const val MAX_CARRY_MS = 60 * 1000L
    private const val HOUR_MS = 60 * 60 * 1000L

    fun createInitialSnapshot(nowEpochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): BudgetSnapshot {
        val hourStart = startOfHour(nowEpochMs, zoneId)
        val quiet = isQuietHour(hourStart, zoneId)
        val allowance = if (quiet) 0L else BASE_HOURLY_BUDGET_MS
        return BudgetSnapshot(
            balanceMs = allowance,
            lastUpdatedEpochMs = nowEpochMs,
            currentHourStartEpochMs = hourStart,
            hourAllowanceMs = allowance
        )
    }

    fun rollForward(
        snapshot: BudgetSnapshot,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BudgetSnapshot {
        if (nowEpochMs <= snapshot.lastUpdatedEpochMs) {
            return snapshot
        }

        var current = snapshot
        while (nowEpochMs >= current.currentHourStartEpochMs + HOUR_MS) {
            val hourStart = current.currentHourStartEpochMs
            val hourIsQuiet = isQuietHour(hourStart, zoneId)
            val carryToNext = if (hourIsQuiet) 0L else min(current.balanceMs, MAX_CARRY_MS)

            val nextHourStart = hourStart + HOUR_MS
            val nextIsQuiet = isQuietHour(nextHourStart, zoneId)
            val nextAllowance = if (nextIsQuiet) 0L else BASE_HOURLY_BUDGET_MS + carryToNext
            val nextBalance = if (nextIsQuiet) 0L else nextAllowance

            current = current.copy(
                balanceMs = nextBalance,
                lastUpdatedEpochMs = nextHourStart,
                currentHourStartEpochMs = nextHourStart,
                hourAllowanceMs = nextAllowance
            )
        }

        return current.copy(lastUpdatedEpochMs = nowEpochMs)
    }

    fun consume(snapshot: BudgetSnapshot, consumedMs: Long, nowEpochMs: Long): BudgetSnapshot {
        if (consumedMs <= 0L) {
            return snapshot.copy(lastUpdatedEpochMs = nowEpochMs)
        }
        return snapshot.copy(
            balanceMs = max(0L, snapshot.balanceMs - consumedMs),
            lastUpdatedEpochMs = nowEpochMs
        )
    }

    private fun startOfHour(epochMs: Long, zoneId: ZoneId): Long {
        val zoned = Instant.ofEpochMilli(epochMs).atZone(zoneId)
        return zoned
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
    }

    private fun isQuietHour(hourStartEpochMs: Long, zoneId: ZoneId): Boolean {
        val localHour = Instant.ofEpochMilli(hourStartEpochMs).atZone(zoneId).hour
        return localHour in 2..7
    }
}
