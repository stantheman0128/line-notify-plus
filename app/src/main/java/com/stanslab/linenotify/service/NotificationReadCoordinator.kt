package com.stanslab.linenotify.service

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/** 讓 AccessibilityService 與 NotificationListener 共用同一套聊天室清除入口。 */
object NotificationReadCoordinator {
    private const val TAG = "LineNotify"

    fun activeRooms(context: Context): List<AccessibilityRoomMatcher.ActiveRoom> {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return runCatching {
            manager.activeNotifications
                .mapNotNull { sbn ->
                    val extras = sbn.notification.extras
                    val roomKey = extras.getString(LineNotificationListener.EXTRA_ROOM_KEY)
                        ?: return@mapNotNull null
                    val chatTitle = extras.getString(LineNotificationListener.EXTRA_CHAT_TITLE)
                        ?: return@mapNotNull null
                    val profileKey = extras.getString(LineNotificationListener.EXTRA_PROFILE_KEY)
                        ?: return@mapNotNull null
                    val sourcePackage = profileKey.substringBeforeLast('@', missingDelimiterValue = "")
                    if (sourcePackage !in LineNotificationListener.LINE_PACKAGES) {
                        return@mapNotNull null
                    }
                    AccessibilityRoomMatcher.ActiveRoom(roomKey, chatTitle, sourcePackage)
                }
                .distinctBy { it.roomKey }
        }.getOrElse {
            Log.w(TAG, "Accessibility 無法查詢 Notify+ 作用中通知；本次不清除", it)
            emptyList()
        }
    }

    fun clearRoom(context: Context, roomKey: String) {
        LineNotificationListener.instance?.clearChatGroupFromAccessibility(roomKey) ?: run {
            // Listener process 若已重建，記憶體 buffer 也不存在；此時直接清掉仍可由 extras 找到的
            // 自有通知即可。若查詢失敗則 fail-open，保留通知。
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching {
                manager.activeNotifications
                    .filter {
                        it.notification.extras.getString(LineNotificationListener.EXTRA_ROOM_KEY) ==
                            roomKey
                    }
                    .forEach { sbn ->
                        if (sbn.tag == null) manager.cancel(sbn.id)
                        else manager.cancel(sbn.tag, sbn.id)
                    }
            }.onFailure {
                Log.w(TAG, "Accessibility 無法清除 Notify+ 通知；保留現況", it)
            }
        }
    }
}
