package dev.m4s4t0.voidwars.game

import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Foe
import dev.m4s4t0.voidwars.model.Primary
import dev.m4s4t0.voidwars.model.Special
import dev.m4s4t0.voidwars.model.Support
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The ship's guns, support systems and the lasting specials (the singularity, the barrage). */
internal class Weapons(private val w: World) {
    private var fireT = 0f
    private var supT = 0f
    private var droneT = 0f
    var laserOn = false
    /** The beam's half-width, for the renderer. */
    var laserW = 0.3f
    val dX = FloatArray(2)
    val dY = FloatArray(2)
    var orbA = 0f
    val orbs get() = if (w.loadout.support == Support.ORBITALS) 2 + w.ship.supportLevel / 2 else 0
    val orbR = 1.75f
    var singT = 0f
    var singX = 0f
    var singY = 0f
    private var barrageN = 0
    private var barrageT = 0f
    private var hitSnd = 0f

    val invulnerable get() = w.specialT > 0f && w.loadout.special == Special.OVERDRIVE

    fun reset() {
        fireT = 0.4f; supT = 1f; droneT = 0.5f; laserOn = false; orbA = 0f; singT = 0f; barrageN = 0
        for (k in 0..1) { dX[k] = w.px; dY[k] = w.py }
    }

    private val dmg get() = w.ship.damage
    private val rate get() = w.ship.fireRate * if (invulnerable) 2.6f else 1f

    fun update(h: Float) {
        val L = w.gunLevel
        val x = w.px + 0.85f
        val y = w.py
        // Only with the trigger held (the demo ship fires by itself), and not while flying in.
        val firing = (w.trigger || w.demo) && w.px > -w.W + 0.3f && w.state == World.State.PLAYING
        laserOn = firing && w.gun == Primary.LASER
        laserW = 0.28f + 0.07f * L
        fireT = maxOf(fireT - h * rate, if (firing) -1f else 0f)
        if (firing && fireT <= 0f) {
            when (w.gun) {
                Primary.PULSE -> {
                    fireT += 0.085f
                    val d = 3.6f * dmg * (1f + 0.06f * (L - 1))
                    bolt(x, y + 0.18f, 0f, d); bolt(x, y - 0.18f, 0f, d)
                    if (L == 2 || L >= 4) bolt(x + 0.1f, y, 0f, d)
                    if (L >= 3) { val a = if (L >= 4) 0.08f else 0.06f; bolt(x - 0.3f, y + 0.45f, a, d); bolt(x - 0.3f, y - 0.45f, -a, d) }
                    if (L >= 5) { bolt(x - 0.5f, y + 0.7f, 0.16f, d); bolt(x - 0.5f, y - 0.7f, -0.16f, d) }
                    w.sfx.play(Snd.SHOT, 0.18f, 1f, -0.6f, 70)
                }
                Primary.SPREAD -> {
                    fireT += 0.15f
                    val n = 3 + 2 * L
                    val spread = 0.5f + 0.06f * L
                    for (k in 0 until n) {
                        val a = -spread / 2 + spread * k / (n - 1)
                        w.shots.add(x, y, cos(a) * 30f, sin(a) * 30f, 0.24f, 2.8f * dmg, 0.6f, SKind.PELLET)
                    }
                    w.sfx.play(Snd.SHOT_HEAVY, 0.22f, 1.3f, -0.6f, 90)
                }
                Primary.LASER -> {
                    fireT += 0.12f
                    w.sfx.play(Snd.LASER, 0.12f, 1f, -0.6f, 110)
                }
                Primary.HOMING -> {
                    fireT += 0.26f
                    val n = 2 + (L - 1) / 2
                    for (k in 0 until n) {
                        val side = if (k % 2 == 0) 1f else -1f
                        val a = side * (0.5f + 0.25f * (k / 2))
                        w.shots.add(w.px, y + side * 0.3f, cos(a) * 12f, sin(a) * 12f, 0.28f, 8.5f * dmg * (1f + 0.08f * (L - 1)), 2.6f, SKind.MISSILE)
                    }
                    w.sfx.play(Snd.MISSILE, 0.2f, 1.2f, -0.6f, 120)
                }
                Primary.PLASMA -> {
                    fireT += 0.42f
                    val r = 0.55f + 0.08f * L
                    val dps = 60f * dmg * (1f + 0.12f * (L - 1))
                    if (L >= 3) {
                        w.shots.add(x, y, cos(0.07f) * 13f, sin(0.07f) * 13f, r * 0.85f, dps * 0.75f, 2.6f, SKind.PLASMA)
                        w.shots.add(x, y, cos(-0.07f) * 13f, sin(-0.07f) * 13f, r * 0.85f, dps * 0.75f, 2.6f, SKind.PLASMA)
                    } else w.shots.add(x, y, 13f, 0f, r, dps, 2.6f, SKind.PLASMA)
                    w.sfx.play(Snd.SHOT_HEAVY, 0.3f, 0.7f, -0.6f, 100)
                }
                Primary.RAIL -> {
                    fireT += 0.6f
                    val d = 34f * dmg * (1f + 0.15f * (L - 1))
                    w.shots.add(x, y, 80f, 0f, 0.32f, d, 0.8f, SKind.RAIL)
                    if (L >= 3) {
                        w.shots.add(x, y + 0.3f, cos(0.1f) * 80f, sin(0.1f) * 80f, 0.26f, d * 0.5f, 0.8f, SKind.RAIL)
                        w.shots.add(x, y - 0.3f, cos(-0.1f) * 80f, sin(-0.1f) * 80f, 0.26f, d * 0.5f, 0.8f, SKind.RAIL)
                    }
                    w.parts.add(x + 0.3f, y, 0f, 0f, 0.12f, 0.9f, w.glow, Shape.SOFT, 0f, 6f)
                    w.sfx.play(Snd.RAIL, 0.4f, 1f, -0.6f, 0)
                    w.shake = maxOf(w.shake, 0.15f)
                }
                Primary.FLAK -> {
                    fireT += 0.32f
                    for (k in -1..1) if (k == 0 || L >= 3) w.shots.add(x, y + k * 0.2f, 24f, k * 2.5f, 0.3f, 6f * dmg, 0.38f, SKind.FLAK)
                    w.sfx.play(Snd.FLAK, 0.3f, 1f, -0.6f, 90)
                }
            }
            if (fireT < -0.2f) fireT = 0f
        }
        supports(h, firing)
        lasting(h)
    }

