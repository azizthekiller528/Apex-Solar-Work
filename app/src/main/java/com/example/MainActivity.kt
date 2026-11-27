package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.firebase.configureAppCheck
import com.example.ui.DocFolioApp
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.DocFolioTheme
import com.example.ui.theme.ThemePreferenceStore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase ops
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge display and render Compose UI with Day/Night/System theme state
        enableEdgeToEdge()
        setContent {
            var themeMode by remember {
                mutableStateOf(ThemePreferenceStore.loadThemeMode(this@MainActivity))
            }
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
            }

            DocFolioTheme(darkTheme = isDark) {
                DocFolioApp(
                    currentUser = Firebase.auth.currentUser,
                    themeMode = themeMode,
                    isDarkTheme = isDark,
                    onThemeModeChange = { nextMode ->
                        themeMode = nextMode
                        ThemePreferenceStore.saveThemeMode(this@MainActivity, nextMode)
                    }
                )
            }
        }
    }
}
