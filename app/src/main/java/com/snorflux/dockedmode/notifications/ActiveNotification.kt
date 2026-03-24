package com.snorflux.dockedmode.notifications

data class ActiveNotification(
    val key: String,
    val packageName: String,
    val receivedAt: Long
)
