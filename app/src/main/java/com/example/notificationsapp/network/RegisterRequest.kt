package com.example.notificationsapp.network

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)