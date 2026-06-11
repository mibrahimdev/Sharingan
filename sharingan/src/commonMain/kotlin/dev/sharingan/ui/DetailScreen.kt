package dev.sharingan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sharingan.SharinganEvent
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun DetailScreenContent(
    event: SharinganEvent,
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSharinganColors.current
    Column(modifier.fillMaxSize().background(colors.bg)) {
        DetailHeader(onBack = onBack, onShare = onShare)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TitleBlock(event)
            descriptorOf(event).DetailBody(event)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DetailHeader(onBack: () -> Unit, onShare: () -> Unit) {
    val colors = LocalSharinganColors.current
    Column(Modifier.fillMaxWidth().background(colors.bgElev)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(SharinganIcons.Back, contentDescription = null, tint = colors.accent, modifier = Modifier.size(17.dp))
                Text("Back", color = colors.accent, fontSize = 14.sp, fontFamily = SansFont, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent)
                    .clickable(onClick = onShare)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(SharinganIcons.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Share", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = SansFont)
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun TitleBlock(event: SharinganEvent) {
    val colors = LocalSharinganColors.current
    val p = presentationOf(colors, event)
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeChip(p.lead, p.leadTint)
            p.status?.let { BadgeChip(it, p.statusTint) }
            Spacer(Modifier.weight(1f))
            Text(p.clockTime, color = colors.textDim, fontSize = 11.sp, fontFamily = MonoFont)
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(p.main, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = MonoFont, lineHeight = 20.sp)
        }
        p.sub?.let {
            Spacer(Modifier.height(3.dp))
            Text(it, color = colors.textDim, fontSize = 11.5.sp, fontFamily = MonoFont)
        }
    }
    HorizontalDivider()
}

@Composable
internal fun BadgeChip(label: String, tint: Tint) {
    Text(
        label,
        color = tint.color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = MonoFont,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(tint.soft)
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .widthIn(min = 30.dp),
    )
}

// ── shared section scaffolding (used by the ProtocolDescriptors) ──

@Composable
internal fun Section(
    label: String,
    right: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalSharinganColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label.uppercase(),
                color = colors.textDim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = MonoFont,
                letterSpacing = 0.8.sp,
                modifier = Modifier.weight(1f),
            )
            right?.let { Text(it, color = colors.textDim, fontSize = 10.5.sp, fontFamily = MonoFont) }
        }
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 13.dp)) {
            content()
        }
        HorizontalDivider()
    }
}

@Composable
internal fun KeyValueRow(key: String, value: String, valueColor: Color? = null) {
    val colors = LocalSharinganColors.current
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(key, color = colors.textMid, fontSize = 11.5.sp, fontFamily = MonoFont, modifier = Modifier.width(96.dp))
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    value,
                    color = valueColor ?: colors.text,
                    fontSize = 11.5.sp,
                    fontFamily = MonoFont,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
internal fun ErrorSection(error: String) {
    val colors = LocalSharinganColors.current
    Section("Error") {
        Text(error, color = colors.err, fontSize = 11.5.sp, fontFamily = MonoFont, lineHeight = 17.sp)
    }
}

@Composable
internal fun BodyBlock(raw: String) {
    val colors = LocalSharinganColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        SelectionContainer {
            Text(
                rememberAnnotatedJson(raw, colors),
                fontSize = 11.5.sp,
                fontFamily = MonoFont,
                lineHeight = 19.sp,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun rememberAnnotatedJson(raw: String, colors: SharinganColors): AnnotatedString =
    remember(raw, colors) {
        val tokens = prettyJsonTokens(raw)
            ?: return@remember AnnotatedString(raw, SpanStyle(color = colors.text))
        buildAnnotatedString {
            for (token in tokens) {
                val color = when (token.type) {
                    JsonTokenType.KEY -> colors.info
                    JsonTokenType.STRING -> colors.ok
                    JsonTokenType.NUMBER -> colors.warn
                    JsonTokenType.LITERAL -> colors.violet
                    JsonTokenType.PUNCT -> colors.textDim
                    JsonTokenType.WS -> colors.textDim
                }
                pushStyle(SpanStyle(color = color))
                append(token.text)
                pop()
            }
        }
    }

// ── Previews ─────────────────────────────────────────────────

@Preview
@Composable
private fun DetailScreenContent_HttpErrorPreview() {
    SharinganTheme(darkTheme = true) {
        DetailScreenContent(event = PreviewData.http.last(), onBack = {}, onShare = {})
    }
}

@Preview
@Composable
private fun DetailScreenContent_HttpOkLightPreview() {
    SharinganTheme(darkTheme = false) {
        DetailScreenContent(event = PreviewData.http.first(), onBack = {}, onShare = {})
    }
}

@Preview
@Composable
private fun DetailScreenContent_MqttPreview() {
    SharinganTheme(darkTheme = false) {
        DetailScreenContent(event = PreviewData.mqtt.first(), onBack = {}, onShare = {})
    }
}

@Preview
@Composable
private fun DetailScreenContent_BlePreview() {
    SharinganTheme(darkTheme = true) {
        DetailScreenContent(event = PreviewData.ble[1], onBack = {}, onShare = {})
    }
}
