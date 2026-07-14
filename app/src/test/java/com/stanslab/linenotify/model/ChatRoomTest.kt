package com.stanslab.linenotify.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChatRoomTest {

    @Test
    fun rollback_removes_exact_instance_when_two_messages_are_value_equal() {
        val first = ChatMessage("A", "+1", 1234L, true, "群組")
        val second = first.copy()
        val room = ChatRoom("群組", isGroup = true)
        room.addMessage(first)
        room.addMessage(second)

        room.removeMessage(second)

        assertEquals(1, room.messages.size)
        assertSame(first, room.messages.single())
    }

    @Test
    fun thread_buffer_matches_messaging_style_retention_limit() {
        val room = ChatRoom("群組", isGroup = true)
        repeat(ChatRoom.MAX_MESSAGES + 5) { index ->
            room.addMessage(ChatMessage("A", "$index", index.toLong(), true, "群組"))
        }

        assertEquals(25, ChatRoom.MAX_MESSAGES)
        assertEquals(25, room.messages.size)
        assertEquals("5", room.messages.first().text)
    }
}
