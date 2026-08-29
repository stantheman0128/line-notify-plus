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
    val roomKey: String = chatTitle,   // 帳號(profile)+聊天室 的唯一 key（雙開區分用）
    val profileKey: String = "",       // 雙開帳號（Android user profile）
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var contentIntent: PendingIntent? = null,
    var replyAction: Notification.Action? = null,
    var senderIcon: Bitmap? = null, // 最近的發送者頭貼
) {
    companion object {
        // NotificationCompat.MessagingStyle 本身最多保留 25「則訊息」，不是 25 行文字。
        // 每一則仍保留 LINE android.messages 提供的完整本文；多留只會增加長駐 listener
        // 的 Bitmap/文字記憶體，系統通知也不會顯示。
        const val MAX_MESSAGES = 25
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        if (msg.senderIcon != null) senderIcon = msg.senderIcon
        while (messages.size > MAX_MESSAGES) {
            messages.removeAt(0)
        }
    }

    fun clearMessages() {
        // 多則訊息與 framework Icon 可能共用同一 Bitmap；手動 recycle 會讓仍在使用的
        // 通知或下一個 callback 讀到 recycled bitmap。交給 GC 統一管理生命週期。
        messages.clear()
        senderIcon = null
    }

    fun removeMessage(message: ChatMessage) {
        // ChatMessage 是 data class；同 sender/text/timestamp 的合法連發可能 value-equal。
        // rollback 必須移除該 callback 的 instance，不能誤刪第一則相同訊息。
        val index = messages.indexOfFirst { it === message }
        if (index >= 0) messages.removeAt(index)
        senderIcon = messages.lastOrNull()?.senderIcon
    }
}
