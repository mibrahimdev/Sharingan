package dev.sharingan.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class JsonPrettyTest {

    @Test
    fun `When minified JSON is prettified Then it is re-indented with two spaces`() {
        val pretty = prettyJson("""{"deviceId":4471,"online":true,"sensors":{"temp":23.4}}""")
        assertEquals(
            """
            {
              "deviceId": 4471,
              "online": true,
              "sensors": {
                "temp": 23.4
              }
            }
            """.trimIndent(),
            pretty,
        )
    }

    @Test
    fun `When an array is prettified Then elements are indented one level`() {
        val pretty = prettyJson("""{"items":[1,2]}""")
        assertEquals(
            """
            {
              "items": [
                1,
                2
              ]
            }
            """.trimIndent(),
            pretty,
        )
    }

    @Test
    fun `Given empty containers When prettified Then they stay inline`() {
        assertEquals("{}", prettyJson("{}"))
        assertEquals(
            """
            {
              "a": []
            }
            """.trimIndent(),
            prettyJson("""{"a":[]}"""),
        )
    }

    @Test
    fun `Given escaped characters in strings When prettified Then escapes are preserved`() {
        assertEquals(
            """
            {
              "msg": "line\n\"quoted\""
            }
            """.trimIndent(),
            prettyJson("""{"msg":"line\n\"quoted\""}"""),
        )
    }

    @Test
    fun `Given null and negative numbers When prettified Then literals render verbatim`() {
        assertEquals(
            """
            {
              "a": null,
              "b": -12.5e3
            }
            """.trimIndent(),
            prettyJson("""{"a":null,"b":-12.5e3}"""),
        )
    }

    @Test
    fun `Given invalid JSON When prettified Then null is returned so callers fall back to plain text`() {
        assertNull(prettyJson("not json"))
        assertNull(prettyJson("""{"a":}"""))
        assertNull(prettyJson("""{"a":1"""))
    }

    @Test
    fun `Given trailing garbage When prettified Then null is returned`() {
        assertNull(prettyJson("""{} extra"""))
    }
}
