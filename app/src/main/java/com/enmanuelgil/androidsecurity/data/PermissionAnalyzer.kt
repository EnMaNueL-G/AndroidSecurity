package com.enmanuelgil.androidsecurity.data

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import com.enmanuelgil.androidsecurity.model.*

object PermissionAnalyzer {

    // ── Permissions classified by category and risk ────
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

    private fun permToCategory(perm: String): PermCategory = when {
        perm.contains("CAMERA")                    -> PermCategory.CAMERA
        perm.contains("RECORD_AUDIO")              -> PermCategory.MICROPHONE
        perm.contains("LOCATION")                  -> PermCategory.LOCATION
        perm.contains("CONTACT")                   -> PermCategory.CONTACTS
        perm.contains("SMS") || perm.contains("CALL") || perm.contains("PHONE")
                                                   -> PermCategory.SMS
        perm.contains("STORAGE") || perm.contains("EXTERNAL")
                                                   -> PermCategory.STORAGE
        perm.contains("ACCESSIBILITY")             -> PermCategory.ACCESSIBILITY
        perm.contains("DEVICE_ADMIN")              -> PermCategory.DEVICE_ADMIN
        perm.contains("SYSTEM_ALERT_WINDOW") || perm.contains("OVERLAY")
                                                   -> PermCategory.OVERLAY
        else                                       -> PermCategory.OTHER
    }

