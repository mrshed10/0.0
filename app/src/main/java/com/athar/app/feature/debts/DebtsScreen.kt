package com.athar.app.feature.debts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.app.R
import com.athar.app.core.region.LocalCurrency
import com.athar.app.core.designsystem.component.EmptyState
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.component.ScreenHeader
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.Money
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatNumber
import com.athar.app.core.security.BiometricAuth
import java.time.LocalDate

/** Days after which an open debt is shown as overdue. */
private const val OVERDUE_DAYS = 30L

@Composable
fun DebtsRoute(viewModel: DebtsViewModel = viewModel(factory = DebtsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var openPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var personEditing by remember { mutableStateOf<Person?>(null) }
    var entryEditing by remember { mutableStateOf<DebtEntry?>(null) }
    var sorting by remember { mutableStateOf(false) }
    val contactFailed = stringResource(R.string.contact_pick_failed)

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val picked = readContact(context, uri)
        if (picked == null) {
            Toast.makeText(context, contactFailed, Toast.LENGTH_SHORT).show()
        } else {
            viewModel.savePerson(picked) { id -> openPersonId = id }
        }
    }
    val openContacts = {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        try {
            pickContact.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, contactFailed, Toast.LENGTH_SHORT).show()
        }
    }

    if (state.locked) {
        LockedDebts(onUnlocked = viewModel::unlock)
        return
    }

    Box(Modifier.fillMaxSize()) {
        val person = openPersonId?.let { state.balanceOf(it) }
        if (person == null) {
            PeopleList(
                state = state,
                onQuery = viewModel::setQuery,
                onOpen = { openPersonId = it },
                onSort = { sorting = true },
                onAddPerson = { personEditing = Person(name = "") },
            )
            AddPersonFab(
                onNewPerson = { personEditing = Person(name = "") },
                onFromContacts = openContacts,
            )
        } else {
            BackHandler { openPersonId = null }
            PersonLedger(
                balance = person,
                entries = state.entriesByPerson[person.person.id].orEmpty(),
                onBack = { openPersonId = null },
                onEditPerson = { personEditing = person.person },
                onAddEntry = { type ->
                    entryEditing = DebtEntry(personId = person.person.id, type = type, amountMinor = 0, date = LocalDate.now())
                },
                onOpenEntry = { entryEditing = it },
            )
        }

        if (sorting) {
            DebtSortSheet(
                selected = state.sort,
                onDismiss = { sorting = false },
                onSelect = { viewModel.setSort(it); sorting = false },
            )
        }
        personEditing?.let { editing ->
            PersonEditorSheet(
                initial = editing,
                onDismiss = { personEditing = null },
                onSave = {
                    viewModel.savePerson(it) { id -> if (editing.isNew) openPersonId = id }
                    personEditing = null
                },
                onDelete = {
                    viewModel.deletePerson(it)
                    personEditing = null
                    openPersonId = null
                },
            )
        }
        entryEditing?.let { editing ->
            EntryEditorSheet(
                initial = editing,
                onDismiss = { entryEditing = null },
                onSave = { viewModel.saveEntry(it); entryEditing = null },
                onDelete = { viewModel.deleteEntry(it); entryEditing = null },
            )
        }
    }
}

/** Reads name and number from a picked phone row; null if the row could not be read. */
private fun readContact(context: Context, uri: Uri): Person? = runCatching {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
        if (!c.moveToFirst()) return@use null
        val name = c.getString(0)?.trim().orEmpty()
        val phone = c.getString(1)?.trim().orEmpty()
        if (name.isBlank() && phone.isBlank()) null else Person(name = name.ifBlank { phone }, phone = phone)
    }
}.getOrNull()

@Composable
private fun screenPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 180.dp)
}

