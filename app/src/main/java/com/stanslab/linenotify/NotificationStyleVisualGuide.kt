package com.stanslab.linenotify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NotificationStyleVisualGuide(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ThreadStylePreview()
        AppleStylePreview()
    }
}

@Composable
fun NotificationStyleGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        title = { Text(stringResource(R.string.style_guide_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NotificationStyleVisualGuide()
            }
        }
    )
}

@Composable
private fun ThreadStylePreview() {
    StylePreviewPanel(
        title = stringResource(R.string.style_thread_title),
        description = stringResource(R.string.style_help_thread_body)
    ) {
        MockPhoneFrame {
            MockNotificationCard(
                title = stringResource(R.string.style_preview_thread_chat_title),
                subtitle = stringResource(R.string.style_preview_thread_message_count)
            ) {
                ChatLine(
                    sender = stringResource(R.string.style_preview_sender_mina),
                    message = stringResource(R.string.style_preview_message_first)
                )
                ChatLine(
                    sender = stringResource(R.string.style_preview_sender_you),
                    message = stringResource(R.string.style_preview_message_reply)
                )
                ChatLine(
                    sender = stringResource(R.string.style_preview_sender_mina),
                    message = stringResource(R.string.style_preview_message_follow_up)
                )
            }
        }
    }
}

@Composable
private fun AppleStylePreview() {
    StylePreviewPanel(
        title = stringResource(R.string.style_apple_title),
        description = stringResource(R.string.style_help_apple_body)
    ) {
        MockPhoneFrame {
            MockNotificationCard(
                title = stringResource(R.string.style_preview_apple_title_first),
                subtitle = stringResource(R.string.style_preview_message_first)
            )
            MockNotificationCard(
                title = stringResource(R.string.style_preview_apple_title_reply),
                subtitle = stringResource(R.string.style_preview_message_reply)
            )
            MockNotificationCard(
                title = stringResource(R.string.style_preview_thread_chat_title),
                subtitle = stringResource(R.string.style_preview_apple_summary)
            )
        }
    }
}

@Composable
private fun StylePreviewPanel(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            content()
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MockPhoneFrame(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.style_preview_status_bar_time),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.style_preview_status_bar_icons),
                    fontSize = 11.sp
                )
            }
            content()
        }
    }
}

@Composable
private fun MockNotificationCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.style_preview_avatar_label),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                content()
            }
        }
    }
}

@Composable
private fun ChatLine(sender: String, message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(sender, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
