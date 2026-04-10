package com.example.notificationsapp.model

data class Event(
    val id: Int,
    val title: String,
    val date_info: String? = null,
    val ticket_url: String? = null,
    val location: String? = null,
    val is_available: Boolean = false,
    val category: Category? = null
)