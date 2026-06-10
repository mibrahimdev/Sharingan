package dev.sharingan.internal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.sharingan.BleEvent
import dev.sharingan.HttpEvent
import dev.sharingan.MqttEvent
import dev.sharingan.R
import dev.sharingan.Sharingan
import dev.sharingan.SharinganActivity
import dev.sharingan.SharinganEvent
import dev.sharingan.shortLabel
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
                        if (enabled && events.isNotEmpty()) {
                            post(context, events, recording)
                        }
                    }
            }
        }
    }

    private fun post(context: Context, events: List<SharinganEvent>, recording: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(manager)

        val http = events.count { it is HttpEvent }
        val mqtt = events.count { it is MqttEvent }
        val ble = events.count { it is BleEvent }
        val stateLabel = if (recording) "Capturing" else "Paused"
        val ticker = events.takeLast(3).reversed().joinToString("\n") { tickerLine(it) }

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
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID)
            else Notification.Builder(context)
            .setSmallIcon(R.drawable.sharingan_ic_notification)
            .setContentTitle("Sharingan — $stateLabel · ${events.size} events")
            .setContentText("HTTP $http · MQTT $mqtt · BLE $ble")
            .setStyle(Notification.BigTextStyle().bigText("HTTP $http · MQTT $mqtt · BLE $ble\n$ticker"))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (recording) "Pause" else "Resume",
                    toggleIntent,
                ).build(),
            )

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; capture continues silently.
        }
    }

    private fun tickerLine(event: SharinganEvent): String = when (event) {
        is HttpEvent -> "${event.method} ${event.path} → ${event.statusCode ?: "ERR"}"
        is MqttEvent -> "${event.direction.shortLabel} ${event.topic}"
        is BleEvent -> "${event.operation.name} ${event.characteristic ?: event.device}"
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
