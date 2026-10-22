package dev.m4s4t0.voidwars.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Currency
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.Stage
import dev.m4s4t0.voidui.Void

@Composable
fun MapScreen(state: AppState) {
    val start = state.mapStage ?: Game.nextStage()
    var sector by remember { mutableIntStateOf(start.sector) }
    val selected = state.mapStage?.takeIf { it.sector == sector }
        ?: (0 until Campaign.STAGES).map { Stage(sector, it) }.let { all -> all.firstOrNull { Game.unlocked(it) && (Game.stars[it.id] ?: 0) == 0 } ?: all.lastOrNull { Game.unlocked(it) } ?: all.first() }
    val sec = Campaign.SECTORS[sector]
    Column(Modifier.fillMaxSize().background(Void.bg.copy(alpha = 0.55f)).safe()) {
        GameBar("Campaign", { state.go(Screen.HOME) }, icon = "campaign", subtitle = "${Campaign.SECTORS.size} sectors · ${Campaign.allStages.size} stages") { WalletChip() }
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Sectors.
            Column(Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Campaign.SECTORS.forEachIndexed { i, s ->
                    val open = Game.sectorOpen(i)
                    val on = i == sector
                    Row(
                        Modifier.fillMaxWidth().squishy { sector = i; state.mapStage = null }
                            .glass(RoundedCornerShape(16.dp), if (on) Void.cyan else Void.border, if (on) 0.92f else 0.75f)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(argbColor(s.nebulaA), argbColor(s.nebulaB)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (open) Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            else Glyph("lock", 16.dp, color = Color.White, glow = false)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = if (open) Void.text else Void.dim, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (open) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Glyph("star", 12.dp, color = CREDIT, glow = false)
                                Text("${Game.sectorStars(i)}/${Campaign.STAGES * 3}", color = Void.dim, fontSize = 11.sp)
                            } else Text(if (Game.level < s.gate) "Pilot level ${s.gate}" else "Beat sector $i", color = Void.dim, fontSize = 11.sp)
                        }
                    }
                }
            }
            // The sector's route.
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(argbColor(sec.nebulaA).copy(alpha = 0.45f), argbColor(sec.nebulaB).copy(alpha = 0.3f))))
                    .border(1.dp, Void.border, RoundedCornerShape(20.dp)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(sec.title, color = Void.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(sec.subtitle, color = Void.text.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Route(sector, selected) { state.mapStage = it; Audio.play(Snd.TAP, 0.6f) }
            }
            StageCard(state, selected, Modifier.width(230.dp).fillMaxHeight())
        }
    }
}

/** Six stops on a winding line, the boss's the biggest. */
@Composable
private fun Route(sector: Int, selected: Stage, onPick: (Stage) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 40.dp, bottom = 10.dp, start = 14.dp, end = 14.dp)) {
        val w = maxWidth; val h = maxHeight
        val pts = (0 until Campaign.STAGES).map { i ->
            val fx = 0.08f + 0.84f * i / (Campaign.STAGES - 1)
            val fy = if (i == Campaign.STAGES - 1) 0.45f else if (i % 2 == 0) 0.62f else 0.3f
            fx to fy
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until pts.size - 1) {
                val a = pts[i]; val b = pts[i + 1]
                val lit = Game.unlocked(Stage(sector, i + 1))
                drawLine(
                    if (lit) Void.cyan.copy(alpha = 0.8f) else Void.dim.copy(alpha = 0.35f),
                    Offset(a.first * size.width, a.second * size.height), Offset(b.first * size.width, b.second * size.height),
                    strokeWidth = 3.dp.toPx(), pathEffect = if (lit) null else dash,
                )
            }
        }
        pts.forEachIndexed { i, (fx, fy) ->
            val st = Stage(sector, i)
            val node = if (st.isBoss) 64.dp else 46.dp
            val open = Game.unlocked(st)
            val stars = Game.stars[st.id] ?: 0
            val on = st == selected
            Column(
                Modifier.offset(w * fx - 40.dp, h * fy - node / 2).width(80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val p = if (on) pulse(900) else 0f
                Box(
                    Modifier.size(node).then(if (open && stars == 0) Modifier.bob(3.dp, i) else Modifier).squishy(sound = false) { onPick(st) }
                        .clip(CircleShape)
                        .background(
                            when {
                                !open -> Brush.linearGradient(listOf(Void.panelAlt, Void.panel))
                                st.isBoss -> Brush.linearGradient(listOf(Void.bad, darken(Void.bad, 0.4f)))
                                else -> Brush.linearGradient(listOf(Void.violet, Void.cyan))
                            },
                        )
                        .border(if (on) 3.dp else 1.5.dp, if (on) lerpWhite(Void.cyan, 0.4f + p * 0.6f) else Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        !open -> Glyph("lock", node * 0.45f, color = Void.dim, glow = false)
                        st.isBoss -> Glyph("skull", node * 0.55f, color = Color.White)
                        else -> Text(st.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (open) Stars(stars, size = 14.dp)
            }
        }
    }
}

@Composable
private fun StageCard(state: AppState, s: Stage, modifier: Modifier) {
    val sec = Campaign.SECTORS[s.sector]
    val open = Game.unlocked(s)
    val stars = Game.stars[s.id] ?: 0
    val reward = Campaign.reward(s, 3, stars == 0, stars < 3)
    Column(modifier.glass(RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stage ${s.title}", color = Void.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            when {
                s.isBoss -> "Boss: ${sec.boss.title}"
                s.hasElite -> "A carrier is out there"
                else -> sec.subtitle
            },
            color = if (s.isBoss) Void.bad else Void.cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        if (s.isBoss) Text(sec.boss.subtitle, color = Void.dim, fontSize = 12.sp)
        Stars(stars, size = 22.dp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            InfoLine("star", "★ clear · ★★ keep 60% hull · ★★★ and down 80%")
            Game.best[s.id]?.let { InfoLine("trophy", "Best ${fmt(it)}") }
            Text(if (stars == 0) "First clear pays double" else "Up to", color = Void.dim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Money(reward.credits.toLong(), fontSize = 14.sp)
                if (reward.cores > 0) Money(reward.cores.toLong(), Currency.CORES, fontSize = 14.sp)
                Text("+${reward.xp} XP", color = Void.violet, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (open) ActionButton(if (stars > 0) "Fly again" else "Launch", Modifier.fillMaxWidth(), icon = "launch", accent = if (s.isBoss) Void.bad else Void.violet) { state.mapStage = s; state.launch(s) }
        else {
            val gate = !Game.sectorOpen(s.sector) && Game.level < sec.gate
            Text(if (gate) "Reach pilot level ${sec.gate} to enter ${sec.title}" else "Clear the stage before it first", color = Void.dim, fontSize = 12.sp)
            ActionButton("Locked", Modifier.fillMaxWidth(), icon = "lock", enabled = false) {}
        }
    }
}

@Composable
fun InfoLine(icon: String, text: String, color: Color = Void.dim) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, 14.dp, color = color, glow = false)
        Text(text, color = color, fontSize = 12.sp)
    }
}
