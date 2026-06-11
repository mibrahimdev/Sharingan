package dev.sharingan.sample

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.sharingan.Sharingan
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E for the capture notification, run against the real sample app:
 *
 * ```
 * ./gradlew :sample:composeApp:connectedDebugAndroidTest
 * ```
 *
 * Verifies the whole zero-setup chain — manifest-merged ContentProvider init →
 * store observer → notification content — by reading the app's own
 * `activeNotifications`. That works regardless of Do Not Disturb (which hides
 * the silent notification from the shade but not from the posting app).
 */
@RunWith(AndroidJUnit4::class)
class CaptureNotificationE2eTest {

    // POST_NOTIFICATIONS exists only on API 33+; below that the grant list is empty.
    @get:Rule
    val grantNotifications: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) {
            GrantPermissionRule.grant("android.permission.POST_NOTIFICATIONS")
        } else {
            GrantPermissionRule.grant()
        }

    @Test
    fun e2e_captureNotificationPostsCountersAndTogglesPausedState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // The sample auto-fires a demo round on launch; first event posts.
            val capturing = awaitNotification(titlePrefix = "Sharingan — Capturing")
            assertNotNull("capture notification was never posted", capturing)

            val text = capturing!!.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            assertTrue(
                "counters line malformed: $text",
                text.matches(Regex("""HTTP \d+ · MQTT \d+ · BLE \d+""")),
            )
            val bigText = capturing.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
            assertTrue("expanded ticker missing: $bigText", bigText.lines().size >= 2)

            try {
                Sharingan.setRecording(false)
                assertNotNull(
                    "notification never switched to Paused",
                    awaitNotification(titlePrefix = "Sharingan — Paused"),
                )
            } finally {
                Sharingan.setRecording(true)
            }
        }
    }

    private fun awaitNotification(
        titlePrefix: String,
        timeoutMillis: Long = 15_000,
    ): StatusBarNotification? {
        val manager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            manager.activeNotifications.firstOrNull { posted ->
                posted.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
                    ?.toString()?.startsWith(titlePrefix) == true
            }?.let { return it }
            Thread.sleep(250)
        }
        return null
    }
}
