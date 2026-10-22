package dev.m4s4t0.voidwars.game

import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.game.World.Companion.H
import dev.m4s4t0.voidwars.game.World.Companion.ORANGE
import dev.m4s4t0.voidwars.game.World.Companion.PINK
import dev.m4s4t0.voidwars.game.World.Companion.RED
import dev.m4s4t0.voidwars.game.World.Companion.VIOLET
import dev.m4s4t0.voidwars.game.World.Companion.YELLOW
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Foe
import dev.m4s4t0.voidwars.model.Formation
import dev.m4s4t0.voidwars.model.Wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val PIf = PI.toFloat()

/** Puts a wave's enemies just off the right edge, set up to fly their formation. */
internal fun World.spawnWave(w: Wave, d: Float) {
    val r = Random(w.seed)
    val y0 = w.y * H * 0.7f
    when (w.formation) {
        Formation.ROCKS -> for (k in 0 until w.count) {
            val roll = r.nextFloat()
            val foe = when { roll < 0.15f + d * 0.02f -> Foe.ROCK_L; roll < 0.6f -> Foe.ROCK_M; else -> Foe.ROCK_S }
            val e = newEnemy(foe, W + 2f + r.nextFloat() * 9f, (r.nextFloat() * 2f - 1f) * (H - 1.2f), d) ?: return
            e.vx = -(2.4f + r.nextFloat() * 2.6f) * if (foe == Foe.ROCK_L) 0.75f else 1f
            e.vy = (r.nextFloat() - 0.5f) * 1.6f
            e.spin = (r.nextFloat() - 0.5f) * 2.5f
            e.rollV = (r.nextFloat() - 0.5f) * 3f
            e.size = 0.85f + r.nextFloat() * 0.35f
            e.mode = 1
        }
        Formation.SINE_LINE -> for (k in 0 until w.count) {
            val e = newEnemy(Foe.DRONE, W + 1.5f + k * 1.5f, y0, d) ?: return
            e.mode = 1; e.a = y0; e.b = k * 0.55f; e.vx = -6.2f
        }
        Formation.V_WING -> {
            val mid = (w.count - 1) / 2f
            for (k in 0 until w.count) {
                val e = newEnemy(Foe.DRONE, W + 1.5f + abs(k - mid) * 1.3f, (y0 + (k - mid) * 1.25f).coerceIn(-H + 1f, H - 1f), d) ?: return
                e.mode = 2; e.vx = -5.2f
                e.fireT = 1.2f + abs(k - mid) * 0.2f
            }
        }
        Formation.DIVE -> for (k in 0 until w.count) {
            val e = newEnemy(Foe.DART, W + 1.5f + k * 2.2f, (r.nextFloat() * 2f - 1f) * (H - 2f), d) ?: return
            e.mode = 0; e.vx = -7f
        }
        Formation.GUN_PAIR -> for (k in 0 until w.count) {
            val ty = if (w.count == 2) (if (k == 0) -4.5f else 4.5f) else (k - 1) * 5.5f
            val e = newEnemy(Foe.GUNSHIP, W + 2.5f + k * 0.8f, ty, d) ?: return
            e.a = W * (0.58f + 0.1f * (k % 2)); e.b = ty; e.vx = -6f
            e.fireT = 1.5f + k * 0.6f
        }
        Formation.SPIN -> for (k in 0 until w.count) {
            val ty = if (w.count == 1) y0 * 0.6f else (if (k == 0) -4f else 4f)
            val e = newEnemy(Foe.SPINNER, W + 2.5f, ty, d) ?: return
            e.a = W * (0.4f + 0.12f * k); e.b = ty; e.vx = -6f
            e.spin = 1.4f
            e.fireT = 1.4f
        }
        Formation.MINES -> for (k in 0 until w.count) {
            val e = newEnemy(Foe.MINE, W + 1.5f + k * 2.4f + r.nextFloat(), (r.nextFloat() * 2f - 1f) * (H - 1.5f), d) ?: return
            e.vx = -2.2f - r.nextFloat(); e.vy = (r.nextFloat() - 0.5f) * 0.8f
            e.spin = (r.nextFloat() - 0.5f) * 3f
        }
        Formation.LANCE -> for (k in 0 until w.count) {
            val ty = (k - (w.count - 1) / 2f) * 5f
            val e = newEnemy(Foe.LANCER, W + 2f, ty, d) ?: return
            e.a = W * (0.7f - 0.08f * k); e.b = ty; e.vx = -6f
            e.fireT = 1.2f + k * 0.7f
        }
        Formation.SWARM -> for (k in 0 until w.count) {
            val e = newEnemy(Foe.DRONE, W + 1f + k * 0.6f, (y0 + (r.nextFloat() - 0.5f) * 2.2f).coerceIn(-H + 1f, H - 1f), d * 0.6f) ?: return
            e.mode = 3; e.vx = -8.5f; e.a = r.nextFloat() * 6f; e.size = 0.8f
        }
        Formation.ELITE -> {
            val e = newEnemy(Foe.CARRIER, W + 4f, 0f, d) ?: return
            e.a = W * 0.56f; e.vx = -4f; e.fireT = 2f
        }
        Formation.BOSS -> Unit
    }
    if (w.formation != Formation.ROCKS && w.formation != Formation.MINES) sfx.play(Snd.WAVE, 0.25f, minGapMs = 300)
}

