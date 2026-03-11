package com.example.notificationsapp.network

import com.example.notificationsapp.model.Event
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path


interface ApiService {
    @POST("register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("events")
    suspend fun getEvents(): Response<List<Event>>

    @POST("events/{id}/notify")
    suspend fun toggleNotification(@Path("id") eventId: Int): Response<Unit>
}