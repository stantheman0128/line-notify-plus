package com.stanslab.linenotify.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
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

    private val recentDedupeKeys = mutableSetOf<String>()
    private val appleNotifIds = mutableMapOf<String, MutableList<Int>>()

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

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

        // 過濾 LINE 的堆疊摘要通知（title 含逗號+冒號）
        if (title.contains("：") && title.contains(",")) {
            if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
                cancelNotification(sbn.key)
            }
            return
        }

        // 解析聊天室類型
        // LINE 群組/社群通知：subText = 群組名或社群名, title = 發送者
        // LINE 個人通知：subText = null, title = 發送者
        val subText = extras.getCharSequence("android.subText")?.toString()
        val channelId = sbn.notification.channelId ?: ""
        val tag = sbn.tag ?: ""
        val shortcutId = extras.getString("android.shortcutId") ?: ""

        // 判斷聊天類型：個人 / 群組 / 社群
        // 社群(OpenChat) 的 shortcutId 通常以 "c" 開頭，群組以 "g" 開頭，個人以 "u" 開頭
        val chatType: String
        val sender: String
        val chatTitle: String

        if (subText != null) {
            sender = title
            chatTitle = subText
            chatType = when {
                shortcutId.startsWith("c") -> "community"
                else -> "group"
            }
        } else {
            sender = title
            chatTitle = title
            chatType = "personal"
        }
        val isGroup = chatType != "personal"

        // Debug: 記錄通知結構幫助分析
        Log.v(TAG, "通知結構 channelId=$channelId tag=$tag shortcutId=$shortcutId chatType=$chatType")

        // 去重
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

        // 提取頭貼
        val largeIcon: Bitmap? = try {
            @Suppress("DEPRECATION")
            (extras.getParcelable("android.largeIcon") as? Bitmap)
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
        } catch (e: Exception) { null }

        // 檢查緩衝區是否需要清除
        val clearedChats = prefs.getStringSet("cleared_chats", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (chatTitle in clearedChats) {
            chatRooms[chatTitle]?.clearMessages()
            clearedChats.remove(chatTitle)
            prefs.edit().putStringSet("cleared_chats", clearedChats).apply()
        }

        Log.d(TAG, "收到訊息 [$chatTitle] $sender: $text (群組=$isGroup)")

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

        // 保存 LINE 的 contentIntent 和回覆 action（在取消通知前）
        if (notification.contentIntent != null) {
            room.contentIntent = notification.contentIntent
        }
        notification.actions?.forEach { action ->
            if (action.remoteInputs?.isNotEmpty() == true) {
                room.replyAction = action
            }
        }

        saveKnownChat(chatTitle, chatType)

        // 先發我們的通知
        val style = prefs.getString(KEY_NOTIFICATION_STYLE, "thread") ?: "thread"
        when (style) {
            "apple" -> postAppleStyleNotification(room, message)
            else -> postThreadStyleNotification(room)
        }

        // 延遲取消 LINE 原通知（確保我們的 contentIntent 不會因為 LINE 通知被取消而失效）
        if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
            val key = sbn.key
            handler.postDelayed({ cancelNotification(key) }, 200)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        if (sbn.packageName !in LINE_PACKAGES) return

        // 當 LINE 的通知被移除（用戶在 LINE 裡讀了訊息），同時移除我們的對應通知
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: return
        val subText = extras.getCharSequence("android.subText")?.toString()
        val chatTitle = subText ?: title

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 移除對話串模式的通知
        threadNotifIds[chatTitle]?.let { notifId ->
            manager.cancel(notifId)
            Log.d(TAG, "LINE 通知被移除，同步清除 [$chatTitle] 的通知")
        }

        // 移除 Apple 模式的通知
        appleNotifIds[chatTitle]?.forEach { notifId ->
            manager.cancel(notifId)
        }
        appleNotifIds.remove(chatTitle)

        // 移除 summary
        summaryIds[chatTitle]?.let { manager.cancel(it) }

        // 清除緩衝區
        chatRooms[chatTitle]?.clearMessages()
    }

    // ==========================================
    // 對話串模式
    // ==========================================
    private fun postThreadStyleNotification(room: ChatRoom) {
        val notifId = threadNotifIds.getOrPut(room.chatTitle) { nextThreadId++ }

        val me = Person.Builder().setName("我").build()
        val msgStyle = NotificationCompat.MessagingStyle(me)

        if (room.isGroup) {
            msgStyle.conversationTitle = room.chatTitle
            msgStyle.isGroupConversation = true
        }

        for (msg in room.messages) {
            val personBuilder = Person.Builder().setName(msg.sender)
            msg.senderIcon?.let { personBuilder.setIcon(IconCompat.createWithBitmap(it)) }
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

        room.senderIcon?.let { builder.setLargeIcon(it) }
        room.contentIntent?.let { builder.setContentIntent(it) }
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

        newMessage.senderIcon?.let { msgBuilder.setLargeIcon(it) }
        room.contentIntent?.let { msgBuilder.setContentIntent(it) }
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
    // 共用
    // ==========================================

    private fun buildClearBufferIntent(chatTitle: String): PendingIntent {
        val intent = Intent(this, NotificationDismissReceiver::class.java).apply {
            action = NotificationDismissReceiver.ACTION_CLEAR_BUFFER
            putExtra(NotificationDismissReceiver.EXTRA_CHAT_TITLE, chatTitle)
        }
        return PendingIntent.getBroadcast(
            this, chatTitle.hashCode(), intent,
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

    private fun saveKnownChat(chatTitle: String, chatType: String) {
        val key = when (chatType) {
            "community" -> "known_communities"
            "group" -> "known_groups"
            else -> "known_chats"
        }
        val known = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (known.add(chatTitle)) {
            prefs.edit().putStringSet(key, known).apply()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "LINE Notify+ 增強通知"
            enableVibration(true)
            setShowBadge(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
