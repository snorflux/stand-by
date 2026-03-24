package com.snorflux.dockedmode.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class StandbyNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        val snapshot = activeNotifications?.toList().orEmpty()
        NotificationFeed.replaceAll(snapshot.map { it.toActiveNotification() })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.toActiveNotification() ?: return
        NotificationFeed.upsert(notification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        NotificationFeed.remove(key)
    }

    private fun StatusBarNotification.toActiveNotification(): ActiveNotification {
        return ActiveNotification(
            key = key,
            packageName = packageName,
            receivedAt = if (postTime > 0L) postTime else System.currentTimeMillis()
        )
    }
}
