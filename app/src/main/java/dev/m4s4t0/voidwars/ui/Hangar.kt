package dev.m4s4t0.voidwars.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Decal
import dev.m4s4t0.voidwars.model.Finish
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.Loadout
import dev.m4s4t0.voidwars.model.MAX_UPGRADE
import dev.m4s4t0.voidwars.model.Paint
import dev.m4s4t0.voidwars.model.Part
import dev.m4s4t0.voidwars.model.Price
import dev.m4s4t0.voidwars.model.Primary
import dev.m4s4t0.voidwars.model.SWATCHES
import dev.m4s4t0.voidwars.model.ShipStats
import dev.m4s4t0.voidwars.model.Slot
import dev.m4s4t0.voidwars.model.Swatch
import dev.m4s4t0.voidwars.model.upgradeCost
import dev.m4s4t0.voidui.Void

private val SLOT_ICON = mapOf(
    Slot.HULL to "hull", Slot.WINGS to "wings", Slot.ENGINE to "engine",
    Slot.PRIMARY to "weapon", Slot.SUPPORT to "support", Slot.SPECIAL to "special",
)

/** Paint channels: the body colour, the trim (and decal), the glow of engines and lights. */
private val CHANNELS = listOf("Body", "Trim", "Glow")

@Composable
fun HangarScreen(state: AppState) {
    var tab by remember { mutableIntStateOf(0) } // 0..5 slots, 6 paint
    var part by remember { mutableStateOf<Part?>(null) }
    var channel by remember { mutableIntStateOf(0) }
    /** A paint item being tried on: a Swatch, Finish or Decal. */
    var paintPick by remember { mutableStateOf<Any?>(null) }

    val fitted = Game.loadout
    val tryPaint: Paint = when (val p = paintPick) {
        is Swatch -> when (channel) { 0 -> fitted.paint.copy(primary = p.id); 1 -> fitted.paint.copy(secondary = p.id); else -> fitted.paint.copy(glow = p.id) }
        is Finish -> fitted.paint.copy(finish = p)
        is Decal -> fitted.paint.copy(decal = p)
        else -> fitted.paint
    }
    val preview = (part?.let { fitted.with(it) } ?: fitted).copy(paint = tryPaint)
    LaunchedEffect(preview) { state.preview = preview }

    Row(Modifier.fillMaxSize().safe()) {
        // Left: the turntable shows through; the numbers sit underneath.
        Column(Modifier.weight(0.9f).fillMaxHeight()) {
            GameBar("Hangar", { state.go(Screen.HOME) }, icon = "hangar")
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    "Drag to spin", color = Void.dim.copy(alpha = 0.7f), fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                )
            }
            StatsPanel(Game.shipStats, preview.stats(Game::upgradeLevel), Modifier.padding(start = 12.dp, end = 6.dp, bottom = 12.dp).fillMaxWidth())
        }
        // Right: the parts.
        Column(Modifier.weight(1.1f).fillMaxHeight().padding(end = 12.dp, bottom = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { WalletChip() }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((0..6).toList()) { i ->
                    val on = tab == i
                    val label = if (i < 6) Slot.entries[i].title else "Paint"
                    val icon = if (i < 6) SLOT_ICON.getValue(Slot.entries[i]) else "paint"
                    Row(
                        Modifier.squishy { tab = i; part = null; paintPick = null }
                            .glass(RoundedCornerShape(50), if (on) Void.cyan else Void.border, if (on) 0.95f else 0.75f)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Glyph(icon, 16.dp, color = if (on) Void.cyan else Void.dim, glow = on)
                        Text(label, color = if (on) Void.text else Void.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().glass(RoundedCornerShape(18.dp), alpha = 0.8f)) {
                if (tab < 6) PartGrid(Slot.entries[tab], part, fitted) { part = it }
                else PaintPanel(channel, { channel = it; paintPick = null }, paintPick, fitted.paint) { paintPick = it }
            }
            if (tab < 6) PartActions(part ?: fitted.part(Slot.entries[tab])) { part = null }
            else PaintActions(paintPick, channel) { paintPick = null }
        }
    }
}

@Composable
private fun PartGrid(slot: Slot, picked: Part?, fitted: Loadout, onPick: (Part) -> Unit) {
    val parts = Loadout.ALL_PARTS.getValue(slot)
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(parts, key = { it.key }) { p ->
            val owned = Game.owns(p)
            val equipped = fitted.part(slot) == p
            val sel = (picked ?: fitted.part(slot)) == p
            val locked = Game.level < p.price.level
            Column(
                Modifier.squishy { onPick(p) }.clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Void.cyan.copy(alpha = 0.12f) else Void.panelAlt.copy(alpha = 0.6f))
                    .border(if (sel) 2.dp else 1.dp, if (sel) Void.cyan else Void.border, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Glyph(if (locked) "lock" else SLOT_ICON.getValue(slot), 20.dp, color = if (owned) Void.cyan else Void.dim, glow = owned)
                    Text(p.title, color = if (locked) Void.dim else Void.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Text(p.desc, color = Void.dim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp, modifier = Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        equipped -> Text("Fitted", color = Void.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        fitted.backup == p -> Text("Backup", color = Void.cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        owned ->Text("Owned", color = Void.dim, fontSize = 12.sp)
                        else -> PriceTag(p.price)
                    }
                    if (owned && slot.upgradable && upgradeCost(p, 1) != null) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        Pips(Game.upgradeLevel(p))
                    }
                }
            }
        }
    }
}

@Composable
private fun Pips(level: Int, size: androidx.compose.ui.unit.Dp = 6.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..MAX_UPGRADE) Box(Modifier.size(size).clip(CircleShape).background(if (i <= level) Void.cyan else Void.border))
    }
}

