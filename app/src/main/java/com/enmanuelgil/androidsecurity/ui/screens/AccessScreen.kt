package com.enmanuelgil.androidsecurity.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.androidsecurity.data.AppEntry
import com.enmanuelgil.androidsecurity.data.Special
import com.enmanuelgil.androidsecurity.ui.ScanState
import com.enmanuelgil.androidsecurity.ui.components.*

private data class Ussd(val code: String, val title: String, val what: String, val changesSomething: Boolean = false)

private val USSD = listOf(
    Ussd("*#21#", "¿Se desvían TODAS mis llamadas?", "Consulta si tus llamadas se reenvían siempre a otro número."),
    Ussd("*#61#", "Desvío si no contesto", "Consulta a qué número van las llamadas que no contestas (suele ser el buzón de voz)."),
    Ussd("*#62#", "Desvío sin cobertura", "Consulta a qué número van las llamadas cuando no tienes señal o el móvil está apagado."),
    Ussd("*#67#", "Desvío si comunico", "Consulta a qué número van las llamadas cuando ya estás hablando."),
    Ussd("##002#", "Quitar todos los desvíos",
        "Cancela TODOS los desvíos, también el del buzón de voz que pone tu operador. Úsalo solo si ves un número que no reconoces.", true),
    Ussd("*#06#", "Ver el IMEI", "Muestra el IMEI; compáralo con el de la caja o la factura."),
)

@Composable
fun AccessScreen(state: ScanState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Ussd?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Accesos especiales", "Los permisos más delicados, app por app") {
                IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Volver a analizar") }
            }
        }
        state.error?.let { item { ErrorCard(it, onRefresh) } }
        if (state.loading && state.scannedAt == 0L) { item { LoadingBox() }; return@LazyColumn }

        items(Special.entries.toList()) { sp ->
            val apps = state.apps.filter { sp in it.special }
            SpecialPanel(sp, apps.filter { !it.isSystem }, apps.filter { it.isSystem })
        }

        item { SectionTitle("Desvíos de llamadas (códigos del operador)") }
        item {
            Panel {
                Text("Una forma de espiar sin apps es desviar tus llamadas o SMS. Estos códigos se abren en el marcador; " +
                    "tú pulsas llamar. Los responde tu operador y algunos no los admiten.", color = Muted, fontSize = 13.sp)
                USSD.forEach { u ->
                    Row(Modifier.fillMaxWidth().clickable {
                        if (u.changesSomething) confirm = u else dial(context, u.code)
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.title, fontWeight = FontWeight.SemiBold, color = if (u.changesSomething) RiskMed else MaterialTheme.colorScheme.onSurface)
                            Text(u.what, color = Muted, fontSize = 12.sp)
                        }
                        Text(u.code, fontFamily = FontFamily.Monospace, color = RiskLow, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    confirm?.let { u ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(u.title) },
            text = { Text(u.what + "\n\nSe abrirá el marcador con ${u.code}; no se hace nada hasta que pulses llamar.") },
            confirmButton = { TextButton(onClick = { dial(context, u.code); confirm = null }) { Text("Abrir marcador") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}

private fun dial(context: android.content.Context, code: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

@Composable
private fun SpecialPanel(sp: Special, user: List<AppEntry>, system: List<AppEntry>) {
    val context = LocalContext.current
    var showSystem by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sp.label, fontWeight = FontWeight.Bold)
                Text(sp.what, color = Muted, fontSize = 12.sp)
            }
            Pill(if (user.isEmpty()) "Ninguna" else "${user.size}", if (user.isEmpty()) RiskOk else RiskMed)
        }
        user.forEach { a -> AppRow(a) }
        if (system.isNotEmpty()) {
            TextButton(onClick = { showSystem = !showSystem }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showSystem) "Ocultar apps del sistema" else "Y ${system.size} app(s) del sistema")
            }
            if (showSystem) system.forEach { a -> AppRow(a) }
        }
        TextButton(onClick = { openSettings(context, sp.settingsAction) }, contentPadding = PaddingValues(0.dp)) { Text("Gestionar en Ajustes") }
    }
}

@Composable
private fun AppRow(a: AppEntry) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { openAppDetails(context, a.pkg) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a.pkg, 30.dp)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!a.isSystem && !a.fromStore) Text("No viene de una tienda", color = RiskMed, fontSize = 11.sp)
        }
    }
}
