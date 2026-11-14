package com.athar.app.feature.counters

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.athar.app.R

/** Localized label for where a counter stands, e.g. "remaining", "since". */
@Composable
fun CounterReading.label(): String = stringResource(
    when (phase) {
        CounterPhase.Remaining -> R.string.counter_until
        CounterPhase.Passed -> R.string.counter_passed
        CounterPhase.Since -> R.string.counter_since
        CounterPhase.StartsIn -> R.string.counter_starts_in
    },
)
