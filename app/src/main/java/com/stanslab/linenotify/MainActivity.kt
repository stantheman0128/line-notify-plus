package com.stanslab.linenotify

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import com.stanslab.linenotify.service.LineAccessibilityService
import com.stanslab.linenotify.ui.theme.ActionGreen
import com.stanslab.linenotify.ui.theme.BrandGreen
import com.stanslab.linenotify.ui.theme.FillGreen
import com.stanslab.linenotify.ui.theme.LineNotifyTheme
import com.stanslab.linenotify.ui.theme.inkGreen

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // v1.6 起「已讀後清除」是固定行為。覆寫舊版可能留下的 false，避免
        // Accessibility 聊天室開啟同步看似啟用、實際卻被舊偏好值擋住。
        getSharedPreferences(LineNotificationListener.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(LineNotificationListener.KEY_CLEAR_AFTER_READ, true)
            .apply()

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
    var accessibilityReadSync by remember {
        mutableStateOf(
            prefs.getBoolean(LineNotificationListener.KEY_ACCESSIBILITY_READ_SYNC, false)
        )
    }
    var hasAccessibilityAccess by remember {
        mutableStateOf(isLineAccessibilityServiceEnabled(context))
    }
    var hasPostNotificationsPermission by remember {
        mutableStateOf(hasPostNotificationsPermission(context))
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
    val onAccessibilityReadSyncChange: (Boolean) -> Unit = {
        accessibilityReadSync = it
        prefs.edit()
            .putBoolean(LineNotificationListener.KEY_ACCESSIBILITY_READ_SYNC, it)
            .apply()
    }
    val onLanguageChange: (String) -> Unit = {
        languageTag = it
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
    }

    val onOpenPermissionSettings = {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    val onOpenAccessibilitySettings = {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
        hasPostNotificationsPermission = hasPostNotificationsPermission(context)
        hasAccessibilityAccess = isLineAccessibilityServiceEnabled(context)
    }

    val needsPermissionSetup = !isListenerEnabled || !hasPostNotificationsPermission
    val isServiceRunning = isListenerEnabled && serviceEnabled
    val sectionSpacing = 12.dp

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
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.main_title),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { context.shareLineNotifyPlus() }) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.main_share_content_description),
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, AboutActivity::class.java))
                    }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.main_info_content_description),
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                ) {
                    HomeStatusAndControls(
                        isListenerEnabled = isListenerEnabled,
                        serviceEnabled = serviceEnabled,
                        isServiceRunning = isServiceRunning,
                        needsPermissionSetup = needsPermissionSetup,
                        hasPostNotificationsPermission = hasPostNotificationsPermission,
                        replaceOriginal = replaceOriginal,
                        notifStyle = notifStyle,
                        onServiceEnabledChange = onServiceEnabledChange,
                        onReplaceOriginalChange = onReplaceOriginalChange,
                        onNotificationStyleChange = onNotificationStyleChange,
                        onOpenPermissionSettings = onOpenPermissionSettings,
                        onRequestPostNotifications = onRequestPostNotifications
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                ) {
                    HomeSecondaryPane(
                        isListenerEnabled = isListenerEnabled,
                        featuresEnabled = serviceEnabled,
                        replaceOriginal = replaceOriginal,
                        clearAfterReply = clearAfterReply,
                        accessibilityReadSync = accessibilityReadSync,
                        hasAccessibilityAccess = hasAccessibilityAccess,
                        languageTag = languageTag,
                        onClearAfterReplyChange = onClearAfterReplyChange,
                        onAccessibilityReadSyncChange = onAccessibilityReadSyncChange,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onLanguageChange = onLanguageChange,
                        onOpenChatManagement = onOpenChatManagement,
                        onOpenHelp = onOpenHelp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                HomeStatusAndControls(
                    isListenerEnabled = isListenerEnabled,
                    serviceEnabled = serviceEnabled,
                    isServiceRunning = isServiceRunning,
                    needsPermissionSetup = needsPermissionSetup,
                    hasPostNotificationsPermission = hasPostNotificationsPermission,
                    replaceOriginal = replaceOriginal,
                    notifStyle = notifStyle,
                    onServiceEnabledChange = onServiceEnabledChange,
                    onReplaceOriginalChange = onReplaceOriginalChange,
                    onNotificationStyleChange = onNotificationStyleChange,
                    onOpenPermissionSettings = onOpenPermissionSettings,
                    onRequestPostNotifications = onRequestPostNotifications
                )
                HomeSecondaryPane(
                    isListenerEnabled = isListenerEnabled,
                    featuresEnabled = serviceEnabled,
                    replaceOriginal = replaceOriginal,
                    clearAfterReply = clearAfterReply,
                    accessibilityReadSync = accessibilityReadSync,
                    hasAccessibilityAccess = hasAccessibilityAccess,
                    languageTag = languageTag,
                    onClearAfterReplyChange = onClearAfterReplyChange,
                    onAccessibilityReadSyncChange = onAccessibilityReadSyncChange,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onLanguageChange = onLanguageChange,
                    onOpenChatManagement = onOpenChatManagement,
                    onOpenHelp = onOpenHelp
                )
            }
        }
    }
}

