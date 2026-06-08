package com.enmanuelgil.androidsecurity.ui.screens

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.model.*
import com.enmanuelgil.androidsecurity.ui.components.*
import com.enmanuelgil.androidsecurity.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

private fun checkUsagePermission(context: Context): Boolean = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
        AppOpsManager.MODE_ALLOWED
} catch (e: Exception) { false }

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel()) {
    val context    = LocalContext.current
    val history    by vm.history.collectAsState()
    val loading    by vm.loading.collectAsState()
    var window     by remember { mutableStateOf(24L * 60 * 60 * 1000L) }
    var permState  by remember { mutableStateOf(checkUsagePermission(context)) }

    LaunchedEffect(window, permState) { if (permState) vm.load(context, window) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title    = stringResource(R.string.hist_title),
            subtitle = stringResource(R.string.hist_subtitle)
        )

        when {
            // ── Permission gate ────────────────────────────────
            !permState -> {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape  = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.History, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp))
                            Text(stringResource(R.string.guard_needs_permission),
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.guard_permission_why),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Button(
                                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(stringResource(R.string.guard_grant_permission))
                            }
                            TextButton(onClick = { permState = checkUsagePermission(context) }) {
                                Text("Ya lo concedí — verificar", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ── Normal content ─────────────────────────────────
            else -> {
                // Time window filter
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        1L * 60 * 60 * 1000      to "1h",
                        6L * 60 * 60 * 1000      to "6h",
                        24L * 60 * 60 * 1000     to "24h",
                        7L * 24 * 60 * 60 * 1000 to "7d"
                    ).forEach { (ms, label) ->
                        FilterChip(
                            selected = window == ms,
                            onClick  = { window = ms },
                            label    = { Text(label) }
                        )
                    }
                }

                when {
                    loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    history.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = Color(0xFF3DDC84), modifier = Modifier.size(48.dp))
                                Text(stringResource(R.string.hist_no_activity),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF3DDC84))
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                history,
                                key = { "${it.packageName}-${it.permission}-${it.timestamp}" }
                            ) { entry ->
                                HistoryEntryCard(entry)
                            }
                        }
                    }
                }
            } // else (permState = true)
        } // outer when
    }
}

@Composable
private fun HistoryEntryCard(entry: AccessHistoryEntry) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val (catColor, catIcon, catLabel) = remember(entry.category, primaryColor) {
        when (entry.category) {
            PermCategory.CAMERA       -> Triple(Color(0xFF0EA5E9),  Icons.Default.Videocam,            "Cámara")
            PermCategory.MICROPHONE   -> Triple(Color(0xFFEF5B5B),  Icons.Default.Mic,                 "Micrófono")
            PermCategory.LOCATION     -> Triple(Color(0xFFF59E0B),  Icons.Default.LocationOn,          "Ubicación")
            PermCategory.CONTACTS     -> Triple(Color(0xFF7C5CEF),  Icons.Default.Contacts,            "Contactos")
            PermCategory.SMS          -> Triple(Color(0xFFEF5B5B),  Icons.Default.Sms,                 "SMS")
            PermCategory.STORAGE      -> Triple(Color(0xFF3DDC84),  Icons.Default.Storage,             "Almacen.")
            PermCategory.ACCESSIBILITY -> Triple(Color(0xFFF59E0B), Icons.Default.AccessibilityNew,    "Accesib.")
            PermCategory.DEVICE_ADMIN -> Triple(Color(0xFFEF5B5B),  Icons.Default.AdminPanelSettings,  "Admin")
            PermCategory.OVERLAY      -> Triple(Color(0xFFF59E0B),  Icons.Default.Layers,              "Overlay")
            PermCategory.OTHER        -> Triple(primaryColor,        Icons.Default.Circle,              "Otro")
        }
    }

    val sdf = remember { SimpleDateFormat("HH:mm  dd/MM", Locale.getDefault()) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color    = catColor.copy(0.12f),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(catIcon, null, tint = catColor, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(entry.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1)
                Text(catLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = catColor)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(sdf.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                if (entry.durationMs > 0) {
                    Text("${entry.durationMs / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.35f))
                }
            }
        }
    }
}
