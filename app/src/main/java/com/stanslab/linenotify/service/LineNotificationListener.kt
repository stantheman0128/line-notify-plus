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
        // 舊版語意：只停用 Notify+ 增強，仍保留 LINE 原始通知。不可直接改成完全靜音，
        // 否則既有使用者升級後會無預警漏通知。
        const val KEY_DISABLED_CHATS = "disabled_chats"
        // 新版由使用者明確關閉後才寫入；完全靜音（含 LINE 原通知與 @all）。
        const val KEY_MUTED_CHATS = "fully_muted_chats_v2"
        const val KEY_NOTIFICATION_STYLE = "notification_style"
        const val KEY_CLEAR_AFTER_REPLY = "clear_after_reply"
        const val KEY_CLEAR_AFTER_READ = "clear_after_read"
        const val KEY_CHAT_LAST_ACTIVE = "chat_last_active"   // JSON {聊天室名: epochMillis}
        const val KEY_CHAT_SORT = "chat_sort"                 // "recent" | "name" | "type"
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
        private const val EXTRA_ROOM_KEY = "com.stanslab.linenotify.extra.ROOM_KEY"
        private const val EXTRA_CHAT_TITLE = "com.stanslab.linenotify.extra.CHAT_TITLE"
        private const val EXTRA_NOTIFICATION_KIND = "com.stanslab.linenotify.extra.NOTIFICATION_KIND"
        private const val EXTRA_IS_GROUP = "com.stanslab.linenotify.extra.IS_GROUP"
        private const val EXTRA_PROFILE_KEY = "com.stanslab.linenotify.extra.PROFILE_KEY"
        private const val EXTRA_POST_GENERATION = "com.stanslab.linenotify.extra.POST_GENERATION"
        private const val MAX_APPLE_CHILDREN_PER_ROOM = 8
        private const val MAX_APPLE_CHILDREN_TOTAL = 24
        private const val ICON_COMPARE_SIZE = 64
        private const val ICON_MATCH_PERCENT = 90
        private const val SELF_AVATAR_DIR = "self_avatars"
        private const val SELF_AVATAR_SIZE = 192

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
    private data class RecentPostedEntry(
        val receipt: ReplacementReceipt,
        val stateEpoch: Long,
        val seenElapsed: Long,
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
    private val recentMirroredVariants = linkedMapOf<String, MirroredVariantEntry>()
    // 第 2 層保底去重：fingerprint(房間+全文+毫秒 when) → 最近成功貼出的訊息。嚴格 mirror
    // 合併之後的兜底，吸收未來任何欄位漂移導致的重複 callback（見 onNotificationPosted）。
    private val recentPostedPayloads = linkedMapOf<String, RecentPostedEntry>()
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
        recentPostedPayloads.clear()
        mirrorPoisonUntil.clear()
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
        // activeNotifications 在部分 OEM 不是同步可見（realme UI 實證會慢於 200ms）。
        // 一次性檢查失敗就放棄會讓 LINE 原通知永久殘留，改成重試階梯；全部失敗才 fail-open。
        val retryDelays = longArrayOf(200L, 500L, 900L)
        var attempt = 0
        lateinit var cancelTask: Runnable
        cancelTask = Runnable {
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
                        val sourceText = extras.getCharSequence("android.text")?.toString()
                            ?: return@any false
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
                pendingOriginalCancels.remove(key)
                if (currentNotificationIsRedacted(key)) {
                    Log.w(TAG, "原通知已被系統遮蔽（取消前重讀命中）；保留 LINE 原通知")
                } else {
                    cancelNotification(key)
                }
            } else if (attempt + 1 < retryDelays.size && pendingOriginalCancels[key] === cancelTask) {
                attempt++
                Log.d(TAG, "副本尚未可查，${retryDelays[attempt]}ms 後重試取消原通知 attempt=$attempt")
                handler.postDelayed(cancelTask, retryDelays[attempt])
            } else {
                pendingOriginalCancels.remove(key)
                Log.w(TAG, "找不到已提交的 Notify+ 副本；保留 LINE 原通知")
            }
        }
        pendingOriginalCancels[key] = cancelTask
        handler.postDelayed(cancelTask, retryDelays[0])
    }

    /**
     * 接管 LINE 的 group summary／堆疊摘要（詳見 [NotificationClassifier.shouldCancelLineSummary]）。
     * 延遲 350ms：讓同批 child callback 的副本先貼出，才能通過「內容已由別處承載」的確認。
     * summary 每次被 LINE 更新都會重新走到這裡（onNotificationPosted 開頭已撤舊任務），
     * 首則訊息若 summary 先到而暫時保留，下一次更新就會補收。
     */
    private fun scheduleLineSummaryCancellation(sbn: StatusBarNotification) {
        if (!prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) return
        // GROUP_SUMMARY 分支在 redaction 守門之前，這裡補同等防線：遮蔽版 summary 是
        // 「有私密訊息」的唯一提示，一律保留。之後若被 LINE 更新，callback 會重新走到這裡。
        val summaryText = sbn.notification.extras.getCharSequence("android.text")?.toString()
        if (NotificationClassifier.textMatchesRedactionPlaceholder(summaryText, systemRedactedText())) {
            Log.w(TAG, "LINE summary 已被系統遮蔽；保留不取消")
            return
        }
        val key = sbn.key
        // summary 的取消只能由「同 profile」的承載者背書：雙開帳號或別 profile 的卡
        // 不能證明這個 summary 的內容已被承載（獨立審查 2026-07-19 反例）。
        val summaryProfileKey = profileKeyOf(sbn)
        val task = Runnable {
            pendingOriginalCancels.remove(key)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val replacementActive = runCatching {
                manager.activeNotifications.orEmpty().any { ours ->
                    val roomKey = ours.notification.extras.getString(EXTRA_ROOM_KEY)
                        ?: findRoomKeyByNotification(ours.tag, ours.id)
                    NotificationClassifier.roomKeyBelongsToProfile(roomKey, summaryProfileKey)
                }
            }.getOrDefault(false)
            val lineChildActive = runCatching {
                activeNotifications.orEmpty().any { active ->
                    active.packageName in LINE_PACKAGES &&
                        profileKeyOf(active) == summaryProfileKey &&
                        active.key != key &&
                        // 同上：group summary 看 flags，不看 extras。舊寫法恆為 true，
                        // 會把別的 summary 誤算成「LINE child 還在」而放行取消。
                        (active.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                        NotificationClassifier.isSupportedMessageChannel(active.notification.channelId) &&
                        !isCallNotification(active.notification)
                }
            }.getOrDefault(false)
            if (NotificationClassifier.shouldCancelLineSummary(
                    replaceEnabled = prefs.getBoolean(KEY_REPLACE_ORIGINAL, true),
                    replacementActive = replacementActive,
                    lineChildActive = lineChildActive,
                )
            ) {
                if (currentNotificationIsRedacted(key)) {
                    Log.w(TAG, "LINE summary 已被系統遮蔽（取消前重讀命中）；保留不取消")
                } else {
                    cancelNotification(key)
                    Log.d(TAG, "接管 LINE summary，已取消")
                }
            } else {
                Log.d(TAG, "LINE summary 為唯一殘留；fail-open 保留")
            }
        }
        pendingOriginalCancels[key] = task
        handler.postDelayed(task, 350)
    }

    /**
     * 取消指令送出前的最後重讀：該 key 的現行 text 已是系統遮蔽占位字時回 true。
     * 同 key 在延遲窗內被系統換成遮蔽版時，callback 撤任務攔不到「已在佇列後段」的更新，
     * 這裡是最後一道。看不到該通知（部分 OEM activeNotifications 可見性延遲）時回 false
     * 照取消——與歷代版本相同的 fail 方向；殘餘的 binder 飛行窗口為 API 固有、無法原子化。
     */
    private fun currentNotificationIsRedacted(key: String): Boolean = runCatching {
        activeNotifications.orEmpty().firstOrNull { it.key == key }
            ?.notification?.extras?.getCharSequence("android.text")?.toString()
            ?.let { current ->
                NotificationClassifier.textMatchesRedactionPlaceholder(current, systemRedactedText())
            }
    }.getOrNull() ?: false

    /**
     * 通知的 largeIcon 是不是就是來源 App 的圖示。
     *
     * AOSP 的 redaction clone 會把 largeIcon 換成 App 圖示。2026-07-12 Nothing A059P 實證：
     * 被誤建出來的「LINE」聊天室，頭貼檔 chat_avatars/2336756.png 正是 LINE 的 App 圖示；
     * 而 [recordChat] 只在 largeIcon 非 null 時存檔、沒有任何預設圖 fallback，
     * 所以那張圖必然是通知自己帶來的。
     *
     * 這是 [NotificationClassifier.matchesAospCloneShape] 用來跟「聊天室名剛好等於 App 名稱」
     * 的正常 1:1 對話分家的關鍵訊號——後者帶的是帳號頭像。
     *
     * 任何一步取不到就回 false，維持既有行為（不當成 clone）。
     */
    private fun largeIconMatchesAppIcon(sbn: StatusBarNotification): Boolean = runCatching {
        val large = sbn.notification.getLargeIcon()?.loadDrawable(this) ?: return false
        val appIcon = packageManager.getApplicationIcon(sbn.packageName)
        val fromNotification = rasterizeIcon(large) ?: return false
        val fromPackage = rasterizeIcon(appIcon) ?: run {
            fromNotification.recycle()
            return false
        }
        val identical = bitmapsMostlyIdentical(fromNotification, fromPackage)
        fromNotification.recycle()
        fromPackage.recycle()
        identical
    }.getOrDefault(false)

    /** 把 icon 畫到固定尺寸畫布，消掉密度與 adaptive icon 造成的尺寸差異。 */
    private fun rasterizeIcon(
        drawable: android.graphics.drawable.Drawable,
        size: Int = ICON_COMPARE_SIZE,
    ): Bitmap? = runCatching {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()

    /** 逐點相同率達 [ICON_MATCH_PERCENT]% 就視為同一張圖，容忍縮放插值的少量差異。 */
    private fun bitmapsMostlyIdentical(a: Bitmap, b: Bitmap): Boolean {
        val total = ICON_COMPARE_SIZE * ICON_COMPARE_SIZE
        val pixelsA = IntArray(total)
        val pixelsB = IntArray(total)
        a.getPixels(pixelsA, 0, ICON_COMPARE_SIZE, 0, 0, ICON_COMPARE_SIZE, ICON_COMPARE_SIZE)
        b.getPixels(pixelsB, 0, ICON_COMPARE_SIZE, 0, 0, ICON_COMPARE_SIZE, ICON_COMPARE_SIZE)
        var same = 0
        for (i in 0 until total) if (pixelsA[i] == pixelsB[i]) same++
        return same * 100 >= total * ICON_MATCH_PERCENT
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
        createNotificationChannel()
        Log.d(TAG, "Notify+ 服務啟動")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pendingOriginalCancels.clear()
        recentMirroredVariants.clear()
        recentPostedPayloads.clear()
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
        restorePersistedSelfAvatars()
        enforceMutedPreferencesOnActiveNotifications()
        Log.d(TAG, "通知監聽已連線")
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
            // 用 mute-eligible 而非白名單：listener 重連時要連公告類的殘留一起掃掉。
            .filter { NotificationClassifier.isMuteEligibleChannel(it.notification.channelId) }
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
        val extras = notification.extras

        // 靜音硬封鎖必須排在「頻道白名單 / GROUP_SUMMARY / title,text 空值 / 系統遮蔽」這四道
        // 早退之前。那四道 return 掉的通知照樣會出現在使用者面前，靜音排在它們後面等於對它們
        // 全部失效——這就是「我明明把聊天室關掉了，@all 和社群公告還是會跳」的成因。
        //
        // 仍排在通話守門之後：來電的 title 是對方名字、subText 為 null，形狀跟 1:1 聊天室
        // 一模一樣，靜音搶在前面會連來電一起撤掉。
        //
        // 這裡只做「撤掉」，不重建 Notify+ 卡片，所以能安全地涵蓋公告類頻道
        // （見 NotificationClassifier.isMuteEligibleChannel）。
        if (NotificationClassifier.isMuteEligibleChannel(channelId)) {
            val gateTitle = extras.getCharSequence("android.title")?.toString()
            val gateSubText = extras.getCharSequence("android.subText")?.toString()
            val gateText = extras.getCharSequence("android.text")?.toString()
            val gateAppLabel = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(sbn.packageName, 0)
                ).toString()
            }.getOrNull()

            // 純公告型社群只走 SquareActivity，永遠不會跑完下面那條會 recordChat 的管線，
            // 於是不會出現在聊天室管理清單裡，使用者連「關掉它」都做不到。這裡補登錄。
            if (NotificationClassifier.isSquareActivityChannel(channelId) &&
                gateTitle != null &&
                NotificationClassifier.isAttributableChatTitle(gateTitle, gateSubText, gateAppLabel) &&
                !NotificationClassifier.textMatchesRedactionPlaceholder(gateText, systemRedactedText())
            ) {
                val announceTitle = NotificationClassifier.chatTitleOf(gateTitle, gateSubText)
                recordChatMetadata(
                    announceTitle,
                    forcedChatType(announceTitle) ?: NotificationClassifier.TYPE_COMMUNITY,
                    sbn.postTime,
                )
            }

            if (NotificationClassifier.shouldHardMute(
                    channelId = channelId,
                    title = gateTitle,
                    subText = gateSubText,
                    text = gateText,
                    isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                    sourceAppLabel = gateAppLabel,
                    systemRedactedText = systemRedactedText(),
                    mutedChats = prefs.getStringSet(KEY_MUTED_CHATS, emptySet()) ?: emptySet(),
                )
            ) {
                val mutedTitle = NotificationClassifier.chatTitleOf(gateTitle!!, gateSubText)
                clearChatGroup(
                    NotificationClassifier.roomKeyOf(profileKeyOf(sbn), mutedTitle)
                )
                cancelNotification(sbn.key)
                Log.d(
                    TAG,
                    "靜音前置守門已封鎖 room=${mutedTitle.hashCode()} " +
                        "channelHash=${channelId?.hashCode()}"
                )
                return
            }
        }

        if (!NotificationClassifier.isSupportedMessageChannel(channelId)) {
            Log.d(TAG, "略過非聊天通知 channelHash=${channelId?.hashCode()}")
            return
        }

        // LINE 26.11.0 起 id=16880000 從 legacy mirror 變成 GROUP_SUMMARY（A065 dumpsys 實證）。
        // summary 不會被 SystemUI 回收，放著會在部分 OEM（realme UI 實證）以「N則新訊息＋預覽」
        // 整卡殘留，看起來就像原通知沒被取代。此檢查必須在 title/text 空值 return 之前——
        // summary 不保證帶 android.text。取消前仍會確認內容已由別的通知承載（fail-open）。
        //
        // ⚠️ 判斷依據是 flags 的 FLAG_GROUP_SUMMARY，不是 extras。framework 從不寫
        // "android.isGroupSummary" 這個 extras key（它只是 Notification.Builder.setGroupSummary
        // 的參數名），舊寫法恆為 false，這整個分支自 vc18 引入起從未執行過。
        // 2026-07-26 Nothing A059P dumpsys 實證：全機通知的 extras 中該 key 出現 0 次，
        // 而確實帶 GROUP_SUMMARY flag 的通知有 2 個。
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            scheduleLineSummaryCancellation(sbn)
            return
        }

        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: return
        val subText = extras.getCharSequence("android.subText")?.toString()

        // Android 15+ 可能在通知交給第三方 listener 前，就把敏感內容換成 framework 占位字串。
        // 原文此時已無法復原；保留原始 LINE 通知給 SystemUI，不要重發占位字、建假聊天室或取消它。
        val sourceAppLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
        // 圖片比對有成本，只在 title/subText 已經長得像 AOSP clone 時才做。
        // 條件必須向 classifier 借，不可在這裡重寫一份（見 matchesAospCloneShapeExceptIcon 的說明）。
        val cloneShapeCandidate = NotificationClassifier.matchesAospCloneShapeExceptIcon(
            title = title,
            subText = subText,
            sourceAppLabel = sourceAppLabel,
        )
        if (NotificationClassifier.isSystemRedactedNotification(
                title = title,
                text = text,
                subText = subText,
                sourceAppLabel = sourceAppLabel,
                systemRedactedText = systemRedactedText(),
                largeIconMatchesAppIcon = cloneShapeCandidate && largeIconMatchesAppIcon(sbn),
            )
        ) {
            Log.w(TAG, "系統已遮蔽敏感通知；保留原始通知且不建立 Notify+ 副本")
            return
        }

        // 過濾 LINE 的堆疊摘要通知（title 含逗號+冒號）；與 group summary 同樣接管
        if (NotificationClassifier.isStackSummaryTitle(title)) {
            scheduleLineSummaryCancellation(sbn)
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
        // LINE 26.11.0 群組 tagged callback 的 title 被組成「群組名：發送者」（見 senderOf）；
        // 這裡只還原「訊息發送者顯示名」，讓它與 legacy mirror 的乾淨 title 一致（嚴格合併才配得上）。
        // roomKey/chatTitle 推導不動——那些拿 title 當聊天室名素材，是另一個語意。
        val sender: String = NotificationClassifier.senderOf(title, subText)
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
        recentPostedPayloads.entries.removeAll { mirrorNow - it.value.seenElapsed > 3000L }
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

        // 第 2 層保底去重：吸收未來任何欄位漂移導致嚴格 mirror 合併失配的重複 callback。
        // 嚴格合併靠 shortcutId/groupKey/channelId 等欄位兩邊全等；LINE 改版動到任一欄位就失效
        // （26.11.0 群組 title 汙染就是這樣繞過第 1 層前的合併）。這裡改用最小充分集合辨識「同一則」：
        // 房間 + 全文 + 毫秒 when 三者全等。
        // 辨真偽原理：真的連續傳兩則相同文字，LINE 的 when 毫秒時間戳必不同（2026-07-21 實測差 2449ms）；
        // 同一則的鏡像 callback when 完全相同。毫秒＋全文＋房間三重相等才吸收，寧可重複不可漏訊息。
        // 僅在 when > 0 才啟用（傳給 dedupeFingerprint 的第三參數是毫秒 when）。
        val dedupeWhen = notification.`when`
        if (dedupeWhen > 0L) {
            val dedupeKey = NotificationClassifier.dedupeFingerprint(roomKey, text, dedupeWhen)
            val postedEntry = recentPostedPayloads[dedupeKey]
            if (postedEntry != null &&
                mirrorNow - postedEntry.seenElapsed <= 3000L &&
                postedEntry.stateEpoch == notificationStateEpoch
            ) {
                Log.d(TAG, "保底去重：吸收同室同文同時間戳的重複 callback")
                if (prefs.getBoolean(KEY_REPLACE_ORIGINAL, true)) {
                    // 照嚴格合併分支（scheduleOriginalCancellation 呼叫）的 fail-open 精神傳參：
                    // expectedSource/expectedMirrorSignature 在欄位漂移情境拿不到可靠值（本來就是
                    // 因為它們變了才走到這），函式允許 null → 省略，退回「只驗我方副本仍在場」的最保守取消。
                    scheduleOriginalCancellation(
                        key = sbn.key,
                        receipt = postedEntry.receipt,
                        requireExactGeneration = true,
                        requiredStateEpoch = notificationStateEpoch,
                    )
                }
                return
            }
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

        // 從 LINE 的 MessagingStyle 通知取出「本人」頭貼與名稱，按帳號(profile)分別快取。
        //
        // ⚠️ 平台欄位要自己讀，不能只靠 NotificationCompat。androidx.core 1.15.0 的
        // MessagingStyle.restoreFromCompatExtras 只讀 compat 專屬的 "android.messagingStyleUser"，
        // 沒讀平台 API 28+ 寫的 Notification.EXTRA_MESSAGING_PERSON("android.messagingUser")
        // ——androidx 要到 1.19.0 才補上。而平台的 MessagingStyle.addExtras() 是把名字寫進
        // EXTRA_SELF_DISPLAY_NAME、把含 icon 的 Person 寫進 EXTRA_MESSAGING_PERSON。
        // 所以 LINE 只要是用平台 Notification.MessagingStyle 建通知，頭貼就會在這一步被靜靜丟掉，
        // 只剩名字活下來。實機症狀正是「帳號名稱抓得到，但本人頭貼永遠是預設圖」。
        try {
            val compatUser = NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)?.user
            val selfIcon = compatUser?.icon ?: platformSelfIcon(extras)
            if (selfIcon != null && !selfPersonIcons.containsKey(profileKey)) {
                selfPersonIcons[profileKey] = selfIcon
                persistSelfAvatar(profileKey, selfIcon)
                Log.d(TAG, "✓ 取得帳號[$profileKey]本人頭貼")
            }
            val selfName = compatUser?.name?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString()
            selfName?.takeIf { it.isNotBlank() }?.let { accountLabels[profileKey] = it }
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

        // 保存 LINE 的 contentIntent 和回覆 action（在取消通知前）。
        // LINE 26.11.0 起，合併失敗時 mirror（tag=null, id=16880000）會晚到，若照舊 last-writer-wins
        // 覆蓋，會把 tagged child 原本指向特定聊天室的跳轉蓋掉，變成點通知只能開 LINE 主畫面
        // （vivo V50 用戶回報）。mirror 的 intent 是彙總體，只准在還沒有值時補上，不准覆蓋既有值。
        if (notification.contentIntent != null &&
            (room.contentIntent == null ||
                !NotificationClassifier.isLegacyMirrorIdentity(sbn.tag, sbn.id))
        ) {
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
        } else {
            if (mirrorSignatureToStore != null && currentSourceVariant != null) {
                recentMirroredVariants[mirrorSignatureToStore] = MirroredVariantEntry(
                    source = currentSourceVariant,
                    receipt = replacementReceipt,
                    stateEpoch = notificationStateEpoch,
                )
            }
            // 第 2 層保底去重的寫入：為每一則成功貼出的訊息（不限 primary variant）記一筆，
            // 指紋 = 房間 + 全文 + 毫秒 when。when <= 0 不記（無時間戳無法辨真偽）。
            val dedupeWhen = notification.`when`
            if (dedupeWhen > 0L) {
                val dedupeKey = NotificationClassifier.dedupeFingerprint(roomKey, text, dedupeWhen)
                recentPostedPayloads[dedupeKey] = RecentPostedEntry(
                    receipt = replacementReceipt,
                    stateEpoch = notificationStateEpoch,
                    seenElapsed = mirrorNow,
                )
            }
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
        if (!prefs.getBoolean(KEY_CLEAR_AFTER_READ, true)) return

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

    /**
     * 讀平台 API 28+ 的 [Notification.EXTRA_MESSAGING_PERSON]，補 androidx.core 1.15.0
     * 漏讀的那條路（見 onNotificationPosted 內的說明）。
     *
     * 轉成 Bitmap 而不是直接包 [android.graphics.drawable.Icon]：Icon 可能是 URI 型，
     * 那種我方無權轉貼；先 loadDrawable 再畫成點陣圖，順便讓它可以存檔持久化。
     */
    private fun platformSelfIcon(extras: Bundle): IconCompat? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            @Suppress("DEPRECATION")
            val person = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON)
                as? android.app.Person
            val drawable = person?.icon?.loadDrawable(this) ?: return null
            val bitmap = rasterizeIcon(drawable, SELF_AVATAR_SIZE) ?: return null
            IconCompat.createWithBitmap(bitmap)
        }.getOrNull()
    }

    /** 本人頭貼的存檔位置，一個 profile 一張。 */
    private fun selfAvatarFile(profileKey: String): java.io.File =
        java.io.File(java.io.File(filesDir, SELF_AVATAR_DIR), "${profileKey.hashCode()}.png")

    /**
     * 把本人頭貼寫到檔案。
     *
     * 沒有這一步，[selfPersonIcons] 就只是純記憶體快取：NotificationListenerService 被系統
     * 回收重綁之後歸零，而補回來的唯一時機是「下一則帶得出本人頭貼的 LINE 通知」，
     * 於是重開機後的第一次回覆必然是預設圖。
     */
    private fun persistSelfAvatar(profileKey: String, icon: IconCompat) {
        runCatching {
            val bitmap = rasterizeIcon(icon.loadDrawable(this) ?: return, SELF_AVATAR_SIZE)
                ?: return
            val file = selfAvatarFile(profileKey)
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bitmap.recycle()
        }.onFailure { Log.w(TAG, "存本人頭貼失敗 profile=$profileKey", it) }
    }

    /** 從存檔還原本人頭貼；listener 重連時呼叫，讓 process 重啟不會退回預設圖。 */
    private fun restorePersistedSelfAvatars() {
        val dir = java.io.File(filesDir, SELF_AVATAR_DIR)
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (file in files) {
            val profileKey = knownProfiles.firstOrNull { "${it.hashCode()}.png" == file.name }
                ?: continue
            if (selfPersonIcons.containsKey(profileKey)) continue
            runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }
                .getOrNull()
                ?.let { selfPersonIcons[profileKey] = IconCompat.createWithBitmap(it) }
        }
    }

    /** 該帳號的本人頭貼（取不到用綠色預設） */
    private fun selfIconFor(room: ChatRoom): IconCompat =
        selfPersonIcons[room.profileKey]
            ?: restoreSelfAvatarFromDisk(room.profileKey)
            ?: IconCompat.createWithResource(this, R.drawable.ic_self_avatar)

    /**
     * 單一 profile 的懶載入。[restorePersistedSelfAvatars] 只在重連時掃一次，而
     * [knownProfiles] 要等第一則通知才會有內容，兩者先後不保證，這裡補一條直接命中的路。
     */
    private fun restoreSelfAvatarFromDisk(profileKey: String): IconCompat? {
        val file = selfAvatarFile(profileKey)
        if (!file.exists()) return null
        return runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()
            ?.let { IconCompat.createWithBitmap(it) }
            ?.also { selfPersonIcons[profileKey] = it }
    }

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
