package dev.sharingan

import dev.sharingan.internal.EventIds
import dev.sharingan.internal.currentTimeMillis

/**
 * Records [HttpEvent]s into a [SharinganStore].
 *
 * HTTP capture is normally automatic via the `SharinganKtor` client plugin;
 * use [log] directly when traffic flows through another HTTP stack.
 *
 * Header values whose names appear in [redactedHeaders] (case-insensitive)
 * are masked at capture time and never reach the store.
 */
public class HttpLogger(
    private val store: SharinganStore,
    private val redactedHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
) {
    /**
     * Records one HTTP exchange.
     *
     * @param method HTTP method, e.g. `"GET"`.
     * @param url Full request URL; host and path are derived from it.
     * @param statusCode Response status, or `null` if the request never got one.
     * @param durationMillis Total request duration.
     * @param timing Optional waterfall phases (DNS/Connect/TLS/TTFB/Download…).
     * @param error Transport-level failure reason, `null` on success.
     */
    public fun log(
        method: String,
        url: String,
        statusCode: Int? = null,
        durationMillis: Long? = null,
        requestHeaders: List<Pair<String, String>> = emptyList(),
        responseHeaders: List<Pair<String, String>> = emptyList(),
        requestBody: String? = null,
        responseBody: String? = null,
        contentType: String? = null,
        responseSizeBytes: Long? = null,
        timing: List<TimingPhase> = emptyList(),
        error: String? = null,
    ) {
        store.record(
            HttpEvent(
                id = EventIds.next("http-"),
                timestampMillis = currentTimeMillis(),
                method = method,
                url = url,
                statusCode = statusCode,
                durationMillis = durationMillis,
                requestHeaders = redact(requestHeaders),
                responseHeaders = redact(responseHeaders),
                requestBody = requestBody,
                responseBody = responseBody,
                contentType = contentType,
                responseSizeBytes = responseSizeBytes,
                timing = timing,
                error = error,
            )
        )
    }

    private fun redact(headers: List<Pair<String, String>>): List<Pair<String, String>> =
        headers.map { (name, value) ->
            if (redactedHeaders.any { it.equals(name, ignoreCase = true) }) name to REDACTED_VALUE
            else name to value
        }

    public companion object {
        /** Mask used in place of redacted header values. */
        public const val REDACTED_VALUE: String = "••••"

        /** Headers masked by default; pass your own set to widen or narrow. */
        public val DEFAULT_REDACTED_HEADERS: Set<String> =
            setOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie")
    }
}
