package com.enmanuelgil.androidsecurity.ui.viewmodel

import android.app.AppOpsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.androidsecurity.data.PermissionAnalyzer
import com.enmanuelgil.androidsecurity.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class HuellaStats(
    val cameraApps   : Int = 0,
    val micApps      : Int = 0,
    val locationApps : Int = 0,
    val contactsApps : Int = 0,
    val smsApps      : Int = 0,
    val privacyScore : Int = 100
)

data class AnomaliesState(
    val anomalies    : List<AnomalyEntry>  = emptyList(),
    val huellaStats  : HuellaStats          = HuellaStats(),
    val loading      : Boolean              = true
)

class AnomaliesViewModel : ViewModel() {

    private val _state = MutableStateFlow(AnomaliesState())
    val state: StateFlow<AnomaliesState> = _state

    fun load(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = AnomaliesState(loading = true)
            try {
                val anomalies  = buildAnomalies(context)
                val huellaStats = buildHuellaStats(context)
                _state.value = AnomaliesState(anomalies, huellaStats, false)
            } catch (e: Exception) {
                _state.value = AnomaliesState(loading = false)
            }
        }
    }

    // ── Device-level anomaly checks ───────────────────
    private fun buildAnomalies(context: Context): List<AnomalyEntry> {
        val list = mutableListOf<AnomalyEntry>()
        val pm   = context.packageManager
        val cr   = context.contentResolver

        // 1. Root indicators
        val rootPaths = listOf(
            "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk", "/data/local/su"
        )
        if (rootPaths.any { File(it).exists() }) {
            list.add(AnomalyEntry(
                title        = "Root detectado",
                description  = "Se detectaron indicadores de root en el sistema. Las apps con root pueden " +
                               "acceder a todos tus datos, modificar el sistema y grabar sin restricciones.",
                riskLevel    = RiskLevel.HIGH,
                type         = AnomalyType.ROOT_DETECTED,
                actionText   = "",
                actionScheme = ""
            ))
        }

        // 2. Developer options
        val devOptions = Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        if (devOptions) {
            list.add(AnomalyEntry(
                title        = "Opciones de desarrollador activas",
                description  = "Las opciones de desarrollador habilitan funciones avanzadas que pueden " +
                               "ser explotadas si alguien tiene acceso físico al dispositivo.",
                riskLevel    = RiskLevel.MEDIUM,
                type         = AnomalyType.DEV_OPTIONS,
                actionText   = "Desactivar",
                actionScheme = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            ))
        }

        // 3. USB debugging
        val usbDebug = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
        if (usbDebug) {
            list.add(AnomalyEntry(
                title        = "Depuración USB activada",
                description  = "Con depuración USB activa, cualquiera con acceso físico puede instalar " +
                               "apps, extraer datos o ejecutar comandos en tu dispositivo por cable.",
                riskLevel    = RiskLevel.HIGH,
                type         = AnomalyType.USB_DEBUG,
                actionText   = "Desactivar",
                actionScheme = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            ))
        }

        // 4. ADB over network (TCP debugging)
        val adbNetwork = try {
            Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1 ||
            Settings.Secure.getInt(cr, "adb_port", 0) > 0
        } catch (_: Exception) { false }
        if (adbNetwork) {
            list.add(AnomalyEntry(
                title        = "ADB por red activado",
                description  = "La depuración ADB inalámbrica permite conexiones remotas al dispositivo " +
                               "desde cualquier equipo en la misma red WiFi.",
                riskLevel    = RiskLevel.HIGH,
                type         = AnomalyType.ADB_NETWORK
            ))
        }

        // 5. User-installed CA certificates
        val hasCerts = try {
            val ks = java.security.KeyStore.getInstance("AndroidCAStore")
            ks.load(null)
            ks.aliases().toList().any { it.startsWith("user:") }
        } catch (_: Exception) { false }
        if (hasCerts) {
            list.add(AnomalyEntry(
                title        = "Certificado CA de usuario instalado",
                description  = "Hay una autoridad de certificación instalada manualmente. Podría permitir " +
                               "interceptar conexiones HTTPS (ataque man-in-the-middle).",
                riskLevel    = RiskLevel.HIGH,
                type         = AnomalyType.USER_CERTIFICATE,
                actionText   = "Ver certificados",
                actionScheme = Settings.ACTION_SECURITY_SETTINGS
            ))
        }

        // 6. VPN active
        val vpnActive = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (_: Exception) { false }
        if (vpnActive) {
            list.add(AnomalyEntry(
                title        = "VPN activa",
                description  = "Hay una conexión VPN activa. Tu tráfico de red está siendo redirigido " +
                               "a través de un servidor externo. Verifica que sea de confianza.",
                riskLevel    = RiskLevel.MEDIUM,
                type         = AnomalyType.VPN_ACTIVE,
                actionText   = "Ver VPN",
                actionScheme = Settings.ACTION_VPN_SETTINGS
            ))
        }

        // 7. Notification listener apps (non-system)
        val notifListeners = (Settings.Secure.getString(cr, "enabled_notification_listeners") ?: "")
            .split(":")
            .mapNotNull { it.split("/").firstOrNull()?.trim()?.takeIf { p -> p.isNotEmpty() } }
            .distinct()
            .filter { pkg ->
                try { !PermissionAnalyzer.isSystemApp(pm.getApplicationInfo(pkg, 0)) }
                catch (_: Exception) { false }
            }
        if (notifListeners.isNotEmpty()) {
            val names = notifListeners.joinToString(", ") { pkg ->
                try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                catch (_: Exception) { pkg }
            }
            list.add(AnomalyEntry(
                title        = "Apps leyendo tus notificaciones",
                description  = "Estas apps tienen acceso a TODAS tus notificaciones: $names. " +
                               "Pueden leer mensajes, códigos 2FA y alertas bancarias.",
                riskLevel    = RiskLevel.MEDIUM,
                type         = AnomalyType.NOTIFICATION_LISTENERS,
                value        = names,
                actionText   = "Gestionar",
                actionScheme = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            ))
        }

        // 8. Overlay apps (SYSTEM_ALERT_WINDOW) — non-system
        val overlayApps = try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            pm.getInstalledApplications(0)
                .filter { info ->
                    !PermissionAnalyzer.isSystemApp(info) &&
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                        info.uid, info.packageName
                    ) == AppOpsManager.MODE_ALLOWED
                }
                .mapNotNull { info ->
                    try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { null }
                }
        } catch (_: Exception) { emptyList() }
        if (overlayApps.isNotEmpty()) {
            list.add(AnomalyEntry(
                title        = "Apps con permiso de overlay",
                description  = "Pueden dibujar sobre otras aplicaciones: ${overlayApps.joinToString(", ")}. " +
                               "Podría usarse para mostrar pantallas falsas sobre apps de banca o contraseñas.",
                riskLevel    = RiskLevel.MEDIUM,
                type         = AnomalyType.OVERLAY_APPS,
                value        = overlayApps.joinToString(", "),
                actionText   = "Gestionar",
                actionScheme = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            ))
        }

        // 9. Battery whitelist (apps bypassing Doze — can run 24/7)
        val pm2 = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val whitelist = pm.getInstalledApplications(0)
            .filter { info ->
                !PermissionAnalyzer.isSystemApp(info) &&
                pm2.isIgnoringBatteryOptimizations(info.packageName)
            }
        if (whitelist.size > 3) {   // small count is normal (alarm clock, etc.)
            list.add(AnomalyEntry(
                title        = "${whitelist.size} apps exentas de optimización de batería",
                description  = "Estas apps pueden ejecutarse en segundo plano de forma continua, " +
                               "sin restricciones de ahorro de energía, incluso cuando el dispositivo está en reposo.",
                riskLevel    = RiskLevel.LOW,
                type         = AnomalyType.BATTERY_WHITELIST,
                value        = whitelist.size.toString(),
                actionText   = "Gestionar",
                actionScheme = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ))
        }

        return list
    }

    // ── Mi Huella Digital stats ───────────────────────
    private fun buildHuellaStats(context: Context): HuellaStats {
        val pm     = context.packageManager
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        // AppOpsManager refleja el estado de concesión runtime real en Android 10+
        // checkOpNoThrow con UID de la app funciona sin permisos especiales
        fun isOpGranted(op: String, uid: Int, pkgName: String): Boolean = try {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(op, uid, pkgName) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }

        // GET_PERMISSIONS para acceder a requestedPermissions (declaraciones en manifest)
        val packages = try {
            if (Build.VERSION.SDK_INT >= 33)
                pm.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(
                    android.content.pm.PackageManager.GET_PERMISSIONS.toLong()))
            else
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(android.content.pm.PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) { emptyList() }

        val userPackages = packages.filter { pkg ->
            try { !PermissionAnalyzer.isSystemApp(pkg.applicationInfo) }
            catch (_: Exception) { false }
        }

        // Dual check: declarado en manifest + AppOps ALLOWED (usuario lo concedió en runtime)
        // applicationInfo puede ser null en algunos paquetes del sistema → usamos ?: continue
        fun countOp(op: String, manifestPerm: String): Int = userPackages.count { pkg ->
            val uid = pkg.applicationInfo?.uid ?: return@count false
            val declared = pkg.requestedPermissions?.contains(manifestPerm) == true
            declared && isOpGranted(op, uid, pkg.packageName)
        }

        val cam = countOp(AppOpsManager.OPSTR_CAMERA,        "android.permission.CAMERA")
        val mic = countOp(AppOpsManager.OPSTR_RECORD_AUDIO,  "android.permission.RECORD_AUDIO")
        val loc = userPackages.count { pkg ->
            val uid = pkg.applicationInfo?.uid ?: return@count false
            val declFine   = pkg.requestedPermissions?.contains("android.permission.ACCESS_FINE_LOCATION")   == true
            val declCoarse = pkg.requestedPermissions?.contains("android.permission.ACCESS_COARSE_LOCATION") == true
            (declFine   && isOpGranted(AppOpsManager.OPSTR_FINE_LOCATION,   uid, pkg.packageName)) ||
            (declCoarse && isOpGranted(AppOpsManager.OPSTR_COARSE_LOCATION, uid, pkg.packageName))
        }
        val con = countOp(AppOpsManager.OPSTR_READ_CONTACTS, "android.permission.READ_CONTACTS")
        val sms = countOp(AppOpsManager.OPSTR_READ_SMS,      "android.permission.READ_SMS")

        val score = (100 - cam * 3 - mic * 3 - loc * 4 - con * 2 - sms * 6).coerceIn(0, 100)
        return HuellaStats(cam, mic, loc, con, sms, score)
    }
}
