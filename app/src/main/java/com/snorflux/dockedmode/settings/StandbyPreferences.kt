package com.snorflux.dockedmode.settings

import android.content.Context

data class StandbyUiSettings(
    val showBatteryInfo: Boolean,
    val showNextAlarm: Boolean,
    val enableBurnInProtection: Boolean,
    val showDndIndicator: Boolean,
    val enableAutoDim: Boolean,
    val showMediaControls: Boolean
)

object StandbyPreferences {
    private const val PREFS_NAME = "standby_settings"

    private const val KEY_SHOW_BATTERY_INFO = "show_battery_info"
    private const val KEY_SHOW_NEXT_ALARM = "show_next_alarm"
    private const val KEY_ENABLE_BURN_IN_PROTECTION = "enable_burn_in_protection"
    private const val KEY_SHOW_DND_INDICATOR = "show_dnd_indicator"
    private const val KEY_ENABLE_AUTO_DIM = "enable_auto_dim"
    private const val KEY_SHOW_MEDIA_CONTROLS = "show_media_controls"

    fun read(context: Context): StandbyUiSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return StandbyUiSettings(
            showBatteryInfo = prefs.getBoolean(KEY_SHOW_BATTERY_INFO, true),
            showNextAlarm = prefs.getBoolean(KEY_SHOW_NEXT_ALARM, true),
            enableBurnInProtection = prefs.getBoolean(KEY_ENABLE_BURN_IN_PROTECTION, true),
            showDndIndicator = prefs.getBoolean(KEY_SHOW_DND_INDICATOR, true),
            enableAutoDim = prefs.getBoolean(KEY_ENABLE_AUTO_DIM, false),
            showMediaControls = prefs.getBoolean(KEY_SHOW_MEDIA_CONTROLS, false)
        )
    }

    fun setShowBatteryInfo(context: Context, enabled: Boolean) {
        write(context, KEY_SHOW_BATTERY_INFO, enabled)
    }

    fun setShowNextAlarm(context: Context, enabled: Boolean) {
        write(context, KEY_SHOW_NEXT_ALARM, enabled)
    }

    fun setEnableBurnInProtection(context: Context, enabled: Boolean) {
        write(context, KEY_ENABLE_BURN_IN_PROTECTION, enabled)
    }

    fun setShowDndIndicator(context: Context, enabled: Boolean) {
        write(context, KEY_SHOW_DND_INDICATOR, enabled)
    }

    fun setEnableAutoDim(context: Context, enabled: Boolean) {
        write(context, KEY_ENABLE_AUTO_DIM, enabled)
    }

    fun setShowMediaControls(context: Context, enabled: Boolean) {
        write(context, KEY_SHOW_MEDIA_CONTROLS, enabled)
    }

    private fun write(context: Context, key: String, enabled: Boolean) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, enabled)
            .apply()
    }
}
