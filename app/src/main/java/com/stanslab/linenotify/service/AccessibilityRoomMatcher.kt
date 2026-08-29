package com.stanslab.linenotify.service

/**
 * Accessibility 聊天畫面辨識的純邏輯。
 *
 * 呼叫端只提供「目前仍有 Notify+ 通知的聊天室標題」是否出現在畫面頂部，以及畫面底部
 * 是否有輸入欄位；不需要把 LINE 的訊息本文傳進來或保存。
 */
object AccessibilityRoomMatcher {

    data class ActiveRoom(
        val roomKey: String,
        val chatTitle: String,
        val sourcePackage: String,
    )

    data class ScreenEvidence(
        val packageName: String,
        val headerTitles: Set<String>,
        val hasBottomEditable: Boolean,
    )

    /**
     * 只有 LINE package、聊天室輸入欄與標題都吻合，且最後只剩一個 roomKey 時才回傳。
     * 同名聊天室或雙開帳號產生歧義時一律 fail-open，不清任何通知。
     */
    fun uniqueRoomKey(
        evidence: ScreenEvidence,
        activeRooms: Collection<ActiveRoom>,
        supportedPackages: Set<String>,
    ): String? {
        if (evidence.packageName !in supportedPackages || !evidence.hasBottomEditable) return null
        return activeRooms
            .asSequence()
            .filter { it.sourcePackage == evidence.packageName }
            .filter { it.chatTitle in evidence.headerTitles }
            .map { it.roomKey }
            .distinct()
            .singleOrNull()
    }
}
