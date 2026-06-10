package dev.sharingan.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The design's stroke icon set, transcribed from its SVG paths and rendered
 * as template [ImageVector]s (tint applies the color). Hand-built to avoid
 * pulling the material-icons artifact into consumers' debug builds.
 */
internal object SharinganIcons {

    val Search: ImageVector by lazy {
        stroke("search") {
            // circle approximated with two arcs
            moveTo(10.5f, 3.5f)
            arcTo(7f, 7f, 0f, true, true, 10.49f, 3.5f)
            moveTo(16f, 16f)
            lineTo(20.5f, 20.5f)
        }
    }

    val Close: ImageVector by lazy {
        stroke("close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Share: ImageVector by lazy {
        stroke("share") {
            moveTo(12f, 3f); lineTo(12f, 16f)
            moveTo(12f, 3f); lineTo(8f, 7f)
            moveTo(12f, 3f); lineTo(16f, 7f)
            moveTo(5f, 13f); lineTo(5f, 19f)
            arcTo(1f, 1f, 0f, false, false, 6f, 20f)
            lineTo(18f, 20f)
            arcTo(1f, 1f, 0f, false, false, 19f, 19f)
            lineTo(19f, 13f)
        }
    }

    val Copy: ImageVector by lazy {
        stroke("copy") {
            moveTo(10.5f, 8f)
            lineTo(17.5f, 8f)
            arcTo(2.5f, 2.5f, 0f, false, true, 20f, 10.5f)
            lineTo(20f, 17.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 17.5f, 20f)
            lineTo(10.5f, 20f)
            arcTo(2.5f, 2.5f, 0f, false, true, 8f, 17.5f)
            lineTo(8f, 10.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 10.5f, 8f)
            close()
            moveTo(16f, 8f); lineTo(16f, 5.5f)
            arcTo(1.5f, 1.5f, 0f, false, false, 14.5f, 4f)
            lineTo(5.5f, 4f)
            arcTo(1.5f, 1.5f, 0f, false, false, 4f, 5.5f)
            lineTo(4f, 14.5f)
            arcTo(1.5f, 1.5f, 0f, false, false, 5.5f, 16f)
            lineTo(8f, 16f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        stroke("chevronRight", width = 2.2f) {
            moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
        }
    }

    val Back: ImageVector by lazy {
        stroke("back", width = 2.2f) {
            moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
        }
    }

    val Check: ImageVector by lazy {
        stroke("check", width = 2.4f) {
            moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7f)
        }
    }

    val Bolt: ImageVector by lazy {
        fill("bolt") {
            moveTo(13f, 2f); lineTo(4f, 14f); lineTo(10f, 14f)
            lineTo(9f, 22f); lineTo(18f, 10f); lineTo(12f, 10f); close()
        }
    }

    /** The "AI agent" robot face used by the share sheet's primary action. */
    val Agent: ImageVector by lazy {
        ImageVector.Builder(
            name = "sharingan.agent",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = null,
            ) {
                moveTo(7f, 7f); lineTo(17f, 7f)
                arcTo(3f, 3f, 0f, false, true, 20f, 10f)
                lineTo(20f, 16f)
                arcTo(3f, 3f, 0f, false, true, 17f, 19f)
                lineTo(7f, 19f)
                arcTo(3f, 3f, 0f, false, true, 4f, 16f)
                lineTo(4f, 10f)
                arcTo(3f, 3f, 0f, false, true, 7f, 7f)
                close()
                moveTo(12f, 4f); lineTo(12f, 7f)
                moveTo(9f, 19f); lineTo(9f, 21f)
                moveTo(15f, 19f); lineTo(15f, 21f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 11.6f)
                arcTo(1.4f, 1.4f, 0f, true, true, 8.99f, 11.6f)
                moveTo(15f, 11.6f)
                arcTo(1.4f, 1.4f, 0f, true, true, 14.99f, 11.6f)
            }
        }.build()
    }

    /** HTTP tab: globe. */
    val Globe: ImageVector by lazy {
        stroke("globe", width = 1.9f) {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
            moveTo(3f, 12f); lineTo(21f, 12f)
            moveTo(12f, 3f)
            curveTo(14.5f, 5.4f, 14.5f, 18.6f, 12f, 21f)
            moveTo(12f, 3f)
            curveTo(9.5f, 5.4f, 9.5f, 18.6f, 12f, 21f)
        }
    }

    /** MQTT tab: broadcast waves. */
    val Waves: ImageVector by lazy {
        stroke("waves") {
            moveTo(4f, 19f)
            arcTo(3f, 3f, 0f, false, true, 7f, 22f)
            moveTo(4f, 13f)
            arcTo(9f, 9f, 0f, false, true, 13f, 22f)
            moveTo(4f, 7f)
            arcTo(15f, 15f, 0f, false, true, 19f, 22f)
        }
    }

    /** Bluetooth tab: the BLE rune. */
    val Bluetooth: ImageVector by lazy {
        stroke("bluetooth", width = 1.9f) {
            moveTo(8f, 7f); lineTo(16f, 12f); lineTo(11f, 16f)
            lineTo(11f, 4f); lineTo(16f, 8f); lineTo(8f, 13f)
        }
    }

    private fun stroke(
        name: String,
        width: Float = 2f,
        block: PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = "sharingan.$name",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            fill = null,
            pathBuilder = block,
        )
    }.build()

    private fun fill(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "sharingan.$name",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = block)
        }.build()
}
