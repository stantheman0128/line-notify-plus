package com.stanslab.linenotify.service

/** 純邏輯：判斷舊版 snooze 被釋放後的 callback 是否只是舊通知回放。 */
object LegacySentinelMigration {
    data class Marker(
        val fingerprint: String,
        val expiresAtMillis: Long,
    )

    fun shouldDiscardRepost(
        marker: Marker?,
        currentFingerprint: String,
        nowMillis: Long,
    ): Boolean = marker != null &&
        nowMillis <= marker.expiresAtMillis &&
        marker.fingerprint == currentFingerprint

    fun shouldRemoveMarker(
        marker: Marker,
        currentFingerprint: String,
        nowMillis: Long,
    ): Boolean = nowMillis > marker.expiresAtMillis ||
        marker.fingerprint != currentFingerprint
}