@Composable
private fun HomeStatusAndControls(
    isListenerEnabled: Boolean,
    serviceEnabled: Boolean,
    isServiceRunning: Boolean,
    needsPermissionSetup: Boolean,
    hasPostNotificationsPermission: Boolean,
    replaceOriginal: Boolean,
    notifStyle: String,
    onServiceEnabledChange: (Boolean) -> Unit,
    onReplaceOriginalChange: (Boolean) -> Unit,
    onNotificationStyleChange: (String) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onRequestPostNotifications: () -> Unit
) {
    when {
        isServiceRunning -> ServiceStatusStrip()
        isListenerEnabled && !serviceEnabled && !needsPermissionSetup -> ServiceOffStrip()
    }

    if (needsPermissionSetup) {
        PermissionAlertCard(
            isListenerEnabled = isListenerEnabled,
            hasPostNotificationsPermission = hasPostNotificationsPermission,
            onOpenPermissionSettings = onOpenPermissionSettings,
            onRequestPostNotifications = onRequestPostNotifications
        )
    }

    if (isListenerEnabled) {
        MasterControlCard(
            serviceEnabled = serviceEnabled,
            replaceOriginal = replaceOriginal,
            onServiceEnabledChange = onServiceEnabledChange,
            onReplaceOriginalChange = onReplaceOriginalChange
        )
        NotificationStyleSection(
            notifStyle = notifStyle,
            featuresEnabled = serviceEnabled,
            onNotificationStyleChange = onNotificationStyleChange
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

@Composable
private fun HomeSecondaryPane(
    isListenerEnabled: Boolean,
    featuresEnabled: Boolean,
    replaceOriginal: Boolean,
    clearAfterReply: Boolean,
    accessibilityReadSync: Boolean,
    hasAccessibilityAccess: Boolean,
    languageTag: String,
    onClearAfterReplyChange: (Boolean) -> Unit,
    onAccessibilityReadSyncChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onOpenChatManagement: () -> Unit,
    onOpenHelp: () -> Unit
) {
    if (isListenerEnabled) {
        ClearTimingCard(
            clearAfterReply = clearAfterReply,
            featuresEnabled = featuresEnabled,
            onClearAfterReplyChange = onClearAfterReplyChange
        )
    }
    if (isListenerEnabled || accessibilityReadSync || hasAccessibilityAccess) {
        AccessibilityReadSyncCard(
            enabled = accessibilityReadSync,
            permissionGranted = hasAccessibilityAccess,
            featuresEnabled = isListenerEnabled && featuresEnabled,
            replaceOriginal = replaceOriginal,
            onEnabledChange = onAccessibilityReadSyncChange,
            onOpenSettings = onOpenAccessibilitySettings,
        )
    }
    EntryListCard(
        isListenerEnabled = isListenerEnabled,
        languageTag = languageTag,
        onLanguageChange = onLanguageChange,
        onOpenChatManagement = onOpenChatManagement,
        onOpenHelp = onOpenHelp
    )
    PrivacyFooter()
}

@Composable
private fun ServiceStatusStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(BrandGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(BrandGreen)
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.service_running),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.service_listening),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ServiceOffStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.service_disabled),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
            text = stringResource(R.string.service_stack_disabled),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun PermissionAlertCard(
    isListenerEnabled: Boolean,
    hasPostNotificationsPermission: Boolean,
    onOpenPermissionSettings: () -> Unit,
    onRequestPostNotifications: () -> Unit
) {
    val remaining = listOf(isListenerEnabled, hasPostNotificationsPermission).count { !it }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                text = pluralStringResource(
                    R.plurals.permission_guide_title_remaining,
                    remaining.coerceAtLeast(1),
                    remaining.coerceAtLeast(1),
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = stringResource(R.string.permission_guide_body),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 5.dp),
                lineHeight = 18.sp
            )
            PermissionStepRow(
                title = stringResource(R.string.permission_notification_access_title),
                body = stringResource(R.string.permission_notification_access_body),
                granted = isListenerEnabled,
                onGoToSettings = onOpenPermissionSettings
            )
            PermissionStepRow(
                title = stringResource(R.string.permission_post_notifications_title),
                body = stringResource(R.string.permission_post_notifications_body),
                granted = hasPostNotificationsPermission,
                onGoToSettings = onRequestPostNotifications
            )
        }
    }
}

