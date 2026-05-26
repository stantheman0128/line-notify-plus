package com.stanslab.linenotify.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput
import androidx.core.content.IntentCompat

/**
 * 攔截用戶在 LINE Notify+ 通知上的快速回覆。
 *
 * 我們的通知直接綁 LINE 的 reply PendingIntent 時，回覆送出後 LINE 不會、
 * 也無法去更新／取消「我們」這則通知，系統的回覆 spinner 就會一直轉。
 * 這個 receiver 先攔下回覆文字、轉發給 LINE，再取消我們自己的通知。
 */
class ReplyRelayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val resultKey = intent.getStringExtra(EXTRA_RESULT_KEY) ?: return
        val lineActionIntent = IntentCompat.getParcelableExtra(
            intent, EXTRA_LINE_PENDING_INTENT, PendingIntent::class.java
        ) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(resultKey)
        if (replyText.isNullOrEmpty()) return

        // 把回覆文字填回 LINE 期望的 RemoteInput，再送出 LINE 的 PendingIntent
        val fillIn = Intent()
        val results = Bundle().apply { putCharSequence(resultKey, replyText) }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(resultKey).build()),
            fillIn,
            results
        )
        try {
            lineActionIntent.send(context, 0, fillIn)
            Log.d(TAG, "回覆已轉發給 LINE")
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "LINE 回覆 PendingIntent 已失效", e)
        }

        // 取消我們自己的通知 → 結束系統的回覆 spinner
        if (notifId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        }
    }

    companion object {
        private const val TAG = "LineNotify"
        const val ACTION_REPLY = "com.stanslab.linenotify.ACTION_REPLY"
        const val EXTRA_RESULT_KEY = "result_key"
        const val EXTRA_LINE_PENDING_INTENT = "line_pending_intent"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
