package com.enmanuelgil.androidsecurity.ui.screens

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

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel()) {
    val context  = LocalContext.current
    val history  by vm.history.collectAsState()
    val loading  by vm.loading.collectAsState()
    var window   by remember { mutableStateOf(24 * 60 * 60 * 1000L) } // 24h default

    LaunchedEffect(window) { vm.load(context, window) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title    = stringResource(R.string.hist_title),
            subtitle = stringResource(R.string.hist_subtitle)
        )

        // Time window filter
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                1L * 60 * 60 * 1000   to "1h",
                6L * 60 * 60 * 1000   to "6h",
                24L * 60 * 60 * 1000  to "24h",
                7L * 24 * 60 * 60 * 1000 to "7d"
            ).forEach { (ms, label) ->
                FilterChip(
                    selected = window == ms,
                    onClick  = { window = ms },
                    label    = { Text(label) }
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = Color(0xFF3DDC84), modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.hist_no_activity),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF3DDC84))
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history, key = { "${it.packageName}-${it.permission}-${it.timestamp}" }) { entry ->
                HistoryEntryCard(entry)
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: AccessHistoryEntry) {
    val (catColor, catIcon, catLabel) = remember(entry.category) {
        when (entry.category) {
            PermCategory.CAMERA      -> Triple(Color(0xFF0EA5E9),  Icons.Default.Videocam,       "Cámara")
            PermCategory.MICROPHONE  -> Triple(Color(0xFFEF5B5B),  Icons.Default.Mic,            "Micrófono")
            PermCategory.LOCATION    -> Triple(Color(0xFFF59E0B),  Icons.Default.LocationOn,     "Ubicación")
            PermCategory.CONTACTS    -> Triple(Color(0xFF7C5CEF),  Icons.Default.Contacts,       "Contactos")
            PermCategory.SMS         -> Triple(Color(0xFFEF5B5B),  Icons.Default.Sms,            "SMS")
            PermCategory.STORAGE     -> Triple(Color(0xFF3DDC84),  Icons.Default.Storage,        "Almacen.")
            PermCategory.ACCESSIBILITY -> Triple(Color(0xFFF59E0B), Icons.Default.AccessibilityNew, "Accesib.")
            PermCategory.DEVICE_ADMIN -> Triple(Color(0xFFEF5B5B), Icons.Default.AdminPanelSettings, "Admin")
            PermCategory.OVERLAY     -> Triple(Color(0xFFF59E0B),  Icons.Default.Layers,         "Overlay")
            PermCategory.OTHER       -> Triple(MaterialTheme.colorScheme.primary, Icons.Default.Circle, "Otro")
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
            // Category icon circle
            Surface(
                color  = catColor.copy(0.12f),
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(catIcon, null, tint = catColor, modifier = Modifier.size(18.dp))
                }
            }

            // App info
            Column(Modifier.weight(1f)) {
                Text(entry.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1)
                Text(catLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = catColor)
            }

            // Time + duration
            Column(horizontalAlignment = Alignment.End) {
                Text(sdf.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                if (entry.durationMs > 0) {
                    val secs = entry.durationMs / 1000
                    Text("${secs}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.35f))
                }
            }
        }
    }
}
