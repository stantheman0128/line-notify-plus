package com.stanslab.linenotify

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stanslab.linenotify.ui.theme.LineNotifyTheme

class HelpActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            LineNotifyTheme {
                HelpScreen(
                    windowWidthSizeClass = windowSizeClass.widthSizeClass,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit
) {
    val twoPaneFromResources = booleanResource(R.bool.use_two_pane_layout)
    val useTwoPane = twoPaneFromResources ||
        windowWidthSizeClass == WindowWidthSizeClass.Medium ||
        windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val horizontalPadding = dimensionResource(R.dimen.screen_horizontal_padding)
    val verticalPadding = dimensionResource(R.dimen.screen_vertical_padding)
    val paneSpacing = dimensionResource(R.dimen.pane_spacing)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
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
        val pagePadding = PaddingValues(
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
                    .padding(pagePadding),
                horizontalArrangement = Arrangement.spacedBy(paneSpacing)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HelpIntroCard()
                    OnboardingCard()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NotificationStyleGuideCard()
                    FaqCard()
                    ReportIssueCard()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(pagePadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HelpIntroCard()
                OnboardingCard()
                NotificationStyleGuideCard()
                FaqCard()
                ReportIssueCard()
            }
        }
    }
}

@Composable
private fun HelpIntroCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.help_intro_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.help_intro_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OnboardingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            StepItem(
                title = stringResource(R.string.onboarding_step_1_title),
                body = stringResource(R.string.onboarding_step_1_body)
            )
            StepItem(
                title = stringResource(R.string.onboarding_step_2_title),
                body = stringResource(R.string.onboarding_step_2_body)
            )
            StepItem(
                title = stringResource(R.string.onboarding_step_3_title),
                body = stringResource(R.string.onboarding_step_3_body)
            )
            StepItem(
                title = stringResource(R.string.onboarding_step_4_title),
                body = stringResource(R.string.onboarding_step_4_body)
            )
        }
    }
}

@Composable
private fun NotificationStyleGuideCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.style_help_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            StepItem(
                title = stringResource(R.string.style_thread_title),
                body = stringResource(R.string.style_help_thread_body)
            )
            HorizontalDivider()
            StepItem(
                title = stringResource(R.string.style_apple_title),
                body = stringResource(R.string.style_help_apple_body)
            )
        }
    }
}

@Composable
private fun FaqCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.faq_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            FaqItem(
                question = stringResource(R.string.faq_q_permission),
                answer = stringResource(R.string.faq_a_permission)
            )
            HorizontalDivider()
            FaqItem(
                question = stringResource(R.string.faq_q_network),
                answer = stringResource(R.string.faq_a_network)
            )
            HorizontalDivider()
            FaqItem(
                question = stringResource(R.string.faq_q_original),
                answer = stringResource(R.string.faq_a_original)
            )
            HorizontalDivider()
            FaqItem(
                question = stringResource(R.string.faq_q_reply),
                answer = stringResource(R.string.faq_a_reply)
            )
        }
    }
}

@Composable
private fun ReportIssueCard() {
    val context = LocalContext.current
    val officialUrl = stringResource(R.string.line_official_url)
    val githubIssuesUrl = stringResource(R.string.github_issues_url)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.report_issue_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.report_issue_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { context.openExternalUri(officialUrl) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.report_issue_line))
            }
            OutlinedButton(
                onClick = { context.openExternalUri(githubIssuesUrl) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.report_issue_github))
            }
        }
    }
}

@Composable
private fun StepItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(question, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(answer, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
