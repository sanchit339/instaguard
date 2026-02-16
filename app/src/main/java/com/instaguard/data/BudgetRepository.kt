package com.instaguard.data

import android.content.Context
import com.instaguard.domain.BudgetEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BudgetRepository(context: Context) {
    private val store = BudgetStore(context.applicationContext)
    private val lock = Mutex()

    suspend fun tick(isConsuming: Boolean, nowEpochMs: Long = System.currentTimeMillis()): BudgetSnapshot {
        return lock.withLock {
            val current = store.readOnce()
            val next = BudgetEngine.advance(current, nowEpochMs, isConsuming)
            store.write(next)
            next
        }
    }

    suspend fun current(): BudgetSnapshot {
        return lock.withLock { store.readOnce() }
    }

    fun observe() = store.snapshotFlow
}
