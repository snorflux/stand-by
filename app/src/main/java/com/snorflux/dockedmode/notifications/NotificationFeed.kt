package com.snorflux.dockedmode.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationFeed {
    private val lock = Any()
    private val notificationsByKey = linkedMapOf<String, ActiveNotification>()

    private val _notifications = MutableStateFlow<List<ActiveNotification>>(emptyList())
    val notifications: StateFlow<List<ActiveNotification>> = _notifications.asStateFlow()

    fun replaceAll(items: List<ActiveNotification>) {
        synchronized(lock) {
            notificationsByKey.clear()
            items.forEach { item ->
                notificationsByKey[item.key] = item
            }
            publishLocked()
        }
    }

    fun upsert(item: ActiveNotification) {
        synchronized(lock) {
            notificationsByKey[item.key] = item
            publishLocked()
        }
    }

    fun remove(key: String) {
        synchronized(lock) {
            notificationsByKey.remove(key)
            publishLocked()
        }
    }

    private fun publishLocked() {
        _notifications.value = notificationsByKey
            .values
            .sortedByDescending { it.receivedAt }
    }
}
