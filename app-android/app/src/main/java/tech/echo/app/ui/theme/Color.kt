package tech.echo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * echo 黑白极简配色（见 ui-design.md §2.1）。
 * 全 App 只有中性灰阶 + 唯一一抹红（仅录音指示）。
 */

// 中性灰阶
val EchoBlack = Color(0xFF1A1A1A)      // 近黑：主色/主要文字
val EchoWhite = Color(0xFFFFFFFF)      // 纯白：背景
val EchoSurfaceGray = Color(0xFFF5F5F5) // 极浅灰：卡片/分区底色
val EchoTextSecondary = Color(0xFF6B6B6B) // 中灰：辅助说明、时间戳
val EchoOutline = Color(0xFFE0E0E0)    // 浅灰：描边、分隔线

// 唯一一抹红：仅用于录音状态指示圆点
val EchoRecordingRed = Color(0xFFE5392F)
val EchoListeningBlue = Color(0xFF1E88E5)
val EchoSpeakerGreen = Color(0xFF2E7D32)

// 深色模式中性阶
val EchoDarkBg = Color(0xFF121212)
val EchoDarkSurface = Color(0xFF1E1E1E)
val EchoDarkOnSurface = Color(0xFFEDEDED)
val EchoDarkTextSecondary = Color(0xFFA8A8A8)
val EchoDarkOutline = Color(0xFF3A3A3A)
