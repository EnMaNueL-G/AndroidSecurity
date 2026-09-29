package com.enmanuelgil.androidsecurity.data

import android.app.KeyguardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Status(val order: Int) { BAD(0), WARN(1), INFO(2), OK(3) }

data class Check(
    val title : String,
    val detail: String,
    val status: Status,
    val action: String? = null,        // acción de Ajustes para «Abrir»
    val actionLabel: String = "Abrir ajustes",
)

/** Comprobaciones del móvil que una app normal puede hacer de verdad (sin root). */
object DeviceChecks {

    fun run(context: Context, apps: List<AppEntry>): List<Check> {
        val cr = context.contentResolver
        val list = mutableListOf<Check>()

        // 1. Bloqueo de pantalla
        val secure = try { (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure } catch (_: Exception) { true }
        list += if (secure) Check("Bloqueo de pantalla", "Tienes PIN, patrón o contraseña.", Status.OK)
        else Check("Sin bloqueo de pantalla", "Cualquiera que coja el móvil puede entrar y ver todo. Pon un PIN o contraseña.",
            Status.BAD, Settings.ACTION_SECURITY_SETTINGS)

        // 2. Parche de seguridad
        val patch = Build.VERSION.SECURITY_PATCH
        val months = try { ChronoUnit.MONTHS.between(LocalDate.parse(patch), LocalDate.now()) } catch (_: Exception) { -1L }
        val android = "Android ${Build.VERSION.RELEASE}"
        list += when {
            months < 0 -> Check("Parche de seguridad", "$android · no se pudo leer la fecha del parche.", Status.INFO)
            months <= 3 -> Check("Parche de seguridad al día", "$android · parche de $patch.", Status.OK)
            months <= 12 -> Check("Parche de seguridad de hace $months meses",
                "$android · parche de $patch. Busca actualizaciones del sistema; si el fabricante ya no publica, el móvil queda sin corregir fallos nuevos.",
                Status.WARN, "android.settings.SYSTEM_UPDATE_SETTINGS", "Buscar actualización")
            else -> Check("Parche de seguridad muy antiguo",
                "$android · parche de $patch (hace $months meses). Tiene fallos conocidos sin corregir: evita instalar APKs de fuera y ten cuidado con los enlaces.",
                Status.BAD, "android.settings.SYSTEM_UPDATE_SETTINGS", "Buscar actualización")
        }

        // 3. Señales de root (no es una garantía: el root moderno puede ocultarse)
        val signs = mutableListOf<String>()
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sd/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su", "/su/bin/su", "/system/app/Superuser.apk")
        if (suPaths.any { File(it).exists() }) signs += "archivo «su»"
        if (Build.TAGS?.contains("test-keys") == true) signs += "sistema firmado con claves de prueba"
        val rootApps = listOf("com.topjohnwu.magisk" to "Magisk", "eu.chainfire.supersu" to "SuperSU",
            "me.weishu.kernelsu" to "KernelSU", "com.noshufou.android.su" to "Superuser")
        rootApps.forEach { (p, n) -> if (apps.any { it.pkg == p }) signs += "app $n" }
        list += if (signs.isEmpty()) Check("Sin señales de root",
            "No se encontraron las señales habituales. No es una garantía: un root bien oculto no se ve sin root.", Status.OK)
        else Check("Posible root", "Se encontró: ${signs.joinToString(", ")}. Con root, cualquier app con permiso de root puede verlo todo.", Status.WARN)

        // 4. Opciones de desarrollador / depuración USB
        val dev = Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val adb = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
        list += when {
            adb -> Check("Depuración USB activada",
                "Con el móvil desbloqueado, un ordenador autorizado puede instalar apps y copiar datos por cable. Si no la usas, desactívala.",
                Status.WARN, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            dev -> Check("Opciones de desarrollador activadas", "No es un riesgo por sí solo, pero si no las usas puedes desactivarlas.",
                Status.INFO, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            else -> Check("Depuración USB desactivada", "Las opciones de desarrollador están apagadas.", Status.OK)
        }

        // 5. Certificados de usuario (permiten ver conexiones cifradas si alguien los instaló)
        val userCerts = try {
            val ks = java.security.KeyStore.getInstance("AndroidCAStore"); ks.load(null)
            ks.aliases().toList().count { it.startsWith("user:") }
        } catch (_: Exception) { -1 }
        list += when {
            userCerts > 0 -> Check("$userCerts certificado(s) de seguridad instalados por el usuario",
                "Si no los instalaste tú (o tu empresa/colegio), alguien podría ver el tráfico cifrado de algunas apps (p. ej. navegadores). Revísalos en Ajustes › Seguridad › Credenciales de usuario.",
                Status.WARN, Settings.ACTION_SECURITY_SETTINGS)
            userCerts == 0 -> Check("Sin certificados de usuario", "Nadie ha añadido certificados para interceptar conexiones.", Status.OK)
            else -> Check("Certificados de usuario", "No se pudieron leer.", Status.INFO)
        }

        // 6. VPN
        val vpn = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (_: Exception) { false }
        list += if (vpn) Check("VPN activa",
            "Tu conexión pasa por una VPN. Bien si la pusiste tú; si no la reconoces, puede estar viendo tu tráfico.",
            Status.INFO, Settings.ACTION_VPN_SETTINGS)
        else Check("Sin VPN", "Tu conexión no pasa por ninguna VPN.", Status.OK)

        // 7-9. Accesos especiales de apps instaladas por ti
        val user = apps.filter { !it.isSystem }
        fun names(l: List<AppEntry>) = l.take(6).joinToString(", ") { it.label } + if (l.size > 6) "…" else ""

        val acc = user.filter { Special.ACCESSIBILITY in it.special }
        list += if (acc.isEmpty()) Check("Ninguna app tuya usa Accesibilidad", "Es el acceso más usado por las apps espía.", Status.OK)
        else Check("${acc.size} app(s) con Accesibilidad", "${names(acc)}. Pueden leer tu pantalla y lo que escribes: quítaselo a las que no reconozcas.",
            if (acc.any { !it.fromStore }) Status.BAD else Status.WARN, Settings.ACTION_ACCESSIBILITY_SETTINGS)

        val admins = user.filter { Special.DEVICE_ADMIN in it.special }
        if (admins.isNotEmpty()) list += Check("${admins.size} app(s) administradoras del dispositivo",
            "${names(admins)}. Pueden bloquear o borrar el móvil.",
            if (admins.any { !it.fromStore }) Status.BAD else Status.WARN, Settings.ACTION_SECURITY_SETTINGS)

        val installers = user.filter { Special.INSTALL_APPS in it.special }
        if (installers.isNotEmpty()) list += Check("${installers.size} app(s) pueden instalar otras apps",
            "${names(installers)}. Normal en navegadores o gestores de archivos; quítalo si no lo necesitas.",
            Status.INFO, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)

        val outside = user.filter { !it.fromStore }
        list += Check("${outside.size} app(s) no instaladas desde una tienda",
            if (outside.isEmpty()) "Todas tus apps vienen de una tienda conocida."
            else "${names(outside)}. No es malo por sí solo, pero así llegan las apps espía: revisa que las reconozcas (pestaña Apps › Fuera de tienda).",
            if (outside.isEmpty()) Status.OK else Status.INFO)

        return list.sortedBy { it.status.order }
    }
}
