package com.enmanuelgil.androidsecurity.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.androidsecurity.ui.components.ScreenHeader
import com.enmanuelgil.androidsecurity.ui.viewmodel.AnomaliesViewModel

@Composable
fun HuellaScreen(vm: AnomaliesViewModel = viewModel()) {
    val context = LocalContext.current
    val state   by vm.state.collectAsState()
    val huella  = state.huellaStats

    LaunchedEffect(Unit) { vm.load(context) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            ScreenHeader(
                title    = "Mi Huella Digital",
                subtitle = "Tu exposición de privacidad en apps instaladas"
            )
        }

        // ── Privacy Score ──────────────────────────────
        item {
            val scoreColor = when {
                huella.privacyScore >= 70 -> Color(0xFF3DDC84)
                huella.privacyScore >= 40 -> Color(0xFFF59E0B)
                else                      -> Color(0xFFEF5B5B)
            }
            val scoreLabel = when {
                huella.privacyScore >= 70 -> "Buena privacidad"
                huella.privacyScore >= 40 -> "Exposición moderada"
                else                      -> "Alta exposición — revisar"
            }

            Surface(
                color    = MaterialTheme.colorScheme.surface,
                shape    = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Puntuación de privacidad",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress        = { huella.privacyScore / 100f },
                            modifier        = Modifier.size(100.dp),
                            color           = scoreColor,
                            trackColor      = scoreColor.copy(0.15f),
                            strokeWidth     = 8.dp,
                            strokeCap       = StrokeCap.Round
                        )
                        Text(
                            "${huella.privacyScore}",
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color      = scoreColor
                        )
                    }
                    Text(scoreLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scoreColor)
                }
            }
        }

        // ── Permission exposure stats ──────────────────
        item {
            Text(
                "Exposición por categoría (apps de usuario)",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {
            Surface(
                color    = MaterialTheme.colorScheme.surface,
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposureRow(Icons.Default.Videocam,     "Cámara",    huella.cameraApps,   Color(0xFF0EA5E9))
                    ExposureRow(Icons.Default.Mic,          "Micrófono", huella.micApps,      Color(0xFFEF5B5B))
                    ExposureRow(Icons.Default.LocationOn,   "Ubicación", huella.locationApps, Color(0xFFF59E0B))
                    ExposureRow(Icons.Default.Contacts,     "Contactos", huella.contactsApps, Color(0xFF7C5CEF))
                    ExposureRow(Icons.Default.Sms,          "SMS",       huella.smsApps,      Color(0xFFEF5B5B))
                }
            }
        }

        // ── Privacy tip ────────────────────────────────
        item {
            Surface(
                color    = Color(0xFF3DDC84).copy(0.08f),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    Icon(Icons.Default.Lightbulb, null,
                        tint = Color(0xFF3DDC84), modifier = Modifier.size(18.dp))
                    Text(
                        "Revisa periódicamente qué apps tienen permiso a tus sensores. " +
                        "Revoca los permisos de apps que no los necesitan activamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
            }
        }

        // ── About / Web ────────────────────────────────
        item {
            Text(
                "Acerca de la app",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {
            Surface(
                color    = MaterialTheme.colorScheme.surface,
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LinkRow(
                        icon    = Icons.Default.Language,
                        label   = "optisuite.app",
                        color   = MaterialTheme.colorScheme.primary,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://optisuite.app"))
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                    LinkRow(
                        icon    = Icons.Default.Code,
                        label   = "GitHub — EnMaNueL-G",
                        color   = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EnMaNueL-G"))
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Versión 1.0.0",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Sin root · Sin internet · 100% local",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                        }
                    }
                }
            }
        }

        // ── Donaciones ─────────────────────────────────
        item {
            Text(
                "Apoya el proyecto",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {
            Surface(
                color    = MaterialTheme.colorScheme.surface,
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Esta aplicación es gratuita y de código abierto. Si te resulta útil, " +
                        "puedes apoyar su desarrollo con una donación voluntaria.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://app.binance.com/payment"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF0B90B),
                            contentColor   = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Donar con Binance Pay", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Todas las donaciones son voluntarias. Ningún dato tuyo se envía a Binance " +
                        "desde esta app — el botón abre Binance en tu navegador.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ExposureRow(
    icon  : ImageVector,
    label : String,
    count : Int,
    color : Color
) {
    val max = 20f
    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(label,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp))
        LinearProgressIndicator(
            progress        = { (count / max).coerceIn(0f, 1f) },
            modifier        = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)),
            color           = color,
            trackColor      = color.copy(0.15f),
            strokeCap       = StrokeCap.Round
        )
        Text("$count",
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color      = color,
            modifier   = Modifier.width(24.dp))
    }
}

@Composable
private fun LinkRow(
    icon    : ImageVector,
    label   : String,
    color   : Color,
    onClick : () -> Unit
) {
    TextButton(
        onClick        = onClick,
        modifier       = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = color,
                modifier = Modifier.weight(1f))
            Icon(Icons.Default.OpenInNew, null,
                tint = color.copy(0.5f), modifier = Modifier.size(14.dp))
        }
    }
}
