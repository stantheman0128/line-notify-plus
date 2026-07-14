package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 純 JVM 單元測試，覆蓋通知分類/分組的核心判斷。
 * 重點在社群(square)判定 — 歷史上 09088de 修過的誤判 bug。
 */
class NotificationClassifierTest {

    // ---- classifyChatType：社群 > 群組 > 個人 ----

    @Test
    fun square_flag_wins_even_with_subText() {
        // 社群訊息也帶 subText(社群名)，但 square 旗標優先，不可被判成群組。
        // 這是 09088de 的回歸點：舊版靠 subText/shortcutId → 社群恆被誤判成群組。
        assertEquals(
            NotificationClassifier.TYPE_COMMUNITY,
            NotificationClassifier.classifyChatType(isSquare = true, subText = "某社群"),
        )
    }

    @Test
    fun square_flag_wins_even_without_subText() {
        assertEquals(
            NotificationClassifier.TYPE_COMMUNITY,
            NotificationClassifier.classifyChatType(isSquare = true, subText = null),
        )
    }

    @Test
    fun subText_present_without_square_is_group() {
        assertEquals(
            NotificationClassifier.TYPE_GROUP,
            NotificationClassifier.classifyChatType(isSquare = false, subText = "愛如潮水"),
        )
    }

    @Test
    fun no_subText_no_square_is_personal() {
        assertEquals(
            NotificationClassifier.TYPE_PERSONAL,
            NotificationClassifier.classifyChatType(isSquare = false, subText = null),
        )
    }

    @Test
    fun empty_subText_is_group_not_personal() {
        // subText != null 才判群組；空字串仍是 non-null，對齊 listener（subText != null）。
        assertEquals(
            NotificationClassifier.TYPE_GROUP,
            NotificationClassifier.classifyChatType(isSquare = false, subText = ""),
        )
    }

    @Test
    fun confirmed_community_is_sticky_when_square_extra_is_missing() {
        assertEquals(
            NotificationClassifier.TYPE_COMMUNITY,
            NotificationClassifier.classifyChatType(
                isSquare = false,
                subText = "已確認社群",
                previousType = NotificationClassifier.TYPE_COMMUNITY,
            ),
        )
    }

    @Test
    fun manual_override_wins_over_private_square_signal() {
        assertEquals(
            NotificationClassifier.TYPE_GROUP,
            NotificationClassifier.classifyChatType(
                isSquare = true,
                subText = "同名聊天室",
                previousType = NotificationClassifier.TYPE_COMMUNITY,
                overrideType = NotificationClassifier.TYPE_GROUP,
            ),
        )
    }

    @Test
    fun invalid_manual_override_is_ignored() {
        assertEquals(
            NotificationClassifier.TYPE_GROUP,
            NotificationClassifier.classifyChatType(
                isSquare = false,
                subText = "群組",
                overrideType = "invalid",
            ),
        )
    }

    // ---- isGroupType ----

    @Test
    fun community_and_group_are_group_type_personal_is_not() {
        assertTrue(NotificationClassifier.isGroupType(NotificationClassifier.TYPE_COMMUNITY))
        assertTrue(NotificationClassifier.isGroupType(NotificationClassifier.TYPE_GROUP))
        assertFalse(NotificationClassifier.isGroupType(NotificationClassifier.TYPE_PERSONAL))
    }

    // ---- isStackSummaryTitle：全形冒號 + 半形逗號才算堆疊摘要 ----

    @Test
    fun stacked_summary_title_detected() {
        assertTrue(NotificationClassifier.isStackSummaryTitle("Amy：早安, Bob：午安"))
    }

    @Test
    fun normal_sender_title_not_a_summary() {
        assertFalse(NotificationClassifier.isStackSummaryTitle("Stan Shih"))
    }

    @Test
    fun colon_without_comma_is_not_summary() {
        assertFalse(NotificationClassifier.isStackSummaryTitle("公告：停水通知"))
    }

    @Test
    fun comma_without_fullwidth_colon_is_not_summary() {
        // 半形冒號不算；必須是全形「：」。純逗號名字不該被誤殺。
        assertFalse(NotificationClassifier.isStackSummaryTitle("Lee, Chen"))
        assertFalse(NotificationClassifier.isStackSummaryTitle("time: 10:30, done"))
    }

    // ---- prefsKeyForType ----

