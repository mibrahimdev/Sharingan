package dev.sharingan

import android.content.Context
import android.content.Intent
import dev.sharingan.internal.CaptureNotification

/**
 * Opens the Sharingan log browser.
 *
 * Usually unnecessary — tapping the capture notification gets users there —
 * but handy behind a debug-drawer button or shake gesture.
 */
public fun Sharingan.show(context: Context) {
    context.startActivity(
        Intent(context, SharinganActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Enables or disables the capture notification (enabled by default).
 *
 * The notification appears once the first event is captured. On Android 13+
 * the host app must hold the `POST_NOTIFICATIONS` runtime permission for it
 * to be visible; Sharingan declares the permission but never requests it.
 */
public fun Sharingan.setNotificationEnabled(enabled: Boolean) {
    CaptureNotification.setEnabled(enabled)
}
