package app.safar.browser.ui.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.safar.browser.translate.TranslateStatus

@Composable
fun TranslateStatusChip(status: TranslateStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (status.error != null) Color(0xCCB3261E) else Color(0xB3000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status.busy) CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
        else Icon(Icons.Filled.Translate, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                status.error != null -> "خطأ في الترجمة"
                status.lastProvider != null -> status.lastProvider.labelAr
                else -> "الترجمة مفعّلة"
            },
            color = Color.White, fontSize = 12.sp,
        )
    }
}
