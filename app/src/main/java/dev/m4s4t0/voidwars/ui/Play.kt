package dev.m4s4t0.voidwars.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import dev.m4s4t0.voidwars.game.Hud
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Currency
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.Hubs
import dev.m4s4t0.voidwars.model.Levels
import dev.m4s4t0.voidwars.model.Outcome
import dev.m4s4t0.voidwars.model.MissionSpec
import dev.m4s4t0.voidwars.model.Stories
import dev.m4s4t0.voidui.Void
import kotlinx.coroutines.delay

@Composable
fun PlayScreen(state: AppState) {
    val run = state.run ?: return
    val h = run.hud
    Box(Modifier.fillMaxSize()) {
        if (run.outcome == null) {
            Box(Modifier.fillMaxSize().safe().padding(10.dp)) {
                if (h != null) {
                    // Hull and chain, top left.
                    Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val low = h.hull < 0.3f
                        val blink = if (low) pulse(300) else 0f
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Glyph("shield", 18.dp, color = if (low) Void.bad else Void.cyan)
                            Bar(h.hull, Modifier.width(150.dp), 9.dp, color = if (low) androidx.compose.ui.graphics.lerp(Void.bad, Color.White, blink * 0.4f) else null)
                        }
                        if (h.chain >= 3) {
                            val pop = remember { Animatable(1f) }
                            LaunchedEffect(h.chain / 10) { pop.snapTo(1.25f); pop.animateTo(1f, tween(220)) }
                            Row(Modifier.scale(pop.value), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("x${h.chain}", color = chainColor(h.chain), fontSize = 22.sp, fontWeight = FontWeight.Black, style = hudStyle())
                                Text("chain · ${"%.1f".format(h.mult)}× score", color = Void.text.copy(alpha = 0.75f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                    }
                    // Progress or the boss, top centre.
                    Column(Modifier.align(Alignment.TopCenter).width(260.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (h.boss != null) {
                            Text(h.boss.title.uppercase(), color = Void.bad, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = hudStyle())
                            Bar(h.bossHp, Modifier.fillMaxWidth(), 8.dp, color = Void.bad)
                        } else if (run.stage != null) {
                            Text("STAGE ${run.stage.title}", color = Void.text.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, style = hudStyle())
                            Bar(h.progress, Modifier.fillMaxWidth(0.7f), 4.dp)
                        } else {
                            Text("RIFT · ${clock(h.seconds)}", color = Color(0xFFFF7AD0), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = hudStyle())
                        }
                    }
                    // Score, credits and pause, top right.
                    Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt(h.score), color = Void.text, fontSize = 20.sp, fontWeight = FontWeight.Black, style = hudStyle())
                            Money(h.credits.toLong(), fontSize = 13.sp, icon = 14.dp)
                        }
                        RoundIcon("pause", size = 42.dp) { state.pause(true) }
                    }
                    // Guns, the special and the trigger, under the right thumb.
                    Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GunPanel(h) { state.view.gl { world.swap() } }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            SpecialButton(h.charge, h.specialOn, Modifier) {
                                if (h.charge >= 1f && h.specialOn <= 0f) state.view.gl { world.special() }
                            }
                            FireButton(state, h.crate > 0f) { on -> state.view.gl { world.trigger = on } }
                        }
                    }
                }
                CalloutText(run.callout, Modifier.align(Alignment.Center))
            }
            BossBanner(run)
        }
        if (run.paused) PauseOverlay(state)
        if (run.down) DownOverlay(state, run)
        run.outcome?.let { Debrief(state, it) }
    }
}

