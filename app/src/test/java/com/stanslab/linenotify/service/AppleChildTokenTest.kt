package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Apple 分組模式 child 卡的訊息身分 token。
 *
 * 對應使用者回報：「顯示通知重複（出現兩個一樣的通知）」，且回報者明確說是 Apple 分組模式。
 *
 * vc23 以前，token 素材含 `childGeneration`（每貼一次就遞增）與 `room.messages.size`，
 * 所以同一則訊息的兩個 LINE callback 必然算出不同 tag、貼出兩張永遠不會互相覆蓋的卡，
 * 而且各佔一個 cap 名額。對照 thread 模式同房永遠同 tag（會覆寫），這解釋了為什麼
 * 「兩個一模一樣的通知」只在 Apple 模式被回報。
 *
 * 這組測試的作用是把「tag 只能由訊息身分決定」鎖成規格。
 */
class AppleChildTokenTest {

    private val room = "jp.naver.line.android@0:爭鮮果后水果批發"
    private val text = "老闆又放大招啦"
    private val whenMs = 1_785_051_673_327L

    private fun token(
        roomKey: String = room,
        text: String = this.text,
        identityWhenMs: Long = whenMs,
        isFromMe: Boolean = false,
    ) = NotificationClassifier.appleChildToken(roomKey, text, identityWhenMs, isFromMe)

    /** 核心性質：同一則訊息重複貼必須算出同一個 token，才會覆蓋成一張卡。 */
    @Test
    fun same_message_yields_same_token() {
        assertEquals(token(), token())
    }

    /**
     * 分辨力：真的連續傳兩則相同文字時，LINE 的 when 毫秒必不同
     * （2026-07-21 實測差 2449ms），所以差一毫秒就要分得開，否則會吃掉真訊息。
     */
    @Test
    fun one_millisecond_apart_yields_different_token() {
        assertNotEquals(token(), token(identityWhenMs = whenMs + 1))
    }

    @Test
    fun different_text_yields_different_token() {
        assertNotEquals(token(), token(text = "$text！"))
    }

    @Test
    fun different_room_yields_different_token() {
        assertNotEquals(token(), token(roomKey = "jp.naver.line.android@0:別的群組"))
    }

    /** 自己的回覆與收到的訊息不可撞身分，即使內容與時間戳恰好相同。 */
    @Test
    fun own_reply_never_collides_with_incoming_message() {
        assertNotEquals(token(isFromMe = false), token(isFromMe = true))
    }

    /**
     * 這條是給未來的人看的：token 不可以摻入任何「每次貼都會變」的值。
     *
     * 函式簽章本身就是防線（傳不進 generation / buffer size / postTime），這裡再用行為
     * 補一道：連續兩次呼叫之間若有任何隱含狀態被摻進去，冪等就會破。
     */
    @Test
    fun token_has_no_hidden_per_call_state() {
        val first = token()
        repeat(5) { token(roomKey = "其他房$it", text = "其他訊息$it") }
        assertEquals(first, token())
    }

    /** 空字串與極端時間戳不可拋例外，也不可退化成同一個值。 */
    @Test
    fun handles_edge_inputs() {
        assertNotEquals(token(text = ""), token(text = " "))
        assertNotEquals(token(identityWhenMs = 0L), token(identityWhenMs = Long.MAX_VALUE))
    }
}
