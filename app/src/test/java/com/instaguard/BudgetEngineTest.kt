package com.instaguard

import com.instaguard.data.BudgetSnapshot
import com.instaguard.domain.BudgetEngine
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetEngineTest {
    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun accrue_whenNotUsing() {
        val start = BudgetSnapshot(
            balanceMs = 0,
            lastUpdatedEpochMs = 1_000L
        )

        val afterHour = BudgetEngine.advance(
            start,
            nowEpochMs = 3_601_000L,
            isConsuming = false,
            zoneId = utc
        )
        assertEquals(300_000L, afterHour.balanceMs)
    }

    @Test
    fun consume_whenUsing() {
        val start = BudgetSnapshot(
            balanceMs = 300_000L,
            lastUpdatedEpochMs = 10_000L
        )

        val afterMinute = BudgetEngine.advance(
            start,
            nowEpochMs = 70_000L,
            isConsuming = true,
            zoneId = utc
        )
        assertEquals(245_000L, afterMinute.balanceMs)
    }

    @Test
    fun noAccrual_duringQuietHours() {
        val start = BudgetSnapshot(
            balanceMs = 0L,
            // 2026-01-01T02:00:00Z
            lastUpdatedEpochMs = 1_767_232_800_000L
        )

        val result = BudgetEngine.advance(
            snapshot = start,
            // 2026-01-01T08:00:00Z
            nowEpochMs = 1_767_254_400_000L,
            isConsuming = false,
            zoneId = utc
        )

        assertEquals(0L, result.balanceMs)
    }

    @Test
    fun accrual_onlyOutsideQuietHours_whenCrossingBoundary() {
        val start = BudgetSnapshot(
            balanceMs = 0L,
            // 2026-01-01T01:00:00Z
            lastUpdatedEpochMs = 1_767_229_200_000L
        )

        val result = BudgetEngine.advance(
            snapshot = start,
            // 2026-01-01T03:00:00Z
            nowEpochMs = 1_767_236_400_000L,
            isConsuming = false,
            zoneId = utc
        )

        // Only 01:00-02:00 accrues; 02:00-03:00 is quiet time.
        assertEquals(300_000L, result.balanceMs)
    }
}
