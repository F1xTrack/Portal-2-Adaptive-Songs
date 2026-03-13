package com.f1xtrack.portal2adaptivesongs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.f1xtrack.portal2adaptivesongs.ui.PortalApp
import com.f1xtrack.portal2adaptivesongs.ui.theme.PortalTheme

class RootActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppThemeFromPrefs()
        super.onCreate(savedInstanceState)

        setContent {
            PortalTheme {
                PortalApp()
            }
        }
    }
}
