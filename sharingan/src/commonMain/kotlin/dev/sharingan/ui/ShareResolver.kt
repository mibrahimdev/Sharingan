package dev.sharingan.ui

import dev.sharingan.HttpEvent
import dev.sharingan.SharinganEvent
import dev.sharingan.SharinganExport

/** How a resolved share leaves the app. */
internal enum class ShareDelivery { CLIPBOARD, SYSTEM_SHARE }

/** The complete outcome of a share-sheet action, ready to perform. */
internal data class ShareResolution(
    val payload: String,
    val delivery: ShareDelivery,
    /** Confirmation toast; null when the system sheet provides its own feedback. */
    val toast: String?,
)

/**
 * The share sheet's full decision table — which exporter, which delivery,
 * which toast — for every action × scope × event-type combination, so the
 * [SharinganScreen] wrapper stays branch-free.
 *
 * Single scope falls back to the whole tab when nothing is selected;
 * "human" format prefers curl but only HTTP events have one.
 */
internal fun resolveShare(
    action: ShareAction,
    scope: ShareScope,
    selectedEvent: SharinganEvent?,
    tabEvents: List<SharinganEvent>,
): ShareResolution {
    val single = if (scope == ShareScope.SINGLE) selectedEvent else null
    return when (action) {
        ShareAction.COPY_AGENT -> ShareResolution(
            payload = single?.let { SharinganExport.agentMarkdown(it) } ?: SharinganExport.agentMarkdown(tabEvents),
            delivery = ShareDelivery.CLIPBOARD,
            toast = "Copied for agent ✓",
        )
        ShareAction.COPY_HUMAN -> ShareResolution(
            payload = (single as? HttpEvent)?.let { SharinganExport.curl(it) }
                ?: SharinganExport.summary(single?.let { listOf(it) } ?: tabEvents),
            delivery = ShareDelivery.CLIPBOARD,
            toast = "Copied ✓",
        )
        ShareAction.COPY_RAW -> ShareResolution(
            payload = single?.let { SharinganExport.json(it) } ?: SharinganExport.sessionJson(tabEvents),
            delivery = ShareDelivery.CLIPBOARD,
            toast = "Copied JSON ✓",
        )
        ShareAction.SYSTEM_SHARE -> ShareResolution(
            payload = single?.let { SharinganExport.agentMarkdown(it) } ?: SharinganExport.agentMarkdown(tabEvents),
            delivery = ShareDelivery.SYSTEM_SHARE,
            toast = null,
        )
    }
}
