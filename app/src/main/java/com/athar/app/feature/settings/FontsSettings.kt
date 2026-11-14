package com.athar.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.fonts.FontInfo
import com.athar.app.core.fonts.FontRegistry
import com.athar.app.core.fonts.FontScript
import com.athar.app.core.format.currentLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FontSlot { Ui, Latin, Decorative }

const val FONT_SAMPLE = "بسم الله الرحمن الرحيم · Athar 2026"

/** A font name drawn in its own typeface. */
@Composable
fun FontSample(context: android.content.Context, info: FontInfo?, text: String = FONT_SAMPLE, size: Int = 18) {
    val family = remember(info?.id) { info?.let { FontRegistry.composeFamily(context, it.id) } ?: FontFamily.Default }
    Text(text, fontFamily = family, fontSize = size.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun FontsSettingsCard(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val locale = currentLocale()
    val scope = rememberCoroutineScope()
    val settings = (context.applicationContext as AtharApplication).container.settings
    val fonts by settings.fonts.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    val all = remember(version) { FontRegistry.all(context) }
    var picker by remember { mutableStateOf<FontSlot?>(null) }
    var importScript by remember { mutableStateOf(FontScript.Arabic) }
    val imported = stringResource(R.string.font_imported)
    val invalid = stringResource(R.string.font_invalid)

    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val info = withContext(Dispatchers.IO) { FontRegistry.import(context, uri, importScript) }
                version++
                onMessage(if (info != null) imported else invalid)
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Rose) {
        CardHeader(stringResource(R.string.fonts_title), Icons.Rounded.TextFields, tint = AtharColors.Rose)
        Spacer(Modifier.height(8.dp))
        SlotRow(stringResource(R.string.font_ui), all.firstOrNull { it.id == fonts.uiId }, locale, context) { picker = FontSlot.Ui }
        SlotRow(
            stringResource(R.string.font_latin),
            all.firstOrNull { it.id == fonts.latinId },
            locale,
            context,
            emptyLabel = stringResource(R.string.font_same_as_arabic),
        ) { picker = FontSlot.Latin }
        SlotRow(stringResource(R.string.font_decorative), all.firstOrNull { it.id == fonts.decorativeId }, locale, context) {
            picker = FontSlot.Decorative
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                stringResource(R.string.font_import_arabic),
                { importScript = FontScript.Arabic; importFont.launch(arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")) },
                color = AtharColors.Rose,
                filled = false,
                icon = Icons.Rounded.FontDownload,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                stringResource(R.string.font_import_latin),
                { importScript = FontScript.Latin; importFont.launch(arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")) },
                color = AtharColors.Rose,
                filled = false,
                icon = Icons.Rounded.FontDownload,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.fonts_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }

    picker?.let { slot ->
        GlassSheet(onDismiss = { picker = null }) {
            Text(
                stringResource(
                    when (slot) {
                        FontSlot.Ui -> R.string.font_ui
                        FontSlot.Latin -> R.string.font_latin
                        FontSlot.Decorative -> R.string.font_decorative
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            val selectedId = when (slot) {
                FontSlot.Ui -> fonts.uiId
                FontSlot.Latin -> fonts.latinId
                FontSlot.Decorative -> fonts.decorativeId
            }
            if (slot == FontSlot.Latin) {
                FontRow(null, stringResource(R.string.font_same_as_arabic), selectedId == null, context, onDelete = null) {
                    settings.updateFonts { it.copy(latinId = null) }
                    picker = null
                }
            }
            val choices = when (slot) {
                FontSlot.Latin -> all.sortedBy { it.script != FontScript.Latin }
                else -> all.filter { it.script == FontScript.Arabic }
            }
            choices.forEach { info ->
                FontRow(
                    info = info,
                    name = info.name(locale),
                    selected = info.id == selectedId,
                    context = context,
                    onDelete = if (info.imported) {
                        {
                            FontRegistry.delete(context, info.id)
                            settings.updateFonts {
                                it.copy(
                                    uiId = if (it.uiId == info.id) FontRegistry.DEFAULT_UI else it.uiId,
                                    latinId = if (it.latinId == info.id) null else it.latinId,
                                    decorativeId = if (it.decorativeId == info.id) FontRegistry.DEFAULT_DECORATIVE else it.decorativeId,
                                )
                            }
                            version++
                        }
                    } else {
                        null
                    },
                ) {
                    settings.updateFonts {
                        when (slot) {
                            FontSlot.Ui -> it.copy(uiId = info.id)
                            FontSlot.Latin -> it.copy(latinId = info.id)
                            FontSlot.Decorative -> it.copy(decorativeId = info.id)
                        }
                    }
                    picker = null
                }
            }
        }
    }
}

@Composable
private fun SlotRow(
    label: String,
    current: FontInfo?,
    locale: java.util.Locale,
    context: android.content.Context,
    emptyLabel: String = "",
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = AtharColors.TextSecondary, modifier = Modifier.weight(1f))
            Text(current?.name(locale) ?: emptyLabel, style = MaterialTheme.typography.labelLarge, color = AtharColors.Gold)
        }
        FontSample(context, current)
    }
}

@Composable
private fun FontRow(
    info: FontInfo?,
    name: String,
    selected: Boolean,
    context: android.content.Context,
    onDelete: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AtharColors.Gold.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) AtharColors.Gold else AtharColors.TextSecondary)
            FontSample(context, info, size = 20)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = AtharColors.Gold)
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = AtharColors.Rose)
            }
        }
    }
}
