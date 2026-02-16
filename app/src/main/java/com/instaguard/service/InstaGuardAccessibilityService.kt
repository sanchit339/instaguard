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

class InstaGuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var repository: BudgetRepository
    private var instagramForeground = false
    private var blockerVisible = false

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            scope.launch {
                val snapshot = repository.tick(isConsuming = instagramForeground)
                if (instagramForeground && snapshot.balanceMs <= 0L) {
                    enforceBlock()
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = BudgetRepository(applicationContext)

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

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
    }
}
