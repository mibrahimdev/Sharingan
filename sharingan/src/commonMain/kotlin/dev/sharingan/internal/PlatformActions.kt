package dev.sharingan.internal

/** Puts [text] on the system clipboard. */
internal expect fun copyToClipboard(text: String)

/** Opens the platform share UI (Android chooser / iOS activity controller). */
internal expect fun shareText(text: String)
