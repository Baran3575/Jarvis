package com.baran.jarvis

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Receives the RUN_COMMAND result PendingIntent from Termux and forwards it
 * to [TermuxSession.deliver].
 */
class ResultService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reqId = intent?.getIntExtra("reqId", -1) ?: -1
        val bundle = intent?.getBundleExtra("com.termux.service.extra.PLUGIN_RESULT_BUNDLE")
        if (reqId != -1) {
            TermuxSession.deliver(reqId, TermuxSession.readBundle(bundle))
        }
        return START_NOT_STICKY
    }
}
