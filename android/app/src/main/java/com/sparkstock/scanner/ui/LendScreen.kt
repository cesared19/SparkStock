package com.sparkstock.scanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sparkstock.scanner.R
import com.sparkstock.scanner.data.Device
import com.sparkstock.scanner.data.Session
import com.sparkstock.scanner.data.SessionExpiredException
import com.sparkstock.scanner.data.User
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A scanned label: looking it up, a device, or why it can't be lent. */
data class Scanned(val tag: String, val device: Device? = null, val problem: String? = null) {
    val lendable get() = device?.isLendable == true
}

private enum class Step { SCAN, PERSON, CONFIRM, DONE }

/**
 * Lend: scan one or more devices (a headset, its controllers…), choose who takes them, the return date, and confirm.
 * SparkStock lends all or none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendScreen(session: Session, onSessionExpired: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val api = remember { session.api() }
    var step by remember { mutableStateOf(Step.SCAN) }
    val scanned = remember { mutableStateListOf<Scanned>() }
    var person by remember { mutableStateOf<User?>(null) }
    var due by remember { mutableStateOf(LocalDate.now().plusDays(7)) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lent by remember { mutableStateOf<List<Device>>(emptyList()) }

    fun fail(e: Throwable) {
        if (e is SessionExpiredException) onSessionExpired() else scope.launch { snackbar.showSnackbar(errorText(context, e)) }
    }

    fun onTag(tag: String) {
        if (scanned.any { it.tag == tag }) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.already_scanned, tag)) }
            return
        }
        scanned.add(0, Scanned(tag))
        scope.launch {
            val index = { scanned.indexOfFirst { it.tag == tag } }
            try {
                val device = api.device(tag).device
                val problem = if (device.isLendable) null else context.getString(R.string.not_available, unavailableText(context, device))
                if (index() >= 0) scanned[index()] = Scanned(tag, device, problem)
            } catch (e: SessionExpiredException) {
                onSessionExpired()
            } catch (e: Exception) {
                if (index() >= 0) scanned[index()] = Scanned(tag, null, errorText(context, e))
            }
        }
    }

    val back = {
        when (step) {
            Step.SCAN, Step.DONE -> onBack()
            Step.PERSON -> step = Step.SCAN
            Step.CONFIRM -> step = Step.PERSON
        }
    }

    ScreenScaffold(stringResource(R.string.lend_title), onBack = back, snackbar = snackbar) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (step) {
                Step.SCAN -> {
                    QrScanner(::onTag, { scope.launch { snackbar.showSnackbar(context.getString(R.string.not_a_label)) } })
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.scanned_count, scanned.size), style = MaterialTheme.typography.titleMedium)
                    if (scanned.isEmpty()) Text(stringResource(R.string.nothing_scanned), style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        items(scanned, key = { it.tag }) { item ->
                            ScannedRow(item, onRemove = { scanned.remove(item) })
                        }
                    }
                    Button(
                        enabled = scanned.any { it.lendable } && scanned.none { it.device == null && it.problem == null },
                        onClick = { step = Step.PERSON },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.next)) }
                }
                Step.PERSON -> PersonPicker(session, onPicked = { person = it; step = Step.CONFIRM }, onError = ::fail)
                Step.CONFIRM -> {
                    val toLend = scanned.filter { it.lendable }
                    var picking by remember { mutableStateOf(false) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(person?.displayName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        toLend.forEach { Text("${it.tag} · ${it.device?.name}", style = MaterialTheme.typography.bodyLarge) }
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("${stringResource(R.string.return_by)}: ${formatDate(due)}")
                        }
                        OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note)) }, modifier = Modifier.fillMaxWidth())
                    }
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        onClick = {
                            val who = person ?: return@Button
                            busy = true
                            scope.launch {
                                try {
                                    // until the end of that day, like on the website
                                    val dueAt = due.atTime(LocalTime.of(23, 59)).atZone(ZoneId.systemDefault()).toEpochSecond()
                                    lent = api.lend(toLend.mapNotNull { it.device?.id }, who.userID, dueAt, note)
                                    session.remember(who.userID)
                                    step = Step.DONE
                                } catch (e: Exception) {
                                    fail(e)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.lend_confirm, toLend.size, person?.displayName.orEmpty(), formatDate(due))) }
                    if (picking) {
                        val state = rememberDatePickerState(
                            initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                            selectableDates = object : SelectableDates {
                                override fun isSelectableDate(utcTimeMillis: Long) =
                                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(LocalDate.now())
                            },
                        )
                        DatePickerDialog(
                            onDismissRequest = { picking = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                                    picking = false
                                }) { Text(stringResource(R.string.ok)) }
                            },
                            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
                        ) { DatePicker(state) }
                    }
                }
                Step.DONE -> {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(72.dp), tint = Good)
                        Text(stringResource(R.string.lent_title, person?.displayName.orEmpty()), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.lent_until, formatDate(due)))
                        lent.forEach { Text("${it.assetTag} · ${it.name}") }
                    }
                    Button(onClick = {
                        scanned.clear(); person = null; note = ""; due = LocalDate.now().plusDays(7); step = Step.SCAN
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lend_more)) }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

@Composable
private fun ScannedRow(item: Scanned, onRemove: () -> Unit) {
    ListItem(
        leadingContent = {
            when {
                item.device == null && item.problem == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                item.lendable -> Icon(Icons.Filled.CheckCircle, null, tint = Good)
                else -> Icon(Icons.Filled.ErrorOutline, null, tint = Bad)
            }
        },
        headlineContent = { Text(item.device?.let { "${it.assetTag} · ${it.name}" } ?: item.tag) },
        supportingContent = { item.problem?.let { Text(it, color = Bad) } ?: item.device?.coverageVersion?.let { Text(it) } },
        trailingContent = { IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) } },
    )
}

/** The people to lend to: the recent ones first, then everyone, with a search. */
@Composable
private fun PersonPicker(session: Session, onPicked: (User) -> Unit, onError: (Throwable) -> Unit) {
    val api = remember { session.api() }
    var people by remember { mutableStateOf<List<User>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            people = api.users().filter { it.isActive }.sortedBy { it.displayName.lowercase() }
        } catch (e: Exception) {
            people = emptyList()
            onError(e)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.choose_person), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_people)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val all = people
        if (all == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val matching = all.filter { p -> words.all { "${p.displayName} ${p.username}".lowercase().contains(it) } }
        val recentIds = session.recentPeople
        val recent = if (words.isEmpty()) recentIds.mapNotNull { id -> all.firstOrNull { it.userID == id } } else emptyList()
        LazyColumn(Modifier.weight(1f)) {
            if (recent.isNotEmpty()) {
                item { Text(stringResource(R.string.recent), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp)) }
                items(recent, key = { "r" + it.userID }) { PersonRow(it, onPicked) }
                item { Text(stringResource(R.string.everyone), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp)) }
            }
            items(matching, key = { it.userID }) { PersonRow(it, onPicked) }
        }
    }
}

@Composable
private fun PersonRow(person: User, onPicked: (User) -> Unit) {
    ListItem(
        headlineContent = { Text(person.displayName) },
        supportingContent = { Text(person.username) },
        modifier = Modifier.clickable { onPicked(person) },
    )
    HorizontalDivider()
}
