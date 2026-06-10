package dev.sharingan.ui

import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.HttpEvent
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import dev.sharingan.TimingPhase

/** Hardcoded fake state for @Preview composables — mirrors the design's IoT traffic. */
internal object PreviewData {

    val http = listOf(
        HttpEvent(
            id = "h1", timestampMillis = 1_749_556_771_882, method = "GET",
            url = "https://api.acme-iot.com/api/v2/devices/4471/state",
            statusCode = 200, durationMillis = 142, responseSizeBytes = 4300,
            requestHeaders = listOf("Authorization" to "••••", "Accept" to "application/json"),
            responseHeaders = listOf("Content-Type" to "application/json; charset=utf-8", "X-Trace-Id" to "b1f4-22a9"),
            responseBody = """{"deviceId":4471,"online":true,"firmware":"2.4.1","battery":0.86,"sensors":{"temp":23.4,"humidity":48}}""",
            contentType = "application/json",
            timing = listOf(
                TimingPhase("DNS", 8), TimingPhase("Connect", 21), TimingPhase("TLS", 34),
                TimingPhase("TTFB", 71), TimingPhase("Download", 8),
            ),
        ),
        HttpEvent(
            id = "h2", timestampMillis = 1_749_556_773_201, method = "POST",
            url = "https://api.acme-iot.com/api/v2/devices/4471/commands",
            statusCode = 202, durationMillis = 96, responseSizeBytes = 312,
            requestBody = """{"cmd":"reboot","delay":5}""",
            responseBody = """{"accepted":true,"commandId":9921}""",
            contentType = "application/json",
        ),
        HttpEvent(
            id = "h3", timestampMillis = 1_749_556_780_118, method = "POST",
            url = "https://auth.acme-iot.com/api/v2/auth/refresh",
            statusCode = 401, durationMillis = 74, responseSizeBytes = 128,
            responseBody = """{"error":"invalid_token","message":"Refresh token expired","code":"AUTH_017"}""",
            contentType = "application/json",
        ),
        HttpEvent(
            id = "h4", timestampMillis = 1_749_556_782_455, method = "GET",
            url = "https://api.acme-iot.com/api/v2/telemetry/4471/stream",
            statusCode = 500, durationMillis = 1240, responseSizeBytes = 96,
            responseHeaders = listOf("Content-Type" to "application/json", "X-Trace-Id" to "e0c2-7741"),
            responseBody = """{"error":"upstream_timeout","message":"Telemetry shard 3 did not respond","traceId":"e0c2-7741"}""",
            contentType = "application/json",
            timing = listOf(TimingPhase("TTFB", 1220), TimingPhase("Download", 15)),
        ),
    )

    val mqtt = listOf(
        MqttEvent(
            id = "m1", timestampMillis = 1_749_556_773_114, direction = MqttDirection.PUBLISH,
            topic = "devices/4471/telemetry", qos = 1,
            payload = """{"temp":23.4,"hum":48,"ts":1749556800}""", payloadSizeBytes = 24,
        ),
        MqttEvent(
            id = "m2", timestampMillis = 1_749_556_773_120, direction = MqttDirection.SUBSCRIBE,
            topic = "devices/4471/commands/#", qos = 1,
        ),
        MqttEvent(
            id = "m3", timestampMillis = 1_749_556_775_441, direction = MqttDirection.RECEIVE,
            topic = "devices/4471/commands/reboot", qos = 1,
            payload = """{"cmd":"reboot","delay":5,"by":"console"}""", payloadSizeBytes = 40,
        ),
        MqttEvent(
            id = "m4", timestampMillis = 1_749_556_779_233, direction = MqttDirection.PUBLISH,
            topic = "devices/4471/ack", qos = 1,
            payload = """{"ack":9921,"ok":false,"err":"busy"}""", payloadSizeBytes = 18,
            error = "broker rejected: busy",
        ),
    )

    val ble = listOf(
        BleEvent(
            id = "b1", timestampMillis = 1_749_556_770_120, operation = BleOperation.CONNECT,
            device = "C8:3A:35:9F:01:22", characteristic = "HR-Monitor-9F",
            payload = """{"event":"GATT connected","mtu":185,"rssi":-58}""",
        ),
        BleEvent(
            id = "b2", timestampMillis = 1_749_556_770_551, operation = BleOperation.NOTIFY,
            device = "HR-Monitor-9F", characteristic = "Heart Rate Measurement", uuid = "0x2A37",
            payload = """{"flags":"0x06","bpm":72,"raw":"06 48 00 12 03"}""", sizeBytes = 8,
        ),
        BleEvent(
            id = "b3", timestampMillis = 1_749_556_771_002, operation = BleOperation.READ,
            device = "HR-Monitor-9F", characteristic = "Battery Level", uuid = "0x2A19",
            payload = """{"raw":"0x5C","battery":92}""", sizeBytes = 1,
        ),
        BleEvent(
            id = "b4", timestampMillis = 1_749_556_773_120, operation = BleOperation.ERROR,
            device = "HR-Monitor-9F", characteristic = "Body Sensor Location", uuid = "0x2A38",
            error = "Attribute not found (GATT 0x0A)",
        ),
    )

    val all = http + mqtt + ble
}

internal fun previewCounts(): Map<Protocol, Int> = mapOf(
    Protocol.HTTP to PreviewData.http.size,
    Protocol.MQTT to PreviewData.mqtt.size,
    Protocol.BLE to PreviewData.ble.size,
)
