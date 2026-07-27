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

    /** legacy mirror 的通知身分（tag=null 且固定 id）。 */
    fun isLegacyMirrorIdentity(tag: String?, id: Int): Boolean =
        tag == null && id == 16_880_000

    fun isObservedLineLegacyMirror(source: MirrorSource): Boolean =
        isLegacyMirrorIdentity(source.tag, source.id)

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

    /** LINE 的「社群活動」頻道，承載社群公告與記事本重要貼文。 */
    fun isSquareActivityChannel(channelId: String?): Boolean =
        channelId == "SquareActivity" ||
            channelId?.endsWith(".notification.SquareActivity") == true

    /**
     * 靜音可以攔截的頻道，刻意比 [isSupportedMessageChannel] 寬。
     *
     * 兩者不是同一件事：[isSupportedMessageChannel] 決定「要不要把它重建成 Notify+ 卡片」，
     * 這裡決定「使用者說要靜音時，要不要幫他撤掉」。公告類通知沒有 MessagingStyle、沒有
     * subText，重建成卡片只會生出假聊天室，但撤掉它沒有任何副作用——所以社群活動頻道
     * 只進這個集合，不進白名單。
     */
    fun isMuteEligibleChannel(channelId: String?): Boolean =
        isSupportedMessageChannel(channelId) || isSquareActivityChannel(channelId)

    /**
     * 這則通知能不能被歸屬到單一聊天室。歸屬不明就不該套用靜音，否則會誤撤別人的通知。
     *
     * 兩種不可歸屬的形狀：系統遮蔽 clone（title 是 App 名稱，跟聊天室無關）、
     * 以及沒有 subText 的堆疊摘要（title 本身就是多個聊天室拼起來的）。
     */
    fun isAttributableChatTitle(
        title: String?,
        subText: String?,
        sourceAppLabel: String?,
    ): Boolean =
        title != null &&
            !matchesAospCloneShapeExceptIcon(title, subText, sourceAppLabel) &&
            !(subText == null && isStackSummaryTitle(title))

    /**
     * 這則該不該因為「使用者把該聊天室完全靜音」而直接撤掉。
     *
     * 存在的理由是守門順序：靜音檢查原本排在頻道白名單、GROUP_SUMMARY、title/text 空值、
     * 系統遮蔽這四道早退**之後**，而那四道 return 掉的通知照樣會出現在使用者面前，
     * 等於靜音對它們一律失效——這就是「我明明關掉了，@all 和社群公告還是會跳」的成因。
     *
     * 遮蔽通知刻意排除：原始內容在 callback 前就已不可逆遺失，撤掉它等於讓使用者永遠
     * 不知道有這則訊息。group summary 也排除，它涵蓋多個聊天室、不可歸屬到單一房。
     */
    fun shouldHardMute(
        channelId: String?,
        title: String?,
        subText: String?,
        text: String?,
        isGroupSummary: Boolean,
        sourceAppLabel: String?,
        systemRedactedText: String?,
        mutedChats: Set<String>,
    ): Boolean {
        if (title == null) return false
        if (!isMuteEligibleChannel(channelId)) return false
        if (isGroupSummary) return false
        if (chatTitleOf(title, subText) !in mutedChats) return false
        if (textMatchesRedactionPlaceholder(text, systemRedactedText)) return false
        return isAttributableChatTitle(title, subText, sourceAppLabel)
    }

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

    /**
     * 從通知 title 還原「乾淨的發送者顯示名」。
     *
     * LINE 26.11.0 對群組訊息的 tagged conversation callback，會把 android.title 組成
     * 「群組名：發送者」（全形冒號），android.subText = 群組名；同一則訊息的 legacy mirror
     * callback 則帶乾淨 title = 純發送者名。實測（2026-07-21，Nothing A065 + LINE 26.11.0
     * dumpsys --noredact 多對樣本）：tagged title="寶貝兒子：Christina王秀華" / subText="寶貝兒子"，
     * mirror title="Christina王秀華" / subText="寶貝兒子"。兩邊 sender 不一致會害
     * [mirrorFingerprint] 兩側算出不同指紋、合併失敗，同一則群組訊息在對話串卡片跳兩列。
     *
     * 這裡把 tagged 那側的「subText：」前綴剝掉，讓兩邊 sender 一致。
     * **只剝全形冒號「：」**——實機證據只看到全形；半形冒號不在證據範圍內，不動，避免誤剝
     * 正常含半形冒號的暱稱。長度守門確保剝完非空（避免整個 title 恰為「群組名：」時剝成空字串）。
     * 1:1 個人訊息兩邊 title 一致、subText 為 null 或不成前綴，一律原樣回傳 title。
     * 純字串操作（startsWith/substring），不用正則，subText 含正則特殊字元也安全。
     */
    fun senderOf(title: String, subText: String?): String {
        val prefix = "$subText："
        if (subText != null &&
            title != subText &&
            title.startsWith(prefix) &&
            title.length > prefix.length
        ) {
            return title.substring(prefix.length)
        }
        return title
    }

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
     * LINE 對同一則訊息會送兩個 callback（tagged conversation + legacy mirror `id=16880000`），
     * 這個指紋用來把它們配成一對、只留一張卡。
     *
     * ⛔ **參數列就是白名單。** 想加欄位，必須先在實機證明它在 tagged 與 legacy mirror 兩邊
     * 一模一樣，否則指紋兩邊算出來不同、配對永遠失敗、使用者就會看到同一則訊息跳兩張卡。
     * 這裡刻意做成純函式而不是直接吃 `StatusBarNotification`：**傳不進 `PendingIntent`、
     * 傳不進 `MessagingStyle`**，型別層面就擋掉舊的失效模式。
     *
     * 實機實測後排除的欄位（2026-07-15，LINE 26.10.1 / Android 16 / Nothing A059P）：
     * - `contentIntent.hashCode()`：兩邊必然不同（實測 250458503 vs 131309470，且是兩者
     *   唯一不同的欄位）。舊版把它算進指紋，導致合併**從未生效過**——每則訊息跳兩張卡，
     *   群組甚至四張。
     * - `MessagingStyle` 內容：legacy mirror **有時整個抽不出來**，強行納入只會退回 null、
     *   放棄合併。
     * - `sbn.key` / `sbn.id` / `sbn.tag`：兩個通知本來就是不同身分。
     *
     * `whenMs` 是訊息的毫秒時間戳，配上 roomKey 與 text 已足以唯一識別一則訊息。誤配對由呼叫端
     * 的形狀護欄擋掉（必須 tagged 先到、legacy 後到）：真正的下一則新訊息以 tagged 身分抵達，
     * 不會被當成 mirror 吃掉。
     */
    fun mirrorFingerprint(
        roomKey: String,
        sender: String,
        text: String,
        packageName: String,
        sourceUid: Int,
        shortcutId: String,
        groupKey: String?,
        channelId: String?,
        whenMs: Long,
    ): String {
        val canonical = StringBuilder()
        fun field(value: String?) {
            if (value == null) canonical.append("-1:")
            else canonical.append(value.length).append(':').append(value)
            canonical.append('|')
        }
        field(roomKey)
        field(sender)
        field(text)
        field(packageName)
        canonical.append(sourceUid).append('|')
        field(shortcutId)
        field(groupKey)
        field(channelId)
        canonical.append(whenMs).append('|')
        return dedupeFingerprint(roomKey, canonical.toString(), 0L)
    }

    /**
     * Android 15+ 交給不受信任 NotificationListener 的敏感通知 clone：內容會被換成
     * framework 的 redacted 占位字串。命中後不能重發、建立假聊天室或取消原通知，
     * 因為原始內容在 callback 前就已不可逆地被移除。
     *
     * **兩條線 OR，因為 OEM 的 clone 形狀分兩種，各自只斷得掉其中一條：**
     *
     * 1. **占位字比對**（[textMatchesRedactionPlaceholder]）。realme UI 7 的 clone 保留原
     *    title/subText（2026-07-18 用戶實證），只有 text 可辨識。
     * 2. **AOSP clone 形狀**（[matchesAospCloneShape]）。Nothing OS 走標準路徑：title 換成
     *    App label、subText 移除、largeIcon 換成 App 圖示，但 text 對不上占位字。
     *    2026-07-12 Nothing A059P 實證：一則「蝦皮店到店包裹通知」在同一毫秒
     *    （chat_last_active 兩筆皆 1783826523374）多生出一個名為「LINE」的聊天室，
     *    其頭貼檔 chat_avatars/2336756.png 是 LINE 的 App 圖示。
     *
     * 歷史教訓：vc18 的 4acd58f 為了修好第 1 種，把第 2 種的判斷條件整條刪掉，於是
     * Nothing 這類機器從「偶爾漏判」變成「必然漏判」。兩種形狀要各留一條線，不能二選一。
     *
     * 誤判成本刻意壓在安全側：把正常通知誤當 clone，後果只是該則不增強、保留 LINE 原通知
     * （fail-open）；反過來漏判則會建出假聊天室，還在取代模式下把真訊息的原通知一起取消。
     */
    fun isSystemRedactedNotification(
        title: String,
        text: String,
        subText: String?,
        sourceAppLabel: String?,
        systemRedactedText: String?,
        largeIconMatchesAppIcon: Boolean = false,
    ): Boolean =
        textMatchesRedactionPlaceholder(text, systemRedactedText) ||
            matchesAospCloneShape(title, subText, sourceAppLabel, largeIconMatchesAppIcon)

    /**
     * AOSP redaction clone 的形狀：title 等於來源 App 名稱、沒有 subText、
     * largeIcon 就是該 App 的圖示。
     *
     * 三個條件缺一不可。只靠前兩個會誤殺「聊天室名剛好等於 App 名稱」的 1:1 對話
     * （例如 LINE 官方帳號本身就叫「LINE」）——那種通知帶的是帳號頭像而非 App 圖示，
     * 由 [largeIconMatchesAppIcon] 分開。
     */
    fun matchesAospCloneShape(
        title: String,
        subText: String?,
        sourceAppLabel: String?,
        largeIconMatchesAppIcon: Boolean,
    ): Boolean =
        matchesAospCloneShapeExceptIcon(title, subText, sourceAppLabel) &&
            largeIconMatchesAppIcon

    /**
     * clone 形狀裡不需要動到圖片的那幾個條件。
     *
     * 存在的唯一理由是給呼叫端當「要不要花成本做圖示比對」的閘門，**不可以在呼叫端自己重寫一份**。
     * 兩處各自維護同一組條件，正是 vc18 那次回歸的病根：改一邊忘另一邊，
     * [matchesAospCloneShape] 就會靜默失效而測試照樣全綠。
     */
    fun matchesAospCloneShapeExceptIcon(
        title: String,
        subText: String?,
        sourceAppLabel: String?,
    ): Boolean =
        !sourceAppLabel.isNullOrEmpty() &&
            title == sourceAppLabel &&
            subText == null

    /**
     * LINE 26.11.0 起，`id=16880000 tag=null` 從 legacy mirror 變成 `GROUP_SUMMARY`
     * （2026-07-19 於 Nothing A065 dumpsys 實證，flags=...|GROUP_SUMMARY）。
     * summary 不會被 SystemUI 自動回收，放著不管會在部分 OEM（realme UI 實證）
     * 以「N則新訊息＋訊息預覽」的完整卡片殘留，看起來就像「原通知沒被取代」。
     *
     * 取消條件：取代模式開啟，且內容已由別的通知承載（我方副本或 LINE child 任一在場）。
     * 兩者皆不在場代表 summary 可能是唯一殘留 → fail-open 保留。
     */
    fun shouldCancelLineSummary(
        replaceEnabled: Boolean,
        replacementActive: Boolean,
        lineChildActive: Boolean,
    ): Boolean = replaceEnabled && (replacementActive || lineChildActive)

    /**
     * text 是否恰為系統遮蔽占位字。兩處守門共用：
     * (1) GROUP_SUMMARY 分支排在 title/text 空值檢查之前（summary 不保證帶 android.text），
     *     走不到 [isSystemRedactedNotification]——遮蔽版 summary 以此保留、不取消；
     * (2) 取消原通知前的最後重讀：同 key 在延遲窗內被更新成遮蔽版時放手不取消（TOCTOU 窄化）。
     * null text 不算遮蔽。
     */
    fun textMatchesRedactionPlaceholder(text: String?, systemRedactedText: String?): Boolean =
        text != null &&
            !systemRedactedText.isNullOrEmpty() &&
            text == systemRedactedText

    /**
     * roomKey 格式 = profileKey + [KEY_SEP] + chatTitle（雙開帳號以 profileKey 區分）。
     * summary 的取消只能由「同 profile」的承載者背書；null roomKey（例如我們自己的
     * Aggregate 聚合卡）不能算。KEY_SEP 一併比對，避免 profileKey 前綴撞名。
     */
    fun roomKeyBelongsToProfile(roomKey: String?, profileKey: String): Boolean =
        roomKey != null && roomKey.startsWith(profileKey + KEY_SEP)

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
