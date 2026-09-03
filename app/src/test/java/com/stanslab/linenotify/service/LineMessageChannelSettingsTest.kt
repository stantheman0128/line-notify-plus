package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineMessageChannelSettingsTest {

    @Test
    fun remembered_message_channel_wins() {
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
    fun stale_or_non_message_channel_falls_back_to_app_settings() {
        val target = LineMessageChannelSettings.resolveTarget(
            installedPackages = setOf("jp.naver.line.android"),
            rememberedPackage = "jp.naver.line.android",
            rememberedChannelId = "jp.naver.line.android.notification.LinePay",
        )

        assertEquals(LineMessageChannelTarget("jp.naver.line.android", null), target)
    }

    @Test
    fun installed_remembered_package_wins_when_both_are_present() {
        val target = LineMessageChannelSettings.resolveTarget(
            installedPackages = LineMessageChannelSettings.knownPackages.toSet(),
            rememberedPackage = "com.linecorp.line",
            rememberedChannelId = "NewMessages",
        )

        assertEquals(LineMessageChannelTarget("com.linecorp.line", "NewMessages"), target)
    }

    @Test
    fun no_observed_channel_does_not_guess_one() {
        val target = LineMessageChannelSettings.resolveTarget(
            installedPackages = setOf("jp.naver.line.android"),
            rememberedPackage = null,
            rememberedChannelId = null,
        )

        assertEquals(LineMessageChannelTarget("jp.naver.line.android", null), target)
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