    private fun bolt(x: Float, y: Float, a: Float, d: Float) {
        w.shots.add(x, y, cos(a) * 40f, sin(a) * 40f, 0.22f, d, 1.2f, SKind.BOLT)
    }

    private fun supports(h: Float, firing: Boolean) {
        val sL = w.ship.supportLevel
        when (w.loadout.support) {
            Support.NONE -> Unit
            Support.DRONES -> {
                for (k in 0..1) {
                    val ty = w.py + if (k == 0) 1.5f else -1.5f
                    val tx = w.px - 0.5f
                    dX[k] += (tx - dX[k]) * min(1f, h * 8f)
                    dY[k] += (ty - dY[k]) * min(1f, h * 8f)
                }
                droneT -= h * rate
                if (firing && droneT <= 0f) {
                    droneT = 0.16f
                    for (k in 0..1) w.shots.add(dX[k] + 0.4f, dY[k], 36f, 0f, 0.18f, 2.2f * dmg * (1f + 0.2f * (sL - 1)), 1.2f, SKind.DRONE)
                }
            }
            Support.MISSILES -> {
                supT -= h
                if (firing && supT <= 0f) {
                    supT = 1.3f - 0.12f * sL
                    for (side in intArrayOf(1, -1)) w.shots.add(w.px - 0.2f, w.py + side * 0.5f, 2f, side * 9f, 0.28f, 9f * dmg * (1f + 0.15f * (sL - 1)), 2.8f, SKind.MISSILE)
                    w.sfx.play(Snd.MISSILE, 0.3f, 1f, -0.6f, 0)
                }
            }
            Support.ORBITALS -> orbA += h * 3f
            Support.TESLA -> {
                supT -= h
                if (firing && supT <= 0f) {
                    supT = 0.75f - 0.06f * sL
                    tesla(sL)
                }
            }
        }
    }

    private fun tesla(sL: Int) {
        var fromX = w.px; var fromY = w.py
        var hits = 0
        val maxHits = 2 + sL
        val reach = 8f
        var lastSeed = -1
        val d = 10f * dmg * (1f + 0.2f * (sL - 1))
        while (hits < maxHits) {
            var best: Enemy? = null
            var bd = if (hits == 0) reach else 4.5f
            for (k in 0 until w.ne) {
                val e = w.enemies[k]
                if (!e.alive || !w.onScreen(e) || e.seed == lastSeed) continue
                val dd = hypot(e.x - fromX, e.y - fromY) - e.r
                if (dd < bd) { bd = dd; best = e }
            }
            val e = best ?: break
            w.arc(fromX, fromY, e.x, e.y)
            w.burst(e.x, e.y, 0xFFB8E8FF.toInt(), 4, 6f, 0.2f)
            fromX = e.x; fromY = e.y; lastSeed = e.seed
            w.damage(e, d)
            hits++
        }
        if (hits > 0) w.sfx.play(Snd.ZAP, 0.35f, 1f, 0f, 60)
    }

    fun singularity(x: Float, y: Float) {
        singT = w.specialMax; singX = x; singY = y
    }

