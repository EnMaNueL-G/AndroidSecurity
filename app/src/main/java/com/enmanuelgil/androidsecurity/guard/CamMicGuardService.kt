package com.enmanuelgil.androidsecurity.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.enmanuelgil.androidsecurity.MainActivity
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.data.AccessEvent
import com.enmanuelgil.androidsecurity.data.AccessLog
import com.enmanuelgil.androidsecurity.model.SensorType

class CamMicGuardService : Service() {

    companion object {
        const val CHANNEL_ID   = "cam_mic_guard"
        const val NOTIF_ID     = 1001
        const val ACTION_START = "com.enmanuelgil.androidsecurity.GUARD_START"
        const val ACTION_STOP  = "com.enmanuelgil.androidsecurity.GUARD_STOP"
    }

    private var cameraManager : CameraManager? = null
    private var audioManager  : AudioManager?  = null

    private var cameraCallback: CameraManager.AvailabilityCallback? = null
    private var audioCallback : AudioManager.AudioRecordingCallback? = null

    // Track which cameras are currently in use (cameraId → in-use)
    private val cameraInUse = mutableMapOf<String, Boolean>()
    // Track mic session start time for duration calculation
    private var micSessionStart = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIF_ID, buildNotification(getString(R.string.guard_service_monitoring)))
        registerCallbacks()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterCallbacks()
        super.onDestroy()
    }

    // ── Camera monitoring ──────────────────────────────
    private fun registerCallbacks() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) {
                if (cameraInUse[cameraId] == true) return  // already tracking
                cameraInUse[cameraId] = true
                updateNotification(getString(R.string.guard_camera_in_use))

                // Log event — try to attribute to foreground app via UsageStats
                val foregroundPkg = getForegroundApp()
                val appLabel = when {
                    foregroundPkg != null -> getAppName(foregroundPkg)
                    hasUsagePermission()  -> getString(R.string.guard_unknown_app)
                    else                  -> getString(R.string.guard_needs_usage_access)
                }
                AccessLog.add(applicationContext, AccessEvent(
                    packageName = foregroundPkg ?: "unknown",
                    appName     = appLabel,
                    sensorType  = SensorType.CAMERA,
                    timestamp   = System.currentTimeMillis(),
                    note        = "cam:$cameraId"
                ))
            }

            override fun onCameraAvailable(cameraId: String) {
                cameraInUse[cameraId] = false
                if (cameraInUse.values.none { it }) {
                    updateNotification(getString(R.string.guard_service_monitoring))
                }
            }
        }
        cameraManager?.registerAvailabilityCallback(cameraCallback!!, null)

        // ── Mic monitoring ─────────────────────────────
        if (Build.VERSION.SDK_INT < 29) return
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                if (configs.isNotEmpty()) {
                    if (micSessionStart == 0L) {
                        micSessionStart = System.currentTimeMillis()
                        updateNotification(getString(R.string.guard_mic_in_use))

                        // Try to identify which app started recording
                        val pkg      = resolveRecordingPackage(configs)
                        val appLabel = when {
                            pkg != null      -> getAppName(pkg)
                            hasUsagePermission() -> getString(R.string.guard_unknown_app)
                            else             -> getString(R.string.guard_needs_usage_access)
                        }
                        AccessLog.add(applicationContext, AccessEvent(
                            packageName = pkg ?: "unknown",
                            appName     = appLabel,
                            sensorType  = SensorType.MICROPHONE,
                            timestamp   = micSessionStart
                        ))
                    }
                } else {
                    if (micSessionStart > 0L) {
                        // Update last logged mic event with duration
                        micSessionStart = 0L
                        updateNotification(getString(R.string.guard_service_monitoring))
                    }
                }
            }
        }
        audioManager?.registerAudioRecordingCallback(audioCallback!!, null)
    }

    /** Identifies the recording app. AudioRecordingConfiguration doesn't expose package name
     *  via public API, so we use the foreground app as best-effort heuristic. */
    @Suppress("UNUSED_PARAMETER")
    private fun resolveRecordingPackage(configs: List<AudioRecordingConfiguration>): String? =
        getForegroundApp()

    /** Returns the package of the most recently used app via UsageStats (most accurate on Android 10+).
     *  Falls back to ActivityManager foreground process list on older versions. */
    private fun getForegroundApp(): String? {
        // Primary: UsageStatsManager — requires PACKAGE_USAGE_STATS (already declared)
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 30_000L, now)
            if (!stats.isNullOrEmpty()) {
                // Excluir nuestra app, launcher y UI del sistema
                val excluded = setOf(
                    packageName,
                    "com.android.systemui",
                    "com.android.launcher3",
                    "com.vivo.launcher",
                    "com.bbk.launcher2"
                )
                val recent = stats
                    .filter { it.packageName !in excluded && it.lastTimeUsed > now - 30_000L }
                    .maxByOrNull { it.lastTimeUsed }
                if (recent != null) return recent.packageName
            }
        } catch (_: Exception) {}

        // Fallback: ActivityManager (works on older Android, restricted on Android 13+)
        @Suppress("DEPRECATION")
        return try {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            am.getRunningAppProcesses()
                ?.filter { it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                ?.firstOrNull { it.pkgList?.none { pkg -> pkg == packageName } == true }
                ?.pkgList?.firstOrNull()
        } catch (_: Exception) { null }
    }

    private fun getAppName(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun hasUsagePermission(): Boolean = try {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    private fun unregisterCallbacks() {
        runCatching { cameraCallback?.let { cameraManager?.unregisterAvailabilityCallback(it) } }
        runCatching {
            audioCallback?.let { cb ->
                if (Build.VERSION.SDK_INT >= 29) audioManager?.unregisterAudioRecordingCallback(cb)
            }
        }
    }

    // ── Notifications ──────────────────────────────────
    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.guard_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.guard_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.guard_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE))
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.guard_stop_service),
                PendingIntent.getService(
                    this, 0,
                    Intent(this, CamMicGuardService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE))
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(text))
    }
}
