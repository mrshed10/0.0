package com.athar.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.format.currentLocale
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Hour / minute steppers (hold to scroll quickly). */
@Composable
fun TimeStepper(time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper(
            stringResource(R.string.hour_label),
            DateTimeFormatter.ofPattern("h a", locale).format(time),
            { n -> onChange(time.plusHours(n)) },
            Modifier.weight(1f),
        )
        Stepper(
            stringResource(R.string.minute_label),
            time.minute.toString().padStart(2, '0'),
            { n -> onChange(time.withMinute(((time.minute + n).mod(60)).toInt())) },
            Modifier.weight(1f),
        )
    }
}
