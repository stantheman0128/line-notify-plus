package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 系統遮蔽 clone 的辨識。兩種 OEM 形狀各一條線，外加誤殺防護。
 *
 * 這組測試鎖住的是一次真實回歸：vc18 的 4acd58f 為了修好 realme（clone 保留原 title/subText，
 * 只有占位字可辨識）把 AOSP 形狀那條線整條刪掉，於是走標準路徑的機器從偶爾漏判變成必然漏判。
 *
 * 實機證據（Nothing A059P / Android 16 / LINE 26.11.0 / 系統語系 zh-Hant-TW）：
 * chat_last_active 在 1783826523374 這個毫秒同時記了「蝦皮店到店包裹通知」與「LINE」兩個聊天室，
 * 後者的頭貼檔 chat_avatars/2336756.png（"LINE".hashCode() = 2336756）是 LINE 的 App 圖示。
 * 同一則通知被處理兩次，第二次是遮蔽版而守門沒認出來。
 */
class RedactionCloneReproTest {

    private val appLabel = "LINE"
    private val defaultLocalePlaceholder = "Sensitive notification content hidden"
    private val zhTwPlaceholder = "系統已隱藏含有私密資訊的通知內容"

    private fun check(
        title: String,
        text: String,
        subText: String? = null,
        sourceAppLabel: String? = appLabel,
        systemRedactedText: String? = defaultLocalePlaceholder,
        largeIconMatchesAppIcon: Boolean = false,
    ) = NotificationClassifier.isSystemRedactedNotification(
        title = title,
        text = text,
        subText = subText,
        sourceAppLabel = sourceAppLabel,
        systemRedactedText = systemRedactedText,
        largeIconMatchesAppIcon = largeIconMatchesAppIcon,
    )

    /**
     * 第 2 條線（AOSP 形狀）。這是 2026-07-12 那次漏判的輸入：形狀完全符合 clone，
     * 但 text 對不上取到的占位字（系統語系繁中、取到的是預設英文，或 OEM 換過字串）。
     * 舊實作只比對 text，這組必然漏判。
     */
    @Test
    fun catches_aosp_clone_shape_when_placeholder_text_does_not_match() {
        assertTrue(
            check(
                title = appLabel,
                text = zhTwPlaceholder,
                subText = null,
                largeIconMatchesAppIcon = true,
            )
        )
    }

    /** 形狀相同但占位字是 OEM 自己換過的字，一樣要擋下。 */
    @Test
    fun catches_aosp_clone_shape_with_oem_rewritten_placeholder() {
        assertTrue(
            check(
                title = appLabel,
                text = "OEM 自己換過的占位字",
                largeIconMatchesAppIcon = true,
            )
        )
    }

    /**
     * 第 1 條線（realme 形狀）。clone 保留原 title/subText、largeIcon 也不是 App 圖示，
     * 只剩占位字可辨識。這是 vc18 修好的案例，不能因為加了形狀線就退化。
     */
    @Test
    fun catches_realme_clone_by_placeholder_text_alone() {
        assertTrue(
            check(
                title = "小明",
                text = defaultLocalePlaceholder,
                subText = "家族群組",
                largeIconMatchesAppIcon = false,
            )
        )
    }

    /**
     * 誤殺防護，也是加入 largeIcon 條件的唯一理由：
     * 聊天室名剛好等於 App 名稱的 1:1 對話（LINE 官方帳號本身就叫「LINE」），
     * title 與 subText 都符合 clone 形狀，只有頭像不同——它帶的是帳號頭像。
     */
    @Test
    fun does_not_flag_conversation_whose_title_equals_app_label() {
        assertFalse(
            check(
                title = appLabel,
                text = "您的驗證碼是 123456",
                subText = null,
                largeIconMatchesAppIcon = false,
            )
        )
    }

    /** 誤殺防護：群組訊息帶 subText，形狀就不成立。 */
    @Test
    fun does_not_flag_group_message_with_subtext() {
        assertFalse(
            check(
                title = appLabel,
                text = "在嗎",
                subText = "家族群組",
                largeIconMatchesAppIcon = true,
            )
        )
    }

    /** 誤殺防護：一般 1:1 訊息，三個條件一個都不成立。 */
    @Test
    fun does_not_flag_ordinary_direct_message() {
        assertFalse(check(title = "小明", text = "晚點打給你"))
    }

    /**
     * 鎖住「閘門」與「完整形狀判斷」的一致性。
     *
     * listener 用 matchesAospCloneShapeExceptIcon 當閘門，決定要不要花成本做圖示比對；
     * 圖示比中時，完整判斷的結果就必須等於閘門。任何一邊被重寫成另一組條件，這條會紅。
     * 這正是 vc18 回歸的病根——同一組條件散在兩處，改一邊忘另一邊而測試照樣全綠。
     */
    @Test
    fun gate_stays_consistent_with_full_shape_check() {
        val cases = listOf(
            Triple(appLabel, null, appLabel),
            Triple(appLabel, "家族群組", appLabel),
            Triple("小明", null, appLabel),
            Triple(appLabel, null, null),
            Triple(appLabel, null, ""),
        )
        for ((title, subText, label) in cases) {
            val gate = NotificationClassifier.matchesAospCloneShapeExceptIcon(title, subText, label)
            val full = NotificationClassifier.matchesAospCloneShape(
                title = title,
                subText = subText,
                sourceAppLabel = label,
                largeIconMatchesAppIcon = true,
            )
            assertEquals("title=$title subText=$subText label=$label", gate, full)
        }
    }

    /** 取不到 App 名稱時不能靠形狀成立，否則任何 title 都可能被誤判。 */
    @Test
    fun does_not_flag_when_app_label_unavailable() {
        assertFalse(
            check(
                title = appLabel,
                text = "在嗎",
                sourceAppLabel = null,
                largeIconMatchesAppIcon = true,
            )
        )
    }
}
