package com.abdellatif.fleur

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.abdellatif.fleur.data.Preferences
import com.abdellatif.fleur.ui.FleurDesign
import com.abdellatif.fleur.ui.FleurSwitch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FleurSwitchAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun thumbsStayWhiteAndToggleInBothThemes() {
        var mode by mutableStateOf("Dark")
        var checked by mutableStateOf(false)
        compose.setContent {
            FleurDesign(Preferences(darkMode = mode)) {
                Row { FleurSwitch(checked, { checked = it }, "Example switch") }
            }
        }
        for (theme in listOf("Dark", "Light")) {
            compose.runOnIdle {
                mode = theme
                checked = false
            }
            val control = compose.onNodeWithContentDescription("Example switch")
            control.assertIsOff()
            for (on in listOf(false, true)) {
                if (on) control.performClick().assertIsOn()
                compose.mainClock.advanceTimeBy(300)
                compose.waitForIdle()
                val pixels = control.captureToImage().toPixelMap()
                val x = (pixels.width * (if (on) 33f else 15f) / 48f).toInt()
                val thumb = pixels[x, pixels.height / 2]
                val channels = listOf(thumb.red, thumb.green, thumb.blue)
                // Allow capture rounding and a fading touch layer, but reject theme tint.
                assertTrue(
                    "White thumb in $theme, checked=$on: $thumb",
                    channels.min() >= .98f &&
                        channels.max() - channels.min() <= .02f &&
                        thumb.alpha >= .99f,
                )
            }
        }
    }
}
