package com.enmanuelgil.androidsecurity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.enmanuelgil.androidsecurity.ui.theme.AndroidSecurityTheme
import com.enmanuelgil.androidsecurity.ui.MainScreen
import com.enmanuelgil.androidsecurity.ui.Screen

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INITIAL_SCREEN = "initial_screen"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow launching with a specific initial tab via intent extra
        // e.g. adb shell am start -n .../.MainActivity --es initial_screen DETECTOR
        val initialScreen = intent.getStringExtra(EXTRA_INITIAL_SCREEN)
            ?.let { name -> runCatching { Screen.valueOf(name) }.getOrNull() }
            ?: Screen.PERMISSIONS

        setContent {
            AndroidSecurityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(initialScreen = initialScreen)
                }
            }
        }
    }
}
