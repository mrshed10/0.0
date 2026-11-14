package com.athar.app.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Text that is either a resource (built-in/sample content) or user-entered. */
sealed interface UiText {
    data class Res(@StringRes val id: Int) : UiText
    data class Raw(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id)
    is UiText.Raw -> value
}
