package com.stanslab.linenotify.service

import android.app.Notification
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
        const val KEY_CHAT_LAST_ACTIVE = "chat_last_active"   // JSON {聊天室名: epochMillis}
        const val KEY_CHAT_SORT = "chat_sort"                 // "recent" | "name" | "type"
        private const val AVATAR_DIR = "chat_avatars"

        // roomKey 分隔字元：profileKey 是純數字（hashCode），用 ":" 即保證 (profile,聊天室) 唯一對應
        private const val KEY_SEP = ":"

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

        /** 某聊天室頭貼的本機檔案（用名字 hash 當檔名，避開 emoji/特殊字元）。 */
        fun avatarFile(context: Context, chatName: String): java.io.File =
            java.io.File(java.io.File(context.filesDir, AVATAR_DIR), "${chatName.hashCode()}.png")

        /** 讀「最後活躍時間」表（聊天室名 → epochMillis）。 */
        fun readLastActive(prefs: SharedPreferences): Map<String, Long> {
            val raw = prefs.getString(KEY_CHAT_LAST_ACTIVE, null) ?: return emptyMap()
            return try {
                val obj = org.json.JSONObject(raw)
                buildMap { obj.keys().forEach { k -> put(k, obj.optLong(k)) } }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    // 以下所有 map 的 key 都是 roomKey = profileKey + 聊天室名（雙開帳號各自獨立）
    private val chatRooms = mutableMapOf<String, ChatRoom>()
    private val summaryIds = mutableMapOf<String, Int>()
    private var nextSummaryId = SUMMARY_ID_BASE
    private var nextMessageId = MESSAGE_ID_BASE
    private val threadNotifIds = mutableMapOf<String, Int>()
    private var nextThreadId = THREAD_ID_BASE

    private val recentDedupeKeys = mutableSetOf<String>()
    private val appleNotifIds = mutableMapOf<String, MutableList<Int>>()

    // 每個帳號（雙開 profile）一張「本人」頭貼 + 顯示名，key = profileKey。
    private val selfPersonIcons = mutableMapOf<String, IconCompat>()
    private val accountLabels = mutableMapOf<String, String>()
    // 看過的帳號 profile；>1 才在通知標題標出帳號來源。
    private val knownProfiles = mutableSetOf<String>()

    // 記錄最近處理過的 roomKey，用於 onNotificationRemoved 判斷是否為連鎖反應
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

    /** 雙開帳號（Android user profile）的穩定 key */
    private fun profileKeyOf(sbn: StatusBarNotification): String =
        (sbn.user?.hashCode() ?: 0).toString()

    /**
     * 是否為「通話類」通知（來電 / 通話中 / 未接）。
     * 只認通話專屬訊號，避免誤判一般訊息：
     *  - category == CATEGORY_CALL（來電 / 通話中，CallStyle）
     *  - category == "missed_call"（未接；用字串避開 API level 疑慮）
     *  - fullScreenIntent != null（來電全螢幕 UI）
     * 刻意不看 FLAG_ONGOING_EVENT —— 太廣，可能誤殺其他常駐通知。
     */
    private fun isCallNotification(n: Notification): Boolean {
        val category = n.category
        if (category == Notification.CATEGORY_CALL) return true
        if (category == "missed_call") return true
        if (n.fullScreenIntent != null) return true
        return false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in LINE_PACKAGES) return
        if (!prefs.getBoolean(KEY_SERVICE_ENABLED, true)) return

        val notification = sbn.notification

        // 通話類守門：LINE 的來電 / 通話中 / 未接通知，title = 對方名字、subText = null，
        // 跟「個人訊息」長得一模一樣 → 會被誤存進聊天室清單；而且取代模式下會被我們一起
        // cancel 掉，可能干擾來電。命中就直接放行：不存清單、不堆疊、不取消，原通知留給系統/手錶。
        if (isCallNotification(notification)) {
            Log.d(
                TAG,
                "略過通話類通知 category=${notification.category} " +
                    "fullScreenIntent=${notification.fullScreenIntent != null}"
            )
            return
        }

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

        // 判斷聊天類型：個人 / 群組 / 社群
        // 社群(LINE OpenChat / Square)：LINE 帶私有 extra line.square.notification=true，
        // 這是唯一可靠的社群標記。社群訊息跟群組同走 NewMessages 頻道、shortcutId 也讀不到
        // （android.shortcutId 不在 extras，頂層 shortcutId 又常是群組 chat id），故一律改看此旗標。
        // 實機 dumpsys 驗證：社群帶 line.square.notification=true，群組沒有。
        val isSquare = extras.getBoolean("line.square.notification", false)
        val chatType: String = when {
            isSquare -> "community"
            subText != null -> "group"
            else -> "personal"
        }
        val sender: String = title
        val chatTitle: String = subText ?: title
        val isGroup = chatType != "personal"

        // 雙開帳號區分：把帳號(profile)納入 key
        val profileKey = profileKeyOf(sbn)
        val roomKey = profileKey + KEY_SEP + chatTitle
        knownProfiles.add(profileKey)

        // Debug: 記錄通知結構幫助分析
        Log.v(TAG, "通知結構 channelId=$channelId tag=$tag square=$isSquare chatType=$chatType profile=$profileKey")

        // 去重
        val timeSeconds = sbn.postTime / 1000
        val dedupeKey = "$roomKey|$text|$timeSeconds"
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

        // 從 LINE 的 MessagingStyle 通知取出「本人」頭貼與名稱，按帳號(profile)分別快取
        try {
            val lineUser = NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)?.user
            if (lineUser != null) {
                if (!selfPersonIcons.containsKey(profileKey)) {
                    lineUser.icon?.let {
                        selfPersonIcons[profileKey] = it
                        Log.d(TAG, "✓ 取得帳號[$profileKey]本人頭貼")
                    }
                }
                lineUser.name?.toString()?.takeIf { it.isNotBlank() }
                    ?.let { accountLabels[profileKey] = it }
            }
        } catch (e: Exception) { /* LINE 沒附就用預設 */ }

        // 檢查我們是否還有該聊天室的活躍通知
        // 如果沒有（被用戶滑掉了），先清緩衝再堆疊新訊息
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ourNotifId = threadNotifIds[roomKey]
        if (ourNotifId != null) {
            val stillActive = manager.activeNotifications.any { it.id == ourNotifId }
            if (!stillActive) {
                chatRooms[roomKey]?.clearMessages()
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

        val room = chatRooms.getOrPut(roomKey) {
            ChatRoom(
                chatTitle = chatTitle,
                isGroup = isGroup,
                roomKey = roomKey,
                profileKey = profileKey,
            )
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

        recordChat(chatTitle, chatType, largeIcon, sbn.postTime)

        // 先發我們的通知
        val style = prefs.getString(KEY_NOTIFICATION_STYLE, "thread") ?: "thread"
        when (style) {
            "apple" -> postAppleStyleNotification(room, message)
            else -> postThreadStyleNotification(room)
        }

        // 延遲取消 LINE 原通知（確保我們的 contentIntent 不會因為 LINE 通知被取消而失效）
        if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
            val key = sbn.key
            recentlyProcessed[roomKey] = System.currentTimeMillis()
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
            val roomKey = findRoomKeyByNotifId(removedId) ?: return
            clearChatGroup(roomKey)
            Log.d(TAG, "本機通知被移除，清整組")
            return
        }

        if (sbn.packageName !in LINE_PACKAGES) return

        // 非取代模式下不同步清除（兩邊通知獨立）
        if (!prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) return
        // 「已讀後清除」已固定為永遠開啟（移除使用者開關）

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: return
        val subText = extras.getCharSequence("android.subText")?.toString()
        val chatTitle = subText ?: title
        val roomKey = profileKeyOf(sbn) + KEY_SEP + chatTitle

        // 最近 2 秒內我們處理過 = 我們自己取消 LINE 原通知的連鎖反應，不做任何事
        val processedTime = recentlyProcessed[roomKey]
        if (processedTime != null && System.currentTimeMillis() - processedTime < 2000) return

        // 用戶在 LINE 裡讀了訊息 → 同步清除我們的通知
        clearChatGroup(roomKey)
        Log.d(TAG, "用戶已讀，同步清除 [$chatTitle] 的通知")
    }

    /**
     * 清掉某聊天室的所有通知（thread / apple children / summary）+ in-memory buffer。
     * 取消前先把 id 記進 suppressedRemovalIds，避免 onNotificationRemoved 連鎖再清一次。
     */
    private fun clearChatGroup(roomKey: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ids = mutableListOf<Int>()
        threadNotifIds.remove(roomKey)?.let { ids.add(it) }
        summaryIds.remove(roomKey)?.let { ids.add(it) }
        appleNotifIds.remove(roomKey)?.let { ids.addAll(it) }
        ids.forEach {
            suppressedRemovalIds.add(it)
            manager.cancel(it)
        }
        chatRooms[roomKey]?.clearMessages()
    }

    /** 由通知 id 反查所屬 roomKey */
    private fun findRoomKeyByNotifId(id: Int): String? {
        threadNotifIds.entries.firstOrNull { it.value == id }?.let { return it.key }
        summaryIds.entries.firstOrNull { it.value == id }?.let { return it.key }
        appleNotifIds.entries.firstOrNull { id in it.value }?.let { return it.key }
        return null
    }

    /**
     * 處理用戶的快速回覆（由 ReplyRelayReceiver 轉發給 LINE 後呼叫，同 process）。
     * 一律先把回覆加進對話 + 重貼通知（接管系統樂觀回覆、停 spinner、顯示回覆＋本人頭貼，
     * 也讓回覆留得住、不被下一則訊息重建洗掉）；「回覆後清除」開啟時再延遲整組清掉。
     */
    fun handleUserReply(roomKey: String, notifId: Int, replyText: CharSequence) {
        val room = chatRooms[roomKey]
        if (room == null) {
            // 沒有對話狀態 → 至少關掉這則停 spinner
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)
            return
        }
        room.addMessage(
            ChatMessage(
                sender = getString(R.string.notification_self_person),
                text = replyText.toString(),
                timestamp = System.currentTimeMillis(),
                isGroup = room.isGroup,
                chatTitle = room.chatTitle,
                senderIcon = null,
                isFromMe = true,
            )
        )
        val style = prefs.getString(KEY_NOTIFICATION_STYLE, "thread") ?: "thread"
        if (style == "apple") postAppleStyleNotification(room, room.messages.last())
        else postThreadStyleNotification(room)

        if (prefs.getBoolean(KEY_CLEAR_AFTER_REPLY, true)) {
            // 「回覆後清除」：等系統樂觀回覆狀態落定後再整組清掉，避免被蓋回來。
            handler.postDelayed({ clearChatGroup(roomKey) }, 2000)
            Log.d(TAG, "回覆後延遲清除整組 [${room.chatTitle}]")
        } else {
            Log.d(TAG, "回覆已加入對話並保留 [${room.chatTitle}]")
        }
    }

    /** 該帳號的本人頭貼（取不到用綠色預設） */
    private fun selfIconFor(room: ChatRoom): IconCompat =
        selfPersonIcons[room.profileKey]
            ?: IconCompat.createWithResource(this, R.drawable.ic_self_avatar)

    /** 多帳號時，標題前綴帳號來源（例：「工作帳號 · 」）；單帳號時為空字串 */
    private fun acctPrefix(room: ChatRoom): String {
        if (knownProfiles.size <= 1) return ""
        val label = accountLabels[room.profileKey] ?: "LINE 分身"
        return "$label · "
    }

    // ==========================================
    // 對話串模式
    // ==========================================
    private fun postThreadStyleNotification(room: ChatRoom) {
        val notifId = threadNotifIds.getOrPut(room.roomKey) { nextThreadId++ }

        val me = Person.Builder()
            .setName(getString(R.string.notification_self_person))
            .setIcon(selfIconFor(room))
            .build()
        val msgStyle = NotificationCompat.MessagingStyle(me)

        if (room.isGroup) {
            msgStyle.conversationTitle = acctPrefix(room) + room.chatTitle
            msgStyle.isGroupConversation = true
        } else if (knownProfiles.size > 1) {
            // 多帳號時，個人聊天也標出帳號來源
            msgStyle.conversationTitle = acctPrefix(room) + room.chatTitle
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
        val groupKey = "linenotify_${room.roomKey}"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val messageId = nextMessageId++
        appleNotifIds.getOrPut(room.roomKey) { mutableListOf() }.add(messageId)

        val msgBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.stanslab.linenotify.R.drawable.ic_notif)
            .setContentTitle(
                if (room.isGroup) {
                    acctPrefix(room) +
                        getString(R.string.notification_group_title, room.chatTitle, newMessage.sender)
                } else {
                    acctPrefix(room) + newMessage.sender
                }
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
        val summaryId = summaryIds.getOrPut(room.roomKey) { nextSummaryId++ }

        val summaryBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.stanslab.linenotify.R.drawable.ic_notif)
            .setContentTitle(acctPrefix(room) + room.chatTitle)
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
                // 回覆送出後 ReplyRelayReceiver 會轉發給 LINE 並交給 service 處理，
                // 否則系統的回覆 spinner 會一直卡住轉圈圈。
                val relayIntent = Intent(this, ReplyRelayReceiver::class.java).apply {
                    action = ReplyRelayReceiver.ACTION_REPLY
                    putExtra(ReplyRelayReceiver.EXTRA_RESULT_KEY, resultKey)
                    putExtra(ReplyRelayReceiver.EXTRA_LINE_PENDING_INTENT, lineActionIntent)
                    putExtra(ReplyRelayReceiver.EXTRA_NOTIF_ID, notifId)
                    putExtra(ReplyRelayReceiver.EXTRA_CHAT_TITLE, room.roomKey)
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
        // 一個聊天室只該屬於一個類別。先把它從另兩個 set 移除，
        // 自動修正過去誤分類的殘留（例如社群之前被當群組存進 known_groups）。
        val editor = prefs.edit()
        var changed = false
        for (other in listOf("known_communities", "known_groups", "known_chats")) {
            if (other == key) continue
            val s = prefs.getStringSet(other, emptySet())?.toMutableSet() ?: continue
            if (s.remove(chatTitle)) {
                editor.putStringSet(other, s)
                changed = true
            }
        }
        val known = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (known.add(chatTitle)) {
            editor.putStringSet(key, known)
            changed = true
        }
        if (changed) editor.apply()
    }

    /** 收錄聊天室：名稱（分類）+ 最後活躍時間 + 頭貼，給「管理個別聊天室」顯示用。 */
    private fun recordChat(chatTitle: String, chatType: String, avatar: Bitmap?, timestamp: Long) {
        saveKnownChat(chatTitle, chatType)
        updateLastActive(chatTitle, timestamp)
        if (avatar != null) saveAvatar(chatTitle, avatar)
    }

    private fun updateLastActive(chatTitle: String, timestamp: Long) {
        val obj = try {
            prefs.getString(KEY_CHAT_LAST_ACTIVE, null)?.let { org.json.JSONObject(it) }
                ?: org.json.JSONObject()
        } catch (e: Exception) {
            org.json.JSONObject()
        }
        obj.put(chatTitle, timestamp)
        prefs.edit().putString(KEY_CHAT_LAST_ACTIVE, obj.toString()).apply()
    }

    /** 把該聊天室最近一次的頭貼存成 PNG（覆蓋舊的，保持最新）。 */
    private fun saveAvatar(chatTitle: String, bitmap: Bitmap) {
        try {
            val file = avatarFile(this, chatTitle)
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "存頭貼失敗：$chatTitle", e)
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
