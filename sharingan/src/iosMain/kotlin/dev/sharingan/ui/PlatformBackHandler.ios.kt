package dev.sharingan.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back affordance on iOS; the in-UI Back button handles it.
}
