package com.stanslab.linenotify.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationDismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLEAR_BUFFER = "com.stanslab.linenotify.CLEAR_BUFFER"
        const val ACTION_DISMISS_GROUP = "com.stanslab.linenotify.DISMISS_GROUP"
        const val EXTRA_CHAT_TITLE = "chat_title"
        const val EXTRA_GROUP_KEY = "group_key"
        private const val TAG = "LineNotify"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CLEAR_BUFFER -> {
                val chatTitle = intent.getStringExtra(EXTRA_CHAT_TITLE) ?: return
                // 通知被滑掉或自動消失時，清除訊息緩衝
                // 這樣下次新訊息不會帶著舊訊息一起出現
                Log.d(TAG, "清除 [$chatTitle] 緩衝區 (通知已消失)")
                // 透過 SharedPreferences 標記需要清除
                val prefs = context.getSharedPreferences(
                    LineNotificationListener.PREFS_NAME, Context.MODE_PRIVATE
                )
                val cleared = prefs.getStringSet("cleared_chats", mutableSetOf())?.toMutableSet()
                    ?: mutableSetOf()
                cleared.add(chatTitle)
                prefs.edit().putStringSet("cleared_chats", cleared).apply()
            }
            ACTION_DISMISS_GROUP -> {
                val groupKey = intent.getStringExtra(EXTRA_GROUP_KEY) ?: return
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                var count = 0
                manager.activeNotifications.forEach { sbn ->
                    if (sbn.notification.group == groupKey) {
                        manager.cancel(sbn.id)
                        count++
                    }
                }
                Log.d(TAG, "清除 group=$groupKey 共 $count 則通知")
            }
        }
    }
}
