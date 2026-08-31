package dev.sharingan.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ScreenInsetsTest {
    @Test
    fun `Given strip=true When content insets built Then top side absent`() {
        val density = Density(1f)
        val source = WindowInsets(left = 7, top = 13, right = 17, bottom = 23)

        val stripped = sharinganContentInsets(source, stripTop = true)

        assertEquals(0, stripped.getTop(density))
        assertEquals(7, stripped.getLeft(density, LayoutDirection.Ltr))
        assertEquals(17, stripped.getRight(density, LayoutDirection.Ltr))
        assertEquals(23, stripped.getBottom(density))
        assertEquals(13, sharinganContentInsets(source, stripTop = false).getTop(density))
    }
}
