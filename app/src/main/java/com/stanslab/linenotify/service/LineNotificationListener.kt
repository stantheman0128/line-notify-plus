package com.stanslab.linenotify.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.stanslab.linenotify.model.ChatMessage
import com.stanslab.linenotify.model.ChatRoom

class LineNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "LineNotify"
        private val LINE_PACKAGES = setOf(
            "jp.naver.line.android",
            "com.linecorp.line",
        )
        private const val CHANNEL_ID = "line_notify_plus"
        private const val CHANNEL_NAME = "LINE Notify+"
        const val PREFS_NAME = "line_notify_prefs"
        const val KEY_REPLACE_ORIGINAL = "replace_original"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_DISABLED_CHATS = "disabled_chats"
        const val KEY_NOTIFICATION_STYLE = "notification_style"

        private const val SUMMARY_ID_BASE = 8000
        private const val MESSAGE_ID_BASE = 9000
        private const val THREAD_ID_BASE = 7000
    }

    private val chatRooms = mutableMapOf<String, ChatRoom>()
    private val summaryIds = mutableMapOf<String, Int>()
    private var nextSummaryId = SUMMARY_ID_BASE
    private var nextMessageId = MESSAGE_ID_BASE
    private val threadNotifIds = mutableMapOf<String, Int>()
    private var nextThreadId = THREAD_ID_BASE

    // 去重
    private val recentNotifications = mutableMapOf<String, Long>()
    private val recentDedupeKeys = mutableSetOf<String>()

    // 記錄每個聊天室對應的 Apple 模式通知 ID（用於清除）
    private val appleNotifIds = mutableMapOf<String, MutableList<Int>>()

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        Log.d(TAG, "LINE Notify+ 服務啟動")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in LINE_PACKAGES) return
        if (!prefs.getBoolean(KEY_SERVICE_ENABLED, true)) return

        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: return

        // 過濾 summary
        if (extras.getBoolean("android.isGroupSummary", false)) {
            if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
                cancelNotification(sbn.key)
            }
            return
        }

        // 過濾 LINE 的堆疊摘要通知（title 含有逗號+冒號，如「A, B：C」）
        if (title.contains("：") && title.contains(",")) {
            if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
                cancelNotification(sbn.key)
            }
            return
        }

        // 先解析聊天室，才能正確去重
        val isGroup = extras.getBoolean("android.isGroupConversation", false)
        val subText = extras.getCharSequence("android.subText")?.toString()

        val sender: String
        val chatTitle: String
        if (isGroup && subText != null) {
            sender = title
            chatTitle = subText
        } else {
            sender = title
            chatTitle = title
        }

        // 去重：LINE 對同一則訊息會發兩個通知（postTime 差幾毫秒）
        // 用「聊天室+內容+秒」去重，確保不同聊天室的相同訊息不會互相擋
        val timeSeconds = sbn.postTime / 1000
        val dedupeKey = "$chatTitle|$text|$timeSeconds"
        if (!recentDedupeKeys.add(dedupeKey)) {
            if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
                cancelNotification(sbn.key)
            }
            return
        }
        if (recentDedupeKeys.size > 100) {
            val toRemove = recentDedupeKeys.take(50)
            recentDedupeKeys.removeAll(toRemove.toSet())
        }

        // 檢查是否個別關閉
        val disabledChats = prefs.getStringSet(KEY_DISABLED_CHATS, emptySet()) ?: emptySet()
        if (chatTitle in disabledChats) return

        // 提取頭貼（LINE 可能用多種方式存放）
        val largeIcon: Bitmap? = try {
            // 方法 1: 標準 largeIcon
            @Suppress("DEPRECATION")
            (extras.getParcelable("android.largeIcon") as? Bitmap)
                // 方法 2: 從 Icon 物件轉換
                ?: notification.getLargeIcon()?.let { icon ->
                    val drawable = icon.loadDrawable(this)
                    if (drawable != null) {
                        val bmp = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    } else null
                }
        } catch (e: Exception) {
            Log.w(TAG, "提取頭貼失敗: ${e.message}")
            null
        }

        // 檢查此聊天室是否剛被清除過（用戶滑掉了通知）
        val clearedChats = prefs.getStringSet("cleared_chats", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (chatTitle in clearedChats) {
            chatRooms[chatTitle]?.clearMessages()
            clearedChats.remove(chatTitle)
            prefs.edit().putStringSet("cleared_chats", clearedChats).apply()
        }

        Log.d(TAG, "收到訊息 [$chatTitle] $sender: $text (群組=$isGroup, 有頭貼=${largeIcon != null})")

        val message = ChatMessage(
            sender = sender,
            text = text,
            timestamp = sbn.postTime,
            isGroup = isGroup,
            chatTitle = chatTitle,
            senderIcon = largeIcon,
        )

        val room = chatRooms.getOrPut(chatTitle) {
            ChatRoom(chatTitle = chatTitle, isGroup = isGroup)
        }
        room.addMessage(message)

        if (notification.contentIntent != null) {
            room.contentIntent = notification.contentIntent
        }
        notification.actions?.forEach { action ->
            if (action.remoteInputs?.isNotEmpty() == true) {
                room.replyAction = action
            }
        }

        saveKnownChat(chatTitle, isGroup)

        val style = prefs.getString(KEY_NOTIFICATION_STYLE, "thread") ?: "thread"
        when (style) {
            "apple" -> postAppleStyleNotification(room, message)
            else -> postThreadStyleNotification(room)
        }

        if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
            cancelNotification(sbn.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        if (sbn.packageName !in LINE_PACKAGES) return
    }

    // ==========================================
    // 對話串模式
    // ==========================================
    private fun postThreadStyleNotification(room: ChatRoom) {
        val notifId = threadNotifIds.getOrPut(room.chatTitle) { nextThreadId++ }
        val groupKey = "linenotify_${room.chatTitle}"

        val me = Person.Builder().setName("我").build()
        val msgStyle = NotificationCompat.MessagingStyle(me)

        if (room.isGroup) {
            msgStyle.conversationTitle = room.chatTitle
            msgStyle.isGroupConversation = true
        }

        for (msg in room.messages) {
            val personBuilder = Person.Builder().setName(msg.sender)
            // 設定發送者頭貼
            msg.senderIcon?.let { icon ->
                personBuilder.setIcon(IconCompat.createWithBitmap(icon))
            }
            msgStyle.addMessage(msg.text, msg.timestamp, personBuilder.build())
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.stanslab.linenotify.R.drawable.ic_notif)
            .setStyle(msgStyle)
            .setAutoCancel(true)
            .setOnlyAlertOnce(room.messages.size > 1)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF06C755.toInt())

        // 設定頭貼為大圖示
        room.senderIcon?.let { builder.setLargeIcon(it) }

        // 點擊跳轉 LINE + 清除緩衝
        room.contentIntent?.let { builder.setContentIntent(it) }

        // 通知被滑掉或點擊時清除緩衝區
        builder.setDeleteIntent(buildClearBufferIntent(room.chatTitle))

        addReplyAction(builder, room)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, builder.build())

        Log.d(TAG, "對話串通知 [${room.chatTitle}] 共 ${room.messages.size} 則")
    }

    // ==========================================
    // Apple 模式
    // ==========================================
    private fun postAppleStyleNotification(room: ChatRoom, newMessage: ChatMessage) {
        val groupKey = "linenotify_${room.chatTitle}"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val messageId = nextMessageId++
        // 記錄此聊天室的所有通知 ID
        appleNotifIds.getOrPut(room.chatTitle) { mutableListOf() }.add(messageId)

        val msgBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.stanslab.linenotify.R.drawable.ic_notif)
            .setContentTitle(
                if (room.isGroup) "${room.chatTitle} — ${newMessage.sender}"
                else newMessage.sender
            )
            .setContentText(newMessage.text)
            .setWhen(newMessage.timestamp)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF06C755.toInt())

        // 頭貼
        newMessage.senderIcon?.let { msgBuilder.setLargeIcon(it) }

        // 點擊跳轉 + 清除同組所有通知
        room.contentIntent?.let { lineIntent ->
            msgBuilder.setContentIntent(lineIntent)
        }
        msgBuilder.setDeleteIntent(buildClearBufferIntent(room.chatTitle))

        addReplyAction(msgBuilder, room)
        manager.notify(messageId, msgBuilder.build())

        // Summary
        val summaryId = summaryIds.getOrPut(room.chatTitle) { nextSummaryId++ }

        val summaryBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.stanslab.linenotify.R.drawable.ic_notif)
            .setContentTitle(room.chatTitle)
            .setContentText("${room.messages.size} 則訊息")
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        room.senderIcon?.let { summaryBuilder.setLargeIcon(it) }
        room.contentIntent?.let { summaryBuilder.setContentIntent(it) }
        summaryBuilder.setDeleteIntent(buildClearBufferIntent(room.chatTitle))

        manager.notify(summaryId, summaryBuilder.build())

        Log.d(TAG, "Apple 分組通知 [${room.chatTitle}] 第 ${room.messages.size} 則")
    }

    // ==========================================
    // 共用工具
    // ==========================================

    /**
     * 通知被滑掉或點擊消失時，清除該聊天室的訊息緩衝區
     */
    private fun buildClearBufferIntent(chatTitle: String): PendingIntent {
        val intent = Intent(this, NotificationDismissReceiver::class.java).apply {
            action = NotificationDismissReceiver.ACTION_CLEAR_BUFFER
            putExtra(NotificationDismissReceiver.EXTRA_CHAT_TITLE, chatTitle)
        }
        return PendingIntent.getBroadcast(
            this,
            chatTitle.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun addReplyAction(builder: NotificationCompat.Builder, room: ChatRoom) {
        room.replyAction?.let { lineAction ->
            lineAction.remoteInputs?.firstOrNull()?.let { remoteInput ->
                val replyInput = androidx.core.app.RemoteInput.Builder(remoteInput.resultKey)
                    .setLabel(remoteInput.label ?: "回覆")
                    .build()
                val action = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send, "回覆", lineAction.actionIntent
                ).addRemoteInput(replyInput).setAllowGeneratedReplies(true).build()
                builder.addAction(action)
            }
        }
    }

    private fun saveKnownChat(chatTitle: String, isGroup: Boolean) {
        val key = if (isGroup) "known_groups" else "known_chats"
        val known = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (known.add(chatTitle)) {
            prefs.edit().putStringSet(key, known).apply()
        }
    }

    /**
     * 外部呼叫：清除特定聊天室的訊息緩衝區
     */
    fun clearChatBuffer(chatTitle: String) {
        chatRooms[chatTitle]?.clearMessages()
        Log.d(TAG, "已清除 [$chatTitle] 的訊息緩衝")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "LINE Notify+ 增強通知"
            enableVibration(true)
            setShowBadge(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
