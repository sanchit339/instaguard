package com.instaguard.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> UpdateWorkScheduler.schedule(context)
        }
    }
}
