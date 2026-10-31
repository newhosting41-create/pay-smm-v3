package com.smmpaysync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restart retry infrastructure after phone reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Prefs(app).note("BOOT received: $a")
            try { MonitorService.ensure(app) } catch (e: Exception) { Prefs(app).note("BOOT monitor failed: ${e.message}") }
            SyncWorker.schedulePeriodic(app)
        }
    }
}
