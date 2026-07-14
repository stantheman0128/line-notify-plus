package com.stanslab.linenotify.service

import java.security.MessageDigest

/**
 * 通知分類與分組的純邏輯，抽離自 [LineNotificationListener]，不依賴 Android framework，
 * 可直接在 JVM 上做單元測試。
 *
 * [LineNotificationListener] 的正式通知管線也必須呼叫這裡，避免 production 與測試各維護一份規則。
 */
object NotificationClassifier {

    const val TYPE_COMMUNITY = "community"
    const val TYPE_GROUP = "group"
    const val TYPE_PERSONAL = "personal"
    const val MODE_ENHANCED = "enhanced"
    const val MODE_LEGACY_ORIGINAL_ONLY = "legacy_original_only"
    const val MODE_MUTED = "muted"

    const val PREFS_KNOWN_COMMUNITIES = "known_communities"
    const val PREFS_KNOWN_GROUPS = "known_groups"
    const val PREFS_KNOWN_CHATS = "known_chats"
    const val PREFS_FORCED_COMMUNITIES = "forced_communities"
    const val PREFS_FORCED_GROUPS = "forced_groups"
    const val PREFS_FORCED_CHATS = "forced_chats"

    /** roomKey 分隔字元，對齊 listener 的 KEY_SEP。 */
    const val KEY_SEP = ":"

    /** LINE 26.10.1 / Nothing OS 實機觀察到的雙通知來源結構（不含訊息內容）。 */
    data class MirrorSource(
        val key: String,
        val id: Int,
        val tag: String?,
        val shortcutId: String,
        val postTime: Long,
        val seenElapsed: Long,
    )

    fun isObservedLineConversationPrimary(source: MirrorSource): Boolean =
        source.tag == "NOTIFICATION_TAG_MESSAGE" &&
            source.id == source.shortcutId.hashCode()

    fun isObservedLineLegacyMirror(source: MirrorSource): Boolean =
        source.tag == null && source.id == 16_880_000

    /**
     * 僅接受實機觀察到的方向：較完整的 tagged conversation 先到，固定 ID mirror 後到。
     * 未知 OEM／LINE 版本或反序一律不合併，寧可重複也不漏訊息／回覆 action。
     */
    fun isObservedLineMirrorPair(first: MirrorSource, second: MirrorSource): Boolean {
        if (first.key == second.key || first.shortcutId != second.shortcutId) return false
        val elapsedDelta = second.seenElapsed - first.seenElapsed
        if (elapsedDelta !in 0L..500L) return false
        if (kotlin.math.abs(second.postTime - first.postTime) > 100L) return false
        return isObservedLineConversationPrimary(first) && isObservedLineLegacyMirror(second)
    }

    /**
     * 判斷聊天類型：個人 / 群組 / 社群。
     *
     * `line.square.notification=true` 是社群的正向證據，但私有 extra 可能因通知種類、
     * LINE 版本或系統 redaction 而缺席。已確認是社群的聊天室採 sticky upgrade：缺少旗標
     * 不構成反證，不會再被降回群組。使用者手動分類的 [overrideType] 優先度最高。
     */
    fun classifyChatType(
        isSquare: Boolean,
        subText: String?,
        previousType: String? = null,
        overrideType: String? = null,
    ): String = when {
        overrideType in validTypes -> overrideType!!
        isSquare -> TYPE_COMMUNITY
        previousType == TYPE_COMMUNITY -> TYPE_COMMUNITY
        subText != null -> TYPE_GROUP
        else -> TYPE_PERSONAL
    }

    private val validTypes = setOf(TYPE_COMMUNITY, TYPE_GROUP, TYPE_PERSONAL)

    /** 除了個人聊天以外都算「群組型」對話（社群也算），對齊 listener 的 isGroup。 */
    fun isGroupType(chatType: String): Boolean = chatType != TYPE_PERSONAL

    /**
     * LINE 的堆疊摘要通知：title 同時含全形冒號「：」與半形逗號「,」
     * （例：「A：訊息, B：訊息」）。命中就該濾掉，不進聊天室清單。
     */
    fun isStackSummaryTitle(title: String): Boolean =
        title.contains("：") && title.contains(",")

    /** 僅攔截 LINE 聊天訊息頻道；付款、好友邀請、動態等其他頻道一律 fail-open。 */
    fun isSupportedMessageChannel(channelId: String?): Boolean =
        channelId == "NewMessages" || channelId?.endsWith(".notification.NewMessages") == true

    /** 分類 → 對應的 SharedPreferences set key。 */
    fun prefsKeyForType(chatType: String): String = when (chatType) {
        TYPE_COMMUNITY -> PREFS_KNOWN_COMMUNITIES
        TYPE_GROUP -> PREFS_KNOWN_GROUPS
        else -> PREFS_KNOWN_CHATS
    }

    /** 使用者手動分類 → 對應的 SharedPreferences set key。 */
    fun forcedPrefsKeyForType(chatType: String): String = when (chatType) {
        TYPE_COMMUNITY -> PREFS_FORCED_COMMUNITIES
        TYPE_GROUP -> PREFS_FORCED_GROUPS
        else -> PREFS_FORCED_CHATS
    }

