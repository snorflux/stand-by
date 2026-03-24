package com.snorflux.dockedmode.standby

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Surface
import com.snorflux.dockedmode.StandbyActivity
import com.snorflux.dockedmode.notifications.StandbyNotificationListenerService

object StandbyModeController {
    private const val LAUNCH_COOLDOWN_MS = 5_000L

    @Volatile
    private var lastLaunchAt = 0L

    fun maybeLaunch(context: Context) {
        if (!shouldLaunch(context)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAt < LAUNCH_COOLDOWN_MS) return
        lastLaunchAt = now

        openStandby(context)
    }

    fun openStandby(context: Context) {
        val intent = Intent(context, StandbyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        runCatching {
            context.startActivity(intent)
        }
    }

    fun shouldLaunch(context: Context): Boolean {
        // Auto-launch whenever dock-like conditions are met.
        return isReadyForManualLaunch(context)
    }

    fun isReadyForManualLaunch(context: Context): Boolean {
        return isCharging(context) && isLandscape(context)
    }

    fun isCharging(context: Context): Boolean {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return false

        val chargeState = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return chargeState == BatteryManager.BATTERY_STATUS_CHARGING ||
            chargeState == BatteryManager.BATTERY_STATUS_FULL
    }

    fun isLandscape(context: Context): Boolean {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val rotation = displayManager
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            return true
        }
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            return false
        }

        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val targetClassName = StandbyNotificationListenerService::class.java.name
        return enabledListeners
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == context.packageName && it.className == targetClassName }
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
