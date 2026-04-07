package com.stanslab.linenotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stanslab.linenotify.ui.theme.LineNotifyTheme
import kotlinx.coroutines.launch

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LineNotifyTheme {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App 資訊") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
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
            // App 名稱 + 版本
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "LINE Notify+",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v$currentVersion",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "LINE 通知增強工具",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 檢查更新
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("版本更新", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    updateInfo?.let { info ->
                        if (info.hasUpdate) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("有新版本 v${info.latestVersion}")
                                        if (!downloading) {
                                            Button(onClick = {
                                                val apk = info.apkUrl
                                                if (apk != null) {
                                                    downloading = true
                                                    scope.launch {
                                                        UpdateChecker.downloadAndInstall(context, apk) { progress ->
                                                            downloadProgress = progress
                                                        }
                                                        downloading = false
                                                    }
                                                } else {
                                                    UpdateChecker.openDownloadPage(context, info.downloadUrl)
                                                }
                                            }) {
                                                Text("一鍵更新")
                                            }
                                        }
                                    }
                                    if (downloading) {
                                        LinearProgressIndicator(
                                            progress = { downloadProgress / 100f },
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                        )
                                        Text(
                                            "下載中 $downloadProgress%",
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "已是最新版本",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            checking = true
                            scope.launch {
                                updateInfo = UpdateChecker.checkForUpdate(context)
                                checking = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !checking
                    ) {
                        Text(if (checking) "檢查中..." else "手動檢查更新")
                    }
                }
            }

            // 更新紀錄
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("更新紀錄", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    ChangelogEntry("v1.0.6", listOf(
                        "一鍵更新：App 內直接下載安裝新版本",
                        "修正返回按鈕顏色看不到的問題",
                    ))

                    HorizontalDivider()

                    ChangelogEntry("v1.0.5", listOf(
                        "訊息堆疊上限提高至 50 則",
                        "修正通知閃現即消失的問題",
                        "關閉取代模式時不再同步清除通知",
                        "App 資訊移至右上角 icon",
                    ))

                    HorizontalDivider()

                    ChangelogEntry("v1.0.4", listOf(
                        "新增 App 資訊頁面",
                        "版本更新紀錄、手動檢查更新",
                    ))

                    HorizontalDivider()

                    ChangelogEntry("v1.0.3", listOf(
                        "在 LINE 裡讀訊息後通知自動消失",
                        "聊天室管理支援社群/群組/個人三種分類",
                        "自動更新檢查",
                    ))

                    HorizontalDivider()

                    ChangelogEntry("v1.0.2", listOf(
                        "修正群組訊息不顯示群組名稱",
                        "修正點擊通知無法跳轉到聊天室",
                        "加入官方帳號按鈕",
                    ))

                    HorizontalDivider()

                    ChangelogEntry("v1.0.1", listOf(
                        "修正不同聊天室的訊息被錯誤合併",
                    ))

                    HorizontalDivider()

                    ChangelogEntry("v1.0.0", listOf(
                        "初始版本",
                        "對話串模式 / Apple 分組模式",
                        "快速回覆、頭貼顯示",
                        "個別聊天室開關",
                        "取代原始 LINE 通知",
                    ))
                }
            }

            // 連結
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("關於", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/stantheman0128/line-notify-plus")))
                    }) {
                        Text("GitHub 原始碼")
                    }

                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://line.me/ti/p/@687yglbr")))
                    }) {
                        Text("LINE 官方帳號 — 回報問題 & 功能建議")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogEntry(version: String, changes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(version, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        changes.forEach { change ->
            Text("• $change", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
