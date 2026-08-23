package dev.sharingan.persistence

import kotlin.test.Test
import kotlin.test.assertEquals

internal class SharinganDatabaseRoundTripTest {
    @Test
    fun `Given a session and an event inserted When read back Then all fields round trip`() {
        val db = SharinganDatabase(createTestDriver())

        db.sharinganDatabaseQueries.insertSession(
            id = "session-1",
            started_at = 1_700_000_000_000L,
            app_id = "com.example.app",
            build = "1.2.3",
            os = "Android",
            device_model = "Pixel 8",
        )
        db.sharinganDatabaseQueries.insertEvent(
            id = "event-1",
            session_id = "session-1",
            timestamp = 1_700_000_001_000L,
            type = "HTTP",
            is_failure = 1L,
            host_or_topic = "api.example.com",
            payload_json = """{"method":"GET"}""",
        )

        val session = db.sharinganDatabaseQueries.selectSession("session-1").executeAsOne()
        assertEquals("session-1", session.id)
        assertEquals(1_700_000_000_000L, session.started_at)
        assertEquals("com.example.app", session.app_id)
        assertEquals("1.2.3", session.build)
        assertEquals("Android", session.os)
        assertEquals("Pixel 8", session.device_model)

        val events = db.sharinganDatabaseQueries.selectEventsForSession("session-1").executeAsList()
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("event-1", event.id)
        assertEquals("session-1", event.session_id)
        assertEquals(1_700_000_001_000L, event.timestamp)
        assertEquals("HTTP", event.type)
        assertEquals(1L, event.is_failure)
        assertEquals("api.example.com", event.host_or_topic)
        assertEquals("""{"method":"GET"}""", event.payload_json)
    }

    @Test
    fun `Given events for a session When the session is deleted Then its events cascade away`() {
        val db = SharinganDatabase(createTestDriver())

        db.sharinganDatabaseQueries.insertSession(
            id = "session-1",
            started_at = 1_700_000_000_000L,
            app_id = null,
            build = null,
            os = null,
            device_model = null,
        )
        repeat(3) { index ->
            db.sharinganDatabaseQueries.insertEvent(
                id = "event-$index",
                session_id = "session-1",
                timestamp = 1_700_000_001_000L + index,
                type = "HTTP",
                is_failure = 0L,
                host_or_topic = "api.example.com",
                payload_json = """{"n":$index}""",
            )
        }

        assertEquals(3, db.sharinganDatabaseQueries.selectAllEvents().executeAsList().size)

        db.sharinganDatabaseQueries.deleteSession("session-1")

        assertEquals(emptyList(), db.sharinganDatabaseQueries.selectAllEvents().executeAsList())
    }
}
