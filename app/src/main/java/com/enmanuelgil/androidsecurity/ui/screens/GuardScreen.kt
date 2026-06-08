package com.enmanuelgil.androidsecurity.ui.screens

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.data.AccessEvent
import com.enmanuelgil.androidsecurity.guard.CamMicGuardService
import com.enmanuelgil.androidsecurity.model.SensorAccessEntry
import com.enmanuelgil.androidsecurity.model.SensorType
import com.enmanuelgil.androidsecurity.ui.components.AppIcon
import com.enmanuelgil.androidsecurity.ui.components.ScreenHeader
import com.enmanuelgil.androidsecurity.ui.viewmodel.GuardViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private fun hasUsagePermission(context: Context): Boolean = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    appOps.checkOpNoThrow(
        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(), context.packageName
    ) == android.app.AppOpsManager.MODE_ALLOWED
} catch (_: Exception) { false }

@Composable
fun GuardScreen(vm: GuardViewModel = viewModel()) {
    val context = LocalContext.current
    val data    by vm.data.collectAsState()
    val hasUsagePerm = remember { hasUsagePermission(context) }

    // ── Real-time camera state via CameraManager ──────
    val cameraInUse = remember { mutableStateMapOf<String, Boolean>() }
    val camActive   = cameraInUse.values.any { it }

    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cb = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(id: String) { cameraInUse[id] = true  }
            override fun onCameraAvailable(id: String)   { cameraInUse[id] = false }
        }
        cm.registerAvailabilityCallback(cb, null)
        onDispose { cm.unregisterAvailabilityCallback(cb) }
    }

    // ── Real-time mic state via AudioManager ──────────
    var micActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        while (true) {
            micActive = am.activeRecordingConfigurations.isNotEmpty()
            delay(2_000)
        }
    }

    // ── Service running state ─────────────────────────
    var serviceRunning by remember { mutableStateOf(isServiceRunning(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            serviceRunning = isServiceRunning(context)
            delay(3_000)
        }
    }

    // ── Reload when tab becomes visible again (lifecycle RESUME) ──
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.load(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { vm.load(context) }

    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(bottom = 24.dp)
    ) {
        // ── Header con botón actualizar ─────────────────
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.guard_title),
                        style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.guard_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
                IconButton(
                    onClick = { vm.load(context) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Actualizar",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(0.3f)
            )
        }

        // ── Banner: permiso Acceso al Uso ──────────────
        if (!hasUsagePerm) {
            item {
                Surface(
                    color    = Color(0xFFF59E0B).copy(0.12f),
                    shape    = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, null,
                            tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Identificación de apps limitada",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFF59E0B))
                            Text("Para saber qué app usó la cámara/micrófono, concede 'Acceso al Uso' en Ajustes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                        ) {
                            Text("Conceder", style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF59E0B))
                        }
                    }
                }
            }
        }

        // ── Real-time status cards ──────────────────────
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado en tiempo real",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RealTimeCard(
                        label   = "Cámara",
                        icon    = Icons.Default.Videocam,
                        active  = camActive,
                        modifier = Modifier.weight(1f)
                    )
                    RealTimeCard(
                        label   = "Micrófono",
                        icon    = Icons.Default.Mic,
                        active  = micActive,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Disclaimer about mic on Android 12+
                Text(
                    "⚠ En Android 12+ la detección de micrófono puede no identificar apps de terceros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    fontSize = 10.sp
                )
            }
        }

        // ── Service toggle ──────────────────────────────
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color  = if (serviceRunning) Color(0xFF3DDC84).copy(0.15f)
                             else MaterialTheme.colorScheme.surfaceVariant,
                    shape  = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (serviceRunning) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                            null,
                            tint = if (serviceRunning) Color(0xFF3DDC84)
                                   else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (serviceRunning) "Servicio Guard activo — registrando eventos"
                            else "Servicio Guard inactivo — no se registran eventos",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (serviceRunning) Color(0xFF3DDC84)
                                    else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }
                }
                Button(
                    onClick = {
                        val intent = Intent(context, CamMicGuardService::class.java)
                        if (serviceRunning) {
                            intent.action = CamMicGuardService.ACTION_STOP
                            context.startService(intent)
                        } else {
                            context.startForegroundService(intent)
                        }
                        serviceRunning = !serviceRunning
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serviceRunning) Color(0xFFEF5B5B)
                                         else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(if (serviceRunning) "Detener" else "Activar",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── Apps with camera/mic permission GRANTED ─────
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Apps con acceso a cámara / micrófono",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                "Estas apps tienen permiso concedido (no significa que estén usándolo ahora)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        when {
            data.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            data.grantedApps.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Ninguna app de usuario tiene permiso de cámara o micrófono",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3DDC84))
                }
            }
            else -> items(data.grantedApps, key = { it.packageName }) { app ->
                GrantedAppCard(app, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        // ── Service history from AccessLog ──────────────
        item {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Historial del servicio Guard",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
                if (data.serviceEvents.isNotEmpty()) {
                    TextButton(
                        onClick = { vm.clearHistory(context) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Limpiar", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }
            }
        }

        if (data.serviceEvents.isEmpty()) {
            item {
                Surface(
                    color  = MaterialTheme.colorScheme.surface,
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.History, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.25f),
                            modifier = Modifier.size(32.dp))
                        Text("Sin registros aún",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        Text("Activa el servicio Guard para que comience a detectar y registrar accesos a cámara y micrófono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                    }
                }
            }
        } else {
            items(data.serviceEvents.take(20), key = { "${it.timestamp}-${it.sensorType}" }) { event ->
                ServiceEventCard(event, Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
            }
            if (data.serviceEvents.size > 20) {
                item {
                    Text(
                        "+ ${data.serviceEvents.size - 20} registros anteriores",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Real-time status card ─────────────────────────────
@Composable
private fun RealTimeCard(
    label   : String,
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    active  : Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (active) Color(0xFFEF5B5B) else Color(0xFF3DDC84)
    Surface(
        color    = color.copy(0.12f),
        shape    = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                if (active) "EN USO" else "LIBRE",
                style     = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color     = color
            )
        }
    }
}

// ── Granted app row ───────────────────────────────────
@Composable
private fun GrantedAppCard(app: SensorAccessEntry, modifier: Modifier = Modifier) {
    val typeColor = when (app.accessType) {
        SensorType.CAMERA     -> Color(0xFF0EA5E9)
        SensorType.MICROPHONE -> Color(0xFFEF5B5B)
        SensorType.BOTH       -> Color(0xFFF59E0B)
    }
    val typeLabel = when (app.accessType) {
        SensorType.CAMERA     -> "Cámara"
        SensorType.MICROPHONE -> "Micrófono"
        SensorType.BOTH       -> "Cám + Mic"
    }
    Surface(
        color    = MaterialTheme.colorScheme.surface,
        shape    = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(drawable = app.icon, size = 36.dp)
            Text(app.appName, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), maxLines = 1)
            Surface(color = typeColor.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
                Text(typeLabel,
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor)
            }
        }
    }
}

// ── Service event row ─────────────────────────────────
@Composable
private fun ServiceEventCard(event: AccessEvent, modifier: Modifier = Modifier) {
    val sdf  = remember { SimpleDateFormat("HH:mm  dd/MM", Locale.getDefault()) }
    val color = when (event.sensorType) {
        com.enmanuelgil.androidsecurity.model.SensorType.CAMERA     -> Color(0xFF0EA5E9)
        com.enmanuelgil.androidsecurity.model.SensorType.MICROPHONE -> Color(0xFFEF5B5B)
        com.enmanuelgil.androidsecurity.model.SensorType.BOTH       -> Color(0xFFF59E0B)
    }
    val sensorLabel = when (event.sensorType) {
        com.enmanuelgil.androidsecurity.model.SensorType.CAMERA     -> "Cámara"
        com.enmanuelgil.androidsecurity.model.SensorType.MICROPHONE -> "Micrófono"
        com.enmanuelgil.androidsecurity.model.SensorType.BOTH       -> "Cám+Mic"
    }
    Surface(color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = color.copy(0.12f), shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (event.sensorType == com.enmanuelgil.androidsecurity.model.SensorType.MICROPHONE)
                            Icons.Default.Mic else Icons.Default.Videocam,
                        null, tint = color, modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(event.appName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(sensorLabel, style = MaterialTheme.typography.labelSmall, color = color)
            }
            Text(sdf.format(Date(event.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        }
    }
}

@Suppress("DEPRECATION")
private fun isServiceRunning(context: Context): Boolean = try {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    am.getRunningServices(500).any {
        it.service.className == CamMicGuardService::class.java.name
    }
} catch (_: Exception) { false }
