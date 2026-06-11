package com.banglu.keyboard

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

fun ComponentActivity.setBangluContent(content: @Composable () -> Unit) {
    val composeView = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent(content)
    }
    setContentView(composeView)
}
