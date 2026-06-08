package com.enmanuelgil.androidsecurity.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.model.*
import com.enmanuelgil.androidsecurity.ui.components.*
import com.enmanuelgil.androidsecurity.ui.viewmodel.PermissionsViewModel

@Composable
fun PermissionsScreen(vm: PermissionsViewModel = viewModel()) {
    val context = LocalContext.current
    val apps    by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    var filter  by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) { vm.load(context) }

    val filtered = remember(apps, filter) {
        when (filter) {
            "high"   -> apps.filter { it.riskLevel == RiskLevel.HIGH }
            "medium" -> apps.filter { it.riskLevel == RiskLevel.MEDIUM }
            "system" -> apps.filter { it.isSystem }
            else     -> apps
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        ScreenHeader(
            title    = stringResource(R.string.perm_title),
            subtitle = stringResource(R.string.perm_subtitle)
        )

        // Stats row
        if (!loading && apps.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip(
                    value = apps.size.toString(),
                    label = "Apps",
                    color = MaterialTheme.colorScheme.primary
                )
                StatChip(
                    value = apps.count { it.riskLevel == RiskLevel.HIGH && !it.isSystem }.toString(),
                    label = "Alto riesgo",
                    color = Color(0xFFEF5B5B)
                )
                StatChip(
                    value = apps.count { it.riskLevel == RiskLevel.MEDIUM }.toString(),
                    label = "Medio",
                    color = Color(0xFFF59E0B)
                )
            }
        }

        // Filter chips
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "all"    to stringResource(R.string.perm_filter_all),
                "high"   to stringResource(R.string.perm_filter_high),
                "medium" to stringResource(R.string.perm_filter_medium),
                "system" to stringResource(R.string.perm_filter_system)
            )
            items(filters) { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick  = { filter = key },
                    label    = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.perm_loading), color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppPermissionCard(app = app)
                }
            }
        }
    }
}

@Composable
private fun AppPermissionCard(app: AppPermissionInfo) {
    var expanded by remember { mutableStateOf(false) }

    val riskColor = when (app.riskLevel) {
        RiskLevel.HIGH   -> Color(0xFFEF5B5B)
        RiskLevel.MEDIUM -> Color(0xFFF59E0B)
        RiskLevel.LOW    -> Color(0xFF3DDC84)
        RiskLevel.SAFE   -> Color(0xFF5A5A7A)
    }
    val riskStr = when (app.riskLevel) {
        RiskLevel.HIGH   -> "Alto"
        RiskLevel.MEDIUM -> "Medio"
        RiskLevel.LOW    -> "Bajo"
        RiskLevel.SAFE   -> "Seguro"
    }

    Card(
        onClick   = { expanded = !expanded },
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App icon
                AppIcon(drawable = app.icon, size = 40.dp)

                // Name + package
                Column(Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        maxLines = 1
                    )
                }

                // Risk badge
                Surface(
                    color  = riskColor.copy(alpha = 0.15f),
                    shape  = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        riskStr,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        color    = riskColor,
                        style    = MaterialTheme.typography.labelMedium
                    )
                }

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint   = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Category icons row
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                app.categories.take(6).forEach { cat ->
                    CategoryBadge(cat)
                }
                if (app.categories.size > 6) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "+${app.categories.size - 6}",
                            Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }
                }
            }

            // Expanded: list all permissions
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.5f))
                Spacer(Modifier.height(10.dp))
                app.permissions.forEach { perm ->
                    val permColor = if (perm in setOf(
                            "android.permission.CAMERA","android.permission.RECORD_AUDIO",
                            "android.permission.READ_SMS","android.permission.ACCESS_FINE_LOCATION",
                            "android.permission.ACCESS_BACKGROUND_LOCATION"
                        )) Color(0xFFEF5B5B) else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    Row(
                        Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(4.dp).clip(CircleShape)
                            .background(permColor)
                        )
                        Text(
                            perm.removePrefix("android.permission."),
                            style = MaterialTheme.typography.bodySmall,
                            color = permColor
                        )
                    }
                }
            }
        }
    }
}