/** Buy, fit or upgrade the part in view. */
@Composable
private fun PartActions(p: Part, onFitted: () -> Unit) {
    val owned = Game.owns(p)
    val equipped = Game.loadout.part(p.slot) == p
    val lvl = Game.upgradeLevel(p)
    val cost = if (owned) upgradeCost(p, lvl) else null
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.title, color = Void.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (owned && p.slot.upgradable && upgradeCost(p, 1) != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Mk $lvl", color = Void.cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Pips(lvl, 7.dp)
            } else Text(p.desc, color = Void.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // A second gun to swap to in flight.
        if (p is Primary && owned && !equipped) {
            val isBackup = Game.loadout.backup == p
            ActionButton(if (isBackup) "Unfit backup" else "Fit as backup", icon = "swap", primary = false) {
                Game.setBackup(if (isBackup) null else p); Audio.play(Snd.POWER, 0.6f)
            }
        }
        when {
            !owned -> BuyButton(p.price) {
                if (Game.buy(p.key, p.price)) { Game.equip(p); onFitted() }
            }
            !equipped -> ActionButton("Fit", icon = "check") { Game.equip(p); Audio.play(Snd.POWER, 0.7f); onFitted() }
            cost != null -> ActionButton("Upgrade  ${fmt(cost)}", icon = "upgrade", enabled = Game.credits >= cost) {
                if (Game.upgrade(p)) { Audio.play(Snd.BUY); Haptics.buzz(Haptics.Kind.SUCCESS, 0.6f) } else Audio.play(Snd.DENY, 0.7f)
            }
            p.slot.upgradable && upgradeCost(p, 1) != null -> Text("Fully upgraded", color = Void.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            else -> Text("Fitted", color = Void.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BuyButton(price: Price, onBought: () -> Unit) {
    if (Game.level < price.level) {
        ActionButton("Pilot level ${price.level}", icon = "lock", enabled = false) {}
        return
    }
    val ok = Game.canAfford(price)
    Row(
        Modifier.squishy(sound = false) {
            if (ok) { Audio.play(Snd.BUY); Haptics.buzz(Haptics.Kind.SUCCESS, 0.7f); onBought() } else { Audio.play(Snd.DENY, 0.7f); Haptics.buzz(Haptics.Kind.TICK) }
        }.clip(RoundedCornerShape(50)).background(if (ok) Void.violet else Void.panelAlt)
            .border(1.dp, if (ok) Color.White.copy(alpha = 0.3f) else Void.border, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Buy", color = if (ok) Color.White else Void.dim, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Money(price.amount.toLong(), price.currency, fontSize = 15.sp, icon = 17.dp, dimmed = !ok)
    }
}

// ---------------------------------------------------------------- paint

@Composable
private fun PaintPanel(channel: Int, onChannel: (Int) -> Unit, pick: Any?, paint: Paint, onPick: (Any) -> Unit) {
    val current = when (channel) { 0 -> paint.primary; 1 -> paint.secondary; else -> paint.glow }
    LazyVerticalGrid(GridCells.Adaptive(46.dp), Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CHANNELS.forEachIndexed { i, name ->
                    val id = when (i) { 0 -> paint.primary; 1 -> paint.secondary; else -> paint.glow }
                    Row(
                        Modifier.squishy { onChannel(i) }.glass(RoundedCornerShape(50), if (i == channel) Void.cyan else Void.border, 0.9f).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(argbColor(SWATCHES.first { it.id == id }.argb)).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape))
                        Text(name, color = if (i == channel) Void.text else Void.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        items(SWATCHES, key = { it.key }) { s ->
            val owned = Game.owns(s)
            val on = if (pick is Swatch) pick == s else s.id == current
            Box(
                Modifier.size(46.dp).squishy { onPick(s) }.clip(CircleShape).background(argbColor(s.argb))
                    .border(if (on) 3.dp else 1.dp, if (on) Void.cyan else Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!owned) Box(Modifier.size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Glyph(if (Game.level < s.price.level) "lock" else if (s.price.currency == dev.m4s4t0.voidwars.model.Currency.CORES) "core" else "credit", 12.dp, color = Color.White, glow = false)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Finish", color = Void.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChipRow(Finish.entries, { it.title }, { it.price }, { Game.owns(it) }, (pick as? Finish) ?: paint.finish, onPick)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Pattern", color = Void.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChipRow(Decal.entries, { it.title }, { it.price }, { Game.owns(it) }, (pick as? Decal) ?: paint.decal, onPick)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T : Any> ChipRow(all: List<T>, title: (T) -> String, price: (T) -> Price, owns: (T) -> Boolean, selected: T, onPick: (Any) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (f in all) {
            val on = f == selected
            Row(
                Modifier.squishy { onPick(f) }.glass(RoundedCornerShape(50), if (on) Void.cyan else Void.border, if (on) 0.95f else 0.7f).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title(f), color = if (on) Void.text else Void.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (!owns(f)) PriceTag(price(f), fontSize = 11.sp)
            }
        }
    }
}

/** Applies an owned paint item straight away; offers to buy one that isn't. */
@Composable
private fun PaintActions(pick: Any?, channel: Int, onDone: () -> Unit) {
    val (key, price, title) = when (pick) {
        is Swatch -> Triple(pick.key, pick.price, pick.title)
        is Finish -> Triple(pick.key, pick.price, pick.title + " finish")
        is Decal -> Triple(pick.key, pick.price, pick.title)
        else -> Triple(null, null, null)
    }
    fun apply() {
        val p = Game.loadout.paint
        Game.paint(
            when (pick) {
                is Swatch -> when (channel) { 0 -> p.copy(primary = pick.id); 1 -> p.copy(secondary = pick.id); else -> p.copy(glow = pick.id) }
                is Finish -> p.copy(finish = pick)
                is Decal -> p.copy(decal = pick)
                else -> p
            },
        )
        onDone()
    }
    // Owned things are put on as soon as they're tapped.
    LaunchedEffect(pick, channel) { if (key != null && price != null && Game.owns(key, price)) apply() }
    Row(
        Modifier.fillMaxWidth().height(56.dp).glass(RoundedCornerShape(18.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (key == null || price == null || Game.owns(key, price)) {
            Text("Tap a colour, finish or pattern to try it on", color = Void.dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        } else {
            Text(title ?: "", color = Void.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            ActionButton("Cancel", primary = false) { onDone() }
            BuyButton(price) { if (Game.buy(key, price)) apply() }
        }
    }
}

// ---------------------------------------------------------------- stats

@Composable
private fun StatsPanel(now: ShipStats, next: ShipStats, modifier: Modifier) {
    data class Row3(val name: String, val icon: String, val a: Float, val b: Float, val max: Float, val lowerBetter: Boolean = false, val fmt: (Float) -> String)
    val rows = listOf(
        Row3("Hull", "shield", now.maxHp.toFloat(), next.maxHp.toFloat(), 320f) { it.toInt().toString() },
        Row3("Speed", "speed", now.speed, next.speed, 40f) { "%.0f".format(it) },
        Row3("Damage", "fire", now.damage, next.damage, 1.5f) { "${(it * 100).toInt()}%" },
        Row3("Fire rate", "weapon", now.fireRate, next.fireRate, 1.4f) { "${(it * 100).toInt()}%" },
        Row3("Graze", "graze", now.graze, next.graze, 2.4f) { "%.1f".format(it) },
        Row3("Magnet", "magnet", now.magnet, next.magnet, 5f) { "%.1f".format(it) },
        Row3("Charge", "bolt", now.charge, next.charge, 2f) { "${(it * 100).toInt()}%" },
        Row3("Hitbox", "crosshair", now.hitbox, next.hitbox, 0.3f, lowerBetter = true) { "%.2f".format(it) },
    )
    Column(modifier.glass(RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { r ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Glyph(r.icon, 13.dp, color = Void.dim, glow = false)
                        Text(r.name, color = Void.dim, fontSize = 11.sp, modifier = Modifier.width(52.dp), maxLines = 1)
                        val better = if (r.lowerBetter) r.b < r.a else r.b > r.a
                        val worse = if (r.lowerBetter) r.b > r.a else r.b < r.a
                        Bar(r.b / r.max, Modifier.weight(1f), 5.dp, color = when { better -> Void.ok; worse -> Void.bad; else -> null })
                        Text(
                            r.fmt(r.b), color = when { better -> Void.ok; worse -> Void.bad; else -> Void.text },
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp), maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
