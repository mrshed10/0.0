package app.safar.browser.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.safar.browser.data.AddressBarPosition
import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.SearchEngines

/** إعداد أولي سريع في 3 خطوات؛ كل الخيارات قابلة للتعديل لاحقاً من الإعدادات. */
@Composable
fun OnboardingScreen(initial: BrowserSettings, onFinish: (BrowserSettings, makeDefault: Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var s by remember { mutableStateOf(initial) }
    var makeDefault by remember { mutableStateOf(true) }
    val steps = 3

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().padding(20.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> {
                    Title("مرحباً بك في سفر", "متصفح خفيف يحترم خصوصيتك. اختر محرك البحث الافتراضي:")
                    SearchEngines.all.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().selectable(s.searchEngineId == e.id) { s = s.copy(searchEngineId = e.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.searchEngineId == e.id, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(e.name, style = MaterialTheme.typography.titleMedium)
                                if (e.privateByDesign) Text("لا يتتبع عمليات البحث", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                1 -> {
                    Title("الحماية", "يمكنك تعديل هذه الخيارات أو استثناء مواقع لاحقاً.")
                    ToggleCard("حظر الإعلانات والمتتبعات", "صفحات أسرع وأقل استهلاكاً للبيانات", s.adBlock) { s = s.copy(adBlock = it) }
                    ToggleCard("فرض HTTPS", "اتصالات مشفرة دائماً", s.httpsOnly) { s = s.copy(httpsOnly = it) }
                    ToggleCard("مقاومة البصمة الرقمية", "إخفاء IP المحلي وتشويش Canvas", s.blockCanvas && s.hideLocalIp) {
                        s = s.copy(blockCanvas = it, hideLocalIp = it)
                    }
                }
                2 -> {
                    Title("مكان شريط العنوان", "الأسفل أسهل للوصول بإبهام واحد.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BarPreview("أعلى", top = true, selected = s.addressBar == AddressBarPosition.TOP, Modifier.weight(1f)) {
                            s = s.copy(addressBar = AddressBarPosition.TOP)
                        }
                        BarPreview("أسفل", top = false, selected = s.addressBar == AddressBarPosition.BOTTOM, Modifier.weight(1f)) {
                            s = s.copy(addressBar = AddressBarPosition.BOTTOM)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                        Text("تعيين سفر كمتصفح افتراضي")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) OutlinedButton(onClick = { step-- }) { Text("السابق") } else Spacer(Modifier.width(1.dp))
            Button(onClick = { if (step < steps - 1) step++ else onFinish(s, makeDefault) }) {
                Text(if (step < steps - 1) "التالي" else "ابدأ التصفح")
            }
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun BarPreview(label: String, top: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier.height(220.dp),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            if (top) FakeBar()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.titleMedium) }
            if (!top) FakeBar()
        }
    }
}

@Composable
private fun FakeBar() = Box(
    Modifier.fillMaxWidth().height(22.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(11.dp)),
) { Box(Modifier.padding(6.dp).size(10.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))) }
