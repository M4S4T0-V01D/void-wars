package dev.m4s4t0.voidwars.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.Mode
import dev.m4s4t0.voidwars.model.Stat
import dev.m4s4t0.voidui.Void

@Composable
fun HomeScreen(state: AppState) {
    val next = Game.nextStage()
    val sector = Campaign.SECTORS[next.sector]
    val missionsLeft = Game.missions.count { !it.done }
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Void.bg.copy(alpha = 0.55f), Color.Transparent, Void.bg.copy(alpha = 0.7f))))) {
        Row(Modifier.fillMaxSize().safe().padding(16.dp)) {
            // The title, the pilot, the wallet.
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LevelChip()
                    WalletChip()
                }
                Column(Modifier.bob(4.dp, periodMs = 3200)) {
                    Text("VOID", style = logo(54), color = Void.violet)
                    Text("WARS", style = logo(54), color = Void.cyan, modifier = Modifier.padding(start = 40.dp))
                    Text(
                        "Sector ${next.sector + 1} · ${sector.title}", color = Void.text.copy(alpha = 0.85f), fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon("settings") { state.go(Screen.SETTINGS) }
                    if (Game.supplyReady) SupplyButton(state)
                    else {
                        val streak = Game.stats[Stat.STREAK] ?: 1
                        Row(Modifier.glass(RoundedCornerShape(50), alpha = 0.7f).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph("streak", 16.dp, color = Void.dim, glow = false)
                            Text("Day $streak streak · next supply drop tomorrow", color = Void.dim, fontSize = 12.sp)
                        }
                    }
                }
            }
            // What to do.
            Column(
                Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                val stars = Game.stars[next.id] ?: 0
                BigButton(
                    "dock", "Fly to the hub", "Stations, contracts, the black market and ${Mode.entries.size} mission lines",
                    accent = Void.violet, height = 72.dp,
                ) { state.hub() }
                BigButton(
                    "launch", "Launch ${next.title}", if (next.isBoss) "Boss: ${sector.boss.title}" else if (stars > 0) "Replay for more stars" else sector.subtitle,
                    accent = if (next.isBoss) Void.bad else Color(0xFF9B6BFF), phase = 1,
                ) { state.launch(next) }
                BigButton("campaign", "Campaign", "${Campaign.allStages.sumOf { Game.stars[it.id] ?: 0 }} / ${Campaign.allStages.size * 3} stars", accent = Void.cyan, phase = 1) { state.go(Screen.MAP) }
                BigButton("hangar", "Hangar", "Build and paint your ship", accent = Color(0xFF3D7BFF), phase = 2) { state.go(Screen.HANGAR) }
                BigButton(
                    "rift", "The Rift", if (Game.riftOpen) "Endless · best ${fmt(Game.riftBest)}" else "Beat the ${Campaign.SECTORS[0].boss.title} to open",
                    accent = Color(0xFFE040A0), phase = 3, enabled = Game.riftOpen,
                ) { if (Game.riftOpen) state.launch(null) else Audio.play(Snd.DENY, 0.7f) }
                BigButton(
                    "missions", "Missions & medals", if (missionsLeft > 0) "$missionsLeft left today" else "All done today",
                    accent = Color(0xFF2FBF71), phase = 4, badge = if (missionsLeft > 0) "$missionsLeft" else null,
                ) { state.go(Screen.MISSIONS) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SupplyButton(state: AppState) {
    val p = pulse(700)
    Row(
        Modifier.bob(3.dp, periodMs = 900).squishy {
            val s = Game.openSupply() ?: return@squishy
            Audio.play(Snd.COIN, 1f)
            Haptics.buzz(Haptics.Kind.SUCCESS)
            state.toast(Toast(System.nanoTime(), "crate", "Supply drop!", "Day ${s.streak} in a row", s.credits, s.cores))
        }.glass(RoundedCornerShape(50), lerpWhite(CREDIT, p * 0.5f), 0.9f).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph("crate", 22.dp, color = CREDIT)
        Text("Supply drop ready!", color = CREDIT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

fun logo(size: Int) = TextStyle(
    fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (size / 6).sp,
    shadow = Shadow(Color(0xFF22E3FF).copy(alpha = 0.6f), blurRadius = 24f),
)
