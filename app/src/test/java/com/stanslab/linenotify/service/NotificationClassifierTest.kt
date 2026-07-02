package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
