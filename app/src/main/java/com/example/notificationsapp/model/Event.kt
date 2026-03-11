package com.example.notificationsapp.model

data class Event(
    val id: Int,
    val title: String,
    val date_info: String,
    val location: String,
    val ticket_url: String,
    val is_available: Boolean,
    val category: Category
)