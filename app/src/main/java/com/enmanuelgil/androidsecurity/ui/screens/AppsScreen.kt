package com.enmanuelgil.androidsecurity.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.androidsecurity.data.AppEntry
import com.enmanuelgil.androidsecurity.data.AppScanner
import com.enmanuelgil.androidsecurity.data.Perm
import com.enmanuelgil.androidsecurity.data.Risk
import com.enmanuelgil.androidsecurity.ui.ScanState
import com.enmanuelgil.androidsecurity.ui.components.*

enum class AppFilter(val label: String, val perm: Perm? = null) {
    REVIEW("Revisar"), ALL("Todas"), UNUSED("Sin usar"), OUTSIDE("Fuera de tienda"),
    CAMERA("Cámara", Perm.CAMERA), MIC("Micrófono", Perm.MIC), LOCATION("Ubicación", Perm.LOCATION),
    BG_LOCATION("Ubicación siempre", Perm.BG_LOCATION), CONTACTS("Contactos", Perm.CONTACTS),
    SMS("SMS", Perm.SMS), CALLS("Llamadas", Perm.CALLS), FILES("Fotos y archivos", Perm.FILES);

    fun matches(a: AppEntry): Boolean = when (this) {
        REVIEW -> a.risk == Risk.HIGH || a.risk == Risk.MEDIUM
        ALL -> true
        UNUSED -> AppScanner.isUnused(a) && a.sensitiveGranted.isNotEmpty()
        OUTSIDE -> !a.fromStore
        else -> perm!! in a.granted
    }

    companion object { fun of(p: Perm) = entries.first { it.perm == p } }
}

@Composable
fun AppsScreen(state: ScanState, filter: AppFilter, onFilter: (AppFilter) -> Unit, onRefresh: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val list = remember(state.apps, filter, query, showSystem) {
        state.apps.filter { a ->
            (showSystem || !a.isSystem) && filter.matches(a) &&
                (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Apps", "Permisos concedidos de verdad y por qué revisarlas") {
            IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Volver a analizar") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppFilter.entries.forEach { f ->
                FilterChip(selected = f == filter, onClick = { onFilter(f) }, label = { Text(f.label) })
            }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Buscar app") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().clickable { showSystem = !showSystem }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(showSystem, { showSystem = it })
            Text("Incluir apps del sistema (vienen con el móvil; no se puntúan)", style = MaterialTheme.typography.bodySmall)
        }
        if (filter == AppFilter.UNUSED && !state.usageAccess)
            Text("Para saber qué apps no usas hace falta «Acceso a datos de uso» (pestaña Inicio).",
                color = RiskMed, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 13.sp)
        if (filter == AppFilter.UNUSED && state.usageAccess)
            Text("Apps que no abres hace más de ${AppScanner.UNUSED_DAYS} días y conservan permisos sensibles. En Android 11+ el sistema " +
                "suele quitárselos solo; si no, hazlo tú o desinstálalas.", color = Muted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

        state.error?.let { ErrorCard(it, onRefresh) }
        when {
            state.loading && state.scannedAt == 0L -> LoadingBox()
            list.isEmpty() -> Text(
                if (filter == AppFilter.REVIEW) "Ninguna app tuya necesita revisión. 👍" else "No hay apps en este filtro.",
                color = Muted, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item { Text("${list.size} app(s)", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp)) }
                items(list, key = { it.pkg }) { AppCard(it) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AppCard(a: AppEntry) {
    val context = LocalContext.current
    var open by rememberSaveable(a.pkg) { mutableStateOf(false) }
    Panel(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a.pkg)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(a.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((a.granted.map { it.label } + a.special.map { it.label }).joinToString(" · ")
                    .ifEmpty { "Sin permisos sensibles concedidos" },
                    color = Muted, fontSize = 12.sp, maxLines = if (open) 4 else 1, overflow = TextOverflow.Ellipsis)
            }
            if (a.isSystem) Pill("Sistema", Muted)
            else if (a.risk == Risk.HIGH || a.risk == Risk.MEDIUM) Pill(a.risk.label, a.risk.color())
        }
        if (a.reasons.isNotEmpty()) Column(Modifier.padding(top = 8.dp)) {
            a.reasons.forEach { Text("• $it", color = a.risk.color(), fontSize = 13.sp) }
        }
        if (open) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val denied = a.requested - a.granted
                if (denied.isNotEmpty()) Line("Pide pero NO tiene", denied.joinToString(", ") { it.label })
                if (a.special.isNotEmpty()) Line("Accesos especiales", a.special.joinToString(", ") { it.label })
                Line("Origen", AppScanner.installerLabel(a.installer))
                Line("Instalada", DateFormat.getDateFormat(context).format(a.installedAt))
                a.lastUsed?.let { Line("Última vez abierta", if (it == 0L) "sin uso registrado" else ago(it)) }
                Text(a.pkg, color = Muted, fontSize = 11.sp)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openAppDetails(context, a.pkg) }) { Text("Permisos y ajustes") }
                if (!a.isSystem) OutlinedButton(onClick = { uninstall(context, a.pkg) }) { Text("Desinstalar") }
            }
        }
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row {
        Text("$k: ", color = Muted, fontSize = 13.sp)
        Text(v, fontSize = 13.sp)
    }
}
