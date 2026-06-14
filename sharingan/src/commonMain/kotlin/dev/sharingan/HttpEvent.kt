package dev.sharingan

/**
 * One phase of an HTTP request's timing waterfall, rendered as a proportional
 * bar in the detail screen.
 *
 * [label] is a free-form [String], so any text renders — but stick to the
 * conventional waterfall vocabulary so phases read consistently across events
 * and exports. Listed in the order they occur:
 *
 * - `DNS` — hostname resolution.
 * - `Connect` — TCP connection establishment.
 * - `TLS` — TLS/SSL handshake.
 * - `TTFB` — time to first byte (request sent → first response byte).
 * - `Download` — response body transfer.
 *
 * The `SharinganKtor` plugin emits the `TTFB`/`Download` split automatically;
 * supply the earlier phases yourself via `Sharingan.http.log(...)` when your
 * HTTP stack exposes them.
 */
public data class TimingPhase(
    public val label: String,
    public val millis: Long,
)

/**
 * A captured HTTP exchange.
 *
 * [host] and [path] are derived from [url]; [path] keeps the query string so
 * rows read like an access log. A `null` [statusCode] together with a non-null
 * [error] means the request failed at the transport layer.
 */
@ConsistentCopyVisibility
public data class HttpEvent internal constructor(
    override val id: String,
    override val timestampMillis: Long,
    public val method: String,
    public val url: String,
    public val statusCode: Int? = null,
    public val durationMillis: Long? = null,
    public val requestHeaders: List<Pair<String, String>> = emptyList(),
    public val responseHeaders: List<Pair<String, String>> = emptyList(),
    public val requestBody: String? = null,
    public val responseBody: String? = null,
    public val contentType: String? = null,
    public val responseSizeBytes: Long? = null,
    public val timing: List<TimingPhase> = emptyList(),
    override val error: String? = null,
) : SharinganEvent {

    /** Authority part of [url], including the port when present. */
    public val host: String get() = splitUrl(url).first

    /** Path plus query string, `/` when the URL has no path. */
    public val path: String get() = splitUrl(url).second

    /** Failures are transport errors or 4xx/5xx responses. */
    override val isFailure: Boolean get() = error != null || (statusCode ?: 0) >= 400

    private companion object {
        fun splitUrl(url: String): Pair<String, String> {
            val afterScheme = url.substringAfter("://", url)
            val slash = afterScheme.indexOf('/')
            return if (slash == -1) afterScheme to "/"
            else afterScheme.take(slash) to afterScheme.substring(slash)
        }
    }
}