@Composable
private fun PermissionButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FillGreen,
            contentColor = Color.White
        )
    ) {
        Text(
            stringResource(R.string.open_notification_access),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun PostNotificationsButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FillGreen,
            contentColor = Color.White
        )
    ) {
        Text(
            stringResource(R.string.request_post_notifications),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun PermissionStepRow(
    title: String,
    body: String,
    granted: Boolean,
    onGoToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.dp)
            .then(
                if (!granted) Modifier.clickable(onClick = onGoToSettings) else Modifier
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (granted) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (granted) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFF15803D)
                )
            } else {
                Text(
                    text = "!",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFB91C1C)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (granted) {
                    stringResource(R.string.permission_status_granted)
                } else {
                    body
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                lineHeight = 15.sp
            )
        }
        if (!granted) {
            Text(
                text = stringResource(R.string.permission_go_to_settings),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = inkGreen()
            )
        }
    }
}

@Composable
private fun MasterControlCard(
    serviceEnabled: Boolean,
    replaceOriginal: Boolean,
    onServiceEnabledChange: (Boolean) -> Unit,
    onReplaceOriginalChange: (Boolean) -> Unit
) {
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val replaceInfoTitle = stringResource(R.string.replace_original_info_title)
    val replaceInfoBody = stringResource(R.string.replace_original_info_body)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            SettingToggle(
                title = stringResource(R.string.enable_stack_notifications_title),
                subtitle = stringResource(R.string.enable_stack_notifications_subtitle),
                checked = serviceEnabled,
                onCheckedChange = onServiceEnabledChange,
                titleSize = 15.sp,
                titleWeight = FontWeight.ExtraBold
            )

            Row(
                modifier = Modifier
                    .padding(top = 13.dp)
                    .alpha(if (serviceEnabled) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.size(12.dp))
                SettingToggle(
                    title = stringResource(R.string.replace_original_title),
                    subtitle = stringResource(R.string.replace_original_subtitle),
                    checked = replaceOriginal,
                    enabled = serviceEnabled,
                    compactSwitch = true,
                    onCheckedChange = onReplaceOriginalChange,
                    onInfo = { infoDialog = replaceInfoTitle to replaceInfoBody },
                    titleSize = 13.sp,
                    titleWeight = FontWeight.SemiBold,
                    subtitleSize = 12.sp
                )
            }
        }
    }

    InfoAlert(dialog = infoDialog, onDismiss = { infoDialog = null })
}

