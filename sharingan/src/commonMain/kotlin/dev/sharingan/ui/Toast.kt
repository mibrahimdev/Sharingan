package dev.sharingan.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The design's confirmation pill ("Copied for agent ✓"), bottom-centered. */
@Composable
internal fun SharinganToast(message: String?, modifier: Modifier = Modifier) {
    val colors = LocalSharinganColors.current
    // Keep the last text visible while the pill animates out.
    var lastMessage by remember { mutableStateOf("") }
    if (message != null) lastMessage = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .padding(bottom = 46.dp)
                .shadow(14.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(if (colors.isDark) Color(0xFF23272F) else Color(0xFF16191D))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(SharinganIcons.Check, contentDescription = null, tint = Color(0xFF3DD68C), modifier = Modifier.size(15.dp))
            Text(lastMessage, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = SansFont)
        }
    }
}
