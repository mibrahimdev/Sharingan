package dev.sharingan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sharingan.HttpEvent
import dev.sharingan.formatBytes
import dev.sharingan.internal.formatClockTime
import dev.sharingan.jsonEscape

/** Everything Sharingan knows about HTTP events. */
internal object HttpDescriptor : ProtocolDescriptor<HttpEvent>() {

    override val protocol: Protocol = Protocol.HTTP
    override val eventNoun: String = "requests"

    override val chips: List<FilterChipSpec> = listOf(
        FilterChipSpec("all", "All"),
        FilterChipSpec("err", "Errors"),
        FilterChipSpec("2xx", "2xx"),
        FilterChipSpec("get", "GET"),
        FilterChipSpec("post", "POST"),
    )

    override fun chipMatches(event: HttpEvent, chipKey: String): Boolean = when (chipKey) {
        "err" -> event.isFailure
        "2xx" -> (event.statusCode ?: 0) in 200..299
        "get" -> event.method.equals("GET", ignoreCase = true)
        "post" -> event.method.equals("POST", ignoreCase = true)
        else -> true
    }

    override fun searchHaystack(event: HttpEvent): List<String?> = buildList {
        add(event.method)
        add(event.url)
        add(event.statusCode?.toString())
        add(event.error)
        add(event.requestBody)
        add(event.responseBody)
        event.requestHeaders.forEach { add("${it.first}: ${it.second}") }
        event.responseHeaders.forEach { add("${it.first}: ${it.second}") }
    }

    override fun present(colors: SharinganColors, event: HttpEvent): EventPresentation {
        val statusTint = colors.httpStatusTint(event.statusCode)
        return EventPresentation(
            lead = event.method.uppercase(),
            leadTint = colors.httpMethodTint(event.method),
            main = event.path,
            sub = event.host,
            status = event.statusCode?.toString() ?: "ERR",
            statusTint = statusTint,
            right = event.durationMillis?.let { "${it}ms" },
            rightColor = null,
            sizeLabel = formatBytes(event.responseSizeBytes),
            clockTime = formatClockTime(event.timestampMillis),
            railColor = statusTint.color,
            isFailure = event.isFailure,
        )
    }

    override fun ticker(event: HttpEvent): String =
        "${event.method} ${event.path} → ${event.statusCode ?: "ERR"}"

    override fun markdown(event: HttpEvent): String = buildString {
        appendLine("## ${event.method} ${event.path}")
        val status = event.statusCode?.toString() ?: "—"
        val duration = event.durationMillis?.let { " · **${it}ms**" } ?: ""
        val size = event.responseSizeBytes?.let { " · ${formatBytes(it)}" } ?: ""
        appendLine("**Status:** $status$duration$size")
        appendLine("**Host:** ${event.host}")
        event.error?.let { appendLine("**Error:** $it") }
        if (event.requestHeaders.isNotEmpty()) {
            appendLine()
            appendLine("### Request headers")
            event.requestHeaders.forEach { (k, v) -> appendLine("- $k: $v") }
        }
        if (event.responseHeaders.isNotEmpty()) {
            appendLine()
            appendLine("### Response headers")
            event.responseHeaders.forEach { (k, v) -> appendLine("- $k: $v") }
        }
        event.requestBody?.let { appendBodySection("Request body", it) }
        event.responseBody?.let { appendBodySection("Response body", it) }
    }.trimEnd()

    override fun summary(event: HttpEvent): String {
        val status = event.statusCode?.toString() ?: (event.error ?: "—")
        val duration = event.durationMillis?.let { " (${it}ms)" } ?: ""
        return "${event.method} ${event.path} → $status$duration"
    }

    override fun fields(event: HttpEvent): List<Pair<String, String>> = buildList {
        putString("protocol", "http")
        putString("method", event.method)
        putString("url", event.url)
        put("statusCode", event.statusCode?.toString())
        put("durationMillis", event.durationMillis?.toString())
        put("requestHeaders", headersJson(event.requestHeaders))
        put("responseHeaders", headersJson(event.responseHeaders))
        putString("requestBody", event.requestBody)
        putString("responseBody", event.responseBody)
        putString("contentType", event.contentType)
        put("responseSizeBytes", event.responseSizeBytes?.toString())
        put(
            "timing",
            event.timing.takeIf { it.isNotEmpty() }?.joinToString(
                prefix = "[",
                postfix = "]",
            ) { "{\"label\": \"${jsonEscape(it.label)}\", \"millis\": ${it.millis}}" },
        )
    }

    private fun headersJson(headers: List<Pair<String, String>>): String? {
        if (headers.isEmpty()) return null
        return headers.joinToString(prefix = "[", postfix = "]") { (k, v) ->
            "{\"name\": \"${jsonEscape(k)}\", \"value\": \"${jsonEscape(v)}\"}"
        }
    }

    @Composable
    override fun Body(event: HttpEvent) {
        val colors = LocalSharinganColors.current
        val statusTint = colors.httpStatusTint(event.statusCode)

        Section("Summary") {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Status", event.statusCode?.toString() ?: "ERR", statusTint.color, Modifier.weight(1f))
                SummaryCard("Duration", event.durationMillis?.let { "$it ms" } ?: "—", colors.text, Modifier.weight(1f))
                SummaryCard("Size", formatBytes(event.responseSizeBytes), colors.text, Modifier.weight(1f))
            }
        }
        event.error?.let { ErrorSection(it) }
        if (event.timing.isNotEmpty()) {
            val total = event.timing.sumOf { it.millis }.coerceAtLeast(1)
            Section("Timing", right = "$total ms") {
                TimingWaterfall(event)
            }
        }
        if (event.requestHeaders.isNotEmpty()) {
            Section("Request headers") {
                event.requestHeaders.forEach { (k, v) -> KeyValueRow(k, v) }
            }
        }
        if (event.responseHeaders.isNotEmpty()) {
            Section("Response headers") {
                event.responseHeaders.forEach { (k, v) -> KeyValueRow(k, v) }
            }
        }
        event.requestBody?.takeIf { it.isNotBlank() }?.let {
            Section("Request body") { BodyBlock(it) }
        }
        event.responseBody?.takeIf { it.isNotBlank() }?.let {
            Section("Response body", right = event.contentType) { BodyBlock(it) }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val colors = LocalSharinganColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(label.uppercase(), color = colors.textDim, fontSize = 10.sp, fontFamily = MonoFont, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = MonoFont, maxLines = 1)
    }
}

@Composable
private fun TimingWaterfall(event: HttpEvent) {
    val colors = LocalSharinganColors.current
    val palette = listOf(colors.textDim, colors.info, colors.violet, colors.warn, colors.ok)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, bottom = 9.dp)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.surface2),
        ) {
            event.timing.forEachIndexed { index, phase ->
                if (phase.millis > 0) {
                    Box(
                        Modifier
                            .weight(phase.millis.toFloat())
                            .fillMaxSize()
                            .background(palette[index % palette.size]),
                    )
                }
            }
        }
        event.timing.forEachIndexed { index, phase ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(palette[index % palette.size]),
                )
                Text(phase.label, color = colors.textMid, fontSize = 11.sp, fontFamily = MonoFont, modifier = Modifier.weight(1f))
                Text("${phase.millis} ms", color = colors.text, fontSize = 11.sp, fontFamily = MonoFont, maxLines = 1)
            }
        }
    }
}
