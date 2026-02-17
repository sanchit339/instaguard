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
                val nextDelayMs = runCatching { processTick(now) }.getOrDefault(IDLE_TICK_MS)
                scheduleNextTick(nextDelayMs)
            }
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

        if (instagramForeground) {
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
            scope.launch {
                val snapshot = repository.refresh(System.currentTimeMillis())
                if (snapshot.balanceMs <= 0L) {
                    enforceBlock()
                }
            }
        } else {
            blockerVisible = false
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        scope.cancel()
        super.onDestroy()
    }

    private fun scheduleNextTick(delayMs: Long) {
        handler.postDelayed(tickRunnable, delayMs)
    }

    private suspend fun processTick(nowEpochMs: Long): Long {
        val tickDeltaMs = max(0L, nowEpochMs - lastTickEpochMs)
        lastTickEpochMs = nowEpochMs

        var snapshot = repository.refresh(nowEpochMs)

        if (instagramForeground && tickDeltaMs > 0L) {
            snapshot = repository.consume(consumedMs = tickDeltaMs, nowEpochMs = nowEpochMs)
            foregroundCountedSinceReconcileMs += tickDeltaMs
        }

        val reconcileInterval = if (instagramForeground) ACTIVE_RECONCILE_INTERVAL_MS else IDLE_RECONCILE_INTERVAL_MS
        if (nowEpochMs - lastReconcileEpochMs >= reconcileInterval) {
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

        val usageSaysInstagramActive = usageStatsReader.isAppCurrentlyForeground(
            nowEpochMs = nowEpochMs,
            targetPackage = INSTAGRAM_PACKAGE
        )
        val shouldBlockNow = snapshot.balanceMs <= 0L && (instagramForeground || usageSaysInstagramActive)

        if (shouldBlockNow) {
            enforceBlock()
            return BLOCKED_TICK_MS
        }

        return if (instagramForeground) ACTIVE_TICK_MS else IDLE_TICK_MS
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
        private const val ACTIVE_TICK_MS = 1_000L
        private const val IDLE_TICK_MS = 5_000L
        private const val BLOCKED_TICK_MS = 2_000L
        private const val ACTIVE_RECONCILE_INTERVAL_MS = 30_000L
        private const val IDLE_RECONCILE_INTERVAL_MS = 60_000L
    }
}