    @Test
    fun prefs_key_mapping() {
        assertEquals(
            NotificationClassifier.PREFS_KNOWN_COMMUNITIES,
            NotificationClassifier.prefsKeyForType(NotificationClassifier.TYPE_COMMUNITY),
        )
        assertEquals(
            NotificationClassifier.PREFS_KNOWN_GROUPS,
            NotificationClassifier.prefsKeyForType(NotificationClassifier.TYPE_GROUP),
        )
        assertEquals(
            NotificationClassifier.PREFS_KNOWN_CHATS,
            NotificationClassifier.prefsKeyForType(NotificationClassifier.TYPE_PERSONAL),
        )
    }

    @Test
    fun prefs_key_unknown_type_falls_back_to_known_chats() {
        assertEquals(
            NotificationClassifier.PREFS_KNOWN_CHATS,
            NotificationClassifier.prefsKeyForType("something-else"),
        )
    }

    @Test
    fun only_line_new_messages_channel_is_supported() {
        assertTrue(
            NotificationClassifier.isSupportedMessageChannel(
                "jp.naver.line.android.notification.NewMessages"
            )
        )
        assertTrue(NotificationClassifier.isSupportedMessageChannel("NewMessages"))
        assertFalse(
            NotificationClassifier.isSupportedMessageChannel(
                "jp.naver.line.android.notification.LinePay"
            )
        )
        assertFalse(NotificationClassifier.isSupportedMessageChannel(null))
    }

    @Test
    fun known_and_forced_type_lookup_use_expected_precedence() {
        val known = mapOf(
            NotificationClassifier.PREFS_KNOWN_COMMUNITIES to setOf("重複", "社群"),
            NotificationClassifier.PREFS_KNOWN_GROUPS to setOf("重複", "群組"),
            NotificationClassifier.PREFS_KNOWN_CHATS to setOf("好友"),
        )
        assertEquals(
            NotificationClassifier.TYPE_COMMUNITY,
            NotificationClassifier.knownTypeOf(known, "重複"),
        )
        assertEquals(
            NotificationClassifier.TYPE_GROUP,
            NotificationClassifier.knownTypeOf(known, "群組"),
        )
        assertNull(NotificationClassifier.knownTypeOf(known, "未知"))

        val forced = mapOf(
            NotificationClassifier.PREFS_FORCED_COMMUNITIES to setOf("手動社群"),
            NotificationClassifier.PREFS_FORCED_GROUPS to setOf("手動群組"),
            NotificationClassifier.PREFS_FORCED_CHATS to setOf("手動好友"),
        )
        assertEquals(
            NotificationClassifier.TYPE_PERSONAL,
            NotificationClassifier.forcedTypeOf(forced, "手動好友"),
        )
    }

    // ---- chatTitleOf / roomKeyOf ----

    @Test
    fun chat_title_prefers_subText() {
        assertEquals("群組名", NotificationClassifier.chatTitleOf(title = "發送者", subText = "群組名"))
        assertEquals("發送者", NotificationClassifier.chatTitleOf(title = "發送者", subText = null))
    }

    @Test
    fun room_key_joins_profile_and_title() {
        assertEquals("123:愛如潮水", NotificationClassifier.roomKeyOf("123", "愛如潮水"))
    }

    @Test
    fun different_profiles_produce_different_room_keys() {
        // 雙開帳號區分：同一聊天室名在不同 profile 下 roomKey 不同。
        val a = NotificationClassifier.roomKeyOf("111", "同名聊天室")
        val b = NotificationClassifier.roomKeyOf("222", "同名聊天室")
        assertEquals("111:同名聊天室", a)
        assertEquals("222:同名聊天室", b)
        assertTrue(a != b)
    }

    // ---- Android 15+ sensitive notification redaction ----

    @Test
    fun framework_redacted_clone_is_detected() {
        assertTrue(
            NotificationClassifier.isSystemRedactedNotification(
                title = "LINE",
                text = "系統已隱藏含有私密資訊的通知內容",
                subText = null,
                sourceAppLabel = "LINE",
                systemRedactedText = "系統已隱藏含有私密資訊的通知內容",
            )
        )
    }

    @Test
    fun ordinary_message_matching_only_part_of_signature_is_not_redacted() {
        assertFalse(
            NotificationClassifier.isSystemRedactedNotification(
                title = "朋友",
                text = "系統已隱藏含有私密資訊的通知內容",
                subText = null,
                sourceAppLabel = "LINE",
                systemRedactedText = "系統已隱藏含有私密資訊的通知內容",
            )
        )
        assertFalse(
            NotificationClassifier.isSystemRedactedNotification(
                title = "LINE",
                text = "一般訊息",
                subText = null,
                sourceAppLabel = "LINE",
                systemRedactedText = "系統已隱藏含有私密資訊的通知內容",
            )
        )
    }

