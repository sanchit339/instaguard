package com.instaguard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "instaguard_budget"
private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

data class BudgetSnapshot(
    val balanceMs: Long,
    val lastUpdatedEpochMs: Long
)

class BudgetStore(private val context: Context) {

    private object Keys {
        val balanceMs: Preferences.Key<Long> = longPreferencesKey("balance_ms")
        val lastUpdatedMs: Preferences.Key<Long> = longPreferencesKey("last_updated_ms")
    }

    val snapshotFlow: Flow<BudgetSnapshot> = context.dataStore.data.map { prefs ->
        BudgetSnapshot(
            balanceMs = prefs[Keys.balanceMs] ?: DEFAULT_INITIAL_BUDGET_MS,
            lastUpdatedEpochMs = prefs[Keys.lastUpdatedMs] ?: System.currentTimeMillis()
        )
    }

    suspend fun readOnce(): BudgetSnapshot {
        val prefs = context.dataStore.data.first()
        return BudgetSnapshot(
            balanceMs = prefs[Keys.balanceMs] ?: DEFAULT_INITIAL_BUDGET_MS,
            lastUpdatedEpochMs = prefs[Keys.lastUpdatedMs] ?: System.currentTimeMillis()
        )
    }

    suspend fun write(snapshot: BudgetSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.balanceMs] = snapshot.balanceMs
            prefs[Keys.lastUpdatedMs] = snapshot.lastUpdatedEpochMs
        }
    }

    companion object {
        const val DEFAULT_INITIAL_BUDGET_MS: Long = 5 * 60 * 1000L
    }
}
