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
import com.stanslab.linenotify.R
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
        const val PREFS_NAME = "line_notify_prefs"
        const val KEY_REPLACE_ORIGINAL = "replace_original"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_DISABLED_CHATS = "disabled_chats"
        const val KEY_NOTIFICATION_STYLE = "notification_style"
        const val KEY_CLEAR_AFTER_REPLY = "clear_after_reply"
        const val KEY_CLEAR_AFTER_READ = "clear_after_read"

        // 我們程式自己取消、不該再觸發「整組清除」連鎖的通知 id。
        // 跨 ReplyRelayReceiver 共用（同一 process，故用靜態集合）。
        val suppressedRemovalIds: MutableSet<Int> =
            java.util.Collections.synchronizedSet(mutableSetOf())

        // 服務 instance（給 ReplyRelayReceiver 在同 process 呼叫 handleUserReply）
        @Volatile
        var instance: LineNotificationListener? = null

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

    // 記錄最近處理過的 chatTitle，用於 onNotificationRemoved 判斷是否為連鎖反應
    private val recentlyProcessed = mutableMapOf<String, Long>()

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        Log.d(TAG, "LINE Notify+ 服務啟動")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
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

        // 檢查我們是否還有該聊天室的活躍通知
        // 如果沒有（被用戶滑掉了），先清緩衝再堆疊新訊息
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ourNotifId = threadNotifIds[chatTitle]
        if (ourNotifId != null) {
            val stillActive = manager.activeNotifications.any { it.id == ourNotifId }
            if (!stillActive) {
                chatRooms[chatTitle]?.clearMessages()
                Log.d(TAG, "通知已被滑掉，清除 [$chatTitle] 緩衝")
            }
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
            recentlyProcessed[chatTitle] = System.currentTimeMillis()
            handler.postDelayed({ cancelNotification(key) }, 200)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        // (A) 我們「自己」的通知被移除（用戶滑掉 / 點掉開 LINE / 回覆關閉）
        //     → 視為「這個聊天室已處理」，清掉整組通知 + buffer，避免殘留或舊訊息又冒回來。
        if (sbn.packageName == packageName) {
            val removedId = sbn.id
            // 我們程式自己取消的（clearChatGroup 連鎖、或「回覆後不清除」）不要再連鎖
            if (suppressedRemovalIds.remove(removedId)) return
            val chatTitle = findChatByNotifId(removedId) ?: return
            clearChatGroup(chatTitle)
            Log.d(TAG, "本機通知被移除，清整組 [$chatTitle]")
            return
        }

        if (sbn.packageName !in LINE_PACKAGES) return

        // 非取代模式下不同步清除（兩邊通知獨立）
        if (!prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) return
        // 「已讀後自動清除」開關
        if (!prefs.getBoolean(KEY_CLEAR_AFTER_READ, true)) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: return
        val subText = extras.getCharSequence("android.subText")?.toString()
        val chatTitle = subText ?: title

        // 最近 2 秒內我們處理過 = 我們自己取消 LINE 原通知的連鎖反應，不做任何事
        val processedTime = recentlyProcessed[chatTitle]
        if (processedTime != null && System.currentTimeMillis() - processedTime < 2000) return

        // 用戶在 LINE 裡讀了訊息 → 同步清除我們的通知
        clearChatGroup(chatTitle)
        Log.d(TAG, "用戶已讀，同步清除 [$chatTitle] 的通知")
    }

    /**
     * 清掉某聊天室的所有通知（thread / apple children / summary）+ in-memory buffer。
     * 取消前先把 id 記進 suppressedRemovalIds，避免 onNotificationRemoved 連鎖再清一次。
     */
    private fun clearChatGroup(chatTitle: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ids = mutableListOf<Int>()
        threadNotifIds.remove(chatTitle)?.let { ids.add(it) }
        summaryIds.remove(chatTitle)?.let { ids.add(it) }
        appleNotifIds.remove(chatTitle)?.let { ids.addAll(it) }
        ids.forEach {
            suppressedRemovalIds.add(it)
            manager.cancel(it)
        }
        chatRooms[chatTitle]?.clearMessages()
    }

    /** 由通知 id 反查所屬聊天室 */
    private fun findChatByNotifId(id: Int): String? {
        threadNotifIds.entries.firstOrNull { it.value == id }?.let { return it.key }
        summaryIds.entries.firstOrNull { it.value == id }?.let { return it.key }
        appleNotifIds.entries.firstOrNull { id in it.value }?.let { return it.key }
        return null
    }

    /**
     * 處理用戶的快速回覆（由 ReplyRelayReceiver 轉發給 LINE 後呼叫，同 process）。
     * - 「回覆後清除」ON：整組清掉（同時停掉系統的回覆 spinner）。
     * - OFF：把回覆「加進對話」並重貼通知 → 回覆留得住、不會被下一則訊息重建洗掉，spinner 也停。
     */
    fun handleUserReply(chatTitle: String, notifId: Int, replyText: CharSequence) {
        val room = chatRooms[chatTitle]
        if (room == null) {
            // 沒有對話狀態 → 至少關掉這則停 spinner
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)
            return
        }
        // 一律先「把回覆加進對話 + 重貼通知」：
        // update（重貼）會「接管」系統的樂觀回覆、停掉 spinner，並用本人綠頭貼顯示回覆，
        // 也讓回覆留得住、不被下一則訊息重建洗掉。
        // （實機驗出來：直接 cancel 會跟系統樂觀回覆搶輸、被蓋回來；update 才贏。）
        room.addMessage(
            ChatMessage(
                sender = getString(R.string.notification_self_person),
                text = replyText.toString(),
                timestamp = System.currentTimeMillis(),
                isGroup = room.isGroup,
                chatTitle = chatTitle,
                senderIcon = null,
                isFromMe = true,
            )
        )
        val style = prefs.getString(KEY_NOTIFICATION_STYLE, "thread") ?: "thread"
        if (style == "apple") postAppleStyleNotification(room, room.messages.last())
        else postThreadStyleNotification(room)

        if (prefs.getBoolean(KEY_CLEAR_AFTER_REPLY, true)) {
            // 「回覆後清除」：等系統樂觀回覆狀態落定後再整組清掉，避免被蓋回來。
            handler.postDelayed({ clearChatGroup(chatTitle) }, 500)
            Log.d(TAG, "回覆後延遲清除整組 [$chatTitle]")
        } else {
            Log.d(TAG, "回覆已加入對話並保留 [$chatTitle]")
        }
    }

    // ==========================================
    // 對話串模式
    // ==========================================
    private fun postThreadStyleNotification(room: ChatRoom) {
        val notifId = threadNotifIds.getOrPut(room.chatTitle) { nextThreadId++ }

        val me = Person.Builder()
            .setName(getString(R.string.notification_self_person))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_self_avatar))
            .build()
        val msgStyle = NotificationCompat.MessagingStyle(me)

        if (room.isGroup) {
            msgStyle.conversationTitle = room.chatTitle
            msgStyle.isGroupConversation = true
        }

        for (msg in room.messages) {
            val person = if (msg.isFromMe) {
                me
            } else {
                Person.Builder().setName(msg.sender).apply {
                    msg.senderIcon?.let { setIcon(IconCompat.createWithBitmap(it)) }
                }.build()
            }
            msgStyle.addMessage(msg.text, msg.timestamp, person)
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

        addReplyAction(builder, room, notifId)

        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(notifId, builder.build())

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
                if (room.isGroup) {
                    getString(R.string.notification_group_title, room.chatTitle, newMessage.sender)
                }
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

        addReplyAction(msgBuilder, room, messageId)
        manager.notify(messageId, msgBuilder.build())

        // Summary
        val summaryId = summaryIds.getOrPut(room.chatTitle) { nextSummaryId++ }

        val summaryBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.stanslab.linenotify.R.drawable.ic_notif)
            .setContentTitle(room.chatTitle)
            .setContentText(getString(R.string.notification_summary_text, room.messages.size))
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        room.senderIcon?.let { summaryBuilder.setLargeIcon(it) }
        room.contentIntent?.let { summaryBuilder.setContentIntent(it) }

        manager.notify(summaryId, summaryBuilder.build())

        Log.d(TAG, "Apple 分組通知 [${room.chatTitle}] 第 ${room.messages.size} 則")
    }

    // ==========================================
    // 共用
    // ==========================================

    private fun addReplyAction(builder: NotificationCompat.Builder, room: ChatRoom, notifId: Int) {
        room.replyAction?.let { lineAction ->
            val lineActionIntent = lineAction.actionIntent ?: return@let
            lineAction.remoteInputs?.firstOrNull()?.let { remoteInput ->
                val resultKey = remoteInput.resultKey
                val replyInput = androidx.core.app.RemoteInput.Builder(resultKey)
                    .setLabel(remoteInput.label ?: getString(R.string.notification_reply_action))
                    .build()

                // 用我們自己的 PendingIntent 包住 LINE 的 reply action：
                // 回覆送出後 ReplyRelayReceiver 會轉發給 LINE 並取消本通知，
                // 否則系統的回覆 spinner 會一直卡住轉圈圈。
                val relayIntent = Intent(this, ReplyRelayReceiver::class.java).apply {
                    action = ReplyRelayReceiver.ACTION_REPLY
                    putExtra(ReplyRelayReceiver.EXTRA_RESULT_KEY, resultKey)
                    putExtra(ReplyRelayReceiver.EXTRA_LINE_PENDING_INTENT, lineActionIntent)
                    putExtra(ReplyRelayReceiver.EXTRA_NOTIF_ID, notifId)
                    putExtra(ReplyRelayReceiver.EXTRA_CHAT_TITLE, room.chatTitle)
                }
                val mutableFlag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else 0
                val relayPendingIntent = PendingIntent.getBroadcast(
                    this, notifId, relayIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
                )

                val action = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    getString(R.string.notification_reply_action),
                    relayPendingIntent
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
            CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
