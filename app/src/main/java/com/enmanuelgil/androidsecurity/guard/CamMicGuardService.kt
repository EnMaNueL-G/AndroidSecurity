package com.enmanuelgil.androidsecurity.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingCallback
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.enmanuelgil.androidsecurity.MainActivity
import com.enmanuelgil.androidsecurity.R

class CamMicGuardService : Service() {

    companion object {
        const val CHANNEL_ID   = "cam_mic_guard"
        const val NOTIF_ID     = 1001
        const val ACTION_START = "com.enmanuelgil.androidsecurity.GUARD_START"
        const val ACTION_STOP  = "com.enmanuelgil.androidsecurity.GUARD_STOP"
    }

    private var cameraManager: CameraManager?    = null
    private var audioManager: AudioManager?      = null
    private var cameraCallback: CameraManager.AvailabilityCallback? = null
    private var audioCallback: AudioRecordingCallback?              = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }

        startForeground(NOTIF_ID, buildNotification(getString(R.string.guard_service_monitoring)))
        registerCameraCallback()
        registerAudioCallback()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterCallbacks()
        super.onDestroy()
    }

    // ── Camera callback ────────────────────────────────────────────────────────
    private fun registerCameraCallback() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) {
                // Camera is in use by some app
                updateNotification(getString(R.string.guard_camera_in_use))
            }
            override fun onCameraAvailable(cameraId: String) {
                updateNotification(getString(R.string.guard_service_monitoring))
            }
        }
        cameraManager?.registerAvailabilityCallback(cameraCallback!!, null)
    }

    // ── Audio callback ─────────────────────────────────────────────────────────
    private fun registerAudioCallback() {
        if (Build.VERSION.SDK_INT < 29) return
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioCallback = object : AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                if (configs.isNotEmpty()) {
                    updateNotification(getString(R.string.guard_mic_in_use))
                } else {
                    updateNotification(getString(R.string.guard_service_monitoring))
                }
            }
        }
        audioManager?.registerAudioRecordingCallback(audioCallback!!, null)
    }

    private fun unregisterCallbacks() {
        try { cameraCallback?.let { cameraManager?.unregisterAvailabilityCallback(it) } } catch (e: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                audioCallback?.let { audioManager?.unregisterAudioRecordingCallback(it) }
            }
        } catch (e: Exception) {}
    }

    // ── Notifications ──────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.guard_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.guard_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle(getString(R.string.guard_notif_title))
        .setContentText(text)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            android.R.drawable.ic_delete,
            getString(R.string.guard_stop_service),
            PendingIntent.getService(
                this, 0,
                Intent(this, CamMicGuardService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, buildNotification(text))
    }
}
