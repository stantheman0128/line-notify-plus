package com.stanslab.linenotify

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.stanslab.linenotify.service.LineNotificationListener
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
        val componentName = ComponentName(this, LineNotificationListener::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
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

    val twoPaneFromResources = booleanResource(R.bool.use_two_pane_layout)
    val useTwoPane = twoPaneFromResources ||
        windowWidthSizeClass == WindowWidthSizeClass.Medium ||
        windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val horizontalPadding = dimensionResource(R.dimen.screen_horizontal_padding)
    val verticalPadding = dimensionResource(R.dimen.screen_vertical_padding)
    val paneSpacing = dimensionResource(R.dimen.pane_spacing)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isListenerEnabled = isNotificationListenerEnabled(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                actions = {
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
                    MainActions(
                        isListenerEnabled = isListenerEnabled,
                        onOpenPermissionSettings = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onOpenChatManagement = {
                            context.startActivity(Intent(context, ChatManagementActivity::class.java))
                        },
                        onOpenHelp = {
                            context.startActivity(Intent(context, HelpActivity::class.java))
                        },
                        onShare = { context.shareLineNotifyPlus() }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isListenerEnabled) {
                        SettingsCard(
                            serviceEnabled = serviceEnabled,
                            replaceOriginal = replaceOriginal,
                            notifStyle = notifStyle,
                            languageTag = languageTag,
                            onServiceEnabledChange = {
                                serviceEnabled = it
                                prefs.edit()
                                    .putBoolean(LineNotificationListener.KEY_SERVICE_ENABLED, it)
                                    .apply()
                            },
                            onReplaceOriginalChange = {
                                replaceOriginal = it
                                prefs.edit()
                                    .putBoolean(LineNotificationListener.KEY_REPLACE_ORIGINAL, it)
                                    .apply()
                            },
                            onNotificationStyleChange = {
                                notifStyle = it
                                prefs.edit()
                                    .putString(LineNotificationListener.KEY_NOTIFICATION_STYLE, it)
                                    .apply()
                            },
                            onLanguageChange = {
                                languageTag = it
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(it)
                                )
                            }
                        )
                    }
                    HelpSummaryCard()
                    FeatureCard()
                    OfficialAccountButton()
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
                MainActions(
                    isListenerEnabled = isListenerEnabled,
                    onOpenPermissionSettings = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenChatManagement = {
                        context.startActivity(Intent(context, ChatManagementActivity::class.java))
                    },
                    onOpenHelp = {
                        context.startActivity(Intent(context, HelpActivity::class.java))
                    },
                    onShare = { context.shareLineNotifyPlus() }
                )

                if (isListenerEnabled) {
                    SettingsCard(
                        serviceEnabled = serviceEnabled,
                        replaceOriginal = replaceOriginal,
                        notifStyle = notifStyle,
                        languageTag = languageTag,
                        onServiceEnabledChange = {
                            serviceEnabled = it
                            prefs.edit()
                                .putBoolean(LineNotificationListener.KEY_SERVICE_ENABLED, it)
                                .apply()
                        },
                        onReplaceOriginalChange = {
                            replaceOriginal = it
                            prefs.edit()
                                .putBoolean(LineNotificationListener.KEY_REPLACE_ORIGINAL, it)
                                .apply()
                        },
                        onNotificationStyleChange = {
                            notifStyle = it
                            prefs.edit()
                                .putString(LineNotificationListener.KEY_NOTIFICATION_STYLE, it)
                                .apply()
                        },
                        onLanguageChange = {
                            languageTag = it
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(it)
                            )
                        }
                    )
                    HelpSummaryCard()
                    FeatureCard()
                    OfficialAccountButton()
                } else {
                    HelpSummaryCard()
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
private fun MainActions(
    isListenerEnabled: Boolean,
    onOpenPermissionSettings: () -> Unit,
    onOpenChatManagement: () -> Unit,
    onOpenHelp: () -> Unit,
    onShare: () -> Unit
) {
    if (!isListenerEnabled) {
        Button(
            onClick = onOpenPermissionSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.open_notification_access), fontSize = 16.sp)
        }
    } else {
        OutlinedButton(
            onClick = onOpenChatManagement,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.manage_chats), fontSize = 16.sp)
        }
    }

    OutlinedButton(
        onClick = onOpenHelp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.open_help), fontSize = 16.sp)
    }

    OutlinedButton(
        onClick = onShare,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.action_share_app), fontSize = 16.sp)
    }
}

@Composable
private fun SettingsCard(
    serviceEnabled: Boolean,
    replaceOriginal: Boolean,
    notifStyle: String,
    languageTag: String,
    onServiceEnabledChange: (Boolean) -> Unit,
    onReplaceOriginalChange: (Boolean) -> Unit,
    onNotificationStyleChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit
) {
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
                onCheckedChange = onReplaceOriginalChange
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionLabel(stringResource(R.string.notification_style_title))
            StyleOption(
                title = stringResource(R.string.style_thread_title),
                subtitle = stringResource(R.string.style_thread_subtitle),
                selected = notifStyle == "thread",
                enabled = serviceEnabled,
                onClick = { onNotificationStyleChange("thread") }
            )
            StyleOption(
                title = stringResource(R.string.style_apple_title),
                subtitle = stringResource(R.string.style_apple_subtitle),
                selected = notifStyle == "apple",
                enabled = serviceEnabled,
                onClick = { onNotificationStyleChange("apple") }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionLabel(stringResource(R.string.language_title))
            Text(
                text = stringResource(R.string.language_subtitle),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LocaleOption(
                title = stringResource(R.string.language_follow_system),
                selected = languageTag.isEmpty(),
                onClick = { onLanguageChange("") }
            )
            LocaleOption(
                title = stringResource(R.string.language_zh_tw),
                selected = languageTag == "zh-TW",
                onClick = { onLanguageChange("zh-TW") }
            )
            LocaleOption(
                title = stringResource(R.string.language_en),
                selected = languageTag == "en",
                onClick = { onLanguageChange("en") }
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(12.dp))
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
    }
}

@Composable
private fun LocaleOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun HelpSummaryCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.help_card_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.help_card_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeatureCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.feature_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.feature_dual_modes))
            Text(stringResource(R.string.feature_quick_reply))
            Text(stringResource(R.string.feature_jump))
            Text(stringResource(R.string.feature_chat_management))
        }
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
            containerColor = MaterialTheme.colorScheme.primary
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
