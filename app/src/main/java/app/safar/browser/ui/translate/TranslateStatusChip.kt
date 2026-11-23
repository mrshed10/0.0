package app.safar.browser.ui.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.safar.browser.translate.TranslateStatus

/** شارة حالة صغيرة + زر إظهار/إخفاء الترجمات بلمسة واحدة. */
@Composable
fun TranslateStatusChip(
    status: TranslateStatus,
    overlaysVisible: Boolean,
    onClick: () -> Unit,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (status.error != null && !status.busy) Color(0xCCB3261E) else Color(0x99000000))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .widthIn(max = 240.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status.busy || status.note != null) CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Filled.Translate, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    status.note != null -> status.note
                    status.busy -> "جارٍ الترجمة…"
                    status.error != null -> "تعذرت الترجمة — المس للتفاصيل"
                    status.lastProvider != null -> status.lastProvider.labelAr + (status.detectedSource?.let { " • $it" } ?: "")
                    else -> "الترجمة مفعّلة"
                },
                color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            if (overlaysVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription = "إظهار/إخفاء الترجمة",
            tint = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x99000000))
                .clickable(onClick = onToggleVisible)
                .padding(6.dp)
                .size(16.dp),
        )
    }
}
