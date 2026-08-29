package com.stanslab.linenotify.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌四階綠。括號內為對白底的對比值。
val BrandGreen = Color(0xFF06C755) // 標誌、狀態點。裝飾用，不承載文字
val ActionGreen = Color(0xFF05A847) // 開關軌道、選取邊框（3.1:1，達 UI 元件門檻）
val FillGreen = Color(0xFF05873C) // 實心按鈕底，白字（4.6:1，過 AA）
val InkGreen = Color(0xFF04672C) // 白底上的綠色文字（7.0:1）
val DarkGreen40 = Color(0xFF3DDC84) // 深色模式用，#06C755 在深底上會顯髒

val Green80 = Color(0xFFA8DAB5)
val Green40 = BrandGreen
val Green30 = ActionGreen

val GreenGrey80 = Color(0xFFBCC8BF)
val GreenGrey40 = Color(0xFF5D6B60)

val DarkGreen = Color(0xFF1B3A21)
val LightGreen = Color(0xFFF0F9F2)

val LightBackground = Color(0xFFFAFBFC)
val LightOutline = Color(0xFFE4E6EC)
val LightMuted = Color(0xFFEEF0F4)
val AmberLegacy = Color(0xFFB45309)
val AmberLegacyDark = Color(0xFFFBBF24)

val DarkBackground = Color(0xFF0F1115)
val DarkSurface = Color(0xFF191C22)
val DarkSurfaceVariant = Color(0xFF252A33)
val DarkOutline = Color(0xFF3A404C)

@Composable
fun inkGreen(): Color = if (isSystemInDarkTheme()) DarkGreen40 else InkGreen

@Composable
fun legacyAmber(): Color = if (isSystemInDarkTheme()) AmberLegacyDark else AmberLegacy