    // ── Scan all installed apps ────────────────────────
    fun getAppPermissions(context: Context): List<AppPermissionInfo> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 33)
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        else
            @Suppress("DEPRECATION") PackageManager.GET_PERMISSIONS

        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(flags as PackageManager.PackageInfoFlags)
        else
            @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        return packages.mapNotNull { pkg ->
            val requestedPerms = pkg.requestedPermissions ?: return@mapNotNull null
            val grantedDangerous = requestedPerms.filterIndexed { i, perm ->
                // Only show permissions that are granted and are dangerous/signature
                perm in HIGH_RISK || perm in MEDIUM_RISK
            }
            if (grantedDangerous.isEmpty()) return@mapNotNull null

            val categories = grantedDangerous.map { permToCategory(it) }.toSet()
            val riskLevel = when {
                grantedDangerous.any { it in HIGH_RISK }   -> RiskLevel.HIGH
                grantedDangerous.any { it in MEDIUM_RISK } -> RiskLevel.MEDIUM
                else                                       -> RiskLevel.LOW
            }
            val isSystem = (pkg.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

            AppPermissionInfo(
                packageName = pkg.packageName,
                appName     = try { pm.getApplicationLabel(pkg.applicationInfo).toString() }
                              catch (e: Exception) { pkg.packageName },
                icon        = try { pm.getApplicationIcon(pkg.packageName) } catch (e: Exception) { null },
                permissions = grantedDangerous,
                categories  = categories,
                riskLevel   = riskLevel,
                isSystem    = isSystem
            )
        }.sortedWith(compareBy({ it.riskLevel.order }, { it.appName }))
    }

    // ── Detect threats ─────────────────────────────────
    fun getThreats(context: Context): List<ThreatEntry> {
        val threats = mutableListOf<ThreatEntry>()
        val pm = context.packageManager

        // 1. Accessibility services
        val accessibilityEnabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        if (accessibilityEnabled.isNotBlank()) {
            accessibilityEnabled.split(":").forEach { comp ->
                val pkg = comp.split("/").firstOrNull()?.trim() ?: return@forEach
                if (pkg.isEmpty()) return@forEach
                val isSystem = try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                } catch (e: Exception) { false }

                threats.add(ThreatEntry(
                    packageName = pkg,
                    appName     = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                                  catch (e: Exception) { pkg },
                    icon        = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null },
                    threatType  = ThreatType.ACCESSIBILITY_SERVICE,
                    detail      = comp,
                    riskLevel   = if (isSystem) RiskLevel.LOW else RiskLevel.HIGH,
                    isSystem    = isSystem
                ))
            }
        }

        // 2. Device Admins
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admins: List<ComponentName> = try { dpm.activeAdmins ?: emptyList() }
                                          catch (e: Exception) { emptyList() }
        admins.forEach { cn ->
            val pkg = cn.packageName
            val isSystem = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) { false }

            threats.add(ThreatEntry(
                packageName = pkg,
                appName     = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                              catch (e: Exception) { pkg },
                icon        = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null },
                threatType  = ThreatType.DEVICE_ADMIN,
                detail      = cn.className,
                riskLevel   = if (isSystem) RiskLevel.LOW else RiskLevel.MEDIUM,
                isSystem    = isSystem
            ))
        }

        // 3. Dangerous permission combos
        val allApps = getAppPermissions(context)
        allApps.forEach { app ->
            val cats = app.categories
            val combos = mutableListOf<String>()

            if (cats.contains(PermCategory.CAMERA) && cats.contains(PermCategory.MICROPHONE))
                combos.add("CAM_MIC")
            if (cats.contains(PermCategory.SMS) && cats.contains(PermCategory.LOCATION))
                combos.add("SMS_LOC")
            if (cats.contains(PermCategory.ACCESSIBILITY) && cats.contains(PermCategory.OVERLAY))
                combos.add("ACC_OVL")

            if (combos.isNotEmpty() && !app.isSystem) {
                threats.add(ThreatEntry(
                    packageName = app.packageName,
                    appName     = app.appName,
                    icon        = app.icon,
                    threatType  = ThreatType.DANGEROUS_COMBO,
                    detail      = combos.joinToString(", "),
                    riskLevel   = RiskLevel.HIGH,
                    isSystem    = false
                ))
            }
        }

        return threats.sortedWith(compareBy({ it.riskLevel.order }, { it.isSystem }, { it.appName }))
    }

    // ── Camera / Microphone access history ─────────────
    fun getCamMicAccesses(context: Context): List<SensorAccessEntry> {
        val results = mutableListOf<SensorAccessEntry>()
        val pm = context.packageManager

        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val camOp  = AppOpsManager.OPSTR_CAMERA
            val micOp  = AppOpsManager.OPSTR_RECORD_AUDIO
            val now    = System.currentTimeMillis()
            val dayMs  = 24 * 60 * 60 * 1000L

            // Map: packageName → (camTime, micTime, camCount, micCount)
            val map = mutableMapOf<String, LongArray>() // [camLast, micLast, camCnt, micCnt]

            listOf(camOp to 0, micOp to 1).forEach { (op, idx) ->
                @Suppress("DEPRECATION")
                val pkgOps: List<AppOpsManager.PackageOps> = try {
                    appOps.getPackagesForOps(arrayOf(op))
                } catch (e: Exception) { emptyList() }

                pkgOps.forEach { pkgOp ->
                    val pkg = pkgOp.packageName
                    pkgOp.ops.forEach { opEntry ->
                        val last = if (Build.VERSION.SDK_INT >= 29) opEntry.lastAccessTime
                                   else @Suppress("DEPRECATION") opEntry.time
                        if (last > 0 && (now - last) < dayMs * 30) {
                            val entry = map.getOrPut(pkg) { LongArray(4) }
                            if (idx == 0) {
                                entry[0] = maxOf(entry[0], last)
                                entry[2]++
                            } else {
                                entry[1] = maxOf(entry[1], last)
                                entry[3]++
                            }
                        }
                    }
                }
            }

            map.forEach { (pkg, data) ->
                val hasCam = data[0] > 0
                val hasMic = data[1] > 0
                if (!hasCam && !hasMic) return@forEach

                val sensorType = when {
                    hasCam && hasMic -> SensorType.BOTH
                    hasCam           -> SensorType.CAMERA
                    else             -> SensorType.MICROPHONE
                }
                val lastAccess = maxOf(data[0], data[1])
                val countToday = (data[2] + data[3]).toInt()

                results.add(SensorAccessEntry(
                    packageName = pkg,
                    appName     = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                                  catch (e: Exception) { pkg },
                    icon        = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null },
                    accessType  = sensorType,
                    lastAccess  = lastAccess,
                    accessCount = countToday
                ))
            }
        } catch (e: Exception) {
            // PACKAGE_USAGE_STATS not granted — return empty, UI shows prompt
        }

        return results.sortedByDescending { it.lastAccess }
    }

    // ── Full access history ────────────────────────────
    fun getAccessHistory(context: Context, windowMs: Long = 24 * 60 * 60 * 1000L): List<AccessHistoryEntry> {
        val results = mutableListOf<AccessHistoryEntry>()
        val pm = context.packageManager

        val sensitiveOps = mapOf(
            AppOpsManager.OPSTR_CAMERA        to PermCategory.CAMERA,
            AppOpsManager.OPSTR_RECORD_AUDIO  to PermCategory.MICROPHONE,
            AppOpsManager.OPSTR_GET_ACCOUNTS  to PermCategory.CONTACTS,
            AppOpsManager.OPSTR_READ_CONTACTS to PermCategory.CONTACTS,
            AppOpsManager.OPSTR_READ_SMS      to PermCategory.SMS,
            AppOpsManager.OPSTR_SEND_SMS      to PermCategory.SMS,
            AppOpsManager.OPSTR_FINE_LOCATION to PermCategory.LOCATION,
            AppOpsManager.OPSTR_COARSE_LOCATION to PermCategory.LOCATION,
        )

        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val now    = System.currentTimeMillis()

            sensitiveOps.forEach { (op, category) ->
                @Suppress("DEPRECATION")
                val pkgOps = try { appOps.getPackagesForOps(arrayOf(op)) }
                             catch (e: Exception) { emptyList<AppOpsManager.PackageOps>() }

                pkgOps.forEach { pkgOp ->
                    pkgOp.ops.forEach { opEntry ->
                        val last = if (Build.VERSION.SDK_INT >= 29) opEntry.lastAccessTime
                                   else @Suppress("DEPRECATION") opEntry.time
                        if (last > 0 && (now - last) <= windowMs) {
                            val dur = if (Build.VERSION.SDK_INT >= 29) opEntry.lastDuration else 0L
                            results.add(AccessHistoryEntry(
                                packageName = pkgOp.packageName,
                                appName     = try { pm.getApplicationLabel(pm.getApplicationInfo(pkgOp.packageName, 0)).toString() }
                                              catch (e: Exception) { pkgOp.packageName },
                                icon        = try { pm.getApplicationIcon(pkgOp.packageName) } catch (e: Exception) { null },
                                permission  = op,
                                category    = category,
                                timestamp   = last,
                                durationMs  = dur
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) { /* PACKAGE_USAGE_STATS not granted */ }

        return results.sortedByDescending { it.timestamp }
    }

    // ── Security score ─────────────────────────────────
    fun computeScore(
        apps    : List<AppPermissionInfo>,
        threats : List<ThreatEntry>,
        accesses: List<SensorAccessEntry>
    ): SecurityScore {
        val highRisk = apps.count { it.riskLevel == RiskLevel.HIGH && !it.isSystem }
        val totalNonSystem = apps.count { !it.isSystem }

        // Start at 100, deduct points
        var score = 100
        score -= minOf(40, highRisk * 5)                          // up to -40 for high-risk apps
        score -= minOf(30, threats.count { !it.isSystem } * 10)   // up to -30 for threats
        score -= minOf(20, accesses.size * 2)                     // up to -20 for sensor accesses
        score  = score.coerceIn(0, 100)

        return SecurityScore(
            score          = score,
            highRiskApps   = highRisk,
            activeThreats  = threats.count { !it.isSystem },
            totalApps      = totalNonSystem,
            sensorAccesses = accesses.size
        )
    }
}
