package com.enmanuelgil.androidsecurity.data

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.enmanuelgil.androidsecurity.model.*

object PermissionAnalyzer {

    // ── High / Medium risk permission sets ───────────
    private val HIGH_RISK = setOf(
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CONTACTS",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_DEVICE_ADMIN",
    )

    private val MEDIUM_RISK = setOf(
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.USE_BIOMETRIC",
        "android.permission.USE_FINGERPRINT",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
    )

    // ── Category mapping ──────────────────────────────
    private fun permToCategory(perm: String): PermCategory = when {
        perm.contains("CAMERA")                                    -> PermCategory.CAMERA
        perm.contains("RECORD_AUDIO")                             -> PermCategory.MICROPHONE
        perm.contains("LOCATION")                                 -> PermCategory.LOCATION
        perm.contains("CONTACT")                                  -> PermCategory.CONTACTS
        perm.contains("SMS") || perm.contains("CALL") || perm.contains("PHONE")
                                                                   -> PermCategory.SMS
        perm.contains("STORAGE") || perm.contains("EXTERNAL")     -> PermCategory.STORAGE
        perm.contains("ACCESSIBILITY")                            -> PermCategory.ACCESSIBILITY
        perm.contains("DEVICE_ADMIN")                             -> PermCategory.DEVICE_ADMIN
        perm.contains("SYSTEM_ALERT_WINDOW") || perm.contains("OVERLAY")
                                                                   -> PermCategory.OVERLAY
        else                                                       -> PermCategory.OTHER
    }

    // ── System app detection ──────────────────────────
    /** Returns true for both pre-installed and updated system packages */
    fun isSystemApp(info: ApplicationInfo): Boolean =
        (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    // ── Scan all installed apps ───────────────────────
    fun getAppPermissions(context: Context): List<AppPermissionInfo> {
        val pm = context.packageManager
        val packages: List<PackageInfo> = getAllPackages(pm)

        return packages.mapNotNull { pkg ->
            val requestedPerms = pkg.requestedPermissions ?: return@mapNotNull null
            val dangerous = requestedPerms.filter { it in HIGH_RISK || it in MEDIUM_RISK }
            if (dangerous.isEmpty()) return@mapNotNull null

            val categories = dangerous.map { permToCategory(it) }.toSet()
            val isSystem   = isSystemApp(pkg.applicationInfo)

            // CRITICAL FIX: System apps legitimately hold sensitive permissions
            // (Settings, Camera, Contacts, Phone). We cap them at SAFE so they
            // don't pollute the HIGH-risk list meant for suspicious user apps.
            val riskLevel = when {
                isSystem -> RiskLevel.SAFE
                dangerous.any { it in HIGH_RISK }   -> RiskLevel.HIGH
                dangerous.any { it in MEDIUM_RISK } -> RiskLevel.MEDIUM
                else                                -> RiskLevel.LOW
            }

            AppPermissionInfo(
                packageName = pkg.packageName,
                appName     = try { pm.getApplicationLabel(pkg.applicationInfo).toString() }
                              catch (e: Exception) { pkg.packageName },
                icon        = try { pm.getApplicationIcon(pkg.packageName) } catch (_: Exception) { null },
                permissions = dangerous,
                categories  = categories,
                riskLevel   = riskLevel,
                isSystem    = isSystem
            )
        }
        // User apps first (non-system), then system; within each group sort by risk then name
        .sortedWith(compareBy({ it.isSystem }, { it.riskLevel.order }, { it.appName }))
    }

    // ── Detect threats (app-level) ────────────────────
    /**
     * Only flags apps whose behavior strongly suggests surveillance or abuse.
     * - Accessibility service active → can read all screen content and keystrokes.
     * - Device admin active → can wipe device or block uninstall.
     * - Accessibility + Overlay → classic keylogger / phishing overlay pattern.
     * - Accessibility + Camera/Mic → spy app pattern.
     * - SMS + Background Location → stalkerware pattern.
     *
     * CAM+MIC alone is NOT a threat — communication apps (WhatsApp, Telegram, etc.)
     * legitimately need both. We only flag if other suspicious elements are present.
     */
    fun getThreats(context: Context): List<ThreatEntry> {
        val threats = mutableListOf<ThreatEntry>()
        val pm = context.packageManager

        // ── 1. Accessibility services ──────────────────
        val accessEnabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        if (accessEnabled.isNotBlank()) {
            accessEnabled.split(":").forEach { comp ->
                val pkg = comp.split("/").firstOrNull()?.trim() ?: return@forEach
                if (pkg.isEmpty()) return@forEach
                val isSystem = try { isSystemApp(pm.getApplicationInfo(pkg, 0)) }
                               catch (_: Exception) { false }

                threats.add(ThreatEntry(
                    packageName = pkg,
                    appName     = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                                  catch (_: Exception) { pkg },
                    icon        = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null },
                    threatType  = ThreatType.ACCESSIBILITY_SERVICE,
                    detail      = comp,
                    reason      = "Tiene acceso total a tu pantalla. Puede leer contraseñas, " +
                                  "capturar lo que escribes y controlar otras aplicaciones.",
                    riskLevel   = if (isSystem) RiskLevel.LOW else RiskLevel.HIGH,
                    isSystem    = isSystem
                ))
            }
        }

        // ── 2. Device admins ───────────────────────────
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admins: List<ComponentName> = try { dpm.activeAdmins ?: emptyList() }
                                          catch (_: Exception) { emptyList() }
        admins.forEach { cn ->
            val pkg = cn.packageName
            val isSystem = try { isSystemApp(pm.getApplicationInfo(pkg, 0)) }
                           catch (_: Exception) { false }

            threats.add(ThreatEntry(
                packageName = pkg,
                appName     = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                              catch (_: Exception) { pkg },
                icon        = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null },
                threatType  = ThreatType.DEVICE_ADMIN,
                detail      = cn.className,
                reason      = "Puede bloquear el dispositivo, borrar todos los datos o " +
                              "impedir su propia desinstalación.",
                riskLevel   = if (isSystem) RiskLevel.LOW else RiskLevel.MEDIUM,
                isSystem    = isSystem
            ))
        }