    fun barrage(n: Int) {
        barrageN = n; barrageT = 0f
    }

    private fun lasting(h: Float) {
        if (barrageN > 0) {
            barrageT -= h
            while (barrageN > 0 && barrageT <= 0f) {
                barrageT += 1f / 30f
                barrageN--
                val a = (w.random() - 0.5f) * 3f
                w.shots.add(w.px, w.py, cos(a) * 14f, sin(a) * 14f, 0.3f, 13f * dmg * (1f + 0.25f * (w.ship.specialLevel - 1)), 3f, SKind.MISSILE)
                w.sfx.play(Snd.MISSILE, 0.3f, 1.3f, 0f, 50)
            }
        }
        if (singT > 0f) {
            singT -= h
            singX += h * 0.8f
            val power = 70f * dmg * (1f + 0.25f * (w.ship.specialLevel - 1))
            val b = w.bullets
            var i = b.n - 1
            while (i >= 0) {
                val dx = singX - b.x[i]; val dy = singY - b.y[i]
                val d2 = dx * dx + dy * dy
                if (d2 < 0.8f * 0.8f) { w.score += 5; b.remove(i) }
                else if (d2 < 7f * 7f) {
                    val d = kotlin.math.sqrt(d2)
                    val pull = 26f / (d + 0.5f)
                    b.vx[i] += dx / d * pull * h * 4f
                    b.vy[i] += dy / d * pull * h * 4f
                }
                i--
            }
            for (k in 0 until w.ne) {
                val e = w.enemies[k]
                if (!e.alive) continue
                val dx = singX - e.x; val dy = singY - e.y
                val d = hypot(dx, dy)
                if (d < 5f + e.r) {
                    val heavy = e.foe == Foe.BOSS || e.foe == Foe.CARRIER
                    w.damage(e, power * h * if (heavy) 0.35f else 1f)
                    if (!heavy && d > 0.3f) { e.vx += dx / d * 18f * h; e.vy += dy / d * 18f * h }
                }
            }
            if (w.rnd.nextFloat() < 0.8f) {
                val a = w.random() * 6.283f
                val r = 3.5f + w.random() * 2f
                w.parts.add(singX + cos(a) * r, singY + sin(a) * r, -cos(a + 0.6f) * r * 2.5f, -sin(a + 0.6f) * r * 2.5f, 0.4f, 0.25f, 0xFFB080FF.toInt(), Shape.SPARK, 0.5f)
            }
        }
    }

    // ---------------------------------------------------------------- shots in flight

    fun updateShots(h: Float) {
        val s = w.shots
        var i = s.n - 1
        while (i >= 0) {
            s.age[i] += h
            when (s.kind[i]) {
                SKind.MISSILE -> {
                    val t = nearest(s.x[i], s.y[i])
                    var vx = s.vx[i]; var vy = s.vy[i]
                    if (t != null && s.age[i] > 0.12f) {
                        val want = atan2(t.y - s.y[i], t.x - s.x[i])
                        val cur = atan2(vy, vx)
                        var da = want - cur
                        while (da > Math.PI) da -= (2 * Math.PI).toFloat()
                        while (da < -Math.PI) da += (2 * Math.PI).toFloat()
                        val turn = (da).coerceIn(-8f * h, 8f * h)
                        val sp = hypot(vx, vy)
                        vx = cos(cur + turn) * sp; vy = sin(cur + turn) * sp
                    }
                    val sp = hypot(vx, vy)
                    val ns = min(28f, sp + 40f * h)
                    if (sp > 1e-3f) { vx *= ns / sp; vy *= ns / sp }
                    s.vx[i] = vx; s.vy[i] = vy
                    if (w.rnd.nextFloat() < 0.6f) w.parts.add(s.x[i], s.y[i], -vx * 0.1f, -vy * 0.1f, 0.25f, 0.22f, 0xFFFFB070.toInt(), Shape.SOFT, 3f, -0.5f)
                }
                SKind.PLASMA -> eatBullets(s.x[i], s.y[i], s.r[i])
            }
            s.x[i] += s.vx[i] * h
            s.y[i] += s.vy[i] * h
            if (s.age[i] >= s.life[i]) {
                if (s.kind[i] == SKind.FLAK) shrapnel(s.x[i], s.y[i])
                s.remove(i)
            } else if (s.x[i] > w.W + 2f || s.x[i] < -w.W - 2f || abs(s.y[i]) > World.H + 2f) s.remove(i)
            i--
        }
    }

