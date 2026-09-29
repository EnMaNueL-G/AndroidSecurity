package com.enmanuelgil.androidsecurity.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.enmanuelgil.androidsecurity.MainActivity
import com.enmanuelgil.androidsecurity.data.AccessLog
import com.enmanuelgil.androidsecurity.data.AppScanner
import com.enmanuelgil.androidsecurity.data.Sensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estado del guardia visible para la interfaz (se pone en onCreate/onDestroy, no se adivina). */
object GuardState {
    internal val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private const val PREFS = "guard"
    fun wanted(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)
    fun setWanted(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
}

/**
 * Guardia de cámara y micrófono. No usa la cámara ni el micrófono: solo escucha los avisos
 * públicos de Android «la cámara está ocupada» / «alguien está grabando audio».
 *
 * Límite real (Android 10+): el sistema NO dice qué app es. Se anota la app que estaba en pantalla
 * en ese momento (si diste Acceso a datos de uso) y si la pantalla estaba apagada, que es la señal
 * que importa: nadie debería grabarte con la pantalla apagada.
 */
class CamMicGuardService : Service() {

    companion object {
        const val CHANNEL_ID = "cam_mic_guard"
        const val ALERT_CHANNEL_ID = "cam_mic_alert"
        const val NOTIF_ID = 1001
        const val ALERT_ID = 1002
        const val ACTION_STOP = "com.enmanuelgil.androidsecurity.GUARD_STOP"

        fun start(context: Context) {
            GuardState.setWanted(context, true)
            try { context.startForegroundService(Intent(context, CamMicGuardService::class.java)) } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            GuardState.setWanted(context, false)
            context.stopService(Intent(context, CamMicGuardService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var cameraManager: CameraManager? = null
    private var audioManager: AudioManager? = null
    private var cameraCallback: CameraManager.AvailabilityCallback? = null
    private var torchCallback: CameraManager.TorchCallback? = null
    private var audioCallback: AudioManager.AudioRecordingCallback? = null

    private val busyCameras = mutableSetOf<String>()
    private val torchOn = mutableSetOf<String>()
    private var cameraEventId = 0L
    private var micEventId = 0L
    private var micSilenced = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, ongoing("Vigilando cámara y micrófono"), type)
        } catch (_: Exception) {
            stopSelf(); return
        }
        GuardState._running.value = true
        AccessLog.closeDangling(this)
        register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            GuardState.setWanted(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregister()
        if (cameraEventId != 0L) AccessLog.finish(this, cameraEventId)
        if (micEventId != 0L) AccessLog.finish(this, micEventId)
        GuardState._running.value = false
        super.onDestroy()
    }

    // ── Escucha ─────────────────────────────────────────────────────────────
    private fun register() {
        if (registered) return
        registered = true
        val cm = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraManager = cm
        torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (enabled) torchOn += cameraId else torchOn -= cameraId
            }
            // Cuando una app abre la cámara, la linterna pasa a «no disponible» (no llega onTorchModeChanged)
            override fun onTorchModeUnavailable(cameraId: String) { torchOn -= cameraId }
        }.also { cm.registerTorchCallback(it, handler) }
        cameraCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) {
                busyCameras += cameraId
                // Esperar un momento: encender la linterna también «ocupa» la cámara
                handler.postDelayed({ cameraMaybeStarted() }, 700)
            }
            override fun onCameraAvailable(cameraId: String) {
                busyCameras -= cameraId
                val present = try { cameraManager?.cameraIdList?.toSet() } catch (_: Exception) { null }
                if (present != null) busyCameras.retainAll(present)
                if (busyCameras.isEmpty() && cameraEventId != 0L) {
                    AccessLog.finish(this@CamMicGuardService, cameraEventId)
                    cameraEventId = 0L
                    refreshNotification()
                }
            }
        }.also { cm.registerAvailabilityCallback(it, handler) }

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager = am
        audioCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) = micChanged(configs)
        }.also { am.registerAudioRecordingCallback(it, handler) }
        // Si ya se estaba grabando al activar el guardia
        micChanged(am.activeRecordingConfigurations)
        // La pantalla se apaga con la cámara o el micrófono ya en uso
        screenReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = screenTurnedOff()
        }.also { registerReceiver(it, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private var screenReceiver: android.content.BroadcastReceiver? = null

    private fun screenTurnedOff() {
        val note = "la pantalla se apagó durante el uso"
        if (cameraEventId != 0L) { AccessLog.addNote(this, cameraEventId, note); alert("La cámara sigue en uso con la pantalla apagada") }
        if (micEventId != 0L && !micSilenced) { AccessLog.addNote(this, micEventId, note); alert("El micrófono sigue en uso con la pantalla apagada") }
    }

    private fun unregister() {
        if (!registered) return
        registered = false
        handler.removeCallbacksAndMessages(null)
        runCatching { cameraCallback?.let { cameraManager?.unregisterAvailabilityCallback(it) } }
        runCatching { torchCallback?.let { cameraManager?.unregisterTorchCallback(it) } }
        runCatching { audioCallback?.let { audioManager?.unregisterAudioRecordingCallback(it) } }
        runCatching { screenReceiver?.let { unregisterReceiver(it) } }
    }

    private fun cameraMaybeStarted() {
        // Algunos móviles «retiran» las cámaras cuando están inactivas y Android lo avisa como «no disponible»:
        // solo cuenta si la cámara sigue existiendo
        val present = try { cameraManager?.cameraIdList?.toSet() } catch (_: Exception) { null }
        if (present != null) busyCameras.retainAll(present)
        if (busyCameras.isEmpty() || cameraEventId != 0L) return
        if (busyCameras.all { it in torchOn }) return          // era la linterna
        val (pkg, app, note) = context()
        cameraEventId = AccessLog.start(this, Sensor.CAMERA, pkg, app, note)
        refreshNotification()
        if (note.contains("pantalla apagada")) alert("Cámara en uso con la pantalla apagada")
    }

    private fun micChanged(configs: List<AudioRecordingConfiguration>) {
        if (configs.isNotEmpty() && micEventId == 0L) {
            val (pkg, app, baseNote) = context()
            var note = baseNote
            // Android 10+: una app en segundo plano sin permiso para grabar recibe silencio
            micSilenced = Build.VERSION.SDK_INT >= 29 && configs.all { it.isClientSilenced }
            if (micSilenced)
                note = listOf(note, "Android la silenció (no se grabó sonido)").filter { it.isNotEmpty() }.joinToString(" · ")
            micEventId = AccessLog.start(this, Sensor.MICROPHONE, pkg, app, note)
            refreshNotification()
            if (baseNote.contains("pantalla apagada") && !note.contains("silenció")) alert("Micrófono en uso con la pantalla apagada")
        } else if (configs.isEmpty() && micEventId != 0L) {
            AccessLog.finish(this, micEventId)
            micEventId = 0L
            refreshNotification()
        }
    }

    /** App en pantalla en ese momento (probable) + si la pantalla estaba apagada. */
    private fun context(): Triple<String?, String?, String> {
        val interactive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        val notes = mutableListOf<String>()
        if (!interactive) notes += "pantalla apagada"
        var pkg: String? = null
        var label: String? = null
        if (AppScanner.hasUsageAccess(this)) {
            pkg = lastForeground()
            label = when {
                pkg == null -> null
                pkg == launcherPackage() -> "Pantalla de inicio (ninguna app abierta)"
                else -> try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            }
        } else notes += "sin Acceso a datos de uso"
        return Triple(pkg, label, notes.joinToString(" · "))
    }

    private fun lastForeground(): String? = try {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 30 * 60_000L, now + 1000)
        val e = UsageEvents.Event()
        // Último estado de cada app: si la última en pasar a primer plano ya se fue al fondo, no hay ninguna abierta
        var last: String? = null
        val inFront = mutableMapOf<String, Boolean>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName == "com.android.systemui") continue
            @Suppress("DEPRECATION")
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { inFront[e.packageName] = true; last = e.packageName }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> inFront[e.packageName] = false
            }
        }
        last?.takeIf { inFront[it] == true }
    } catch (_: Exception) { null }

    private fun launcherPackage(): String? = try {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    } catch (_: Exception) { null }

    // ── Notificaciones ──────────────────────────────────────────────────────
    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Guardia activo", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Aviso fijo mientras el guardia de cámara y micrófono está encendido"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL_ID, "Alertas del guardia", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Cámara o micrófono en uso con la pantalla apagada"
        })
    }

    private fun openApp() = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_INITIAL_SCREEN, "GUARD")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun ongoing(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setContentTitle("Guardia de cámara y micrófono")
        .setContentText(text)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(openApp())
        .addAction(0, "Detener", PendingIntent.getService(this, 1,
            Intent(this, CamMicGuardService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun refreshNotification() {
        val text = when {
            cameraEventId != 0L && micEventId != 0L -> "Cámara y micrófono EN USO ahora"
            cameraEventId != 0L -> "Cámara EN USO ahora"
            micEventId != 0L -> "Micrófono EN USO ahora"
            else -> "Vigilando cámara y micrófono"
        }
        try { getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, ongoing(text)) } catch (_: Exception) {}
    }

    private fun alert(text: String) {
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(text)
            .setContentText("Toca para ver el registro del guardia")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        try { getSystemService(NotificationManager::class.java)?.notify(if (text.contains("cámara", ignoreCase = true)) ALERT_ID else ALERT_ID + 1, n) } catch (_: Exception) {}
    }
}
