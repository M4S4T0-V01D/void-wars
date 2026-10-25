package dev.m4s4t0.voidwars.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import dev.m4s4t0.voidui.Void
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The game's icons: neon line drawings on a 24-unit grid, drawn straight onto a canvas (no bitmaps), with a soft glow
 * under each stroke.
 */
@Composable
fun Glyph(name: String, size: Dp, modifier: Modifier = Modifier, color: Color = Void.cyan, glow: Boolean = true) {
    Canvas(modifier.size(size)) { glyph(name, color, glow) }
}

/** Draws on a 24 x 24 grid scaled to the canvas. */
private class Pen(val d: DrawScope, val color: Color, val glow: Boolean) {
    val k = d.size.minDimension / 24f
    private val w = 1.9f * k

    private fun stroke(p: Path, width: Float = w) {
        if (glow) d.drawPath(p, color.copy(alpha = 0.22f), style = Stroke(width * 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        d.drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun line(vararg pts: Float, close: Boolean = false, width: Float = 1f) {
        val p = Path()
        p.moveTo(pts[0] * k, pts[1] * k)
        var i = 2
        while (i < pts.size) { p.lineTo(pts[i] * k, pts[i + 1] * k); i += 2 }
        if (close) p.close()
        stroke(p, w * width)
    }

    fun fill(vararg pts: Float, alpha: Float = 1f, c: Color = color) {
        val p = Path()
        p.moveTo(pts[0] * k, pts[1] * k)
        var i = 2
        while (i < pts.size) { p.lineTo(pts[i] * k, pts[i + 1] * k); i += 2 }
        p.close()
        d.drawPath(p, c.copy(alpha = alpha * c.alpha), style = Fill)
    }

    fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false, alpha: Float = 1f) {
        if (filled) { d.drawCircle(color.copy(alpha = alpha), r * k, Offset(cx * k, cy * k)); return }
        if (glow) d.drawCircle(color.copy(alpha = 0.22f), r * k, Offset(cx * k, cy * k), style = Stroke(w * 3.2f))
        d.drawCircle(color, r * k, Offset(cx * k, cy * k), style = Stroke(w))
    }

    fun arc(cx: Float, cy: Float, r: Float, from: Float, sweep: Float) {
        val tl = Offset((cx - r) * k, (cy - r) * k)
        val sz = Size(2 * r * k, 2 * r * k)
        if (glow) d.drawArc(color.copy(alpha = 0.22f), from, sweep, false, tl, sz, style = Stroke(w * 3.2f, cap = StrokeCap.Round))
        d.drawArc(color, from, sweep, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
    }

    /** A regular polygon of [n] sides. */
    fun ngon(cx: Float, cy: Float, r: Float, n: Int, rot: Float = 0f, filled: Boolean = false, alpha: Float = 1f) {
        val pts = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = rot + i * 2 * PI.toFloat() / n
            pts[i * 2] = cx + cos(a) * r; pts[i * 2 + 1] = cy + sin(a) * r
        }
        if (filled) fill(*pts, alpha = alpha) else line(*pts, close = true)
    }

    fun star(cx: Float, cy: Float, r: Float, filled: Boolean) {
        val pts = FloatArray(20)
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
            val rr = if (i % 2 == 0) r else r * 0.45f
            pts[i * 2] = cx + cos(a) * rr; pts[i * 2 + 1] = cy + sin(a) * rr
        }
        if (filled) fill(*pts)
        line(*pts, close = true)
    }
}

fun DrawScope.glyph(name: String, color: Color, glow: Boolean = true) {
    val p = Pen(this, color, glow)
    with(p) {
        when (name) {
            "ship", "hull" -> { line(21f, 12f, 6f, 5f, 8f, 12f, 6f, 19f, close = true); line(8f, 12f, 3f, 12f); line(14f, 12f, 11f, 10.5f, 11f, 13.5f, close = true) }
            "wings" -> { line(10f, 12f, 4f, 3f, 2f, 3f, 6f, 12f, 2f, 21f, 4f, 21f, close = true); line(10f, 12f, 21f, 12f) }
            "engine" -> { line(4f, 8f, 12f, 8f, 16f, 6f, 16f, 18f, 12f, 16f, 4f, 16f, close = true); line(19f, 9f, 22f, 8f); line(19f, 12f, 23f, 12f); line(19f, 15f, 22f, 16f) }
            "weapon" -> { line(3f, 15f, 15f, 15f, 15f, 10f, 3f, 10f, close = true); line(15f, 12.5f, 22f, 12.5f); line(6f, 15f, 5f, 20f) }
            "support" -> { circle(12f, 12f, 3.5f); circle(12f, 12f, 8.5f); circle(20.5f, 12f, 1.6f, true); circle(3.5f, 12f, 1.6f, true) }
            "special", "bolt" -> line(13f, 2f, 5f, 13.5f, 11f, 13.5f, 9.5f, 22f, 19f, 9.5f, 13f, 9.5f, close = true)
            "paint" -> { line(4f, 20f, 7f, 14f, 10f, 17f, close = true); line(8.5f, 15.5f, 19f, 5f, 21f, 7f, 10.5f, 17.5f) }
            "crosshair", "target" -> { circle(12f, 12f, 7f); line(12f, 2f, 12f, 7f); line(12f, 17f, 12f, 22f); line(2f, 12f, 7f, 12f); line(17f, 12f, 22f, 12f); circle(12f, 12f, 1.3f, true) }
            "rock" -> line(5f, 9f, 9f, 4f, 16f, 5f, 20f, 10f, 18f, 18f, 11f, 20f, 5f, 16f, close = true).also { line(9f, 9f, 12f, 11f, 11f, 15f) }
            "graze" -> { circle(9f, 13f, 3f); arc(9f, 13f, 7.5f, -60f, 120f); circle(19f, 6f, 2f, true) }
            "credit", "credits" -> { ngon(12f, 12f, 9f, 6, PI.toFloat() / 6); line(12f, 7f, 12f, 17f); line(15f, 9f, 10.5f, 9f, 9f, 10.5f, 10.5f, 12f, 13.5f, 12f, 15f, 13.5f, 13.5f, 15f, 9f, 15f) }
            "core", "cores" -> { ngon(12f, 12f, 9.5f, 4, 0f); ngon(12f, 12f, 4.5f, 4, 0f, filled = true, alpha = 0.9f) }
            "flag" -> { line(6f, 21f, 6f, 3f); line(6f, 4f, 18f, 4f, 15f, 8f, 18f, 12f, 6f, 12f) }
            "capsule" -> { line(8f, 5f, 16f, 5f, 18f, 7f, 18f, 17f, 16f, 19f, 8f, 19f, 6f, 17f, 6f, 7f, close = true); line(12f, 9f, 12f, 15f); line(9f, 12f, 15f, 12f) }
            "chain", "combo" -> { line(4f, 16f, 8f, 8f, 12f, 16f, 16f, 8f, 20f, 16f); circle(20f, 6f, 1.6f, true) }
            "carrier" -> { line(2f, 12f, 6f, 7f, 18f, 7f, 22f, 12f, 18f, 17f, 6f, 17f, close = true); line(8f, 10f, 15f, 10f); line(8f, 14f, 15f, 14f) }
            "star", "perfect" -> star(12f, 12.5f, 9.5f, true)
            "star_off" -> star(12f, 12.5f, 9.5f, false)
            "skull", "bosses" -> { line(5f, 12f, 5f, 8f, 8f, 4f, 16f, 4f, 19f, 8f, 19f, 12f, 16f, 15f, 16f, 19f, 8f, 19f, 8f, 15f, close = true); circle(9.5f, 10.5f, 1.8f, true); circle(14.5f, 10.5f, 1.8f, true); line(12f, 16f, 12f, 19f) }
            "calendar", "streak" -> { line(4f, 6f, 20f, 6f, 20f, 20f, 4f, 20f, close = true); line(4f, 10f, 20f, 10f); line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f); circle(12f, 15f, 1.6f, true) }
            "ghost" -> { line(5f, 20f, 5f, 10f); arc(12f, 10f, 7f, 180f, 180f); line(19f, 10f, 19f, 20f, 16.5f, 18f, 14f, 20f, 12f, 18f, 10f, 20f, 7.5f, 18f, 5f, 20f); circle(9.5f, 10f, 1.2f, true); circle(14.5f, 10f, 1.2f, true) }
            "hangar" -> { line(2f, 20f, 2f, 10f, 12f, 4f, 22f, 10f, 22f, 20f); line(6f, 20f, 6f, 12f, 18f, 12f, 18f, 20f); line(9f, 16f, 15f, 16f) }
            "wrench", "upgrade" -> { line(14f, 10f, 5f, 19f); arc(16.5f, 7.5f, 4.5f, 120f, 270f); line(4f, 18f, 6f, 20f) }
            "rift" -> { arc(12f, 12f, 9f, 0f, 250f); arc(12f, 12f, 5.5f, 120f, 250f); circle(12f, 12f, 1.8f, true) }
            "medal", "trophy" -> { circle(12f, 15f, 6f); line(8f, 3f, 10f, 9.5f); line(16f, 3f, 14f, 9.5f); star(12f, 15f, 3f, true) }
            "mission", "missions" -> { line(5f, 3f, 17f, 3f, 19f, 5f, 19f, 21f, 5f, 21f, close = true); line(8f, 9f, 9.5f, 10.5f, 12f, 8f); line(8f, 15f, 9.5f, 16.5f, 12f, 14f); line(14f, 9.5f, 16f, 9.5f); line(14f, 15.5f, 16f, 15.5f) }
            "lock" -> { line(6f, 11f, 18f, 11f, 18f, 20f, 6f, 20f, close = true); arc(12f, 9f, 4f, 180f, 180f); line(8f, 9f, 8f, 11f); line(16f, 9f, 16f, 11f); circle(12f, 15.5f, 1.4f, true) }
            "check" -> line(5f, 12.5f, 10f, 17.5f, 19f, 7f)
            "play", "launch" -> fill(8f, 5f, 19f, 12f, 8f, 19f).also { line(8f, 5f, 19f, 12f, 8f, 19f, close = true) }
            "pause" -> { line(9f, 5f, 9f, 19f, width = 1.4f); line(15f, 5f, 15f, 19f, width = 1.4f) }
            "settings" -> { circle(12f, 12f, 3.2f); for (i in 0 until 8) { val a = i * PI.toFloat() / 4; line(12f + cos(a) * 6f, 12f + sin(a) * 6f, 12f + cos(a) * 9f, 12f + sin(a) * 9f, width = 1.3f) }; circle(12f, 12f, 6.5f) }
            "level" -> { line(4f, 16f, 12f, 8f, 20f, 16f); line(4f, 21f, 12f, 13f, 20f, 21f); line(4f, 11f, 12f, 3f, 20f, 11f) }
            "crate", "supply" -> { line(3f, 8f, 12f, 4f, 21f, 8f, 21f, 17f, 12f, 21f, 3f, 17f, close = true); line(3f, 8f, 12f, 12f, 21f, 8f); line(12f, 12f, 12f, 21f) }
            "heart", "repair" -> line(12f, 20f, 4f, 12f, 4f, 7f, 7f, 4f, 10f, 4f, 12f, 7f, 14f, 4f, 17f, 4f, 20f, 7f, 20f, 12f, close = true)
            "map", "campaign" -> { line(3f, 6f, 9f, 3f, 15f, 6f, 21f, 3f, 21f, 18f, 15f, 21f, 9f, 18f, 3f, 21f, close = true); line(9f, 3f, 9f, 18f); line(15f, 6f, 15f, 21f) }
            "home" -> { line(3f, 11f, 12f, 3f, 21f, 11f); line(6f, 9f, 6f, 20f, 18f, 20f, 18f, 9f) }
            "sound" -> { line(4f, 9f, 8f, 9f, 13f, 4f, 13f, 20f, 8f, 15f, 4f, 15f, close = true); arc(14f, 12f, 4f, -50f, 100f); arc(14f, 12f, 7.5f, -50f, 100f) }
            "warning" -> { line(12f, 3f, 22f, 20f, 2f, 20f, close = true); line(12f, 9f, 12f, 14f); circle(12f, 17f, 1.2f, true) }
            "retry" -> { arc(12f, 12f, 7.5f, -60f, 300f); line(17f, 2.5f, 16f, 6.5f, 20f, 7.5f) }
            "swap" -> { line(4f, 8f, 19f, 8f); line(15f, 4f, 19f, 8f, 15f, 12f); line(20f, 16f, 5f, 16f); line(9f, 12f, 5f, 16f, 9f, 20f) }
            "next" -> { line(5f, 5f, 12f, 12f, 5f, 19f); line(12f, 5f, 19f, 12f, 12f, 19f) }
            "shield" -> line(12f, 2.5f, 20f, 6f, 19f, 13f, 12f, 21.5f, 5f, 13f, 4f, 6f, close = true)
            "speed" -> { arc(12f, 14f, 9f, 180f, 180f); line(12f, 14f, 17f, 8f); circle(12f, 14f, 1.4f, true) }
            "fire" -> line(12f, 21f, 7f, 17f, 7f, 12f, 10f, 8f, 10f, 12f, 13f, 3f, 17f, 10f, 17f, 17f, close = true)
            "magnet" -> { line(6f, 4f, 6f, 13f); arc(12f, 13f, 6f, 0f, 180f); line(18f, 13f, 18f, 4f); line(4f, 7f, 8f, 7f); line(16f, 7f, 20f, 7f) }
            "contract" -> { line(5f, 3f, 15f, 3f, 19f, 7f, 19f, 21f, 5f, 21f, close = true); line(15f, 3f, 15f, 7f, 19f, 7f); line(8f, 10f, 15f, 10f); line(8f, 13.5f, 15f, 13.5f); line(8f, 17.5f, 10f, 15.5f, 11.5f, 17.5f, 13.5f, 16f) }
            "market" -> { line(3f, 9f, 5f, 4f, 19f, 4f, 21f, 9f, close = true); line(5f, 9f, 5f, 20f, 19f, 20f, 19f, 9f); line(10f, 20f, 10f, 14f, 14f, 14f, 14f, 20f); circle(8f, 9f, 1.2f, true); circle(16f, 9f, 1.2f, true) }
            "shard", "shards" -> { fill(12f, 2f, 18f, 10f, 12f, 22f, 6f, 10f, alpha = 0.35f); line(12f, 2f, 18f, 10f, 12f, 22f, 6f, 10f, close = true); line(6f, 10f, 18f, 10f); line(12f, 2f, 12f, 22f, width = 0.8f) }
            "beacon" -> { line(12f, 10f, 8f, 21f); line(12f, 10f, 16f, 21f); line(9.5f, 17f, 14.5f, 17f); circle(12f, 8f, 2f, true); arc(12f, 8f, 5f, -150f, 120f); arc(12f, 8f, 8.5f, -150f, 120f) }
            "escort" -> { line(4f, 14f, 8f, 10f, 14f, 10f, 14f, 18f, 8f, 18f, close = true); line(14f, 12f, 18f, 12f, 20f, 14f, 20f, 18f, 14f, 18f); line(7f, 6f, 12f, 3f, 17f, 6f); line(12f, 3f, 12f, 7f) }
            "salvage" -> { line(12f, 2f, 12f, 9f); arc(12f, 14f, 5f, 200f, 320f); line(12f, 9f, 12f, 19f); line(7f, 14f, 4f, 14f); line(17f, 14f, 20f, 14f); circle(12f, 4f, 1.4f, true) }
            "ore" -> { ngon(12f, 13f, 8f, 6, 0.3f); fill(9f, 11f, 13f, 9f, 15f, 13f, 11f, 16f, alpha = 0.5f); line(4.5f, 9f, 9f, 11f); line(15f, 13f, 19.5f, 16f) }
            "defense" -> { line(12f, 2f, 20f, 5f, 20f, 12f, 12f, 22f, 4f, 12f, 4f, 5f, close = true); line(8f, 11f, 16f, 11f); line(12f, 7f, 12f, 16f) }
            "ace" -> { line(12f, 3f, 14.5f, 10f, 22f, 12f, 14.5f, 14f, 12f, 21f, 9.5f, 14f, 2f, 12f, 9.5f, 10f, close = true); circle(12f, 12f, 2f, true) }
            "boost" -> { line(4f, 6f, 10f, 12f, 4f, 18f); line(10f, 6f, 16f, 12f, 10f, 18f); line(16f, 6f, 22f, 12f, 16f, 18f) }
            "dock" -> { circle(12f, 12f, 8.5f); circle(12f, 12f, 3f, true); line(12f, 1f, 12f, 5f); line(12f, 19f, 12f, 23f); line(1f, 12f, 5f, 12f); line(19f, 12f, 23f, 12f) }
            "crown" -> { line(3f, 18f, 3f, 7f, 8f, 12f, 12f, 4f, 16f, 12f, 21f, 7f, 21f, 18f, close = true); line(3f, 21f, 21f, 21f); circle(12f, 14f, 1.5f, true) }
            "moon" -> { arc(12f, 12f, 9f, 60f, 240f); arc(16f, 9f, 7.5f, 95f, 175f) }
            "eye" -> { arc(12f, 20f, 11f, 213f, 114f); arc(12f, 4f, 11f, 33f, 114f); circle(12f, 12f, 3.5f); circle(12f, 12f, 1.4f, true) }
            "diamond" -> { line(6f, 4f, 18f, 4f, 22f, 9f, 12f, 21f, 2f, 9f, close = true); line(2f, 9f, 22f, 9f); line(9f, 4f, 12f, 21f, 15f, 4f) }
            "atom" -> {
                circle(12f, 12f, 1.8f, true)
                for (r in 0 until 3) {
                    val q = r * 1.047f
                    line(*FloatArray(48) { k -> val a = (k / 2) * 0.2732f; val x = kotlin.math.cos(a) * 9.5f; val y = kotlin.math.sin(a) * 3.4f; if (k % 2 == 0) 12f + x * kotlin.math.cos(q) - y * kotlin.math.sin(q) else 12f + x * kotlin.math.sin(q) + y * kotlin.math.cos(q) }, close = true)
                }
            }
            "spiral" -> { arc(12f, 12f, 2f, 0f, 180f); arc(11f, 12f, 3f, 180f, 180f); arc(12f, 12f, 4f, 0f, 180f); arc(11f, 12f, 5f, 180f, 180f); arc(12f, 12f, 6f, 0f, 180f); arc(11f, 12f, 7f, 180f, 180f); arc(12f, 12f, 8f, 0f, 180f) }
            "sword" -> { line(19f, 3f, 21f, 3f, 21f, 5f, 9f, 17f, 7f, 15f, close = true); line(5f, 13f, 11f, 19f); line(7f, 17f, 3f, 21f) }
            "wing" -> { line(3f, 18f, 8f, 8f, 21f, 4f, 17f, 9f, 20f, 9f, 15f, 13f, 18f, 13f, 12f, 17f, close = true); line(8f, 8f, 12f, 17f) }
            "close" -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            "trail" -> { line(3f, 18f, 9f, 15f, 14f, 15f, width = 0.8f); line(3f, 13f, 9f, 12f, 14f, 12f, width = 0.8f); fill(14f, 9f, 22f, 12f, 14f, 18f, alpha = 0.5f); line(14f, 9f, 22f, 12f, 14f, 18f, close = true) }
            "shot" -> { line(4f, 20f, 12f, 12f, width = 0.9f); line(9f, 20f, 15f, 14f, width = 0.6f); circle(16f, 8f, 3.5f, true); circle(16f, 8f, 6f) }
            "burst" -> { circle(12f, 12f, 3f, true); for (i in 0 until 8) { val a = i * PI.toFloat() / 4; line(12f + cos(a) * 5.5f, 12f + sin(a) * 5.5f, 12f + cos(a) * 10f, 12f + sin(a) * 10f) } }
            "title" -> { line(3f, 7f, 21f, 7f, 21f, 17f, 3f, 17f, close = true); line(6f, 11f, 12f, 11f); line(6f, 14f, 16f, 14f); star(18f, 11f, 2f, true) }
            "emblem" -> { ngon(12f, 12f, 9.5f, 6, 0f); star(12f, 12f, 5f, true) }
            else -> star(12f, 12.5f, 9f, false)
        }
    }
}