@Composable
private fun NotificationStyleSection(
    notifStyle: String,
    featuresEnabled: Boolean,
    onNotificationStyleChange: (String) -> Unit
) {
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showStyleGuideDialog by remember { mutableStateOf(false) }

    if (showStyleGuideDialog) {
        NotificationStyleGuideDialog(onDismiss = { showStyleGuideDialog = false })
    }

    val threadTitle = stringResource(R.string.style_thread_title)
    val threadBody = stringResource(R.string.style_help_thread_body)
    val appleTitle = stringResource(R.string.style_apple_title)
    val appleBody = stringResource(R.string.style_help_apple_body)
    val demoPending = stringResource(R.string.info_demo_pending)

    Column(
        modifier = Modifier.alpha(if (featuresEnabled) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabelWithInfo(
            text = stringResource(R.string.notification_style_title),
            onInfoClick = { showStyleGuideDialog = true }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            StylePickerCard(
                modifier = Modifier.weight(1f),
                title = threadTitle,
                subtitle = stringResource(R.string.style_thread_subtitle),
                selected = notifStyle == "thread",
                enabled = featuresEnabled,
                onClick = { onNotificationStyleChange("thread") },
                onInfo = { infoDialog = threadTitle to "$threadBody\n\n$demoPending" },
                preview = { MiniThreadPreview() }
            )
            StylePickerCard(
                modifier = Modifier.weight(1f),
                title = appleTitle,
                subtitle = stringResource(R.string.style_apple_subtitle),
                selected = notifStyle == "apple",
                enabled = featuresEnabled,
                onClick = { onNotificationStyleChange("apple") },
                onInfo = { infoDialog = appleTitle to "$appleBody\n\n$demoPending" },
                preview = { MiniApplePreview() }
            )
        }
    }

    InfoAlert(dialog = infoDialog, onDismiss = { infoDialog = null })
}

@Composable
private fun StylePickerCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    preview: @Composable () -> Unit
) {
    val borderColor = if (selected) ActionGreen else MaterialTheme.colorScheme.outline
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 2
                        )
                        Spacer(Modifier.size(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.info_icon_desc),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = onInfo),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                    )
                }
                Spacer(Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .border(1.5.dp, borderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(ActionGreen)
                        )
                    }
                }
            }
            Box(modifier = Modifier.padding(top = 8.dp)) {
                preview()
            }
        }
    }
}

@Composable
private fun MiniThreadPreview() {
    val line = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MiniNotifBar(lineColor = line, secondLine = false)
        MiniNotifBar(lineColor = line, secondLine = true, showDot = false)
    }
}

@Composable
private fun MiniApplePreview() {
    val line = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MiniNotifBar(lineColor = line, secondLine = false)
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(line.copy(alpha = 0.85f))
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(line.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun MiniNotifBar(lineColor: Color, secondLine: Boolean, showDot: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (showDot) lineColor else Color.Transparent)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(lineColor)
            )
            if (secondLine) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(lineColor)
                )
            }
        }
    }
}

@Composable
private fun ClearTimingCard(
    clearAfterReply: Boolean,
    featuresEnabled: Boolean,
    onClearAfterReplyChange: (Boolean) -> Unit
) {
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val clearReplyTitle = stringResource(R.string.clear_after_reply_title)
    val clearReplySubtitle = stringResource(R.string.clear_after_reply_subtitle)
    val demoPending = stringResource(R.string.info_demo_pending)

    Column(
        modifier = Modifier.alpha(if (featuresEnabled) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel(stringResource(R.string.clear_timing_title))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                SettingToggle(
                    title = clearReplyTitle,
                    subtitle = clearReplySubtitle,
                    checked = clearAfterReply,
                    enabled = featuresEnabled,
                    compactSwitch = true,
                    onCheckedChange = onClearAfterReplyChange,
                    onInfo = { infoDialog = clearReplyTitle to "$clearReplySubtitle\n\n$demoPending" },
                    titleSize = 13.sp,
                    titleWeight = FontWeight.SemiBold,
                    subtitleSize = 12.sp
                )
            }
        }
    }

    InfoAlert(dialog = infoDialog, onDismiss = { infoDialog = null })
}

