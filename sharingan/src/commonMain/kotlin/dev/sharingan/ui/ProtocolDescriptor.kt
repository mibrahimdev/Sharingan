package dev.sharingan.ui

import androidx.compose.runtime.Composable
import dev.sharingan.BleEvent
import dev.sharingan.HttpEvent
import dev.sharingan.MqttEvent
import dev.sharingan.SharinganEvent
import dev.sharingan.jsonEscape

/**
 * The single home for everything Sharingan knows about one protocol:
 * filter chips, chip matching, search haystack, row presentation, the
 * notification ticker line, per-event export fragments, and the detail
 * body. Adding a protocol means writing one descriptor and registering
 * it in [descriptorOf] — the compiler points at that one spot.
 *
 * Session-level concerns (tab order, counts header, export session
 * assembly) stay outside: they belong to the session, not to a protocol.
 *
 * [descriptorOf] guarantees each descriptor only ever receives its own
 * event subtype, but the type system can't see that guarantee, so the
 * public members accept [SharinganEvent] and cast exactly once here.
 * Implementations override the protected typed members and never cast.
 * (The typed members need distinct names: `E` erases to [SharinganEvent],
 * so same-named overloads would clash on the JVM.)
 */
internal abstract class ProtocolDescriptor<E : SharinganEvent> {

    abstract val protocol: Protocol

    /** `requests` / `messages` / `operations` — the column-header noun. */
    abstract val eventNoun: String

    /** Quick-filter chips below the search field, in design order. */
    abstract val chips: List<FilterChipSpec>

    /** Chip matching; `"all"` is handled by the caller before dispatch. */
    protected abstract fun chipMatches(event: E, chipKey: String): Boolean

    /** Strings the search box matches against; nulls are skipped. */
    protected abstract fun searchHaystack(event: E): List<String?>

    /** Everything a row or detail title needs, resolved once per event. */
    protected abstract fun present(colors: SharinganColors, event: E): EventPresentation

    /** One-line text for the capture notification's expanded ticker. */
    protected abstract fun ticker(event: E): String

    /** Agent Markdown for one event — no session header or separators. */
    protected abstract fun markdown(event: E): String

    /** One-line human-readable digest, no leading timestamp. */
    protected abstract fun summary(event: E): String

    /**
     * Protocol-specific JSON fields as name → raw JSON value pairs, with
     * `"protocol"` first. Shared fields (id, timestamp, error) are added
     * by [dev.sharingan.SharinganExport].
     */
    protected abstract fun fields(event: E): List<Pair<String, String>>

    /** Protocol-specific detail sections, rendered below the shared title block. */
    @Composable
    protected abstract fun Body(event: E)

    // ── untyped bridges ──────────────────────────────────────────
    // descriptorOf(event) returns the descriptor matching the event's
    // concrete type, so this cast cannot fail at runtime.

    @Suppress("UNCHECKED_CAST")
    private fun typed(event: SharinganEvent): E = event as E

    fun matchesChip(event: SharinganEvent, chipKey: String): Boolean = chipMatches(typed(event), chipKey)

    fun haystack(event: SharinganEvent): List<String?> = searchHaystack(typed(event))

    fun presentation(colors: SharinganColors, event: SharinganEvent): EventPresentation =
        present(colors, typed(event))

    fun tickerLine(event: SharinganEvent): String = ticker(typed(event))

    fun agentMarkdown(event: SharinganEvent): String = markdown(typed(event))

    fun summaryLine(event: SharinganEvent): String = summary(typed(event))

    fun jsonFields(event: SharinganEvent): List<Pair<String, String>> = fields(typed(event))

    @Composable
    fun DetailBody(event: SharinganEvent) {
        Body(typed(event))
    }

    // ── shared fragment helpers for implementations ─────────────

    /** Adds a raw JSON value if non-null. */
    protected fun MutableList<Pair<String, String>>.put(name: String, raw: String?) {
        raw?.let { add(name to it) }
    }

    /** Adds an escaped JSON string value if non-null. */
    protected fun MutableList<Pair<String, String>>.putString(name: String, value: String?) {
        value?.let { add(name to "\"${jsonEscape(it)}\"") }
    }

    /** Fenced body section for agent Markdown; ```json when it looks like JSON. */
    protected fun StringBuilder.appendBodySection(title: String, body: String) {
        val looksJson = body.trimStart().firstOrNull() in setOf('{', '[')
        appendLine()
        appendLine("### $title")
        appendLine(if (looksJson) "```json" else "```")
        appendLine(body)
        appendLine("```")
    }
}

/** The one exhaustive event → descriptor mapping in the codebase. */
internal fun descriptorOf(event: SharinganEvent): ProtocolDescriptor<*> = when (event) {
    is HttpEvent -> HttpDescriptor
    is MqttEvent -> MqttDescriptor
    is BleEvent -> BleDescriptor
}

/** Tab-level lookup for concerns keyed by [Protocol] (chips, noun) rather than by event. */
internal fun descriptorOf(protocol: Protocol): ProtocolDescriptor<*> = when (protocol) {
    Protocol.HTTP -> HttpDescriptor
    Protocol.MQTT -> MqttDescriptor
    Protocol.BLE -> BleDescriptor
}
