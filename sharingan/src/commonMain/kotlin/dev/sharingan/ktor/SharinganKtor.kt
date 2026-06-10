package dev.sharingan.ktor

import dev.sharingan.HttpLogger
import dev.sharingan.Sharingan
import dev.sharingan.SharinganStore
import dev.sharingan.TimingPhase
import dev.sharingan.internal.currentTimeMillis
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.content.OutgoingContent

/**
 * Configuration for the [SharinganKtor] client plugin.
 */
public class SharinganKtorConfig {
    /** Destination store; defaults to the shared [Sharingan.store]. */
    public var store: SharinganStore = Sharingan.store

    /** Header names (case-insensitive) whose values are masked at capture. */
    public var redactedHeaders: Set<String> = HttpLogger.DEFAULT_REDACTED_HEADERS

    /** Capture request/response bodies (textual content types only). */
    public var captureBodies: Boolean = true

    /** Bodies longer than this many characters are truncated with a marker. */
    public var maxBodyBytes: Int = 64 * 1024
}

/**
 * Ktor client plugin that records every HTTP exchange into Sharingan.
 *
 * ```kotlin
 * val client = HttpClient {
 *     install(SharinganKtor)
 * }
 * ```
 *
 * Captures method, URL, status, headers (with redaction), textual bodies
 * (capped), duration and a TTFB/Download timing split. Transport failures are
 * recorded as failure events and rethrown untouched. Event-stream and binary
 * bodies are never read, so streaming responses keep streaming.
 */
public val SharinganKtor: ClientPlugin<SharinganKtorConfig> =
    createClientPlugin("SharinganKtor", ::SharinganKtorConfig) {
        val logger = HttpLogger(pluginConfig.store, pluginConfig.redactedHeaders)
        val captureBodies = pluginConfig.captureBodies
        val maxBodyBytes = pluginConfig.maxBodyBytes

        on(Send) { request ->
            val startMillis = currentTimeMillis()
            val method = request.method.value
            val url = request.url.buildString()
            val content = request.body as? OutgoingContent
            val requestHeaders = buildList {
                request.headers.entries().forEach { (name, values) -> values.forEach { add(name to it) } }
                content?.contentType?.let { add("Content-Type" to it.toString()) }
                content?.contentLength?.let { add("Content-Length" to it.toString()) }
            }
            val requestBody = if (captureBodies) outgoingBodyText(content, maxBodyBytes) else null

            val call = try {
                proceed(request)
            } catch (failure: Throwable) {
                logger.log(
                    method = method,
                    url = url,
                    durationMillis = currentTimeMillis() - startMillis,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = failure.message ?: failure::class.simpleName ?: "request failed",
                )
                throw failure
            }

            val firstByteMillis = currentTimeMillis()
            val responseBody =
                if (captureBodies && shouldReadBody(call.response)) {
                    truncate(call.response.bodyAsText(), maxBodyBytes)
                } else {
                    null
                }
            val endMillis = currentTimeMillis()

            logger.log(
                method = method,
                url = url,
                statusCode = call.response.status.value,
                durationMillis = endMillis - startMillis,
                requestHeaders = requestHeaders,
                responseHeaders = flattenHeaders(call.response.headers),
                requestBody = requestBody,
                responseBody = responseBody,
                contentType = call.response.contentType()?.let { "${it.contentType}/${it.contentSubtype}" },
                responseSizeBytes = call.response.contentLength()
                    ?: responseBody?.encodeToByteArray()?.size?.toLong(),
                timing = listOf(
                    TimingPhase("TTFB", firstByteMillis - startMillis),
                    TimingPhase("Download", endMillis - firstByteMillis),
                ),
            )
            call
        }
    }

private fun flattenHeaders(headers: Headers): List<Pair<String, String>> =
    headers.entries().flatMap { (name, values) -> values.map { name to it } }

private fun outgoingBodyText(content: OutgoingContent?, maxBytes: Int): String? = when (content) {
    is OutgoingContent.ByteArrayContent ->
        if (isTextual(content.contentType)) truncate(content.bytes().decodeToString(), maxBytes) else null
    else -> null
}

private fun shouldReadBody(response: HttpResponse): Boolean {
    val contentType = response.contentType() ?: return false
    // Never read streams the caller intends to consume incrementally.
    if (contentType.match(ContentType.Text.EventStream)) return false
    return isTextual(contentType)
}

private fun isTextual(contentType: ContentType?): Boolean {
    contentType ?: return false
    return contentType.contentType == "text" ||
        contentType.contentSubtype == "json" ||
        contentType.contentSubtype == "xml" ||
        contentType.contentSubtype.endsWith("+json") ||
        contentType.contentSubtype.endsWith("+xml") ||
        contentType.contentSubtype == "x-www-form-urlencoded"
}

private fun truncate(body: String, maxBytes: Int): String =
    if (body.length <= maxBytes) body
    else body.take(maxBytes) + "\n… (+${body.length - maxBytes} chars truncated)"
