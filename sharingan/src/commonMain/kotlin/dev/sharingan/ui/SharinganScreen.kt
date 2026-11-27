package dev.sharingan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.sharingan.HttpEvent
import dev.sharingan.Sharingan
import dev.sharingan.SharinganEvent
import dev.sharingan.SharinganStore
import dev.sharingan.db.SessionSummary
import dev.sharingan.internal.Persistence
import dev.sharingan.internal.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.ui.tooling.preview.Preview

// Sheet hosts sit below the status bar, so the top inset would be paid twice (#42); full-screen hosts still need it.
internal val LocalStripTopInset = staticCompositionLocalOf { false }

internal fun sharinganContentInsets(
    safeDrawing: WindowInsets,
    stripTop: Boolean,
): WindowInsets =
    if (stripTop) {
        safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    } else {
        safeDrawing
    }

/**
 * The Sharingan log browser: home (three protocol tabs), event detail, and
 * the share sheet — the whole flow from the design, in one composable.
 *
 * Drop it anywhere: it fills its container, applies its own light/dark theme
 * and consumes safe-drawing insets. Android apps usually never call this —
 * the capture notification opens `SharinganActivity` which hosts it. On iOS,
 * present `SharinganViewController()` (which wraps this) from Swift.
 */
@Composable
public fun SharinganScreen(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    store: SharinganStore = Sharingan.store,
) {
    val events by store.events.collectAsState()
    val recording by store.isRecording.collectAsState()

    var protocolName by rememberSaveable { mutableStateOf(Protocol.HTTP.name) }
    val protocol = Protocol.valueOf(protocolName)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSessionStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var sessionEvents by remember { mutableStateOf<List<SharinganEvent>?>(null) }
    var sessionPickerOpen by remember { mutableStateOf(false) }
    var pastSessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var loadingPastSessions by remember { mutableStateOf(false) }
    var shareScope by remember { mutableStateOf<ShareScope?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Search and chip selection reset when the tab changes, like the design.
    var query by remember(protocol) { mutableStateOf("") }
    var chipKey by remember(protocol) { mutableStateOf("all") }

    val activeEvents = sessionEvents ?: events
    val counts =
        remember(activeEvents) {
            Protocol.entries.associateWith { p -> activeEvents.count { protocolOf(it) == p } }
        }
    val rows =
        remember(activeEvents, protocol, chipKey, query) {
            visibleEvents(activeEvents, protocol, chipKey, query)
        }
    val selectedEvent = selectedId?.let { id -> activeEvents.firstOrNull { it.id == id } }
    val tabEvents = remember(activeEvents, protocol) { activeEvents.filter { protocolOf(it) == protocol } }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    LaunchedEffect(sessionPickerOpen) {
        if (sessionPickerOpen) {
            loadingPastSessions = true
            pastSessions = withContext(Dispatchers.Default) { Persistence.pastSessions() }
            loadingPastSessions = false
        }
    }

    LaunchedEffect(selectedSessionId) {
        selectedSessionId?.let { id ->
            sessionEvents = withContext(Dispatchers.Default) { Persistence.sessionEvents(id) }
        }
    }

    val shareState =
        shareScope?.let { scope ->
            ShareSheetState(
                scope = scope,
                protocol = protocol,
                preview = resolveShare(ShareAction.COPY_AGENT, scope, selectedEvent, tabEvents).payload.take(400),
                curlAvailable = scope == ShareScope.SINGLE && selectedEvent is HttpEvent,
            )
        }

    SharinganTheme(darkTheme = darkTheme) {
        SharinganScreenContent(
            homeState =
                HomeUiState(
                    protocol = protocol,
                    counts = counts,
                    rows = rows,
                    query = query,
                    chipKey = chipKey,
                    recording = recording,
                    pastSession = selectedSessionId != null,
                    sessionLabel =
                        selectedSessionStartedAt?.let(::formatDateTime)
                            ?: if (selectedSessionId == null) "Current session ▾" else "Past session",
                ),
            sessionPickerState =
                if (sessionPickerOpen) {
                    SessionPickerState(
                        currentEventCount = events.size,
                        pastSessions = pastSessions,
                        selectedSessionId = selectedSessionId,
                        isLoading = loadingPastSessions,
                    )
                } else {
                    null
                },
            selectedEvent = selectedEvent,
            shareState = shareState,
            toastMessage = toastMessage,
            onSelectProtocol = {
                protocolName = it.name
                selectedId = null
            },
            onQueryChange = { query = it },
            onChipChange = { chipKey = it },
            onToggleRecording = { store.setRecording(!recording) },
            onOpenEvent = { selectedId = it.id },
            onBack = { selectedId = null },
            onOpenSessionPicker = { sessionPickerOpen = true },
            onSelectCurrentSession = {
                selectedId = null
                selectedSessionId = null
                selectedSessionStartedAt = null
                sessionEvents = null
                sessionPickerOpen = false
            },
            onSelectPastSession = { session ->
                selectedId = null
                if (selectedSessionId != session.id || sessionEvents == null) {
                    sessionEvents = emptyList()
                    selectedSessionId = session.id
                }
                selectedSessionStartedAt = session.startedAtMillis
                sessionPickerOpen = false
            },
            onReturnToLive = {
                selectedId = null
                selectedSessionId = null
                selectedSessionStartedAt = null
                sessionEvents = null
            },
            onSessionPickerDismiss = { sessionPickerOpen = false },
            onShareSingle = { shareScope = ShareScope.SINGLE },
            onShareAll = { shareScope = ShareScope.ALL },
            onShareAction = { action ->
                val resolution = resolveShare(action, shareScope ?: ShareScope.ALL, selectedEvent, tabEvents)
                when (resolution.delivery) {
                    ShareDelivery.CLIPBOARD -> dev.sharingan.internal.copyToClipboard(resolution.payload)
                    ShareDelivery.SYSTEM_SHARE -> dev.sharingan.internal.shareText(resolution.payload)
                }
                resolution.toast?.let { toastMessage = it }
                shareScope = null
            },
            onShareDismiss = { shareScope = null },
            modifier = modifier,
        )
    }
}

/** Stateless body of [SharinganScreen]; everything it shows arrives as parameters. */
@Composable
internal fun SharinganScreenContent(
    homeState: HomeUiState,
    sessionPickerState: SessionPickerState?,
    selectedEvent: SharinganEvent?,
    shareState: ShareSheetState?,
    toastMessage: String?,
    onSelectProtocol: (Protocol) -> Unit,
    onQueryChange: (String) -> Unit,
    onChipChange: (String) -> Unit,
    onToggleRecording: () -> Unit,
    onOpenEvent: (SharinganEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSessionPicker: () -> Unit,
    onSelectCurrentSession: () -> Unit,
    onSelectPastSession: (SessionSummary) -> Unit,
    onReturnToLive: () -> Unit,
    onSessionPickerDismiss: () -> Unit,
    onShareSingle: () -> Unit,
    onShareAll: () -> Unit,
    onShareAction: (ShareAction) -> Unit,
    onShareDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSharinganColors.current
    PlatformBackHandler(enabled = selectedEvent != null, onBack = onBack)
    // The logger is a locale-neutral surface — always LTR, on every platform and preview (#38).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
            modifier = modifier,
            containerColor = colors.bg,
            contentWindowInsets = sharinganContentInsets(WindowInsets.safeDrawing, LocalStripTopInset.current),
        ) { innerPadding ->
            Box(
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .fillMaxSize(),
            ) {
                if (selectedEvent != null) {
                    DetailScreenContent(event = selectedEvent, onBack = onBack, onShare = onShareSingle)
                } else {
                    HomeScreenContent(
                        state = homeState,
                        onSelectProtocol = onSelectProtocol,
                        onQueryChange = onQueryChange,
                        onChipChange = onChipChange,
                        onToggleRecording = onToggleRecording,
                        onOpenEvent = onOpenEvent,
                        onShareAll = onShareAll,
                        onOpenSessionPicker = onOpenSessionPicker,
                        onReturnToLive = onReturnToLive,
                    )
                }
                SharinganToast(toastMessage, Modifier.align(Alignment.BottomCenter))
            }
        }
        if (shareState != null) {
            ShareSheet(state = shareState, onAction = onShareAction, onDismiss = onShareDismiss)
        }
        if (sessionPickerState != null) {
            SessionPickerSheet(
                state = sessionPickerState,
                onSelectCurrent = onSelectCurrentSession,
                onSelectPast = onSelectPastSession,
                onDismiss = onSessionPickerDismiss,
            )
        }
    }
}

@Preview
@Composable
private fun SharinganScreenContent_PastSessionPreview() {
    SharinganTheme(darkTheme = false) {
        SharinganScreenContent(
            homeState =
                HomeUiState(
                    protocol = Protocol.HTTP,
                    counts = previewCounts(),
                    rows = PreviewData.http,
                    query = "",
                    chipKey = "all",
                    recording = true,
                    pastSession = true,
                    sessionLabel = "Jun 10, 2025 14:22",
                ),
            sessionPickerState = null,
            selectedEvent = null,
            shareState = null,
            toastMessage = null,
            onSelectProtocol = {},
            onQueryChange = {},
            onChipChange = {},
            onToggleRecording = {},
            onOpenEvent = {},
            onBack = {},
            onOpenSessionPicker = {},
            onSelectCurrentSession = {},
            onSelectPastSession = {},
            onReturnToLive = {},
            onSessionPickerDismiss = {},
            onShareSingle = {},
            onShareAll = {},
            onShareAction = {},
            onShareDismiss = {},
        )
    }
}
