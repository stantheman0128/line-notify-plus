package com.stanslab.linenotify.model

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap

data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isGroup: Boolean,
    val chatTitle: String,
    val senderIcon: Bitmap? = null, // 發送者頭貼
    val isFromMe: Boolean = false, // 是否為本人（快速回覆）所發
)

data class ChatRoom(
    val chatTitle: String,
    val isGroup: Boolean,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var contentIntent: PendingIntent? = null,
    var replyAction: Notification.Action? = null,
    var senderIcon: Bitmap? = null, // 最近的發送者頭貼
) {
    companion object {
        const val MAX_MESSAGES = 50
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        if (msg.senderIcon != null) senderIcon = msg.senderIcon
        while (messages.size > MAX_MESSAGES) {
            messages.removeAt(0)
        }
    }

    fun clearMessages() {
        messages.forEach { it.senderIcon?.recycle() }
        messages.clear()
        senderIcon = null
    }
}
