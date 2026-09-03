package com.stanslab.linenotify.service

/** Android 系統設定頁所需的 LINE 訊息頻道座標。 */
internal data class LineMessageChannelTarget(
    val packageName: String,
    val channelId: String?,
)

/**
 * 集中管理支援的 LINE package 與實機觀察到的訊息頻道。
 *
 * 頻道 ID 不硬猜：LINE 版本或地區版可能不同。沒有觀察紀錄時，設定入口會安全地退回
 * LINE 的 App 通知分類頁，仍由使用者選「訊息提醒」。
 */
internal object LineMessageChannelSettings {
    val knownPackages = listOf(
        "jp.naver.line.android",
        "com.linecorp.line",
    )

    fun isSupportedMessageChannel(channelId: String?): Boolean =
        channelId == "NewMessages" || channelId?.endsWith(".notification.NewMessages") == true

    fun resolveTarget(
        installedPackages: Set<String>,
        rememberedPackage: String?,
        rememberedChannelId: String?,
    ): LineMessageChannelTarget? {
        val rememberedInstalledPackage = rememberedPackage?.takeIf {
            it in knownPackages && it in installedPackages
        }
        val packageName = rememberedInstalledPackage
            ?: knownPackages.firstOrNull { it in installedPackages }
            ?: return null

        val channelId = rememberedChannelId?.takeIf {
            rememberedPackage == packageName && isSupportedMessageChannel(it)
        }

        return LineMessageChannelTarget(packageName, channelId)
    }
}
