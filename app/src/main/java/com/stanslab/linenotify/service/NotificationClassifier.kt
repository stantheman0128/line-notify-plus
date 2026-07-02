package com.stanslab.linenotify.service

/**
 * 通知分類與分組的純邏輯，抽離自 [LineNotificationListener]，不依賴 Android framework，
 * 可直接在 JVM 上做單元測試。
 *
 * 目前 [LineNotificationListener] 仍是內嵌各自的判斷（歷史原因），這裡是那些判斷的
 * 標準參考實作，行為對齊。之後把 listener 改成呼叫本物件即可（見測試檔說明的「可測化重構」）。
 */
object NotificationClassifier {

    const val TYPE_COMMUNITY = "community"
    const val TYPE_GROUP = "group"
    const val TYPE_PERSONAL = "personal"

    const val PREFS_KNOWN_COMMUNITIES = "known_communities"
    const val PREFS_KNOWN_GROUPS = "known_groups"
    const val PREFS_KNOWN_CHATS = "known_chats"

    /** roomKey 分隔字元，對齊 listener 的 KEY_SEP。 */
    const val KEY_SEP = ":"

    /**
     * 判斷聊天類型：個人 / 群組 / 社群。
     *
     * 社群(LINE OpenChat / Square)唯一可靠標記是私有 extra `line.square.notification=true`；
     * 群組帶 subText(群組名)、個人沒有 subText。優先序：社群 > 群組 > 個人。
     * 這是 2026-06-30 commit 09088de 修社群誤判的核心判斷。
     */
    fun classifyChatType(isSquare: Boolean, subText: String?): String = when {
        isSquare -> TYPE_COMMUNITY
        subText != null -> TYPE_GROUP
        else -> TYPE_PERSONAL
    }

    /** 除了個人聊天以外都算「群組型」對話（社群也算），對齊 listener 的 isGroup。 */
    fun isGroupType(chatType: String): Boolean = chatType != TYPE_PERSONAL

    /**
     * LINE 的堆疊摘要通知：title 同時含全形冒號「：」與半形逗號「,」
     * （例：「A：訊息, B：訊息」）。命中就該濾掉，不進聊天室清單。
     */
    fun isStackSummaryTitle(title: String): Boolean =
        title.contains("：") && title.contains(",")

    /** 分類 → 對應的 SharedPreferences set key。 */
    fun prefsKeyForType(chatType: String): String = when (chatType) {
        TYPE_COMMUNITY -> PREFS_KNOWN_COMMUNITIES
        TYPE_GROUP -> PREFS_KNOWN_GROUPS
        else -> PREFS_KNOWN_CHATS
    }

    /** 顯示用聊天室名：有 subText 用 subText(群組/社群名)，否則用 title(發送者)。 */
    fun chatTitleOf(title: String, subText: String?): String = subText ?: title

    /** roomKey = profileKey + 分隔字元 + 聊天室名（雙開帳號各自獨立）。 */
    fun roomKeyOf(profileKey: String, chatTitle: String): String =
        profileKey + KEY_SEP + chatTitle

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
