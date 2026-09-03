package com.stanslab.linenotify.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.stanslab.linenotify.R
import com.stanslab.linenotify.model.ChatMessage
import com.stanslab.linenotify.model.ChatRoom
import org.json.JSONObject

class LineNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "LineNotify"
        internal val LINE_PACKAGES = LineMessageChannelSettings.knownPackages.toSet()
        const val CHANNEL_ID = "line_notify_plus"
        const val PREFS_NAME = "line_notify_prefs"
        const val KEY_REPLACE_ORIGINAL = "replace_original"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_LAST_LINE_MESSAGE_PACKAGE = "last_line_message_package"
        const val KEY_LAST_LINE_MESSAGE_CHANNEL = "last_line_message_channel"
        // 舊版語意：只停用 Notify+ 增強，仍保留 LINE 原始通知。不可直接改成完全靜音，
        // 否則既有使用者升級後會無預警漏通知。
        const val KEY_DISABLED_CHATS = "disabled_chats"
        // 新版由使用者明確關閉後才寫入；完全靜音（含 LINE 原通知與 @all）。
        const val KEY_MUTED_CHATS = "fully_muted_chats_v2"
        const val KEY_NOTIFICATION_STYLE = "notification_style"
        const val KEY_CLEAR_AFTER_REPLY = "clear_after_reply"
        // v1.6.0 起已讀後清除固定開啟；僅保留 key 讓 UI 清理舊版偏好資料。
        const val KEY_CLEAR_AFTER_READ = "clear_after_read"
        const val KEY_ACCESSIBILITY_READ_SYNC = "accessibility_read_sync"
        const val KEY_CHAT_LAST_ACTIVE = "chat_last_active"   // JSON {聊天室名: epochMillis}
        const val KEY_CHAT_SORT = "chat_sort"                 // "recent" | "name" | "type"
        // 使用新 key，確保曾跑過 vc30 舊清理流程的安裝仍會執行 v1.5.0 的安全遷移。
        private const val KEY_LEGACY_SENTINEL_CLEANUP = "legacy_sentinel_cleanup_v1_5_0"
        private const val KEY_LEGACY_SENTINEL_RELEASE_MARKERS =
            "legacy_sentinel_release_markers_v1_5_0"
        // 100ms release callback 正常會立刻抵達；15 秒只用來跨越短暫 process/service 重建，
        // 避免標記留太久而碰到同 key、同內容的真正新訊息。
        private const val LEGACY_SENTINEL_RELEASE_WINDOW_MS = 15_000L
        private const val AVATAR_DIR = "chat_avatars"

        // roomKey 分隔字元：profileKey 是純數字（hashCode），用 ":" 即保證 (profile,聊天室) 唯一對應
        private const val KEY_SEP = ":"

        // 服務 instance（給 ReplyRelayReceiver 在同 process 呼叫 handleUserReply）
        @Volatile
        var instance: LineNotificationListener? = null

        @Volatile
        var isListenerConnected: Boolean = false

        private const val THREAD_NOTIFICATION_ID = 1
        private const val SUMMARY_NOTIFICATION_ID = 2
        private const val APPLE_CHILD_NOTIFICATION_ID = 3
        internal const val EXTRA_ROOM_KEY = "com.stanslab.linenotify.extra.ROOM_KEY"
        internal const val EXTRA_CHAT_TITLE = "com.stanslab.linenotify.extra.CHAT_TITLE"
        private const val EXTRA_NOTIFICATION_KIND = "com.stanslab.linenotify.extra.NOTIFICATION_KIND"
        private const val EXTRA_IS_GROUP = "com.stanslab.linenotify.extra.IS_GROUP"
        internal const val EXTRA_PROFILE_KEY = "com.stanslab.linenotify.extra.PROFILE_KEY"
        private const val EXTRA_POST_GENERATION = "com.stanslab.linenotify.extra.POST_GENERATION"
        private const val MAX_APPLE_CHILDREN_PER_ROOM = 8
        private const val MAX_APPLE_CHILDREN_TOTAL = 24

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
    private data class NotificationRef(val tag: String, val id: Int)
    private data class AppleEviction(val roomKey: String, val ref: NotificationRef)
    private data class PostedNotification(
        val ref: NotificationRef,
        val generation: Long,
        val acceptNewerGeneration: Boolean,
    )
    private data class ReplacementReceipt(
        val posts: List<PostedNotification>,
        // Apple 模式要等 child+summary active 且最新 budget plan 成功提交，才允許 outer
        // runnable 取消 LINE；不能只依賴 cancel() 是否已反映到 activeNotifications。
        @Volatile var committed: Boolean,
    )
    private data class MirroredVariantEntry(
        val source: NotificationClassifier.MirrorSource,
        val receipt: ReplacementReceipt,
        val stateEpoch: Long,
    )

    private val summaryIds = mutableMapOf<String, NotificationRef>()
    private val threadNotifIds = mutableMapOf<String, NotificationRef>()

    private val appleNotifIds = mutableMapOf<String, MutableList<NotificationRef>>()
    private val appleNotificationOrder = ArrayDeque<Pair<String, NotificationRef>>()
    private val appleMessagesByRef = mutableMapOf<NotificationRef, ChatMessage>()
    // NotificationManager.activeNotifications 可能比 notify() 慢一拍；交易確認前不可把 child
    // 當成已消失而從 budget 索引剪掉。
    private val pendingAppleRefCounts = mutableMapOf<NotificationRef, Int>()
    private val pendingThreadRefCounts = mutableMapOf<NotificationRef, Int>()
    private val pendingOriginalCancels = mutableMapOf<String, Runnable>()
    private val legacySentinelReleaseMarkers =
        mutableMapOf<String, LegacySentinelMigration.Marker>()
    private val recentMirroredVariants = linkedMapOf<String, MirroredVariantEntry>()
    private val mirrorPoisonUntil = mutableMapOf<String, Long>()
    private val nextPostGeneration = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
    private var notificationStateEpoch = 0L

    // 每個帳號（雙開 profile）一張「本人」頭貼 + 顯示名，key = profileKey。
    private val selfPersonIcons = mutableMapOf<String, IconCompat>()
    private val accountLabels = mutableMapOf<String, String>()
    // 看過的帳號 profile；>1 才在通知標題標出帳號來源。
    private val knownProfiles = mutableSetOf<String>()

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private fun markApplePending(ref: NotificationRef) {
        pendingAppleRefCounts[ref] = (pendingAppleRefCounts[ref] ?: 0) + 1
    }

    private fun unmarkApplePending(ref: NotificationRef) {
        val remaining = (pendingAppleRefCounts[ref] ?: return) - 1
        if (remaining <= 0) pendingAppleRefCounts.remove(ref)
        else pendingAppleRefCounts[ref] = remaining
    }

    private fun markThreadPending(ref: NotificationRef) {
        pendingThreadRefCounts[ref] = (pendingThreadRefCounts[ref] ?: 0) + 1
    }

    private fun unmarkThreadPending(ref: NotificationRef) {
        val remaining = (pendingThreadRefCounts[ref] ?: return) - 1
        if (remaining <= 0) pendingThreadRefCounts.remove(ref)
        else pendingThreadRefCounts[ref] = remaining
    }

    private fun invalidateMirrorCandidates() {
        notificationStateEpoch++
        recentMirroredVariants.clear()
        mirrorPoisonUntil.clear()
    }

    /**
     * LINE 的 EXTRA_TEXT 可能只是通知列預覽；android.messages 才保有最新一則完整文字。
     * 不保存額外歷史，只把完整文字交給既有的 25 則 in-memory buffer。
     */
    private fun completeMessageText(notification: Notification, previewText: String): String {
        val compatText = runCatching {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
                ?.messages
                ?.lastOrNull()
                ?.text
                ?.toString()
        }.getOrNull()

        // 某些 LINE legacy mirror 缺少完整的 MessagingStyle metadata，Compat 可能整體解析失敗；
        // 此時只讀 framework android.messages 最後一筆的 text，不碰 sender 或訊息歷史。
        @Suppress("DEPRECATION")
        val bundledText = runCatching {
            (notification.extras
                .getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.lastOrNull() as? android.os.Bundle)
                ?.getCharSequence("text")
                ?.toString()
        }.getOrNull()

        return LineMessageTextResolver.resolve(
            previewText = previewText,
            latestMessagingText = bundledText?.takeIf { it.isNotEmpty() } ?: compatText,
        )
    }

    /**
     * LINE 26.10.1 在 Nothing OS 會為同一訊息各發一個 conversation record 與 legacy mirror。
     * 只有完整 MessagingStyle payload 可建立可靠 identity；缺 timestamp／style 就不合併。
     * 回傳值是 SHA-256，不把訊息原文留在 cache。
     */
    /**
     * 從 [sbn] 抽出 mirror 配對指紋所需的欄位。**指紋的欄位白名單由
     * [NotificationClassifier.mirrorFingerprint] 的參數列定義**（想加欄位先讀那邊的說明）。
     * 這裡只負責抽值，抽不到必要欄位就回 null（fail-open：不合併，寧可重複也不漏訊息）。
     */
    private fun mirroredPayloadFingerprint(
        sbn: StatusBarNotification,
        roomKey: String,
        sender: String,
        text: String,
    ): String? {
        val notification = sbn.notification
        val shortcutId = notification.shortcutId ?: return null
        if (notification.`when` <= 0L) return null
        val sourceUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sbn.uid
        } else {
            // StatusBarNotification#getUid was added in API 29. UserHandle's
            // identity hash keeps work/personal profiles distinct on API 26-28.
            sbn.user?.hashCode() ?: -1
        }
        return NotificationClassifier.mirrorFingerprint(
            roomKey = roomKey,
            sender = sender,
            text = text,
            packageName = sbn.packageName,
            sourceUid = sourceUid,
            shortcutId = shortcutId,
            groupKey = sbn.groupKey,
            channelId = notification.channelId,
            whenMs = notification.`when`,
        )
    }

    private fun scheduleOriginalCancellation(
        key: String,
        receipt: ReplacementReceipt,
        requireExactGeneration: Boolean = false,
        requiredStateEpoch: Long? = null,
        expectedSource: NotificationClassifier.MirrorSource? = null,
        expectedMirrorSignature: String? = null,
    ) {
        val cancelTask = Runnable {
            pendingOriginalCancels.remove(key)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val sourceStillCurrent = if (expectedSource != null && expectedMirrorSignature != null) {
                runCatching {
                    activeNotifications.orEmpty().any { source ->
                        if (source.key != expectedSource.key ||
                            source.postTime != expectedSource.postTime ||
                            source.id != expectedSource.id ||
                            source.tag != expectedSource.tag ||
                            source.notification.shortcutId != expectedSource.shortcutId
                        ) return@any false
                        val extras = source.notification.extras
                        val sourceTitle = extras.getCharSequence("android.title")?.toString()
                            ?: return@any false
                        val sourcePreviewText = extras.getCharSequence("android.text")?.toString()
                            ?: return@any false
                        val sourceText = completeMessageText(
                            source.notification,
                            sourcePreviewText,
                        )
                        val sourceSubText = extras.getCharSequence("android.subText")?.toString()
                        val sourceRoomKey = profileKeyOf(source) + KEY_SEP +
                            NotificationClassifier.chatTitleOf(sourceTitle, sourceSubText)
                        mirroredPayloadFingerprint(
                            source,
                            sourceRoomKey,
                            sourceTitle,
                            sourceText,
                        ) == expectedMirrorSignature
                    }
                }.getOrDefault(false)
            } else {
                true
            }
            val replacementIsActive =
                prefs.getBoolean(KEY_REPLACE_ORIGINAL, true) &&
                sourceStillCurrent &&
                (requiredStateEpoch == null || requiredStateEpoch == notificationStateEpoch) &&
                receipt.committed && receipt.posts.all { posted ->
                val expected = if (requireExactGeneration) {
                    posted.copy(acceptNewerGeneration = false)
                } else {
                    posted
                }
                isActive(manager, expected)
            }
            if (replacementIsActive) {
                cancelNotification(key)
            } else {
                Log.w(TAG, "找不到已提交的 Notify+ 副本；保留 LINE 原通知")
            }
        }
        pendingOriginalCancels[key] = cancelTask
        handler.postDelayed(cancelTask, 200)
    }

    private fun systemRedactedText(): String? {
        // NotificationManagerService 使用系統語系建立遮蔽文字；不能跟著 App 的單獨語言設定，
        // 也不能永久快取，否則變更系統語言後精確比對會失效。
        val systemResources = Resources.getSystem()
        val id = systemResources.getIdentifier("redacted_notification_message", "string", "android")
        return if (id == 0) null else runCatching { systemResources.getString(id) }.getOrNull()
    }

    /**
     * 只有確認 Notify+ 目前真的具備顯示副本的條件，才可進入 replacement 流程。
     * 任一條件不滿足都 fail-open 保留 LINE，避免「副本被系統擋掉、原通知又被取消」。
     */
    private fun canPostReplacementNotifications(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun notificationRef(
        kind: String,
        roomKey: String,
        id: Int,
        messageToken: String = "",
    ): NotificationRef {
        val digest = NotificationClassifier
            .dedupeFingerprint(roomKey, "$kind\u0000$messageToken", 0L)
            .take(24)
        return NotificationRef(tag = "notifyplus:$kind:$digest", id = id)
    }

    private fun addIdentityExtras(
        builder: NotificationCompat.Builder,
        room: ChatRoom,
        kind: String,
        generation: Long,
    ) {
        builder.addExtras(Bundle().apply {
            putString(EXTRA_ROOM_KEY, room.roomKey)
            putString(EXTRA_CHAT_TITLE, room.chatTitle)
            putString(EXTRA_NOTIFICATION_KIND, kind)
            putBoolean(EXTRA_IS_GROUP, room.isGroup)
            putString(EXTRA_PROFILE_KEY, room.profileKey)
            putLong(EXTRA_POST_GENERATION, generation)
        })
    }

    private fun activeState(manager: NotificationManager, ref: NotificationRef): Boolean? =
        runCatching {
            manager.activeNotifications.any { it.tag == ref.tag && it.id == ref.id }
        }.getOrNull()

    private fun isActive(manager: NotificationManager, ref: NotificationRef): Boolean =
        activeState(manager, ref) == true

    private fun isActive(manager: NotificationManager, posted: PostedNotification): Boolean =
        runCatching {
            manager.activeNotifications.any {
                val activeGeneration =
                    it.notification.extras.getLong(EXTRA_POST_GENERATION, Long.MIN_VALUE)
                it.tag == posted.ref.tag &&
                    it.id == posted.ref.id &&
                    if (posted.acceptNewerGeneration) {
                        activeGeneration >= posted.generation
                    } else {
                        activeGeneration == posted.generation
                    }
            }
        }.getOrDefault(false)

    /** 從 SystemUI 仍保留的自有通知重建索引，避免 process restart 後 ID/cap/清除狀態歸零。 */
    private fun rebuildNotificationIndexes(active: Array<out StatusBarNotification>) {
        invalidateMirrorCandidates()
        val previouslyIndexedAppleRooms = appleNotifIds.keys.toSet()
        threadNotifIds.clear()
        summaryIds.clear()
        appleNotifIds.clear()
        appleNotificationOrder.clear()
        appleMessagesByRef.clear()
        previouslyIndexedAppleRooms.forEach(chatRooms::remove)
        val rebuiltAppleRooms = mutableMapOf<String, ChatRoom>()

        active.sortedBy { sbn -> sbn.notification.`when`.takeIf { it > 0L } ?: sbn.postTime }
            .forEach { sbn ->
            val extras = sbn.notification.extras
            val roomKey = extras.getString(EXTRA_ROOM_KEY) ?: return@forEach
            val tag = sbn.tag ?: return@forEach
            val ref = NotificationRef(tag, sbn.id)
            when (extras.getString(EXTRA_NOTIFICATION_KIND)) {
                "thread" -> threadNotifIds[roomKey] = ref
                "apple-summary" -> summaryIds[roomKey] = ref
                "apple-child" -> {
                    appleNotifIds.getOrPut(roomKey) { mutableListOf() }.add(ref)
                    appleNotificationOrder.addLast(roomKey to ref)

                    // 用 temporary room 重建，最後原子替換；同一 instance 重連不會重複 append。
                    val chatTitle = extras.getString(EXTRA_CHAT_TITLE) ?: return@forEach
                    val profileKey = extras.getString(EXTRA_PROFILE_KEY).orEmpty()
                    val room = rebuiltAppleRooms.getOrPut(roomKey) {
                        ChatRoom(
                            chatTitle = chatTitle,
                            isGroup = extras.getBoolean(EXTRA_IS_GROUP, false),
                            roomKey = roomKey,
                            profileKey = profileKey,
                        )
                    }
                    if (profileKey.isNotEmpty()) knownProfiles.add(profileKey)
                    room.contentIntent = sbn.notification.contentIntent ?: room.contentIntent
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    if (text != null) {
                        val rebuiltMessage = ChatMessage(
                            sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                                ?: chatTitle,
                            text = text,
                            timestamp = sbn.notification.`when`.takeIf { it > 0L } ?: sbn.postTime,
                            isGroup = room.isGroup,
                            chatTitle = chatTitle,
                        )
                        room.addMessage(rebuiltMessage)
                        appleMessagesByRef[ref] = rebuiltMessage
                    }
                }
            }
        }
        chatRooms.putAll(rebuiltAppleRooms)
    }

    private fun pruneInactiveAppleIndexes(manager: NotificationManager): Boolean {
        val activeRefs = runCatching {
            manager.activeNotifications.mapTo(mutableSetOf()) { NotificationRef(it.tag.orEmpty(), it.id) }
        }.getOrElse {
            Log.w(TAG, "無法查詢作用中通知；保留 Apple 索引並讓本次 LINE 通知 fail-open", it)
            return false
        }
        appleNotifIds.entries.toList().forEach { (roomKey, refs) ->
            val removedRefs = refs.filter { it !in activeRefs && it !in pendingAppleRefCounts }
            if (removedRefs.isNotEmpty()) invalidateMirrorCandidates()
            refs.removeAll(removedRefs.toSet())
            removedRefs.forEach { ref ->
                appleMessagesByRef.remove(ref)?.let { chatRooms[roomKey]?.removeMessage(it) }
            }
            if (refs.isEmpty()) appleNotifIds.remove(roomKey)
        }
        appleNotificationOrder.removeAll { (_, ref) ->
            ref !in activeRefs && ref !in pendingAppleRefCounts
        }
        summaryIds.entries.removeAll { (_, ref) ->
            ref !in activeRefs && ref !in pendingAppleRefCounts
        }
        return true
    }

    /**
     * 先規劃、後提交 eviction。若 24 個 child 都是不同房的唯一未讀，沒有安全 victim，
     * 就回傳 null 讓本次 LINE 原通知 fail-open，而不是刪除其他房的唯一通知。
     */
    private fun planAppleEvictions(
        manager: NotificationManager,
        currentRoomKey: String,
        additionalChildCount: Int,
        protectedRef: NotificationRef? = null,
        excludeOtherPendingChildren: Boolean = false,
        mandatoryEvictionRef: NotificationRef? = null,
    ): List<AppleEviction>? {
        if (!pruneInactiveAppleIndexes(manager)) return null
        val planned = mutableListOf<AppleEviction>()
        val plannedRefs = mutableSetOf<NotificationRef>()
        // Commit A 時，稍後才會確認的 B 不應算進 A 的 eviction 配額；否則 A 先多刪一筆、
        // B 再 rollback，最後會掉到 cap 以下並遺失已接管的舊訊息。
        val ignoredPendingChildren = if (excludeOtherPendingChildren) {
            pendingAppleRefCounts.keys.toSet()
        } else {
            emptySet()
        }
        val remainingCounts = appleNotifIds.mapValuesTo(mutableMapOf()) { (_, refs) ->
            refs.count { it !in ignoredPendingChildren }
        }
        remainingCounts[currentRoomKey] =
            (remainingCounts[currentRoomKey] ?: 0) + additionalChildCount

        fun plan(roomKey: String, ref: NotificationRef) {
            if (!plannedRefs.add(ref)) return
            planned.add(AppleEviction(roomKey, ref))
            remainingCounts[roomKey] = (remainingCounts[roomKey] ?: 0) - 1
        }

        // 快速回覆是「新回覆 child 取代被回覆 child」的一筆交易。把舊 child 納入同一
        // commit plan，避免先因 9→8 淘汰 C1，交易後又另刪 C5，最後錯掉到 7。
        if (mandatoryEvictionRef != null &&
            mandatoryEvictionRef != protectedRef &&
            mandatoryEvictionRef !in ignoredPendingChildren
        ) {
            val mandatoryRoom = appleNotifIds.entries
                .firstOrNull { (_, refs) -> mandatoryEvictionRef in refs }
                ?.key
            if (mandatoryRoom != null && (remainingCounts[mandatoryRoom] ?: 0) > 1) {
                plan(mandatoryRoom, mandatoryEvictionRef)
            }
        }

        while ((remainingCounts[currentRoomKey] ?: 0) > MAX_APPLE_CHILDREN_PER_ROOM) {
            val candidate = appleNotifIds[currentRoomKey]
                ?.firstOrNull {
                    it !in plannedRefs && it != protectedRef && it !in ignoredPendingChildren
                }
                ?: return null
            plan(currentRoomKey, candidate)
        }

        val indexedNonPendingCount = appleNotificationOrder.count { (_, ref) ->
            ref !in ignoredPendingChildren
        }
        while (indexedNonPendingCount + additionalChildCount - planned.size >
            MAX_APPLE_CHILDREN_TOTAL
        ) {
            val candidate = appleNotificationOrder.firstOrNull { (roomKey, ref) ->
                ref !in plannedRefs && ref != protectedRef &&
                    ref !in ignoredPendingChildren &&
                    (remainingCounts[roomKey] ?: 0) > 1
            } ?: return null
            plan(candidate.first, candidate.second)
        }
        return planned
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadLegacySentinelReleaseMarkers()
        createNotificationChannel()
        Log.d(TAG, "Notify+ 服務啟動")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pendingOriginalCancels.clear()
        recentMirroredVariants.clear()
        mirrorPoisonUntil.clear()
        pendingAppleRefCounts.clear()
        pendingThreadRefCounts.clear()
        isListenerConnected = false
        instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        releaseLegacyLineSentinelsOnce()
        // 早期版本沒有持久 room extras；程序重建後無法安全歸屬，只清這些不可管理的舊副本。
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = runCatching { manager.activeNotifications.orEmpty() }.getOrElse {
            // 查詢失敗不等於「沒有通知」。保留現有索引，下次 callback/reconnect 再同步。
            Log.w(TAG, "通知監聽已連線，但作用中通知查詢失敗；暫不重建索引", it)
            return
        }
        active
            .filter { it.notification.extras.getString(EXTRA_ROOM_KEY).isNullOrEmpty() }
            .forEach { sbn ->
                if (sbn.tag == null) manager.cancel(sbn.id) else manager.cancel(sbn.tag, sbn.id)
            }
        rebuildNotificationIndexes(active)
        enforceMutedPreferencesOnActiveNotifications()
        Log.d(TAG, "通知監聽已連線")
    }

    /**
     * v1.4.1～v1.4.4 曾把 LINE 原通知 snooze 最長七天。移除該方案後，Android 仍會保留
     * 那些系統層紀錄，導致同 key 的新訊息繼續被藏住；升級後只做一次短期 snooze。未取消的
     * 舊通知回放會先以持久 marker 攔下再丟棄，已被 LINE 取消的紀錄則由系統直接丟棄。
     */
    private fun releaseLegacyLineSentinelsOnce() {
        discardActiveLegacySentinelReposts()
        if (prefs.getBoolean(KEY_LEGACY_SENTINEL_CLEANUP, false)) return
        val snoozed = runCatching { getSnoozedNotifications().orEmpty() }.getOrElse {
            Log.w(TAG, "無法查詢舊版 snooze 通知；下次連線再重試", it)
            return
        }
        val lineNotifications = snoozed
            .filter { it.packageName in LINE_PACKAGES }
            .filter { NotificationClassifier.isSupportedMessageChannel(it.notification.channelId) }
            .filterNot { isCallNotification(it.notification) }
        val expiresAt = System.currentTimeMillis() + LEGACY_SENTINEL_RELEASE_WINDOW_MS
        lineNotifications.forEach { sbn ->
            legacySentinelReleaseMarkers[sbn.key] = LegacySentinelMigration.Marker(
                fingerprint = legacySentinelFingerprint(sbn),
                expiresAtMillis = expiresAt,
            )
        }
        if (lineNotifications.isNotEmpty() && !persistLegacySentinelReleaseMarkers()) {
            Log.w(TAG, "無法保存舊 snooze 清理標記；保留原狀並在下次連線重試")
            legacySentinelReleaseMarkers.clear()
            return
        }
        var releasedCount = 0
        lineNotifications.forEach { sbn ->
            runCatching { snoozeNotification(sbn.key, 100L) }
                .onSuccess { releasedCount++ }
                .onFailure {
                    legacySentinelReleaseMarkers.remove(sbn.key)
                    Log.w(TAG, "無法釋放一則舊 snooze 通知；下次連線再重試", it)
                }
        }
        persistLegacySentinelReleaseMarkers()
        if (releasedCount == lineNotifications.size) {
            prefs.edit().putBoolean(KEY_LEGACY_SENTINEL_CLEANUP, true).commit()
        }
        scheduleLegacySentinelMarkerExpiry()
        if (releasedCount > 0) {
            Log.w(TAG, "正在安全移除舊版隱藏的 LINE 通知 count=$releasedCount")
        }
    }

    private fun loadLegacySentinelReleaseMarkers() {
        val raw = prefs.getString(KEY_LEGACY_SENTINEL_RELEASE_MARKERS, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            root.keys().forEach { key ->
                val marker = root.getJSONObject(key)
                legacySentinelReleaseMarkers[key] = LegacySentinelMigration.Marker(
                    fingerprint = marker.getString("fingerprint"),
                    expiresAtMillis = marker.getLong("expiresAtMillis"),
                )
            }
            pruneLegacySentinelReleaseMarkers(System.currentTimeMillis())
        }.onFailure {
            legacySentinelReleaseMarkers.clear()
            prefs.edit().remove(KEY_LEGACY_SENTINEL_RELEASE_MARKERS).apply()
            Log.w(TAG, "舊 snooze 清理標記損壞，已捨棄", it)
        }
    }

    private fun persistLegacySentinelReleaseMarkers(): Boolean {
        val root = JSONObject()
        legacySentinelReleaseMarkers.forEach { (key, marker) ->
            root.put(
                key,
                JSONObject()
                    .put("fingerprint", marker.fingerprint)
                    .put("expiresAtMillis", marker.expiresAtMillis),
            )
        }
        val editor = prefs.edit()
        if (legacySentinelReleaseMarkers.isEmpty()) {
            editor.remove(KEY_LEGACY_SENTINEL_RELEASE_MARKERS)
        } else {
            editor.putString(KEY_LEGACY_SENTINEL_RELEASE_MARKERS, root.toString())
        }
        return editor.commit()
    }

    private fun pruneLegacySentinelReleaseMarkers(nowMillis: Long) {
        val removed = legacySentinelReleaseMarkers.entries.removeAll { (_, marker) ->
            nowMillis > marker.expiresAtMillis
        }
        if (removed) persistLegacySentinelReleaseMarkers()
    }

    private fun scheduleLegacySentinelMarkerExpiry() {
        val nextExpiry = legacySentinelReleaseMarkers.values
            .minOfOrNull { it.expiresAtMillis }
            ?: return
        val delay = (nextExpiry - System.currentTimeMillis() + 1L).coerceAtLeast(1L)
        handler.postDelayed({
            pruneLegacySentinelReleaseMarkers(System.currentTimeMillis())
            scheduleLegacySentinelMarkerExpiry()
        }, delay)
    }

    private fun legacySentinelFingerprint(sbn: StatusBarNotification): String {
        val notification = sbn.notification
        val extras = notification.extras
        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        }.getOrNull()
        val messages = style?.messages.orEmpty().joinToString(separator = "\u0001") { message ->
            "${message.timestamp}\u0000${message.person?.name}\u0000${message.text}"
        }
        val canonical = buildString {
            append(notification.channelId).append('\u0000')
            append(extras.getCharSequence(Notification.EXTRA_TITLE)).append('\u0000')
            append(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)).append('\u0000')
            append(extras.getCharSequence(Notification.EXTRA_TEXT)).append('\u0000')
            // snooze/repost 可能改動 Notification.when；原 StatusBarNotification.postTime
            // 會隨 framework 保存的 record 回來，新 notify() 才會得到新的 postTime。
            append(sbn.postTime).append('\u0000')
            append(messages)
        }
        return NotificationClassifier.dedupeFingerprint(sbn.key, canonical, sbn.postTime)
    }

    private fun consumeLegacySentinelRepost(sbn: StatusBarNotification): Boolean {
        val marker = legacySentinelReleaseMarkers[sbn.key] ?: return false
        val now = System.currentTimeMillis()
        val fingerprint = legacySentinelFingerprint(sbn)
        val discard = LegacySentinelMigration.shouldDiscardRepost(marker, fingerprint, now)
        if (discard || LegacySentinelMigration.shouldRemoveMarker(marker, fingerprint, now)) {
            legacySentinelReleaseMarkers.remove(sbn.key)
            persistLegacySentinelReleaseMarkers()
        }
        if (!discard) return false
        cancelNotification(sbn.key)
        Log.d(TAG, "已捨棄舊 snooze 回放 keyHash=${sbn.key.hashCode()}")
        return true
    }

    private fun discardActiveLegacySentinelReposts() {
        if (legacySentinelReleaseMarkers.isEmpty()) return
        runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .filter { it.packageName in LINE_PACKAGES }
            .forEach(::consumeLegacySentinelRepost)
        scheduleLegacySentinelMarkerExpiry()
    }

    override fun onListenerDisconnected() {
        isListenerConnected = false
        invalidateMirrorCandidates()
        Log.w(TAG, "通知監聽已斷線")
        super.onListenerDisconnected()
    }

    private fun enforceMutedPreferencesOnActiveNotifications() {
        if (!prefs.getBoolean(KEY_SERVICE_ENABLED, true)) return
        val muted = prefs.getStringSet(KEY_MUTED_CHATS, emptySet()) ?: emptySet()
        if (muted.isEmpty()) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .filter { it.notification.extras.getString(EXTRA_CHAT_TITLE) in muted }
            .mapNotNull { it.notification.extras.getString(EXTRA_ROOM_KEY) }
            .distinct()
            .forEach(::clearChatGroup)

        runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .filter { it.packageName in LINE_PACKAGES }
            .filter { NotificationClassifier.isSupportedMessageChannel(it.notification.channelId) }
            .filterNot { isCallNotification(it.notification) }
            .filter { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                title != null && NotificationClassifier.chatTitleOf(title, subText) in muted
            }
            .forEach { cancelNotification(it.key) }
    }

    /** 雙開帳號（Android user profile）的穩定 key */
    private fun profileKeyOf(sbn: StatusBarNotification): String =
        "${sbn.packageName}@${sbn.user?.hashCode() ?: 0}"

    private fun knownChatType(chatTitle: String): String? = NotificationClassifier.knownTypeOf(
        mapOf(
            NotificationClassifier.PREFS_KNOWN_COMMUNITIES to
                (prefs.getStringSet(NotificationClassifier.PREFS_KNOWN_COMMUNITIES, emptySet()) ?: emptySet()),
            NotificationClassifier.PREFS_KNOWN_GROUPS to
                (prefs.getStringSet(NotificationClassifier.PREFS_KNOWN_GROUPS, emptySet()) ?: emptySet()),
            NotificationClassifier.PREFS_KNOWN_CHATS to
                (prefs.getStringSet(NotificationClassifier.PREFS_KNOWN_CHATS, emptySet()) ?: emptySet()),
        ),
        chatTitle,
    )

    private fun forcedChatType(chatTitle: String): String? = NotificationClassifier.forcedTypeOf(
        mapOf(
            NotificationClassifier.PREFS_FORCED_COMMUNITIES to
                (prefs.getStringSet(NotificationClassifier.PREFS_FORCED_COMMUNITIES, emptySet()) ?: emptySet()),
            NotificationClassifier.PREFS_FORCED_GROUPS to
                (prefs.getStringSet(NotificationClassifier.PREFS_FORCED_GROUPS, emptySet()) ?: emptySet()),
            NotificationClassifier.PREFS_FORCED_CHATS to
                (prefs.getStringSet(NotificationClassifier.PREFS_FORCED_CHATS, emptySet()) ?: emptySet()),
        ),
        chatTitle,
    )

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
        if (consumeLegacySentinelRepost(sbn)) return

        // LINE 可能在 200ms 取消延遲內用相同 key 更新通知。先撤掉舊任務，否則舊訊息的
        // runnable 會誤殺剛更新的敏感占位通知或來電通知。
        pendingOriginalCancels.remove(sbn.key)?.let(handler::removeCallbacks)

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

        val channelId = notification.channelId
        // 記住 LINE 實際使用的訊息頻道，讓設定頁能直達正確分類。只保存 package/channel
        // 座標，不保存通知標題、本文或聯絡人資料。
        if (NotificationClassifier.isSupportedMessageChannel(channelId) &&
            (prefs.getString(KEY_LAST_LINE_MESSAGE_PACKAGE, null) != sbn.packageName ||
                prefs.getString(KEY_LAST_LINE_MESSAGE_CHANNEL, null) != channelId)
        ) {
            prefs.edit()
                .putString(KEY_LAST_LINE_MESSAGE_PACKAGE, sbn.packageName)
                .putString(KEY_LAST_LINE_MESSAGE_CHANNEL, channelId)
                .apply()
        }
        if (!NotificationClassifier.isSupportedMessageChannel(channelId)) {
            Log.d(TAG, "略過非聊天通知 channelHash=${channelId?.hashCode()}")
            return
        }

        val extras = notification.extras

        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val previewText = extras.getCharSequence("android.text")?.toString() ?: return
        val subText = extras.getCharSequence("android.subText")?.toString()

        // Android 15+ 可能在通知交給第三方 listener 前，就把敏感內容換成 framework 占位字串。
        // 原文此時已無法復原；保留原始 LINE 通知給 SystemUI，不要重發占位字、建假聊天室或取消它。
        val sourceAppLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
        if (NotificationClassifier.isSystemRedactedNotification(
                title = title,
                text = previewText,
                subText = subText,
                sourceAppLabel = sourceAppLabel,
                systemRedactedText = systemRedactedText(),
            )
        ) {
            Log.w(TAG, "系統已遮蔽敏感通知；保留原始通知且不建立 Notify+ 副本")
            return
        }

        // 先用預覽文字做系統遮蔽判斷，避免完整 MessagingStyle 意外繞過隱私保護；
        // 確認不是遮蔽通知後，才優先採用 android.messages 的完整最新本文。
        val text = completeMessageText(notification, previewText)
        if (text != previewText) {
            Log.d(TAG, "使用完整通知文字 previewLen=${previewText.length} fullLen=${text.length}")
        }

        // LINE summary 沒有可靠的單一聊天室識別，也可能比 child 先到。永遠 fail-open 保留；
        // child 被成功取代/取消後 SystemUI 通常會自行重算，不能為了去重而冒險吃掉唯一通知。
        if (extras.getBoolean("android.isGroupSummary", false)) {
            return
        }

        // 過濾 LINE 的堆疊摘要通知（title 含逗號+冒號）
        if (NotificationClassifier.isStackSummaryTitle(title)) {
            return
        }

        // 解析聊天室類型
        // LINE 群組/社群通知：subText = 群組名或社群名, title = 發送者
        // LINE 個人通知：subText = null, title = 發送者
        val tag = sbn.tag ?: ""

        // 判斷聊天類型：個人 / 群組 / 社群
        // 社群(LINE OpenChat / Square)：LINE 帶私有 extra line.square.notification=true，
        // 這是唯一可靠的社群標記。社群訊息跟群組同走 NewMessages 頻道、shortcutId 也讀不到
        // （android.shortcutId 不在 extras，頂層 shortcutId 又常是群組 chat id），故一律改看此旗標。
        // 實機 dumpsys 驗證：社群帶 line.square.notification=true，群組沒有。
        val isSquareKeyPresent = extras.containsKey("line.square.notification")
        val isSquare = extras.getBoolean("line.square.notification", false)
        val sender: String = title
        val chatTitle = NotificationClassifier.chatTitleOf(title, subText)
        val previousType = knownChatType(chatTitle)
        val overrideType = forcedChatType(chatTitle)
        val chatType = NotificationClassifier.classifyChatType(
            isSquare = isSquare,
            subText = subText,
            previousType = previousType,
            overrideType = overrideType,
        )
        val isGroup = NotificationClassifier.isGroupType(chatType)

        // 雙開帳號區分：把帳號(profile)納入 key
        val profileKey = profileKeyOf(sbn)
        val roomKey = profileKey + KEY_SEP + chatTitle
        knownProfiles.add(profileKey)

        // Debug: 記錄通知結構幫助分析
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val debugStyle = runCatching {
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            }.getOrNull()
            Log.v(
                TAG,
                "通知結構 id=${sbn.id} channelHash=${channelId.hashCode()} " +
                    "tagHash=${tag.hashCode()} keyHash=${sbn.key.hashCode()} " +
                    "shortcutHash=${notification.shortcutId?.hashCode()} " +
                    "groupHash=${sbn.groupKey.hashCode()} squareKey=$isSquareKeyPresent " +
                    "square=$isSquare previous=$previousType override=$overrideType " +
                    "chatType=$chatType profile=$profileKey when=${notification.`when`} " +
                    "messages=${debugStyle?.messages?.size ?: -1} " +
                    "historic=${debugStyle?.historicMessages?.size ?: -1} " +
                    "latestTs=${debugStyle?.messages?.lastOrNull()?.timestamp ?: -1L}"
            )
        }

        // 個別聊天室關閉 = 完全靜音。即使全域「取代原始通知」關閉，也要撤掉 LINE 原通知；
        // 同時清掉先前仍留在通知欄的 Notify+ 對話。@all 與直接 @我都遵守此硬封鎖。
        val mutedChats = prefs.getStringSet(KEY_MUTED_CHATS, emptySet()) ?: emptySet()
        val legacyOriginalOnlyChats = prefs.getStringSet(KEY_DISABLED_CHATS, emptySet()) ?: emptySet()
        val notificationMode = NotificationClassifier.notificationModeOf(
            chatTitle,
            legacyOriginalOnlyChats,
            mutedChats,
        )
        if (notificationMode == NotificationClassifier.MODE_MUTED) {
            recordChatMetadata(chatTitle, chatType, sbn.postTime)
            clearChatGroup(roomKey)
            cancelNotification(sbn.key)
            Log.d(TAG, "已封鎖聊天室通知 room=${chatTitle.hashCode()} profile=$profileKey")
            return
        }

        // 升級相容：舊版關閉聊天室的承諾是「不由 Notify+ 增強，但仍顯示 LINE 原通知」。
        // 保留舊 key 原語意；使用者在新版重新開啟再關閉後才改用上面的完全靜音 set。
        if (notificationMode == NotificationClassifier.MODE_LEGACY_ORIGINAL_ONLY) {
            recordChatMetadata(chatTitle, chatType, sbn.postTime)
            Log.d(TAG, "沿用舊版原始通知模式 room=${chatTitle.hashCode()} profile=$profileKey")
            return
        }

        // 一般聊天室若無法顯示 Notify+ 副本，必須 fail-open 保留 LINE。明確設定的完全靜音
        // 已在上方先處理，仍然生效；這兩種產品語意不可混在一起。
        if (!canPostReplacementNotifications()) {
            recordChatMetadata(chatTitle, chatType, sbn.postTime)
            Log.w(TAG, "Notify+ 無法顯示通知；fail-open 保留 LINE 原通知")
            return
        }

        // Nothing OS + LINE 26.10.1 實機會把同一訊息以 conversation + legacy mirror 各送一次。
        // 僅合併已觀察到的嚴格 pair shape，並要求完整 MessagingStyle SHA 完全相同；任何欄位
        // 缺失或第三個 callback 都照常處理。這避免回到舊版「同房＋同文字＋整秒」造成漏訊息。
        val mirrorNow = android.os.SystemClock.elapsedRealtime()
        recentMirroredVariants.entries.removeAll {
            mirrorNow - it.value.source.seenElapsed > 500L
        }
        mirrorPoisonUntil.entries.removeAll { (_, until) -> mirrorNow > until }
        val mirrorSignature = mirroredPayloadFingerprint(sbn, roomKey, sender, text)
        val currentSourceVariant = notification.shortcutId?.let { shortcutId ->
            NotificationClassifier.MirrorSource(
                key = sbn.key,
                id = sbn.id,
                tag = sbn.tag,
                shortcutId = shortcutId,
                postTime = sbn.postTime,
                seenElapsed = mirrorNow,
            )
        }
        var mirrorSignatureToStore = if (
            currentSourceVariant != null &&
            NotificationClassifier.isObservedLineConversationPrimary(currentSourceVariant)
        ) {
            mirrorSignature
        } else {
            null
        }
        if (mirrorSignature != null && currentSourceVariant != null &&
            mirrorSignature !in mirrorPoisonUntil
        ) {
            val existing = recentMirroredVariants[mirrorSignature]
            if (existing != null) {
                // one-shot consume；同 signature 的第 3 個 callback 寧可重複，不能再 suppress。
                recentMirroredVariants.remove(mirrorSignature)
                if (existing.stateEpoch == notificationStateEpoch &&
                    NotificationClassifier.isObservedLineMirrorPair(
                        existing.source,
                        currentSourceVariant,
                    )
                ) {
                    mirrorPoisonUntil[mirrorSignature] = mirrorNow + 500L
                    if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
                        scheduleOriginalCancellation(
                            key = sbn.key,
                            receipt = existing.receipt,
                            requireExactGeneration = true,
                            requiredStateEpoch = notificationStateEpoch,
                            expectedSource = currentSourceVariant,
                            expectedMirrorSignature = mirrorSignature,
                        )
                    }
                    Log.d(TAG, "合併 LINE conversation/legacy mirror callback")
                    return
                }
                // 同 SHA 但 shape 不符代表 identity collision/未知版本；本次照常處理且不再建 cache。
                mirrorPoisonUntil[mirrorSignature] = mirrorNow + 500L
                mirrorSignatureToStore = null
            }
        } else if (mirrorSignature != null && mirrorSignature in mirrorPoisonUntil) {
            mirrorSignatureToStore = null
        }

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
        val ourNotification = threadNotifIds[roomKey]
        if (ourNotification != null) {
            val activeState = activeState(manager, ourNotification)
            if (activeState == false && ourNotification !in pendingThreadRefCounts) {
                invalidateMirrorCandidates()
                chatRooms[roomKey]?.clearMessages()
                Log.d(TAG, "通知已被滑掉，清除 room=${chatTitle.hashCode()} 緩衝")
            } else if (activeState == null) {
                Log.w(TAG, "無法查詢對話串通知狀態；保留既有 buffer")
            }
        }

        Log.d(TAG, "收到訊息 room=${chatTitle.hashCode()} profile=$profileKey type=$chatType")

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
        val replacementReceipt = when (style) {
            "apple" -> postAppleStyleNotification(room, message)
            else -> postThreadStyleNotification(room)
        }
        if (replacementReceipt == null) {
            room.removeMessage(message)
        } else if (mirrorSignatureToStore != null && currentSourceVariant != null) {
            recentMirroredVariants[mirrorSignatureToStore] = MirroredVariantEntry(
                source = currentSourceVariant,
                receipt = replacementReceipt,
                stateEpoch = notificationStateEpoch,
            )
        }

        // 延遲取消 LINE 原通知（確保我們的 contentIntent 不會因為 LINE 通知被取消而失效）
        if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true) && replacementReceipt != null) {
            scheduleOriginalCancellation(sbn.key, replacementReceipt)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        if (sbn.packageName in LINE_PACKAGES) {
            pendingOriginalCancels.remove(sbn.key)?.let(handler::removeCallbacks)
        }

        // (A) 我們「自己」的通知被移除（用戶滑掉 / 點掉開 LINE / 回覆關閉）
        //     → 視為「這個聊天室已處理」，清掉整組通知 + buffer，避免殘留或舊訊息又冒回來。
        if (sbn.packageName == packageName) {
            // 任何 replacement removal 都代表既有 receipt 可能已失效；先讓尚未執行的
            // mirror 合併保守地 fail-open。非使用者 removal 仍不可清除聊天室內容。
            invalidateMirrorCandidates()
            // APP_CANCEL / 系統淘汰是程式或 framework 行為，不可誤當使用者已處理整個聊天室。
            if (reason != REASON_CLICK && reason != REASON_CANCEL && reason != REASON_CANCEL_ALL) return
            val roomKey = sbn.notification.extras.getString(EXTRA_ROOM_KEY)
                ?: findRoomKeyByNotification(sbn.tag, sbn.id)
                ?: return
            clearChatGroup(roomKey)
            Log.d(TAG, "本機通知被移除，清整組")
            return
        }

        if (sbn.packageName !in LINE_PACKAGES) return

        // 已讀同步只在「非取代模式」做：取代模式下 LINE 原通知已被我們在 200ms 殺掉，
        // 等不到它被移除，自然判不了已讀（先天限制，不是 bug）。
        if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) return

        // 只有「使用者真的在 LINE 端把原通知處理掉」才同步清我們的，靠移除原因 reason 分流：
        //   8 APP_CANCEL：LINE 自己收掉（在 LINE 內已讀，最主要）  9 APP_CANCEL_ALL：一次清光（開 App/全部已讀）
        //   1 CLICK：點了 LINE 那則   2 CANCEL：滑掉 LINE 那則
        // 排掉系統打掃（群組摘要連鎖、頻道變更）與我們自己取消，避免誤清。
        if (reason != REASON_APP_CANCEL && reason != REASON_APP_CANCEL_ALL &&
            reason != REASON_CLICK && reason != REASON_CANCEL
        ) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val subText = extras.getCharSequence("android.subText")?.toString()
        val chatTitle = subText ?: title
        val roomKey = profileKeyOf(sbn) + KEY_SEP + chatTitle

        // 使用者在 LINE 端讀掉/處理掉原通知 → 同步清除我們這則
        clearChatGroup(roomKey)
        Log.d(TAG, "LINE 已讀同步 reason=$reason room=${chatTitle.hashCode()}")
    }

    /** 清掉某聊天室的所有通知（thread / apple children / summary）與 in-memory buffer。 */
    private fun clearChatGroup(roomKey: String) {
        invalidateMirrorCandidates()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val refs = mutableListOf<NotificationRef>()
        threadNotifIds.remove(roomKey)?.let(refs::add)
        summaryIds.remove(roomKey)?.let(refs::add)
        appleNotifIds.remove(roomKey)?.let(refs::addAll)
        appleNotificationOrder.removeAll { it.first == roomKey }
        refs.forEach { ref -> appleMessagesByRef.remove(ref) }

        // service/process 重建後記憶體索引可能已空；extras 仍可精準找回舊的自有通知。
        runCatching { manager.activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .filter { it.notification.extras.getString(EXTRA_ROOM_KEY) == roomKey }
            .mapTo(refs) { NotificationRef(it.tag.orEmpty(), it.id) }

        refs.distinct().forEach { ref ->
            if (ref.tag.isEmpty()) manager.cancel(ref.id) else manager.cancel(ref.tag, ref.id)
        }
        chatRooms.remove(roomKey)?.clearMessages()
    }

    internal fun clearChatGroupFromAccessibility(roomKey: String) {
        clearChatGroup(roomKey)
        Log.d(TAG, "Accessibility 已清除 Notify+ 聊天室 room=${roomKey.hashCode()}")
    }

    /** 聊天室在管理頁被關閉時，立即清除目前仍顯示的 Notify+ 與原始 LINE 通知。 */
    fun clearChatNotifications(chatTitle: String) {
        chatRooms
            .filterValues { it.chatTitle == chatTitle }
            .keys
            .toList()
            .forEach(::clearChatGroup)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .filter { it.notification.extras.getString(EXTRA_CHAT_TITLE) == chatTitle }
            .mapNotNull { it.notification.extras.getString(EXTRA_ROOM_KEY) }
            .distinct()
            .forEach(::clearChatGroup)

        runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .filter { it.packageName in LINE_PACKAGES && !isCallNotification(it.notification) }
            .filter { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence("android.title")?.toString()
                val subText = extras.getCharSequence("android.subText")?.toString()
                title != null && NotificationClassifier.chatTitleOf(title, subText) == chatTitle
            }
            .forEach { cancelNotification(it.key) }
    }

    /** 切換通知樣式時清掉現有兩種樣式，避免舊、新通知同時殘留。 */
    fun clearAllEnhancedNotifications() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        chatRooms.keys.toList().forEach(::clearChatGroup)
        manager.cancelAll()
        threadNotifIds.clear()
        summaryIds.clear()
        appleNotifIds.clear()
        appleNotificationOrder.clear()
        appleMessagesByRef.clear()
    }

    /** 由穩定 tag + type id 反查所屬 roomKey。 */
    private fun findRoomKeyByNotification(tag: String?, id: Int): String? {
        fun NotificationRef.matches(): Boolean = this.id == id && this.tag == tag
        threadNotifIds.entries.firstOrNull { it.value.matches() }?.let { return it.key }
        summaryIds.entries.firstOrNull { it.value.matches() }?.let { return it.key }
        appleNotifIds.entries.firstOrNull { (_, refs) -> refs.any { it.matches() } }?.let { return it.key }
        return null
    }

    /**
     * 處理用戶的快速回覆（由 ReplyRelayReceiver 轉發給 LINE 後呼叫，同 process）。
     * 一律先把回覆加進對話 + 重貼通知（接管系統樂觀回覆、停 spinner、顯示回覆＋本人頭貼，
     * 也讓回覆留得住、不被下一則訊息重建洗掉）；「回覆後清除」開啟時再延遲整組清掉。
     */
    fun handleUserReply(
        roomKey: String,
        notificationTag: String?,
        notificationId: Int,
        replyText: CharSequence,
    ) {
        val room = chatRooms[roomKey]
        if (room == null) {
            // 沒有對話狀態 → 至少關掉這則停 spinner
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationTag == null) manager.cancel(notificationId)
            else manager.cancel(notificationTag, notificationId)
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
        val replyMessage = room.messages.last()
        val style = prefs.getString(KEY_NOTIFICATION_STYLE, "thread") ?: "thread"
        val receipt = if (style == "apple") {
            val repliedRef = appleNotifIds[roomKey]
                ?.firstOrNull { it.tag == notificationTag && it.id == notificationId }
            postAppleStyleNotification(
                room,
                replyMessage,
                replacementVictim = repliedRef,
            )
        } else {
            postThreadStyleNotification(room)
        }
        if (receipt == null) room.removeMessage(replyMessage)

        if (prefs.getBoolean(KEY_CLEAR_AFTER_REPLY, true)) {
            // 「回覆後清除」：等系統樂觀回覆狀態落定後再整組清掉，避免被蓋回來。
            handler.postDelayed({ clearChatGroup(roomKey) }, 2000)
            Log.d(TAG, "回覆後延遲清除 room=${room.chatTitle.hashCode()}")
        } else {
            Log.d(TAG, "回覆已加入對話並保留 room=${room.chatTitle.hashCode()}")
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
    private fun postThreadStyleNotification(room: ChatRoom): ReplacementReceipt? {
        val notifRef = threadNotifIds.getOrPut(room.roomKey) {
            notificationRef("thread", room.roomKey, THREAD_NOTIFICATION_ID)
        }
        val generation = nextPostGeneration.incrementAndGet()

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
        applyPrivateVisibility(builder)

        room.senderIcon?.let { builder.setLargeIcon(it) }
        room.contentIntent?.let { builder.setContentIntent(it) }

        addIdentityExtras(
            builder,
            room,
            "thread",
            generation,
        )
        addReplyAction(builder, room, notifRef)

        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        markThreadPending(notifRef)
        val posted = runCatching { mgr.notify(notifRef.tag, notifRef.id, builder.build()) }
            .onFailure { Log.e(TAG, "張貼對話串通知失敗", it) }
            .isSuccess
        if (!posted) {
            unmarkThreadPending(notifRef)
            threadNotifIds.remove(room.roomKey, notifRef)
            return null
        }
        // activeNotifications 在部分 OEM 不是同步可見；短 grace 期間若同房新訊息抵達，
        // 不可把前一則誤當成已被使用者滑掉而清空 buffer。ref-count 支援 burst 更新。
        handler.postDelayed({ unmarkThreadPending(notifRef) }, 1_000)

        Log.d(TAG, "對話串通知 room=${room.chatTitle.hashCode()} count=${room.messages.size}")
        return ReplacementReceipt(
            posts = listOf(
                PostedNotification(
                    ref = notifRef,
                    generation = generation,
                    acceptNewerGeneration = true,
                )
            ),
            committed = true,
        )
    }

    // ==========================================
    // Apple 模式
    // ==========================================
    private fun postAppleStyleNotification(
        room: ChatRoom,
        newMessage: ChatMessage,
        replacementVictim: NotificationRef? = null,
    ): ReplacementReceipt? {
        val groupKey = "linenotify_${room.roomKey}"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Preflight：先確認目前看得到的狀態至少存在安全方案；真正提交前仍會重算，
        // 避免 burst 中另一筆 pending transaction 成敗後讓這份計畫過期。
        if (planAppleEvictions(
                manager = manager,
                currentRoomKey = room.roomKey,
                additionalChildCount = 1,
                mandatoryEvictionRef = replacementVictim,
            ) == null
        ) {
            Log.w(TAG, "Apple child budget 無安全淘汰目標；保留本次 LINE 原通知")
            return null
        }

        // generation 是 process 內單調遞增值，確保即使同一房、同一毫秒、同文字、同 sender
        // 的兩個 callback 也不會得到同 tag 而互相覆蓋。
        val childGeneration = nextPostGeneration.incrementAndGet()
        val token = "$childGeneration:${newMessage.timestamp}:${room.messages.size}:" +
            NotificationClassifier.dedupeFingerprint(
                room.roomKey,
                newMessage.text,
                newMessage.timestamp,
            ).take(12)
        val messageRef = notificationRef(
            kind = "apple-child",
            roomKey = room.roomKey,
            id = APPLE_CHILD_NOTIFICATION_ID,
            messageToken = token,
        )
        appleNotifIds.getOrPut(room.roomKey) { mutableListOf() }.add(messageRef)
        appleMessagesByRef[messageRef] = newMessage
        markApplePending(messageRef)

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
            .setStyle(NotificationCompat.BigTextStyle().bigText(newMessage.text))
            .setWhen(newMessage.timestamp)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF06C755.toInt())
        applyPrivateVisibility(msgBuilder)

        newMessage.senderIcon?.let { msgBuilder.setLargeIcon(it) }
        room.contentIntent?.let { msgBuilder.setContentIntent(it) }

        addIdentityExtras(
            msgBuilder,
            room,
            "apple-child",
            childGeneration,
        )
        addReplyAction(msgBuilder, room, messageRef)
        appleNotificationOrder.addLast(room.roomKey to messageRef)
        val childPosted = runCatching { manager.notify(messageRef.tag, messageRef.id, msgBuilder.build()) }
            .onFailure { Log.e(TAG, "張貼 Apple child 通知失敗", it) }
            .isSuccess
        if (!childPosted) {
            unmarkApplePending(messageRef)
            appleNotifIds[room.roomKey]?.remove(messageRef)
            appleMessagesByRef.remove(messageRef)
            appleNotificationOrder.remove(room.roomKey to messageRef)
            return null
        }

        // Summary
        val summaryWasNew = room.roomKey !in summaryIds
        val summaryRef = summaryIds.getOrPut(room.roomKey) {
            notificationRef("apple-summary", room.roomKey, SUMMARY_NOTIFICATION_ID)
        }
        val summaryGeneration = nextPostGeneration.incrementAndGet()
        markApplePending(summaryRef)

        val summaryBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.stanslab.linenotify.R.drawable.ic_notif)
            .setContentTitle(acctPrefix(room) + room.chatTitle)
            .setContentText(
                getString(
                    R.string.notification_summary_text,
                    minOf(
                        appleNotifIds[room.roomKey]?.size ?: 0,
                        MAX_APPLE_CHILDREN_PER_ROOM,
                    ),
                )
            )
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        applyPrivateVisibility(summaryBuilder)

        room.senderIcon?.let { summaryBuilder.setLargeIcon(it) }
        room.contentIntent?.let { summaryBuilder.setContentIntent(it) }
        addIdentityExtras(summaryBuilder, room, "apple-summary", summaryGeneration)

        val summaryPosted = runCatching {
            manager.notify(summaryRef.tag, summaryRef.id, summaryBuilder.build())
        }.onFailure { Log.e(TAG, "張貼 Apple summary 通知失敗", it) }.isSuccess
        if (!summaryPosted) {
            unmarkApplePending(messageRef)
            unmarkApplePending(summaryRef)
            cancelAppleChild(manager, room.roomKey, messageRef)
            if (summaryWasNew) summaryIds.remove(room.roomKey, summaryRef)
            return null
        }

        Log.d(TAG, "Apple 分組通知 room=${room.chatTitle.hashCode()} count=${room.messages.size}")
        val receipt = ReplacementReceipt(
            posts = listOf(
                PostedNotification(messageRef, childGeneration, acceptNewerGeneration = false),
                PostedNotification(summaryRef, summaryGeneration, acceptNewerGeneration = true),
            ),
            committed = false,
        )
        handler.postDelayed({
            unmarkApplePending(messageRef)
            unmarkApplePending(summaryRef)
            val replacementIsActive = receipt.posts.all { isActive(manager, it) }
            val commitEvictions = if (replacementIsActive) {
                planAppleEvictions(
                    manager = manager,
                    currentRoomKey = room.roomKey,
                    additionalChildCount = 0,
                    protectedRef = messageRef,
                    excludeOtherPendingChildren = true,
                    mandatoryEvictionRef = replacementVictim,
                )
            } else {
                null
            }
            if (replacementIsActive && commitEvictions != null) {
                val affectedRooms = mutableSetOf<String>()
                commitEvictions.forEach {
                    cancelAppleChild(
                        manager,
                        it.roomKey,
                        it.ref,
                        removeBufferedMessage = true,
                    )
                    affectedRooms.add(it.roomKey)
                }
                affectedRooms.forEach { refreshAppleSummary(manager, it) }
                // 若較早的實驗版曾留下 thread fallback，只在 Apple child+summary 確認 active 後撤掉。
                threadNotifIds.remove(room.roomKey)?.let { manager.cancel(it.tag, it.id) }
                receipt.committed = true
            } else {
                invalidateMirrorCandidates()
                if (replacementIsActive) {
                    Log.w(TAG, "Apple budget 提交前狀態已變更；回滾本次副本並保留 LINE 原通知")
                }
                appleNotifIds[room.roomKey]?.remove(messageRef)
                appleMessagesByRef.remove(messageRef)
                appleNotificationOrder.remove(room.roomKey to messageRef)
                if (isActive(manager, NotificationRef(messageRef.tag, messageRef.id))) {
                    manager.cancel(messageRef.tag, messageRef.id)
                }
                if (summaryWasNew) {
                    val exactSummary = PostedNotification(
                        summaryRef,
                        summaryGeneration,
                        acceptNewerGeneration = false,
                    )
                    when {
                        isActive(manager, exactSummary) -> {
                            summaryIds.remove(room.roomKey, summaryRef)
                            manager.cancel(summaryRef.tag, summaryRef.id)
                        }
                        !isActive(manager, summaryRef) ->
                            summaryIds.remove(room.roomKey, summaryRef)
                        // 較新的 callback 已更新同一 summary ref；不可把它當成 A 的 rollback 一起取消。
                        else -> Unit
                    }
                }
                room.removeMessage(newMessage)
                refreshAppleSummary(manager, room.roomKey)
            }
        }, 200)
        return receipt
    }

    // ==========================================
    // 共用
    // ==========================================

    private fun cancelAppleChild(
        manager: NotificationManager,
        roomKey: String,
        ref: NotificationRef,
        removeBufferedMessage: Boolean = false,
    ) {
        invalidateMirrorCandidates()
        val roomRefs = appleNotifIds[roomKey]
        roomRefs?.remove(ref)
        val bufferedMessage = appleMessagesByRef.remove(ref)
        if (removeBufferedMessage && bufferedMessage != null) {
            chatRooms[roomKey]?.removeMessage(bufferedMessage)
        }
        appleNotificationOrder.remove(roomKey to ref)
        manager.cancel(ref.tag, ref.id)
        if (roomRefs?.isEmpty() == true) {
            appleNotifIds.remove(roomKey)
            summaryIds.remove(roomKey)?.let { manager.cancel(it.tag, it.id) }
        }
    }

    /** 以實際仍索引中的 child 數重建摘要，修正 eviction／rollback／回覆後的計數。 */
    private fun refreshAppleSummary(manager: NotificationManager, roomKey: String) {
        val childCount = appleNotifIds[roomKey]?.size ?: 0
        val summaryRef = summaryIds[roomKey] ?: return
        if (childCount == 0) {
            summaryIds.remove(roomKey, summaryRef)
            manager.cancel(summaryRef.tag, summaryRef.id)
            return
        }
        val room = chatRooms[roomKey] ?: return
        val generation = nextPostGeneration.incrementAndGet()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(acctPrefix(room) + room.chatTitle)
            .setContentText(
                getString(
                    R.string.notification_summary_text,
                    minOf(childCount, MAX_APPLE_CHILDREN_PER_ROOM),
                )
            )
            .setAutoCancel(true)
            .setGroup("linenotify_${room.roomKey}")
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        applyPrivateVisibility(builder)
        room.senderIcon?.let { builder.setLargeIcon(it) }
        room.contentIntent?.let { builder.setContentIntent(it) }
        addIdentityExtras(builder, room, "apple-summary", generation)

        markApplePending(summaryRef)
        val posted = runCatching {
            manager.notify(summaryRef.tag, summaryRef.id, builder.build())
        }.onFailure { Log.e(TAG, "更新 Apple summary 失敗", it) }.isSuccess
        if (posted) handler.postDelayed({ unmarkApplePending(summaryRef) }, 500)
        else unmarkApplePending(summaryRef)
    }

    /** 聊天內容維持 PRIVATE；鎖定畫面的 publicVersion 只顯示通用提示，不暴露原文。 */
    private fun applyPrivateVisibility(builder: NotificationCompat.Builder) {
        val publicVersion = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_public_text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        builder
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
    }

    private fun addReplyAction(
        builder: NotificationCompat.Builder,
        room: ChatRoom,
        notificationRef: NotificationRef,
    ) {
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
                    data = Uri.parse(
                        "notifyplus://reply/${Uri.encode(notificationRef.tag)}/${notificationRef.id}"
                    )
                    putExtra(ReplyRelayReceiver.EXTRA_RESULT_KEY, resultKey)
                    putExtra(ReplyRelayReceiver.EXTRA_LINE_PENDING_INTENT, lineActionIntent)
                    putExtra(ReplyRelayReceiver.EXTRA_NOTIF_ID, notificationRef.id)
                    putExtra(ReplyRelayReceiver.EXTRA_NOTIF_TAG, notificationRef.tag)
                    putExtra(ReplyRelayReceiver.EXTRA_CHAT_TITLE, room.roomKey)
                }
                val mutableFlag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else 0
                val relayPendingIntent = PendingIntent.getBroadcast(
                    this,
                    31 * notificationRef.tag.hashCode() + notificationRef.id,
                    relayIntent,
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
        recordChatMetadata(chatTitle, chatType, timestamp)
        if (avatar != null) saveAvatar(chatTitle, avatar)
    }

    private fun recordChatMetadata(chatTitle: String, chatType: String, timestamp: Long) {
        saveKnownChat(chatTitle, chatType)
        updateLastActive(chatTitle, timestamp)
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
            Log.w(TAG, "存頭貼失敗 room=${chatTitle.hashCode()}", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
