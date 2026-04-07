package com.stanslab.linenotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val TAG = "LineNotify"
    private const val GITHUB_API = "https://api.github.com/repos/stantheman0128/line-notify-plus/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val hasUpdate: Boolean
    )

    suspend fun checkForUpdate(context: Context): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode != 200) return@withContext null

                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                // 簡單 JSON 解析（避免加 dependency）
                val tagName = extractJsonString(response, "tag_name") ?: return@withContext null
                val htmlUrl = extractJsonString(response, "html_url") ?: return@withContext null

                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) { "0" }

                val latestClean = tagName.removePrefix("v")
                val hasUpdate = isNewer(latestClean, currentVersion ?: "0")

                Log.d(TAG, "版本檢查: 目前=$currentVersion 最新=$latestClean 需更新=$hasUpdate")

                UpdateInfo(
                    latestVersion = latestClean,
                    downloadUrl = htmlUrl,
                    hasUpdate = hasUpdate
                )
            } catch (e: Exception) {
                Log.w(TAG, "檢查更新失敗: ${e.message}")
                null
            }
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val match = Regex(pattern).find(json)
        return match?.groupValues?.get(1)
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun openDownloadPage(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
