package com.paperly.mobile.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 纸间设计令牌（与 app\设计\界面重设计方案.md 第 3 节一致）。
 * 主色 = 松石绿（做题 / 自己），AI 色 = 琥珀（辅助 / AI），其余为墨色与纸色灰阶。
 */
@Immutable
data class PaperColors(
    val paper: Color,        // 最底层背景
    val surface: Color,      // 卡片
    val surface2: Color,     // 次级面（侧栏、缩略图栏）
    val desk: Color,         // 阅读器“桌面”
    val sheet: Color,        // 纸张本身
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val line: Color,
    val line2: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val ai: Color,
    val aiSoft: Color,
    val danger: Color,
    val chip: Color,         // 选中的深色胶囊（工具选中、筛选选中）
    val onChip: Color,
    val dark: Boolean,
)

private val LightPaper = PaperColors(
    paper = Color(0xFFF5F3ED), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFFAF9F5), desk = Color(0xFFECEAE3), sheet = Color(0xFFFFFEFB),
    ink = Color(0xFF1D2926), ink2 = Color(0xFF4A5752), muted = Color(0xFF86928D), line = Color(0xFFE5E7E0), line2 = Color(0xFFEEF0EA),
    accent = Color(0xFF2F6B52), accentSoft = Color(0xFFE7F0EA), onAccent = Color.White,
    ai = Color(0xFFB8733A), aiSoft = Color(0xFFFBF1E6), danger = Color(0xFFC0473B),
    chip = Color(0xFF1D2926), onChip = Color.White, dark = false,
)

private val DarkPaper = PaperColors(
    paper = Color(0xFF1B1D1C), surface = Color(0xFF242726), surface2 = Color(0xFF202322), desk = Color(0xFF161817), sheet = Color(0xFFF3F1EA),
    ink = Color(0xFFE8E6E0), ink2 = Color(0xFFC3C6C0), muted = Color(0xFF8D9691), line = Color(0xFF343836), line2 = Color(0xFF2C302E),
    accent = Color(0xFF79BC9C), accentSoft = Color(0xFF2A3B33), onAccent = Color(0xFF0E241A),
    ai = Color(0xFFE2A56E), aiSoft = Color(0xFF3A2E22), danger = Color(0xFFE58A7F),
    chip = Color(0xFFE8E6E0), onChip = Color(0xFF1B1D1C), dark = true,
)

val LocalPaper = staticCompositionLocalOf { LightPaper }

/** 在任意 Composable 中用 `Paper.c.accent` 读取令牌。 */
object Paper {
    val c: PaperColors @Composable get() = LocalPaper.current
}

private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.w(size: Int, weight: FontWeight, line: Int, ls: Float = 0f) =
        copy(fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = ls.sp)
    return base.copy(
        displaySmall = base.displaySmall.w(40, FontWeight.SemiBold, 46, 0.5f),
        headlineMedium = base.headlineMedium.w(25, FontWeight.SemiBold, 32, 0.2f),
        headlineSmall = base.headlineSmall.w(21, FontWeight.SemiBold, 28),
        titleLarge = base.titleLarge.w(18, FontWeight.SemiBold, 25),
        titleMedium = base.titleMedium.w(15, FontWeight.SemiBold, 21),
        titleSmall = base.titleSmall.w(14, FontWeight.SemiBold, 19),
        bodyLarge = base.bodyLarge.w(15, FontWeight.Normal, 23),
        bodyMedium = base.bodyMedium.w(14, FontWeight.Normal, 22),
        bodySmall = base.bodySmall.w(12, FontWeight.Normal, 18),
        labelLarge = base.labelLarge.w(13, FontWeight.SemiBold, 18),
        labelMedium = base.labelMedium.w(12, FontWeight.Medium, 16),
        labelSmall = base.labelSmall.w(11, FontWeight.Medium, 14, 0.3f),
    )
}

@Composable
fun PaperlyTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkPaper else LightPaper
    // 把令牌映射到 M3 配色，让对话框、输入框、开关等系统组件也统一风格
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.accent,
        secondary = p.ink2, secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink,
        tertiary = p.ai, tertiaryContainer = p.aiSoft,
        background = p.paper, onBackground = p.ink, surface = p.paper, onSurface = p.ink, onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface2, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, surfaceContainerHighest = p.line2,
        outline = p.muted, outlineVariant = p.line, error = p.danger,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.accent,
        secondary = p.ink2, secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink,
        tertiary = p.ai, tertiaryContainer = p.aiSoft,
        background = p.paper, onBackground = p.ink, surface = p.paper, onSurface = p.ink, onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface2, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, surfaceContainerHighest = p.line2,
        outline = p.muted, outlineVariant = p.line, error = p.danger,
    )
    CompositionLocalProvider(LocalPaper provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography(), shapes = PaperShapes, content = content)
    }
}