/** Steers and fires one enemy for [dt]. */
internal fun World.think(e: Enemy, dt: Float) {
    val rate = Campaign.fireScale(difficulty)
    val playing = state == World.State.PLAYING && alive
    when (e.foe) {
        Foe.ROCK_S, Foe.ROCK_M, Foe.ROCK_L -> Unit
        Foe.DRONE -> {
            when (e.mode) {
                1 -> e.y = e.a + sin(e.t * 2.1f + e.b) * 2.6f
                2 -> Unit
                3 -> {
                    // Swarmers lean towards the ship and wiggle.
                    e.vy += ((py - e.y) * 0.9f - e.vy) * dt * 1.5f
                    e.vy += sin(e.t * 7f + e.a) * 6f * dt
                }
            }
            heading(e)
            if (e.mode != 3 && playing && difficulty >= 0.3f && onScreen(e) && e.x > px + 2f) {
                e.fireT -= dt * rate
                if (e.fireT <= 0f) {
                    e.fireT = if (e.mode == 2) 1.8f else 2.6f + random() * 1.5f
                    fire(e.x - 0.4f, e.y, aim(e.x, e.y), 5.2f, BShape.ORB, PINK)
                    sfx.play(Snd.ENEMY_SHOT, 0.25f, 1f, pan(e), 60)
                }
            }
        }
        Foe.DART -> {
            if (e.mode == 0 && e.x < W * 0.55f) {
                // Lock on and dive.
                e.mode = 1
                val a = aim(e.x, e.y)
                e.vx = cos(a) * 11f; e.vy = sin(a) * 11f
                if (e.vx > -3f) { e.vx = -3f }
                if (playing && difficulty >= 0.5f) fan(e.x, e.y, a, if (difficulty > 2f) 3 else 1, 0.35f, 6.5f, BShape.RICE, ORANGE)
            }
            heading(e)
        }
        Foe.GUNSHIP -> {
            if (e.phase == 0) {
                approach(e, e.a, e.b, dt)
                if (e.t > 11f) { e.phase = 1 }
            } else { e.vx += 6f * dt; e.vy *= 0.98f }
            e.rot = PIf; e.roll = e.vy * 0.08f
            if (playing && e.phase == 0 && onScreen(e)) {
                e.fireT -= dt * rate
                if (e.fireT <= 0f) {
                    e.fired++
                    e.fireT = 1.7f
                    if (difficulty >= 2f && e.fired % 3 == 0) ring(e.x, e.y, 14, random(), 4.6f, BShape.RICE, ORANGE)
                    else fan(e.x - 0.6f, e.y, aim(e.x, e.y), 5, 0.75f, 6f, BShape.ORB, PINK)
                    sfx.play(Snd.ENEMY_SHOT, 0.35f, 0.9f, pan(e), 60)
                }
            }
        }
        Foe.SPINNER -> {
            if (e.phase == 0) {
                approach(e, e.a, e.b, dt)
                if (e.t > 9.5f) e.phase = 1
            } else { e.vx += 6f * dt }
            if (playing && e.phase == 0 && onScreen(e) && e.t > 1.5f) {
                e.fireT -= dt * rate
                if (e.fireT <= 0f) {
                    e.fireT = 0.13f
                    val arms = if (difficulty > 2.2f) 4 else 3
                    for (k in 0 until arms) fire(e.x, e.y, e.c + k * 2f * PIf / arms, 4.4f, BShape.RICE, VIOLET)
                    e.c += 0.21f
                    sfx.play(Snd.ENEMY_SHOT, 0.15f, 1.3f, pan(e), 120)
                }
            }
        }
        Foe.MINE -> {
            e.vy *= 0.995f
            if (playing && onScreen(e)) {
                val dx = px - e.x; val dy = py - e.y
                if (dx * dx + dy * dy < 2.9f * 2.9f && e.t > 0.5f) {
                    // Too close: it goes off in a ring of shrapnel. Doesn't count as yours, or against you.
                    e.alive = false
                    if (e.counted) spawned--
                    ring(e.x, e.y, 10 + (difficulty * 1.5f).toInt(), random(), 4.6f, BShape.DIAMOND, YELLOW)
                    explode(e.x, e.y, 0.9f, World.BULLET_COLORS[YELLOW])
                    sfx.play(Snd.BOOM, 0.6f, 1.2f, pan(e), 30)
                }
            }
        }
        Foe.LANCER -> {
            if (e.phase < 3) {
                approach(e, e.a, e.b, dt)
                e.fireT -= dt
                if (e.fireT <= 0f && playing && onScreen(e)) {
                    // Charge a beam straight back along its row, then hop to a new row.
                    laser(e, e.x - 0.9f, e.y, PIf, 1.0f, 0.75f, 0.7f, color = RED)
                    sfx.play(Snd.ENEMY_SHOT, 0.5f, 0.6f, pan(e), 100)
                    e.phase++
                    e.fireT = 2.6f / (0.7f + rate * 0.3f)
                    e.c = e.t + 1.9f
                }
                if (e.c > 0f && e.t > e.c) { e.c = 0f; e.b = (py + (random() - 0.5f) * 3f).coerceIn(-H + 1.5f, H - 1.5f) }
            } else if (e.t > e.c + 0.5f) { e.vx += 6f * dt }
            e.rot = PIf; e.roll = e.vy * 0.1f
        }
        Foe.CARRIER -> carrier(e, dt, rate, playing)
        Foe.BOSS -> bossThink(e, dt, playing)
    }
}

