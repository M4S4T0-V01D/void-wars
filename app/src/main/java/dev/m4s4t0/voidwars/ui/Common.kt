package dev.m4s4t0.voidwars.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Currency
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.Levels
import dev.m4s4t0.voidwars.model.Price
import dev.m4s4t0.voidui.Void

/** Gold for credits, and the cores' violet-cyan. */
val CREDIT = Color(0xFFFFC23D)
val CORE = Color(0xFFB98CFF)

fun argbColor(argb: Long) = Color(argb.toInt())

/** Keeps content clear of the camera cutout and any bars that are showing. */
@Composable
fun Modifier.safe() = windowInsetsPadding(WindowInsets.safeDrawing)

/** Every tap in the app: a soft tick and a tiny buzz. */
fun tapFeedback() {
    Audio.play(Snd.TAP, 0.6f)
    Haptics.buzz(Haptics.Kind.CLICK, 0.4f)
}

/** Dips a little while held and springs back on release. */
@Composable
fun Modifier.squishy(enabled: Boolean = true, sound: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.4f, stiffness = 600f), label = "squish")
    return this.scale(s).clickable(src, null, enabled) { if (sound) tapFeedback(); onClick() }
}

/** A gentle hover, so things on screen feel like they're floating in space. */
@Composable
fun Modifier.bob(amount: Dp = 3.dp, phase: Int = 0, periodMs: Int = 2000): Modifier {
    val t = rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = androidx.compose.animation.core.StartOffset(phase * 260)),
        label = "bobY",
    )
    return graphicsLayer { translationY = y * amount.toPx() }
}

/** A slow 0..1 pulse for glows. */
@Composable
fun pulse(periodMs: Int = 1200): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    return v
}

/** A frosted panel over the battle behind. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(18.dp), border: Color = Void.border, alpha: Float = 0.84f) =
    clip(shape).background(Void.panel.copy(alpha = alpha)).border(1.dp, border, shape)

/** 12,345. */
fun fmt(n: Long): String = "%,d".format(n)
fun fmt(n: Int): String = "%,d".format(n)

/** An amount of credits or cores with its icon. */
@Composable
fun Money(amount: Long, currency: Currency = Currency.CREDITS, modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp, icon: Dp = 16.dp, dimmed: Boolean = false) {
    val c = if (currency == Currency.CREDITS) CREDIT else CORE
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Glyph(if (currency == Currency.CREDITS) "credit" else "core", icon, color = c, glow = false)
        Text(fmt(amount), color = if (dimmed) c.copy(alpha = 0.5f) else c, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** What something costs, or the level it opens at if the pilot isn't there yet. */
@Composable
fun PriceTag(price: Price, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp) {
    if (Game.level < price.level) LevelTag(price.level, modifier, fontSize)
    else Money(price.amount.toLong(), price.currency, modifier, fontSize, (fontSize.value + 2).dp, dimmed = !Game.canAfford(price))
}

@Composable
fun Stars(n: Int, of: Int = 3, size: Dp = 18.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        for (i in 0 until of) Glyph(if (i < n) "star" else "star_off", size, color = if (i < n) CREDIT else Void.dim.copy(alpha = 0.6f), glow = i < n)
    }
}

/** The wallet: credits and cores on glass. */
@Composable
fun WalletChip(modifier: Modifier = Modifier) {
    Row(
        modifier.glass(RoundedCornerShape(50), alpha = 0.85f).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Money(Game.credits.toLong(), Currency.CREDITS, fontSize = 15.sp, icon = 18.dp)
        Money(Game.cores.toLong(), Currency.CORES, fontSize = 15.sp, icon = 18.dp)
    }
}

@Composable
fun LevelChip(modifier: Modifier = Modifier) {
    val (lvl, into, need) = Levels.of(Game.xp)
    Row(
        modifier.glass(RoundedCornerShape(50), alpha = 0.85f).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph("level", 18.dp, color = Void.violet, glow = false)
        Text("Lv $lvl", color = Void.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Bar(into.toFloat() / need, Modifier.width(56.dp), 6.dp)
    }
}

/** A thin rounded progress bar in the accent gradient (or [color]). */
@Composable
fun Bar(f: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, color: Color? = null, track: Color = Void.panelAlt) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(
            Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50))
                .background(if (color != null) Brush.horizontalGradient(listOf(color, lerpWhite(color, 0.3f))) else Brush.horizontalGradient(listOf(Void.violet, Void.cyan))),
        )
    }
}

