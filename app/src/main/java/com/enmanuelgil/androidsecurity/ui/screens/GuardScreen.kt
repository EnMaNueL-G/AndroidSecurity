package com.enmanuelgil.androidsecurity.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.enmanuelgil.androidsecurity.data.AccessEvent
import com.enmanuelgil.androidsecurity.data.AccessLog
import com.enmanuelgil.androidsecurity.data.AppScanner
import com.enmanuelgil.androidsecurity.data.Sensor
import com.enmanuelgil.androidsecurity.guard.CamMicGuardService
import com.enmanuelgil.androidsecurity.guard.GuardState
import com.enmanuelgil.androidsecurity.ui.components.*

/** Estado en vivo de cámara y micrófono mientras la pantalla está visible (sin servicio). */
@Composable
private fun rememberLiveSensors(): Pair<State<Int>, State<Int>> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val camera = remember { mutableIntStateOf(0) }    // 0 libre · 1 en uso · 2 linterna
    val mic = remember { mutableIntStateOf(0) }       // 0 libre · 1 en uso · 2 en uso pero silenciado
    DisposableEffect(owner) {
        val handler = Handler(Looper.getMainLooper())
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val busy = mutableSetOf<String>(); val torch = mutableSetOf<String>()
        fun updCam() {
            // Cámaras «retiradas» por el sistema cuando están inactivas no cuentan como en uso
            val present = try { cm.cameraIdList.toSet() } catch (_: Exception) { null }
            if (present != null) busy.retainAll(present)
            camera.intValue = when { busy.isEmpty() -> 0; busy.all { it in torch } -> 2; else -> 1 }
        }
        fun updMic(c: List<AudioRecordingConfiguration>) {
            mic.intValue = when { c.isEmpty() -> 0; Build.VERSION.SDK_INT >= 29 && c.all { it.isClientSilenced } -> 2; else -> 1 }
        }
        val camCb = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(id: String) { busy += id; updCam() }
            override fun onCameraAvailable(id: String) { busy -= id; updCam() }
        }
        val torchCb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) { if (enabled) torch += id else torch -= id; updCam() }
            override fun onTorchModeUnavailable(id: String) { torch -= id; updCam() }
        }
        val micCb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) = updMic(configs)
        }
        var on = false
        fun register() {
            if (on) return; on = true
            busy.clear(); torch.clear()
            cm.registerTorchCallback(torchCb, handler); cm.registerAvailabilityCallback(camCb, handler)
            am.registerAudioRecordingCallback(micCb, handler); updMic(am.activeRecordingConfigurations)
        }
        fun unregister() {
            if (!on) return; on = false
            runCatching { cm.unregisterAvailabilityCallback(camCb) }; runCatching { cm.unregisterTorchCallback(torchCb) }
            runCatching { am.unregisterAudioRecordingCallback(micCb) }
        }
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) register() else if (e == Lifecycle.Event.ON_PAUSE) unregister()
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        onDispose { owner.lifecycle.removeObserver(obs); unregister() }
    }
    return camera to mic
}

