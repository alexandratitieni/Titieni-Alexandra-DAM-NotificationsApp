package com.example.notificationsapp.model

data class Notification(
    val id: Int,
    val title: String,
    val message: String,
    val sent_at: String,
    val is_read: Boolean,
    val event_id: Int
)