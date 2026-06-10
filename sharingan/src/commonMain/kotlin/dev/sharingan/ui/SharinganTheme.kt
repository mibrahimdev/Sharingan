package dev.sharingan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Sharingan's design tokens, lifted verbatim from the design handoff.
 * Resolved once per theme; read via [LocalSharinganColors].
 */
@Immutable
internal data class SharinganColors(
    val isDark: Boolean,
    val bg: Color,
    val bgElev: Color,
    val surface: Color,
    val surface2: Color,
    val hover: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMid: Color,
    val textDim: Color,
    val faint: Color,
    val accent: Color,
    val accentSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val err: Color,
    val errSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val violet: Color,
    val violetSoft: Color,
)

internal val SharinganLightColors = SharinganColors(
    isDark = false,
    bg = Color(0xFFF4F5F7),
    bgElev = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1F3F5),
    hover = Color(0xFFEAEDF0),
    border = Color(0x170F141E),
    borderStrong = Color(0x290F141E),
    text = Color(0xFF16191D),
    textMid = Color(0xFF565C64),
    textDim = Color(0xFF8A909A),
    faint = Color(0x090F141E),
    accent = Color(0xFFD6322A),
    accentSoft = Color(0x1AD6322A),
    ok = Color(0xFF1F9D57),
    okSoft = Color(0x1F1F9D57),
    warn = Color(0xFFB97B0E),
    warnSoft = Color(0x1FB97B0E),
    err = Color(0xFFD6322A),
    errSoft = Color(0x1AD6322A),
    info = Color(0xFF2563EB),
    infoSoft = Color(0x1A2563EB),
    violet = Color(0xFF7C5CE0),
    violetSoft = Color(0x1F7C5CE0),
)

internal val SharinganDarkColors = SharinganColors(
    isDark = true,
    bg = Color(0xFF0E0F13),
    bgElev = Color(0xFF15171C),
    surface = Color(0xFF181B21),
    surface2 = Color(0xFF1F232B),
    hover = Color(0xFF242933),
    border = Color(0x14FFFFFF),
    borderStrong = Color(0x24FFFFFF),
    text = Color(0xFFE8EAED),
    textMid = Color(0xFFA6ACB5),
    textDim = Color(0xFF6B7280),
    faint = Color(0x0AFFFFFF),
    accent = Color(0xFFE5342B),
    accentSoft = Color(0x29E5342B),
    ok = Color(0xFF3DD68C),
    okSoft = Color(0x243DD68C),
    warn = Color(0xFFE0A33B),
    warnSoft = Color(0x24E0A33B),
    err = Color(0xFFFF5D55),
    errSoft = Color(0x26FF5D55),
    info = Color(0xFF5AA9FF),
    infoSoft = Color(0x245AA9FF),
    violet = Color(0xFFA78BFA),
    violetSoft = Color(0x26A78BFA),
)

internal val LocalSharinganColors = staticCompositionLocalOf { SharinganLightColors }

/**
 * The terminal aesthetic uses the platform's monospaced face instead of
 * bundling IBM Plex (~200 KB per weight) — a deliberate minimal-size call.
 */
internal val MonoFont: FontFamily = FontFamily.Monospace
internal val SansFont: FontFamily = FontFamily.Default

/** Provides Sharingan tokens plus a matching Material 3 scheme (sheets, ripples). */
@Composable
internal fun SharinganTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) SharinganDarkColors else SharinganLightColors
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.bg,
            surface = colors.bgElev,
            surfaceContainerLow = colors.bgElev,
            onSurface = colors.text,
            onBackground = colors.text,
            outline = colors.borderStrong,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.bg,
            surface = colors.bgElev,
            surfaceContainerLow = colors.bgElev,
            onSurface = colors.text,
            onBackground = colors.text,
            outline = colors.borderStrong,
        )
    }
    CompositionLocalProvider(LocalSharinganColors provides colors) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
