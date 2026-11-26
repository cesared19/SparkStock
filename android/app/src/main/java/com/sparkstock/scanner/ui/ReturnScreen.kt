package com.sparkstock.scanner.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sparkstock.scanner.R
import com.sparkstock.scanner.data.Condition
import com.sparkstock.scanner.data.Device
import com.sparkstock.scanner.data.Session
import com.sparkstock.scanner.data.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Return: scan a device someone brings back, say how it came back (fine, or needs repair), optionally with a note
 * and a photo, and confirm. Then the next one.
 */
@Composable
fun ReturnScreen(session: Session, onSessionExpired: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val api = remember { session.api() }
    var device by remember { mutableStateOf<Device?>(null) }
    var condition by remember { mutableStateOf(Condition.OK) }
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = photoUri
        if (saved && uri != null) scope.launch { photo = withContext(Dispatchers.IO) { shrinkPhoto(context, uri) } }
    }

    fun reset() {
        device = null; condition = Condition.OK; note = ""; photo = null
    }

    fun onTag(tag: String) {
        if (busy || device != null) return
        problem = null
        busy = true
        scope.launch {
            try {
                val found = api.device(tag).device
                if (found.status == "ON_LOAN") {
                    device = found
                    lastResult = null
                } else {
                    problem = context.getString(R.string.not_on_loan, found.assetTag, unavailableText(context, found))
                }
            } catch (e: SessionExpiredException) {
                onSessionExpired()
            } catch (e: Exception) {
                problem = errorText(context, e)
            } finally {
                busy = false
            }
        }
    }

    ScreenScaffold(stringResource(R.string.return_title), onBack = { if (device != null) reset() else onBack() }, snackbar = snackbar) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val current = device
            if (current == null) {
                lastResult?.let { Banner(it, Good) }
                problem?.let { Banner(it, Warning) }
                QrScanner(::onTag, { scope.launch { snackbar.showSnackbar(context.getString(R.string.not_a_label)) } })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text("${current.assetTag} · ${current.name}", style = MaterialTheme.typography.headlineSmall)
                current.holder?.let { Text(stringResource(R.string.on_loan_to, it.displayName), style = MaterialTheme.typography.titleMedium) }
                current.dueAt?.let {
                    Text(
                        stringResource(R.string.due, formatSeconds(it)) + if (current.isOverdue) " · " + stringResource(R.string.overdue) else "",
                        color = if (current.isOverdue) Bad else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(stringResource(R.string.how_came_back), style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(condition == Condition.OK, { condition = Condition.OK }, SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.condition_ok))
                    }
                    SegmentedButton(condition == Condition.NEEDS_REPAIR, { condition = Condition.NEEDS_REPAIR }, SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.condition_repair))
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note)) }, modifier = Modifier.fillMaxWidth())
                photo?.let { bytes ->
                    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    bitmap?.let { Image(it, null, Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit) }
                }
                OutlinedButton(onClick = {
                    val uri = newPhotoUri(context)
                    photoUri = uri
                    camera.launch(uri)
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PhotoCamera, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (photo == null) R.string.take_photo else R.string.retake_photo))
                }
                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val detail = api.giveBack(current.id, condition, note)
                                var message = context.getString(
                                    if (condition == Condition.NEEDS_REPAIR) R.string.returned_repair else R.string.returned, current.assetTag,
                                )
                                photo?.let { bytes ->
                                    try {
                                        api.addPhoto(current.id, detail.events.first().id, bytes)
                                    } catch (e: Exception) {
                                        message = context.getString(R.string.photo_failed, errorText(context, e))
                                    }
                                }
                                lastResult = message
                                reset()
                            } catch (e: SessionExpiredException) {
                                onSessionExpired()
                            } catch (e: Exception) {
                                snackbar.showSnackbar(errorText(context, e))
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.confirm_return, current.assetTag)) }
            }
        }
    }
}
