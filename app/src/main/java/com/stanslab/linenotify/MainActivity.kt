package com.stanslab.linenotify

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import com.stanslab.linenotify.service.LineNotificationListener
import com.stanslab.linenotify.ui.theme.LineNotifyTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 請求通知權限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 安裝更新後自動 rebind listener
        rebindListenerIfNeeded()

        setContent {
            LineNotifyTheme {
                MainScreen()
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
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(LineNotificationListener.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isListenerEnabled by remember { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(prefs.getBoolean(LineNotificationListener.KEY_SERVICE_ENABLED, true)) }
    var replaceOriginal by remember { mutableStateOf(prefs.getBoolean(LineNotificationListener.KEY_REPLACE_ORIGINAL, true)) }
    var notifStyle by remember { mutableStateOf(prefs.getString(LineNotificationListener.KEY_NOTIFICATION_STYLE, "thread") ?: "thread") }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isListenerEnabled = isNotificationListenerEnabled(context)
        // 每小時最多檢查一次更新
        val lastCheck = prefs.getLong("last_update_check", 0)
        if (System.currentTimeMillis() - lastCheck > 3600_000) {
            scope.launch {
                updateInfo = UpdateChecker.checkForUpdate(context)
                prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LINE Notify+") },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, AboutActivity::class.java))
                    }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "App 資訊",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服務狀態卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isListenerEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isListenerEnabled) "✓ 服務運行中" else "✗ 服務未啟用",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isListenerEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (isListenerEnabled)
                            "正在監聽 LINE 通知"
                        else
                            "需要授權通知存取權限才能運作",
                        color = if (isListenerEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 更新提示
            updateInfo?.takeIf { it.hasUpdate }?.let { info ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("有新版本 v${info.latestVersion}", fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = {
                            val apk = info.apkUrl
                            if (apk != null) {
                                scope.launch {
                                    UpdateChecker.downloadAndInstall(context, apk) {}
                                }
                            } else {
                                UpdateChecker.openDownloadPage(context, info.downloadUrl)
                            }
                        }) {
                            Text("更新")
                        }
                    }
                }
            }

            if (!isListenerEnabled) {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("開啟通知存取權限", fontSize = 16.sp)
                }
            }

            // 設定區域
            if (isListenerEnabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "設定",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        SettingToggle(
                            title = "啟用增強通知",
                            subtitle = "攔截 LINE 通知並重新組合顯示",
                            checked = serviceEnabled,
                            onCheckedChange = {
                                serviceEnabled = it
                                prefs.edit().putBoolean(LineNotificationListener.KEY_SERVICE_ENABLED, it).apply()
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingToggle(
                            title = "取代原始通知",
                            subtitle = "隱藏 LINE 原本的通知，只顯示增強版本",
                            checked = replaceOriginal,
                            enabled = serviceEnabled,
                            onCheckedChange = {
                                replaceOriginal = it
                                prefs.edit().putBoolean(LineNotificationListener.KEY_REPLACE_ORIGINAL, it).apply()
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 通知風格切換
                        Text(
                            text = "通知風格",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        StyleOption(
                            title = "對話串模式",
                            subtitle = "同聊天室合併成一則，顯示完整對話串",
                            selected = notifStyle == "thread",
                            enabled = serviceEnabled,
                            onClick = {
                                notifStyle = "thread"
                                prefs.edit().putString(LineNotificationListener.KEY_NOTIFICATION_STYLE, "thread").apply()
                            }
                        )
                        StyleOption(
                            title = "Apple 分組模式",
                            subtitle = "每則訊息獨立顯示，同聊天室自動收合",
                            selected = notifStyle == "apple",
                            enabled = serviceEnabled,
                            onClick = {
                                notifStyle = "apple"
                                prefs.edit().putString(LineNotificationListener.KEY_NOTIFICATION_STYLE, "apple").apply()
                            }
                        )
                    }
                }

                // 聊天室管理按鈕
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, ChatManagementActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("管理個別聊天室", fontSize = 16.sp)
                }

                // 功能說明
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "功能",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• 雙模式 — 對話串模式 or Apple 分組模式")
                        Text("• 快速回覆 — 直接在通知上回覆訊息")
                        Text("• 點擊即開 — 點通知跳轉 LINE 並自動清除")
                        Text("• 個別管理 — 每個聊天室可獨立開關")
                    }
                }

                // 官方帳號
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://line.me/ti/p/@687yglbr"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("加入 LINE Notify+ 官方帳號", fontSize = 16.sp)
                }
            }
        }
    }
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
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
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
