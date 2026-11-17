package com.athar.app.core.notify

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.designsystem.theme.AtharFontsTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Shown over the lock screen while the adhan plays (like an alarm clock), so
 * it can be stopped with one tap without unlocking the phone.
 */
class AdhanAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AtharFontsTheme {
                val playback by AdhanService.playback.collectAsState()
                val seen = remember { booleanArrayOf(false) }
                LaunchedEffect(playback) {
                    if (playback != null) seen[0] = true
                    // The adhan ended (or was stopped elsewhere): close the screen.
                    if (playback == null && seen[0]) finish()
                }
                LaunchedEffect(Unit) {
                    // Opened late, after the adhan already ended.
                    kotlinx.coroutines.delay(1_500)
                    if (AdhanService.playback.value == null) finish()
                }
                val title = playback?.title.orEmpty()
                val text = playback?.text.orEmpty()
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
                    label = "scale",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF05070F), Color(0xFF1B1F4A), Color(0xFF3B2A12)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm")),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Light,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("🕌", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = AtharColors.Gold,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(text, style = MaterialTheme.typography.bodyLarge, color = AtharColors.TextSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(48.dp))
                        Box(
                            Modifier
                                .size(132.dp)
                                .scale(pulse)
                                .background(AtharColors.Gold.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(104.dp)
                                    .background(AtharColors.Gold, CircleShape)
                                    .clickable {
                                        AdhanService.stop(this@AdhanAlarmActivity)
                                        finish()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null, tint = AtharColors.Night, modifier = Modifier.size(52.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.adhan_stop),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.adhan_stop_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = AtharColors.TextTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