@Composable
private fun AccessibilityReadSyncCard(
    enabled: Boolean,
    permissionGranted: Boolean,
    featuresEnabled: Boolean,
    replaceOriginal: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showDisclosure by remember { mutableStateOf(false) }
    // 前置條件失效時不能新開；已經啟用的使用者仍必須能直接關閉。
    val toggleEnabled = enabled || (featuresEnabled && replaceOriginal)
    val statusText = when {
        !enabled && permissionGranted ->
            stringResource(R.string.accessibility_status_off_authorized)
        !featuresEnabled -> stringResource(R.string.accessibility_status_service_required)
        !replaceOriginal -> stringResource(R.string.accessibility_status_replace_required)
        !enabled -> stringResource(R.string.accessibility_status_off)
        permissionGranted -> stringResource(R.string.accessibility_status_active)
        else -> stringResource(R.string.accessibility_status_permission_missing)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.accessibility_section_title))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.accessibility_read_sync_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.accessibility_read_sync_subtitle),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    BrandSwitch(
                        checked = enabled,
                        enabled = toggleEnabled,
                        onCheckedChange = { checked ->
                            if (checked) showDisclosure = true else onEnabledChange(false)
                        },
                    )
                }

                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )

                if (enabled || permissionGranted) {
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.accessibility_open_settings),
                            color = inkGreen(),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
            text = { Text(stringResource(R.string.accessibility_disclosure_body)) },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.accessibility_disclosure_decline))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    onEnabledChange(true)
                    onOpenSettings()
                }) {
                    Text(stringResource(R.string.accessibility_disclosure_accept))
                }
            },
        )
    }
}

@Composable
private fun EntryListCard(
    isListenerEnabled: Boolean,
    languageTag: String,
    onLanguageChange: (String) -> Unit,
    onOpenChatManagement: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val context = LocalContext.current
    val officialUrl = stringResource(R.string.line_official_url)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            if (isListenerEnabled) {
                EntryRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.manage_chats),
                    onClick = onOpenChatManagement
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                LanguageEntryRow(
                    languageTag = languageTag,
                    onLanguageChange = onLanguageChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            }
            EntryRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.open_help),
                onClick = onOpenHelp
            )
            if (isListenerEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                EntryRow(
                    icon = Icons.Filled.Add,
                    title = stringResource(R.string.join_official_account),
                    onClick = { context.openExternalUri(officialUrl) }
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    value: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        EntryIcon(icon)
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun LanguageEntryRow(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.language_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline
            )
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
private fun EntryIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrivacyFooter() {
    Text(
        text = stringResource(R.string.privacy_footer),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun SectionLabelWithInfo(text: String, onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.style_guide_open_content_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    compactSwitch: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    onInfo: (() -> Unit)? = null,
    titleSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    titleWeight: FontWeight = FontWeight.Medium,
    subtitleSize: androidx.compose.ui.unit.TextUnit = 13.sp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = titleSize, fontWeight = titleWeight)
                if (onInfo != null) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.info_icon_desc),
                        modifier = Modifier
                            .size(15.dp)
                            .clickable { onInfo() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = subtitleSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = (subtitleSize.value + 4).sp
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        BrandSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            compact = compactSwitch
        )
    }
}

@Composable
private fun BrandSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = if (compact) Modifier.scale(0.85f) else Modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = ActionGreen,
            checkedBorderColor = ActionGreen,
            checkedThumbColor = Color.White
        )
    )
}

@Composable
private fun InfoAlert(dialog: Pair<String, String>?, onDismiss: () -> Unit) {
    if (dialog == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.first) },
        text = { Text(dialog.second) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val componentName = ComponentName(context, LineNotificationListener::class.java)
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabledListeners.contains(componentName.flattenToString())
}

private fun isLineAccessibilityServiceEnabled(context: Context): Boolean {
    val component = ComponentName(context, LineAccessibilityService::class.java)
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            val serviceInfo = info.resolveInfo.serviceInfo
            ComponentName(serviceInfo.packageName, serviceInfo.name) == component
        }
}

private fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
