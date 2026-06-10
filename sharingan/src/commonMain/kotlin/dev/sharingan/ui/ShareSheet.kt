package dev.sharingan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.ui.tooling.preview.Preview

/** What the user picked in the share sheet. */
internal enum class ShareAction { COPY_AGENT, COPY_HUMAN, COPY_RAW, SYSTEM_SHARE }

/** Whether the sheet shares one event or the protocol tab's whole session. */
internal enum class ShareScope { SINGLE, ALL }

internal data class ShareSheetState(
    val scope: ShareScope,
    val protocol: Protocol,
    /** Markdown payload preview shown at the top of the sheet. */
    val preview: String,
    /** True when the single shared event is HTTP (enables "Copy as cURL"). */
    val curlAvailable: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareSheet(
    state: ShareSheetState,
    onAction: (ShareAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSharinganColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.bgElev,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 2.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        ShareSheetBody(state, onAction)
    }
}

@Composable
internal fun ShareSheetBody(
    state: ShareSheetState,
    onAction: (ShareAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSharinganColors.current
    Column(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 26.dp, top = 8.dp)) {
        Text(
            if (state.scope == ShareScope.ALL) "Share session"
            else "Share this ${state.protocol.eventNounSingular()}",
            color = colors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansFont,
        )
        Text(
            "Extract clean, structured logs for a human or an agent to debug with.",
            color = colors.textMid,
            fontSize = 12.5.sp,
            fontFamily = SansFont,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        // payload preview with a bottom fade
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 108.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
        ) {
            Text(
                state.preview,
                color = colors.textMid,
                fontSize = 10.5.sp,
                fontFamily = MonoFont,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.bgElev))),
            )
        }

        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareOption(
                icon = { tint -> Icon(SharinganIcons.Agent, null, tint = tint, modifier = Modifier.size(17.dp)) },
                title = "Copy for AI agent",
                subtitle = "Structured Markdown — paste into Claude or any chat",
                primary = true,
                onClick = { onAction(ShareAction.COPY_AGENT) },
            )
            ShareOption(
                icon = { tint -> Icon(SharinganIcons.Copy, null, tint = tint, modifier = Modifier.size(16.dp)) },
                title = when {
                    state.scope == ShareScope.ALL -> "Copy summary"
                    state.curlAvailable -> "Copy as cURL"
                    else -> "Copy summary"
                },
                subtitle = if (state.scope == ShareScope.SINGLE && state.curlAvailable) {
                    "Reproducible request command"
                } else {
                    "Human-readable digest"
                },
                onClick = { onAction(ShareAction.COPY_HUMAN) },
            )
            ShareOption(
                icon = { tint -> Icon(SharinganIcons.Bolt, null, tint = tint, modifier = Modifier.size(14.dp)) },
                title = if (state.scope == ShareScope.ALL) "Copy session (.json)" else "Copy raw JSON",
                subtitle = if (state.scope == ShareScope.ALL) "Full protocol timeline" else "Single event payload",
                onClick = { onAction(ShareAction.COPY_RAW) },
            )
            ShareOption(
                icon = { tint -> Icon(SharinganIcons.Share, null, tint = tint, modifier = Modifier.size(16.dp)) },
                title = "Share via…",
                subtitle = "Send to a teammate or paste into a ticket",
                onClick = { onAction(ShareAction.SYSTEM_SHARE) },
            )
        }
    }
}

@Composable
private fun ShareOption(
    icon: @Composable (Color) -> Unit,
    title: String,
    subtitle: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalSharinganColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) colors.accent else colors.surface)
            .border(1.dp, if (primary) Color.Transparent else colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (primary) Color.White.copy(alpha = 0.18f) else colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            icon(if (primary) Color.White else colors.textMid)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (primary) Color.White else colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SansFont,
            )
            Text(
                subtitle,
                color = if (primary) Color.White.copy(alpha = 0.8f) else colors.textDim,
                fontSize = 11.5.sp,
                fontFamily = SansFont,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Icon(
            SharinganIcons.ChevronRight,
            contentDescription = null,
            tint = if (primary) Color.White.copy(alpha = 0.9f) else colors.textDim.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Preview
@Composable
private fun ShareSheetBody_SinglePreview() {
    SharinganTheme(darkTheme = false) {
        Box(Modifier.background(LocalSharinganColors.current.bgElev)) {
            ShareSheetBody(
                state = ShareSheetState(
                    scope = ShareScope.SINGLE,
                    protocol = Protocol.HTTP,
                    preview = "## GET /api/v2/devices/4471/state\n**Status:** 200 · **142ms** · 4.2 KB\n**Host:** api.acme-iot.com",
                    curlAvailable = true,
                ),
                onAction = {},
            )
        }
    }
}

@Preview
@Composable
private fun ShareSheetBody_SessionDarkPreview() {
    SharinganTheme(darkTheme = true) {
        Box(Modifier.background(LocalSharinganColors.current.bgElev)) {
            ShareSheetBody(
                state = ShareSheetState(
                    scope = ShareScope.ALL,
                    protocol = Protocol.MQTT,
                    preview = "# Sharingan session export\n4 events · HTTP 0 · MQTT 4 · BLE 0",
                    curlAvailable = false,
                ),
                onAction = {},
            )
        }
    }
}
