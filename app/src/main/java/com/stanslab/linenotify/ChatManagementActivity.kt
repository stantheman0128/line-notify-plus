package com.stanslab.linenotify

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stanslab.linenotify.service.LineNotificationListener
import com.stanslab.linenotify.ui.theme.LineNotifyTheme

class ChatManagementActivity : AppCompatActivity() {
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

private const val TYPE_COMMUNITY = "community"
private const val TYPE_GROUP = "group"
private const val TYPE_PERSONAL = "personal"

data class ChatItem(
    val name: String,
    val type: String,
    val enabled: Boolean,
    val avatarPath: String?,
    val lastActive: Long,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(LineNotificationListener.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var allChats by remember { mutableStateOf(loadAllChats(context, prefs)) }
    var category by remember { mutableStateOf("all") }
    var sortMode by remember {
        mutableStateOf(
            (prefs.getString(LineNotificationListener.KEY_CHAT_SORT, "recent") ?: "recent")
                .let { if (it == "name") "name" else "recent" }
        )
    }
    fun reload() { allChats = loadAllChats(context, prefs) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    // Info dialog state
    var showInfo by remember { mutableStateOf(false) }

    // Multi-select state
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    val shown = remember(allChats, category, sortMode) { applyFilterSort(allChats, category, sortMode) }

    // Final displayed list: apply search filter on top of filter/sort
    val displayed = remember(shown, searchQuery) {
        if (searchQuery.isBlank()) shown
        else shown.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val horizontalPadding = dimensionResource(R.dimen.screen_horizontal_padding)
    val verticalPadding = dimensionResource(R.dimen.screen_vertical_padding)

    // Info dialog
    if (showInfo) {
        var showPrivacy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.chat_info_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.chat_info_body))
                    Text(
                        text = stringResource(R.string.chat_info_privacy_link),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .combinedClickable(onClick = { showPrivacy = !showPrivacy })
                    )
                    if (showPrivacy) {
                        Text(
                            text = stringResource(R.string.chat_info_privacy_body),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                // Contextual selection bar
                val allDisplayedSelected = displayed.isNotEmpty() &&
                    displayed.all { it.name in selected }
                TopAppBar(
                    title = { Text(stringResource(R.string.chat_selected_count, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selected.clear()
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            if (allDisplayedSelected) {
                                selected.clear()
                            } else {
                                val toAdd = displayed.map { it.name }
                                selected.clear()
                                selected.addAll(toAdd)
                            }
                        }) {
                            Text(
                                if (allDisplayedSelected) {
                                    stringResource(R.string.chat_deselect_all)
                                } else {
                                    stringResource(R.string.chat_select_all)
                                }
                            )
                        }
                        TextButton(onClick = {
                            selected.forEach { name -> setChatEnabled(prefs, name, true) }
                            reload()
                            selectionMode = false
                            selected.clear()
                        }) {
                            Text(stringResource(R.string.chat_bulk_enable))
                        }
                        TextButton(onClick = {
                            selected.forEach { name -> setChatEnabled(prefs, name, false) }
                            reload()
                            selectionMode = false
                            selected.clear()
                        }) {
                            Text(stringResource(R.string.chat_bulk_disable))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                // Normal top app bar
                TopAppBar(
                    title = {
                        if (searchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.chat_search_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(stringResource(R.string.chat_management_title))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (searchActive) {
                                searchActive = false
                                searchQuery = ""
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    actions = {
                        // Search icon (left of info)
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.chat_search_content_description),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        // Info icon (right of search)
                        IconButton(onClick = { showInfo = true }) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.chat_info_content_description),
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
        }
    ) { innerPadding ->
        if (allChats.isEmpty()) {
            EmptyChatState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                FilterSortBar(
                    category = category,
                    sortMode = sortMode,
                    horizontalPadding = horizontalPadding,
                    onCategory = { category = it },
                    onSort = {
                        sortMode = it
                        prefs.edit().putString(LineNotificationListener.KEY_CHAT_SORT, it).apply()
                    }
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 4.dp,
                        bottom = verticalPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayed, key = { "${it.type}_${it.name}" }) { chat ->
                        val isSelected = chat.name in selected
                        ChatRow(
                            chat = chat,
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            onToggle = { enabled ->
                                setChatEnabled(prefs, chat.name, enabled)
                                reload()
                            },
                            onLongClick = {
                                selectionMode = true
                                searchActive = false
                                if (chat.name !in selected) selected.add(chat.name)
                            },
                            onClickInSelection = {
                                if (isSelected) selected.remove(chat.name)
                                else selected.add(chat.name)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSortBar(
    category: String,
    sortMode: String,
    horizontalPadding: Dp,
    onCategory: (String) -> Unit,
    onSort: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryChip("all", stringResource(R.string.chat_filter_all), category, onCategory)
            CategoryChip(TYPE_PERSONAL, stringResource(R.string.chat_type_personal), category, onCategory)
            CategoryChip(TYPE_GROUP, stringResource(R.string.chat_type_group), category, onCategory)
            CategoryChip(TYPE_COMMUNITY, stringResource(R.string.chat_type_community), category, onCategory)
        }
        Spacer(modifier = Modifier.size(8.dp))
        SortDropdown(sortMode = sortMode, onChange = onSort)
    }
}

@Composable
private fun CategoryChip(
    value: String,
    label: String,
    current: String,
    onClick: (String) -> Unit
) {
    FilterChip(
        selected = current == value,
        onClick = { onClick(value) },
        label = { Text(label, fontSize = 13.sp) }
    )
}

@Composable
private fun SortDropdown(sortMode: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (sortMode == "name") {
        stringResource(R.string.chat_sort_name)
    } else {
        stringResource(R.string.chat_sort_recent)
    }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(label, fontSize = 13.sp)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_sort_recent)) },
                onClick = { onChange("recent"); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_sort_name)) },
                onClick = { onChange("name"); expanded = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    chat: ChatItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit,
    onClickInSelection: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = { if (selectionMode) onClickInSelection() }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            ChatAvatar(chat)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = chatTypeLabel(chat.type),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = chat.enabled,
                onCheckedChange = if (selectionMode) null else onToggle,
                enabled = !selectionMode
            )
        }
    }
}

@Composable
private fun ChatAvatar(chat: ChatItem) {
    val img = remember(chat.avatarPath) {
        chat.avatarPath?.let {
            runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull()
        }
    }
    val avatarSize = 44.dp
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(avatarColor(chat.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialOf(chat.name),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun chatTypeLabel(type: String): String = when (type) {
    TYPE_COMMUNITY -> stringResource(R.string.chat_type_community)
    TYPE_GROUP -> stringResource(R.string.chat_type_group)
    else -> stringResource(R.string.chat_type_personal)
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

/* ---------------- 資料層（純函式，非 Composable） ---------------- */

private fun loadAllChats(context: Context, prefs: SharedPreferences): List<ChatItem> {
    val communities = prefs.getStringSet("known_communities", emptySet()) ?: emptySet()
    val groups = prefs.getStringSet("known_groups", emptySet()) ?: emptySet()
    val personal = prefs.getStringSet("known_chats", emptySet()) ?: emptySet()
    val disabled = prefs.getStringSet(LineNotificationListener.KEY_DISABLED_CHATS, emptySet()) ?: emptySet()
    val lastActive = LineNotificationListener.readLastActive(prefs)

    fun toItem(name: String, type: String) = ChatItem(
        name = name,
        type = type,
        enabled = name !in disabled,
        avatarPath = LineNotificationListener.avatarFile(context, name)
            .let { if (it.exists()) it.absolutePath else null },
        lastActive = lastActive[name] ?: 0L
    )

    return communities.map { toItem(it, TYPE_COMMUNITY) } +
        groups.map { toItem(it, TYPE_GROUP) } +
        personal.map { toItem(it, TYPE_PERSONAL) }
}

/** 依「分類」過濾，再依「最新訊息 / 名稱」排序。 */
private fun applyFilterSort(all: List<ChatItem>, category: String, sort: String): List<ChatItem> {
    val filtered = if (category == "all") all else all.filter { it.type == category }
    return if (sort == "name") {
        filtered.sortedBy { it.name.lowercase() }
    } else {
        filtered.sortedWith(compareByDescending<ChatItem> { it.lastActive }.thenBy { it.name.lowercase() })
    }
}

private fun setChatEnabled(prefs: SharedPreferences, name: String, enabled: Boolean) {
    val disabled = (prefs.getStringSet(LineNotificationListener.KEY_DISABLED_CHATS, emptySet()) ?: emptySet())
        .toMutableSet()
    if (enabled) disabled.remove(name) else disabled.add(name)
    prefs.edit().putStringSet(LineNotificationListener.KEY_DISABLED_CHATS, disabled).apply()
}

private fun initialOf(name: String): String {
    val t = name.trim()
    if (t.isEmpty()) return "?"
    return String(Character.toChars(t.codePointAt(0)))  // 取首字（含 emoji 完整 code point）
}

private fun avatarColor(name: String): Color {
    val palette = listOf<Long>(
        0xFF06C755, 0xFF7B61FF, 0xFFEA4C89, 0xFF00A3BF,
        0xFFFF8A00, 0xFF3D5AFE, 0xFF00897B, 0xFFD81B60
    )
    val idx = (name.hashCode() and 0x7fffffff) % palette.size
    return Color(palette[idx])
}
