package com.snorflux.dockedmode.standby

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Surface
import com.snorflux.dockedmode.StandbyActivity
import com.snorflux.dockedmode.notifications.StandbyNotificationListenerService

object StandbyModeController {
    private const val TAG = "StandbyModeController"
    private const val LAUNCH_COOLDOWN_MS = 5_000L

    @Volatile
    private var lastLaunchAt = 0L

    @Volatile
    var isPhysicallyLandscape: Boolean = false

    @Volatile
    var isStandbyActive: Boolean = false

    fun maybeLaunch(context: Context) {
        if (isStandbyActive) return
        if (!shouldLaunch(context)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAt < LAUNCH_COOLDOWN_MS) return
        lastLaunchAt = now

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        
        val isLocked = keyguardManager.isKeyguardLocked
        val isInteractive = powerManager.isInteractive

        // If the user is actively using the device (unlocked and screen on),
        // we should NEVER interrupt them or spam notifications, even if they are in our app.
        // Standby is strictly for when the device is idle/locked.
        if (isInteractive && !isLocked) {
            return
        }

        // To launch over the lockscreen from the background, we must use a Full-Screen Intent notification.
        StandbyLaunchNotifier.show(context)
    }

    fun openStandby(context: Context): Boolean {
        val intent = Intent(context, StandbyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Direct standby launch failed; fallback to full-screen notification", error)
            false
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
        if (isPhysicallyLandscape) return true

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

    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.canUseFullScreenIntent() == true
        } else {
            true // Auto-granted before Android 14
        }
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
