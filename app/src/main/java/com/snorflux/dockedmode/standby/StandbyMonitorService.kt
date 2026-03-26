package com.snorflux.dockedmode.standby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.hardware.SensorManager
import android.view.OrientationEventListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.snorflux.dockedmode.R

class StandbyMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var orientationListener: OrientationEventListener? = null
    
    private val pollRunnable = object : Runnable {
        override fun run() {
            StandbyModeController.maybeLaunch(applicationContext)

            if (StandbyModeController.isCharging(applicationContext)) {
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            } else {
                stopSelf() // Automatically stop
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification())

        orientationListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val isLandscape = (orientation in 60..120) || (orientation in 240..300)
                StandbyModeController.isPhysicallyLandscape = isLandscape
            }
        }
        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                stopSelf()
            }

            else -> startMonitoring()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        orientationListener?.disable()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        handler.removeCallbacks(pollRunnable)
        pollRunnable.run()
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(pollRunnable)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Standby Monitor",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Monitors charging and landscape state for standby launch"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Monitoring standby conditions")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val ACTION_START_MONITORING = "com.snorflux.dockedmode.action.START_MONITORING"
        private const val ACTION_STOP_MONITORING = "com.snorflux.dockedmode.action.STOP_MONITORING"
        private const val CHANNEL_ID = "standby_monitor"
        private const val NOTIFICATION_ID = 1101
        private const val CHECK_INTERVAL_MS = 5_000L

        fun syncWithPowerState(context: Context) {
            if (StandbyModeController.isCharging(context)) {
                start(context)
            } else {
                stop(context)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, StandbyMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StandbyMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
