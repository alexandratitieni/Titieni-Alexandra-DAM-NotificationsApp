package com.example.notificationsapp.model

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val fcm_token: String? = null
)