package com.stanslab.linenotify.service

/**
 * LINE 可能把 [previewText] 做成通知列預覽，而把未截短的本文放在 MessagingStyle。
 * 只要 MessagingStyle 確實提供最新一則文字，就優先使用；解析不到時才保留預覽欄位。
 */
internal object LineMessageTextResolver {
    fun resolve(previewText: String, latestMessagingText: String?): String =
        latestMessagingText?.takeIf { it.isNotEmpty() } ?: previewText
}
