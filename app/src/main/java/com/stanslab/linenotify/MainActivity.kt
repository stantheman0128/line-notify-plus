package com.stanslab.linenotify

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.stanslab.linenotify.service.LineNotificationListener
import com.stanslab.linenotify.service.LineMessageChannelSettings
import com.stanslab.linenotify.ui.theme.Green40
import com.stanslab.linenotify.ui.theme.LineNotifyTheme

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        rebindListenerIfNeeded()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            LineNotifyTheme {
                MainScreen(windowWidthSizeClass = windowSizeClass.widthSizeClass)
            }
        }
    }

    private fun rebindListenerIfNeeded() {
        if (packageName !in NotificationManagerCompat.getEnabledListenerPackages(this)) return
        if (LineNotificationListener.isListenerConnected) return
        val componentName = ComponentName(this, LineNotificationListener::class.java)
        // 以 framework connection callback 判斷，不能用 service instance：斷線後物件仍可能存活。
        NotificationListenerService.requestRebind(componentName)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(windowWidthSizeClass: WindowWidthSizeClass) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(LineNotificationListener.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isListenerEnabled by remember { mutableStateOf(false) }
    var hasParallelNotifyListenerEnabled by remember { mutableStateOf(false) }
    var serviceEnabled by remember {
        mutableStateOf(prefs.getBoolean(LineNotificationListener.KEY_SERVICE_ENABLED, true))
    }
    var replaceOriginal by remember {
        mutableStateOf(prefs.getBoolean(LineNotificationListener.KEY_REPLACE_ORIGINAL, true))
    }
    var notifStyle by remember {
        mutableStateOf(
            prefs.getString(LineNotificationListener.KEY_NOTIFICATION_STYLE, "thread") ?: "thread"
        )
    }
    var languageTag by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
    var clearAfterReply by remember {
        mutableStateOf(prefs.getBoolean(LineNotificationListener.KEY_CLEAR_AFTER_REPLY, true))
    }
    var clearAfterRead by remember {
        mutableStateOf(prefs.getBoolean(LineNotificationListener.KEY_CLEAR_AFTER_READ, true))
    }
    var hasPostNotificationsPermission by remember {
        mutableStateOf(hasPostNotificationsPermission(context))
    }
    var notifyPlusCanAlert by remember {
        mutableStateOf(canNotifyPlusAlert(context))
    }
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasPostNotificationsPermission = hasPostNotificationsPermission(context)
    }

    val onServiceEnabledChange: (Boolean) -> Unit = {
        serviceEnabled = it
        prefs.edit().putBoolean(LineNotificationListener.KEY_SERVICE_ENABLED, it).apply()
    }
    val onReplaceOriginalChange: (Boolean) -> Unit = {
        replaceOriginal = it
        prefs.edit().putBoolean(LineNotificationListener.KEY_REPLACE_ORIGINAL, it).apply()
    }
    val onNotificationStyleChange: (String) -> Unit = {
        // 舊樣式通知若留在 SystemUI，下一則新樣式會造成同聊天室重複顯示。
        // service 不在時仍由 NotificationManager cancelAll 清掉 orphan 通知。
        LineNotificationListener.instance?.clearAllEnhancedNotifications()
            ?: NotificationManagerCompat.from(context).cancelAll()
        notifStyle = it
        prefs.edit().putString(LineNotificationListener.KEY_NOTIFICATION_STYLE, it).apply()
    }
    val onClearAfterReplyChange: (Boolean) -> Unit = {
        clearAfterReply = it
        prefs.edit().putBoolean(LineNotificationListener.KEY_CLEAR_AFTER_REPLY, it).apply()
    }
    val onClearAfterReadChange: (Boolean) -> Unit = {
        clearAfterRead = it
        prefs.edit().putBoolean(LineNotificationListener.KEY_CLEAR_AFTER_READ, it).apply()
    }
    val onLanguageChange: (String) -> Unit = {
        languageTag = it
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
    }

    val onOpenPermissionSettings = {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    val onOpenLineMessageChannelSettings = {
        openLineNotificationSettings(
            context = context,
            rememberedPackage = prefs.getString(
                LineNotificationListener.KEY_LAST_LINE_MESSAGE_PACKAGE,
                null,
            ),
            rememberedChannelId = prefs.getString(
                LineNotificationListener.KEY_LAST_LINE_MESSAGE_CHANNEL,
                null,
            ),
            openMessageChannel = true,
        )
    }
    val onOpenLineAppNotificationSettings = {
        openLineNotificationSettings(
            context = context,
            rememberedPackage = prefs.getString(
                LineNotificationListener.KEY_LAST_LINE_MESSAGE_PACKAGE,
                null,
            ),
            rememberedChannelId = prefs.getString(
                LineNotificationListener.KEY_LAST_LINE_MESSAGE_CHANNEL,
                null,
            ),
            openMessageChannel = false,
        )
    }
    val onOpenNotifyPlusNotificationSettings = {
        openNotifyPlusNotificationSettings(context)
    }
    val onRequestPostNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val onOpenChatManagement = {
        context.startActivity(Intent(context, ChatManagementActivity::class.java))
    }
    val onOpenHelp = {
        context.startActivity(Intent(context, HelpActivity::class.java))
    }

    val twoPaneFromResources = booleanResource(R.bool.use_two_pane_layout)
    val useTwoPane = twoPaneFromResources ||
        windowWidthSizeClass == WindowWidthSizeClass.Medium ||
        windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val horizontalPadding = dimensionResource(R.dimen.screen_horizontal_padding)
    val verticalPadding = dimensionResource(R.dimen.screen_vertical_padding)
    val paneSpacing = dimensionResource(R.dimen.pane_spacing)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isListenerEnabled = isNotificationListenerEnabled(context)
        hasParallelNotifyListenerEnabled = isParallelNotifyListenerEnabled(context)
        hasPostNotificationsPermission = hasPostNotificationsPermission(context)
        notifyPlusCanAlert = canNotifyPlusAlert(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.main_title))
                    }
                },
                actions = {
                    IconButton(onClick = { context.shareLineNotifyPlus() }) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.main_share_content_description),
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, AboutActivity::class.java))
                    }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.main_info_content_description),
                            modifier = Modifier.size(28.dp),
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
        val contentPadding = PaddingValues(
            start = horizontalPadding,
            top = verticalPadding,
            end = horizontalPadding,
            bottom = verticalPadding
        )

        if (useTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(paneSpacing)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ServiceStatusCard(
                        isListenerEnabled = isListenerEnabled,
                        serviceEnabled = serviceEnabled
                    )
                    if (hasParallelNotifyListenerEnabled) {
                        ParallelInstallWarningCard(onOpenPermissionSettings)
                    }
                    if (!isListenerEnabled || !hasPostNotificationsPermission) {
                        PermissionGuideCard(
                            isListenerEnabled = isListenerEnabled,
                            hasPostNotificationsPermission = hasPostNotificationsPermission
                        )
                    }
                    if (isListenerEnabled) {
                        SettingsCard(
                            serviceEnabled = serviceEnabled,
                            replaceOriginal = replaceOriginal,
                            notifyPlusCanAlert = notifyPlusCanAlert,
                            onServiceEnabledChange = onServiceEnabledChange,
                            onReplaceOriginalChange = onReplaceOriginalChange,
                            onOpenLineMessageChannelSettings = onOpenLineMessageChannelSettings,
                            onOpenLineAppNotificationSettings = onOpenLineAppNotificationSettings,
                            onOpenNotifyPlusNotificationSettings =
                                onOpenNotifyPlusNotificationSettings,
                        )
                    } else {
                        PermissionButton(onClick = onOpenPermissionSettings)
                    }
                    if (!hasPostNotificationsPermission &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        PostNotificationsButton(onClick = onRequestPostNotifications)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isListenerEnabled) {
                        ManageChatsRow(onClick = onOpenChatManagement)
                        AdvancedSettingsCard(
                            notifStyle = notifStyle,
                            clearAfterReply = clearAfterReply,
                            clearAfterRead = clearAfterRead,
                            languageTag = languageTag,
                            featuresEnabled = serviceEnabled,
                            onNotificationStyleChange = onNotificationStyleChange,
                            onClearAfterReplyChange = onClearAfterReplyChange,
                            onClearAfterReadChange = onClearAfterReadChange,
                            onLanguageChange = onLanguageChange
                        )
                    }
                    HelpEntryButton(onClick = onOpenHelp)
                    if (isListenerEnabled) {
                        OfficialAccountButton()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ServiceStatusCard(
                    isListenerEnabled = isListenerEnabled,
                    serviceEnabled = serviceEnabled
                )
                if (hasParallelNotifyListenerEnabled) {
                    ParallelInstallWarningCard(onOpenPermissionSettings)
                }
                if (!isListenerEnabled || !hasPostNotificationsPermission) {
                    PermissionGuideCard(
                        isListenerEnabled = isListenerEnabled,
                        hasPostNotificationsPermission = hasPostNotificationsPermission
                    )
                }

                if (isListenerEnabled) {
                    SettingsCard(
                        serviceEnabled = serviceEnabled,
                        replaceOriginal = replaceOriginal,
                        notifyPlusCanAlert = notifyPlusCanAlert,
                        onServiceEnabledChange = onServiceEnabledChange,
                        onReplaceOriginalChange = onReplaceOriginalChange,
                        onOpenLineMessageChannelSettings = onOpenLineMessageChannelSettings,
                        onOpenLineAppNotificationSettings = onOpenLineAppNotificationSettings,
                        onOpenNotifyPlusNotificationSettings =
                            onOpenNotifyPlusNotificationSettings,
                    )
                    if (!hasPostNotificationsPermission &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        PostNotificationsButton(onClick = onRequestPostNotifications)
                    }
                    ManageChatsRow(onClick = onOpenChatManagement)
                    AdvancedSettingsCard(
                        notifStyle = notifStyle,
                        clearAfterReply = clearAfterReply,
                        clearAfterRead = clearAfterRead,
                        languageTag = languageTag,
                        featuresEnabled = serviceEnabled,
                        onNotificationStyleChange = onNotificationStyleChange,
                        onClearAfterReplyChange = onClearAfterReplyChange,
                        onClearAfterReadChange = onClearAfterReadChange,
                        onLanguageChange = onLanguageChange
                    )
                    HelpEntryButton(onClick = onOpenHelp)
                    OfficialAccountButton()
                } else {
                    PermissionButton(onClick = onOpenPermissionSettings)
                    if (!hasPostNotificationsPermission &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        PostNotificationsButton(onClick = onRequestPostNotifications)
                    }
                    HelpEntryButton(onClick = onOpenHelp)
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusCard(isListenerEnabled: Boolean, serviceEnabled: Boolean) {
    val isServiceRunning = isListenerEnabled && serviceEnabled
    val containerColor = if (isServiceRunning) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isServiceRunning) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val bodyText = when {
        isServiceRunning -> stringResource(R.string.service_listening)
        isListenerEnabled -> stringResource(R.string.service_stack_disabled)
        else -> stringResource(R.string.service_needs_permission)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isServiceRunning) {
                    stringResource(R.string.service_running)
                } else {
                    stringResource(R.string.service_disabled)
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(text = bodyText, color = contentColor)
        }
    }
}

@Composable
private fun ParallelInstallWarningCard(onOpenPermissionSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.parallel_notify_warning_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.parallel_notify_warning_body),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = onOpenPermissionSettings) {
                Text(stringResource(R.string.parallel_notify_warning_action))
            }
        }
    }
}

