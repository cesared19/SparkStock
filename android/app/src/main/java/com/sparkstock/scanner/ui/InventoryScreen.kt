package com.sparkstock.scanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sparkstock.scanner.R
import com.sparkstock.scanner.data.Device
import com.sparkstock.scanner.data.InventoryCheck
import com.sparkstock.scanner.data.Location
import com.sparkstock.scanner.data.Session
import com.sparkstock.scanner.data.SessionExpiredException
import kotlinx.coroutines.launch

/**
 * Inventory check: choose the location, scan everything that's there, and compare with SparkStock: what's where it
 * should be, what's missing, what SparkStock has somewhere else (and can move here), and unknown labels.
 */
@Composable
fun InventoryScreen(session: Session, onSessionExpired: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val api = remember { session.api() }
    var locations by remember { mutableStateOf<List<Location>?>(null) }
    var location by remember { mutableStateOf<Location?>(null) }
    val tags = remember { mutableStateListOf<String>() }
    var result by remember { mutableStateOf<InventoryCheck?>(null) }
    var moved by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun fail(e: Throwable) {
        if (e is SessionExpiredException) onSessionExpired() else scope.launch { snackbar.showSnackbar(errorText(context, e)) }
    }

    fun check(apply: Boolean) {
        val where = location ?: return
        busy = true
        scope.launch {
            try {
                val checked = api.inventoryCheck(where.id, tags.toList(), apply)
                if (apply) moved = context.getString(R.string.moved, checked.moved, where.name)
                result = checked
            } catch (e: Exception) {
                fail(e)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            locations = api.locations().filter { !it.isArchived }
        } catch (e: Exception) {
            locations = emptyList()
            fail(e)
        }
    }

    val back = {
        when {
            result != null -> { result = null; moved = null }
            location != null -> { location = null; tags.clear() }
            else -> onBack()
        }
    }

    ScreenScaffold(
        location?.let { stringResource(R.string.checking, it.name) } ?: stringResource(R.string.inventory_title),
        onBack = back,
        snackbar = snackbar,
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            val where = location
            val checked = result
            when {
                where == null -> {
                    Text(stringResource(R.string.choose_location), style = MaterialTheme.typography.titleLarge)
                    val list = locations
                    if (list == null) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        LazyColumn {
                            items(list, key = { it.id }) { option ->
                                ListItem(headlineContent = { Text(option.name) }, modifier = Modifier.clickable { location = option })
                                HorizontalDivider()
                            }
                        }
                    }
                }
                checked == null -> {
                    QrScanner(
                        onTag = { tag ->
                            if (tag in tags) scope.launch { snackbar.showSnackbar(context.getString(R.string.already_scanned, tag)) }
                            else tags.add(0, tag)
                        },
                        onInvalid = { scope.launch { snackbar.showSnackbar(context.getString(R.string.not_a_label)) } },
                    )
                    Text(stringResource(R.string.scanned_count, tags.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(tags, key = { it }) { tag -> Text(tag, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge) }
                    }
                    Button(enabled = !busy, onClick = { check(apply = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.finish, tags.size))
                    }
                }
                else -> {
                    val allInPlace = stringResource(R.string.all_in_place)
                    val missingTitle = stringResource(R.string.missing, checked.missing.size)
                    val elsewhereTitle = stringResource(R.string.elsewhere, checked.elsewhere.size)
                    val unknownTitle = stringResource(R.string.unknown, checked.unknown.size)
                    val foundTitle = stringResource(R.string.found, checked.found.size)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        moved?.let { item { Banner(it, Good) } }
                        if (checked.missing.isEmpty() && checked.elsewhere.isEmpty() && checked.unknown.isEmpty()) {
                            item { Banner(allInPlace, Good) }
                        }
                        section(missingTitle, Bad, checked.missing.map { deviceLine(it) })
                        section(
                            elsewhereTitle, Warning,
                            checked.elsewhere.map { misplaced ->
                                val device = misplaced.device
                                val where = device.holder?.let { context.getString(R.string.with_person, it.displayName) }
                                    ?: context.getString(R.string.at_location, device.location?.name ?: context.getString(R.string.no_location))
                                "${deviceLine(device)} · ${statusText(context, device.status)} · $where"
                            },
                        )
                        section(unknownTitle, Bad, checked.unknown)
                        section(foundTitle, Good, checked.found.map { deviceLine(it) })
                    }
                    val movable = checked.elsewhere.count { it.canMove }
                    if (movable > 0 && moved == null) {
                        Button(enabled = !busy, onClick = { check(apply = true) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.move_here, movable))
                        }
                    }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

private fun deviceLine(device: Device) = "${device.assetTag} · ${device.name}"

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, color: Color, lines: List<String>) {
    if (lines.isEmpty()) return
    item { Text(title, color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
    items(lines) { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 3.dp)) }
}