@Composable
private fun PeopleList(
    state: DebtsUiState,
    onQuery: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onSort: () -> Unit,
    onAddPerson: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = stringResource(R.string.debts_ledger),
                subtitle = stringResource(R.string.debts_people_count, state.balances.size.formatNumber()),
                action = {
                    if (state.balances.size > 1) {
                        GlassButton(
                            stringResource(R.string.sort_by),
                            onSort,
                            icon = Icons.AutoMirrored.Rounded.Sort,
                        )
                    }
                },
            )
        }
        item(key = "totals") { TotalsCard(state.totals) }
        if (state.balances.size > 3) {
            item(key = "search") {
                GlassTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    placeholder = stringResource(R.string.search_people),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.loaded && state.balances.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = stringResource(R.string.empty_debts_title),
                    body = stringResource(R.string.empty_debts_body),
                    actionLabel = stringResource(R.string.add_person),
                    onAction = onAddPerson,
                )
            }
        }
        if (state.sort != DebtSort.AmountDesc && state.balances.size > 1) {
            item(key = "sortLabel") {
                Text(
                    stringResource(R.string.sorted_by, stringResource(state.sort.label)),
                    style = MaterialTheme.typography.labelMedium,
                    color = AtharColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        items(state.visible, key = { it.person.id }) { balance ->
            PersonRow(balance, today, onClick = { onOpen(balance.person.id) })
        }
    }
}

@Composable
private fun TotalsCard(totals: DebtTotals) {
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Teal, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.debts_total_remaining),
            style = MaterialTheme.typography.labelLarge,
            color = AtharColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Row {
            Text(
                Money.format(totals.owedToMeMinor),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = AtharColors.Teal,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                " " + LocalCurrency.current,
                style = MaterialTheme.typography.titleSmall,
                color = AtharColors.TextSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallStat(stringResource(R.string.debts_i_owe), Money.format(totals.iOweMinor), AtharColors.Rose, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.12f)))
            SmallStat(stringResource(R.string.debts_open_people), totals.openPeople.formatNumber(), AtharColors.TextPrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** Colour and label for a balance: teal "owes you", rose "you owe", grey "settled". */
@Composable
fun balanceStyle(minor: Long): Pair<Color, String> = when {
    minor > 0 -> AtharColors.Teal to stringResource(R.string.balance_owes_you)
    minor < 0 -> AtharColors.Rose to stringResource(R.string.balance_you_owe)
    else -> AtharColors.TextTertiary to stringResource(R.string.balance_settled)
}

@Composable
private fun PersonRow(balance: PersonBalance, today: LocalDate, onClick: () -> Unit) {
    val (color, label) = balanceStyle(balance.balanceMinor)
    val days = balance.since?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f))
                    .border(1.dp, color.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    balance.person.name.trim().take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    balance.person.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (balance.person.phone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Call, null, tint = AtharColors.TextTertiary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            balance.person.phone,
                            style = MaterialTheme.typography.labelMedium,
                            color = AtharColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (days != null) {
                    val overdue = days > OVERDUE_DAYS
                    Text(
                        when {
                            days == 0L -> stringResource(R.string.debt_since_today)
                            overdue -> stringResource(R.string.debt_overdue_days, days.formatNumber())
                            else -> stringResource(R.string.debt_since_days, days.formatNumber())
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                        color = if (overdue) AtharColors.Rose else AtharColors.TextTertiary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(abs(balance.balanceMinor)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

/** Floating add button that unfolds into "new person" and "from contacts". */
@Composable
private fun AddPersonFab(onNewPerson: () -> Unit, onFromContacts: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fabRotation")
    if (expanded) BackHandler { expanded = false }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = false },
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FabOption(stringResource(R.string.add_from_contacts), Icons.Rounded.Contacts) {
                        expanded = false
                        onFromContacts()
                    }
                    FabOption(stringResource(R.string.add_new_person), Icons.Rounded.PersonAdd) {
                        expanded = false
                        onNewPerson()
                    }
                }
            }
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(AtharColors.Teal)
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_person),
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp).rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun FabOption(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1C2230).copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(AtharColors.Teal.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AtharColors.Teal, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PersonLedger(
    balance: PersonBalance,
    entries: List<DebtEntry>,
    onBack: () -> Unit,
    onEditPerson: () -> Unit,
    onAddEntry: (EntryType) -> Unit,
    onOpenEntry: (DebtEntry) -> Unit,
) {
    val context = LocalContext.current
    val (color, label) = balanceStyle(balance.balanceMinor)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "top") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(12.dp))
                Text(
                    balance.person.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (balance.person.phone.isNotBlank()) {
                    GlassIconButton(Icons.Rounded.Call, stringResource(R.string.call), {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + balance.person.phone)))
                    })
                    Spacer(Modifier.width(8.dp))
                }
                GlassIconButton(Icons.Rounded.Edit, stringResource(R.string.edit_person), onEditPerson)
            }
        }
        item(key = "balance") {
            GlassCard(Modifier.fillMaxWidth(), accent = color, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                Row {
                    Text(
                        Money.format(balance.balanceMinor),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        " " + LocalCurrency.current,
                        style = MaterialTheme.typography.titleSmall,
                        color = AtharColors.TextSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(
                        stringResource(R.string.entry_gave),
                        { onAddEntry(EntryType.Gave) },
                        color = AtharColors.Teal,
                        icon = Icons.Rounded.NorthEast,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        stringResource(R.string.entry_took),
                        { onAddEntry(EntryType.Took) },
                        color = AtharColors.Rose,
                        icon = Icons.Rounded.SouthWest,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.entry_explain),
                    style = MaterialTheme.typography.labelSmall,
                    color = AtharColors.TextTertiary,
                )
            }
        }
        if (entries.isEmpty()) {
            item(key = "noEntries") {
                Text(
                    stringResource(R.string.no_entries),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AtharColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            item(key = "entries") {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)) {
                    entries.forEach { EntryRow(it, onClick = { onOpenEntry(it) }) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: DebtEntry, onClick: () -> Unit) {
    val locale = currentLocale()
    val gave = entry.type == EntryType.Gave
    val color = if (gave) AtharColors.Teal else AtharColors.Rose
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (gave) Icons.Rounded.NorthEast else Icons.Rounded.SouthWest,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.note.ifBlank { stringResource(if (gave) R.string.entry_gave else R.string.entry_took) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.date.formatGregorian(locale, "EEE d MMM yyyy"),
                style = MaterialTheme.typography.labelSmall,
                color = AtharColors.TextTertiary,
            )
        }
        Text(
            (if (gave) "+" else "−") + Money.format(entry.amountMinor),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun LockedDebts(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.debts_locked_title)
    val subtitle = stringResource(R.string.debts_locked_body)
    var error by remember { mutableStateOf<String?>(null) }
    val prompt = {
        BiometricAuth.authenticate(context, title, subtitle, onSuccess = onUnlocked, onError = { error = it })
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Rounded.Lock,
            title = title,
            body = error ?: subtitle,
            actionLabel = stringResource(R.string.unlock),
            onAction = prompt,
        )
    }
    LaunchedEffect(Unit) { prompt() }
}
