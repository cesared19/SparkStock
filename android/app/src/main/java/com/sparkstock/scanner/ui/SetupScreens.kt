package com.sparkstock.scanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sparkstock.scanner.R
import com.sparkstock.scanner.data.Api
import com.sparkstock.scanner.data.ApiException
import com.sparkstock.scanner.data.Session
import kotlinx.coroutines.launch
import java.util.Locale

/** The SparkStock address, checked before it's saved: the API must answer (a 401 without a session is fine). */
@Composable
fun SetupScreen(session: Session, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(session.serverUrl.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(stringResource(R.string.setup_title), onBack = null) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.setup_intro))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; error = null },
                label = { Text(stringResource(R.string.server_address)) },
                placeholder = { Text("192.168.1.20:3100") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Banner(it, Bad) }
            Button(
                enabled = !busy && address.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val url = Api.normalizeServer(address)
                    if (url == null) {
                        error = context.getString(R.string.invalid_address)
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        try {
                            Api(url, null, Locale.getDefault().toLanguageTag()).me()
                            session.serverUrl = url
                            onDone()
                        } catch (e: ApiException) {
                            // SparkStock answered: without a session it says 401, so the address is right
                            if (e.status == 401) {
                                session.serverUrl = url
                                onDone()
                            } else {
                                error = context.getString(R.string.cant_reach)
                            }
                        } catch (e: Exception) {
                            error = context.getString(R.string.cant_reach)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) stringResource(R.string.loading) else stringResource(R.string.connect)) }
        }
    }
}

/** Sign-in with the SparkyTC account; only admins can lend to others and receive any device. */
@Composable
fun LoginScreen(session: Session, notice: String?, onSignedIn: () -> Unit, onChangeServer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(session.user?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(notice) }

    ScreenScaffold(stringResource(R.string.app_name), onBack = null) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.sign_in_intro))
            Text(session.serverUrl.orEmpty(), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Banner(it, Bad) }
            Button(
                enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val login = Api(session.serverUrl.orEmpty(), null, Locale.getDefault().toLanguageTag()).login(username.trim(), password)
                            if (!login.user.isAdmin) {
                                Api(session.serverUrl.orEmpty(), login.token, Locale.getDefault().toLanguageTag()).logout()
                                error = context.getString(R.string.admins_only)
                            } else {
                                session.token = login.token
                                session.user = login.user
                                password = ""
                                onSignedIn()
                            }
                        } catch (e: Exception) {
                            error = errorText(context, e)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) stringResource(R.string.loading) else stringResource(R.string.sign_in)) }
            TextButton(onClick = onChangeServer) { Text(stringResource(R.string.change_server)) }
        }
    }
}
