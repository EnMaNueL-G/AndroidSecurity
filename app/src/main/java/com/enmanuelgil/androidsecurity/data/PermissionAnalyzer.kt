package com.enmanuelgil.androidsecurity.data

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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

    // ── Reflection helper for hidden getPackagesForOps ─
    @Suppress("UNCHECKED_CAST")
    private fun getPackagesForOpsReflect(appOps: AppOpsManager, op: String): List<Any> {
        return try {
            val method = appOps.javaClass.getMethod("getPackagesForOps", Array<String>::class.java)
            (method.invoke(appOps, arrayOf(op)) as? List<*>)?.filterNotNull() ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun getPackageName(pkgOp: Any): String? = try {
        pkgOp.javaClass.getMethod("getPackageName").invoke(pkgOp) as? String
    } catch (e: Exception) { null }

    private fun getOps(pkgOp: Any): List<Any> = try {
        @Suppress("UNCHECKED_CAST")
        (pkgOp.javaClass.getMethod("getOps").invoke(pkgOp) as? List<*>)?.filterNotNull() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    // Android 10 (API 29)+ replaced getTime() with getLastAccessTime(int flags).
    // OP_FLAGS_ALL_TRUSTED = 0x0b, OP_FLAGS_ALL = 0x1f — pass ALL to get any recorded access.
    private fun getLastAccessTime(opEntry: Any): Long = try {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                opEntry.javaClass
                    .getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
                    .invoke(opEntry, 0x1f) as? Long ?: 0L
            } catch (_: Exception) {
                opEntry.javaClass.getMethod("getLastAccessTime").invoke(opEntry) as? Long ?: 0L
            }
        } else {
            opEntry.javaClass.getMethod("getTime").invoke(opEntry) as? Long ?: 0L
        }
    } catch (e: Exception) { 0L }

    private fun getLastDuration(opEntry: Any): Long = try {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                opEntry.javaClass
                    .getMethod("getLastDuration", Int::class.javaPrimitiveType)
                    .invoke(opEntry, 0x1f) as? Long ?: 0L
            } catch (_: Exception) {
                opEntry.javaClass.getMethod("getLastDuration").invoke(opEntry) as? Long ?: 0L
            }
        } else 0L
    } catch (e: Exception) { 0L }

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
            val grantedDangerous = requestedPerms.filter { perm ->
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
        }.sortedWith(compareBy({ it.riskLevel.order }, { it.isSystem }, { it.appName }))
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
    // Uses UsageStatsManager (requires PACKAGE_USAGE_STATS) — Android 12+ blocked the
    // hidden getPackagesForOps API, so UsageStats is the only reliable public approach.
    fun getCamMicAccesses(context: Context): List<SensorAccessEntry> {
        val pm  = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val windowMs = 30L * 24 * 60 * 60 * 1000L

        val statsMap = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - windowMs, now)
                ?.filter { it.lastTimeUsed > 0L }
                ?.groupBy { it.packageName }
                ?.mapValues { (_, list) -> list.maxByOrNull { it.lastTimeUsed }!! }
        } catch (e: Exception) { null } ?: return emptyList()

        return statsMap.values.mapNotNull { stat ->
            val pkg    = stat.packageName
            val hasCam = pm.checkPermission("android.permission.CAMERA", pkg) ==
                         PackageManager.PERMISSION_GRANTED
            val hasMic = pm.checkPermission("android.permission.RECORD_AUDIO", pkg) ==
                         PackageManager.PERMISSION_GRANTED
            if (!hasCam && !hasMic) return@mapNotNull null

            SensorAccessEntry(
                packageName = pkg,
                appName     = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                              catch (e: Exception) { pkg },
                icon        = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null },
                accessType  = when {
                    hasCam && hasMic -> SensorType.BOTH
                    hasCam           -> SensorType.CAMERA
                    else             -> SensorType.MICROPHONE
                },
                lastAccess  = stat.lastTimeUsed,
                accessCount = 0
            )
        }.sortedByDescending { it.lastAccess }
    }

    // ── Full access history ────────────────────────────
    fun getAccessHistory(context: Context, windowMs: Long = 24L * 60 * 60 * 1000L): List<AccessHistoryEntry> {
        val pm  = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        val statsMap = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - windowMs, now)
                ?.filter { it.lastTimeUsed > 0L }
                ?.groupBy { it.packageName }
                ?.mapValues { (_, list) -> list.maxByOrNull { it.lastTimeUsed }!! }
        } catch (e: Exception) { null } ?: return emptyList()

        // Permissions to track → category mapping
        val sensitivePerms = listOf(
            "android.permission.CAMERA"                 to PermCategory.CAMERA,
            "android.permission.RECORD_AUDIO"           to PermCategory.MICROPHONE,
            "android.permission.ACCESS_FINE_LOCATION"   to PermCategory.LOCATION,
            "android.permission.ACCESS_COARSE_LOCATION" to PermCategory.LOCATION,
            "android.permission.READ_CONTACTS"          to PermCategory.CONTACTS,
            "android.permission.READ_SMS"               to PermCategory.SMS,
        )

        val results = mutableListOf<AccessHistoryEntry>()
        val seenCats = mutableSetOf<String>() // deduplicate pkg+category

        for ((pkg, stat) in statsMap) {
            val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                          catch (e: Exception) { pkg }
            val icon    = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }

            for ((perm, category) in sensitivePerms) {
                val key = "$pkg:${category.name}"
                if (key in seenCats) continue          // already added this pkg+cat
                if (pm.checkPermission(perm, pkg) != PackageManager.PERMISSION_GRANTED) continue

                seenCats.add(key)
                results.add(AccessHistoryEntry(
                    packageName = pkg,
                    appName     = appName,
                    icon        = icon,
                    permission  = perm,
                    category    = category,
                    timestamp   = stat.lastTimeUsed,
                    durationMs  = stat.totalTimeInForeground
                ))
            }
        }

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

        var score = 100
        score -= minOf(40, highRisk * 5)
        score -= minOf(30, threats.count { !it.isSystem } * 10)
        score -= minOf(20, accesses.size * 2)
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
