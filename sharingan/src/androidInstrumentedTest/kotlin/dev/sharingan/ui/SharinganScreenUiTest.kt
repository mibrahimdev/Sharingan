package dev.sharingan.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.HttpEvent
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import dev.sharingan.SharinganStore
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * On-device UI tests for the full log-browser flow, written against the
 * Compose Multiplatform test API and run as Android instrumented tests:
 *
 * ```
 * ./gradlew :sharingan:connectedDebugAndroidTest
 * ```
 *
 * Each test drives [SharinganScreen] with its own [SharinganStore], so the
 * suite needs no notification permission, no DND state, and no sample app —
 * any connected device or emulator works. (The capture-notification E2E
 * lives in the sample's instrumented tests, since it needs a real app.)
 */
@OptIn(ExperimentalTestApi::class)
internal class SharinganScreenUiTest {

    /** A miniature of the sample's IoT round: 3 HTTP (one 401, one 500), 1 MQTT, 1 BLE. */
    private fun seededStore(): SharinganStore = SharinganStore().apply {
        record(
            HttpEvent(
                id = "h1", timestampMillis = 1_000, method = "GET",
                url = "https://api.acme-iot.com/api/v2/devices/4471/state",
                statusCode = 200, durationMillis = 82,
            ),
        )
        record(
            HttpEvent(
                id = "h2", timestampMillis = 2_000, method = "POST",
                url = "https://api.acme-iot.com/api/v2/auth/refresh",
                statusCode = 401, durationMillis = 178,
            ),
        )
        record(
            HttpEvent(
                id = "h3", timestampMillis = 3_000, method = "GET",
                url = "https://api.acme-iot.com/api/v2/telemetry/4471/stream",
                statusCode = 500, durationMillis = 1037,
                responseBody = """{"error":"upstream_timeout"}""",
            ),
        )
        record(
            MqttEvent(
                id = "m1", timestampMillis = 4_000,
                direction = MqttDirection.PUBLISH, topic = "devices/4471/telemetry", qos = 1,
            ),
        )
        record(
            BleEvent(
                id = "b1", timestampMillis = 5_000,
                operation = BleOperation.NOTIFY, device = "HR-9F",
                characteristic = "Heart Rate Measurement",
            ),
        )
    }

    @Test
    fun homeTab_showsHttpRowsWithStatusAndCount() = runComposeUiTest {
        setContent { SharinganScreen(store = seededStore()) }

        onNodeWithText("3 REQUESTS").assertExists()
        onNodeWithText("/api/v2/devices/4471/state").assertExists()
        onNodeWithText("401").assertExists()
        onNodeWithText("500").assertExists()
    }

    @Test
    fun errorsChip_filtersToFailuresOnly() = runComposeUiTest {
        setContent { SharinganScreen(store = seededStore()) }

        onNodeWithText("Errors").performClick()

        onNodeWithText("2 REQUESTS").assertExists()
        onNodeWithText("/api/v2/devices/4471/state").assertDoesNotExist()
        onNodeWithText("401").assertExists()
        onNodeWithText("500").assertExists()
    }

    @Test
    fun mqttTab_showsDescriptorChipsAndRows() = runComposeUiTest {
        setContent { SharinganScreen(store = seededStore()) }

        onNodeWithText("MQTT").performClick()

        onNodeWithText("1 MESSAGES").assertExists()
        onNodeWithText("Pub").assertExists()
        onNodeWithText("Sub").assertExists()
        onNodeWithText("PUB").assertExists()
        onNodeWithText("devices/4471/telemetry").assertExists()
    }

    @Test
    fun search_narrowsRowsByUrl() = runComposeUiTest {
        setContent { SharinganScreen(store = seededStore()) }

        onNode(hasSetTextAction()).performTextInput("telemetry")

        onNodeWithText("1 REQUESTS").assertExists()
        onNodeWithText("/api/v2/telemetry/4471/stream").assertExists()
        onNodeWithText("/api/v2/auth/refresh").assertDoesNotExist()
    }

    @Test
    fun rowTap_opensDetailWithSummaryAndBody_backReturnsHome() = runComposeUiTest {
        setContent { SharinganScreen(store = seededStore()) }

        onNodeWithText("/api/v2/telemetry/4471/stream").performClick()

        onNodeWithText("SUMMARY").assertExists()
        onNodeWithText("STATUS").assertExists()
        onNodeWithText("upstream_timeout", substring = true).assertExists()

        onNodeWithText("Back").performClick()
        onNodeWithText("3 REQUESTS").assertExists()
    }

    @Test
    fun shareSheet_offersAgentCopyFirstAndCurlForSingleHttp() = runComposeUiTest {
        setContent { SharinganScreen(store = seededStore()) }

        onNodeWithText("/api/v2/auth/refresh").performClick()
        onNodeWithText("Share").performClick()

        onNodeWithText("Share this request").assertExists()
        onNodeWithText("Copy for AI agent").assertExists()
        onNodeWithText("Copy as cURL").assertExists()
        // Preview pane already renders the agent Markdown for the selected event.
        onNodeWithText("## POST /api/v2/auth/refresh", substring = true).assertExists()
    }

    @Test
    fun copyForAgent_showsToastThatAutoDismisses() = runComposeUiTest {
        setContent { SharinganScreen(store = seededStore()) }

        onNodeWithText("/api/v2/auth/refresh").performClick()
        onNodeWithText("Share").performClick()
        onNodeWithText("Copy for AI agent").performClick()

        onNodeWithText("Copied for agent ✓").assertExists()

        mainClock.advanceTimeBy(2_500)
        onNodeWithText("Copied for agent ✓").assertDoesNotExist()
    }

    @Test
    fun recPill_pausesCaptureAndShowsPausedState() = runComposeUiTest {
        val store = seededStore()
        setContent { SharinganScreen(store = store) }

        onNodeWithText("REC").performClick()

        onNodeWithText("PAUSED").assertExists()
        assertFalse(store.isRecording.value)
    }
}
