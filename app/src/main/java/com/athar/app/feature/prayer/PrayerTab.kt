package com.athar.app.feature.prayer

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.app.R
import com.athar.app.core.designsystem.component.AnimatedCountdown
import com.athar.app.core.designsystem.component.AnimatedRing
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.component.ScreenHeader
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatClock
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatHijri
import com.athar.app.core.notify.AlertKind
import com.athar.app.core.notify.AlertPermissionCards
import com.athar.app.core.notify.Notifier
import com.athar.app.core.notify.ToneRow
import com.athar.app.core.region.Cities
import com.athar.app.core.region.City
import java.time.Duration
import kotlin.math.roundToInt

/** Prayer tab: times & adhan, tasbih, adhkar. */
@Composable
fun PrayerTabRoute(viewModel: PrayerViewModel = viewModel(factory = PrayerViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var page by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val locale = currentLocale()
    var citySheet by remember { mutableStateOf(false) }
    var methodSheet by remember { mutableStateOf(false) }
    var prayerSheet by remember { mutableStateOf<Prayer?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(R.string.nav_prayer),
                    subtitle = state.city?.name(locale) ?: stringResource(R.string.my_location),
                )
            }
            item(key = "switcher") {
                GlassSegmented(
                    options = listOf(
                        stringResource(R.string.prayer_times),
                        stringResource(R.string.tasbih),
                        stringResource(R.string.adhkar),
                    ),
                    selectedIndex = page,
                    onSelect = { page = it },
                )
            }
            when (page) {
                0 -> timesPage(
                    state = state,
                    viewModel = viewModel,
                    onPickCity = { citySheet = true },
                    onPickMethod = { methodSheet = true },
                    onOpenPrayer = { prayerSheet = it },
                )
                1 -> item(key = "tasbih") {
                    TasbihPanel(
                        state = state.tasbih,
                        today = state.now.toLocalDate(),
                        onTap = viewModel::tasbihTap,
                        onReset = viewModel::tasbihReset,
                        onPreset = viewModel::tasbihPreset,
                        onCustom = viewModel::tasbihCustom,
                    )
                }
                else -> item(key = "adhkar") {
                    AdhkarPanel(
                        lists = state.adhkar,
                        onSave = viewModel::saveDhikr,
                        onDelete = viewModel::deleteDhikr,
                        onMove = viewModel::moveDhikr,
                        onReset = viewModel::resetAdhkar,
                    )
                }
            }
        }

        if (citySheet) {
            CitySheet(
                onSelect = { viewModel.selectCity(it); citySheet = false },
                onDismiss = { citySheet = false },
            )
        }
        prayerSheet?.let { prayer ->
            PrayerSettingsSheet(
                prayer = prayer,
                prefs = state.prefs,
                onDismiss = { prayerSheet = null },
                onSave = { alert, silent -> viewModel.saveAlert(prayer, alert, silent); prayerSheet = null },
                onApplyAll = { alert, silent -> viewModel.applyToAll(alert, silent); prayerSheet = null },
            )
        }
        if (methodSheet) {
            GlassSheet(onDismiss = { methodSheet = false }) {
                Text(stringResource(R.string.calc_method), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                PrayerMethod.entries.forEach { method ->
                    SelectRow(
                        text = stringResource(method.label),
                        selected = method == state.prefs.method,
                        onClick = { viewModel.setMethod(method); methodSheet = false },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.timesPage(
    state: PrayerUiState,
    viewModel: PrayerViewModel,
    onPickCity: () -> Unit,
    onPickMethod: () -> Unit,
    onOpenPrayer: (Prayer) -> Unit,
) {
    item(key = "next") { NextPrayerHero(state) }
    state.current?.let { item(key = "current") { CurrentPrayerCard(state, it) } }
    item(key = "list") { TimesCard(state, viewModel::toggleAdhan, onOpen = onOpenPrayer) }
    item(key = "perm") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AlertPermissionCards() } }
    item(key = "location") { LocationCard(state, onPickCity, viewModel::useLocation) }
    item(key = "qibla") { QiblaCard(state.qibla) }
    item(key = "method") {
        GlassCard(Modifier.fillMaxWidth()) {
            CardHeader(stringResource(R.string.calc_settings), Icons.Rounded.Calculate, tint = AtharColors.Violet)
            Spacer(Modifier.height(12.dp))
            SelectRow(
                text = stringResource(R.string.calc_method) + ": " + stringResource(state.prefs.method.label),
                selected = false,
                onClick = onPickMethod,
            )
            Spacer(Modifier.height(10.dp))
            FieldLabel(stringResource(R.string.asr_madhab))
            Spacer(Modifier.height(6.dp))
            GlassSegmented(
                options = listOf(stringResource(R.string.asr_standard), stringResource(R.string.asr_hanafi)),
                selectedIndex = state.prefs.madhab.ordinal,
                onSelect = { viewModel.setMadhab(AsrMadhab.entries[it]) },
                accent = AtharColors.Violet,
            )
        }
    }
    item(key = "audio") { AdhanAudioCard(state.prefs, viewModel) }
    item(key = "silent") { SilentModeCard(state.prefs, viewModel) }
    item(key = "reset") { ResetPrayerDefaults(viewModel::resetDefaults) }
}

@Composable
private fun NextPrayerHero(state: PrayerUiState) {
    val locale = currentLocale()
    val left = Duration.between(state.now, state.next.at).coerceAtLeast(Duration.ZERO)
    val seconds = left.seconds
    val hms = String.format(java.util.Locale.ENGLISH, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    val total = Duration.between(state.previousAt, state.next.at).seconds.coerceAtLeast(1)
    val progress = 1f - seconds.toFloat() / total
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Gold, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.next_prayer), style = MaterialTheme.typography.labelLarge, color = AtharColors.Gold)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            AnimatedRing(progress, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(state.next.prayer.label), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                AnimatedCountdown(
                    hms,
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = AtharColors.Gold),
                )
                Text(state.next.at.toLocalTime().formatClock(locale), style = MaterialTheme.typography.titleSmall, color = AtharColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            state.now.toLocalDate().formatGregorian(locale, "EEEE d MMMM") + " · " + state.now.toLocalDate().formatHijri(),
            style = MaterialTheme.typography.labelMedium,
            color = AtharColors.TextTertiary,
        )
    }
}

@Composable
private fun TimesCard(state: PrayerUiState, onToggle: (Prayer) -> Unit, onOpen: (Prayer) -> Unit) {
    val locale = currentLocale()
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)) {
        Text(
            stringResource(R.string.prayer_tap_for_settings),
            style = MaterialTheme.typography.labelSmall,
            color = AtharColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
        state.times.forEach { slot ->
            val isNext = slot.prayer == state.next.prayer && state.next.at.toLocalDate() == state.now.toLocalDate()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isNext) AtharColors.Gold.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(enabled = slot.prayer.isObligatory) { onOpen(slot.prayer) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(slot.prayer.label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                    color = if (isNext) AtharColors.Gold else AtharColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(slot.time.formatClock(locale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (slot.prayer.isObligatory) {
                    val on = state.prefs.alert(slot.prayer).adhan
                    IconButton(onClick = { onToggle(slot.prayer) }) {
                        Icon(
                            if (on) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                            contentDescription = stringResource(R.string.adhan_toggle),
                            tint = if (on) AtharColors.Gold else AtharColors.TextTertiary,
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun LocationCard(state: PrayerUiState, onPickCity: () -> Unit, onLocation: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val locale = currentLocale()
    val failed = stringResource(R.string.location_failed)
    val fetch = {
        LocationHelper.current(context) { loc ->
            if (loc != null) onLocation(loc.latitude, loc.longitude) else Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetch()
    }
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Teal) {
        CardHeader(stringResource(R.string.location_title), Icons.Rounded.LocationOn, tint = AtharColors.Teal)
        Spacer(Modifier.height(6.dp))
        Text(
            state.city?.name(locale)
                ?: (stringResource(R.string.my_location) + " (%.3f, %.3f)".format(state.prefs.latitude, state.prefs.longitude)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(stringResource(R.string.choose_city), onPickCity, color = AtharColors.Teal, modifier = Modifier.weight(1f))
            GlassButton(
                stringResource(R.string.use_my_location),
                {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) fetch() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                color = AtharColors.Teal,
                filled = false,
                icon = Icons.Rounded.MyLocation,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QiblaCard(qibla: Double) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Navigation,
                    contentDescription = null,
                    tint = AtharColors.Gold,
                    modifier = Modifier.size(36.dp).rotate(qibla.toFloat()),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, tint = AtharColors.Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.qibla), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("${qibla.roundToInt()}°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.qibla_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
            }
        }
    }
}

@Composable
internal fun SelectRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AtharColors.Gold.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (selected) AtharColors.Gold else AtharColors.TextPrimary)
    }
}

@Composable
private fun CitySheet(onSelect: (City) -> Unit, onDismiss: () -> Unit) {
    val locale = currentLocale()
    var query by remember { mutableStateOf("") }
    GlassSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.choose_city), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        GlassTextField(query, { query = it }, stringResource(R.string.search_city), Modifier.fillMaxWidth())
        Cities.all
            .filter { query.isBlank() || it.nameAr.contains(query.trim()) || it.nameEn.contains(query.trim(), ignoreCase = true) }
            .forEach { city ->
                SelectRow("${city.country.flag}  ${city.name(locale)}", selected = false, onClick = { onSelect(city) })
            }
    }
}
