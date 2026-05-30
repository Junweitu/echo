package tech.echo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * echo 黑白极简主题（见 ui-design.md §2）。
 * 不用动态取色，固定一套中性灰阶；录音红通过 EchoRecordingRed 单独引用，不进 colorScheme。
 */

private val LightColors = lightColorScheme(
    primary = EchoBlack,
    onPrimary = EchoWhite,
    background = EchoWhite,
    onBackground = EchoBlack,
    surface = EchoWhite,
    onSurface = EchoBlack,
    surfaceVariant = EchoSurfaceGray,
    onSurfaceVariant = EchoTextSecondary,
    outline = EchoOutline,
    outlineVariant = EchoOutline,
)

private val DarkColors = darkColorScheme(
    primary = EchoWhite,
    onPrimary = EchoBlack,
    background = EchoDarkBg,
    onBackground = EchoDarkOnSurface,
    surface = EchoDarkSurface,
    onSurface = EchoDarkOnSurface,
    surfaceVariant = EchoDarkSurface,
    onSurfaceVariant = EchoDarkTextSecondary,
    outline = EchoDarkOutline,
    outlineVariant = EchoDarkOutline,
)

/**
 * echo 间距规格（见 ui-design.md §2.3）。8dp 栅格，只用这几档。
 */
object EchoSpacing {
    val pageHorizontal = 24.dp
    val cardPadding = 16.dp
    val sectionGap = 24.dp
    val elementGap = 12.dp
    val elementGapSmall = 8.dp
    val listItemMinHeight = 56.dp
}

/** 统一圆角（ui-design.md §2.4）。 */
object EchoShape {
    val corner = 12.dp
}

@Composable
fun EchoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EchoTypography,
        content = content,
    )
}
