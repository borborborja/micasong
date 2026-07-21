package com.micasong.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.ui.MiCaSongApp
import com.micasong.player.ui.RootViewModel
import com.micasong.player.ui.theme.MiCaSongTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (a FragmentActivity) rather than ComponentActivity: the Cast MediaRouteButton's
// device-chooser dialog is a DialogFragment and throws on anything else (spec §36).
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            val rootViewModel: RootViewModel = hiltViewModel()
            val settings by rootViewModel.settings.collectAsStateWithLifecycle()
            applyWindowPreferences(settings)
            MiCaSongTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                customThemeJson = settings.customThemeJson,
            ) {
                MiCaSongApp()
            }
        }
    }

    /** Apply the Interfaz › Advanced window preferences (spec §44): keep-on, status bar, orientation. */
    @androidx.compose.runtime.Composable
    private fun applyWindowPreferences(settings: com.micasong.player.data.settings.UserSettings) {
        androidx.compose.runtime.LaunchedEffect(settings.keepScreenOn, settings.hideStatusBar, settings.screenOrientation) {
            if (settings.keepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (settings.hideStatusBar) controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            else controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())

            requestedOrientation = when (settings.screenOrientation) {
                com.micasong.player.data.settings.ScreenOrientation.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                com.micasong.player.data.settings.ScreenOrientation.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                com.micasong.player.data.settings.ScreenOrientation.SYSTEM -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    /** Ask for POST_NOTIFICATIONS on Android 13+ so the media notification can appear. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
