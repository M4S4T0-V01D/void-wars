package dev.m4s4t0.voidwars

import android.app.Application
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.model.Game

class WarsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Themes.init(this)
        Game.init(this)
        Haptics.init(this)
        Audio.start()
    }
}
