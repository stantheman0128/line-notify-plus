package com.stanslab.linenotify

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stanslab.linenotify.service.LineNotificationListener
import com.stanslab.linenotify.ui.theme.LineNotifyTheme

class ChatManagementActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            LineNotifyTheme {
                ChatManagementScreen(
                    windowWidthSizeClass = windowSizeClass.widthSizeClass,
                    onBack = { finish() }
                )
            }
        }
    }
}

data class ChatItem(val name: String, val type: String, val enabled: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatManagementScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit
) {
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

    val allChats = remember(knownGroups, knownChats, knownCommunities, disabledChats.value) {
        buildList {
            knownCommunities.sorted().forEach { name ->
                add(ChatItem(name, type = CHAT_TYPE_COMMUNITY, enabled = name !in disabledChats.value))
            }
            knownGroups.sorted().forEach { name ->
                add(ChatItem(name, type = CHAT_TYPE_GROUP, enabled = name !in disabledChats.value))
            }
            knownChats.sorted().forEach { name ->
                add(ChatItem(name, type = CHAT_TYPE_PERSONAL, enabled = name !in disabledChats.value))
            }
        }
    }

    var selectedChatName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(allChats) {
        if (selectedChatName !in allChats.map { it.name }) {
            selectedChatName = allChats.firstOrNull()?.name
        }
    }
    val selectedChat = allChats.firstOrNull { it.name == selectedChatName }

    val twoPaneFromResources = booleanResource(R.bool.use_two_pane_layout)
    val useTwoPane = twoPaneFromResources ||
        windowWidthSizeClass == WindowWidthSizeClass.Medium ||
        windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val horizontalPadding = dimensionResource(R.dimen.screen_horizontal_padding)
    val verticalPadding = dimensionResource(R.dimen.screen_vertical_padding)
    val paneSpacing = dimensionResource(R.dimen.pane_spacing)

    fun setChatEnabled(chat: ChatItem, enabled: Boolean) {
        val current = disabledChats.value.toMutableSet()
        if (enabled) {
            current.remove(chat.name)
        } else {
            current.add(chat.name)
        }
        disabledChats.value = current
        prefs.edit()
            .putStringSet(LineNotificationListener.KEY_DISABLED_CHATS, current)
            .apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
            EmptyChatState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            )
        } else if (useTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(paneSpacing)
            ) {
                ChatList(
                    chats = allChats,
                    selectedChat = selectedChat,
                    disabledChats = disabledChats,
                    onSelect = { selectedChatName = it.name },
                    onToggle = ::setChatEnabled,
                    modifier = Modifier.weight(1f)
                )
                ChatDetailPane(
                    chat = selectedChat,
                    onToggle = ::setChatEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            ChatList(
                chats = allChats,
                selectedChat = null,
                disabledChats = disabledChats,
                onSelect = { selectedChatName = it.name },
                onToggle = ::setChatEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    top = verticalPadding,
                    end = horizontalPadding,
                    bottom = verticalPadding
                )
            )
        }
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.no_line_notifications),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.no_line_notifications_body),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ChatList(
    chats: List<ChatItem>,
    selectedChat: ChatItem?,
    disabledChats: MutableState<MutableSet<String>>,
    onSelect: (ChatItem) -> Unit,
    onToggle: (ChatItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val communities = chats.filter { it.type == CHAT_TYPE_COMMUNITY }
        if (communities.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.chat_section_communities)) }
            items(communities, key = { "c_${it.name}" }) { chat ->
                ChatToggleItem(
                    chat = chat.copy(enabled = chat.name !in disabledChats.value),
                    selected = selectedChat?.name == chat.name,
                    onSelect = onSelect,
                    onToggle = onToggle
                )
            }
        }

        val groups = chats.filter { it.type == CHAT_TYPE_GROUP }
        if (groups.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.chat_section_groups)) }
            items(groups, key = { "g_${it.name}" }) { chat ->
                ChatToggleItem(
                    chat = chat.copy(enabled = chat.name !in disabledChats.value),
                    selected = selectedChat?.name == chat.name,
                    onSelect = onSelect,
                    onToggle = onToggle
                )
            }
        }

        val individuals = chats.filter { it.type == CHAT_TYPE_PERSONAL }
        if (individuals.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.chat_section_personal)) }
            items(individuals, key = { "p_${it.name}" }) { chat ->
                ChatToggleItem(
                    chat = chat.copy(enabled = chat.name !in disabledChats.value),
                    selected = selectedChat?.name == chat.name,
                    onSelect = onSelect,
                    onToggle = onToggle
                )
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
    selected: Boolean,
    onSelect: (ChatItem) -> Unit,
    onToggle: (ChatItem, Boolean) -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(chat) },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = chatTypeLabel(chat.type),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Switch(
                checked = chat.enabled,
                onCheckedChange = { onToggle(chat, it) }
            )
        }
    }
}

@Composable
private fun ChatDetailPane(
    chat: ChatItem?,
    onToggle: (ChatItem, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chat == null) {
                Text(
                    text = stringResource(R.string.chat_detail_empty_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.chat_detail_empty_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.chat_detail_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = chat.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = chatTypeLabel(chat.type),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_toggle_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (chat.enabled) {
                                stringResource(R.string.chat_status_enabled)
                            } else {
                                stringResource(R.string.chat_status_disabled)
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Switch(
                        checked = chat.enabled,
                        onCheckedChange = { onToggle(chat, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun chatTypeLabel(type: String): String = when (type) {
    CHAT_TYPE_COMMUNITY -> stringResource(R.string.chat_type_community)
    CHAT_TYPE_GROUP -> stringResource(R.string.chat_type_group)
    else -> stringResource(R.string.chat_type_personal)
}

private const val CHAT_TYPE_COMMUNITY = "community"
private const val CHAT_TYPE_GROUP = "group"
private const val CHAT_TYPE_PERSONAL = "personal"
