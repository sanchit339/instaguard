package com.instaguard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.instaguard.domain.BudgetEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "instaguard_budget"
private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

data class BudgetSnapshot(
    val balanceMs: Long,
    val lastUpdatedEpochMs: Long,
    val currentHourStartEpochMs: Long,
    val hourAllowanceMs: Long
)

class BudgetStore(private val context: Context) {

    private object Keys {
        val balanceMs: Preferences.Key<Long> = longPreferencesKey("balance_ms")
        val lastUpdatedMs: Preferences.Key<Long> = longPreferencesKey("last_updated_ms")
        val currentHourStartMs: Preferences.Key<Long> = longPreferencesKey("current_hour_start_ms")
        val hourAllowanceMs: Preferences.Key<Long> = longPreferencesKey("hour_allowance_ms")
    }

    val snapshotFlow: Flow<BudgetSnapshot> = context.dataStore.data.map { prefs ->
        toSnapshotOrDefault(prefs)
    }

    suspend fun readOnce(): BudgetSnapshot {
        val prefs = context.dataStore.data.first()
        return toSnapshotOrDefault(prefs)
    }

    suspend fun write(snapshot: BudgetSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.balanceMs] = snapshot.balanceMs
            prefs[Keys.lastUpdatedMs] = snapshot.lastUpdatedEpochMs
            prefs[Keys.currentHourStartMs] = snapshot.currentHourStartEpochMs
            prefs[Keys.hourAllowanceMs] = snapshot.hourAllowanceMs
        }
    }

    private fun toSnapshotOrDefault(prefs: Preferences): BudgetSnapshot {
        val now = System.currentTimeMillis()
        val lastUpdated = prefs[Keys.lastUpdatedMs] ?: now
        val hourStart = prefs[Keys.currentHourStartMs]
        val allowance = prefs[Keys.hourAllowanceMs]
        val balance = prefs[Keys.balanceMs]

        if (hourStart == null || allowance == null || balance == null) {
            return BudgetEngine.createInitialSnapshot(now)
        }

        return BudgetSnapshot(
            balanceMs = balance,
            lastUpdatedEpochMs = lastUpdated,
            currentHourStartEpochMs = hourStart,
            hourAllowanceMs = allowance
        )
    }
}
