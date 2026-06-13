package com.f1xtrack.portal2adaptivesongs

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.f1xtrack.portal2adaptivesongs.ui.PortalApp
import com.f1xtrack.portal2adaptivesongs.ui.theme.PortalTheme

class RootActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppThemeFromPrefs()
        super.onCreate(savedInstanceState)

        setContent {
            var animationIntensity by remember {
                mutableStateOf(readAnimationIntensityPreference(this))
            }
            PortalTheme(animationIntensity = animationIntensity) {
                PortalApp(
                    onAnimationIntensityChanged = { animationIntensity = it },
                )
            }
        }
    }
}