        // ── 3. Suspicious permission combos ───────────
        // Only user-installed apps (isSystem = false) are checked.
        val allApps = getAppPermissions(context)
        allApps.filter { !it.isSystem }.forEach { app ->
            val cats  = app.categories
            val perms = app.permissions

            val hasAcc     = cats.contains(PermCategory.ACCESSIBILITY)
            val hasOverlay = cats.contains(PermCategory.OVERLAY)
            val hasCam     = cats.contains(PermCategory.CAMERA)
            val hasMic     = cats.contains(PermCategory.MICROPHONE)
            val hasSms     = cats.contains(PermCategory.SMS)
            val hasLoc     = cats.contains(PermCategory.LOCATION)
            val hasBgLoc   = perms.contains("android.permission.ACCESS_BACKGROUND_LOCATION")

            // Overlay + Accessibility → phishing / keylogger pattern
            if (hasAcc && hasOverlay) {
                threats.add(ThreatEntry(
                    packageName = app.packageName, appName = app.appName, icon = app.icon,
                    threatType  = ThreatType.DANGEROUS_COMBO,
                    detail      = "ACC+OVL",
                    reason      = "Combinación de alto riesgo: puede superponer pantallas falsas " +
                                  "mientras lee tus contraseñas en tiempo real (patrón keylogger).",
                    riskLevel   = RiskLevel.HIGH, isSystem = false
                ))
            }

            // Accessibility + Camera or Mic → spy app pattern
            if (hasAcc && (hasCam || hasMic) && !hasOverlay) {
                threats.add(ThreatEntry(
                    packageName = app.packageName, appName = app.appName, icon = app.icon,
                    threatType  = ThreatType.DANGEROUS_COMBO,
                    detail      = "ACC+CAM/MIC",
                    reason      = "Una app de accesibilidad con acceso a cámara o micrófono " +
                                  "puede grabar sin que la pantalla lo indique.",
                    riskLevel   = RiskLevel.HIGH, isSystem = false
                ))
            }

            // SMS + precise Background Location → stalkerware
            if (hasSms && hasLoc && hasBgLoc) {
                threats.add(ThreatEntry(
                    packageName = app.packageName, appName = app.appName, icon = app.icon,
                    threatType  = ThreatType.DANGEROUS_COMBO,
                    detail      = "SMS+BG_LOC",
                    reason      = "Puede leer tus mensajes y rastrear tu ubicación exacta en " +
                                  "segundo plano sin que lo notes (patrón espía).",
                    riskLevel   = RiskLevel.HIGH, isSystem = false
                ))
            }

            // Overlay alone (non-system) → phishing risk
            if (hasOverlay && !hasAcc && !app.isSystem) {
                threats.add(ThreatEntry(
                    packageName = app.packageName, appName = app.appName, icon = app.icon,
                    threatType  = ThreatType.OVERLAY_ACTIVE,
                    detail      = "OVERLAY",
                    reason      = "Puede mostrar pantallas falsas sobre otras aplicaciones, " +
                                  "incluyendo pantallas de pago o inicio de sesión.",
                    riskLevel   = RiskLevel.MEDIUM, isSystem = false
                ))
            }
        }

