package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i("BootReceiver", "Received broadcast action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (KyoshinBackgroundService.isEnabled(context)) {
                Log.i("BootReceiver", "Starting KyoshinBackgroundService from boot/update receiver")
                KyoshinBackgroundService.start(context)
            }
        }
    }
}
