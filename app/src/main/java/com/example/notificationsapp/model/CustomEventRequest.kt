package com.example.notificationsapp.model

data class CustomEventRequest(
    val user_id: Int,
    val title: String,
    val url: String,
    val date_info: String
)