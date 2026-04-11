package com.example.notificationsapp.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
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
import com.example.notificationsapp.model.CustomEventRequest
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
    var subscribedEventIds by remember { mutableStateOf(setOf<Int>()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var customTitle by remember { mutableStateOf("") }
    var customDate by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }

    val loadData = {
        scope.launch {
            isLoading = true
            try {
                val eventsResponse = RetrofitClient.instance.getEvents()
                if (eventsResponse.isSuccessful) {
                    events = eventsResponse.body() ?: emptyList()
                }

                if (userId != -1) {
                    val subsResponse = RetrofitClient.instance.getUserSubscriptions(userId)
                    if (subsResponse.isSuccessful) {
                        subscribedEventIds = subsResponse.body()?.toSet() ?: emptySet()
                    }
                }
            } catch (e: Exception) {
                Log.e("DASHBOARD_ERROR", "Fetch failed", e)
                Toast.makeText(context, "Network failed", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    val deleteEvent = { eventId: Int ->
        scope.launch {
            try {
                val response = RetrofitClient.instance.deleteEvent(eventId)
                if (response.isSuccessful) {
                    loadData()
                    Toast.makeText(context, "Event deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DELETE_ERROR", "Delete failed", e)
            }
        }
    }

    val handleToggleSubscription: (Int) -> Unit = { eventId ->
        val isCurrentlySubscribed = subscribedEventIds.contains(eventId)
        scope.launch {
            try {
                val request = SubscriptionRequest(user_id = userId, event_id = eventId)
                val response = if (isCurrentlySubscribed) {
                    RetrofitClient.instance.unsubscribeFromEvent(request)
                } else {
                    RetrofitClient.instance.subscribeToEvent(request)
                }

                if (response.isSuccessful) {
                    subscribedEventIds = if (isCurrentlySubscribed) {
                        subscribedEventIds - eventId
                    } else {
                        subscribedEventIds + eventId
                    }
                    val msg = if (isCurrentlySubscribed) "Unsubscribed" else "Subscribed!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Connection error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val handleLogout = {
        sharedPref.edit().clear().apply()
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        (context as? ComponentActivity)?.finish()
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val categories = remember(events) {
        events.mapNotNull { it.category?.name }.distinct().sorted()
    }

    val filteredEvents = remember(events, searchQuery, selectedCategory) {
        events.filter { event ->
            val catName = event.category?.name ?: "Custom"
            val matchesSearch = event.title.contains(searchQuery, ignoreCase = true) ||
                    catName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || catName == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add Custom Link", tint = Color.White)
            }
        },
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Upcoming events", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { handleLogout() }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.Red)
                    }
                }
            )
        },
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
                    singleLine = true
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
                            onClick = { selectedCategory = if (selectedCategory == category) null else category },
                            label = { Text(category) },
                            leadingIcon = if (selectedCategory == category) {
                                { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (filteredEvents.isEmpty()) {
                    Text("No events found", Modifier.align(Alignment.Center), color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredEvents) { event ->
                            EventCard(
                                event = event,
                                isSubscribed = subscribedEventIds.contains(event.id),
                                onNotifyClick = { id -> handleToggleSubscription(id) },
                                onDeleteClick = { id -> deleteEvent(id) }
                            )
                        }
                    }
                }
            }
        }
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Add Custom Event to Monitor") },
                text = {
                    Column {
                        OutlinedTextField(value = customTitle, onValueChange = { customTitle = it }, label = { Text("Event Title") })
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = customDate, onValueChange = { customDate = it }, label = { Text("Date (ex: 20 May)") })
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = customUrl, onValueChange = { customUrl = it }, label = { Text("Ticket URL") })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            val response = RetrofitClient.instance.addCustomEvent(
                                CustomEventRequest(
                                    userId,
                                    customTitle,
                                    customUrl,
                                    date_info = customDate
                                )
                            )
                            if (response.isSuccessful) {
                                showDialog = false
                                customTitle = ""
                                customUrl = ""
                                loadData()
                            }
                        }
                    }) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
fun EventCard(event: Event, isSubscribed: Boolean, onNotifyClick: (Int) -> Unit, onDeleteClick: (Int) -> Unit) {
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
                    text = (event.category?.name ?: "Custom").uppercase(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!event.location.isNullOrBlank() && event.location != "User Added") {
                    Text(
                        text = "${event.date_info ?: ""} • ${event.location}",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = event.date_info ?: "",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }

            if (event.category?.name == "Custom" || event.category == null) {
                IconButton(onClick = { onDeleteClick(event.id) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red.copy(alpha = 0.6f)
                    )
                }
            }

            IconButton(onClick = { onNotifyClick(event.id) }) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Subscribe",
                    tint = if (isSubscribed) Color(0xFFFFD700) else Color.LightGray
                )
            }
        }
    }
}