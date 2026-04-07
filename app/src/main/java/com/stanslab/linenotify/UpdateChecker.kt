package com.stanslab.linenotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val TAG = "LineNotify"
    private const val GITHUB_API = "https://api.github.com/repos/stantheman0128/line-notify-plus/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,  // GitHub release 頁面
        val apkUrl: String?,      // 直接 APK 下載連結
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

                val tagName = extractJsonString(response, "tag_name") ?: return@withContext null
                val htmlUrl = extractJsonString(response, "html_url") ?: return@withContext null

                // 提取 APK 下載連結（從 assets 中找 .apk 結尾的）
                val apkUrl = extractApkUrl(response)

                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) { "0" }

                val latestClean = tagName.removePrefix("v")
                val hasUpdate = isNewer(latestClean, currentVersion ?: "0")

                Log.d(TAG, "版本檢查: 目前=$currentVersion 最新=$latestClean 需更新=$hasUpdate apk=$apkUrl")

                UpdateInfo(
                    latestVersion = latestClean,
                    downloadUrl = htmlUrl,
                    apkUrl = apkUrl,
                    hasUpdate = hasUpdate
                )
            } catch (e: Exception) {
                Log.w(TAG, "檢查更新失敗: ${e.message}")
                null
            }
        }
    }

    /**
     * 下載 APK 並觸發安裝
     */
    suspend fun downloadAndInstall(context: Context, apkUrl: String, onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val updateDir = File(context.cacheDir, "updates")
                updateDir.mkdirs()
                val apkFile = File(updateDir, "update.apk")

                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                val totalSize = conn.contentLength
                var downloaded = 0

                conn.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalSize > 0) {
                                onProgress((downloaded * 100 / totalSize))
                            }
                        }
                    }
                }
                conn.disconnect()

                Log.d(TAG, "APK 下載完成: ${apkFile.length()} bytes")

                // 觸發安裝
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(installIntent)
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "下載更新失敗: ${e.message}")
                false
            }
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val match = Regex(pattern).find(json)
        return match?.groupValues?.get(1)
    }

    private fun extractApkUrl(json: String): String? {
        // 找 browser_download_url 中以 .apk 結尾的
        val pattern = "\"browser_download_url\"\\s*:\\s*\"([^\"]*\\.apk)\""
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