@Composable
private fun PermissionButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Green40,
            contentColor = Color.White
        )
    ) {
        Text(stringResource(R.string.open_notification_access), fontSize = 16.sp)
    }
}

@Composable
private fun PostNotificationsButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.request_post_notifications), fontSize = 16.sp)
    }
}

@Composable
private fun PermissionGuideCard(
    isListenerEnabled: Boolean,
    hasPostNotificationsPermission: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.permission_guide_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.permission_guide_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PermissionStepRow(
                title = stringResource(R.string.permission_notification_access_title),
                body = stringResource(R.string.permission_notification_access_body),
                granted = isListenerEnabled
            )
            HorizontalDivider()
            PermissionStepRow(
                title = stringResource(R.string.permission_post_notifications_title),
                body = stringResource(R.string.permission_post_notifications_body),
                granted = hasPostNotificationsPermission
            )
        }
    }
}

@Composable
private fun PermissionStepRow(
    title: String,
    body: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill(granted = granted)
    }
}

@Composable
private fun StatusPill(granted: Boolean) {
    val containerColor = if (granted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (granted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Text(
            text = if (granted) {
                stringResource(R.string.permission_status_granted)
            } else {
                stringResource(R.string.permission_status_missing)
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun SettingsCard(
    serviceEnabled: Boolean,
    replaceOriginal: Boolean,
    notifyPlusCanAlert: Boolean,
    onServiceEnabledChange: (Boolean) -> Unit,
    onReplaceOriginalChange: (Boolean) -> Unit,
    onOpenLineMessageChannelSettings: () -> Unit,
    onOpenLineAppNotificationSettings: () -> Unit,
    onOpenNotifyPlusNotificationSettings: () -> Unit,
) {
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSinglePopupDialog by remember { mutableStateOf(false) }
    val replaceInfoTitle = stringResource(R.string.replace_original_info_title)
    val replaceInfoBody = stringResource(R.string.replace_original_info_body)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SettingToggle(
                title = stringResource(R.string.enable_stack_notifications_title),
                subtitle = stringResource(R.string.enable_stack_notifications_subtitle),
                checked = serviceEnabled,
                onCheckedChange = onServiceEnabledChange
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingToggle(
                title = stringResource(R.string.replace_original_title),
                subtitle = stringResource(R.string.replace_original_subtitle),
                checked = replaceOriginal,
                enabled = serviceEnabled,
                onCheckedChange = onReplaceOriginalChange,
                onInfo = { infoDialog = replaceInfoTitle to replaceInfoBody }
            )

            if (serviceEnabled && replaceOriginal) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.single_popup_setup_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        if (notifyPlusCanAlert) {
                            R.string.single_popup_setup_body
                        } else {
                            R.string.notify_plus_alert_not_ready
                        }
                    ),
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp,
                    color = if (notifyPlusCanAlert) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                OutlinedButton(
                    onClick = {
                        if (notifyPlusCanAlert) {
                            showSinglePopupDialog = true
                        } else {
                            onOpenNotifyPlusNotificationSettings()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(
                        stringResource(
                            if (notifyPlusCanAlert) {
                                R.string.single_popup_setup_action
                            } else {
                                R.string.notify_plus_alert_fix_action
                            }
                        )
                    )
                }
                if (notifyPlusCanAlert) {
                    TextButton(
                        onClick = onOpenLineAppNotificationSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.single_popup_setup_fallback))
                    }
                }
            }
        }
    }

    if (infoDialog != null) {
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(infoDialog!!.first) },
            text = { Text(infoDialog!!.second) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showSinglePopupDialog) {
        AlertDialog(
            onDismissRequest = { showSinglePopupDialog = false },
            title = { Text(stringResource(R.string.single_popup_dialog_title)) },
            text = { Text(stringResource(R.string.single_popup_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSinglePopupDialog = false
                        onOpenLineMessageChannelSettings()
                    },
                ) {
                    Text(stringResource(R.string.single_popup_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSinglePopupDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun ManageChatsRow(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.manage_chats), fontSize = 16.sp)
    }
}

@Composable
private fun AdvancedSettingsCard(
    notifStyle: String,
    clearAfterReply: Boolean,
    clearAfterRead: Boolean,
    languageTag: String,
    featuresEnabled: Boolean,
    onNotificationStyleChange: (String) -> Unit,
    onClearAfterReplyChange: (Boolean) -> Unit,
    onClearAfterReadChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showStyleGuideDialog by remember { mutableStateOf(false) }

    if (showStyleGuideDialog) {
        NotificationStyleGuideDialog(onDismiss = { showStyleGuideDialog = false })
    }

    val threadTitle = stringResource(R.string.style_thread_title)
    val threadBody = stringResource(R.string.style_help_thread_body)
    val appleTitle = stringResource(R.string.style_apple_title)
    val appleBody = stringResource(R.string.style_help_apple_body)
    val clearReplyTitle = stringResource(R.string.clear_after_reply_title)
    val clearReplySubtitle = stringResource(R.string.clear_after_reply_subtitle)
    val demoPending = stringResource(R.string.info_demo_pending)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.advanced_section_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                SectionLabelWithInfo(
                    text = stringResource(R.string.notification_style_title),
                    onInfoClick = { showStyleGuideDialog = true }
                )
                StyleOption(
                    title = threadTitle,
                    subtitle = stringResource(R.string.style_thread_subtitle),
                    selected = notifStyle == "thread",
                    enabled = featuresEnabled,
                    onClick = { onNotificationStyleChange("thread") },
                    onInfo = { infoDialog = threadTitle to "$threadBody\n\n$demoPending" }
                )
                StyleOption(
                    title = appleTitle,
                    subtitle = stringResource(R.string.style_apple_subtitle),
                    selected = notifStyle == "apple",
                    enabled = featuresEnabled,
                    onClick = { onNotificationStyleChange("apple") },
                    onInfo = { infoDialog = appleTitle to "$appleBody\n\n$demoPending" }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingToggle(
                    title = clearReplyTitle,
                    subtitle = clearReplySubtitle,
                    checked = clearAfterReply,
                    enabled = featuresEnabled,
                    onCheckedChange = onClearAfterReplyChange,
                    onInfo = { infoDialog = clearReplyTitle to "$clearReplySubtitle\n\n$demoPending" }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionLabel(stringResource(R.string.language_title))
                LanguageDropdown(
                    languageTag = languageTag,
                    onLanguageChange = onLanguageChange
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }

    if (infoDialog != null) {
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(infoDialog!!.first) },
            text = { Text(infoDialog!!.second) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun LanguageDropdown(
    languageTag: String,
    onLanguageChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        languageTag.isEmpty() -> stringResource(R.string.language_follow_system)
        languageTag.startsWith("zh") -> stringResource(R.string.language_zh_tw)
        languageTag.startsWith("en") -> stringResource(R.string.language_en)
        else -> stringResource(R.string.language_follow_system)
    }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                fontSize = 15.sp
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_follow_system)) },
                onClick = { onLanguageChange(""); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_zh_tw)) },
                onClick = { onLanguageChange("zh-TW"); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_en)) },
                onClick = { onLanguageChange("en"); expanded = false }
            )
        }
    }
}