/** An open-space mission: the left thumb steers, the right holds the trigger, boosts and fires the special. */
@Composable
fun SpaceScreen(state: AppState) {
    val run = state.run ?: return
    val spec = run.spec ?: return
    val h = run.space
    Box(Modifier.fillMaxSize()) {
        if (run.outcome == null) {
            Box(Modifier.fillMaxSize().safe().padding(10.dp)) {
                if (h != null) {
                    // Hull and the ally's hull, top left.
                    Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val low = h.hull < 0.3f
                        val blink = if (low) pulse(300) else 0f
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Glyph("shield", 18.dp, color = if (low) Void.bad else Void.cyan)
                            Bar(h.hull, Modifier.width(150.dp), 9.dp, color = if (low) androidx.compose.ui.graphics.lerp(Void.bad, Color.White, blink * 0.4f) else null)
                        }
                        if (h.ally >= 0f) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Glyph(spec.mode.icon, 18.dp, color = if (h.ally < 0.3f) Void.bad else Void.ok)
                            Bar(h.ally, Modifier.width(120.dp), 7.dp, color = if (h.ally < 0.3f) Void.bad else Void.ok)
                        }
                        Text("${plural(h.kills, "kill")} · ${clock(h.seconds)}", color = Void.dim, fontSize = 11.sp, style = hudStyle())
                    }
                    // The objective, top centre.
                    Column(Modifier.align(Alignment.TopCenter).width(280.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(spec.title.uppercase(), color = Void.text.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, maxLines = 1, style = hudStyle())
                        val done = if (spec.mode.timed) clock(h.objective.toFloat()) + " / " + clock(h.goal.toFloat()) else "${fmt(h.objective)} / ${fmt(h.goal)}"
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Glyph(spec.mode.icon, 16.dp, color = Void.cyan)
                            Text(done, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, style = hudStyle())
                        }
                        Bar(if (h.goal > 0) h.objective.toFloat() / h.goal else 0f, Modifier.fillMaxWidth(0.7f), 4.dp)
                        if (h.timeLeft >= 0f) Text(clock(h.timeLeft), color = if (h.timeLeft < 10f) Void.bad else CREDIT, fontSize = 14.sp, fontWeight = FontWeight.Black, style = hudStyle())
                    }
                    // Score, credits and pause, top right.
                    Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt(h.score), color = Void.text, fontSize = 20.sp, fontWeight = FontWeight.Black, style = hudStyle())
                            Money(h.credits.toLong(), fontSize = 13.sp, icon = 14.dp)
                        }
                        RoundIcon("pause", size = 42.dp) { state.pause(true) }
                    }
                    // The trigger, boost and the special, under the right thumb.
                    Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SpecialButton(h.charge, h.specialOn, Modifier) {
                                if (h.charge >= 1f && h.specialOn <= 0f) state.view.gl { space.special() }
                            }
                            BoostButton(h.boost) { state.view.gl { space.boost() } }
                        }
                        FireButton(state, false) { on -> state.view.gl { space.trigger = on } }
                    }
                    Text(
                        spec.goalText, color = Void.dim, fontSize = 11.sp, style = hudStyle(),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    )
                }
                CalloutText(run.callout, Modifier.align(Alignment.Center))
            }
            BossBanner(run)
        }
        if (run.paused) PauseOverlay(state)
        if (run.down) DownOverlay(state, run)
        run.outcome?.let { Debrief(state, it) }
    }
}

/** A burst of speed; the ring is the boost meter. */
@Composable
internal fun BoostButton(meter: Float, modifier: Modifier = Modifier, onBoost: () -> Unit) {
    val ready = meter >= 0.3f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(92.dp).squishy(enabled = ready, sound = false) { Haptics.buzz(Haptics.Kind.CLICK, 0.4f); onBoost() }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 5.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.45f), size.minDimension / 2 - sw / 2)
                drawCircle(Color.White.copy(alpha = 0.15f), size.minDimension / 2 - sw / 2, style = Stroke(sw))
                drawArc(
                    CREDIT, -90f, 360f * meter.coerceIn(0f, 1f), false,
                    topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            Glyph("boost", 38.dp, color = if (ready) Color.White else Void.dim, glow = ready)
        }
        Text("Boost", color = if (ready) CREDIT else Void.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = hudStyle())
    }
}

private fun chainColor(n: Int) = when {
    n >= 100 -> Color(0xFFFF4FB8)
    n >= 50 -> Color(0xFFFFC23D)
    n >= 25 -> Color(0xFF22E3FF)
    else -> Color.White
}

@Composable
internal fun hudStyle() = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 6f))

/** The big button: a ring that fills as it charges, and pulses when it's ready. */
@Composable
private fun SpecialButton(charge: Float, on: Float, modifier: Modifier, onFire: () -> Unit) {
    val ready = charge >= 1f && on <= 0f
    val p = if (ready) pulse(500) else 0f
    val name = Game.loadout.special.title
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(78.dp).scale(1f + p * 0.06f).squishy(enabled = ready, sound = false, onClick = onFire),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 5.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.45f), size.minDimension / 2 - sw / 2)
                drawCircle(Color.White.copy(alpha = 0.15f), size.minDimension / 2 - sw / 2, style = Stroke(sw))
                val f = if (on > 0f) on else charge.coerceIn(0f, 1f)
                drawArc(
                    Brush.sweepGradient(listOf(Void.violet, Void.cyan, Void.violet)), -90f, 360f * f, false,
                    topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round),
                )
                if (ready) drawCircle(Void.cyan.copy(alpha = 0.18f + p * 0.2f), size.minDimension / 2 - sw)
            }
            Glyph("special", 34.dp, color = if (ready) Color.White else Void.dim, glow = ready)
        }
        Text(if (ready) "$name!" else name, color = if (ready) Void.cyan else Void.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = hudStyle())
    }
}

