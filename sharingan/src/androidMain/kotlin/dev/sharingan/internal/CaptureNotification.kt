package dev.sharingan.internal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.sharingan.R
import dev.sharingan.Sharingan
import dev.sharingan.SharinganActivity
import dev.sharingan.SharinganEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * The design's sticky capture card, as an Android ongoing notification:
 * per-protocol counters in the collapsed line, a three-event ticker when
 * expanded, a Pause/Resume action, and tap-to-open. Silent, no sound or
 * vibration, and updated in place.
 */
internal object CaptureNotification {

    private const val CHANNEL_ID = "sharingan.capture"
    private const val NOTIFICATION_ID = 0x5EE1
    private var scope: CoroutineScope? = null

    @Volatile
    private var enabled: Boolean = true

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) SharinganAndroid.appContext?.let(::cancel)
    }

    /** Starts observing the store; first event posts the notification. */
    fun start(context: Context) {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { observer ->
            observer.launch {
                Sharingan.store.events
                    .combine(Sharingan.store.isRecording) { events, recording -> events to recording }
                    .conflate()
                    .collect { (events, recording) ->
                        if (enabled) post(context, events, recording)
                    }
            }
        }
    }

    private fun post(context: Context, events: List<SharinganEvent>, recording: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        val content = notificationContentOf(events, recording) ?: return
        ensureChannel(manager)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SharinganActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, SharinganNotificationReceiver::class.java)
                .setAction(SharinganNotificationReceiver.ACTION_TOGGLE_RECORDING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        @Suppress("DEPRECATION")
        val base =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID)
            else Notification.Builder(context)
        val builder = base
            .setSmallIcon(R.drawable.sharingan_ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.countsLine)
            .setStyle(Notification.BigTextStyle().bigText(content.expandedText))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(null, content.actionLabel, toggleIntent).build(),
            )

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {
            // Missing POST_NOTIFICATIONS or any notification failure must never
            // crash the host app; capture continues silently.
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sharingan capture", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live HTTP · MQTT · Bluetooth capture status"
                setShowBadge(false)
            },
        )
    }

    private fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}

/** Handles the notification's Pause/Resume action. */
public class SharinganNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_RECORDING) {
            Sharingan.setRecording(!Sharingan.isRecording.value)
        }
    }

    public companion object {
        internal const val ACTION_TOGGLE_RECORDING: String = "dev.sharingan.TOGGLE_RECORDING"
    }
}