@Composable
private fun SectionLabelWithInfo(text: String, onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.style_guide_open_content_description),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onInfo: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (onInfo != null) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.info_icon_desc),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onInfo() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun StyleOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onInfo: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (onInfo != null) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.info_icon_desc),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onInfo() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(12.dp))
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
    }
}

@Composable
private fun HelpEntryButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.open_help), fontSize = 16.sp)
    }
}

@Composable
private fun OfficialAccountButton() {
    val context = LocalContext.current
    val officialUrl = stringResource(R.string.line_official_url)
    Button(
        onClick = {
            context.openExternalUri(officialUrl)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Green40,
            contentColor = Color.White
        )
    ) {
        Text(stringResource(R.string.join_official_account), fontSize = 16.sp)
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val componentName = ComponentName(context, LineNotificationListener::class.java)
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabledListeners.contains(componentName.flattenToString())
}

private fun isParallelNotifyListenerEnabled(context: Context): Boolean =
    context.packageName != PRODUCTION_PACKAGE_NAME &&
        PRODUCTION_PACKAGE_NAME in NotificationManagerCompat.getEnabledListenerPackages(context)

private fun openLineNotificationSettings(
    context: Context,
    rememberedPackage: String?,
    rememberedChannelId: String?,
    openMessageChannel: Boolean,
) {
    if (!canNotifyPlusAlert(context)) {
        Toast.makeText(context, R.string.notify_plus_alert_not_ready, Toast.LENGTH_LONG).show()
        openNotifyPlusNotificationSettings(context)
        return
    }

    val installedPackages = LineMessageChannelSettings.knownPackages
        .filterTo(mutableSetOf()) { context.isPackageInstalled(it) }
    val target = LineMessageChannelSettings.resolveTarget(
        installedPackages = installedPackages,
        rememberedPackage = rememberedPackage,
        rememberedChannelId = rememberedChannelId,
    )
    if (target == null) {
        Toast.makeText(context, R.string.line_app_not_found, Toast.LENGTH_LONG).show()
        return
    }

    val appSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, target.packageName)
    val appDetailsIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${target.packageName}"),
    )
    val channelId = target.channelId
    val intents = if (openMessageChannel && channelId != null) {
        listOf(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, target.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
            appSettingsIntent,
            appDetailsIntent,
        )
    } else {
        listOf(appSettingsIntent, appDetailsIntent)
    }

    if (intents.none { context.tryStartActivity(it) }) {
        Toast.makeText(context, R.string.notification_settings_not_found, Toast.LENGTH_LONG).show()
    }
}

private fun openNotifyPlusNotificationSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName),
        ),
    )
    if (intents.none { context.tryStartActivity(it) }) {
        Toast.makeText(context, R.string.notification_settings_not_found, Toast.LENGTH_LONG).show()
    }
}

@Suppress("DEPRECATION")
private fun Context.isPackageInstalled(packageName: String): Boolean = try {
    packageManager.getApplicationInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

private fun Context.tryStartActivity(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

private fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun canNotifyPlusAlert(context: Context): Boolean {
    if (!hasPostNotificationsPermission(context)) return false
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(LineNotificationListener.CHANNEL_ID) ?: return false
    return channel.importance >= NotificationManager.IMPORTANCE_HIGH
}

private const val PRODUCTION_PACKAGE_NAME = "com.stanslab.linenotify"