private fun World.carrier(e: Enemy, dt: Float, rate: Float, playing: Boolean) {
    if (e.t < 40f) {
        approach(e, e.a, sin(e.t * 0.4f) * 3f, dt, 1.5f)
    } else e.vx += 3f * dt
    e.rot = PIf; e.roll = e.vy * 0.04f
    if (!playing || !onScreen(e) || e.t > 40f) return
    e.fireT -= dt * (0.6f + rate * 0.4f)
    if (e.fireT > 0f) return
    e.fired++
    when (e.fired % 4) {
        0 -> {
            // Launch three drones at the ship.
            for (k in 0 until 3) {
                val d = newEnemy(Foe.DRONE, e.x - 1f, e.y + (k - 1) * 1.2f, difficulty, counted = false) ?: break
                d.mode = 3; d.vx = -7f; d.size = 0.8f; d.entered = true
            }
            e.fireT = 2.2f
            sfx.play(Snd.WHOOSH, 0.5f, 0.8f, pan(e), 0)
        }
        1 -> {
            curtain(e.x - 1.5f, 5f)
            e.fireT = 2.2f
        }
        2 -> {
            for (k in 0 until 3) fan(e.x - 1.5f, e.y, aim(e.x, e.y), 7, 1.0f, 5f + k * 0.8f, BShape.RICE, ORANGE)
            e.fireT = 2f
        }
        3 -> {
            ring(e.x, e.y + 1.2f, 16, random(), 4.2f, BShape.ORB, PINK, turn = 0.35f)
            ring(e.x, e.y - 1.2f, 16, random(), 4.2f, BShape.ORB, VIOLET, turn = -0.35f)
            e.fireT = 2.4f
        }
    }
    sfx.play(Snd.ENEMY_SHOT, 0.5f, 0.7f, pan(e), 60)
}

