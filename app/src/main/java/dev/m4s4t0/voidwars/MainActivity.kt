package dev.m4s4t0.voidwars

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.m4s4t0.voidwars.ui.WarsAppUi
import dev.m4s4t0.voidui.VoidTheme

class MainActivity : ComponentActivity() {
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A landscape game: hide the bars, swipe from an edge to peek at them.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (intent?.action == Updater.ACTION_SHOW) Updater.showRequested = true
        setContent { VoidTheme { WarsAppUi() } }
        // The update notification needs this; asked once, on first start.
        val sp = getSharedPreferences("updates", MODE_PRIVATE)
        if (!sp.getBoolean("askedNotify", false)) {
            sp.edit().putBoolean("askedNotify", true).apply()
            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Updater.ACTION_SHOW) Updater.showRequested = true
    }

    override fun onStart() {
        super.onStart()
        Updater.foreground = true
        Updater.check(this, quiet = true)
        UpdateJob.schedule(this)
    }

    override fun onStop() {
        super.onStop()
        Updater.foreground = false
        if (!isChangingConfigurations) Themes.syncLauncherIcon()
    }
}
