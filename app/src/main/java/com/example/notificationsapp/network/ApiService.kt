package com.example.notificationsapp.network

import com.example.notificationsapp.model.Event
import com.example.notificationsapp.model.SubscriptionRequest

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

    @POST("update-token")
    suspend fun updateToken(@Body request: TokenUpdate): Response<Any>

    @POST("subscribe")
    suspend fun subscribeToEvent(@Body request: SubscriptionRequest): Response<Unit>

    @POST("unsubscribe")
    suspend fun unsubscribeFromEvent(@Body request: SubscriptionRequest): Response<Unit>

    @POST("events/{id}/notify")
    suspend fun toggleNotification(@Path("id") eventId: Int): Response<Unit>

    @GET("users/{user_id}/subscriptions")
    suspend fun getUserSubscriptions(@Path("user_id") userId: Int): Response<List<Int>>
}