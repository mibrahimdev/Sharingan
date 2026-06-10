package dev.sharingan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Sharingan brand mark: red iris with a radial sheen, a pupil and three
 * tomoe dots. Drawn with Canvas — no image assets shipped.
 */
@Composable
internal fun SharinganMark(
    size: Dp = 22.dp,
    ink: Color = Color(0xFF0B0C0F),
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFFFF5A4E),
                    0.55f to Color(0xFFE5342B),
                    1.0f to Color(0xFFA21C16),
                ),
                center = Offset(center.x - r * 0.24f, center.y - r * 0.4f),
                radius = r * 1.6f,
            ),
            radius = r,
            center = center,
        )
        drawCircle(color = ink.copy(alpha = 0.22f), radius = r * 0.965f, center = center, style = Stroke(r * 0.07f))
        drawCircle(color = ink.copy(alpha = 0.55f), radius = r * 0.57f, center = center, style = Stroke(r * 0.065f))
        drawCircle(color = ink, radius = r * 0.23f, center = center)
        for (angleDeg in intArrayOf(0, 120, 240)) {
            val rad = (angleDeg - 90) * PI.toFloat() / 180f
            drawCircle(
                color = ink,
                radius = r * 0.155f,
                center = Offset(center.x + r * 0.57f * cos(rad), center.y + r * 0.57f * sin(rad)),
            )
        }
    }
}
