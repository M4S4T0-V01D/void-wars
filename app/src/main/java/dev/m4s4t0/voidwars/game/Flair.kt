package dev.m4s4t0.voidwars.game

import dev.m4s4t0.voidwars.model.Look
import dev.m4s4t0.voidwars.model.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The pilot's fitted trail, shot colour and kill burst, resolved once per run and drawn by both engines. */
class Flair {
    var trail = 0; var trailStyle = 0
    var shot = 0; var shotStyle = 0
    var burst = 0; var burstStyle = 0
    private var hue = 0f

    fun set(p: Paint, glow: Int) {
        fun col(k: Look) = p.look(k).argb.toInt().let { if (it == 0) glow else it }
        trail = col(Look.TRAIL); trailStyle = p.look(Look.TRAIL).style
        shot = col(Look.SHOT); shotStyle = p.look(Look.SHOT).style
        burst = col(Look.BURST); burstStyle = p.look(Look.BURST).style
    }

    /** The player's shot colour at time [t]; shimmer cycles the hue. */
    fun shotColor(t: Float) = if (shotStyle == 1) rainbow(t * 0.6f) else shot

    /** One exhaust puff at ([x], [y]) moving ([vx], [vy]); ([nx], [ny]) is the sideways axis. */
    fun puff(parts: Particles, rnd: Random, x: Float, y: Float, vx: Float, vy: Float, nx: Float, ny: Float, len: Float) {
        val j = { (rnd.nextFloat() - 0.5f) * 1.2f }
        when (trailStyle) {
            1 -> parts.add(x, y, vx + j() * 3f, vy + j() * 3f, 0.25f * len, 0.22f, trail, Shape.SPARK, 1.5f)
            2 -> { hue += 0.015f; parts.add(x, y, vx + j(), vy + j(), 0.22f * len, 0.42f, rainbow(hue), Shape.SOFT, 3f, -1.2f) }
            3 -> for (s in floatArrayOf(-0.22f, 0.22f)) parts.add(x + nx * s, y + ny * s, vx, vy, 0.2f * len, 0.3f, trail, Shape.SOFT, 3f, -0.8f)
            4 -> if (rnd.nextFloat() < 0.35f) parts.add(x, y, vx * 0.4f, vy * 0.4f, 0.35f * len, 0.2f, trail, Shape.WAVE, 2f, 2.2f)
                 else parts.add(x, y, vx + j(), vy + j(), 0.12f * len, 0.3f, trail, Shape.SOFT, 3f, -1.2f)
            5 -> parts.add(x + j() * 0.4f, y + j() * 0.4f, vx * 0.3f + j(), vy * 0.3f + j(), 0.5f * len, 0.16f + rnd.nextFloat() * 0.14f, if (rnd.nextFloat() < 0.3f) WHITE else trail, Shape.STAR, 1.2f)
            6 -> {
                parts.add(x, y, vx * 1.2f + j(), vy * 1.2f + j(), 0.16f * len, 0.5f, trail, Shape.SOFT, 3f, -2f)
                parts.add(x, y, vx + j() * 2f, vy + j() * 2f, 0.1f * len, 0.3f, 0xFFFFD070.toInt(), Shape.SOFT, 3f, -1.5f)
            }
            else -> parts.add(x, y, vx + j(), vy + j(), 0.18f * len, 0.42f, trail, Shape.SOFT, 3f, -1.2f)
        }
    }

    /** The extra flourish on a kill, on top of the engine's plain explosion. */
    fun kill(parts: Particles, rnd: Random, x: Float, y: Float, size: Float) {
        when (burstStyle) {
            1 -> { parts.add(x, y, 0f, 0f, 0.5f, 0.3f, burst, Shape.WAVE, 0f, size * 16f); parts.add(x, y, 0f, 0f, 0.35f, 0.3f, WHITE, Shape.WAVE, 0f, size * 10f) }
            2 -> repeat((6 + size * 6).toInt()) {
                val a = rnd.nextFloat() * 6.283f; val s = (5f + rnd.nextFloat() * 8f) * (0.6f + size * 0.4f)
                parts.add(x, y, cos(a) * s, sin(a) * s, 0.6f + rnd.nextFloat() * 0.4f, 0.22f + rnd.nextFloat() * 0.2f, burst, Shape.DIAMOND, 1.6f)
            }
            3 -> repeat((10 + size * 8).toInt()) {
                val a = rnd.nextFloat() * 6.283f; val s = 3f + rnd.nextFloat() * 7f
                parts.add(x, y, cos(a) * s, sin(a) * s, 0.8f + rnd.nextFloat() * 0.5f, 0.16f + rnd.nextFloat() * 0.1f, rainbow(rnd.nextFloat()), Shape.STAR, 2.2f)
            }
            4 -> {
                repeat((8 + size * 6).toInt()) {
                    val a = rnd.nextFloat() * 6.283f; val d = 1.5f + size * 2f
                    parts.add(x + cos(a) * d, y + sin(a) * d, -cos(a) * d * 3f, -sin(a) * d * 3f, 0.3f, 0.25f, burst, Shape.SOFT, 0f, -0.6f)
                }
                parts.add(x, y, 0f, 0f, 0.45f, size * 1.5f, burst, Shape.HOLE, 0f, -size * 2f)
            }
            else -> {}
        }
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** A full-saturation colour around the hue wheel; [h] wraps at 1. */
        fun rainbow(h: Float): Int {
            val x = ((h % 1f) + 1f) % 1f * 6f
            val f = x - x.toInt()
            val (r, g, b) = when (x.toInt()) {
                0 -> Triple(1f, f, 0f); 1 -> Triple(1f - f, 1f, 0f); 2 -> Triple(0f, 1f, f)
                3 -> Triple(0f, 1f - f, 1f); 4 -> Triple(f, 0f, 1f); else -> Triple(1f, 0f, 1f - f)
            }
            return (0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        }
    }
}
