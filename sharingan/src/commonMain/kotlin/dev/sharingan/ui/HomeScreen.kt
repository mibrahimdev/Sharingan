package dev.sharingan.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sharingan.SharinganEvent
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Everything [HomeScreenContent] renders; assembled by the wrapper. */
internal data class HomeUiState(
    val protocol: Protocol,
    val counts: Map<Protocol, Int>,
    val rows: List<SharinganEvent>,
    val query: String,
    val chipKey: String,
    val recording: Boolean,
)

@Composable
internal fun HomeScreenContent(
    state: HomeUiState,
    onSelectProtocol: (Protocol) -> Unit,
    onQueryChange: (String) -> Unit,
    onChipChange: (String) -> Unit,
    onToggleRecording: () -> Unit,
    onOpenEvent: (SharinganEvent) -> Unit,
    onShareAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSharinganColors.current
    Column(modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxWidth().background(colors.bgElev)) {
            HomeHeader(recording = state.recording, onToggleRecording = onToggleRecording, onShareAll = onShareAll)
            TabBar(selected = state.protocol, counts = state.counts, onSelect = onSelectProtocol)
            SearchField(
                protocol = state.protocol,
                query = state.query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
            FilterChips(
                protocol = state.protocol,
                selectedKey = state.chipKey,
                onSelect = onChipChange,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 9.dp),
            )
        }
        HorizontalDivider()
        ColumnHeader(
            count = state.rows.size,
            noun = state.protocol.eventNoun(),
            recording = state.recording,
        )
        if (state.rows.isEmpty()) {
            EmptyState(hasAny = (state.counts[state.protocol] ?: 0) > 0)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.rows, key = { it.id }) { event ->
                    TerminalRow(event = event, onOpen = { onOpenEvent(event) })
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    recording: Boolean,
    onToggleRecording: () -> Unit,
    onShareAll: () -> Unit,
) {
    val colors = LocalSharinganColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SharinganMark(size = 24.dp, ink = if (colors.isDark) Color(0xFF0B0C0F) else Color.White)
        Text(
            "Sharingan",
            color = colors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansFont,
            modifier = Modifier.weight(1f),
        )
        RecordingPill(recording = recording, onToggle = onToggleRecording)
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .clickable(onClick = onShareAll),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SharinganIcons.Share, contentDescription = "Share session", tint = colors.textMid, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun RecordingPill(recording: Boolean, onToggle: () -> Unit) {
    val colors = LocalSharinganColors.current
    val pulse = rememberInfiniteTransition(label = "rec")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "recAlpha",
    )
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (recording) colors.accentSoft else colors.surface)
            .border(1.dp, if (recording) colors.accent else colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (recording) pulseAlpha else 1f)
                .clip(CircleShape)
                .background(if (recording) colors.accent else colors.textDim),
        )
        Text(
            if (recording) "REC" else "PAUSED",
            color = if (recording) colors.accent else colors.textMid,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MonoFont,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun TabBar(
    selected: Protocol,
    counts: Map<Protocol, Int>,
    onSelect: (Protocol) -> Unit,
) {
    val colors = LocalSharinganColors.current
    Row(Modifier.fillMaxWidth()) {
        Protocol.entries.forEach { protocol ->
            val on = protocol == selected
            val icon = when (protocol) {
                Protocol.HTTP -> SharinganIcons.Globe
                Protocol.MQTT -> SharinganIcons.Waves
                Protocol.BLE -> SharinganIcons.Bluetooth
            }
            Column(
                Modifier.weight(1f).clickable { onSelect(protocol) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.padding(top = 11.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (on) colors.accent else colors.textMid,
                        modifier = Modifier.size(14.dp).alpha(if (on) 1f else 0.55f),
                    )
                    Text(
                        protocol.label,
                        color = if (on) colors.text else colors.textMid,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        fontFamily = SansFont,
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) colors.accentSoft else colors.faint)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                            .widthIn(min = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${counts[protocol] ?: 0}",
                            color = if (on) colors.accent else colors.textDim,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MonoFont,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth(0.64f)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (on) colors.accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    protocol: Protocol,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSharinganColors.current
    val placeholder = when (protocol) {
        Protocol.HTTP -> "Filter path, status, header…"
        Protocol.MQTT -> "Filter topic, payload…"
        Protocol.BLE -> "Filter characteristic, device…"
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface2)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(SharinganIcons.Search, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(14.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = colors.textDim, fontSize = 12.sp, fontFamily = MonoFont, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 12.sp, fontFamily = MonoFont),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                SharinganIcons.Close,
                contentDescription = "Clear search",
                tint = colors.textDim,
                modifier = Modifier.size(14.dp).clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun FilterChips(
    protocol: Protocol,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSharinganColors.current
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chipsFor(protocol).forEach { chip ->
            val on = chip.key == selectedKey
            Text(
                chip.label,
                color = if (on) colors.bg else colors.textMid,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = MonoFont,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) colors.text else colors.surface)
                    .border(1.dp, if (on) Color.Transparent else colors.border, RoundedCornerShape(20.dp))
                    .clickable { onSelect(chip.key) }
                    .padding(horizontal = 11.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ColumnHeader(count: Int, noun: String, recording: Boolean) {
    val colors = LocalSharinganColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count ${noun.uppercase()}",
            color = colors.textDim,
            fontSize = 10.5.sp,
            fontFamily = MonoFont,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (recording) "● live" else "paused",
            color = if (recording) colors.textDim else colors.warn,
            fontSize = 10.5.sp,
            fontFamily = MonoFont,
        )
    }
}

@Composable
private fun EmptyState(hasAny: Boolean) {
    val colors = LocalSharinganColors.current
    Text(
        if (hasAny) "No matches" else "Nothing captured yet",
        color = colors.textDim,
        fontSize = 12.sp,
        fontFamily = MonoFont,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
    )
}

/** Compact single-line row — the design's default "Terminal" density. */
@Composable
internal fun TerminalRow(event: SharinganEvent, onOpen: () -> Unit) {
    val colors = LocalSharinganColors.current
    val p = presentationOf(colors, event)
    val isBle = protocolOf(event) == Protocol.BLE
    Box(Modifier.fillMaxWidth().height(38.dp).clickable(onClick = onOpen)) {
        if (p.isFailure) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 6.dp)
                    .fillMaxHeight()
                    .width(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(p.railColor),
            )
        }
        Row(
            Modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                p.lead,
                color = p.leadTint.color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFont,
                maxLines = 1,
                modifier = Modifier.width(if (isBle) 60.dp else 42.dp),
            )
            Text(
                p.main,
                color = colors.text,
                fontSize = 12.5.sp,
                fontFamily = MonoFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            p.status?.let {
                Text(
                    it,
                    color = p.statusTint.color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFont,
                    maxLines = 1,
                )
            }
            Text(
                p.right ?: p.sizeLabel,
                color = p.rightColor ?: colors.textDim,
                fontSize = 11.5.sp,
                fontFamily = MonoFont,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(46.dp),
            )
            Text(
                rowTime(p.clockTime),
                color = colors.textDim.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontFamily = MonoFont,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp),
            )
        }
        HorizontalDivider(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
internal fun HorizontalDivider(modifier: Modifier = Modifier) {
    val colors = LocalSharinganColors.current
    Box(modifier.fillMaxWidth().height(1.dp).background(colors.faint))
}

// ── Previews ─────────────────────────────────────────────────

@Preview
@Composable
private fun HomeScreenContent_LightPreview() {
    SharinganTheme(darkTheme = false) {
        HomeScreenContent(
            state = HomeUiState(
                protocol = Protocol.HTTP,
                counts = previewCounts(),
                rows = PreviewData.http,
                query = "",
                chipKey = "all",
                recording = true,
            ),
            onSelectProtocol = {}, onQueryChange = {}, onChipChange = {},
            onToggleRecording = {}, onOpenEvent = {}, onShareAll = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_DarkPreview() {
    SharinganTheme(darkTheme = true) {
        HomeScreenContent(
            state = HomeUiState(
                protocol = Protocol.MQTT,
                counts = previewCounts(),
                rows = PreviewData.mqtt,
                query = "",
                chipKey = "all",
                recording = true,
            ),
            onSelectProtocol = {}, onQueryChange = {}, onChipChange = {},
            onToggleRecording = {}, onOpenEvent = {}, onShareAll = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_PausedEmptyPreview() {
    SharinganTheme(darkTheme = false) {
        HomeScreenContent(
            state = HomeUiState(
                protocol = Protocol.BLE,
                counts = mapOf(),
                rows = emptyList(),
                query = "",
                chipKey = "all",
                recording = false,
            ),
            onSelectProtocol = {}, onQueryChange = {}, onChipChange = {},
            onToggleRecording = {}, onOpenEvent = {}, onShareAll = {},
        )
    }
}
