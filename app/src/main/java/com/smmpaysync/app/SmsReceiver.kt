package com.smmpaysync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.concurrent.Executors

/**
 * Diagnostic SMS receiver.
 * Persists the SMS immediately and keeps a short goAsync() window for the first
 * sync attempt. Foreground service is only an additional execution path.
 */
class SmsReceiver : BroadcastReceiver() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        executor.execute {
            try {
                val prefs = Prefs(app)
                prefs.note("1 SMS_RECEIVED broadcast")
                if (!prefs.monitor) { prefs.note("STOP monitor is OFF"); return@execute }

                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (msgs.isNullOrEmpty()) { prefs.note("STOP no SMS parts"); return@execute }
                val from = msgs.first().originatingAddress ?: ""
                val provider = Providers.byHeader(from)
                if (provider == null) { prefs.note("STOP unknown sender: $from"); return@execute }
                if (!prefs.providerEnabled(provider)) { prefs.note("STOP provider disabled: ${provider.key}"); return@execute }
                val body = msgs.joinToString("") { it.messageBody ?: "" }
                prefs.note("2 RECEIVER OK sender=$from provider=${provider.key}")

                // Save first. This must succeed even if Android refuses an FGS start.
                SmsIngest.handle(app, provider, body, sendInline = false)
                prefs.note("3 SMS SAVED; immediate sync starting")

                // First attempt immediately while the SMS broadcast is still alive.
                if (UiUtil.isOnline(app)) {
                    val out = SyncEngine.run(app)
                    prefs.note("4 API SYNC outcome=$out result=${prefs.lastResult}")
                } else {
                    prefs.note("4 OFFLINE; queued for retry")
                }

                // Try foreground path as an additional reliability mechanism; failure is logged.
                try {
                    SmsForegroundService.enqueueSms(app, provider.key, body)
                    prefs.note("5 FGS START requested")
                } catch (e: Exception) {
                    prefs.note("5 FGS START FAILED: ${e.javaClass.simpleName}: ${e.message}")
                }
                SyncWorker.enqueue(app)
            } catch (e: Exception) {
                Prefs(app).note("RECEIVER ERROR ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
