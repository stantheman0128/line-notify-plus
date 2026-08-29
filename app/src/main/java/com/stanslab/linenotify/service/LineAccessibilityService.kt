package com.stanslab.linenotify.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * 實驗性的 LINE 聊天畫面偵測。
 *
 * 只比對目前尚有 Notify+ 通知的聊天室標題，不保存訊息本文、不模擬手勢或操作 LINE。
 */
class LineAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "LineNotify"
        private const val SCAN_DELAY_MS = 180L
        private const val CONFIRM_DELAY_MS = 220L
        private const val MAX_NODES_PER_SCAN = 512
        private const val MAX_CANDIDATE_TITLES_PER_SCAN = 48
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRoomKey: String? = null
    private var scanScheduled = false

    private val scanRunnable = Runnable {
        scanScheduled = false
        scanForVisibleRoom(confirming = false)
    }

    private val confirmRunnable = Runnable {
        scanForVisibleRoom(confirming = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val sourcePackage = event?.packageName?.toString() ?: return
        if (sourcePackage !in LineNotificationListener.LINE_PACKAGES) return
        if (!featureEnabled()) {
            resetCandidate()
            return
        }
        if (!scanScheduled) {
            scanScheduled = true
            handler.postDelayed(scanRunnable, SCAN_DELAY_MS)
        }
    }

    override fun onInterrupt() {
        resetCandidate()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun featureEnabled(): Boolean {
        val prefs = getSharedPreferences(LineNotificationListener.PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(LineNotificationListener.KEY_SERVICE_ENABLED, true) &&
            prefs.getBoolean(LineNotificationListener.KEY_REPLACE_ORIGINAL, true) &&
            prefs.getBoolean(LineNotificationListener.KEY_CLEAR_AFTER_READ, true) &&
            prefs.getBoolean(LineNotificationListener.KEY_ACCESSIBILITY_READ_SYNC, false)
    }

    private fun scanForVisibleRoom(confirming: Boolean) {
        if (!featureEnabled()) {
            resetCandidate()
            return
        }
        val root = rootInActiveWindow ?: run {
            resetCandidate()
            return
        }
        val packageName = root.packageName?.toString() ?: run {
            resetCandidate()
            return
        }
        if (packageName !in LineNotificationListener.LINE_PACKAGES) {
            resetCandidate()
            return
        }

        val activeRooms = NotificationReadCoordinator.activeRooms(this)
            .filter { it.sourcePackage == packageName }
        if (activeRooms.isEmpty()) {
            resetCandidate()
            return
        }
        val evidence = collectEvidence(root, packageName, activeRooms.mapTo(mutableSetOf()) {
            it.chatTitle
        })
        debugLog(
            "Accessibility 掃描 candidates=${activeRooms.size} " +
                "headerMatches=${evidence.headerTitles.size} " +
                "bottomEditable=${evidence.hasBottomEditable}",
        )
        val roomKey = AccessibilityRoomMatcher.uniqueRoomKey(
            evidence = evidence,
            activeRooms = activeRooms,
            supportedPackages = LineNotificationListener.LINE_PACKAGES,
        ) ?: run {
            resetCandidate()
            return
        }

        if (!confirming && pendingRoomKey == roomKey) {
            // 畫面持續更新時保留既有確認計時，不讓 event storm 永遠把確認往後推。
            return
        }
        if (pendingRoomKey != roomKey) {
            pendingRoomKey = roomKey
            debugLog("Accessibility 候選待二次確認 room=${roomKey.hashCode()}")
            handler.removeCallbacks(confirmRunnable)
            handler.postDelayed(confirmRunnable, CONFIRM_DELAY_MS)
            return
        }

        pendingRoomKey = null
        NotificationReadCoordinator.clearRoom(this, roomKey)
        Log.d(TAG, "Accessibility 確認 LINE 聊天畫面，清除 room=${roomKey.hashCode()}")
    }

    /** 只比較候選聊天室標題；不收集、保存或記錄其他可見文字。 */
    private fun collectEvidence(
        root: AccessibilityNodeInfo,
        packageName: String,
        candidateTitles: Set<String>,
    ): AccessibilityRoomMatcher.ScreenEvidence {
        val rootBounds = Rect().also(root::getBoundsInScreen)
        if (rootBounds.height() <= 0) {
            return AccessibilityRoomMatcher.ScreenEvidence(packageName, emptySet(), false)
        }
        val rootHeight = rootBounds.height()
        val maxHeaderHeight = (resources.displayMetrics.density * 160f).toInt()
        val headerLimit = rootBounds.top + minOf((rootHeight * 0.30f).toInt(), maxHeaderHeight)
        val editableFloor = rootBounds.top + (rootHeight * 0.45f).toInt()
        var hasBottomEditable = false
        var visited = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty() && visited < MAX_NODES_PER_SCAN) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (node.isEditable && bounds.centerY() >= editableFloor) {
                    hasBottomEditable = true
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }

        // 只請 framework 尋找目前 Notify+ 通知中已知的候選聊天室名稱；不逐一讀取其他
        // 畫面文字。候選過多時直接 fail-open，避免在主執行緒做大量跨程序查詢。
        val headerTitles = if (!hasBottomEditable ||
            candidateTitles.size > MAX_CANDIDATE_TITLES_PER_SCAN
        ) {
            emptySet()
        } else {
            candidateTitles
                .asSequence()
                .filter { it.isNotEmpty() }
                .filter { candidateTitle ->
                    root.findAccessibilityNodeInfosByText(candidateTitle).orEmpty().any { node ->
                        if (!node.isVisibleToUser || isInsideScrollableContainer(node)) {
                            return@any false
                        }
                        val bounds = Rect().also(node::getBoundsInScreen)
                        if (bounds.centerY() > headerLimit) return@any false
                        node.text?.toString()?.trim() == candidateTitle ||
                            node.contentDescription?.toString()?.trim() == candidateTitle
                    }
                }
                .toSet()
        }

        return AccessibilityRoomMatcher.ScreenEvidence(
            packageName = packageName,
            headerTitles = headerTitles,
            hasBottomEditable = hasBottomEditable,
        )
    }

    private fun isInsideScrollableContainer(node: AccessibilityNodeInfo): Boolean {
        var ancestor = node.parent
        var depth = 0
        while (ancestor != null && depth < 12) {
            if (ancestor.isScrollable || ancestor.collectionInfo != null) return true
            ancestor = ancestor.parent
            depth++
        }
        return false
    }

    private fun resetCandidate() {
        pendingRoomKey = null
        handler.removeCallbacks(confirmRunnable)
    }

    private fun debugLog(message: String) {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.v(TAG, message)
        }
    }
}