/** A wall of bullets marching left with one gap to slip through. */
internal fun World.curtain(x: Float, speed: Float, gap: Float = 3.4f, color: Int = YELLOW) {
    val gy = (random() * 2f - 1f) * (H - 3f)
    var y = -H + 0.3f
    while (y < H) {
        if (abs(y - gy) > gap / 2) fire(x, y, PIf, speed, BShape.DIAMOND, color)
        y += 0.8f
    }
}

/** Glides towards (x, y). */
internal fun approach(e: Enemy, x: Float, y: Float, dt: Float, k: Float = 2.4f) {
    e.vx += ((x - e.x) * k - e.vx) * (dt * 4f).coerceAtMost(1f)
    e.vy += ((y - e.y) * k - e.vy) * (dt * 4f).coerceAtMost(1f)
}

private fun heading(e: Enemy) {
    if (hypot(e.vx, e.vy) > 0.5f) e.rot = atan2(e.vy, e.vx)
    e.roll = (e.vy * 0.12f).coerceIn(-0.8f, 0.8f)
}

internal fun World.random() = rnd.nextFloat()
internal fun World.pan(e: Enemy) = (e.x / W).coerceIn(-1f, 1f)

// ---------------------------------------------------------------- bosses

private fun World.bossThink(e: Enemy, dt: Float, playing: Boolean) {
    e.rot = PIf
    if (e.phase < 0) {
        // The entrance: slide in, untouchable, then the fight starts.
        approach(e, W * 0.6f, 0f, dt, 1.4f)
        if (e.x < W * 0.6f + 0.4f) { e.phase = 1; e.phaseT = 0f; e.fireT = 1.2f }
        return
    }
    e.phaseT += dt
    // Drift up and down (faster as it gets angry), always facing the ship.
    val sway = 0.45f + 0.12f * e.phase
    approach(e, W * 0.6f + sin(e.t * 0.37f) * 1.2f, sin(e.t * sway) * 3.4f, dt, 1.2f)
    e.roll = e.vy * 0.05f
    if (!playing) return
    val rate = sqrt(Campaign.fireScale(difficulty))
    e.fireT -= dt * rate
    when (e.boss) {
        dev.m4s4t0.voidwars.model.Boss.WARDEN, null -> warden(e)
        dev.m4s4t0.voidwars.model.Boss.HYDRA -> hydra(e)
        dev.m4s4t0.voidwars.model.Boss.TEMPEST -> tempest(e)
        dev.m4s4t0.voidwars.model.Boss.REVENANT -> revenant(e)
        dev.m4s4t0.voidwars.model.Boss.VOID_HEART -> voidHeart(e)
    }
}

private fun World.shotSound(e: Enemy, gain: Float = 0.45f, pitch: Float = 0.8f) = sfx.play(Snd.ENEMY_SHOT, gain, pitch, pan(e), 70)

