package com.example.notificationsapp.activity

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notificationsapp.model.Event
import com.example.notificationsapp.network.RetrofitClient
import com.example.notificationsapp.model.SubscriptionRequest
import com.example.notificationsapp.ui.theme.NotificationsAppTheme
import kotlinx.coroutines.launch

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificationsAppTheme {
                DashboardScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sharedPref = remember { context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }
    val userId = remember { sharedPref.getInt("USER_ID", -1) }

    var events by remember { mutableStateOf(emptyList<Event>()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val loadData = {
        scope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.instance.getEvents()
                if (response.isSuccessful) {
                    events = response.body() ?: emptyList()
                } else {
                    Toast.makeText(context, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network failed: Check server connection", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    val handleSubscribe: (Int) -> Unit = { eventId ->
        if (userId == -1) {
            Toast.makeText(context, "User not identified. Please re-login.", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                try {
                    val request = SubscriptionRequest(user_id = userId, event_id = eventId)
                    val response = RetrofitClient.instance.subscribeToEvent(request)

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Successfully subscribed to notifications!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Subscription failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error connecting to server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val categories = remember(events) {
        events.map { it.category.name }.distinct().sorted()
    }

    val filteredEvents = remember(events, searchQuery, selectedCategory) {
        events.filter { event ->
            val matchesSearch = event.title.contains(searchQuery, ignoreCase = true) ||
                    event.category.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || event.category.name == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search events...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            },
                            label = { Text(category) },
                            leadingIcon = if (selectedCategory == category) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (filteredEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No events found", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredEvents) { event ->
                            EventCard(
                                event = event,
                                onNotifyClick = { id -> handleSubscribe(id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventCard(event: Event, onNotifyClick: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.category.name.uppercase(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${event.date_info} • ${event.location}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (event.is_available) Color(0xFFC8E6C9) else Color(0xFFE0E0E0)
                ) {
                    Text(
                        text = if (event.is_available) "AVAILABLE" else "UNAVAILABLE",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (event.is_available) Color(0xFF2E7D32) else Color(0xFF616161)
                    )
                }
            }

            IconButton(
                onClick = { onNotifyClick(event.id) }
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Subscribe",
                    tint = if (event.is_available) MaterialTheme.colorScheme.primary else Color.LightGray
                )
            }
        }
    }
}