/** A round glass button with a glyph. */
@Composable
fun RoundIcon(icon: String, modifier: Modifier = Modifier, size: Dp = 46.dp, on: Boolean = false, tint: Color = Void.text, onClick: () -> Unit) {
    Box(
        modifier.size(size).squishy(onClick = onClick).glass(CircleShape, if (on) Void.cyan else Void.border, 0.8f),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, size * 0.5f, color = tint, glow = false) }
}

/** The bar along the top of a menu screen: back, a title, then [actions] on the right. */
@Composable
fun GameBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp).squishy(onClick = onBack).glass(CircleShape, alpha = 0.85f), contentAlignment = Alignment.Center) {
            BackArrow()
        }
        Row(
            Modifier.weight(1f, fill = false).glass(RoundedCornerShape(50), alpha = 0.8f).padding(start = if (icon != null) 10.dp else 16.dp, end = 18.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Glyph(icon, 24.dp, color = Void.cyan)
            Column {
                Text(title, color = Void.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = Void.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(0.001f))
        actions()
    }
}

@Composable
private fun BackArrow() {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val k = size.minDimension / 24f
        val p = androidx.compose.ui.graphics.Path().apply { moveTo(14f * k, 5f * k); lineTo(7f * k, 12f * k); lineTo(14f * k, 19f * k) }
        drawPath(p, Void.text, style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** A big menu button: a glyph, a title and a line, and a little badge if something's waiting. */
@Composable
fun BigButton(
    icon: String, title: String, sub: String?, modifier: Modifier = Modifier, accent: Color = Void.violet,
    badge: String? = null, phase: Int = 0, height: Dp = 62.dp, enabled: Boolean = true, onClick: () -> Unit,
) {
    Box(modifier.bob(2.dp, phase)) {
        Row(
            Modifier.fillMaxWidth().height(height).squishy(onClick = onClick).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = if (enabled) 0.85f else 0.3f), darken(accent, 0.3f).copy(alpha = 0.9f))))
                .border(1.5.dp, lerpWhite(accent, 0.4f).copy(alpha = if (enabled) 0.8f else 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Glyph(if (enabled) icon else "lock", height * 0.48f, color = Color.White)
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (badge != null) Badge(badge, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp))
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Void.bad).border(1.5.dp, Color.White, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** "Lv 4": what the pilot is still too junior for. */
@Composable
fun LevelTag(level: Int, modifier: Modifier = Modifier, fontSize: TextUnit = 11.sp) {
    Text(
        "Lv $level", color = Void.violet, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Void.violet.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** A solid action button: the accent gradient, or a quiet panel; dimmed when it can't be pressed. */
@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, icon: String? = null, primary: Boolean = true, enabled: Boolean = true, accent: Color = Void.violet, onClick: () -> Unit) {
    Row(
        modifier.squishy(enabled, onClick = onClick).clip(RoundedCornerShape(50))
            .background(
                if (primary) Brush.linearGradient(listOf(accent.copy(alpha = if (enabled) 1f else 0.35f), androidx.compose.ui.graphics.lerp(accent, Void.cyan, 0.6f).copy(alpha = if (enabled) 1f else 0.35f)))
                else Brush.linearGradient(listOf(Void.panelAlt, Void.panelAlt)),
            )
            .border(1.dp, if (primary) Color.White.copy(alpha = 0.25f) else Void.border, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val c = if (primary) Color.White else Void.text
        if (icon != null) Glyph(icon, 18.dp, color = c.copy(alpha = if (enabled) 1f else 0.5f), glow = false)
        Text(text, color = c.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

fun lerpWhite(c: Color, t: Float) = Color(c.red + (1 - c.red) * t, c.green + (1 - c.green) * t, c.blue + (1 - c.blue) * t, c.alpha)
fun darken(c: Color, t: Float) = Color(c.red * t, c.green * t, c.blue * t, c.alpha)

fun plural(n: Int, word: String) = if (n == 1) "$n $word" else "${fmt(n)} ${word}s"

/** 2:05. */
fun clock(seconds: Float): String { val s = seconds.toInt(); return "%d:%02d".format(s / 60, s % 60) }
