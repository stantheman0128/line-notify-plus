package com.stanslab.linenotify.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineReadSyncTest {

    @Test
    fun app_cancel_is_the_primary_in_app_read_signal() {
        assertTrue(LineReadSync.isLineReadReason(LineReadSync.REASON_APP_CANCEL))
        assertTrue(LineReadSync.isLineReadReason(LineReadSync.REASON_APP_CANCEL_ALL))
        assertTrue(LineReadSync.isLineReadReason(LineReadSync.REASON_CLICK))
        assertTrue(LineReadSync.isLineReadReason(LineReadSync.REASON_CANCEL))
    }

    @Test
    fun snooze_and_listener_cancel_are_not_read_signals() {
        assertFalse(LineReadSync.isLineReadReason(LineReadSync.REASON_SNOOZED))
        assertFalse(LineReadSync.isLineReadReason(LineReadSync.REASON_LISTENER_CANCEL))
        assertFalse(LineReadSync.isLineReadReason(LineReadSync.REASON_LISTENER_CANCEL_ALL))
        assertFalse(LineReadSync.isLineReadReason(LineReadSync.REASON_CANCEL_ALL))
    }

    @Test
    fun replace_mode_must_still_clear_on_line_read() {
        // 舊版在取代模式直接 return，這是這次要拆掉的限制。
        assertTrue(
            LineReadSync.shouldClearForLineRemoval(
                reason = LineReadSync.REASON_APP_CANCEL,
                clearAfterRead = true,
            ),
        )
        assertTrue(
            LineReadSync.shouldClearForLineRemoval(
                reason = LineReadSync.REASON_APP_CANCEL_ALL,
                clearAfterRead = true,
            ),
        )
    }

    @Test
    fun our_own_hide_or_cancel_must_not_clear_notify_plus() {
        assertFalse(
            LineReadSync.shouldClearForLineRemoval(
                reason = LineReadSync.REASON_SNOOZED,
                clearAfterRead = true,
            ),
        )
        assertFalse(
            LineReadSync.shouldClearForLineRemoval(
                reason = LineReadSync.REASON_LISTENER_CANCEL,
                clearAfterRead = true,
            ),
        )
    }

    @Test
    fun clear_after_read_off_blocks_all_line_sync() {
        assertFalse(
            LineReadSync.shouldClearForLineRemoval(
                reason = LineReadSync.REASON_APP_CANCEL,
                clearAfterRead = false,
            ),
        )
    }

    @Test
    fun payload_fingerprint_tracks_text_but_ignores_notification_when() {
        val key = "0|jp.naver.line.android|n1"
        val first = LineReadSync.payloadFingerprint(key, "嗨")
        assertEquals(first, LineReadSync.payloadFingerprint(key, "嗨"))
        assertTrue(first != LineReadSync.payloadFingerprint(key, "嗨嗨"))
    }

    @Test
    fun sentinel_key_echo_ignores_payload_and_blocks_unless_ingesting() {
        assertTrue(LineReadSync.isSentinelKeyEcho(hasSentinel = true, ingestingUpdate = false))
        assertFalse(LineReadSync.isSentinelKeyEcho(hasSentinel = true, ingestingUpdate = true))
        assertFalse(LineReadSync.isSentinelKeyEcho(hasSentinel = false, ingestingUpdate = false))
    }

    @Test
    fun consecutive_duplicate_is_same_sender_and_text() {
        assertTrue(LineReadSync.isConsecutiveDuplicate("A", "嗨", "A", "嗨"))
        assertFalse(LineReadSync.isConsecutiveDuplicate("A", "嗨", "A", "嗨嗨"))
        assertFalse(LineReadSync.isConsecutiveDuplicate("A", "嗨", "B", "嗨"))
        assertFalse(LineReadSync.isConsecutiveDuplicate(null, null, "A", "嗨"))
    }

    @Test
    fun snoozed_update_ingest_has_cooldown_and_skips_probe_quiet_window() {
        assertTrue(
            LineReadSync.shouldIngestSnoozedUpdate(
                nowElapsed = 2_000L,
                lastIngestElapsed = 0L,
                lastProbeElapsed = 0L,
            ),
        )
        assertFalse(
            LineReadSync.shouldIngestSnoozedUpdate(
                nowElapsed = 1_999L,
                lastIngestElapsed = 0L,
                lastProbeElapsed = 0L,
            ),
        )
        assertFalse(
            LineReadSync.shouldIngestSnoozedUpdate(
                nowElapsed = 3_000L,
                lastIngestElapsed = 0L,
                lastProbeElapsed = 2_800L,
            ),
        )
    }

    @Test
    fun watchdog_is_faster_while_the_screen_is_on() {
        assertEquals(300L, LineReadSync.watchdogIntervalMs(interactive = true))
        assertEquals(1_500L, LineReadSync.watchdogIntervalMs(interactive = false))
        assertTrue(LineReadSync.RANKING_RECONCILE_DELAY_MS < LineReadSync.INTERACTIVE_WATCHDOG_MS)
    }

    @Test
    fun classify_sentinel_updated_vs_same_vs_gone() {
        val last = "fp-a"
        assertEquals(
            LineReadSync.SentinelSighting.SNOOZED_UPDATED,
            LineReadSync.classifySentinel(
                isActive = false,
                snoozedFingerprint = "fp-b",
                lastFingerprint = last,
            ),
        )
        assertEquals(
            LineReadSync.SentinelSighting.SNOOZED_SAME,
            LineReadSync.classifySentinel(
                isActive = true,
                snoozedFingerprint = last,
                lastFingerprint = last,
            ),
        )
        assertEquals(
            LineReadSync.SentinelSighting.ACTIVE,
            LineReadSync.classifySentinel(
                isActive = true,
                snoozedFingerprint = null,
                lastFingerprint = last,
            ),
        )
        assertEquals(
            LineReadSync.SentinelSighting.GONE,
            LineReadSync.classifySentinel(
                isActive = false,
                snoozedFingerprint = null,
                lastFingerprint = last,
            ),
        )
    }

    @Test
    fun gone_is_read_only_after_we_actually_saw_it_snoozed() {
        assertFalse(
            LineReadSync.shouldTreatGoneAsRead(
                LineReadSync.SentinelSighting.GONE,
                seenSnoozed = false,
            ),
        )
        assertTrue(
            LineReadSync.shouldTreatGoneAsRead(
                LineReadSync.SentinelSighting.GONE,
                seenSnoozed = true,
            ),
        )
        assertFalse(
            LineReadSync.shouldTreatGoneAsRead(
                LineReadSync.SentinelSighting.SNOOZED_SAME,
                seenSnoozed = true,
            ),
        )
    }

    @Test
    fun probe_only_after_interval_on_unchanged_snoozed_sentinel() {
        assertTrue(LineReadSync.PROBE_ENABLED)
        assertTrue(
            LineReadSync.shouldProbe(
                sighting = LineReadSync.SentinelSighting.SNOOZED_SAME,
                nowElapsed = 60_000L,
                lastProbeElapsed = 0L,
            ),
        )
        assertFalse(
            LineReadSync.shouldProbe(
                sighting = LineReadSync.SentinelSighting.SNOOZED_SAME,
                nowElapsed = LineReadSync.PROBE_INTERVAL_MS - 1L,
                lastProbeElapsed = 0L,
                probeIntervalMs = LineReadSync.PROBE_INTERVAL_MS,
                probeEnabled = true,
            ),
        )
        assertTrue(
            LineReadSync.shouldProbe(
                sighting = LineReadSync.SentinelSighting.SNOOZED_SAME,
                nowElapsed = LineReadSync.PROBE_INTERVAL_MS,
                lastProbeElapsed = 0L,
                probeIntervalMs = LineReadSync.PROBE_INTERVAL_MS,
                probeEnabled = true,
            ),
        )
        assertFalse(
            LineReadSync.shouldProbe(
                sighting = LineReadSync.SentinelSighting.SNOOZED_UPDATED,
                nowElapsed = 10_000L,
                lastProbeElapsed = 0L,
                probeIntervalMs = LineReadSync.PROBE_INTERVAL_MS,
                probeEnabled = true,
            ),
        )
    }

    @Test
    fun probe_duration_is_positive_so_aosp_will_not_no_op() {
        assertTrue(LineReadSync.PROBE_UNSNOOZE_MS > 0L)
        assertTrue(LineReadSync.SENTINEL_SNOOZE_MS > LineReadSync.PROBE_UNSNOOZE_MS)
    }
}
