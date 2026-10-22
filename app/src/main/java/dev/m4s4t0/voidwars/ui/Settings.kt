package dev.m4s4t0.voidwars.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.BuildConfig
import dev.m4s4t0.voidwars.Themes
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.MEDALS
import dev.m4s4t0.voidwars.model.Quality
import dev.m4s4t0.voidui.BUILTIN_THEMES
import dev.m4s4t0.voidui.Card
import dev.m4s4t0.voidui.PillButton
import dev.m4s4t0.voidui.Pills
import dev.m4s4t0.voidui.SectionTitle
import dev.m4s4t0.voidui.Toggle
import dev.m4s4t0.voidui.Void

@Composable
fun SettingsScreen(state: AppState) {
    val theme by Themes.choice.collectAsState()
    val iconFollows by Themes.iconFollows.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    val violet = SliderDefaults.colors(thumbColor = Void.violet, activeTrackColor = Void.violet, inactiveTrackColor = Void.panelAlt)
    val cyan = SliderDefaults.colors(thumbColor = Void.cyan, activeTrackColor = Void.cyan, inactiveTrackColor = Void.panelAlt)
    Column(Modifier.fillMaxSize().background(Void.bg.copy(alpha = 0.7f)).safe()) {
        GameBar("Settings", { state.go(Screen.HOME) }, icon = "settings")
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Sound and music")
                Card {
                    Toggle("Music", "A synth track for every sector", Game.music) { Game.music = it; Game.saveSettings() }
                    Text("Music volume", color = Void.text, fontSize = 14.sp)
                    Slider(Game.musicVolume, { Game.musicVolume = it }, onValueChangeFinished = { Game.saveSettings() }, enabled = Game.music, colors = violet)
                    Toggle("Sounds", "Blasters, explosions and pickups", Game.sounds) { Game.sounds = it; Game.saveSettings() }
                    Text("Sound volume", color = Void.text, fontSize = 14.sp)
                    Slider(Game.volume, { Game.volume = it }, onValueChangeFinished = { Game.saveSettings(); Audio.play(Snd.BOOM, 0.8f) }, colors = cyan)
                    Toggle("Haptics", "Feel every hit and explosion", Game.haptics) { Game.haptics = it; Game.saveSettings(); tapFeedback() }
                }
                SectionTitle("Controls")
                Card {
                    Text("Ship sensitivity · ${"%.1f".format(Game.sensitivity)}×", color = Void.text, fontSize = 14.sp)
                    Text("How far the ship moves for each bit your finger moves", color = Void.dim, fontSize = 12.sp)
                    Slider(Game.sensitivity, { Game.sensitivity = (it * 10).toInt() / 10f }, valueRange = 0.6f..2.4f, onValueChangeFinished = { Game.saveSettings() }, colors = cyan)
                    Toggle("Show the hitbox", "The glowing dot is the only part of the ship that can be hit", Game.showHitbox) { Game.showHitbox = it; Game.saveSettings() }
                    Toggle("Screen shake", "Explosions rattle the camera", Game.shake) { Game.shake = it; Game.saveSettings() }
                }
                SectionTitle("Graphics")
                Card {
                    Pills(Quality.entries.map { it to it.title }, Game.quality) { tapFeedback(); Game.quality = it; Game.saveSettings() }
                    Text(
                        when (Game.quality) {
                            Quality.LOW -> "Lower resolution and fewer sparks. Lightest on battery and memory."
                            Quality.MEDIUM -> "A sharp picture with plenty of sparks. Good for most phones."
                            Quality.HIGH -> "Full resolution, smooth edges and a rich nebula."
                        },
                        color = Void.dim, fontSize = 12.sp,
                    )
                    Toggle("Cap at 60 fps", "Saves a lot of battery on 90 and 120 Hz screens", Game.cap60) { Game.cap60 = it; Game.saveSettings() }
                }
                Spacer(Modifier.height(12.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Theme")
                Card {
                    Pills(BUILTIN_THEMES.map { it.id to it.name }, theme) { tapFeedback(); Themes.choose(it) }
                    Toggle("App icon matches the theme", "The launcher icon changes colour with the theme", iconFollows) { Themes.setIconFollows(it) }
                }
                SectionTitle("Updates")
                UpdatesCard()
                SectionTitle("Progress")
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Level ${Game.level}", color = Void.dim, fontSize = 13.sp)
                        Money(Game.credits.toLong(), fontSize = 13.sp, icon = 15.dp)
                        Text("${Game.medals.size}/${MEDALS.size} medals", color = Void.dim, fontSize = 13.sp)
                    }
                    PillButton("Start over", false) { confirmReset = true }
                }
                Text("Void Wars ${BuildConfig.VERSION_NAME} · part of the VOID family", color = Void.dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Void.panel,
            title = { Text("Start over?", color = Void.text) },
            text = { Text("Credits, cores, levels, parts, stars and medals all go back to the beginning. This can't be undone.", color = Void.dim) },
            confirmButton = { PillButton("Start over", true) { Game.reset(); confirmReset = false; state.go(Screen.HOME) } },
            dismissButton = { PillButton("Cancel", false) { confirmReset = false } },
        )
    }
}
