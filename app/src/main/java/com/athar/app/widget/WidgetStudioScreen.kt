package com.athar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.athar.app.R
import com.athar.app.core.designsystem.component.ConfirmButton
import com.athar.app.core.designsystem.component.EmptyState
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.glass.HideBottomBar
import com.athar.app.core.designsystem.theme.AtharColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the studio editor is working on. */
private sealed interface StudioEdit {
    data class Placed(val appWidgetId: Int, val config: WidgetConfig, val size: DpSize?) : StudioEdit
    data class Design(val design: SavedDesign) : StudioEdit
}

/**
 * Widget Studio: your widgets on the home screen (edit / save / unlink),
 * your saved designs (add / edit / duplicate / share / delete), a custom
 * designer, and import of shared designs.
 */
@Composable
fun WidgetStudioScreen(onBack: () -> Unit, onOpenGallery: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { DesignLibrary(context) }
    val designs by library.designs.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<StudioEdit?>(null) }
    var pasteSheet by remember { mutableStateOf(false) }
    var exportJson by remember { mutableStateOf<String?>(null) }

    val msgImported = stringResource(R.string.studio_imported)
    val msgInvalid = stringResource(R.string.studio_import_invalid)
    val msgSaved = stringResource(R.string.studio_saved)
    val msgPin = stringResource(R.string.pin_requested)
    val msgPinNo = stringResource(R.string.pin_unsupported)
    val copySuffix = stringResource(R.string.studio_copy_suffix)
    val newName = stringResource(R.string.studio_new_design)
    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_LONG).show()

    fun importRaw(raw: String) {
        runCatching { DesignCodec.import(raw) }
            .onSuccess { library.addAll(it); page = 1; toast(msgImported.format(it.size)) }
            .onFailure { toast(msgInvalid) }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                }
                if (raw != null) importRaw(raw) else toast(msgInvalid)
            }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = exportJson
        if (uri != null && json != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    // Editor (full screen) for a placed widget or a saved design.
    when (val e = editing) {
        is StudioEdit.Placed -> {
            WidgetEditorScreen(
                initial = e.config,
                previewSize = e.size,
                onCancel = { editing = null },
                onSave = { config ->
                    WidgetStore(context).put(e.appWidgetId, config)
                    scope.launch { WidgetUpdater.refresh(context, e.appWidgetId) }
                    editing = null
                },
                secondaryLabel = stringResource(R.string.studio_save_as_design),
                onSecondary = { config ->
                    library.save(SavedDesign(DesignLibrary.newId(), newName, config, e.size?.let { it.width > it.height * 1.3f } ?: true, System.currentTimeMillis()))
                    toast(msgSaved)
                },
            )
            return
        }
        is StudioEdit.Design -> {
            val name = remember(e.design.id) { mutableStateOf(e.design.name) }
            WidgetEditorScreen(
                initial = e.design.config,
                wide = e.design.wide,
                name = name,
                onCancel = { editing = null },
                onSave = { config ->
                    library.save(e.design.copy(name = name.value.ifBlank { newName }, config = config))
                    editing = null
                    page = 1
                },
                secondaryLabel = stringResource(R.string.add_to_home_screen),
                onSecondary = { config -> toast(if (WidgetPinner.pin(context, config, small = !e.design.wide)) msgPin else msgPinNo) },
            )
            return
        }
        null -> Unit
    }

    BackHandler(onBack = onBack)
    HideBottomBar()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Placed widgets, refreshed when returning to the app.
    val lifecycle = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val placed = remember(refresh) {
        val store = WidgetStore(context)
        val manager = AppWidgetManager.getInstance(context)
        WidgetUpdater.placedWidgetIds(context).map { id ->
            val o = manager.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            Triple(id, store.get(id), if (w > 0 && h > 0) DpSize(w.dp, h.dp) else null)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.studio_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.studio_subtitle), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    stringResource(R.string.studio_design_new),
                    {
                        editing = StudioEdit.Design(
                            SavedDesign(
                                DesignLibrary.newId(),
                                newName,
                                WidgetConfig(
                                    WidgetContent(WidgetType.Custom, extra = CustomSpecJson.encode(CustomSpec.starter())),
                                    WidgetPresets.glassNight,
                                ),
                                wide = true,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    },
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(stringResource(R.string.gallery_title), onOpenGallery, filled = false, icon = Icons.Rounded.Widgets, modifier = Modifier.weight(1f))
            }
        }
        item {
            GlassSegmented(
                options = listOf(
                    stringResource(R.string.studio_on_home, placed.size.toString()),
                    stringResource(R.string.studio_my_designs, designs.size.toString()),
                ),
                selectedIndex = page,
                onSelect = { page = it },
            )
        }

        if (page == 0) {
            if (placed.isEmpty()) {
                item { EmptyState(Icons.Rounded.Widgets, stringResource(R.string.widgets_none), stringResource(R.string.studio_none_body)) }
            }
            items(placed, key = { it.first }) { (id, config, size) ->
                PlacedCard(
                    config = config,
                    size = size,
                    onEdit = { editing = StudioEdit.Placed(id, config ?: WidgetConfig(WidgetContent(WidgetType.NextPrayer)), size) },
                    onSave = {
                        if (config != null) {
                            library.save(SavedDesign(DesignLibrary.newId(), newName, config, size?.let { it.width > it.height * 1.3f } ?: true, System.currentTimeMillis()))
                            toast(msgSaved)
                        }
                    },
                    onUnlink = {
                        WidgetStore(context).remove(id)
                        scope.launch { WidgetUpdater.refresh(context, id) }
                        refresh++
                    },
                )
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        stringResource(R.string.studio_import_file),
                        { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                        filled = false,
                        icon = Icons.Rounded.FileOpen,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        stringResource(R.string.studio_import_paste),
                        { pasteSheet = true },
                        filled = false,
                        icon = Icons.Rounded.ContentPaste,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (designs.isEmpty()) {
                item { EmptyState(Icons.Rounded.Widgets, stringResource(R.string.studio_no_designs), stringResource(R.string.studio_no_designs_body)) }
            }
            items(designs, key = { it.id }) { design ->
                DesignCard(
                    design = design,
                    onAdd = { toast(if (WidgetPinner.pin(context, design.config, small = !design.wide)) msgPin else msgPinNo) },
                    onEdit = { editing = StudioEdit.Design(design) },
                    onDuplicate = { library.duplicate(design.id, copySuffix) },
                    onShare = {
                        val json = DesignCodec.export(listOf(design))
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, json), null)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    onDelete = { library.delete(design.id) },
                )
            }
            if (designs.isNotEmpty()) {
                item {
                    GlassButton(
                        stringResource(R.string.studio_export_all),
                        {
                            exportJson = DesignCodec.export(designs)
                            saveFile.launch("athar-widgets.json")
                        },
                        filled = false,
                        icon = Icons.Rounded.Save,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (pasteSheet) {
        var text by remember { mutableStateOf("") }
        GlassSheet(onDismiss = { pasteSheet = false }) {
            Text(stringResource(R.string.studio_import_paste), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.studio_paste_hint), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
            GlassTextField(text, { text = it }, "{ \"format\": \"athar-widget\" … }", Modifier.fillMaxWidth(), singleLine = false)
            GlassButton(
                stringResource(R.string.studio_import),
                { importRaw(text); pasteSheet = false },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlacedCard(config: WidgetConfig?, size: DpSize?, onEdit: () -> Unit, onSave: () -> Unit, onUnlink: () -> Unit) {
    val context = LocalContext.current
    val data by produceState<WidgetData>(WidgetData.Unconfigured, config) { value = WidgetDataLoader.load(context, config) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            config?.let { "${it.content.type.emoji}  " + stringResource(it.content.type.label) } ?: stringResource(R.string.widget_tap_to_setup),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        if (config != null) {
            WidgetPreview(config, data, wide = true, size = size, onTap = onEdit)
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(stringResource(R.string.edit), onEdit, icon = Icons.Rounded.Edit, modifier = Modifier.weight(1f))
            GlassButton(stringResource(R.string.studio_save), onSave, filled = false, icon = Icons.Rounded.Save, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        ConfirmButton(
            text = stringResource(R.string.studio_unlink),
            confirmText = stringResource(R.string.tap_to_confirm),
            onConfirm = onUnlink,
            icon = Icons.Rounded.LinkOff,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.studio_remove_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }
}

@Composable
private fun DesignCard(
    design: SavedDesign,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val data by produceState<WidgetData>(WidgetData.Unconfigured, design.config) { value = WidgetDataLoader.load(context, design.config) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(design.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            WidgetPreview(
                design.config,
                data,
                design.wide,
                Modifier.fillMaxWidth(if (design.wide) 1f else 0.55f),
                onTap = onEdit,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(stringResource(R.string.studio_add_home), onAdd, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
            GlassButton(stringResource(R.string.edit), onEdit, filled = false, icon = Icons.Rounded.Edit, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(stringResource(R.string.studio_duplicate), onDuplicate, filled = false, icon = Icons.Rounded.ContentCopy, modifier = Modifier.weight(1f))
            GlassButton(stringResource(R.string.crash_share), onShare, filled = false, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        ConfirmButton(
            text = stringResource(R.string.delete),
            confirmText = stringResource(R.string.tap_to_confirm),
            onConfirm = onDelete,
            icon = Icons.Rounded.DeleteOutline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
