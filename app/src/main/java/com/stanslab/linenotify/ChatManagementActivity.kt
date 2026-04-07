package com.stanslab.linenotify

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stanslab.linenotify.service.LineNotificationListener
import com.stanslab.linenotify.ui.theme.LineNotifyTheme

class ChatManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LineNotifyTheme {
                ChatManagementScreen(onBack = { finish() })
            }
        }
    }
}

data class ChatItem(val name: String, val type: String, val enabled: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(LineNotificationListener.PREFS_NAME, Context.MODE_PRIVATE)
    }

    val knownGroups = prefs.getStringSet("known_groups", emptySet()) ?: emptySet()
    val knownChats = prefs.getStringSet("known_chats", emptySet()) ?: emptySet()
    val knownCommunities = prefs.getStringSet("known_communities", emptySet()) ?: emptySet()
    val disabledChats = remember {
        mutableStateOf(
            prefs.getStringSet(LineNotificationListener.KEY_DISABLED_CHATS, emptySet())
                ?.toMutableSet() ?: mutableSetOf()
        )
    }

    val allChats = remember(knownGroups, knownChats, knownCommunities) {
        val list = mutableListOf<ChatItem>()
        knownCommunities.sorted().forEach { name ->
            list.add(ChatItem(name, type = "community", enabled = name !in disabledChats.value))
        }
        knownGroups.sorted().forEach { name ->
            list.add(ChatItem(name, type = "group", enabled = name !in disabledChats.value))
        }
        knownChats.sorted().forEach { name ->
            list.add(ChatItem(name, type = "personal", enabled = name !in disabledChats.value))
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聊天室管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (allChats.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "還沒有收到任何 LINE 通知",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "收到訊息後，聊天室會自動出現在這裡",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 社群區塊
                val communities = allChats.filter { it.type == "community" }
                if (communities.isNotEmpty()) {
                    item {
                        SectionHeader("社群")
                    }
                    items(communities, key = { "c_${it.name}" }) { chat ->
                        ChatToggleItem(chat, disabledChats, prefs)
                    }
                }

                // 群組區塊
                val groups = allChats.filter { it.type == "group" }
                if (groups.isNotEmpty()) {
                    item {
                        SectionHeader("群組")
                    }
                    items(groups, key = { "g_${it.name}" }) { chat ->
                        ChatToggleItem(chat, disabledChats, prefs)
                    }
                }

                // 個人聊天區塊
                val individuals = allChats.filter { it.type == "personal" }
                if (individuals.isNotEmpty()) {
                    item {
                        SectionHeader("個人聊天")
                    }
                    items(individuals, key = { "p_${it.name}" }) { chat ->
                        ChatToggleItem(chat, disabledChats, prefs)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ChatToggleItem(
    chat: ChatItem,
    disabledChats: MutableState<MutableSet<String>>,
    prefs: android.content.SharedPreferences
) {
    var enabled by remember { mutableStateOf(chat.name !in disabledChats.value) }

    val typeLabel = when (chat.type) {
        "community" -> "社群"
        "group" -> "群組"
        else -> "個人"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = typeLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { newValue ->
                    enabled = newValue
                    val current = disabledChats.value.toMutableSet()
                    if (newValue) {
                        current.remove(chat.name)
                    } else {
                        current.add(chat.name)
                    }
                    disabledChats.value = current
                    prefs.edit()
                        .putStringSet(LineNotificationListener.KEY_DISABLED_CHATS, current)
                        .apply()
                }
            )
        }
    }
}
