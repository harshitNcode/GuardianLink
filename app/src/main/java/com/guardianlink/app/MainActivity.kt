package com.guardianlink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.guardianlink.app.navigation.AppNavigation
import com.guardianlink.app.ui.theme.GuardianLinkTheme
import com.guardianlink.app.util.AuthPreference
import com.guardianlink.app.util.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.guardianlink.app.data.ApiConfig.HOST = AuthPreference.getServerHost(this)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var isDarkMode by remember { mutableStateOf(ThemePreference.isDarkMode(context)) }

            GuardianLinkTheme(darkTheme = isDarkMode) {
                AppNavigation(
                    isDarkMode = isDarkMode,
                    onToggleTheme = {
                        isDarkMode = !isDarkMode
                        ThemePreference.setDarkMode(context, isDarkMode)
                    }
                )
            }
        }
    }
}