package com.enmanuelgil.androidsecurity.ui.screens

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.androidsecurity.data.Perm
import com.enmanuelgil.androidsecurity.data.Risk
import com.enmanuelgil.androidsecurity.data.Status
import com.enmanuelgil.androidsecurity.ui.ScanState
import com.enmanuelgil.androidsecurity.ui.components.*

@Composable
fun HomeScreen(state: ScanState, onRefresh: () -> Unit, onOpenApps: (AppFilter) -> Unit) {
    val context = LocalContext.current
    val user = state.apps.filter { !it.isSystem }
    val high = user.count { it.risk == Risk.HIGH }
    val medium = user.count { it.risk == Risk.MEDIUM }
    val green = state.checks.count { it.status == Status.OK }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Revisión", "Seguridad y privacidad de este móvil · sin root, sin Internet") {
                IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Volver a analizar") }
            }
        }
        state.error?.let { item { ErrorCard(it, onRefresh) } }
        if (state.loading && state.scannedAt == 0L) { item { LoadingBox() }; return@LazyColumn }

        // ── Resumen ─────────────────────────────────────────────
        item {
            Panel {
                val color = when { high > 0 -> RiskHigh; medium > 0 -> RiskMed; else -> RiskOk }
                Text(when {
                    high > 0 -> "$high app(s) para revisar ya"
                    medium > 0 -> "$medium app(s) merecen un vistazo"
                    else -> "Nada preocupante en tus apps"
                }, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("$green de ${state.checks.size} comprobaciones del móvil en verde · ${user.size} apps instaladas por ti",
                    color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                if (high + medium > 0)
                    Button(onClick = { onOpenApps(AppFilter.REVIEW) }, modifier = Modifier.padding(top = 10.dp)) { Text("Ver cuáles y por qué") }
                Text("No es un antivirus: señala lo que suelen usar las apps espía (Accesibilidad, notificaciones, administrador, " +
                    "apps de fuera de la tienda) para que decidas tú.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }

        if (!state.usageAccess) item {
            Panel {
                Text("Concede «Acceso a datos de uso»", fontWeight = FontWeight.SemiBold)
                Text("Sirve para ver qué apps llevas meses sin abrir (y siguen teniendo permisos) y, en el Guardia, qué app estaba en pantalla. " +
                    "Los datos no salen del móvil.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                OutlinedButton(onClick = { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS, context.packageName) },
                    modifier = Modifier.padding(top = 8.dp)) { Text("Abrir ajustes") }
            }
        }

        // ── Comprobaciones del móvil ────────────────────────────
        item { SectionTitle("Comprobaciones del móvil") }
        items(state.checks) { c ->
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(10.dp).background(c.status.color(), CircleShape))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(c.title, fontWeight = FontWeight.SemiBold)
                        Text(c.detail, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                        c.action?.let { a ->
                            TextButton(onClick = { openSettings(context, a) }, contentPadding = PaddingValues(0.dp)) { Text(c.actionLabel) }
                        }
                    }
                }
            }
        }

        // ── Qué apps pueden… ────────────────────────────────────
        item { SectionTitle("Apps instaladas por ti que pueden…") }
        item {
            Panel {
                val rows = listOf(
                    Perm.CAMERA to "usar la cámara", Perm.MIC to "usar el micrófono", Perm.LOCATION to "saber tu ubicación",
                    Perm.BG_LOCATION to "saber tu ubicación siempre", Perm.CONTACTS to "leer tus contactos",
                    Perm.SMS to "leer o enviar SMS", Perm.CALLS to "ver o hacer llamadas", Perm.FILES to "ver tus fotos y archivos",
                )
                rows.forEach { (p, what) ->
                    val n = user.count { p in it.granted }
                    Row(Modifier.fillMaxWidth().clickable { onOpenApps(AppFilter.of(p)) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(what.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                        Text("$n de ${user.size}", color = if (n == 0) Muted else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Solo cuenta permisos CONCEDIDOS. Toca una fila para ver las apps y quitar lo que sobre.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
