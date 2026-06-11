package dev.sharingan.ui

import dev.sharingan.HttpEvent
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import dev.sharingan.SharinganExport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ShareResolverTest {

    private val http = HttpEvent(
        id = "h1",
        timestampMillis = 0,
        method = "GET",
        url = "https://api.acme.com/state",
        statusCode = 200,
    )
    private val mqtt = MqttEvent(
        id = "m1",
        timestampMillis = 1,
        direction = MqttDirection.PUBLISH,
        topic = "devices/4471/telemetry",
    )
    private val tab = listOf(http, mqtt)

    @Test
    fun `Given single scope When copy for agent is resolved Then payload is that event's agent Markdown to the clipboard`() {
        val r = resolveShare(ShareAction.COPY_AGENT, ShareScope.SINGLE, http, tab)
        assertEquals(SharinganExport.agentMarkdown(http), r.payload)
        assertEquals(ShareDelivery.CLIPBOARD, r.delivery)
        assertEquals("Copied for agent ✓", r.toast)
    }

    @Test
    fun `Given all scope When copy for agent is resolved Then payload is the whole session Markdown`() {
        val r = resolveShare(ShareAction.COPY_AGENT, ShareScope.ALL, http, tab)
        assertEquals(SharinganExport.agentMarkdown(tab), r.payload)
    }

    @Test
    fun `Given a selected HTTP event When copy human is resolved Then payload is a curl command`() {
        val r = resolveShare(ShareAction.COPY_HUMAN, ShareScope.SINGLE, http, tab)
        assertEquals(SharinganExport.curl(http), r.payload)
        assertEquals("Copied ✓", r.toast)
    }

    @Test
    fun `Given a selected MQTT event When copy human is resolved Then payload falls back to the one-line summary`() {
        val r = resolveShare(ShareAction.COPY_HUMAN, ShareScope.SINGLE, mqtt, tab)
        assertEquals(SharinganExport.summary(listOf(mqtt)), r.payload)
    }

    @Test
    fun `Given single scope When copy raw is resolved Then payload is the event JSON`() {
        val r = resolveShare(ShareAction.COPY_RAW, ShareScope.SINGLE, http, tab)
        assertEquals(SharinganExport.json(http), r.payload)
        assertEquals("Copied JSON ✓", r.toast)
    }

    @Test
    fun `Given all scope When copy raw is resolved Then payload is the session JSON`() {
        val r = resolveShare(ShareAction.COPY_RAW, ShareScope.ALL, http, tab)
        assertEquals(SharinganExport.sessionJson(tab), r.payload)
    }

    @Test
    fun `When system share is resolved Then delivery is the system sheet and no toast is shown`() {
        val r = resolveShare(ShareAction.SYSTEM_SHARE, ShareScope.SINGLE, http, tab)
        assertEquals(SharinganExport.agentMarkdown(http), r.payload)
        assertEquals(ShareDelivery.SYSTEM_SHARE, r.delivery)
        assertNull(r.toast)
    }

    @Test
    fun `Given single scope without a selected event When resolved Then payload falls back to the whole tab`() {
        val r = resolveShare(ShareAction.COPY_AGENT, ShareScope.SINGLE, null, tab)
        assertEquals(SharinganExport.agentMarkdown(tab), r.payload)
    }

    @Test
    fun `Given all scope When copy human is resolved Then a selected event is ignored`() {
        val r = resolveShare(ShareAction.COPY_HUMAN, ShareScope.ALL, http, tab)
        assertEquals(SharinganExport.summary(tab), r.payload)
        assertTrue(r.payload.startsWith("Sharingan session"))
    }
}
