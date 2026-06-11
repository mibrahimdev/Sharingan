package dev.sharingan.sample

import dev.sharingan.Sharingan
import dev.sharingan.ktor.SharinganKtor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay

/**
 * Deterministic demo traffic mirroring the design's IoT scenario — works
 * offline thanks to MockEngine, while every exchange flows through the real
 * SharinganKtor plugin exactly like production traffic would.
 */
object DemoTraffic {

    private val client = HttpClient(
        MockEngine { request ->
            delay((40..220).random().toLong())
            when {
                request.url.encodedPath.endsWith("/auth/refresh") -> respond(
                    """{"error":"invalid_token","message":"Refresh token expired","code":"AUTH_017"}""",
                    HttpStatusCode.Unauthorized,
                    jsonHeaders(),
                )
                request.url.encodedPath.endsWith("/stream") -> {
                    delay(900)
                    respond(
                        """{"error":"upstream_timeout","message":"Telemetry shard 3 did not respond","traceId":"e0c2-7741"}""",
                        HttpStatusCode.InternalServerError,
                        jsonHeaders(),
                    )
                }
                request.url.encodedPath.endsWith("/commands") -> respond(
                    """{"accepted":true,"commandId":9921,"queuedAt":"2026-06-10T12:04:33Z"}""",
                    HttpStatusCode.Accepted,
                    jsonHeaders(),
                )
                request.method.value == "DELETE" -> respond("", HttpStatusCode.NoContent)
                else -> respond(
                    """{"deviceId":4471,"online":true,"firmware":"2.4.1","battery":0.86,"sensors":{"temp":23.4,"humidity":48}}""",
                    HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/json; charset=utf-8"),
                        "X-Trace-Id" to listOf("b1f4-22a9"),
                        HttpHeaders.CacheControl to listOf("no-store"),
                    ),
                )
            }
        },
    ) {
        install(SharinganKtor)
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private var beat = 0

    /** One round of mixed traffic: 5 HTTP calls, MQTT chatter, BLE heart-rate. */
    suspend fun runRound() {
        // HTTP — the design's device-state flow, including its two failures.
        runCatching {
            client.get("https://api.acme-iot.com/api/v2/devices/4471/state") {
                header("Authorization", "Bearer 9f3a-secret")
                header("Accept", "application/json")
                header("X-Device-Id", "4471")
            }
        }
        runCatching {
            client.post("https://api.acme-iot.com/api/v2/devices/4471/commands") {
                header("Authorization", "Bearer 9f3a-secret")
                setBody(TextContent("""{"cmd":"reboot","delay":5}""", ContentType.Application.Json))
            }
        }
        runCatching {
            client.post("https://auth.acme-iot.com/api/v2/auth/refresh") {
                setBody(TextContent("""{"refresh":"••••"}""", ContentType.Application.Json))
            }
        }
        runCatching { client.get("https://api.acme-iot.com/api/v2/telemetry/4471/stream") }
        runCatching {
            client.delete("https://api.acme-iot.com/api/v2/devices/4471/sessions/77") {
                header("Authorization", "Bearer 9f3a-secret")
            }
        }

        // MQTT — telemetry heartbeat, command subscription, one broker failure.
        Sharingan.mqtt.subscribed("devices/4471/commands/#", qos = 1)
        Sharingan.mqtt.publish(
            topic = "devices/4471/telemetry",
            payload = """{"temp":${23 + beat % 3}.${4 + beat % 5},"hum":${47 + beat % 3},"ts":${1749556800 + beat}}""",
            qos = 1,
        )
        Sharingan.mqtt.received(
            topic = "devices/4471/commands/reboot",
            payload = """{"cmd":"reboot","delay":5,"by":"console"}""",
            qos = 1,
        )
        Sharingan.mqtt.publish(
            topic = "devices/4471/ack",
            payload = """{"ack":9921,"ok":false,"err":"busy"}""",
            qos = 1,
            error = "broker rejected: busy",
        )

        // BLE — the design's heart-rate monitor session.
        if (beat == 0) {
            Sharingan.ble.connect(device = "HR-Monitor-9F")
            Sharingan.ble.discover(
                device = "HR-Monitor-9F",
                service = "Heart Rate Service",
                uuid = "0x180D",
                detail = """{"characteristics":["0x2A37","0x2A38","0x2A39"]}""",
            )
        }
        Sharingan.ble.notify(
            device = "HR-Monitor-9F",
            characteristic = "Heart Rate Measurement",
            uuid = "0x2A37",
            value = """{"flags":"0x06","bpm":${70 + beat % 9},"raw":"06 4${beat % 9} 00 12 03"}""",
        )
        Sharingan.ble.read(
            device = "HR-Monitor-9F",
            characteristic = "Battery Level",
            uuid = "0x2A19",
            value = """{"raw":"0x5C","battery":${92 - beat % 4}}""",
        )
        if (beat % 3 == 2) {
            Sharingan.ble.error(
                device = "HR-Monitor-9F",
                characteristic = "Body Sensor Location",
                uuid = "0x2A38",
                message = "Attribute not found (GATT 0x0A)",
            )
        }
        beat++
    }
}
