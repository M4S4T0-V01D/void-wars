package dev.m4s4t0.voidwars

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.m4s4t0.voidui.BUILTIN_THEMES
import dev.m4s4t0.voidui.Void
import kotlinx.coroutines.flow.MutableStateFlow

/** The colour theme (one of the VOID family's seven, from void-ui), optionally with a matching launcher icon. */
object Themes {
    val choice = MutableStateFlow(BUILTIN_THEMES.first().id)
    val iconFollows = MutableStateFlow(true)
    private lateinit var app: Context

    private fun prefs() = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        choice.value = prefs().getString("theme", BUILTIN_THEMES.first().id) ?: BUILTIN_THEMES.first().id
        iconFollows.value = prefs().getBoolean("iconFollows", true)
        apply()
    }

    fun choose(id: String) {
        choice.value = id
        prefs().edit().putString("theme", id).apply()
        apply()
    }

    fun setIconFollows(on: Boolean) {
        iconFollows.value = on
        prefs().edit().putBoolean("iconFollows", on).apply()
    }

    private fun apply() {
        Void.palette = BUILTIN_THEMES.firstOrNull { it.id == choice.value } ?: BUILTIN_THEMES.first()
    }

    /**
     * Switches the launcher icon to match. Called when the app leaves the screen: disabling the alias the
     * app was launched through closes its task, so doing it while the user is in Settings would throw them out.
     */
    fun syncLauncherIcon() {
        if (!::app.isInitialized) return
        setLauncherIcon(if (iconFollows.value) Void.palette.id else BUILTIN_THEMES.first().id)
    }

    /** Enable the launcher alias for [themeId] and disable the others (aliases from art/gen_icon.py). */
    private fun setLauncherIcon(themeId: String) {
        val pm = app.packageManager
        val want = ComponentName(app, "${app.packageName}.Icon_${themeId.replace('-', '_')}")
        val enabledNow = pm.getComponentEnabledSetting(want)
        val isDefault = themeId == BUILTIN_THEMES.first().id
        val alreadyOn = enabledNow == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (isDefault && enabledNow == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        if (alreadyOn) return
        try {
            // Turn the new one on before the old one off, so the launcher always has an entry.
            pm.setComponentEnabledSetting(want, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            for (t in BUILTIN_THEMES) {
                if (t.id == themeId) continue
                pm.setComponentEnabledSetting(
                    ComponentName(app, "${app.packageName}.Icon_${t.id.replace('-', '_')}"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } catch (e: Exception) {
            // Some launchers or work profiles refuse; the app keeps its current icon.
        }
    }
}
