package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Test

class LineMessageTextResolverTest {
    @Test
    fun messaging_style_full_text_wins_over_preview() {
        val fullText = (1..40).joinToString("\n") { "第 $it 行" }
        val preview = fullText.lineSequence().take(25).joinToString("\n")

        assertEquals(fullText, LineMessageTextResolver.resolve(preview, fullText))
    }

    @Test
    fun missing_messaging_style_falls_back_to_preview() {
        assertEquals("預覽內容", LineMessageTextResolver.resolve("預覽內容", null))
    }

    @Test
    fun empty_messaging_style_falls_back_to_preview() {
        assertEquals("預覽內容", LineMessageTextResolver.resolve("預覽內容", ""))
    }

    @Test
    fun full_text_preserves_newlines_and_whitespace() {
        val fullText = "第一行\n  第二行\n\n第四行  "

        assertEquals(fullText, LineMessageTextResolver.resolve("第一行…", fullText))
    }
}
