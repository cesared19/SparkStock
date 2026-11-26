package com.sparkstock.scanner.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sparkstock.scanner.R
import com.sparkstock.scanner.data.ApiException
import com.sparkstock.scanner.data.Device
import com.sparkstock.scanner.data.OfflineException
import com.sparkstock.scanner.data.SessionExpiredException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What went wrong, for people: SparkStock's own message (already in their language), or a connection problem. */
fun errorText(context: Context, error: Throwable): String = when (error) {
    is ApiException -> error.message ?: ""
    is OfflineException -> context.getString(R.string.cant_reach)
    is SessionExpiredException -> context.getString(R.string.session_expired)
    else -> error.message ?: error.toString()
}

fun statusText(context: Context, status: String): String {
    val id = context.resources.getIdentifier("status_$status", "string", context.packageName)
    return if (id != 0) context.getString(id) else status.lowercase()
}

/** Why a device can't be lent: its status, and who has it. */
fun unavailableText(context: Context, device: Device): String {
    val status = statusText(context, device.status)
    return device.holder?.let { "$status · ${context.getString(R.string.with_person, it.displayName)}" } ?: status
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatDate(date: LocalDate): String = date.format(dateFormat)

fun formatSeconds(seconds: Double): String = formatDate(Instant.ofEpochMilli((seconds * 1000).toLong()).atZone(ZoneId.systemDefault()).toLocalDate())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        content = content,
    )
}

@Composable
fun Banner(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.14f), contentColor = color, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
