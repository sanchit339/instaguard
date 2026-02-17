package com.instaguard

import android.app.Application
import com.instaguard.update.UpdateWorkScheduler

class InstaGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateWorkScheduler.schedule(this)
    }
}