private fun World.warden(e: Enemy) {
    if (e.fireT > 0f) return
    e.fired++
    when (e.phase) {
        1 -> {
            fan(e.x - 1.8f, e.y, aim(e.x, e.y), 5, 0.7f, 6f, BShape.ORB, PINK)
            if (e.fired % 3 == 0) ring(e.x, e.y, 18, random(), 4.2f, BShape.RICE, ORANGE)
            e.fireT = 1.05f
        }
        2 -> {
            fire(e.x, e.y, e.c, 5.2f, BShape.RICE, VIOLET); fire(e.x, e.y, e.c + PIf, 5.2f, BShape.RICE, VIOLET)
            e.c += 0.19f
            if (e.fired % 18 == 0) fan(e.x - 1.8f, e.y, aim(e.x, e.y), 3, 0.4f, 6.5f, BShape.BIG, PINK)
            e.fireT = 0.09f
        }
        else -> {
            ring(e.x, e.y, 20, if (e.fired % 2 == 0) 0f else PIf / 20, 4.8f, BShape.DIAMOND, YELLOW)
            if (e.fired % 4 == 0) { bomb(e.x - 1f, e.y, aim(e.x, e.y) + 0.3f, 6f, 1.1f, 12); bomb(e.x - 1f, e.y, aim(e.x, e.y) - 0.3f, 6f, 1.1f, 12) }
            e.fireT = 0.6f
        }
    }
    if (e.phase != 2 || e.fired % 4 == 0) shotSound(e)
}

private fun World.hydra(e: Enemy) {
    if (e.fireT > 0f) return
    e.fired++
    // Three heads: up, down and the middle.
    val hx = floatArrayOf(-0.6f, -1.6f, -0.6f)
    val hy = floatArrayOf(2.4f, 0f, -2.4f)
    when (e.phase) {
        1 -> {
            val h = e.fired % 3
            fan(e.x + hx[h], e.y + hy[h], aim(e.x + hx[h], e.y + hy[h]), 3, 0.3f, 7f, BShape.RICE, if (h == 1) ORANGE else PINK)
            e.fireT = 0.34f
        }
        2 -> {
            val w = sin(e.t * 2.2f) * 0.9f
            for (h in intArrayOf(0, 2)) fire(e.x + hx[h], e.y + hy[h], PIf + w * (if (h == 0) 1 else -1), 5.6f, BShape.ORB, if (h == 0) PINK else VIOLET, turn = if (h == 0) -0.5f else 0.5f)
            if (e.fired % 12 == 0) fan(e.x + hx[1], e.y, aim(e.x, e.y), 5, 0.5f, 7f, BShape.BIG, ORANGE)
            e.fireT = 0.12f
        }
        else -> {
            ring(e.x + hx[1], e.y, 16, random(), 3.8f, BShape.ORB, PINK, turn = 0.45f)
            ring(e.x + hx[1], e.y, 16, random(), 3.8f, BShape.ORB, VIOLET, turn = -0.45f)
            for (h in intArrayOf(0, 2)) fire(e.x + hx[h], e.y + hy[h], aim(e.x + hx[h], e.y + hy[h]), 7.5f, BShape.RICE, ORANGE)
            e.fireT = 0.85f
        }
    }
    if (e.phase != 2 || e.fired % 4 == 0) shotSound(e)
}

