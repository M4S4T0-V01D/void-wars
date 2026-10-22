package dev.m4s4t0.voidwars.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.model.Currency
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.MEDALS
import dev.m4s4t0.voidwars.model.Missions
import dev.m4s4t0.voidwars.model.Stat
import dev.m4s4t0.voidui.SectionTitle
import dev.m4s4t0.voidui.Void

@Composable
fun MissionsScreen(state: AppState) {
    Column(Modifier.fillMaxSize().background(Void.bg.copy(alpha = 0.6f)).safe()) {
        GameBar("Missions & medals", { state.go(Screen.HOME) }, icon = "missions", subtitle = "${Game.medals.size} / ${MEDALS.size} medals") { WalletChip() }
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Today")
                Game.missions.forEach { m ->
                    val prog = m.progress(Game.stats)
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), if (m.done) Void.ok.copy(alpha = 0.6f) else Void.border).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Glyph(if (m.done) "check" else m.icon, 28.dp, color = if (m.done) Void.ok else Void.cyan)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(m.text, color = Void.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Bar(prog.toFloat() / m.goal, Modifier.weight(1f), 6.dp, color = if (m.done) Void.ok else null)
                                Text("${fmt(prog)}/${fmt(m.goal)}", color = Void.dim, fontSize = 11.sp)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Money(m.credits.toLong(), fontSize = 13.sp, dimmed = m.done)
                            if (m.cores > 0) Money(m.cores.toLong(), Currency.CORES, fontSize = 13.sp, dimmed = m.done)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Finish all three for", color = Void.dim, fontSize = 12.sp)
                    Money(Missions.ALL_BONUS_CORES.toLong(), Currency.CORES, fontSize = 12.sp, icon = 14.dp)
                    Text("· new missions every day", color = Void.dim, fontSize = 12.sp)
                }
                SectionTitle("Pilot record")
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    fun s(k: String) = fmt(Game.stats[k] ?: 0L)
                    InfoLine("crosshair", "${s(Stat.KILLS)} enemies · ${s(Stat.ROCKS)} meteors · ${s(Stat.BOSSES)} bosses", Void.text)
                    InfoLine("graze", "${s(Stat.GRAZES)} grazes · best chain x${s(Stat.BEST_COMBO)}", Void.text)
                    InfoLine("flag", "${s(Stat.CLEARS)} stages cleared · ${s(Stat.PERFECT)} perfect · ${s(Stat.RUNS)} flights", Void.text)
                    InfoLine("rift", "Rift best ${fmt(Game.riftBest)} · ${clock((Game.stats[Stat.RIFT_BEST_SECONDS] ?: 0L).toFloat())} survived", Void.text)
                    InfoLine("streak", "${s(Stat.STREAK)} day streak · ${clock((Game.stats[Stat.SECONDS] ?: 0L).toFloat() / 60f)} hours flown", Void.text)
                }
            }
            LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.weight(1.1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Medals") }
                items(MEDALS, key = { it.id }) { m ->
                    val got = m.id in Game.medals
                    val have = (Game.stats[m.stat] ?: 0L).coerceAtMost(m.goal)
                    Row(
                        Modifier.glass(RoundedCornerShape(14.dp), if (got) CREDIT.copy(alpha = 0.6f) else Void.border, if (got) 0.9f else 0.7f).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(if (got) CREDIT.copy(alpha = 0.18f) else Void.panelAlt),
                            contentAlignment = Alignment.Center,
                        ) { Glyph(m.icon, 22.dp, color = if (got) CREDIT else Void.dim, glow = got) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(m.title, color = if (got) Void.text else Void.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(m.desc, color = Void.dim, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
                            if (got) Money(m.cores.toLong(), Currency.CORES, fontSize = 11.sp, icon = 12.dp, dimmed = true)
                            else Bar(have.toFloat() / m.goal, Modifier.fillMaxWidth(), 4.dp)
                        }
                    }
                }
            }
        }
    }
}