    // ---- per-chat full mute ----

    @Test
    fun disabled_chat_is_fully_muted() {
        assertTrue(NotificationClassifier.shouldMuteChat("安靜群組", setOf("安靜群組")))
        assertFalse(NotificationClassifier.shouldMuteChat("其他群組", setOf("安靜群組")))
    }

    @Test
    fun legacy_disabled_chat_keeps_original_mode_after_upgrade() {
        assertEquals(
            NotificationClassifier.MODE_LEGACY_ORIGINAL_ONLY,
            NotificationClassifier.notificationModeOf("舊聊天室", setOf("舊聊天室"), emptySet()),
        )
    }

    @Test
    fun explicit_new_mute_wins_if_corrupt_data_contains_both_modes() {
        assertEquals(
            NotificationClassifier.MODE_MUTED,
            NotificationClassifier.notificationModeOf(
                "聊天室",
                legacyOriginalOnlyChats = setOf("聊天室"),
                mutedChats = setOf("聊天室"),
            ),
        )
    }

    @Test
    fun enable_then_disable_moves_legacy_chat_to_new_mute_set() {
        val enabled = NotificationClassifier.updateNotificationPreferenceSets(
            chatTitle = "舊聊天室",
            enabled = true,
            legacyOriginalOnlyChats = setOf("舊聊天室"),
            mutedChats = emptySet(),
        )
        assertTrue(enabled.legacyOriginalOnly.isEmpty())
        assertTrue(enabled.muted.isEmpty())

        val disabledAgain = NotificationClassifier.updateNotificationPreferenceSets(
            chatTitle = "舊聊天室",
            enabled = false,
            legacyOriginalOnlyChats = enabled.legacyOriginalOnly,
            mutedChats = enabled.muted,
        )
        assertTrue(disabledAgain.legacyOriginalOnly.isEmpty())
        assertEquals(setOf("舊聊天室"), disabledAgain.muted)
    }

    @Test
    fun dedupe_fingerprint_is_stable_and_does_not_retain_plaintext() {
        val first = NotificationClassifier.dedupeFingerprint("profile:room", "私密訊息", 1234)
        val same = NotificationClassifier.dedupeFingerprint("profile:room", "私密訊息", 1234)
        val changed = NotificationClassifier.dedupeFingerprint("profile:room", "另一則", 1234)
        assertEquals(first, same)
        assertFalse(first.contains("私密訊息"))
        assertTrue(first != changed)
    }

    // ---- LINE conversation + legacy mirror（Nothing OS 實機 fixture）----

    private fun mirrorPrimary(
        key: String = "primary",
        shortcut: String = "chat-123",
        postTime: Long = 1_000L,
        elapsed: Long = 2_000L,
    ) = NotificationClassifier.MirrorSource(
        key = key,
        id = shortcut.hashCode(),
        tag = "NOTIFICATION_TAG_MESSAGE",
        shortcutId = shortcut,
        postTime = postTime,
        seenElapsed = elapsed,
    )

    private fun legacyMirror(
        key: String = "mirror",
        shortcut: String = "chat-123",
        postTime: Long = 1_009L,
        elapsed: Long = 2_024L,
    ) = NotificationClassifier.MirrorSource(
        key = key,
        id = 16_880_000,
        tag = null,
        shortcutId = shortcut,
        postTime = postTime,
        seenElapsed = elapsed,
    )