private fun World.tempest(e: Enemy) {
    val beaming = beams.any { it.active && it.owner === e }
    if (e.fireT > 0f) return
    e.fired++
    when (e.phase) {
        1 -> {
            if (!beaming && e.fired % 10 == 1) {
                laser(e, e.x - 1f, e.y, PIf - 0.7f, 1.2f, 3.2f, 0.8f, 0.42f, ICE_COLOR)
                laser(e, e.x - 1f, e.y, PIf + 0.7f, 1.2f, 3.2f, 0.8f, -0.42f, ICE_COLOR)
                sfx.play(Snd.LASER, 0.6f, 0.5f, pan(e), 0)
            }
            fire(e.x - 1f, e.y, PIf + (random() - 0.5f) * 2.4f, 4f, BShape.ORB, VIOLET)
            e.fireT = 0.22f
        }
        2 -> {
            for (k in 0 until 3) fire(e.x, e.y, e.c + k * 2f * PIf / 3, 1.6f, BShape.RICE, World.ICE, acc = 0.9f)
            e.c += 0.17f
            e.fireT = 0.07f
        }
        else -> {
            if (!beaming && e.fired % 4 == 1) {
                val a = aim(e.x, e.y)
                for (k in -1..1) laser(e, e.x - 1f, e.y, a + k * 0.42f, 1.0f, 0.9f, 0.7f, color = ICE_COLOR)
                sfx.play(Snd.LASER, 0.6f, 0.5f, pan(e), 0)
            }
            fan(e.x - 1f, e.y, aim(e.x, e.y), 7, 1.1f, 5.5f, BShape.ORB, PINK)
            e.fireT = 1.15f
        }
    }
    if (e.phase != 2 || e.fired % 5 == 0) shotSound(e, 0.4f, 1.1f)
}

private const val ICE_COLOR = World.ICE

private fun World.revenant(e: Enemy) {
    if (e.fireT > 0f) return
    e.fired++
    when (e.phase) {
        1 -> {
            curtain(e.x - 1.5f, 5f)
            if (e.fired % 3 == 0) summonDarts(e)
            e.fireT = 2.5f
        }
        2 -> {
            bomb(e.x - 1.5f, e.y, aim(e.x, e.y) + (random() - 0.5f) * 0.6f, 6.5f, 1.1f, 14, RED)
            fan(e.x - 1.5f, e.y, aim(e.x, e.y), 3, 0.25f, 7.5f, BShape.RICE, ORANGE)
            e.fireT = 1.3f
        }
        else -> {
            if (e.fired % 6 == 0) curtain(e.x - 1.5f, 5.5f, 3.2f)
            for (k in 0 until 2) fire(e.x, e.y, e.c + k * PIf, 4.4f, BShape.ORB, RED)
            e.c -= 0.23f
            e.fireT = 0.33f
        }
    }
    shotSound(e)
}

private fun World.summonDarts(e: Enemy) {
    for (k in 0 until 2) {
        val d = newEnemy(Foe.DART, e.x, e.y + (k * 2 - 1) * 2f, difficulty, counted = false) ?: return
        d.vx = -7f; d.entered = true
    }
}

private fun World.voidHeart(e: Enemy) {
    if (e.fireT > 0f) return
    e.fired++
    when (e.phase) {
        1 -> {
            for (k in 0 until 5) fire(e.x, e.y, e.c + k * 2f * PIf / 5, 4.8f, BShape.RICE, if (k % 2 == 0) VIOLET else PINK)
            e.c += 0.13f
            e.fireT = 0.1f
        }
        2 -> {
            if (beams.none { it.active && it.owner === e } && e.fired % 6 == 1) {
                laser(e, e.x - 1f, e.y, PIf - 0.5f, 1.2f, 3.6f, 0.8f, 0.3f, VIOLET)
                laser(e, e.x - 1f, e.y, PIf + 0.5f, 1.2f, 3.6f, 0.8f, -0.3f, VIOLET)
                sfx.play(Snd.LASER, 0.6f, 0.4f, pan(e), 0)
            }
            bomb(e.x - 1f, e.y, aim(e.x, e.y) + (random() - 0.5f) * 1.2f, 6.5f, 1f, 12, VIOLET)
            e.fireT = 0.9f
        }
        else -> {
            val s = if (e.fired % 2 == 0) 1f else -1f
            ring(e.x, e.y, 22, random(), 3.6f, BShape.ORB, if (s > 0) PINK else VIOLET, acc = 0.35f, turn = 0.38f * s)
            if (e.fired % 2 == 0) fan(e.x - 1f, e.y, aim(e.x, e.y), 5, 0.4f, 8f, BShape.RICE, YELLOW)
            e.fireT = 0.5f
        }
    }
    if (e.phase != 1 || e.fired % 5 == 0) shotSound(e, 0.45f, 0.6f)
}
