package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityRoomMatcherTest {
    private val packages = setOf("jp.naver.line.android", "com.linecorp.line")
    private val room = AccessibilityRoomMatcher.ActiveRoom(
        roomKey = "jp.naver.line.android@0:家人",
        chatTitle = "家人",
        sourcePackage = "jp.naver.line.android",
    )

    @Test
    fun unique_header_and_composer_matches_room() {
        val evidence = AccessibilityRoomMatcher.ScreenEvidence(
            packageName = "jp.naver.line.android",
            headerTitles = setOf("家人"),
            hasBottomEditable = true,
        )

        assertEquals(room.roomKey, AccessibilityRoomMatcher.uniqueRoomKey(evidence, listOf(room), packages))
    }

    @Test
    fun chat_list_without_bottom_composer_does_not_match() {
        val evidence = AccessibilityRoomMatcher.ScreenEvidence(
            packageName = "jp.naver.line.android",
            headerTitles = setOf("家人"),
            hasBottomEditable = false,
        )

        assertNull(AccessibilityRoomMatcher.uniqueRoomKey(evidence, listOf(room), packages))
    }

    @Test
    fun unsupported_or_wrong_package_does_not_match() {
        val evidence = AccessibilityRoomMatcher.ScreenEvidence(
            packageName = "example.other",
            headerTitles = setOf("家人"),
            hasBottomEditable = true,
        )

        assertNull(AccessibilityRoomMatcher.uniqueRoomKey(evidence, listOf(room), packages))
    }

    @Test
    fun same_title_across_profiles_is_ambiguous() {
        val second = room.copy(roomKey = "jp.naver.line.android@10:家人")
        val evidence = AccessibilityRoomMatcher.ScreenEvidence(
            packageName = "jp.naver.line.android",
            headerTitles = setOf("家人"),
            hasBottomEditable = true,
        )

        assertNull(
            AccessibilityRoomMatcher.uniqueRoomKey(evidence, listOf(room, second), packages),
        )
    }

    @Test
    fun title_from_another_line_package_does_not_cross_match() {
        val evidence = AccessibilityRoomMatcher.ScreenEvidence(
            packageName = "com.linecorp.line",
            headerTitles = setOf("家人"),
            hasBottomEditable = true,
        )

        assertNull(AccessibilityRoomMatcher.uniqueRoomKey(evidence, listOf(room), packages))
    }
}
