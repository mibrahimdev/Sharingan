package dev.sharingan

import android.content.Context

/** No-op: the release artifact has no log browser to open. */
public fun Sharingan.show(@Suppress("UNUSED_PARAMETER") context: Context) {}

/** No-op: the release artifact posts no notification. */
public fun Sharingan.setNotificationEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {}
