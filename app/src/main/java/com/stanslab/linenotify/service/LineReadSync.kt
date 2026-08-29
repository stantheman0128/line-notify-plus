package com.stanslab.linenotify.service

/**
 * 取代模式下「LINE 內已讀 → Notify+ 也消失」的判斷邏輯。
 *
 * 背景：NotificationListenerService.cancelNotification() 會把 LINE 的
 * StatusBarNotification 從系統裡刪掉。之後使用者在 LINE App 裡點開聊天室，
 * LINE 再 NotificationManager.cancel() 就是 no-op，我們收不到
 * REASON_APP_CANCEL，這就是舊版寫成「先天限制」的原因。
 *
 * 修法：不要 cancel，改 snooze（藏到通知欄外，但系統仍保留這則通知）。
 * LINE 已讀時仍能對同一則 cancel；有的系統會直接回 APP_CANCEL，
 * 有的只把 snoozed record 標成 isCanceled。後者用短暫 unsnooze 探測：
 * 未取消的會再出現（立刻再藏起來），已取消的會從 snoozed/active 兩邊消失。
 *
 * 常數對齊 [android.service.notification.NotificationListenerService] 的公開 REASON_*，
 * 讓這份邏輯可以在沒有 Android framework 的 JVM 測試裡跑。
 */
object LineReadSync {
    /** 用長時間 snooze 當 sentinel；到期前若還未已讀，watchdog 會再延長。 */
    const val SENTINEL_SNOOZE_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * 探測用的極短 snooze。AOSP 對 duration <= 0 會直接 return；
     * 15ms 足夠觸發 repost，又短到多數機型來不及畫出完整 heads-up。
     */
    const val PROBE_UNSNOOZE_MS = 15L

    /** 螢幕亮著時較密：使用者正在 LINE 裡已讀，清單若有變就能較快對上。 */
    const val INTERACTIVE_WATCHDOG_MS = 300L
    const val IDLE_WATCHDOG_MS = 1_500L
    const val RANKING_RECONCILE_DELAY_MS = 50L

    /**
     * 探測必須開：Nothing 上 LINE 已讀後，snoozed 紀錄常常還在，也不送 APP_CANCEL。
     * 重複增生已改由「哨兵 key 一律 echo」擋住，不再靠關探測。
     */
    const val PROBE_ENABLED = true
    const val PROBE_INTERVAL_MS = 1_000L
    /** 探測後 extras 會抖一下；這段時間只對齊指紋，不當新訊息。 */
    const val PROBE_QUIET_MS = 500L
    const val MIN_SNOOZED_INGEST_MS = 2_000L

    const val REASON_CLICK = 1
    const val REASON_CANCEL = 2
    const val REASON_CANCEL_ALL = 3
    const val REASON_APP_CANCEL = 8
    const val REASON_APP_CANCEL_ALL = 9
    const val REASON_LISTENER_CANCEL = 10
    const val REASON_LISTENER_CANCEL_ALL = 11
    const val REASON_SNOOZED = 18

    enum class SentinelSighting {
        /** 仍藏著，內容沒變。 */
        SNOOZED_SAME,
        /** 仍藏著，但 LINE 用同一 key 更新了內容（同房新訊息）。 */
        SNOOZED_UPDATED,
        /** 又出現在通知欄（探測 repost 或 snooze 失敗）。 */
        ACTIVE,
        /** active 與 snoozed 都沒有。 */
        GONE,
    }

    /**
     * 只看 key + 正文。不要納入通知的 when 時間戳：系統 unsnooze／再 snooze
     * 常會改這個時間，舊版因此把同一則當成新訊息越疊越多。
     */
    fun payloadFingerprint(key: String, text: String): String = "$key\u0000$text"

    fun watchdogIntervalMs(interactive: Boolean): Long =
        if (interactive) INTERACTIVE_WATCHDOG_MS else IDLE_WATCHDOG_MS

    /**
     * 只要這則還在哨兵表裡，任何 post 都當成系統又送回來，不要進對話串。
     * 真正的同 key 新訊息只走 snoozed 清單的 UPDATED，並帶 ingestingUpdate。
     */
    fun isSentinelKeyEcho(hasSentinel: Boolean, ingestingUpdate: Boolean): Boolean =
        hasSentinel && !ingestingUpdate

    fun shouldIngestSnoozedUpdate(
        nowElapsed: Long,
        lastIngestElapsed: Long,
        lastProbeElapsed: Long,
        minIntervalMs: Long = MIN_SNOOZED_INGEST_MS,
        probeQuietMs: Long = PROBE_QUIET_MS,
    ): Boolean {
        if (nowElapsed - lastIngestElapsed < minIntervalMs) return false
        if (nowElapsed - lastProbeElapsed < probeQuietMs) return false
        return true
    }

    fun isConsecutiveDuplicate(
        lastSender: String?,
        lastText: String?,
        sender: String,
        text: String,
    ): Boolean = lastSender != null && lastSender == sender && lastText == text

    fun isLineReadReason(reason: Int): Boolean = when (reason) {
        REASON_APP_CANCEL, REASON_APP_CANCEL_ALL, REASON_CLICK, REASON_CANCEL -> true
        else -> false
    }

    /**
     * LINE 原通知被移除時，要不要清掉對應的 Notify+。
     * 取代模式也要清：sentinel 就是為了讓這條路在取代模式走得通。
     * REASON_SNOOZED / LISTENER_CANCEL 是我們自己藏或殺掉，不是已讀。
     */
    fun shouldClearForLineRemoval(reason: Int, clearAfterRead: Boolean): Boolean {
        if (!clearAfterRead) return false
        if (reason == REASON_SNOOZED) return false
        if (reason == REASON_LISTENER_CANCEL || reason == REASON_LISTENER_CANCEL_ALL) return false
        return isLineReadReason(reason)
    }

    fun classifySentinel(
        isActive: Boolean,
        snoozedFingerprint: String?,
        lastFingerprint: String,
    ): SentinelSighting = when {
        snoozedFingerprint != null && snoozedFingerprint != lastFingerprint ->
            SentinelSighting.SNOOZED_UPDATED
        snoozedFingerprint != null -> SentinelSighting.SNOOZED_SAME
        isActive -> SentinelSighting.ACTIVE
        else -> SentinelSighting.GONE
    }

    fun shouldProbe(
        sighting: SentinelSighting,
        nowElapsed: Long,
        lastProbeElapsed: Long,
        probeIntervalMs: Long = PROBE_INTERVAL_MS,
        probeEnabled: Boolean = PROBE_ENABLED,
    ): Boolean = probeEnabled &&
        sighting == SentinelSighting.SNOOZED_SAME &&
        nowElapsed - lastProbeElapsed >= probeIntervalMs

    /**
     * 從未在 getSnoozedNotifications() 裡見過這則，就還不能把 GONE 當成已讀：
     * 可能是 snooze 還沒寫進 list、或 OEM 根本不回 snoozed 清單。
     */
    fun shouldTreatGoneAsRead(sighting: SentinelSighting, seenSnoozed: Boolean): Boolean =
        sighting == SentinelSighting.GONE && seenSnoozed
}
