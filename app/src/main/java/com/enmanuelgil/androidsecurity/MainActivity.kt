package com.enmanuelgil.androidsecurity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.enmanuelgil.androidsecurity.ui.MainScreen
import com.enmanuelgil.androidsecurity.ui.Screen
import com.enmanuelgil.androidsecurity.ui.theme.AndroidSecurityTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Pestaña inicial, p. ej. `adb shell am start -n …/.MainActivity --es initial_screen GUARD` */
        const val EXTRA_INITIAL_SCREEN = "initial_screen"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent.getStringExtra(EXTRA_INITIAL_SCREEN)
            ?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: Screen.HOME
        setContent {
            AndroidSecurityTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(initialScreen = initial)
                }
            }
        }
    }
}