    @Test
    fun observed_tagged_then_legacy_pair_is_accepted_at_inclusive_boundaries() {
        assertTrue(NotificationClassifier.isObservedLineMirrorPair(mirrorPrimary(), legacyMirror()))
        assertTrue(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary(postTime = 1_000L, elapsed = 2_000L),
                legacyMirror(postTime = 1_100L, elapsed = 2_500L),
            )
        )
    }

    @Test
    fun reverse_mirror_order_is_rejected() {
        assertFalse(NotificationClassifier.isObservedLineMirrorPair(legacyMirror(), mirrorPrimary()))
    }

    @Test
    fun unknown_tag_or_ids_are_rejected() {
        assertFalse(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary().copy(tag = "other"),
                legacyMirror(),
            )
        )
        assertFalse(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary().copy(id = 7),
                legacyMirror(),
            )
        )
        assertFalse(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary(),
                legacyMirror().copy(id = 7),
            )
        )
    }

    @Test
    fun same_key_or_different_shortcut_is_rejected() {
        assertFalse(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary(key = "same"),
                legacyMirror(key = "same"),
            )
        )
        assertFalse(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary(shortcut = "a"),
                legacyMirror(shortcut = "b"),
            )
        )
    }

    @Test
    fun elapsed_or_post_time_outside_window_is_rejected() {
        assertFalse(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary(elapsed = 2_000L),
                legacyMirror(elapsed = 2_501L),
            )
        )
        assertFalse(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary(elapsed = 2_000L),
                legacyMirror(elapsed = 1_999L),
            )
        )
        assertFalse(
            NotificationClassifier.isObservedLineMirrorPair(
                mirrorPrimary(postTime = 1_000L),
                legacyMirror(postTime = 1_101L),
            )
        )
    }

    // ---- reclassify：改類別時從舊 set 移除（09088de 的自動修正殘留邏輯）----

    @Test
    fun reclassify_moves_chat_from_group_to_community() {
        // 社群曾被誤存進 known_groups，這次判成社群 → 應從 groups 移除、進 communities。
        val before = mapOf(
            NotificationClassifier.PREFS_KNOWN_GROUPS to setOf("測試用途", "真群組"),
            NotificationClassifier.PREFS_KNOWN_COMMUNITIES to emptySet<String>(),
            NotificationClassifier.PREFS_KNOWN_CHATS to emptySet<String>(),
        )
        val after = NotificationClassifier.reclassify(
            before, "測試用途", NotificationClassifier.TYPE_COMMUNITY,
        )
        assertFalse("應從舊 groups set 移除", "測試用途" in after[NotificationClassifier.PREFS_KNOWN_GROUPS]!!)
        assertTrue("其他群組不動", "真群組" in after[NotificationClassifier.PREFS_KNOWN_GROUPS]!!)
        assertTrue("應進 communities", "測試用途" in after[NotificationClassifier.PREFS_KNOWN_COMMUNITIES]!!)
    }

    @Test
    fun reclassify_is_idempotent_when_already_correct() {
        val before = mapOf(
            NotificationClassifier.PREFS_KNOWN_COMMUNITIES to setOf("Nothing Taiwan"),
            NotificationClassifier.PREFS_KNOWN_GROUPS to emptySet<String>(),
            NotificationClassifier.PREFS_KNOWN_CHATS to emptySet<String>(),
        )
        val after = NotificationClassifier.reclassify(
            before, "Nothing Taiwan", NotificationClassifier.TYPE_COMMUNITY,
        )
        assertEquals(setOf("Nothing Taiwan"), after[NotificationClassifier.PREFS_KNOWN_COMMUNITIES])
        assertTrue(after[NotificationClassifier.PREFS_KNOWN_GROUPS]!!.isEmpty())
        assertTrue(after[NotificationClassifier.PREFS_KNOWN_CHATS]!!.isEmpty())
    }

    @Test
    fun reclassify_new_personal_chat_lands_in_known_chats_only() {
        val before = mapOf(
            NotificationClassifier.PREFS_KNOWN_COMMUNITIES to emptySet<String>(),
            NotificationClassifier.PREFS_KNOWN_GROUPS to emptySet<String>(),
            NotificationClassifier.PREFS_KNOWN_CHATS to emptySet<String>(),
        )
        val after = NotificationClassifier.reclassify(
            before, "Stan Shih", NotificationClassifier.TYPE_PERSONAL,
        )
        assertEquals(setOf("Stan Shih"), after[NotificationClassifier.PREFS_KNOWN_CHATS])
        assertTrue(after[NotificationClassifier.PREFS_KNOWN_GROUPS]!!.isEmpty())
        assertTrue(after[NotificationClassifier.PREFS_KNOWN_COMMUNITIES]!!.isEmpty())
    }

    @Test
    fun reclassify_removes_from_both_other_sets() {
        // 極端情況：同名同時殘留在兩個錯的 set，改類別要兩邊都清掉。
        val before = mapOf(
            NotificationClassifier.PREFS_KNOWN_GROUPS to setOf("重複"),
            NotificationClassifier.PREFS_KNOWN_CHATS to setOf("重複"),
            NotificationClassifier.PREFS_KNOWN_COMMUNITIES to emptySet<String>(),
        )
        val after = NotificationClassifier.reclassify(
            before, "重複", NotificationClassifier.TYPE_COMMUNITY,
        )
        assertTrue("重複" in after[NotificationClassifier.PREFS_KNOWN_COMMUNITIES]!!)
        assertFalse("重複" in after[NotificationClassifier.PREFS_KNOWN_GROUPS]!!)
        assertFalse("重複" in after[NotificationClassifier.PREFS_KNOWN_CHATS]!!)
    }
}
