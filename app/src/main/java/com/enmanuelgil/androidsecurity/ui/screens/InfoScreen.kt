package com.enmanuelgil.androidsecurity.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.androidsecurity.BuildConfig
import com.enmanuelgil.androidsecurity.ui.components.*

@Composable
fun InfoScreen() {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader("AndroidSecurity", "Versión ${BuildConfig.VERSION_NAME} · Enmanuel Gil · OptiSuite") }
        item { SectionTitle("Qué hace") }
        item {
            Panel {
                Bullet("Revisa el móvil: bloqueo de pantalla, antigüedad del parche, señales de root, depuración USB, certificados y VPN.")
                Bullet("Lista las apps con permisos CONCEDIDOS (no solo pedidos) y señala las combinaciones que usan las apps espía.")
                Bullet("Encuentra apps que no abres hace meses y siguen con permisos, y apps que no vienen de una tienda.")
                Bullet("Accesos especiales app por app: Accesibilidad, notificaciones, administrador, ventanas encima, instalar apps…")
                Bullet("Guardia: registra cuándo se ocupan la cámara y el micrófono y avisa si pasa con la pantalla apagada.")
            }
        }
        item { SectionTitle("Qué NO puede hacer (y ninguna app sin root)") }
        item {
            Panel {
                Bullet("Saber qué app usa la cámara o el micrófono en ese momento (Android no lo dice; en Android 12+ lo muestra el propio sistema).")
                Bullet("Quitar permisos por ti: te lleva a la pantalla de Ajustes donde lo haces tú.")
                Bullet("Garantizar que no hay root o una app espía muy bien oculta. No es un antivirus.")
                Bullet("Ver las apps de otro perfil (perfil de trabajo, Island, «apps duplicadas»): solo analiza el perfil donde está instalada.")
            }
        }
        item { SectionTitle("Privacidad") }
        item {
            Panel {
                Text("No tiene permiso de Internet: nada sale del móvil. Sin anuncios, sin cuentas y sin copia en la nube. " +
                    "El registro del guardia se guarda solo en este móvil y puedes borrarlo cuando quieras.", fontSize = 13.sp)
                Text("Código abierto (licencia MIT): github.com/EnMaNueL-G/AndroidSecurity", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EnMaNueL-G/AndroidSecurity"))) } catch (_: Exception) {}
                }, contentPadding = PaddingValues(0.dp)) { Text("Ver el código") }
            }
        }
        item { SectionTitle("Apoya el proyecto") }
        item {
            Panel {
                Text("La app es gratuita. Si te resulta útil, puedes apoyarla con una donación voluntaria por Binance.",
                    color = Muted, fontSize = 13.sp)
                Donate(context, "Binance Pay ID", "1165745950")
                Donate(context, "USDT · red BSC (BEP-20)", "0xb6f6731a4ea87f8e1fd6f44f48b5bc4204571f08")
                Text("Envía solo USDT por la red BSC (BEP-20) a esa dirección.", color = Muted, fontSize = 11.sp)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Bullet(t: String) = Text("• $t", fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp))

@Composable
private fun Donate(context: Context, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color(0xFFF0B90B), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        TextButton(onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
        }) { Text("Copiar") }
    }
}
