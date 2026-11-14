package com.athar.app.core.designsystem.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Lets overlays (sheets) hide the floating bottom bar while they are open. */
class BottomBarController {
    private var hiders by mutableIntStateOf(0)
    val visible: Boolean get() = hiders == 0

    fun hide() { hiders++ }
    fun show() { if (hiders > 0) hiders-- }
}

val LocalBottomBarController = staticCompositionLocalOf { BottomBarController() }
