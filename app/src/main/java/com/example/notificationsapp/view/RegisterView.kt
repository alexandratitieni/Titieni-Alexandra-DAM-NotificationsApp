package com.example.notificationsapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notificationsapp.network.RegisterRequest
import com.example.notificationsapp.network.RetrofitClient
import kotlinx.coroutines.launch

class RegisterView : ViewModel() {
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf("")

    fun onSignUp(name: String, email: String, pass: String) {
        if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            statusMessage = "Te rugăm să completezi toate câmpurile."
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.instance.register(RegisterRequest(name, email, pass))
                if (response.isSuccessful) {
                    statusMessage = "Succes! Utilizator creat cu ID: ${response.body()?.id}"
                } else {
                    statusMessage = "Eroare: Email-ul este deja înregistrat."
                }
            } catch (e: Exception) {
                statusMessage = "Eroare de rețea. Verifică dacă serverul Python rulează."
            } finally {
                isLoading = false
            }
        }
    }
}