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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sharingan.db.SessionSummary
import dev.sharingan.internal.formatDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview

internal data class SessionPickerState(
    val currentEventCount: Int,
    val pastSessions: List<SessionSummary>,
    val selectedSessionId: String?,
    val isLoading: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionPickerSheet(
    state: SessionPickerState,
    onSelectCurrent: () -> Unit,
    onSelectPast: (SessionSummary) -> Unit,
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
        SessionPickerBody(state, onSelectCurrent, onSelectPast)
    }
}

@Composable
internal fun SessionPickerBody(
    state: SessionPickerState,
    onSelectCurrent: () -> Unit,
    onSelectPast: (SessionSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSharinganColors.current
    Column(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 26.dp)) {
        Text("Sessions", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = SansFont)
        Text(
            "Choose a session to inspect its captured events.",
            color = colors.textMid,
            fontSize = 12.5.sp,
            fontFamily = SansFont,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item(key = "current") {
                SessionRow(
                    title = "Current session",
                    subtitle = "● LIVE · ${state.currentEventCount} events",
                    selected = state.selectedSessionId == null,
                    onClick = onSelectCurrent,
                )
            }
            if (state.isLoading && state.pastSessions.isEmpty()) {
                item(key = "loading") {
                    Text(
                        "Loading past sessions…",
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = MonoFont,
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                    )
                }
            } else if (state.pastSessions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No past sessions yet",
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = MonoFont,
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                    )
                }
            } else {
                items(state.pastSessions, key = { it.id }) { session ->
                    SessionRow(
                        title = formatDateTime(session.startedAtMillis),
                        subtitle = "${session.eventCount} events",
                        badge = if (session.failureCount > 0) "${session.failureCount} failed" else null,
                        selected = state.selectedSessionId == session.id,
                        onClick = { onSelectPast(session) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val colors = LocalSharinganColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = SansFont)
            Text(
                subtitle,
                color = colors.textDim,
                fontSize = 11.sp,
                fontFamily = MonoFont,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        badge?.let {
            Text(it, color = colors.warn, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = MonoFont)
        }
        if (selected) {
            Icon(SharinganIcons.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(15.dp))
        }
    }
}

@Preview
@Composable
private fun SessionPickerBody_PastSessionsPreview() {
    SharinganTheme(darkTheme = false) {
        Box(Modifier.background(LocalSharinganColors.current.bgElev)) {
            SessionPickerBody(
                state =
                    SessionPickerState(
                        currentEventCount = 6,
                        pastSessions =
                            listOf(
                                SessionSummary("session-1", 1_749_556_771_000L, 18, 2),
                                SessionSummary("session-2", 1_749_470_371_000L, 9, 0),
                            ),
                        selectedSessionId = "session-1",
                        isLoading = false,
                    ),
                onSelectCurrent = {},
                onSelectPast = {},
            )
        }
    }
}