/** Hold to fire. Takes its own finger, so the other one keeps steering. */
@Composable
private fun FireButton(state: AppState, crate: Boolean, set: (Boolean) -> Unit) {
    var held by remember { mutableStateOf(false) }
    val accent = if (crate) CRATE else Void.cyan
    fun trigger(on: Boolean) {
        if (held == on) return
        held = on
        set(on)
        if (on) Haptics.buzz(Haptics.Kind.CLICK, 0.3f)
    }
    DisposableEffect(Unit) { onDispose { set(false) } }
    Box(
        Modifier.size(104.dp).scale(if (held) 0.93f else 1f).pointerInput(Unit) {
            awaitEachGesture {
                try {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    trigger(true)
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        if (e.changes.none { it.pressed }) break
                    }
                } finally {
                    trigger(false)
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Color.Black.copy(alpha = 0.45f), r - 2.dp.toPx())
            if (held) drawCircle(accent.copy(alpha = 0.28f), r - 2.dp.toPx())
            drawCircle(accent.copy(alpha = if (held) 0.95f else 0.5f), r - 3.dp.toPx(), style = Stroke(4.dp.toPx()))
        }
        Glyph("crosshair", 44.dp, color = if (held) Color.White else accent, glow = held)
    }
}

/** The gun firing now, a crate gun's time left, and the swap to the backup. */
@Composable
private fun GunPanel(h: Hud, onSwap: () -> Unit) {
    val crate = h.crate > 0f
    val accent = if (crate) CRATE else Void.cyan
    Row(
        Modifier.glass(RoundedCornerShape(50), if (crate) CRATE.copy(alpha = 0.7f) else Void.border, 0.6f).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph("weapon", 16.dp, color = accent)
        Column(Modifier.widthIn(min = 90.dp)) {
            Text(h.gun.title, color = Void.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (crate) Bar(h.crate, Modifier.width(90.dp).padding(top = 2.dp), 3.dp, color = CRATE)
            else if (h.swapTo != null) Text("swap: ${h.swapTo.title}", color = Void.dim, fontSize = 10.sp, maxLines = 1)
        }
        if (h.swapTo != null) RoundIcon("swap", size = 36.dp, tint = accent) { onSwap() }
        else Spacer(Modifier.width(4.dp))
    }
}

private val CRATE = Color(0xFFFF7A1F)

@Composable
internal fun CalloutText(c: Callout?, modifier: Modifier) {
    if (c == null) return
    val a = remember(c.id) { Animatable(0f) }
    LaunchedEffect(c.id) { a.animateTo(1f, tween(1400)) }
    val t = a.value
    Text(
        c.text, color = if (c.big) Void.cyan else Color.White, fontSize = if (c.big) 34.sp else 18.sp, fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center, style = hudStyle(),
        modifier = modifier.padding(bottom = 120.dp).graphicsLayer {
            alpha = if (t < 0.15f) t / 0.15f else (1f - (t - 0.6f).coerceAtLeast(0f) / 0.4f)
            val s = if (t < 0.15f) 0.7f + t * 2f else 1f + (t - 0.15f) * 0.08f
            scaleX = s; scaleY = s
            translationY = -t * 40f
        },
    )
}

@Composable
private fun BossBanner(run: RunState) {
    val b = run.bossWarning
    LaunchedEffect(b) { if (b != null) { delay(3200); run.bossWarning = null } }
    AnimatedVisibility(b != null, Modifier.fillMaxSize(), enter = fadeIn() + scaleIn(initialScale = 1.3f), exit = fadeOut()) {
        val boss = b ?: return@AnimatedVisibility
        val p = pulse(250)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Void.bad.copy(alpha = 0.35f), Color.Transparent))).padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Glyph("warning", 30.dp, color = lerpWhite(Void.bad, p * 0.5f))
                    Text("WARNING", color = lerpWhite(Void.bad, p * 0.5f), fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp, style = hudStyle())
                    Glyph("warning", 30.dp, color = lerpWhite(Void.bad, p * 0.5f))
                }
                Text(boss.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, style = hudStyle())
                Text(boss.subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, style = hudStyle())
            }
        }
    }
}

@Composable
private fun Scrim(content: @Composable BoxScope.() -> Unit) {
    // Swallows touches so the ship doesn't move under a dialog.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .then(Modifier.clickableNoRipple()),
        contentAlignment = Alignment.Center, content = content,
    )
}

