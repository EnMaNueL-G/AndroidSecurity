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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.data.AccessEvent
import com.enmanuelgil.androidsecurity.model.SensorType
import com.enmanuelgil.androidsecurity.ui.components.ScreenHeader
import com.enmanuelgil.androidsecurity.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val events  by vm.events.collectAsState()
    val loading by vm.loading.collectAsState()
    var window  by remember { mutableStateOf(24L * 60 * 60 * 1000L) }

    LaunchedEffect(window) { vm.load(context, window) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title    = stringResource(R.string.hist_title),
            subtitle = "Eventos registrados por el servicio Guard en tiempo real"
        )

        // Time window filter
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                1L  * 60 * 60 * 1000       to "1h",
                6L  * 60 * 60 * 1000       to "6h",
                24L * 60 * 60 * 1000       to "24h",
                7L  * 24 * 60 * 60 * 1000  to "7 días"
            ).forEach { (ms, label) ->
                FilterChip(
                    selected = window == ms,
                    onClick  = { window = ms },
                    label    = { Text(label) }
                )
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            events.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.History, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.25f),
                            modifier = Modifier.size(56.dp))
                        Text("Sin registros en este período",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        Text(
                            "El historial solo muestra eventos reales detectados por el servicio Guard.\n" +
                            "Activa Guard → toca 'Activar' para comenzar a registrar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.35f)
                        )
                    }
                }
            }

            else -> {
                // Summary chip
                Surface(
                    color    = MaterialTheme.colorScheme.primary.copy(0.08f),
                    shape    = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${events.size} acceso(s) registrado(s)",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LazyColumn(
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(events, key = { "${it.timestamp}-${it.sensorType}-${it.packageName}" }) { ev ->
                        HistoryEventCard(ev)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEventCard(event: AccessEvent) {
    val sdf = remember { SimpleDateFormat("HH:mm  dd/MM", Locale.getDefault()) }
    val (color, icon, label) = when (event.sensorType) {
        SensorType.CAMERA     -> Triple(Color(0xFF0EA5E9), Icons.Default.Videocam,  "Cámara")
        SensorType.MICROPHONE -> Triple(Color(0xFFEF5B5B), Icons.Default.Mic,       "Micrófono")
        SensorType.BOTH       -> Triple(Color(0xFFF59E0B), Icons.Default.Videocam,  "Cám + Mic")
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color    = color.copy(0.12f),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(event.appName,
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 1)
                Text(label,
                    style = MaterialTheme.typography.bodySmall,
                    color = color)
                if (event.note.isNotEmpty()) {
                    Text(event.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.35f))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(sdf.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                if (event.durationMs > 0) {
                    Text("${event.durationMs / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.35f))
                }
            }
        }
    }
}