    private fun nearest(x: Float, y: Float): Enemy? {
        var best: Enemy? = null
        var bd = Float.MAX_VALUE
        for (k in 0 until w.ne) {
            val e = w.enemies[k]
            if (!e.alive || !w.onScreen(e) || e.x < x - 3f) continue
            val d = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y)
            if (d < bd) { bd = d; best = e }
        }
        return best
    }

    private fun eatBullets(x: Float, y: Float, r: Float) {
        val b = w.bullets
        var i = b.n - 1
        while (i >= 0) {
            val dx = b.x[i] - x; val dy = b.y[i] - y
            val rr = r + b.r[i]
            if (dx * dx + dy * dy < rr * rr) {
                w.parts.add(b.x[i], b.y[i], 0f, 0f, 0.2f, 0.4f, w.glow, Shape.SOFT, 0f, 1f)
                b.remove(i)
            }
            i--
        }
    }

    private fun shrapnel(x: Float, y: Float) {
        val n = 8 + 2 * w.gunLevel
        for (k in 0 until n) {
            val a = -0.9f + 1.8f * k / (n - 1) + (w.random() - 0.5f) * 0.1f
            val sp = 18f + w.random() * 6f
            w.shots.add(x, y, cos(a) * sp, sin(a) * sp, 0.18f, 2.6f * dmg, 0.3f, SKind.SHRAPNEL)
        }
        w.parts.add(x, y, 0f, 0f, 0.15f, 0.9f, 0xFFFFD090.toInt(), Shape.SOFT, 0f, 5f)
        w.sfx.play(Snd.FLAK, 0.2f, 1.5f, 0f, 70)
    }

    // ---------------------------------------------------------------- hits

    fun hitEnemies(h: Float) {
        hitSnd -= h
        val s = w.shots
        var i = s.n - 1
        while (i >= 0) {
            val kind = s.kind[i]
            var gone = false
            for (k in 0 until w.ne) {
                val e = w.enemies[k]
                if (!e.alive || !w.onScreen(e)) continue
                val dx = e.x - s.x[i]; val dy = e.y - s.y[i]
                val rr = e.r * e.size + s.r[i]
                if (dx * dx + dy * dy > rr * rr) continue
                when (kind) {
                    SKind.PLASMA -> {
                        w.damage(e, s.dmg[i] * h)
                        if (w.rnd.nextFloat() < 0.3f) spark(s.x[i] + dx * 0.5f, s.y[i] + dy * 0.5f)
                    }
                    SKind.RAIL -> if (s.last[i] != e.seed) {
                        s.last[i] = e.seed
                        w.damage(e, s.dmg[i])
                        spark(e.x - e.r * 0.5f, s.y[i])
                        hitSound(e)
                    }
                    else -> {
                        w.damage(e, s.dmg[i])
                        spark(s.x[i], s.y[i])
                        hitSound(e)
                        if (kind == SKind.FLAK) shrapnel(s.x[i], s.y[i])
                        gone = true
                    }
                }
                if (gone) break
            }
            if (gone) s.remove(i)
            i--
        }
        if (laserOn) laser(h)
        val n = orbs
        if (n > 0 && w.alive) {
            val sL = w.ship.supportLevel
            for (k in 0 until n) {
                val a = orbA + k * 6.2832f / n
                val ox = w.px + cos(a) * orbR; val oy = w.py + sin(a) * orbR
                eatBullets(ox, oy, 0.4f)
                for (j in 0 until w.ne) {
                    val e = w.enemies[j]
                    if (!e.alive) continue
                    val rr = e.r * e.size + 0.4f
                    if ((e.x - ox) * (e.x - ox) + (e.y - oy) * (e.y - oy) < rr * rr) w.damage(e, 35f * dmg * (1f + 0.2f * (sL - 1)) * h)
                }
            }
        }
    }

    private fun laser(h: Float) {
        val L = w.gunLevel
        val dps = (36f + 11f * L) * dmg * rate
        val x0 = w.px + 0.8f
        for (k in 0 until w.ne) {
            val e = w.enemies[k]
            if (!e.alive || !w.onScreen(e) || e.x < x0 - e.r) continue
            if (abs(e.y - w.py) < e.r * e.size + laserW) {
                w.damage(e, dps * h)
                if (w.rnd.nextFloat() < 0.35f) spark(e.x - e.r * 0.7f, w.py + (w.random() - 0.5f) * laserW)
            }
        }
    }

    private fun spark(x: Float, y: Float) {
        for (k in 0 until 2) w.parts.add(x, y, -4f - w.random() * 8f, (w.random() - 0.5f) * 9f, 0.18f, 0.16f, w.glow, Shape.SPARK, 4f)
    }

    private fun hitSound(e: Enemy) {
        if (hitSnd > 0f) return
        hitSnd = 0.05f
        val rock = e.foe == Foe.ROCK_S || e.foe == Foe.ROCK_M || e.foe == Foe.ROCK_L
        w.sfx.play(Snd.HIT, if (rock) 0.25f else 0.3f, if (rock) 0.6f else 1f, w.pan(e), 40)
    }
}
