package com.instaguard

import com.instaguard.data.BudgetSnapshot
import com.instaguard.domain.BudgetEngine
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetEngineTest {
    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun initialBudget_isFiveMinutesOutsideQuietHours() {
        // 2026-01-01T10:10:00Z
        val snapshot = BudgetEngine.createInitialSnapshot(1_767_615_000_000L, utc)
        assertEquals(300_000L, snapshot.balanceMs)
        assertEquals(300_000L, snapshot.hourAllowanceMs)
    }

    @Test
    fun initialBudget_isZeroInsideQuietHours() {
        // 2026-01-01T03:10:00Z
        val snapshot = BudgetEngine.createInitialSnapshot(1_767_589_800_000L, utc)
        assertEquals(0L, snapshot.balanceMs)
        assertEquals(0L, snapshot.hourAllowanceMs)
    }

    @Test
    fun nextHour_appliesMaxOneMinuteCarry() {
        val start = BudgetSnapshot(
            balanceMs = 220_000L,
            hourAllowanceMs = 300_000L,
            // 2026-01-01T10:00:00Z
            currentHourStartEpochMs = 1_767_614_400_000L,
            // 2026-01-01T10:59:59Z
            lastUpdatedEpochMs = 1_767_617_999_000L
        )

        val rolled = BudgetEngine.rollForward(
            snapshot = start,
            // 2026-01-01T11:00:00Z
            nowEpochMs = 1_767_618_000_000L,
            zoneId = utc
        )

        assertEquals(360_000L, rolled.hourAllowanceMs)
        assertEquals(360_000L, rolled.balanceMs)
    }

    @Test
    fun quietHour_hasNoRefillAndNoCarry() {
        val start = BudgetSnapshot(
            balanceMs = 100_000L,
            hourAllowanceMs = 300_000L,
            // 2026-01-01T01:00:00Z
            currentHourStartEpochMs = 1_767_582_800_000L,
            // 2026-01-01T01:59:59Z
            lastUpdatedEpochMs = 1_767_586_399_000L
        )

        val rolled = BudgetEngine.rollForward(
            snapshot = start,
            // 2026-01-01T02:00:00Z
            nowEpochMs = 1_767_586_400_000L,
            zoneId = utc
        )

        assertEquals(0L, rolled.hourAllowanceMs)
        assertEquals(0L, rolled.balanceMs)
    }

    @Test
    fun consume_deductsFromBalance() {
        val start = BudgetSnapshot(
            balanceMs = 300_000L,
            hourAllowanceMs = 300_000L,
            currentHourStartEpochMs = 1_767_614_400_000L,
            lastUpdatedEpochMs = 1_767_614_400_000L
        )

        val next = BudgetEngine.consume(start, consumedMs = 54_000L, nowEpochMs = 1_767_614_454_000L)
        assertEquals(246_000L, next.balanceMs)
    }
}
