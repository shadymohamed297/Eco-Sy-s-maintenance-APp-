package com.markazsayana.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.rememberNavController
import com.markazsayana.app.ui.navigation.MarkazSayanaNavHost
import com.markazsayana.app.ui.theme.MarkazSayanaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The app is Arabic RTL only, per the design brief.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MarkazSayanaTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MarkazSayanaNavHost(navController = rememberNavController())
                    }
                }
            }
        }
    }
}
