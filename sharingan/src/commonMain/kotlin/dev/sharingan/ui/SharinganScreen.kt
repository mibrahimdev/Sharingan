package dev.sharingan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.sharingan.HttpEvent
import dev.sharingan.Sharingan
import dev.sharingan.SharinganEvent
import dev.sharingan.SharinganExport
import dev.sharingan.SharinganStore
import kotlinx.coroutines.delay

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
    var shareScope by remember { mutableStateOf<ShareScope?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Search and chip selection reset when the tab changes, like the design.
    var query by remember(protocol) { mutableStateOf("") }
    var chipKey by remember(protocol) { mutableStateOf("all") }

    val counts = remember(events) {
        Protocol.entries.associateWith { p -> events.count { protocolOf(it) == p } }
    }
    val rows = remember(events, protocol, chipKey, query) {
        visibleEvents(events, protocol, chipKey, query)
    }
    val selectedEvent = selectedId?.let { id -> events.firstOrNull { it.id == id } }
    val tabEvents = remember(events, protocol) { events.filter { protocolOf(it) == protocol } }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    fun sharePayload(action: ShareAction, scope: ShareScope): String {
        val single = if (scope == ShareScope.SINGLE) selectedEvent else null
        return when (action) {
            ShareAction.COPY_AGENT, ShareAction.SYSTEM_SHARE ->
                single?.let { SharinganExport.agentMarkdown(it) } ?: SharinganExport.agentMarkdown(tabEvents)
            ShareAction.COPY_HUMAN ->
                (single as? HttpEvent)?.let { SharinganExport.curl(it) }
                    ?: SharinganExport.summary(single?.let { listOf(it) } ?: tabEvents)
            ShareAction.COPY_RAW ->
                single?.let { SharinganExport.json(it) } ?: SharinganExport.sessionJson(tabEvents)
        }
    }

    val shareState = shareScope?.let { scope ->
        ShareSheetState(
            scope = scope,
            protocol = protocol,
            preview = sharePayload(ShareAction.COPY_AGENT, scope).take(400),
            curlAvailable = scope == ShareScope.SINGLE && selectedEvent is HttpEvent,
        )
    }

    SharinganTheme(darkTheme = darkTheme) {
        SharinganScreenContent(
            homeState = HomeUiState(
                protocol = protocol,
                counts = counts,
                rows = rows,
                query = query,
                chipKey = chipKey,
                recording = recording,
            ),
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
            onShareSingle = { shareScope = ShareScope.SINGLE },
            onShareAll = { shareScope = ShareScope.ALL },
            onShareAction = { action ->
                val scope = shareScope ?: ShareScope.ALL
                val payload = sharePayload(action, scope)
                when (action) {
                    ShareAction.COPY_AGENT -> {
                        dev.sharingan.internal.copyToClipboard(payload)
                        toastMessage = "Copied for agent ✓"
                    }
                    ShareAction.COPY_HUMAN -> {
                        dev.sharingan.internal.copyToClipboard(payload)
                        toastMessage = "Copied ✓"
                    }
                    ShareAction.COPY_RAW -> {
                        dev.sharingan.internal.copyToClipboard(payload)
                        toastMessage = "Copied JSON ✓"
                    }
                    ShareAction.SYSTEM_SHARE -> dev.sharingan.internal.shareText(payload)
                }
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
    selectedEvent: SharinganEvent?,
    shareState: ShareSheetState?,
    toastMessage: String?,
    onSelectProtocol: (Protocol) -> Unit,
    onQueryChange: (String) -> Unit,
    onChipChange: (String) -> Unit,
    onToggleRecording: () -> Unit,
    onOpenEvent: (SharinganEvent) -> Unit,
    onBack: () -> Unit,
    onShareSingle: () -> Unit,
    onShareAll: () -> Unit,
    onShareAction: (ShareAction) -> Unit,
    onShareDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSharinganColors.current
    PlatformBackHandler(enabled = selectedEvent != null, onBack = onBack)
    Scaffold(
        modifier = modifier,
        containerColor = colors.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
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
                )
            }
            SharinganToast(toastMessage, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (shareState != null) {
        ShareSheet(state = shareState, onAction = onShareAction, onDismiss = onShareDismiss)
    }
}
