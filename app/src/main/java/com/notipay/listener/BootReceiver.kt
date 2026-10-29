package com.notipay.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs.isServiceEnabled(context)) {
            KeeperService.start(context)
        }
    }
}
