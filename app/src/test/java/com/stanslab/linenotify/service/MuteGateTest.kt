package com.stanslab.linenotify.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 靜音硬封鎖的判定。
 *
 * 對應使用者回報的兩件事：「我把聊天室關掉了，@all 還是會跳」與「社群管理員設了重要貼文，
 * 通知還是會跳」。成因不是靜音判斷本身寫錯，而是它在 onNotificationPosted 裡排在
 * 頻道白名單、GROUP_SUMMARY、title/text 空值、系統遮蔽這四道早退**之後**——
 * 那四道 return 掉的通知照樣會出現在使用者面前，靜音對它們一律失效。
 *
 * LINE 26.11.0 在 Nothing A059P 實測註冊了 10 個 channel，社群公告與記事本重要貼文
 * 走的是 SquareActivity（社群活動），不在 isSupportedMessageChannel 的白名單內。
 */
class MuteGateTest {

    private val appLabel = "LINE"
    private val newMessages = "jp.naver.line.android.notification.NewMessages"
    private val squareActivity = "jp.naver.line.android.notification.SquareActivity"
    private val redacted = "Sensitive notification content hidden"

    private fun muted(
        channelId: String?,
        title: String?,
        subText: String? = null,
        text: String? = "訊息內容",
        isGroupSummary: Boolean = false,
        mutedChats: Set<String> = setOf("爭鮮果后水果批發", "GEMINI 3.0 PRO"),
    ) = NotificationClassifier.shouldHardMute(
        channelId = channelId,
        title = title,
        subText = subText,
        text = text,
        isGroupSummary = isGroupSummary,
        sourceAppLabel = appLabel,
        systemRedactedText = redacted,
        mutedChats = mutedChats,
    )

    /** 靜音可攔截的頻道比白名單寬，社群活動要進得來。 */
    @Test
    fun mute_eligible_channel_covers_square_activity() {
        assertTrue(NotificationClassifier.isMuteEligibleChannel(newMessages))
        assertTrue(NotificationClassifier.isMuteEligibleChannel(squareActivity))
        assertTrue(NotificationClassifier.isMuteEligibleChannel("SquareActivity"))
    }

    /** 但重建卡片的白名單不可以跟著放寬，公告類沒有 MessagingStyle，重建只會生假聊天室。 */
    @Test
    fun supported_message_channel_stays_narrow() {
        assertTrue(NotificationClassifier.isSupportedMessageChannel(newMessages))
        assertFalse(NotificationClassifier.isSupportedMessageChannel(squareActivity))
    }

    /** 通話與其他頻道兩邊都不該進。 */
    @Test
    fun unrelated_channels_are_excluded_from_both() {
        for (id in listOf(
            "jp.naver.line.android.notification.VoIP.01.Incoming",
            "jp.naver.line.android.notification.Timeline",
            "jp.naver.line.android.notification.LinePay",
            "jp.naver.line.android.notification.GeneralNotifications",
        )) {
            assertFalse(id, NotificationClassifier.isMuteEligibleChannel(id))
            assertFalse(id, NotificationClassifier.isSupportedMessageChannel(id))
        }
    }

    /**
     * 核心案例：已靜音社群的公告通知走 SquareActivity，必須被撤掉。
     * 舊行為在頻道白名單那一道就 return 了，這條會紅。
     */
    @Test
    fun mutes_square_activity_announcement() {
        assertTrue(muted(squareActivity, title = "GEMINI 3.0 PRO"))
    }

    /** 一般聊天訊息的靜音不能因為這次改動而退化。 */
    @Test
    fun mutes_ordinary_group_message() {
        assertTrue(muted(newMessages, title = "萱萱", subText = "爭鮮果后水果批發"))
    }

    /** 沒被靜音的聊天室不能誤撤。 */
    @Test
    fun does_not_mute_unmuted_chat() {
        assertFalse(muted(newMessages, title = "小明"))
        assertFalse(muted(squareActivity, title = "沒靜音的社群"))
    }

    /**
     * 系統遮蔽的通知即使該聊天室已靜音也不能撤：原文在 callback 前就已不可逆遺失，
     * 撤掉等於讓使用者永遠不知道有這則訊息。
     */
    @Test
    fun never_mutes_system_redacted_notification() {
        assertFalse(
            muted(newMessages, title = "爭鮮果后水果批發", text = redacted)
        )
    }

    /** group summary 涵蓋多個聊天室，不可歸屬到單一房，不能靠它做靜音判斷。 */
    @Test
    fun never_mutes_group_summary() {
        assertFalse(
            muted(newMessages, title = "爭鮮果后水果批發", isGroupSummary = true)
        )
    }

    /** AOSP 遮蔽 clone 的 title 是 App 名稱，跟聊天室無關，不可歸屬。 */
    @Test
    fun never_mutes_unattributable_clone_shape() {
        assertFalse(
            NotificationClassifier.isAttributableChatTitle(appLabel, null, appLabel)
        )
        assertFalse(muted(newMessages, title = appLabel, mutedChats = setOf(appLabel)))
    }

    /** 沒有 subText 的堆疊摘要，title 本身就是多室拼接，不可歸屬。 */
    @Test
    fun never_mutes_stack_summary_title() {
        assertFalse(
            NotificationClassifier.isAttributableChatTitle("A：早安, B：午安", null, appLabel)
        )
    }

    /** 有 subText 時 title 是發送者名，即使含逗號冒號也仍可歸屬到 subText 那個房。 */
    @Test
    fun group_message_with_subtext_stays_attributable() {
        assertTrue(
            NotificationClassifier.isAttributableChatTitle("早餐, 午餐：Amy", "早餐, 午餐", appLabel)
        )
    }

    /** title 取不到就無從歸屬，一律不撤。 */
    @Test
    fun does_not_mute_without_title() {
        assertFalse(muted(newMessages, title = null))
    }
}
