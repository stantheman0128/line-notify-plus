package com.stanslab.linenotify.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySentinelMigrationTest {
    private val marker = LegacySentinelMigration.Marker(
        fingerprint = "old-payload",
        expiresAtMillis = 10_000L,
    )

    @Test
    fun matching_old_repost_is_discarded_during_window() {
        assertTrue(
            LegacySentinelMigration.shouldDiscardRepost(marker, "old-payload", 9_000L),
        )
    }

    @Test
    fun changed_payload_is_treated_as_a_real_new_message() {
        assertFalse(
            LegacySentinelMigration.shouldDiscardRepost(marker, "new-payload", 9_000L),
        )
        assertTrue(
            LegacySentinelMigration.shouldRemoveMarker(marker, "new-payload", 9_000L),
        )
    }

    @Test
    fun expired_marker_never_swallows_a_new_message() {
        assertFalse(
            LegacySentinelMigration.shouldDiscardRepost(marker, "old-payload", 10_001L),
        )
        assertTrue(
            LegacySentinelMigration.shouldRemoveMarker(marker, "old-payload", 10_001L),
        )
    }
}
