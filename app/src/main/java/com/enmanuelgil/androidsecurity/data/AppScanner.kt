package com.enmanuelgil.androidsecurity.data

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Permisos sensibles, agrupados como los muestra Android en Ajustes. */
enum class Perm(val label: String) {
    CAMERA("Cámara"),
    MIC("Micrófono"),
    LOCATION("Ubicación"),
    BG_LOCATION("Ubicación siempre"),
    CONTACTS("Contactos"),
    SMS("SMS"),
    CALLS("Llamadas"),
    FILES("Fotos y archivos"),
    CALENDAR("Calendario"),
    BODY("Sensores corporales");

    companion object {
        private val MAP = mapOf(
            "android.permission.CAMERA" to CAMERA,
            "android.permission.RECORD_AUDIO" to MIC,
            "android.permission.ACCESS_FINE_LOCATION" to LOCATION,
            "android.permission.ACCESS_COARSE_LOCATION" to LOCATION,
            "android.permission.ACCESS_BACKGROUND_LOCATION" to BG_LOCATION,
            "android.permission.READ_CONTACTS" to CONTACTS,
            "android.permission.WRITE_CONTACTS" to CONTACTS,
            "android.permission.READ_SMS" to SMS,
            "android.permission.SEND_SMS" to SMS,
            "android.permission.RECEIVE_SMS" to SMS,
            "android.permission.RECEIVE_MMS" to SMS,
            "android.permission.READ_CALL_LOG" to CALLS,
            "android.permission.WRITE_CALL_LOG" to CALLS,
            "android.permission.PROCESS_OUTGOING_CALLS" to CALLS,
            "android.permission.CALL_PHONE" to CALLS,
            "android.permission.ANSWER_PHONE_CALLS" to CALLS,
            "android.permission.READ_EXTERNAL_STORAGE" to FILES,
            "android.permission.READ_MEDIA_IMAGES" to FILES,
            "android.permission.READ_MEDIA_VIDEO" to FILES,
            "android.permission.READ_MEDIA_AUDIO" to FILES,
            "android.permission.READ_CALENDAR" to CALENDAR,
            "android.permission.WRITE_CALENDAR" to CALENDAR,
            "android.permission.BODY_SENSORS" to BODY,
        )
        fun of(permission: String): Perm? = MAP[permission]
    }
}

