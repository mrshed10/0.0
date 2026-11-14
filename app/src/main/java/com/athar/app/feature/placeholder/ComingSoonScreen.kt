package com.athar.app.feature.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors

@Composable
fun ComingSoonScreen(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = AtharColors.Gold, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = AtharColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
