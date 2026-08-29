package com.stanslab.linenotify

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stanslab.linenotify.ui.theme.LineNotifyTheme

class AboutActivity : AppCompatActivity() {
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
    val horizontalPadding = dimensionResource(R.dimen.screen_horizontal_padding)
    val verticalPadding = dimensionResource(R.dimen.screen_vertical_padding)
    val githubUrl = stringResource(R.string.github_url)
    val githubIssuesUrl = stringResource(R.string.github_issues_url)
    val officialUrl = stringResource(R.string.line_official_url)

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: context.getString(R.string.unknown_version)
        } catch (e: Exception) {
            context.getString(R.string.unknown_version)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.app_version_format, currentVersion),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.changelog_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    ChangelogEntry(
                        "v1.6.0",
                        listOf(
                            stringResource(R.string.changelog_1_6_0_home),
                            stringResource(R.string.changelog_1_6_0_chats),
                            stringResource(R.string.changelog_1_6_0_read_sync),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.5.0",
                        listOf(
                            stringResource(R.string.changelog_1_5_0_accessibility),
                            stringResource(R.string.changelog_1_5_0_migration),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.4.5",
                        listOf(
                            stringResource(R.string.changelog_1_4_5_reliability),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.4.4",
                        listOf(
                            stringResource(R.string.changelog_1_4_4_readsync),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.4.3",
                        listOf(
                            stringResource(R.string.changelog_1_4_3_loop),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.4.2",
                        listOf(
                            stringResource(R.string.changelog_1_4_2_speed),
                            stringResource(R.string.changelog_1_4_2_dupe),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.4.1",
                        listOf(
                            stringResource(R.string.changelog_1_4_1_readsync),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.3.1",
                        listOf(
                            stringResource(R.string.changelog_1_3_1_dupe),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.3.0",
                        listOf(
                            stringResource(R.string.changelog_1_3_0_redacted),
                            stringResource(R.string.changelog_1_3_0_mute),
                            stringResource(R.string.changelog_1_3_0_type),
                            stringResource(R.string.changelog_1_3_0_dupe),
                            stringResource(R.string.changelog_1_3_0_permission),
                            stringResource(R.string.changelog_1_3_0_safety),
                            stringResource(R.string.changelog_1_3_0_lockscreen),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.2.1",
                        listOf(
                            stringResource(R.string.changelog_1_2_1_readsync),
                            stringResource(R.string.changelog_1_2_1_info),
                            stringResource(R.string.changelog_1_2_1_rename),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.2.0",
                        listOf(
                            stringResource(R.string.changelog_1_2_0_reply),
                            stringResource(R.string.changelog_1_2_0_clear),
                            stringResource(R.string.changelog_1_2_0_dual),
                            stringResource(R.string.changelog_1_2_0_ui),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.1.0",
                        listOf(
                            stringResource(R.string.changelog_1_1_0_icon),
                            stringResource(R.string.changelog_1_1_0_play),
                            stringResource(R.string.changelog_1_1_0_stability),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.0.6",
                        listOf(
                            stringResource(R.string.changelog_1_0_6_updater),
                            stringResource(R.string.changelog_1_0_6_back),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.0.5",
                        listOf(
                            stringResource(R.string.changelog_1_0_5_stack),
                            stringResource(R.string.changelog_1_0_5_flash),
                            stringResource(R.string.changelog_1_0_5_replace),
                            stringResource(R.string.changelog_1_0_5_about),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.0.4",
                        listOf(
                            stringResource(R.string.changelog_1_0_4_about),
                            stringResource(R.string.changelog_1_0_4_manual_update),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.0.3",
                        listOf(
                            stringResource(R.string.changelog_1_0_3_read),
                            stringResource(R.string.changelog_1_0_3_chat_management),
                            stringResource(R.string.changelog_1_0_3_auto_update),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.0.2",
                        listOf(
                            stringResource(R.string.changelog_1_0_2_group),
                            stringResource(R.string.changelog_1_0_2_jump),
                            stringResource(R.string.changelog_1_0_2_official),
                        )
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.0.1",
                        listOf(stringResource(R.string.changelog_1_0_1_merge))
                    )

                    HorizontalDivider()

                    ChangelogEntry(
                        "v1.0.0",
                        listOf(
                            stringResource(R.string.changelog_1_0_0_initial),
                            stringResource(R.string.changelog_1_0_0_modes),
                            stringResource(R.string.changelog_1_0_0_reply_avatar),
                            stringResource(R.string.changelog_1_0_0_chat_switch),
                            stringResource(R.string.changelog_1_0_0_replace),
                        )
                    )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.about_links_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = { context.openExternalUri(githubUrl) }) {
                        Text(stringResource(R.string.github_source))
                    }

                    TextButton(onClick = { context.openExternalUri(githubIssuesUrl) }) {
                        Text(stringResource(R.string.github_issues))
                    }

                    TextButton(onClick = { context.openExternalUri(officialUrl) }) {
                        Text(stringResource(R.string.official_account_report))
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
            Text(
                "• $change",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