/** Accesos especiales: no son permisos normales, se conceden en pantallas aparte de Ajustes. */
enum class Special(val label: String, val what: String, val settingsAction: String) {
    ACCESSIBILITY("Accesibilidad",
        "Puede leer todo lo que aparece en pantalla (también lo que escribes) y pulsar por ti.",
        Settings.ACTION_ACCESSIBILITY_SETTINGS),
    NOTIFICATIONS("Lee tus notificaciones",
        "Ve todas tus notificaciones: mensajes, códigos de verificación y avisos del banco.",
        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    DEVICE_ADMIN("Administrador del dispositivo",
        "Puede bloquear el móvil, borrarlo o impedir que la desinstales sin quitarle antes este permiso.",
        Settings.ACTION_SECURITY_SETTINGS),
    OVERLAY("Mostrar sobre otras apps",
        "Puede dibujar ventanas encima de otras apps (burbujas de chat… o pantallas falsas).",
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    INSTALL_APPS("Instalar apps desconocidas",
        "Puede instalar otras apps (Android te pide confirmación cada vez).",
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
    USAGE("Acceso a datos de uso",
        "Sabe qué apps usas y cuánto tiempo.",
        Settings.ACTION_USAGE_ACCESS_SETTINGS),
    ALL_FILES("Acceso a todos los archivos",
        "Puede leer y borrar cualquier archivo de tu almacenamiento.",
        if (Build.VERSION.SDK_INT >= 30) Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION else Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    BATTERY("Sin restricciones de batería",
        "Puede seguir funcionando en segundo plano sin límites de ahorro de energía.",
        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
}

enum class Risk(val order: Int, val label: String) {
    HIGH(0, "Revisar"), MEDIUM(1, "Atención"), LOW(2, "Normal"), NONE(3, "Sin permisos sensibles")
}

data class AppEntry(
    val pkg        : String,
    val label      : String,
    val isSystem   : Boolean,
    val installer  : String?,     // paquete del instalador (null = desconocido)
    val fromStore  : Boolean,     // instalada desde una tienda conocida
    val granted    : Set<Perm>,   // permisos CONCEDIDOS de verdad
    val requested  : Set<Perm>,   // permisos que pide (concedidos o no)
    val special    : Set<Special>,
    val installedAt: Long,
    val lastUsed   : Long?,       // null = sin acceso a datos de uso; 0 = sin uso registrado
    val risk       : Risk,
    val reasons    : List<String>,
) {
    val sensitiveGranted: Set<Perm> get() = granted intersect SENSITIVE

    companion object {
        /** Los que más exponen tu privacidad (para «apps sin usar que conservan permisos»). */
        val SENSITIVE = setOf(Perm.CAMERA, Perm.MIC, Perm.LOCATION, Perm.BG_LOCATION,
            Perm.CONTACTS, Perm.SMS, Perm.CALLS)
    }
}

object AppScanner {

    const val DAY = 24L * 60 * 60 * 1000
    /** Días sin abrir una app para considerarla «sin usar» (Android usa ~90 para quitar permisos). */
    const val UNUSED_DAYS = 90L

    /** Tiendas conocidas. Una app instalada con el instalador de paquetes (APK) o sin instalador
     *  conocido NO es peligrosa por eso, pero sí es la vía habitual de las apps espía. */
    private val STORES = mapOf(
        "com.android.vending" to "Google Play",
        "com.google.android.feedback" to "Google Play",
        "com.sec.android.app.samsungapps" to "Galaxy Store",
        "com.huawei.appmarket" to "AppGallery",
        "com.xiaomi.market" to "GetApps (Xiaomi)",
        "com.xiaomi.mipicks" to "GetApps (Xiaomi)",
        "com.oppo.market" to "App Market (OPPO)",
        "com.heytap.market" to "App Market (OPPO)",
        "com.bbk.appstore" to "V-Appstore (vivo)",
        "com.vivo.appstore" to "V-Appstore (vivo)",
        "com.amazon.venezia" to "Amazon Appstore",
        "org.fdroid.fdroid" to "F-Droid",
        "com.aurora.store" to "Aurora Store",
        "com.motorola.ccc.ota" to "Motorola",
    )
    private val MANUAL = setOf(
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
    )

    fun installerLabel(pkg: String?): String = when {
        pkg == null -> "Instalador desconocido"
        pkg in STORES -> STORES.getValue(pkg)
        pkg in MANUAL -> "Instalada a mano (archivo APK)"
        else -> "Instalada por otra app ($pkg)"
    }

    fun isSystemApp(info: ApplicationInfo): Boolean =
        (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = opMode(ops, AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT)
            context.checkCallingOrSelfPermission("android.permission.PACKAGE_USAGE_STATS") == PackageManager.PERMISSION_GRANTED
        else mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    private fun opMode(ops: AppOpsManager, op: String, uid: Int, pkg: String): Int = try {
        if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(op, uid, pkg) else ops.checkOpNoThrow(op, uid, pkg)
    } catch (_: Exception) { AppOpsManager.MODE_ERRORED }

    /** Permiso especial concedido: el modo de AppOps manda; «por defecto» = lo que diga el permiso. */
    private fun opAllowed(ops: AppOpsManager, pm: PackageManager, op: String, perm: String, uid: Int, pkg: String): Boolean {
        val mode = opMode(ops, op, uid, pkg)
        return mode == AppOpsManager.MODE_ALLOWED ||
            (mode == AppOpsManager.MODE_DEFAULT && pm.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED)
    }

    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    private fun installedPackages(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

    /** Paquetes de la cadena Settings.Secure con componentes separados por «:» (accesibilidad…). */
    private fun packagesIn(setting: String?): Set<String> =
        (setting ?: "").split(':').mapNotNull { c ->
            c.substringBefore('/').trim().takeIf { it.isNotEmpty() }
        }.toSet()

    /** Última vez que se usó cada app (hasta ~1 año; Android guarda menos en algunos móviles). */
    private fun lastUsedMap(context: Context): Map<String, Long>? {
        if (!hasUsageAccess(context)) return null
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            usm.queryAndAggregateUsageStats(now - 366 * DAY, now)
                .mapValues { (_, s) ->
                    var t = s.lastTimeUsed
                    if (Build.VERSION.SDK_INT >= 29) t = maxOf(t, s.lastTimeVisible, s.lastTimeForegroundServiceUsed)
                    t
                }
        } catch (_: Exception) { null }
    }

    fun scan(context: Context): List<AppEntry> {
        val pm  = context.packageManager
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val cr  = context.contentResolver

        val accessibility = packagesIn(Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        val listeners = try { NotificationManagerCompat.getEnabledListenerPackages(context) } catch (_: Exception) { emptySet() }
        val admins = try {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
                .activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (_: Exception) { emptySet() }
        val lastUsed = lastUsedMap(context)
        val now = System.currentTimeMillis()

        return installedPackages(pm).mapNotNull { pkg ->
            val info = pkg.applicationInfo ?: return@mapNotNull null
            if (pkg.packageName == context.packageName) return@mapNotNull null
            val names = pkg.requestedPermissions ?: emptyArray()
            val flags = pkg.requestedPermissionsFlags ?: IntArray(0)

            val requested = mutableSetOf<Perm>()
            val granted = mutableSetOf<Perm>()
            names.forEachIndexed { i, name ->
                val p = Perm.of(name) ?: return@forEachIndexed
                requested += p
                if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) granted += p
            }
            // Android 13+: los permisos antiguos de almacenamiento ya no dan acceso a nada
            if (Build.VERSION.SDK_INT >= 33 && Perm.FILES in granted &&
                names.none { it.startsWith("android.permission.READ_MEDIA_") }) {
                if (info.targetSdkVersion >= 33) granted -= Perm.FILES
            }

            val uid = info.uid
            val special = mutableSetOf<Special>()
            if (pkg.packageName in accessibility) special += Special.ACCESSIBILITY
            if (pkg.packageName in listeners) special += Special.NOTIFICATIONS
            if (pkg.packageName in admins) special += Special.DEVICE_ADMIN
            if ("android.permission.SYSTEM_ALERT_WINDOW" in names &&
                opAllowed(ops, pm, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, "android.permission.SYSTEM_ALERT_WINDOW", uid, pkg.packageName))
                special += Special.OVERLAY
            if ("android.permission.REQUEST_INSTALL_PACKAGES" in names &&
                opAllowed(ops, pm, "android:request_install_packages", "android.permission.REQUEST_INSTALL_PACKAGES", uid, pkg.packageName))
                special += Special.INSTALL_APPS
            if ("android.permission.PACKAGE_USAGE_STATS" in names &&
                opAllowed(ops, pm, AppOpsManager.OPSTR_GET_USAGE_STATS, "android.permission.PACKAGE_USAGE_STATS", uid, pkg.packageName))
                special += Special.USAGE
            if (Build.VERSION.SDK_INT >= 30 && "android.permission.MANAGE_EXTERNAL_STORAGE" in names &&
                opAllowed(ops, pm, "android:manage_external_storage", "android.permission.MANAGE_EXTERNAL_STORAGE", uid, pkg.packageName))
                special += Special.ALL_FILES
            val isSystem = isSystemApp(info)
            if (!isSystem && try { power.isIgnoringBatteryOptimizations(pkg.packageName) } catch (_: Exception) { false })
                special += Special.BATTERY


            val installer = installerOf(pm, pkg.packageName)
            val fromStore = installer in STORES
            val used = lastUsed?.let { it[pkg.packageName] ?: 0L }
            val (risk, reasons) = assess(isSystem, fromStore, granted, special, pkg.firstInstallTime, used, now)

            AppEntry(
                pkg = pkg.packageName,
                label = try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { pkg.packageName },
                isSystem = isSystem, installer = installer, fromStore = fromStore,
                granted = granted, requested = requested, special = special,
                installedAt = pkg.firstInstallTime, lastUsed = used,
                risk = risk, reasons = reasons,
            )
        }.sortedWith(compareBy({ it.isSystem }, { it.risk.order }, { it.label.lowercase() }))
    }

    /**
     * Nivel de atención. No es un antivirus: señala combinaciones que usan las apps espía
     * (accesibilidad, notificaciones, administrador…) sobre todo si la app no viene de una tienda.
     * Las apps del sistema no se puntúan (vienen con el móvil y suelen necesitar esos accesos).
     */
    fun assess(
        isSystem: Boolean, fromStore: Boolean, granted: Set<Perm>, special: Set<Special>,
        installedAt: Long, lastUsed: Long?, now: Long,
    ): Pair<Risk, List<String>> {
        val sensitive = granted intersect AppEntry.SENSITIVE
        if (isSystem) return (if (sensitive.isEmpty() && special.isEmpty()) Risk.NONE else Risk.LOW) to emptyList()

        val high = mutableListOf<String>()
        val medium = mutableListOf<String>()
        val acc = Special.ACCESSIBILITY in special
        val notif = Special.NOTIFICATIONS in special
        val admin = Special.DEVICE_ADMIN in special

        if (acc && !fromStore) high += "Tiene Accesibilidad activa y no viene de una tienda: es la forma habitual de espiar lo que haces."
        else if (acc) medium += "Tiene Accesibilidad activa: puede leer tu pantalla. Comprueba que la reconoces."
        if (acc && (notif || admin)) high += "Combina Accesibilidad con " + (if (notif) "lectura de notificaciones" else "administrador del dispositivo") + "."
        if (admin && !fromStore) high += "Es administradora del dispositivo y no viene de una tienda."
        else if (admin) medium += "Es administradora del dispositivo (puede bloquear o borrar el móvil)."
        if (notif && !fromStore && !acc) medium += "Lee tus notificaciones y no viene de una tienda."
        if (Perm.SMS in granted && Perm.BG_LOCATION in granted)
            (if (fromStore) medium else high).add("Lee tus SMS y sabe tu ubicación siempre, también en segundo plano.")
        if (Special.OVERLAY in special && !fromStore) medium += "Puede mostrar ventanas encima de otras apps y no viene de una tienda."
        if (Special.ALL_FILES in special && !fromStore) medium += "Tiene acceso a todos tus archivos y no viene de una tienda."
        val unused = lastUsed != null && now - installedAt > UNUSED_DAYS * DAY &&
            (lastUsed == 0L || now - lastUsed > UNUSED_DAYS * DAY)
        if (unused && sensitive.isNotEmpty())
            medium += (if (lastUsed == 0L) "No consta que la hayas abierto en meses" else "Hace ${(now - lastUsed!!) / DAY} días que no la abres") +
                " y conserva: " + sensitive.joinToString(", ") { it.label.lowercase() } + "."

        return when {
            high.isNotEmpty() -> Risk.HIGH to (high + medium)
            medium.isNotEmpty() -> Risk.MEDIUM to medium
            sensitive.isNotEmpty() || special.isNotEmpty() -> Risk.LOW to emptyList()
            else -> Risk.NONE to emptyList()
        }
    }

    fun isUnused(app: AppEntry, now: Long = System.currentTimeMillis()): Boolean =
        app.lastUsed != null && now - app.installedAt > UNUSED_DAYS * DAY &&
            (app.lastUsed == 0L || now - app.lastUsed > UNUSED_DAYS * DAY)
}
