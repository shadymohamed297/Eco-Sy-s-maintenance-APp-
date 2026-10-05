package com.markazsayana.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MarkazSayanaColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = CardWhite,
    primaryContainer = BrandNavyChipBg,
    onPrimaryContainer = BrandNavy,
    secondary = AccentOrange,
    onSecondary = BrandNavyDark,
    secondaryContainer = OrangeChipBg,
    onSecondaryContainer = OrangeTextDark,
    tertiary = SuccessGreen,
    background = SurfacePage,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceScreen,
    onSurfaceVariant = TextSecondary,
    outline = BorderMedium,
    outlineVariant = Divider,
    error = UrgentRed,
    onError = CardWhite,
)

/** Standard 16px screen padding used throughout the design. */
val ScreenPadding = 16.dp

@Composable
fun MarkazSayanaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MarkazSayanaColorScheme,
        typography = MaterialTheme.typography.copy(
            bodyLarge = Body15,
            bodyMedium = Body14,
            bodySmall = Caption13,
            titleLarge = Title22Bold,
            titleMedium = Title18Bold,
            titleSmall = Body15SemiBold,
            labelLarge = Body14SemiBold,
            labelMedium = Caption12SemiBold,
            labelSmall = Micro11,
        ),
        shapes = AppShapes,
        content = content,
    )
}
