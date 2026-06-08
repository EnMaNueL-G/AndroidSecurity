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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.model.*
import com.enmanuelgil.androidsecurity.ui.components.*
import com.enmanuelgil.androidsecurity.ui.viewmodel.GuardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GuardScreen(vm: GuardViewModel = viewModel()) {
    val context    = LocalContext.current
    val accesses   by vm.accesses.collectAsState()
    val loading    by vm.loading.collectAsState()
    val hasPermission = remember { checkUsagePermission(context) }
    var permState  by remember { mutableStateOf(hasPermission) }

    LaunchedEffect(permState) {
        if (permState) vm.load(context)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title    = stringResource(R.string.guard_title),
            subtitle = stringResource(R.string.guard_subtitle)
        )

        if (!permState) {
            // Permission request card
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
                        Icon(Icons.Default.Visibility, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.guard_needs_permission),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.guard_permission_why),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.guard_grant_permission))
                        }
                        // Re-check after returning from settings
                        TextButton(onClick = { permState = checkUsagePermission(context) }) {
                            Text("Ya lo concedí — verificar", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            return@Column
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        if (accesses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = Color(0xFF3DDC84), modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.guard_no_access),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF3DDC84))
                }
            }
            return@Column
        }

        // Stats
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val camCount = accesses.count { it.accessType == SensorType.CAMERA || it.accessType == SensorType.BOTH }
            val micCount = accesses.count { it.accessType == SensorType.MICROPHONE || it.accessType == SensorType.BOTH }
            StatChip(value = camCount.toString(), label = "Cámara", color = Color(0xFF0EA5E9))
            StatChip(value = micCount.toString(), label = "Micrófono", color = Color(0xFFEF5B5B))
            StatChip(value = accesses.size.toString(), label = "Total apps", color = MaterialTheme.colorScheme.primary)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(accesses, key = { it.packageName }) { entry ->
                GuardEntryCard(entry)
            }

            item {
                Text(
                    stringResource(R.string.guard_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun GuardEntryCard(entry: SensorAccessEntry) {
    val typeColor = when (entry.accessType) {
        SensorType.CAMERA     -> Color(0xFF0EA5E9)
        SensorType.MICROPHONE -> Color(0xFFEF5B5B)
        SensorType.BOTH       -> Color(0xFFF59E0B)
    }
    val typeLabel = when (entry.accessType) {
        SensorType.CAMERA     -> "Cámara"
        SensorType.MICROPHONE -> "Micrófono"
        SensorType.BOTH       -> "Cám + Mic"
    }
    val typeIcon = when (entry.accessType) {
        SensorType.CAMERA     -> Icons.Default.Videocam
        SensorType.MICROPHONE -> Icons.Default.Mic
        SensorType.BOTH       -> Icons.Default.Sensors
    }
    val sdf = remember { SimpleDateFormat("HH:mm  dd/MM", Locale.getDefault()) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(drawable = entry.icon, size = 40.dp)

            Column(Modifier.weight(1f)) {
                Text(entry.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "Último: ${sdf.format(Date(entry.lastAccess))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(color = typeColor.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(12.dp))
                        Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = typeColor)
                    }
                }
                if (entry.accessCount > 0) {
                    Text(
                        "${entry.accessCount} hoy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                }
            }
        }
    }
}

private fun checkUsagePermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }
}
