package com.instaguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.instaguard.BlockActivity
import com.instaguard.data.BudgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

class InstaGuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var repository: BudgetRepository
    private lateinit var usageStatsReader: InstagramUsageStatsReader

    private var instagramForeground = false
    private var blockerVisible = false
    private var lastTickEpochMs = 0L
    private var lastReconcileEpochMs = 0L
    private var foregroundCountedSinceReconcileMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            scope.launch {
                processTick(now)
            }

            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = BudgetRepository(applicationContext)
        usageStatsReader = InstagramUsageStatsReader(applicationContext)

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

        val now = System.currentTimeMillis()
        lastTickEpochMs = now
        lastReconcileEpochMs = now
        foregroundCountedSinceReconcileMs = 0L

        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        instagramForeground = pkg == INSTAGRAM_PACKAGE
        if (!instagramForeground) {
            blockerVisible = false
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun processTick(nowEpochMs: Long) {
        val tickDeltaMs = max(0L, nowEpochMs - lastTickEpochMs)
        lastTickEpochMs = nowEpochMs

        var snapshot = repository.refresh(nowEpochMs)

        if (instagramForeground && tickDeltaMs > 0L) {
            snapshot = repository.consume(consumedMs = tickDeltaMs, nowEpochMs = nowEpochMs)
            foregroundCountedSinceReconcileMs += tickDeltaMs
        }

        if (nowEpochMs - lastReconcileEpochMs >= RECONCILE_INTERVAL_MS) {
            val observedForegroundMs = usageStatsReader.queryForegroundMs(
                startEpochMs = lastReconcileEpochMs,
                endEpochMs = nowEpochMs,
                targetPackage = INSTAGRAM_PACKAGE
            )
            val uncountedMs = max(0L, observedForegroundMs - foregroundCountedSinceReconcileMs)
            if (uncountedMs > 0L) {
                snapshot = repository.consume(consumedMs = uncountedMs, nowEpochMs = nowEpochMs)
            }
            lastReconcileEpochMs = nowEpochMs
            foregroundCountedSinceReconcileMs = 0L
        }

        val usageSaysInstagramActive = usageStatsReader.queryForegroundMs(
            startEpochMs = max(0L, nowEpochMs - FOREGROUND_FALLBACK_WINDOW_MS),
            endEpochMs = nowEpochMs,
            targetPackage = INSTAGRAM_PACKAGE
        ) > 0L
        val shouldBlockNow = snapshot.balanceMs <= 0L && (instagramForeground || usageSaysInstagramActive)

        if (shouldBlockNow) {
            enforceBlock()
        }
    }

    private fun enforceBlock() {
        if (!blockerVisible) {
            blockerVisible = true
            val intent = Intent(this, BlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val TICK_MS = 1_000L
        private const val RECONCILE_INTERVAL_MS = 60_000L
        private const val FOREGROUND_FALLBACK_WINDOW_MS = 3_000L
    }
}
