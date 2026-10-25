package dev.m4s4t0.voidwars.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.game.Station
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Currency
import dev.m4s4t0.voidwars.model.Deal
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.Hubs
import dev.m4s4t0.voidwars.model.Market
import dev.m4s4t0.voidwars.model.MissionSpec
import dev.m4s4t0.voidwars.model.Mode
import dev.m4s4t0.voidwars.model.Stories
import dev.m4s4t0.voidui.SectionTitle
import dev.m4s4t0.voidui.Void

// ---------------------------------------------------------------- the hub

/**
 * Over the hub: the left thumb flies the ship anywhere; the station list on the right sets the autopilot, and the
 * Dock button appears in reach of a station.
 */
@Composable
fun HubScreen(state: AppState) {
    val h = state.hub
    var chart by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().safe().padding(10.dp)) {
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon("home", size = 42.dp) { state.title() }
                LevelChip()
                WalletChip(shards = true)
            }
            // Where the operation is based; a tap opens the chart of hubs reached so far.
            val th = Game.hubTheme
            Row(
                Modifier.squishy { chart = true }.glass(RoundedCornerShape(12.dp), Color(th.glow), 0.6f).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Glyph("map", 18.dp, color = Color(th.glow), glow = false)
                Column {
                    Text(th.title, color = Void.text, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(th.place + " · ${Game.hubsOpen}/${Hubs.all.size}", color = Void.dim, fontSize = 10.sp)
                }
            }
        }
        // Where to go: a tap flies there by itself, a second tap stops.
        Column(
            Modifier.align(Alignment.TopEnd).width(190.dp).fillMaxHeight(0.78f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("STATIONS", color = Void.dim, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(start = 6.dp), style = hudStyle())
            Station.entries.forEach { s ->
                val open = s.mode?.let(Game::modeOpen) ?: (s != Station.RIFT || Game.riftOpen)
                val heading = h?.autopilot == s
                val c = Color(s.color)
                Row(
                    Modifier.fillMaxWidth().squishy { state.view.gl { space.autopilot(if (heading) null else s) } }
                        .glass(RoundedCornerShape(12.dp), if (heading) c else if (h?.near == s) c.copy(alpha = 0.6f) else Void.border, if (heading) 0.9f else 0.6f)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Glyph(s.icon, 18.dp, color = if (open) c else Void.dim, glow = heading)
                    Text(s.title, color = if (open) Void.text else Void.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                    val m = s.mode
                    when {
                        m != null && !open -> LevelTag(m.level, fontSize = 10.sp)
                        m != null && Game.chaptersDone(m) >= Stories.CHAPTERS -> Glyph("check", 14.dp, color = Void.ok, glow = false)
                        heading -> Glyph("next", 14.dp, color = c, glow = false)
                    }
                }
            }
        }
        val near = h?.near
        if (near != null) {
            val c = Color(near.color)
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).bob(4.dp)
                    .squishy { state.view.gl { space.dock() } }
                    .glass(RoundedCornerShape(50), c, 0.92f).padding(start = 14.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Glyph("dock", 28.dp, color = c)
                Column {
                    Text("Dock: ${near.title}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(near.blurb, color = Void.dim, fontSize = 11.sp)
                }
            }
        } else if (h != null && h.autopilot == null) {
            Text(
                "Drag on the left to fly anywhere · tap a station to set course",
                color = Void.dim, fontSize = 12.sp, style = hudStyle(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
        if (h != null) BoostButton(h.boost, Modifier.align(Alignment.BottomEnd)) { state.view.gl { space.boost() } }
        CalloutText(state.hubCallout, Modifier.align(Alignment.Center))
        if (chart) HubChart(state) { chart = false }
    }
}

/** Every hub along the campaign's road: the ones reached can be jumped back to, the rest say what opens them. */
@Composable
private fun HubChart(state: AppState, close: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(remember { MutableInteractionSource() }, null) { close() }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxHeight(0.92f).clickable(remember { MutableInteractionSource() }, null) {}.glass(RoundedCornerShape(18.dp), Void.border, 0.95f).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Galaxy chart", modifier = Modifier.weight(1f))
                RoundIcon("close", size = 36.dp) { close() }
            }
            Text("Beat a sector's boss to move the operation on to the next hub.", color = Void.dim, fontSize = 12.sp)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Hubs.all.forEachIndexed { i, th ->
                    val open = Game.hubOpen(i); val here = i == Game.hubAt
                    val c = Color(th.glow)
                    Row(
                        Modifier.fillMaxWidth().squishy(enabled = open && !here) { close(); state.jump(i) }
                            .glass(RoundedCornerShape(12.dp), if (here) c else if (open) c.copy(alpha = 0.45f) else Void.border, if (open) 0.8f else 0.5f)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(if (open) c else Void.dim.copy(alpha = 0.4f)))
                        Column(Modifier.weight(1f)) {
                            Text(if (open) th.title else "Uncharted", color = if (open) Void.text else Void.dim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (open) th.place else "Beat ${Campaign.SECTORS[th.sector].title} to reach it",
                                color = Void.dim, fontSize = 11.sp, maxLines = 1,
                            )
                        }
                        when {
                            here -> Text("YOU ARE HERE", color = c, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            open -> Glyph("next", 16.dp, color = c, glow = false)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- the black market

@Composable
fun MarketScreen(state: AppState) {
    Column(Modifier.fillMaxSize().background(Void.bg.copy(alpha = 0.6f)).safe()) {
        GameBar("Black market", { state.go(Screen.HOME) }, icon = "market", subtitle = "Void shards only. No refunds.") { WalletChip(shards = true) }
        val boosts = listOfNotNull(
            "Double XP: ${Game.xpBoost} runs".takeIf { Game.xpBoost > 0 },
            "Double credits: ${Game.creditBoost} runs".takeIf { Game.creditBoost > 0 },
            "Shield: ${Game.shieldBoost} runs".takeIf { Game.shieldBoost > 0 },
        )
        LazyVerticalGrid(
            GridCells.Adaptive(230.dp), Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    InfoLine("shard", "Earn shards from contracts, story chapters, mission modes and bosses.", SHARD)
                    if (boosts.isNotEmpty()) InfoLine("level", "Active: " + boosts.joinToString(" · "), Void.ok)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Staples") }
            items(Market.STAPLES, key = { it.id }) { DealCard(it) }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("The fence · today only") }
            if (Game.fence.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("The fence has nothing you don't already own. Come back tomorrow.", color = Void.dim, fontSize = 12.sp)
            }
            items(Game.fence.toList(), key = { it.id }) { DealCard(it) }
        }
    }
}

@Composable
private fun DealCard(d: Deal) {
    val owned = d.key != null && Game.has(d.key)
    val can = Game.canBuy(d)
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), if (can) SHARD.copy(alpha = 0.6f) else Void.border, 0.8f).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(SHARD.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Glyph(d.icon, 22.dp, color = SHARD, glow = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.title, color = Void.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(d.desc, color = Void.dim, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
        }
        when {
            owned -> Glyph("check", 20.dp, color = Void.ok, glow = false)
            Game.level < d.level -> LevelTag(d.level)
            else -> Row(
                Modifier.squishy(enabled = can, sound = false) {
                    if (Game.buyDeal(d)) { Audio.play(Snd.BUY); Haptics.buzz(Haptics.Kind.SUCCESS, 0.6f) } else Audio.play(Snd.DENY, 0.6f)
                }.glass(RoundedCornerShape(50), if (can) SHARD else Void.border, if (can) 0.95f else 0.5f).padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Money(d.shards.toLong(), Currency.SHARDS, fontSize = 13.sp, dimmed = !can) }
        }
    }
}

// ---------------------------------------------------------------- the contract board

@Composable
fun ContractsScreen(state: AppState) {
    val list = Game.contracts.toList()
    Column(Modifier.fillMaxSize().background(Void.bg.copy(alpha = 0.6f)).safe()) {
        GameBar(
            "Contract board", { state.go(Screen.HOME) }, icon = "contract",
            subtitle = "${list.count { it.id in Game.contractsDone }} / ${list.size} done · new jobs every day",
        ) { WalletChip(shards = true) }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No contracts yet: reach pilot level ${Mode.entries.minOf { it.level }} to open the first mission type.", color = Void.dim, fontSize = 14.sp)
            }
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(250.dp), Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            list.groupBy { it.spec.sector }.toSortedMap().forEach { (sector, jobs) ->
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("${sector + 1} · ${Campaign.SECTORS[sector].title}") }
                items(jobs, key = { it.id }) { c ->
                    val done = c.id in Game.contractsDone
                    val m = c.spec.mode
                    Column(
                        Modifier.fillMaxWidth().squishy(enabled = !done) { state.launch(c.spec) }
                            .glass(RoundedCornerShape(14.dp), if (done) Void.ok.copy(alpha = 0.6f) else Void.border, 0.8f).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Glyph(if (done) "check" else m.icon, 22.dp, color = if (done) Void.ok else Void.cyan, glow = false)
                            Column(Modifier.weight(1f)) {
                                Text(c.spec.title.substringBefore(" · "), color = Void.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${m.title}${if (m.open) " · open space" else ""}", color = Void.dim, fontSize = 10.sp)
                            }
                            if (!done) Glyph("play", 18.dp, color = Void.cyan, glow = false)
                        }
                        Text(c.spec.goalText, color = Void.dim, fontSize = 11.sp, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Money(c.credits.toLong(), fontSize = 12.sp, dimmed = done)
                            Money(c.shards.toLong(), Currency.SHARDS, fontSize = 12.sp, dimmed = done)
                            Text("+${c.xp} XP", color = Void.violet.copy(alpha = if (done) 0.5f else 1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- a mode's station

/** A mission type's post: its story line (each chapter unlocking the next, some unlocking gear) and quick missions. */
@Composable
fun ModeScreen(state: AppState) {
    val m = state.mode
    val open = Game.modeOpen(m)
    val chapters = Stories.all[m].orEmpty()
    val done = Game.chaptersDone(m)
    Column(Modifier.fillMaxSize().background(Void.bg.copy(alpha = 0.6f)).safe()) {
        GameBar(m.title, { state.go(Screen.HOME) }, icon = m.icon, subtitle = m.blurb + if (m.open) " · open space" else " · on the rails") { WalletChip(shards = true) }
        if (!open) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                Glyph("lock", 48.dp, color = Void.dim)
                Text("Opens at pilot level ${m.level}", color = Void.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("You're level ${Game.level}. Fly the campaign, the Rift or contracts to level up.", color = Void.dim, fontSize = 13.sp)
            }
            return@Column
        }
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The story.
            Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Story · $done / ${Stories.CHAPTERS}")
                chapters.forEach { c ->
                    val cleared = c.index < done
                    val can = Game.chapterOpen(c)
                    val accent = when { cleared -> Void.ok; can -> Void.cyan; else -> Void.border }
                    Row(
                        Modifier.fillMaxWidth().squishy(enabled = can) { state.launch(c.spec()) }
                            .glass(RoundedCornerShape(14.dp), accent.copy(alpha = if (can && !cleared) 0.9f else 0.6f), if (can) 0.85f else 0.55f).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            if (cleared) Glyph("check", 18.dp, color = Void.ok, glow = false)
                            else if (!can) Glyph("lock", 16.dp, color = Void.dim, glow = false)
                            else Text("${c.index + 1}", color = Void.cyan, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.title, color = if (can) Void.text else Void.dim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(if (can || cleared) c.text else "Beat chapter ${c.index} first.", color = Void.dim, fontSize = 11.sp, lineHeight = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Money(c.reward.credits.toLong(), fontSize = 11.sp, icon = 12.dp, dimmed = cleared)
                                if (c.reward.cores > 0) Money(c.reward.cores.toLong(), Currency.CORES, fontSize = 11.sp, icon = 12.dp, dimmed = cleared)
                                if (c.reward.shards > 0) Money(c.reward.shards.toLong(), Currency.SHARDS, fontSize = 11.sp, icon = 12.dp, dimmed = cleared)
                            }
                            c.unlock?.let { InfoLine(if (cleared) "check" else "lock", "Unlocks ${Game.itemTitle(it)}", if (cleared) Void.ok else SHARD) }
                        }
                        if (can && !cleared) Glyph("play", 20.dp, color = Void.cyan, glow = false)
                    }
                }
                val all = Game.storiesDone()
                InfoLine(
                    "title", "Finish all ten story lines for the title \"${Game.itemTitle(Stories.LAST_LIGHT)}\" · $all / ${Mode.entries.size}",
                    if (Game.has(Stories.LAST_LIGHT)) Void.ok else Void.dim,
                )
            }
            // Quick missions anywhere the pilot has reached.
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Quick mission")
                val sectors = Game.openSectors.coerceAtLeast(1)
                var sector by remember(m) { mutableIntStateOf(sectors - 1) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until sectors) {
                        val on = i == sector
                        Text(
                            "${i + 1}", color = if (on) Color.White else Void.dim, fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                            modifier = Modifier.size(34.dp).squishy { sector = i }.glass(CircleShape, if (on) Void.cyan else Void.border, if (on) 0.9f else 0.6f).padding(top = 7.dp),
                        )
                    }
                }
                val info = Campaign.SECTORS[sector]
                val spec = remember(m, sector) { MissionSpec.quick(m, sector, (m.ordinal * 977 + sector * 31 + Game.level)) }
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(info.title, color = Void.text, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(info.subtitle, color = Void.dim, fontSize = 11.sp)
                    InfoLine(m.icon, spec.goalText, Void.text)
                    val pay = MissionSpec.reward(spec, true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Money(pay.credits.toLong(), fontSize = 12.sp)
                        if (pay.shards > 0) Money(pay.shards.toLong(), Currency.SHARDS, fontSize = 12.sp)
                        Text("+${pay.xp} XP", color = Void.violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                ActionButton("Launch", Modifier.fillMaxWidth(), icon = "launch") {
                    state.launch(MissionSpec.quick(m, sector, (System.nanoTime() and 0xFFFFFF).toInt()))
                }
                val best = Game.best["mode:${m.name}"] ?: 0L
                if (best > 0) InfoLine("medal", "Best score ${fmt(best)}", CREDIT)
            }
        }
    }
}
