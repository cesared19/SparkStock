package com.sparkstock.scanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sparkstock.scanner.R

@Composable
fun HomeScreen(name: String, onLend: () -> Unit, onReturn: () -> Unit, onInventory: () -> Unit, onSignOut: () -> Unit) {
    ScreenScaffold(
        stringResource(R.string.app_name),
        onBack = null,
        actions = {
            IconButton(onClick = onSignOut) { Icon(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.sign_out)) }
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.hello, name), style = MaterialTheme.typography.headlineSmall)
            BigAction(Icons.Filled.Outbox, stringResource(R.string.lend), stringResource(R.string.lend_hint), Color(0xFF1D4ED8), onLend)
            BigAction(Icons.Filled.AssignmentReturn, stringResource(R.string.give_back), stringResource(R.string.give_back_hint), Color(0xFF0F766E), onReturn)
            BigAction(Icons.Filled.Inventory, stringResource(R.string.inventory), stringResource(R.string.inventory_hint), Color(0xFF7C3AED), onInventory)
        }
    }
}

// a big colour tile, like the reports
@Composable
private fun BigAction(icon: ImageVector, title: String, hint: String, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = color, contentColor = Color.White),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(44.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(hint, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
