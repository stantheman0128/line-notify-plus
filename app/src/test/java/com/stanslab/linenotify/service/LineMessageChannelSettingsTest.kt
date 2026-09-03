package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineMessageChannelSettingsTest {

    @Test
    fun remembered_real_channel_wins() {
        val target = LineMessageChannelSettings.resolveTarget(
            installedPackages = setOf("jp.naver.line.android"),
            rememberedPackage = "jp.naver.line.android",
            rememberedChannelId = "jp.naver.line.android.notification.NewMessages",
        )
        assertEquals(
            LineMessageChannelTarget(
                "jp.naver.line.android",
                "jp.naver.line.android.notification.NewMessages",
            ),
            target,
        )
    }

    @Test
    fun stale_memory_uses_installed_line_without_guessing_channel() {
        val target = LineMessageChannelSettings.resolveTarget(
            installedPackages = setOf("jp.naver.line.android"),
            rememberedPackage = "com.linecorp.line",
            rememberedChannelId = "com.linecorp.line.notification.LinePay",
        )
        assertEquals(LineMessageChannelTarget("jp.naver.line.android", null), target)
    }

    @Test
    fun installed_remembered_package_wins_when_both_line_packages_exist() {
        val target = LineMessageChannelSettings.resolveTarget(
            installedPackages = LineMessageChannelSettings.knownPackages.toSet(),
            rememberedPackage = "com.linecorp.line",
            rememberedChannelId = "NewMessages",
        )
        assertEquals(LineMessageChannelTarget("com.linecorp.line", "NewMessages"), target)
    }

    @Test
    fun non_message_channel_for_same_package_is_not_reused() {
        val target = LineMessageChannelSettings.resolveTarget(
            installedPackages = setOf("jp.naver.line.android"),
            rememberedPackage = "jp.naver.line.android",
            rememberedChannelId = "jp.naver.line.android.notification.LinePay",
        )
        assertEquals(LineMessageChannelTarget("jp.naver.line.android", null), target)
    }

    @Test
    fun package_without_observed_notification_does_not_guess_channel() {
        val target = LineMessageChannelSettings.resolveTarget(
            installedPackages = setOf("com.linecorp.line"),
            rememberedPackage = null,
            rememberedChannelId = null,
        )
        assertEquals(LineMessageChannelTarget("com.linecorp.line", null), target)
    }

    @Test
    fun no_installed_line_returns_null() {
        assertNull(
            LineMessageChannelSettings.resolveTarget(
                installedPackages = emptySet(),
                rememberedPackage = "jp.naver.line.android",
                rememberedChannelId = "jp.naver.line.android.notification.NewMessages",
            )
        )
    }
}
