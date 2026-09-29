package com.enmanuelgil.androidsecurity.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.enmanuelgil.androidsecurity.data.Risk
import com.enmanuelgil.androidsecurity.data.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val RiskHigh = Color(0xFFEF5B5B)
val RiskMed  = Color(0xFFF5A524)
val RiskLow  = Color(0xFF5B8DEF)
val RiskOk   = Color(0xFF3DDC84)
val Muted    = Color(0xFF9898B8)

fun Risk.color() = when (this) { Risk.HIGH -> RiskHigh; Risk.MEDIUM -> RiskMed; Risk.LOW -> RiskLow; Risk.NONE -> Muted }
fun Status.color() = when (this) { Status.BAD -> RiskHigh; Status.WARN -> RiskMed; Status.INFO -> RiskLow; Status.OK -> RiskOk }

// ── Acciones de Ajustes ─────────────────────────────────────────────────────
private fun tryStart(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

/** Abre una pantalla de Ajustes; si el móvil no la tiene, abre Ajustes. */
fun openSettings(context: Context, action: String, pkg: String? = null) {
    if (pkg != null && tryStart(context, Intent(action, Uri.parse("package:$pkg")))) return
    if (tryStart(context, Intent(action))) return
    if (!tryStart(context, Intent(Settings.ACTION_SETTINGS)))
        Toast.makeText(context, "No se pudo abrir Ajustes", Toast.LENGTH_SHORT).show()
}

/** Ficha de la app en Ajustes: permisos, desactivar, forzar detención… */
fun openAppDetails(context: Context, pkg: String) = openSettings(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)

fun uninstall(context: Context, pkg: String) {
    if (!tryStart(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))) openAppDetails(context, pkg)
}

// ── Iconos de apps (carga perezosa y caché pequeña) ─────────────────────────
private val iconCache = LruCache<String, ImageBitmap>(250)

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bmp by produceState(iconCache.get(pkg), pkg) {
        if (value == null) value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap().also { iconCache.put(pkg, it) }
            } catch (_: Exception) { null }
        }
    }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(size).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Android, null, tint = Muted, modifier = Modifier.size(size * 0.55f))
    }
}

// ── Piezas comunes ──────────────────────────────────────────────────────────
@Composable
fun ScreenHeader(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Surface(color = color.copy(0.14f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun LoadingBox(text: String = "Analizando el móvil…") {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(32.dp))
            Text(text, color = Muted, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun ErrorCard(text: String, onRetry: () -> Unit) {
    Panel {
        Text(text, color = RiskHigh)
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
    val d = (now - ms) / (24L * 60 * 60 * 1000)
    return when {
        d <= 0 -> "hoy"
        d == 1L -> "ayer"
        d < 60 -> "hace $d días"
        d < 730 -> "hace ${d / 30} meses"
        else -> "hace ${d / 365} años"
    }
}