    /** 從三個 known set 取得目前分類；社群優先，避免舊資料重複時被降級。 */
    fun knownTypeOf(current: Map<String, Set<String>>, chatTitle: String): String? = when {
        chatTitle in current[PREFS_KNOWN_COMMUNITIES].orEmpty() -> TYPE_COMMUNITY
        chatTitle in current[PREFS_KNOWN_GROUPS].orEmpty() -> TYPE_GROUP
        chatTitle in current[PREFS_KNOWN_CHATS].orEmpty() -> TYPE_PERSONAL
        else -> null
    }

    /** 從三個 forced set 取得使用者手動分類。 */
    fun forcedTypeOf(current: Map<String, Set<String>>, chatTitle: String): String? = when {
        chatTitle in current[PREFS_FORCED_COMMUNITIES].orEmpty() -> TYPE_COMMUNITY
        chatTitle in current[PREFS_FORCED_GROUPS].orEmpty() -> TYPE_GROUP
        chatTitle in current[PREFS_FORCED_CHATS].orEmpty() -> TYPE_PERSONAL
        else -> null
    }

    /** 顯示用聊天室名：有 subText 用 subText(群組/社群名)，否則用 title(發送者)。 */
    fun chatTitleOf(title: String, subText: String?): String = subText ?: title

    /** roomKey = profileKey + 分隔字元 + 聊天室名（雙開帳號各自獨立）。 */
    fun roomKeyOf(profileKey: String, chatTitle: String): String =
        profileKey + KEY_SEP + chatTitle

    /** 個別聊天室開關的正式語意：關閉就是完全靜音，不受全域「取代原始通知」影響。 */
    fun shouldMuteChat(chatTitle: String, mutedChats: Set<String>): Boolean =
        chatTitle in mutedChats

    fun notificationModeOf(
        chatTitle: String,
        legacyOriginalOnlyChats: Set<String>,
        mutedChats: Set<String>,
    ): String = when {
        chatTitle in mutedChats -> MODE_MUTED
        chatTitle in legacyOriginalOnlyChats -> MODE_LEGACY_ORIGINAL_ONLY
        else -> MODE_ENHANCED
    }

    data class NotificationPreferenceSets(
        val legacyOriginalOnly: Set<String>,
        val muted: Set<String>,
    )

    /** 新版明確切換：開啟會移除兩種停用狀態；關閉會從 legacy 原始模式移到完全靜音。 */
    fun updateNotificationPreferenceSets(
        chatTitle: String,
        enabled: Boolean,
        legacyOriginalOnlyChats: Set<String>,
        mutedChats: Set<String>,
    ): NotificationPreferenceSets {
        val legacy = legacyOriginalOnlyChats.toMutableSet().apply { remove(chatTitle) }
        val muted = mutedChats.toMutableSet().apply {
            if (enabled) remove(chatTitle) else add(chatTitle)
        }
        return NotificationPreferenceSets(legacy, muted)
    }

    /** 去重只保留不可逆 fingerprint，避免把完整訊息文字額外留在 dedupe cache。 */
    fun dedupeFingerprint(roomKey: String, text: String, timeSeconds: Long): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$roomKey\u0000$text\u0000$timeSeconds".toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /**
     * Android 15+ 交給不受信任 NotificationListener 的敏感通知 clone：title 會改成來源
     * App label、text 會改成 framework 的 redacted 字串，subText 被移除。命中後不能重發、
     * 建立假聊天室或取消原通知，因為原始內容在 callback 前就已不可逆地被移除。
     */
    fun isSystemRedactedNotification(
        title: String,
        text: String,
        subText: String?,
        sourceAppLabel: String?,
        systemRedactedText: String?,
    ): Boolean =
        !systemRedactedText.isNullOrEmpty() &&
            text == systemRedactedText &&
            subText == null &&
            !sourceAppLabel.isNullOrEmpty() &&
            title == sourceAppLabel

    /**
     * 一個聊天室只該屬於一個分類。把它放進 [chatType] 對應的 set，並從其他兩個 set 移除，
     * 自動修正過去誤分類的殘留（例如社群曾被當群組存進 known_groups）。
     *
     * [current] 是三個 set 的現況（key = PREFS_KNOWN_*）。回傳更新後的三個 set。
     * 這是 [LineNotificationListener.saveKnownChat] 的純資料版本。
     */
    fun reclassify(
        current: Map<String, Set<String>>,
        chatTitle: String,
        chatType: String,
    ): Map<String, Set<String>> {
        val target = prefsKeyForType(chatType)
        val allKeys = listOf(PREFS_KNOWN_COMMUNITIES, PREFS_KNOWN_GROUPS, PREFS_KNOWN_CHATS)
        return allKeys.associateWith { key ->
            val set = current[key].orEmpty().toMutableSet()
            if (key == target) set.add(chatTitle) else set.remove(chatTitle)
            set.toSet()
        }
    }
}