@Composable
fun GuardScreen() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val running by GuardState.running.collectAsState()
    LaunchedEffect(Unit) { AccessLog.load(context) }
    val events by AccessLog.events.collectAsState()
    val (camera, mic) = rememberLiveSensors()
    var usage by remember { mutableStateOf(AppScanner.hasUsageAccess(context)) }
    fun notifsEnabled(): Boolean {
        val nm = androidx.core.app.NotificationManagerCompat.from(context)
        val ch = nm.getNotificationChannel(CamMicGuardService.ALERT_CHANNEL_ID)
        return nm.areNotificationsEnabled() && (ch == null || ch.importance != android.app.NotificationManager.IMPORTANCE_NONE)
    }
    var notifOk by remember { mutableStateOf(notifsEnabled()) }
    var confirmClear by remember { mutableStateOf(false) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { usage = AppScanner.hasUsageAccess(context); notifOk = notifsEnabled() } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        CamMicGuardService.start(context); notifOk = notifsEnabled()
    }

    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        else CamMicGuardService.start(context)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader("Guardia", "Cámara y micrófono: ahora y mientras no miras") }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveTile(Modifier.weight(1f), Icons.Default.Videocam, "Cámara",
                    when (camera.value) { 1 -> "EN USO"; 2 -> "LINTERNA"; else -> "LIBRE" }, camera.value == 1)
                LiveTile(Modifier.weight(1f), Icons.Default.Mic, "Micrófono",
                    when (mic.value) { 1 -> "EN USO"; 2 -> "SILENCIADO"; else -> "LIBRE" }, mic.value == 1)
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Guardia en segundo plano", fontWeight = FontWeight.Bold)
                        Text(if (running) "Activo · se reactiva al reiniciar el móvil" else "Apagado",
                            color = if (running) RiskOk else Muted, fontSize = 13.sp)
                    }
                    Switch(checked = running, onCheckedChange = { if (it) enable() else CamMicGuardService.stop(context) })
                }
                Text("Anota cada vez que la cámara o el micrófono se ocupan: hora, duración, si la pantalla estaba apagada y qué app " +
                    "estaba en pantalla. Te avisa si se usan con la pantalla apagada. No usa la cámara ni el micrófono y apenas gasta batería. " +
                    "Algunos fabricantes impiden que se reactive al reiniciar: permite el «inicio automático» si tu móvil lo tiene.",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (running && !notifOk) {
                    Text("Las notificaciones de la app están bloqueadas: el guardia anota, pero no puede avisarte.",
                        color = RiskMed, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { openAppDetails(context, context.packageName) }
                    }, modifier = Modifier.padding(top = 6.dp)) { Text("Permitir notificaciones") }
                }
            }
        }
        if (!usage) item {
            Panel {
                Text("Sin «Acceso a datos de uso» el registro no puede anotar qué app estaba en pantalla.", fontSize = 13.sp)
                OutlinedButton(onClick = { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS, context.packageName) },
                    modifier = Modifier.padding(top = 6.dp)) { Text("Conceder") }
            }
        }
        item {
            Panel {
                Text("Lo que Android no deja saber", fontWeight = FontWeight.SemiBold)
                Text("Ninguna app normal puede saber QUÉ app usa la cámara o el micrófono: Android solo avisa de que están ocupados. " +
                    "Por eso el registro dice «en pantalla: …» (probable, no confirmado). Si no había ninguna app abierta o la pantalla " +
                    "estaba apagada, sospecha. " +
                    if (Build.VERSION.SDK_INT >= 31) "En tu Android, el punto verde de la barra y el Panel de privacidad sí muestran la app exacta."
                    else "En Android 12 o superior el propio sistema lo muestra; tu versión no.",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                if (Build.VERSION.SDK_INT >= 31)
                    TextButton(onClick = { openSettings(context, Settings.ACTION_PRIVACY_SETTINGS) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Abrir Privacidad (Panel de privacidad)")
                    }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("REGISTRO (${events.size})", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.weight(1f))
                if (events.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Borrar") }
            }
        }
        if (events.isEmpty()) item {
            Text(if (running) "Aún no se ha ocupado la cámara ni el micrófono desde que activaste el guardia."
                else "Activa el guardia para empezar a anotar. Solo se guardan los usos vistos con el guardia activo.",
                color = Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        items(events, key = { it.id }) { EventRow(it, running) }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("¿Borrar el registro?") },
        text = { Text("Se borran los ${events.size} usos anotados. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { AccessLog.clear(context); confirmClear = false }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun LiveTile(modifier: Modifier, icon: ImageVector, label: String, state: String, busy: Boolean) {
    val color = if (busy) RiskHigh else if (state == "LIBRE") RiskOk else RiskMed
    Surface(modifier, color = color.copy(0.10f), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = color)
            Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Text(state, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun EventRow(e: AccessEvent, running: Boolean) {
    val context = LocalContext.current
    val date = DateFormat.getDateFormat(context).format(e.start) + " " + DateFormat.getTimeFormat(context).format(e.start)
    val dur = when {
        e.end == 0L && running -> "en uso ahora"
        e.end <= 0L -> "duración desconocida"
        else -> duration(e.end - e.start)
    }
    val suspicious = e.note.contains("pantalla apagada") || e.foregroundApp?.startsWith("Pantalla de inicio") == true
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (e.sensor == Sensor.CAMERA) Icons.Default.Videocam else Icons.Default.Mic, null,
                tint = if (suspicious) RiskHigh else RiskLow)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${e.sensor.label} · $date", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(dur, color = Muted, fontSize = 12.sp)
                e.foregroundApp?.let { Text("En pantalla: $it (probable)", fontSize = 13.sp) }
                if (e.note.isNotEmpty()) Text(e.note, color = if (suspicious) RiskHigh else Muted, fontSize = 12.sp)
            }
        }
    }
}

private fun duration(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min ${s % 60} s"
        else -> "${s / 3600} h ${(s % 3600) / 60} min"
    }
}