@Composable
private fun Modifier.clickableNoRipple(): Modifier {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return clickable(src, null) {}
}

@Composable
private fun PauseOverlay(state: AppState) {
    val run = state.run ?: return
    Scrim {
        Column(
            Modifier.widthIn(max = 340.dp).glass(RoundedCornerShape(24.dp), alpha = 0.95f).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Paused", color = Void.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(
                run.spec?.let { "${it.title} · ${it.sectorInfo.title}" }
                    ?: run.stage?.let { "Stage ${it.title} · ${Campaign.SECTORS[it.sector].title}" } ?: "The Rift",
                color = Void.dim, fontSize = 13.sp,
            )
            ActionButton("Resume", Modifier.fillMaxWidth(), icon = "play") { state.pause(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallToggle("music", "Music", Game.music) { Game.music = it; Game.saveSettings() }
                SmallToggle("sound", "Sounds", Game.sounds) { Game.sounds = it; Game.saveSettings() }
            }
            ActionButton("Abandon run", Modifier.fillMaxWidth(), icon = "flag", primary = false) { state.giveUp() }
            Text("Abandoning keeps the credits you picked up.", color = Void.dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SmallToggle(icon: String, label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(
        Modifier.squishy { set(!on) }.glass(RoundedCornerShape(50), if (on) Void.cyan else Void.border, 0.9f).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph(icon, 16.dp, color = if (on) Void.cyan else Void.dim, glow = false)
        Text("$label ${if (on) "on" else "off"}", color = if (on) Void.text else Void.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Shot down: a core buys one more go, or the run ends. Gives up by itself after a few seconds. */
@Composable
private fun DownOverlay(state: AppState, run: RunState) {
    var left by remember { mutableIntStateOf(8) }
    LaunchedEffect(Unit) {
        Haptics.buzz(Haptics.Kind.BIG_BOOM)
        while (left > 0) { delay(1000); left-- }
        state.giveUp()
    }
    val can = run.canRevive && Game.cores >= 1
    Scrim {
        Column(
            Modifier.widthIn(max = 360.dp).glass(RoundedCornerShape(24.dp), Void.bad.copy(alpha = 0.7f), 0.95f).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Shot down!", color = Void.bad, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                if (run.canRevive) "Spend a core to rebuild the ship and keep your chain alive." else "One revive a run. Bank what you've got.",
                color = Void.dim, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            if (run.canRevive) {
                Row(
                    Modifier.fillMaxWidth().squishy(enabled = can, sound = false) { state.revive() }.clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(listOf(CORE.copy(alpha = if (can) 1f else 0.3f), Void.cyan.copy(alpha = if (can) 1f else 0.3f))))
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph("repair", 20.dp, color = Color.White, glow = false)
                    Text("Revive", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Money(1, Currency.CORES, fontSize = 15.sp, icon = 16.dp)
                }
                if (!can) Text("You have no cores. Earn them from bosses, missions and medals.", color = Void.dim, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            ActionButton("End run ($left)", Modifier.fillMaxWidth(), primary = false) { state.giveUp() }
        }
    }
}

// ---------------------------------------------------------------- the debrief

@Composable
private fun Debrief(state: AppState, o: Outcome) {
    val r = o.result
    val cleared = r.cleared
    val stage = r.stage
    val spec = r.spec
    var shownStars by remember(o) { mutableIntStateOf(0) }
    LaunchedEffect(o) {
        delay(450)
        for (i in 1..o.stars) {
            shownStars = i
            Audio.play(Snd.BELL, 0.8f, 1f + i * 0.12f)
            Haptics.buzz(Haptics.Kind.CLICK, 0.7f)
            delay(380)
        }
    }
    val next = stage?.next?.takeIf { cleared && Game.unlocked(it) }
    // The next story chapter, once this one's beaten.
    val nextChapter = spec?.storyId?.let(Stories::chapter)?.let { c -> Stories.all[c.mode]?.getOrNull(c.index + 1) }?.takeIf { cleared && Game.chapterOpen(it) }
    Scrim {
        Row(
            Modifier.widthIn(max = 640.dp).glass(RoundedCornerShape(26.dp), if (cleared) Void.cyan.copy(alpha = 0.6f) else Void.border, 0.95f).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        spec != null -> if (cleared) "${spec.title} complete!" else "Mission failed"
                        stage == null -> "Rift run over"
                        cleared -> "Stage ${stage.title} clear!"
                        else -> "Mission failed"
                    },
                    color = if (cleared || (stage == null && spec == null)) Void.text else Void.bad, fontSize = 26.sp, fontWeight = FontWeight.Black,
                )
                if ((stage != null || spec != null) && cleared) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..3) {
                        val on = i <= shownStars
                        val s = remember { Animatable(0f) }
                        LaunchedEffect(on) { if (on) { s.snapTo(1.8f); s.animateTo(1f, tween(260)) } }
                        Glyph(if (on) "star" else "star_off", 38.dp, Modifier.scale(if (on) s.value else 1f), color = if (on) CREDIT else Void.dim.copy(alpha = 0.5f), glow = on)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fmt(r.score), color = Void.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    if (o.newBest && r.score > 0) Text("NEW BEST", color = CREDIT, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    "${plural(r.kills, "kill")} · ${plural(r.grazes, "graze")} · best chain x${r.bestChain} · ${clock(r.seconds)}",
                    color = Void.dim, fontSize = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Money((r.credits + o.reward.credits).toLong(), fontSize = 18.sp, icon = 20.dp)
                    if (o.reward.cores > 0) Money(o.reward.cores.toLong(), Currency.CORES, fontSize = 18.sp, icon = 20.dp)
                    if (o.reward.shards > 0) Money(o.reward.shards.toLong(), Currency.SHARDS, fontSize = 18.sp, icon = 20.dp)
                    Text("+${o.reward.xp + r.kills / 2} XP", color = Void.violet, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                if (o.firstClear && spec?.storyId != null) Text("Chapter complete: story bonus paid", color = Void.ok, fontSize = 12.sp)
                if (o.contractDone) Text("Contract fulfilled: bonus paid", color = Void.ok, fontSize = 12.sp)
                o.unlocked?.let { Text("Unlocked: ${Game.itemTitle(it)}!", color = SHARD, fontSize = 15.sp, fontWeight = FontWeight.Black) }
                if (o.firstClear && stage != null) Text(if (stage.isBoss) "First clear: double pay and boss cores" else "First clear: double pay", color = Void.ok, fontSize = 12.sp)
                if (o.firstClear && stage != null && stage.isBoss) Hubs.all.getOrNull(stage.sector + 1)?.let {
                    Text("New hub charted: ${it.title}. The operation moves on.", color = Color(it.glow), fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                if (o.levelAfter > o.levelBefore) Text("Pilot level ${o.levelAfter}!", color = CREDIT, fontSize = 15.sp, fontWeight = FontWeight.Black)
                val (lvl, into, need) = Levels.of(Game.xp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lv $lvl", color = Void.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Bar(into.toFloat() / need, Modifier.width(160.dp), 6.dp)
                }
                if (!cleared && (stage != null || spec != null)) Text(tip(r.hits, r.seconds), color = Void.dim, fontSize = 12.sp)
            }
            Column(Modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                if (spec != null) {
                    if (nextChapter != null) ActionButton("Next: ${nextChapter.title}", Modifier.fillMaxWidth(), icon = "next") { state.launch(nextChapter.spec()) }
                    ActionButton(if (cleared) "Again" else "Retry", Modifier.fillMaxWidth(), icon = "retry", primary = nextChapter == null) {
                        state.launch(if (spec.storyId != null || spec.contractId != null) spec else MissionSpec.quick(spec.mode, spec.sector, (System.nanoTime() and 0xFFFFFF).toInt()))
                    }
                    ActionButton("Hangar", Modifier.fillMaxWidth(), icon = "hangar", primary = false) { state.go(Screen.HANGAR) }
                    ActionButton("Hub", Modifier.fillMaxWidth(), icon = "home", primary = false) { state.hub() }
                    return@Column
                }
                if (next != null) ActionButton("Next: ${next.title}", Modifier.fillMaxWidth(), icon = "next") { state.launch(next) }
                ActionButton(if (stage == null) "Again" else "Retry", Modifier.fillMaxWidth(), icon = "retry", primary = next == null) { state.launch(stage) }
                ActionButton("Hangar", Modifier.fillMaxWidth(), icon = "hangar", primary = false) { state.go(Screen.HANGAR) }
                ActionButton(if (stage == null) (if (state.fromHub) "Hub" else "Home") else "Map", Modifier.fillMaxWidth(), icon = if (stage == null) "home" else "map", primary = false) {
                    if (stage != null) state.mapStage = next ?: stage
                    state.go(if (stage == null) Screen.HOME else Screen.MAP)
                }
            }
        }
    }
}

private fun tip(hits: Int, seconds: Float) = when {
    seconds < 20f -> "Tip: only the tiny glowing core of your ship can be hit. Slip through the gaps."
    hits > 12 -> "Tip: upgrade your hull in the hangar for more armour."
    else -> "Tip: bullets brushing past you charge your special. Save it for the thick of it."
}
