package com.vkturn.proxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class ProxyReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_START = "com.vkturn.proxy.action.START"
        const val ACTION_STOP = "com.vkturn.proxy.action.STOP"
        private const val TAG = "ProxyReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received action: $action")

        when (action) {
            ACTION_START -> {
                Log.d(TAG, "Starting proxy service via intent")
                val serviceIntent = Intent(context, ProxyService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping proxy service via intent")
                val serviceIntent = Intent(context, ProxyService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
