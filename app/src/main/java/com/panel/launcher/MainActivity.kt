package com.panel.launcher

import android.graphics.Color
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import com.panel.launcher.ui.HomeScreen
import com.panel.launcher.ui.PanelTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    /** Incremented whenever the Home button is pressed while we're already showing. */
    private val homePresses = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (vm.prefs.lockPortrait) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        vm.hostView = { window?.decorView }
        setContent {
            PanelTheme(pixelFont = vm.prefs.pixelFont) {
                HomeScreen(vm, homePresses.intValue)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { vm.widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { vm.widgetHost.stopListening() }
    }

    @Deprecated("Widget permission / setup screens report back here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BIND_WIDGET || requestCode == REQUEST_CONFIGURE_WIDGET) {
            vm.onWidgetActivityResult(this, requestCode, resultCode == RESULT_OK)
        }
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
    }

    override fun onDestroy() {
        vm.hostView = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        homePresses.intValue++
    }
}
