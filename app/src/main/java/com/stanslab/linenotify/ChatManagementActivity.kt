package com.stanslab.linenotify

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
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
import com.stanslab.linenotify.service.NotificationClassifier
import com.stanslab.linenotify.ui.theme.ActionGreen
import com.stanslab.linenotify.ui.theme.FillGreen
import com.stanslab.linenotify.ui.theme.LineNotifyTheme
import com.stanslab.linenotify.ui.theme.inkGreen
import com.stanslab.linenotify.ui.theme.legacyAmber

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
    val legacyOriginalOnly: Boolean,
    val avatarPath: String?,
    val lastActive: Long,
    val manuallyClassified: Boolean,
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

    var searchQuery by remember { mutableStateOf("") }

    // Info dialog state
    var showInfo by remember { mutableStateOf(false) }
    var editingChat by remember { mutableStateOf<ChatItem?>(null) }

    // Multi-select state
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    val shown = remember(allChats, category, sortMode) { applyFilterSort(allChats, category, sortMode) }

    val displayed = remember(shown, searchQuery) {
        if (searchQuery.isBlank()) shown
        else shown.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val grouped = remember(displayed) {
        listOf(TYPE_PERSONAL, TYPE_GROUP, TYPE_COMMUNITY)
            .map { type -> type to displayed.filter { it.type == type } }
            .filter { it.second.isNotEmpty() }
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

    editingChat?.let { chat ->
        ChatTypeDialog(
            chat = chat,
            onDismiss = { editingChat = null },
            onTypeSelected = { type ->
                setChatTypeOverride(prefs, chat.name, type)
                reload()
                editingChat = null
            }
        )
    }

    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selectionMode) {
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
                                contentDescription = stringResource(R.string.chat_exit_selection)
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
                TopAppBar(
                    title = { Text(stringResource(R.string.chat_management_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.chat_enter_selection)
                            )
                        }
                        IconButton(onClick = { showInfo = true }) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.chat_info_content_description)
                            )
                        }
                    },
                    colors = barColors
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.chat_search_hint), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.chat_search_content_description)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 4.dp)
                )
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
                    )
                ) {
                    grouped.forEach { (type, chats) ->
                        item(key = "section_$type") {
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.chat_section_count,
                                        chatSectionTitle(type),
                                        chats.size
                                    ),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 6.dp)
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column {
                                        chats.forEachIndexed { index, chat ->
                                            val isSelected = chat.name in selected
                                            ChatRow(
                                                chat = chat,
                                                selectionMode = selectionMode,
                                                isSelected = isSelected,
                                                showDivider = index != chats.lastIndex,
                                                onToggle = { enabled ->
                                                    setChatEnabled(prefs, chat.name, enabled)
                                                    reload()
                                                },
                                                onLongClick = {
                                                    selectionMode = true
                                                    if (chat.name !in selected) selected.add(chat.name)
                                                },
                                                onClickInSelection = {
                                                    if (isSelected) selected.remove(chat.name)
                                                    else selected.add(chat.name)
                                                },
                                                onOpenSettings = { editingChat = chat },
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = FillGreen,
            selectedLabelColor = Color.White
        )
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
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit,
    onClickInSelection: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val rowColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val statusText = when {
        chat.legacyOriginalOnly -> stringResource(R.string.chat_legacy_original_only)
        chat.enabled -> stringResource(R.string.chat_status_enabled)
        else -> stringResource(R.string.chat_status_disabled)
    }
    val statusColor = when {
        chat.legacyOriginalOnly -> legacyAmber()
        chat.enabled -> inkGreen()
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowColor)
                .combinedClickable(
                    onLongClick = onLongClick,
                    onClick = {
                        if (selectionMode) onClickInSelection()
                        else onOpenSettings()
                    }
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
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
            Spacer(modifier = Modifier.size(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.manuallyClassified) {
                        Text(
                            text = stringResource(R.string.chat_manual_tag),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Switch(
                checked = chat.enabled,
                onCheckedChange = if (selectionMode) null else onToggle,
                enabled = !selectionMode,
                modifier = Modifier.scale(0.85f),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ActionGreen,
                    checkedBorderColor = ActionGreen,
                    checkedThumbColor = Color.White
                )
            )
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 58.dp)
            )
        }
    }
}

@Composable
private fun ChatTypeDialog(
    chat: ChatItem,
    onDismiss: () -> Unit,
    onTypeSelected: (String) -> Unit,
) {
    val choices = listOf(
        TYPE_PERSONAL to stringResource(R.string.chat_type_personal),
        TYPE_GROUP to stringResource(R.string.chat_type_group),
        TYPE_COMMUNITY to stringResource(R.string.chat_type_community),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_edit_type_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.chat_edit_type_body, chat.name),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                choices.forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTypeSelected(type) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = chat.type == type,
                            onClick = { onTypeSelected(type) }
                        )
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ChatAvatar(chat: ChatItem) {
    val img = remember(chat.avatarPath) {
        chat.avatarPath?.let {
            runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull()
        }
    }
    val avatarSize = 34.dp
    val avatarShape = RoundedCornerShape(11.dp)
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .clip(avatarShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(avatarShape)
                .background(avatarColor(chat.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialOf(chat.name),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun chatSectionTitle(type: String): String = when (type) {
    TYPE_COMMUNITY -> stringResource(R.string.chat_section_communities)
    TYPE_GROUP -> stringResource(R.string.chat_section_groups)
    else -> stringResource(R.string.chat_section_personal)
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
    val legacyOriginalOnly =
        prefs.getStringSet(LineNotificationListener.KEY_DISABLED_CHATS, emptySet()) ?: emptySet()
    val muted = prefs.getStringSet(LineNotificationListener.KEY_MUTED_CHATS, emptySet()) ?: emptySet()
    val lastActive = LineNotificationListener.readLastActive(prefs)
    val forced = setOf(
        NotificationClassifier.PREFS_FORCED_COMMUNITIES,
        NotificationClassifier.PREFS_FORCED_GROUPS,
        NotificationClassifier.PREFS_FORCED_CHATS,
    ).flatMapTo(mutableSetOf()) { key -> prefs.getStringSet(key, emptySet()) ?: emptySet() }

    fun toItem(name: String, type: String) = ChatItem(
        name = name,
        type = type,
        enabled = NotificationClassifier.notificationModeOf(name, legacyOriginalOnly, muted) ==
            NotificationClassifier.MODE_ENHANCED,
        legacyOriginalOnly = NotificationClassifier.notificationModeOf(name, legacyOriginalOnly, muted) ==
            NotificationClassifier.MODE_LEGACY_ORIGINAL_ONLY,
        avatarPath = LineNotificationListener.avatarFile(context, name)
            .let { if (it.exists()) it.absolutePath else null },
        lastActive = lastActive[name] ?: 0L,
        manuallyClassified = name in forced,
    )

    val knownNames = communities + groups + personal
    val orphanPreferences = (legacyOriginalOnly + muted) - knownNames

    return communities.map { toItem(it, TYPE_COMMUNITY) } +
        groups.map { toItem(it, TYPE_GROUP) } +
        personal.map { toItem(it, TYPE_PERSONAL) } +
        orphanPreferences.map { toItem(it, TYPE_PERSONAL) }
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
    val currentLegacy = prefs.getStringSet(LineNotificationListener.KEY_DISABLED_CHATS, emptySet())
        ?: emptySet()
    val currentMuted = prefs.getStringSet(LineNotificationListener.KEY_MUTED_CHATS, emptySet())
        ?: emptySet()
    val updated = NotificationClassifier.updateNotificationPreferenceSets(
        chatTitle = name,
        enabled = enabled,
        legacyOriginalOnlyChats = currentLegacy,
        mutedChats = currentMuted,
    )
    prefs.edit()
        .putStringSet(LineNotificationListener.KEY_DISABLED_CHATS, updated.legacyOriginalOnly)
        .putStringSet(LineNotificationListener.KEY_MUTED_CHATS, updated.muted)
        .apply()
    if (!enabled) LineNotificationListener.instance?.clearChatNotifications(name)
}

/** 手動分類是對 LINE 私有 extra 變動的保險；同時搬動 known set，讓 UI 立即反映。 */
private fun setChatTypeOverride(prefs: SharedPreferences, name: String, type: String) {
    val knownKeys = listOf(
        NotificationClassifier.PREFS_KNOWN_COMMUNITIES,
        NotificationClassifier.PREFS_KNOWN_GROUPS,
        NotificationClassifier.PREFS_KNOWN_CHATS,
    )
    val forcedKeys = listOf(
        NotificationClassifier.PREFS_FORCED_COMMUNITIES,
        NotificationClassifier.PREFS_FORCED_GROUPS,
        NotificationClassifier.PREFS_FORCED_CHATS,
    )
    val targetKnown = NotificationClassifier.prefsKeyForType(type)
    val targetForced = NotificationClassifier.forcedPrefsKeyForType(type)
    val editor = prefs.edit()
    (knownKeys + forcedKeys).forEach { key ->
        val values = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (key == targetKnown || key == targetForced) values.add(name) else values.remove(name)
        editor.putStringSet(key, values)
    }
    editor.apply()
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
