package com.snorflux.dockedmode.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class StandbyTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_CONFIGURATION_CHANGED,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_USER_PRESENT,
            PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED -> {
                StandbyMonitorService.syncWithPowerState(context)
                StandbyModeController.maybeLaunch(context)
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                StandbyMonitorService.syncWithPowerState(context)
            }
        }
    }
}