        return threats
            .distinctBy { "${it.packageName}-${it.threatType}-${it.detail}" }
            .sortedWith(compareBy({ it.riskLevel.order }, { it.isSystem }, { it.appName }))
    }

    // ── Apps with camera/mic permission GRANTED ───────
    /** For Guard tab: shows user-installed apps with camera/mic actually granted at runtime.
     *  Dual check: app must DECLARE the permission in manifest AND AppOps must be ALLOWED.
     *  This eliminates false positives from FOREGROUND_SERVICE_CAMERA type declarations. */
    fun getGrantedCamMicApps(context: Context): List<SensorAccessEntry> {
        val pm     = context.packageManager
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        fun isOpGranted(op: String, uid: Int, pkgName: String): Boolean = try {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(op, uid, pkgName) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }

        return getAllPackages(pm).mapNotNull { pkg ->
            val appInfo  = pkg.applicationInfo ?: return@mapNotNull null
            if (isSystemApp(appInfo)) return@mapNotNull null

            // Must declare the actual runtime permission in manifest
            val requested = pkg.requestedPermissions ?: return@mapNotNull null
            val declaresCam = "android.permission.CAMERA"       in requested
            val declaresMic = "android.permission.RECORD_AUDIO" in requested

            // AND AppOps must reflect user actually granted it
            val hasCam = declaresCam && isOpGranted(AppOpsManager.OPSTR_CAMERA,      appInfo.uid, pkg.packageName)
            val hasMic = declaresMic && isOpGranted(AppOpsManager.OPSTR_RECORD_AUDIO, appInfo.uid, pkg.packageName)
            if (!hasCam && !hasMic) return@mapNotNull null

            SensorAccessEntry(
                packageName = pkg.packageName,
                appName     = try { pm.getApplicationLabel(appInfo).toString() }
                              catch (_: Exception) { pkg.packageName },
                icon        = try { pm.getApplicationIcon(pkg.packageName) } catch (_: Exception) { null },
                accessType  = when {
                    hasCam && hasMic -> SensorType.BOTH
                    hasCam           -> SensorType.CAMERA
                    else             -> SensorType.MICROPHONE
                },
                lastAccess  = 0L,
                accessCount = 0
            )
        }.sortedBy { it.appName }
    }

    // ── Security score ────────────────────────────────
    fun computeScore(
        apps    : List<AppPermissionInfo>,
        threats : List<ThreatEntry>
    ): SecurityScore {
        val highRisk     = apps.count { it.riskLevel == RiskLevel.HIGH }
        val nonSystemTotal = apps.count { !it.isSystem }
        var score = 100
        score -= minOf(40, highRisk * 5)
        score -= minOf(40, threats.count { !it.isSystem } * 10)
        score  = score.coerceIn(0, 100)
        return SecurityScore(
            score          = score,
            highRiskApps   = highRisk,
            activeThreats  = threats.count { !it.isSystem },
            totalApps      = nonSystemTotal,
            sensorAccesses = 0
        )
    }

    // ── Package helper ────────────────────────────────
    @Suppress("DEPRECATION")
    private fun getAllPackages(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                PackageManager.GET_PERMISSIONS.toLong()))
        else
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
}
