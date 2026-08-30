package com.banglu.keyboard

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

// S147: every :ui screen renders with the bundled Noto Sans Bengali by
// default (the mock's --sans); display serif/mono are opted into per-Text.
private val bangluTypography: Typography by lazy {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = BanglaSans),
        displayMedium = base.displayMedium.copy(fontFamily = BanglaSans),
        displaySmall = base.displaySmall.copy(fontFamily = BanglaSans),
        headlineLarge = base.headlineLarge.copy(fontFamily = BanglaSans),
        headlineMedium = base.headlineMedium.copy(fontFamily = BanglaSans),
        headlineSmall = base.headlineSmall.copy(fontFamily = BanglaSans),
        titleLarge = base.titleLarge.copy(fontFamily = BanglaSans),
        titleMedium = base.titleMedium.copy(fontFamily = BanglaSans),
        titleSmall = base.titleSmall.copy(fontFamily = BanglaSans),
        bodyLarge = base.bodyLarge.copy(fontFamily = BanglaSans),
        bodyMedium = base.bodyMedium.copy(fontFamily = BanglaSans),
        bodySmall = base.bodySmall.copy(fontFamily = BanglaSans),
        labelLarge = base.labelLarge.copy(fontFamily = BanglaSans),
        labelMedium = base.labelMedium.copy(fontFamily = BanglaSans),
        labelSmall = base.labelSmall.copy(fontFamily = BanglaSans)
    )
}

fun ComponentActivity.setBangluContent(content: @Composable () -> Unit) {
    val composeView = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme(typography = bangluTypography) {
                content()
            }
        }
    }
    setContentView(composeView)
}
