package com.instaguard.data

import android.content.Context
import com.instaguard.domain.BudgetEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BudgetRepository(context: Context) {
    private val store = BudgetStore(context.applicationContext)
    private val lock = Mutex()

    suspend fun refresh(nowEpochMs: Long = System.currentTimeMillis()): BudgetSnapshot {
        return lock.withLock {
            val current = store.readOnce()
            val rolled = BudgetEngine.rollForward(current, nowEpochMs)
            if (rolled != current) {
                store.write(rolled)
            }
            rolled
        }
    }

    suspend fun consume(consumedMs: Long, nowEpochMs: Long = System.currentTimeMillis()): BudgetSnapshot {
        return lock.withLock {
            val current = store.readOnce()
            val rolled = BudgetEngine.rollForward(current, nowEpochMs)
            val next = BudgetEngine.consume(rolled, consumedMs, nowEpochMs)
            store.write(next)
            next
        }
    }

    suspend fun current(): BudgetSnapshot {
        return lock.withLock { store.readOnce() }
    }

    fun observe() = store.snapshotFlow
}
