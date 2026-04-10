package com.example.notificationsapp.network

data class LoginResponse(
    val id: Int,
    val name: String,
    val status: String,
    val fcm_token: String? = null
)