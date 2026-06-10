package dev.sharingan.ui

import androidx.compose.runtime.Composable

/**
 * System back integration: on Android the hardware/gesture back pops the
 * detail screen; on iOS the in-UI Back button is the platform-conventional
 * path, so this is a no-op there.
 */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
