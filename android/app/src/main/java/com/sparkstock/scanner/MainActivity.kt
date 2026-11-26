package com.sparkstock.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sparkstock.scanner.data.Session
import com.sparkstock.scanner.ui.*
import kotlinx.coroutines.launch

/** SparkStock Scanner: lend, receive and check devices by scanning their QR labels. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SparkStockTheme { App() } }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val session = remember { Session(context.applicationContext) }
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    val start = when {
        session.serverUrl == null -> "setup"
        session.token == null -> "login"
        else -> "home"
    }

    fun toLogin(expired: Boolean) {
        session.signOut()
        notice = if (expired) context.getString(R.string.session_expired) else null
        nav.navigate("login") { popUpTo(0) }
    }

    NavHost(nav, startDestination = start) {
        composable("setup") { SetupScreen(session) { nav.navigate("login") { popUpTo(0) } } }
        composable("login") {
            LoginScreen(
                session, notice,
                onSignedIn = { notice = null; nav.navigate("home") { popUpTo(0) } },
                onChangeServer = { nav.navigate("setup") { popUpTo(0) } },
            )
        }
        composable("home") {
            HomeScreen(
                session.user?.displayName.orEmpty(),
                onLend = { nav.navigate("lend") },
                onReturn = { nav.navigate("return") },
                onInventory = { nav.navigate("inventory") },
                onSignOut = {
                    val api = session.api()
                    scope.launch { api.logout() }
                    toLogin(expired = false)
                },
            )
        }
        composable("lend") { LendScreen(session, { toLogin(expired = true) }) { nav.popBackStack() } }
        composable("return") { ReturnScreen(session, { toLogin(expired = true) }) { nav.popBackStack() } }
        composable("inventory") { InventoryScreen(session, { toLogin(expired = true) }) { nav.popBackStack() } }
    }
}
