package com.stanslab.linenotify.service

/** Android 系統設定頁所需的 LINE 訊息頻道座標。 */
internal data class LineMessageChannelTarget(
    val packageName: String,
    val channelId: String?,
)

/**
 * 集中管理支援的 LINE package 與訊息頻道，避免 listener、分類器和設定頁各自漂移。
 *
 * 這裡刻意維持純 Kotlin，讓座標選擇可以用 JVM 單元測試驗證。
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
