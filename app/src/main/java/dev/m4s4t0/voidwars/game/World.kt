package dev.m4s4t0.voidwars.game

import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Boss
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Engine
import dev.m4s4t0.voidwars.model.Foe
import dev.m4s4t0.voidwars.model.Loadout
import dev.m4s4t0.voidwars.model.RunResult
import dev.m4s4t0.voidwars.model.ShipStats
import dev.m4s4t0.voidwars.model.Special
import dev.m4s4t0.voidwars.model.Stage
import dev.m4s4t0.voidwars.model.Wave
import dev.m4s4t0.voidwars.model.swatch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Where the world's sounds and buzzes go (stubbed in tests). */
interface Sfx {
    fun play(s: Snd, gain: Float = 1f, pitch: Float = 1f, pan: Float = 0f, minGapMs: Long = 25)
    fun buzz(k: Haptics.Kind, strength: Float = 1f)
}

object DeviceSfx : Sfx {
    override fun play(s: Snd, gain: Float, pitch: Float, pan: Float, minGapMs: Long) = Audio.play(s, gain, pitch, pan, minGapMs)
    override fun buzz(k: Haptics.Kind, strength: Float) = Haptics.buzz(k, strength)
}

/** What the HUD shows, sent about 20 times a second. */
data class Hud(
    val hull: Float,
    val score: Long,
    val credits: Int,
    val chain: Int,
    val mult: Float,
    val charge: Float,
    val specialOn: Float,
    val progress: Float,
    val bossHp: Float,
    val boss: Boss?,
    val seconds: Float,
    val grazes: Int,
)

sealed interface WorldEvent {
    data class BossWarning(val boss: Boss) : WorldEvent
    data class BossDown(val boss: Boss) : WorldEvent
    data object StageClear : WorldEvent
    data class Died(val canRevive: Boolean) : WorldEvent
    data class Finished(val result: RunResult) : WorldEvent
    data class Callout(val text: String, val big: Boolean = false) : WorldEvent
}

interface WorldListener {
    fun hud(h: Hud)
    fun event(e: WorldEvent)
}

/**
 * The game: one run of a stage (or the Rift, or the menu's attract loop). Lives on the GL thread and is stepped by the
 * renderer each frame in small fixed-size steps. World units: the screen is 18 tall (y -9..9) and [W] * 2 wide; the
 * ship flies right, enemies come from the right.
 */
class World(val sfx: Sfx = DeviceSfx) {
    companion object {
        const val H = 9f
        const val STEP = 1f / 90f
        val BULLET_COLORS = intArrayOf(0xFFFF3D8B.toInt(), 0xFFFF8A2A.toInt(), 0xFFFFE14D.toInt(), 0xFFFF3B3B.toInt(), 0xFFC070FF.toInt(), 0xFF7CC8FF.toInt())
        const val PINK = 0
        const val ORANGE = 1
        const val YELLOW = 2
        const val RED = 3
        const val VIOLET = 4
        const val ICE = 5
    }

    enum class State { IDLE, PLAYING, CLEAR, DOWN, OVER }

    var listener: WorldListener? = null
    /** Half the screen's width in world units (set from the aspect ratio). */
    var W = 16f

    val bullets = Bullets(1600)
    val shots = Shots(700)
    val parts = Particles(2400)
    val pickups = Pickups(240)
    val enemies = Array(160) { Enemy() }
    var ne = 0
    val beams = Array(10) { Beam() }
    val arcs = Array(24) { Arc() }
    val rnd = Random(System.nanoTime())

    // ---- the run ----
    var state = State.IDLE
        private set
    var demo = false
        private set
    var stage: Stage? = null
        private set
    val rift get() = stage == null && !demo
    var difficulty = 0f
    var t = 0f
    var dirT = 0f
    private var waves: List<Wave> = emptyList()
    private var waveIdx = 0
    private var riftNext = 2f
    private var riftN = 0
    private var stageEnd = 60f
    private var pendingBoss: Wave? = null
    private var warnT = 0f
    var boss: Enemy? = null
    private var clearT = 0f
    private var downT = 0f
    private var askedRevive = false
    private var hudT = 0f
    var sector = 0

    // ---- the ship ----
    lateinit var loadout: Loadout
    lateinit var ship: ShipStats
    var px = 0f; var py = 0f
    var pvx = 0f; var pvy = 0f
    var tx = 0f; var ty = 0f
    var bank = 0f
    var hp = 100f
    var alive = true
    var iframes = 0f
    private var sinceHit = 99f
    var glow = 0xFF22E3FF.toInt()
    var trim = 0xFFA970FF.toInt()
    var charge = 0f
    var specialT = 0f
    var specialMax = 1f
    var timeScale = 1f
    var revived = false
    private var specialReady = false

    // Touch dragging (relative: the ship moves by however far the finger does).
    private var dragId = -1
    private var fx = 0f; private var fy = 0f
    private var sx = 0f; private var sy = 0f
    var unitsPerPx = 0.02f

    // ---- the tally ----
    var score = 0L
    var credits = 0
    var kills = 0; var rocks = 0; var elites = 0; var bossKills = 0
    var spawned = 0
    var grazes = 0
    var pickupsN = 0
    var specials = 0
    var hits = 0
    var chain = 0
    var chainT = 0f
    var bestChain = 0
    var chains25 = 0

    // ---- feel ----
    var shake = 0f
    var freeze = 0f
    /** A white flash over everything (specials, the boss going down). */
    var flash = 0f
    var flashColor = -1

    // ---- weapons (Weapons.kt) ----
    internal val wpn = Weapons(this)

    val mult get() = 1f + min(chain, 200) / 20f
    val hull get() = if (ship.maxHp > 0) hp / ship.maxHp else 0f
    val laserOn get() = alive && wpn.laserOn

    // ---------------------------------------------------------------- starting

    /** A stage, the Rift (stage = null) or the menu's attract loop (demo). */
    fun start(l: Loadout, stats: ShipStats, s: Stage?, demo: Boolean = false, quality: Float = 1f) {
        loadout = l
        ship = stats
        stage = s
        this.demo = demo
        parts.limit = (parts.cap * quality).toInt().coerceAtLeast(400)
        glow = argb(swatch(l.paint.glow).argb)
        trim = argb(swatch(l.paint.secondary).argb)
        bullets.n = 0; shots.n = 0; parts.n = 0; pickups.n = 0
        for (i in 0 until ne) enemies[i].alive = false
        ne = 0
        for (b in beams) { b.active = false; b.owner = null }
        for (a in arcs) a.life = 0f
        t = 0f; dirT = 0f; waveIdx = 0; riftNext = 2.5f; riftN = 0; pendingBoss = null; warnT = 0f; boss = null
        clearT = 0f; downT = 0f; askedRevive = false
        px = -W * 0.6f; py = 0f; tx = -W * 0.55f; ty = 0f; pvx = 0f; pvy = 0f; bank = 0f
        hp = ship.maxHp.toFloat(); alive = true; iframes = 1.5f; sinceHit = 99f
        charge = if (demo) 0f else 0.35f; specialT = 0f; timeScale = 1f; revived = false; specialReady = false
        dragId = -1
        score = 0; credits = 0; kills = 0; rocks = 0; elites = 0; bossKills = 0; spawned = 0; grazes = 0; pickupsN = 0
        specials = 0; hits = 0; chain = 0; chainT = 0f; bestChain = 0; chains25 = 0
        shake = 0f; freeze = 0f; flash = 0f
        wpn.reset()
        when {
            demo -> { sector = 0; difficulty = 0.5f }
            s != null -> {
                sector = s.sector
                difficulty = s.difficulty
                waves = Campaign.waves(s)
                stageEnd = waves.lastOrNull()?.at ?: Campaign.length(s)
            }
            else -> { sector = 0; difficulty = Campaign.riftDifficulty(0f) }
        }
        state = State.PLAYING
        // The ship flies in from the left edge.
        px = -W - 1.5f
    }

    fun stop() {
        state = State.IDLE
    }

    // ---------------------------------------------------------------- input

    fun touch(action: Int, id: Int, x: Float, y: Float) {
        when (action) {
            0 -> if (dragId < 0) { dragId = id; fx = x; fy = y; sx = tx; sy = ty }
            1 -> if (id == dragId) {
                val k = unitsPerPx * dev.m4s4t0.voidwars.model.Game.sensitivity
                var nx = sx + (x - fx) * k
                var ny = sy - (y - fy) * k
                val cx = nx.coerceIn(-W + 0.9f, W - 1.2f)
                val cy = ny.coerceIn(-H + 0.8f, H - 0.8f)
                // Pinned against an edge: re-anchor, so turning back responds straight away.
                if (cx != nx) { sx = cx - (x - fx) * k; nx = cx }
                if (cy != ny) { sy = cy + (y - fy) * k; ny = cy }
                tx = nx; ty = ny
            }
            2 -> if (id == dragId) dragId = -1
            3 -> dragId = -1
        }
    }

    // ---------------------------------------------------------------- the loop

    /** Advances by [dtRaw] seconds of real time. */
    fun frame(dtRaw: Float) {
        if (state == State.IDLE || state == State.OVER) { fxOnly(min(dtRaw, 0.05f)); return }
        val dt = min(dtRaw, 0.05f)
        shake = max(0f, shake - dt * 9f)
        flash = max(0f, flash - dt * 2.5f)
        if (freeze > 0f) {
            freeze -= dt
            return
        }
        var left = dt
        while (left > 1e-5f) {
            val h = min(left, STEP)
            step(h)
            left -= h
        }
        hudT -= dt
        if (hudT <= 0f && !demo) {
            hudT = 0.05f
            listener?.hud(
                Hud(
                    hull, score, credits, chain, mult, charge, if (specialMax > 0f) specialT / specialMax else 0f, progress(),
                    boss?.frac ?: 0f, boss?.boss, t, grazes,
                ),
            )
        }
    }

    private fun progress(): Float = when {
        stage == null -> 0f
        state == State.CLEAR || state == State.OVER -> 1f
        else -> (dirT / (stageEnd + 1f)).coerceIn(0f, 1f)
    }

    /** After the run (results screen): let the explosions finish. */
    private fun fxOnly(dt: Float) {
        updateParticles(dt)
        flash = max(0f, flash - dt * 2.5f)
        shake = max(0f, shake - dt * 9f)
    }

    private fun step(h: Float) {
        t += h
        val wdt = h * timeScale
        when (state) {
            State.PLAYING -> {
                director(wdt)
                if (alive) {
                    movePlayer(h)
                    wpn.update(h)
                    specials(h)
                }
            }
            State.CLEAR -> {
                clearT += h
                movePlayer(h)
                if (clearT > 3.2f) finish(true)
            }
            State.DOWN -> {
                downT += h
                if (downT > 1.3f && !askedRevive) {
                    askedRevive = true
                    listener?.event(WorldEvent.Died(!revived))
                }
            }
            else -> Unit
        }
        if (chainT > 0f) {
            chainT -= h
            if (chainT <= 0f) chain = 0
        }
        updateEnemies(wdt)
        updateBeams(wdt)
        updateBullets(wdt)
        updateShots(h)
        updatePickups(h)
        updateParticles(h)
        updateArcs(h)
        collide(h, wdt)
        sweep()
    }

    // ---------------------------------------------------------------- the director

    private fun director(dt: Float) {
        if (demo) { demoDirector(dt); return }
        dirT += dt
        pendingBoss?.let { w ->
            warnT -= dt
            if (warnT <= 0f) {
                pendingBoss = null
                spawnBoss(w.boss ?: Boss.WARDEN, difficulty)
            }
            return
        }
        val s = stage
        if (s != null) {
            while (waveIdx < waves.size && waves[waveIdx].at <= dirT) {
                val w = waves[waveIdx++]
                if (w.boss != null) {
                    pendingBoss = w; warnT = 2.6f
                    sfx.play(Snd.WARNING, 0.8f, minGapMs = 0)
                    listener?.event(WorldEvent.BossWarning(w.boss))
                    return
                }
                spawnWave(w, difficulty)
            }
            if (waveIdx >= waves.size && boss == null && liveFoes() == 0) clear()
        } else {
            // The Rift: everything, getting harder, until you fall. No new waves while a boss is up.
            if (boss != null) { dirT -= dt; return }
            difficulty = Campaign.riftDifficulty(dirT)
            sector = min(4, (difficulty / 1.2f).toInt())
            if (dirT >= riftNext) {
                val w = Campaign.riftWave(dirT, ++riftN, rnd)
                riftNext = dirT + Campaign.riftGap(w.formation, dirT)
                if (w.boss != null) {
                    pendingBoss = w; warnT = 2.6f
                    sfx.play(Snd.WARNING, 0.8f, minGapMs = 0)
                    listener?.event(WorldEvent.BossWarning(w.boss))
                } else spawnWave(w, difficulty)
            }
        }
    }

    private fun demoDirector(dt: Float) {
        dirT += dt
        // A lazy autopilot weaving through rocks and drones, guns blazing.
        tx = -W * 0.55f + sin(t * 0.31f) * 2.5f
        ty = sin(t * 0.47f) * 4.5f + sin(t * 1.3f) * 1.2f
        if (dirT >= riftNext) {
            val f = if (riftN % 3 == 2) dev.m4s4t0.voidwars.model.Formation.SINE_LINE else dev.m4s4t0.voidwars.model.Formation.ROCKS
            spawnWave(Wave(dirT, f, 5, rnd.nextFloat() * 1.2f - 0.6f, rnd.nextInt()), 0f)
            riftN++
            riftNext = dirT + 3.5f
        }
    }

    private fun liveFoes(): Int {
        var c = 0
        for (i in 0 until ne) if (enemies[i].alive) c++
        return c
    }

    private fun clear() {
        if (state != State.PLAYING) return
        state = State.CLEAR
        clearT = 0f
        val bonus = (hull * 4000 * (1f + difficulty * 0.5f)).toLong()
        score += bonus
        for (i in 0 until pickups.n) pickups.pulled[i] = true
        sfx.play(Snd.DONE, 0.9f, minGapMs = 0)
        sfx.buzz(Haptics.Kind.SUCCESS)
        listener?.event(WorldEvent.StageClear)
    }

    private fun finish(cleared: Boolean) {
        if (state == State.OVER) return
        state = State.OVER
        // Whatever's still floating is collected for you.
        for (i in 0 until pickups.n) if (pickups.kind[i] == PKind.CREDIT || pickups.kind[i] == PKind.GEM) credits += pickups.value[i]
        pickups.n = 0
        if (demo) return
        listener?.event(
            WorldEvent.Finished(
                RunResult(
                    stage, cleared, score, credits, kills, rocks, elites, bossKills, spawned, grazes, pickupsN, specials,
                    if (cleared) hull else 0f, hits, t, bestChain, chains25,
                ),
            ),
        )
    }

    /** The pilot gives up (from pause, or declining a revive). */
    fun giveUp() {
        if (state == State.OVER || state == State.IDLE) return
        finish(false)
    }

    fun revive() {
        if (state != State.DOWN) return
        revived = true
        alive = true
        hp = ship.maxHp.toFloat()
        iframes = 3f
        state = State.PLAYING
        charge = max(charge, 0.5f)
        px = -W * 0.6f; py = 0f; tx = px; ty = 0f
        clearBullets(true)
        ring(px, py, 0xFFFFFFFF.toInt(), 1f, 12f)
        flash = 0.6f; flashColor = glow
        sfx.play(Snd.SHIELD, minGapMs = 0)
    }

    // ---------------------------------------------------------------- the ship

    private fun movePlayer(h: Float) {
        if (state == State.CLEAR) { tx = W + 6f; ty = py }
        // Fly in at the start of a run.
        val maxV = if (px < -W + 0.5f) 30f else ship.speed * if (state == State.CLEAR) 1.6f else 1f
        val dx = tx - px
        val dy = ty - py
        val d = hypot(dx, dy)
        // Follow the finger tightly (bullet hell needs it), capped at the ship's top speed.
        val want = min(maxV, d * 18f)
        val nvx = if (d > 1e-4f) dx / d * want else 0f
        val nvy = if (d > 1e-4f) dy / d * want else 0f
        pvx += (nvx - pvx) * min(1f, h * 30f)
        pvy += (nvy - pvy) * min(1f, h * 30f)
        px += pvx * h
        py += pvy * h
        if (state == State.PLAYING) {
            px = px.coerceIn(-W - 2f, W - 1.2f)
            py = py.coerceIn(-H + 0.8f, H - 0.8f)
        }
        bank += ((-pvy / max(ship.speed, 1f)) * 0.75f - bank) * min(1f, h * 10f)
        if (iframes > 0f) iframes -= h
        sinceHit += h
        if (ship.regen > 0f && sinceHit > 4f && hp < ship.maxHp) hp = min(ship.maxHp.toFloat(), hp + ship.regen * ship.maxHp * 0.01f * h)
        exhaust(h)
    }

    private var trailT = 0f

    private fun exhaust(h: Float) {
        trailT -= h
        if (trailT > 0f) return
        trailT = 1f / 70f
        val e = loadout.engine
        val len = e.trail
        val offs = when (e) {
            Engine.ION, Engine.FUSION -> floatArrayOf(0f)
            Engine.AFTERBURNER -> floatArrayOf(-0.3f, 0.3f)
            Engine.QUANTUM -> floatArrayOf(-0.32f, 0f, 0.32f)
        }
        val back = -0.95f
        for (o in offs) {
            val ex = px + back
            val ey = py + o * cos(bank)
            parts.add(ex, ey, -9f * len + pvx * 0.3f + rnd.nextFloat() - 0.5f, (rnd.nextFloat() - 0.5f) * 1.2f, 0.18f * len, 0.42f, glow, Shape.SOFT, 3f, -1.2f)
            if (rnd.nextFloat() < 0.5f) parts.add(ex, ey, -12f * len, (rnd.nextFloat() - 0.5f) * 2f, 0.12f * len, 0.2f, 0xFFFFFFFF.toInt(), Shape.SPARK, 2f)
        }
    }

    /** The pilot takes [dmg]. */
    fun hurt(dmg: Float) {
        if (demo || !alive || iframes > 0f || wpn.invulnerable || state != State.PLAYING) return
        hp -= dmg
        hits++
        sinceHit = 0f
        iframes = ship.iframes
        if (chain >= 10) listener?.event(WorldEvent.Callout("Chain broken"))
        chain = 0; chainT = 0f
        shake = max(shake, 0.9f)
        freeze = 0.07f
        flash = 0.35f; flashColor = 0xFFFF3050.toInt()
        sfx.play(Snd.HURT, minGapMs = 0)
        sfx.buzz(Haptics.Kind.HIT)
        burst(px, py, 0xFFFF6A5A.toInt(), 22, 9f, 0.5f)
        // A breather: the bullets right around the ship vanish.
        clearNear(px, py, 3.2f)
        if (hp <= 0f) die()
    }

    private fun die() {
        hp = 0f
        alive = false
        state = State.DOWN
        downT = 0f
        askedRevive = false
        wpn.laserOn = false
        explode(px, py, 2.2f, glow)
        explode(px, py, 1.2f, 0xFFFFFFFF.toInt())
        shake = 1.6f
        freeze = 0.15f
        sfx.play(Snd.BIG_BOOM, minGapMs = 0)
        sfx.buzz(Haptics.Kind.BIG_BOOM)
    }

    // ---------------------------------------------------------------- specials

    fun special() {
        if (!alive || state != State.PLAYING || charge < 1f || specialT > 0f || demo) return
        charge = 0f
        specialReady = false
        specials++
        val lvl = ship.specialLevel
        val power = 1f + 0.25f * (lvl - 1)
        val kind = loadout.special
        specialMax = kind.seconds * (1f + 0.1f * (lvl - 1))
        specialT = specialMax
        sfx.play(Snd.SPECIAL, minGapMs = 0)
        sfx.buzz(Haptics.Kind.BIG_BOOM, 0.8f)
        shake = 1.2f
        when (kind) {
            Special.NOVA -> {
                clearBullets(true)
                for (i in 0 until ne) {
                    val e = enemies[i]
                    if (!e.alive || !onScreen(e)) continue
                    damage(e, 140f * power * ship.damage * if (e.foe == Foe.BOSS || e.foe == Foe.CARRIER) 0.35f else 1f)
                }
                ring(px, py, glow, 1.4f, 40f)
                ring(px, py, 0xFFFFFFFF.toInt(), 0.9f, 30f)
                flash = 0.9f; flashColor = glow
                iframes = max(iframes, 1.5f)
            }
            Special.OVERDRIVE -> { ring(px, py, 0xFFFFD050.toInt(), 0.6f, 14f); flash = 0.4f; flashColor = 0xFFFFD050.toInt() }
            Special.PHASE -> { ring(px, py, 0xFF9A7CFF.toInt(), 0.8f, 20f); flash = 0.4f; flashColor = 0xFF9A7CFF.toInt() }
            Special.SINGULARITY -> wpn.singularity(px + 7f, py)
            Special.BARRAGE -> { wpn.barrage((24 + 6 * lvl)); flash = 0.3f; flashColor = glow }
        }
    }

    private fun specials(h: Float) {
        if (specialT > 0f) {
            specialT -= h
            if (specialT <= 0f) specialT = 0f
        }
        timeScale = if (specialT > 0f && loadout.special == Special.PHASE) 0.4f else 1f
        if (charge >= 1f && !specialReady) {
            specialReady = true
            sfx.play(Snd.CHARGED, 0.8f, minGapMs = 0)
            listener?.event(WorldEvent.Callout("Special ready"))
        }
    }

    fun addCharge(v: Float) {
        if (specialT > 0f) return
        charge = min(1f, charge + v * ship.charge)
    }

    // ---------------------------------------------------------------- enemies

    fun newEnemy(foe: Foe, x: Float, y: Float, d: Float, counted: Boolean = true): Enemy? {
        if (ne >= enemies.size) return null
        val e = enemies[ne++]
        e.alive = true
        e.foe = foe
        e.boss = null
        e.mode = 0
        e.x = x; e.y = y; e.vx = 0f; e.vy = 0f
        e.maxHp = foe.hp * Campaign.hpScale(d)
        e.hp = e.maxHp
        e.r = foe.radius
        e.t = 0f; e.a = 0f; e.b = 0f; e.c = 0f; e.d = 0f
        e.fireT = 0.6f + rnd.nextFloat()
        e.fired = 0; e.phase = 0; e.phaseT = 0f
        e.rot = 0f; e.spin = 0f
        e.roll = 0f; e.rollV = 0f
        e.flash = 0f
        e.seed = rnd.nextInt(1 shl 20)
        e.counted = counted
        e.entered = false
        e.size = 1f
        if (counted) spawned++
        return e
    }

    private fun updateEnemies(dt: Float) {
        for (i in 0 until ne) {
            val e = enemies[i]
            if (!e.alive) continue
            e.t += dt
            if (e.flash > 0f) e.flash -= dt
            think(e, dt)
            e.x += e.vx * dt
            e.y += e.vy * dt
            e.rot += e.spin * dt
            e.roll += e.rollV * dt
            if (!e.entered && e.x < W + e.r * 0.5f && e.x > -W) e.entered = true
            if (e.entered && (e.x < -W - e.r - 1.5f || e.x > W + e.r + 6f || abs(e.y) > H + e.r + 3f)) {
                e.alive = false
                if (e === boss) boss = null
            }
        }
    }

    fun onScreen(e: Enemy) = e.x < W + e.r * 0.3f && e.x > -W - e.r && abs(e.y) < H + e.r

    /** Deals [amount] to [e]; true if it died. */
    fun damage(e: Enemy, amount: Float): Boolean {
        if (!e.alive || amount <= 0f) return false
        // Bosses shrug off damage while flying in.
        if (e.foe == Foe.BOSS && e.phase < 0) return false
        e.hp -= amount
        e.flash = 0.06f
        if (e.hp <= 0f) {
            kill(e)
            return true
        }
        if (e.foe == Foe.BOSS) bossPhaseCheck(e)
        return false
    }

    fun kill(e: Enemy) {
        if (!e.alive) return
        e.alive = false
        val f = e.foe
        if (e.counted || f == Foe.BOSS) kills++
        if (!e.counted && e.foe.name.startsWith("ROCK")) { spawned++; kills++ }
        chain++
        chainT = 2.4f
        if (chain > bestChain) bestChain = chain
        if (chain == 25) chains25++
        if (chain == 25 || chain == 50 || chain == 100 || chain == 200 || (chain > 200 && chain % 100 == 0)) {
            listener?.event(WorldEvent.Callout("x$chain chain!", chain >= 100))
            sfx.play(Snd.POWER, 0.6f, minGapMs = 0)
        }
        score += (f.score * mult).toLong()
        addCharge(f.score * 0.00045f)
        val pan = (e.x / W).coerceIn(-1f, 1f)
        when (f) {
            Foe.ROCK_S, Foe.ROCK_M, Foe.ROCK_L -> {
                rocks++
                rockBurst(e)
                sfx.play(Snd.ROCK, 0.5f + e.r * 0.3f, 1.25f - e.r * 0.25f, pan, 30)
                if (f != Foe.ROCK_S) {
                    val kid = if (f == Foe.ROCK_L) Foe.ROCK_M else Foe.ROCK_S
                    val n = if (f == Foe.ROCK_L) 3 else 2 + rnd.nextInt(2)
                    for (k in 0 until n) {
                        val c = newEnemy(kid, e.x, e.y, difficulty, counted = false) ?: break
                        val a = rnd.nextFloat() * 6.283f
                        c.vx = e.vx * 0.6f + cos(a) * 3.5f
                        c.vy = e.vy * 0.6f + sin(a) * 3.5f
                        c.rollV = (rnd.nextFloat() - 0.5f) * 5f
                        c.spin = (rnd.nextFloat() - 0.5f) * 3f
                        c.entered = true
                        c.mode = 1
                        c.size = 0.85f + rnd.nextFloat() * 0.3f
                    }
                }
                sfx.buzz(Haptics.Kind.TICK, 0.3f + e.r * 0.2f)
            }
            Foe.BOSS -> bossDown(e)
            Foe.CARRIER -> {
                elites++
                explode(e.x, e.y, 2.6f, BULLET_COLORS[ORANGE])
                for (k in 0 until 5) explode(e.x + rnd.nextFloat() * 3f - 1.5f, e.y + rnd.nextFloat() * 2f - 1f, 1.2f, 0xFFFFC060.toInt())
                drop(e.x, e.y, PKind.REPAIR, 1)
                drop(e.x, e.y, PKind.CHARGE, 1)
                clearBullets(true)
                shake = max(shake, 1.4f)
                freeze = 0.1f
                sfx.play(Snd.BIG_BOOM, 1f, pan = pan, minGapMs = 0)
                sfx.buzz(Haptics.Kind.BIG_BOOM)
            }
            else -> {
                val big = e.r > 0.8f
                explode(e.x, e.y, e.r * 1.4f, BULLET_COLORS[if (big) ORANGE else PINK])
                sfx.play(if (big) Snd.BIG_BOOM else Snd.BOOM, if (big) 0.8f else 0.55f, 1f, pan, if (big) 40 else 35)
                sfx.buzz(if (big) Haptics.Kind.BOOM else Haptics.Kind.TICK, if (big) 0.8f else 0.4f)
                if (big) shake = max(shake, 0.4f)
                if (f == Foe.GUNSHIP || f == Foe.SPINNER || f == Foe.LANCER) {
                    val roll = rnd.nextFloat()
                    if (roll < 0.12f) drop(e.x, e.y, PKind.REPAIR, 1) else if (roll < 0.3f) drop(e.x, e.y, PKind.CHARGE, 1)
                }
            }
        }
        // Credits: the foe's worth, a little more in harder places, as coins (and gems for the big ones).
        if (f != Foe.BOSS) {
            var value = (f.credits * (1f + difficulty * 0.18f) * (0.7f + rnd.nextFloat() * 0.6f)).toInt().coerceAtLeast(1)
            if (!e.counted && f != Foe.ROCK_S && f != Foe.ROCK_M) value = max(1, value / 2)
            while (value >= 10) { drop(e.x, e.y, PKind.GEM, 10); value -= 10 }
            var coins = 0
            while (value > 0 && coins < 5) { val v = min(value, 1 + value / 4); drop(e.x, e.y, PKind.CREDIT, v); value -= v; coins++ }
        }
        // Linked beams die with their owner.
        for (b in beams) if (b.active && b.owner === e) { b.active = false; b.owner = null }
    }

    /** Takes dead enemies out of the pool. */
    private fun sweep() {
        var i = ne - 1
        while (i >= 0) {
            if (!enemies[i].alive) {
                val dead = enemies[i]
                for (b in beams) if (b.owner === dead) { b.active = false; b.owner = null }
                enemies[i] = enemies[ne - 1]
                enemies[ne - 1] = dead
                ne--
            }
            i--
        }
    }

    // ---------------------------------------------------------------- bullets

    fun fire(x: Float, y: Float, ang: Float, speed: Float, shape: Int = BShape.ORB, color: Int = PINK, acc: Float = 0f, turn: Float = 0f): Int {
        val s = speed * Campaign.bulletScale(difficulty)
        val r = when (shape) { BShape.BIG -> 0.42f; BShape.RICE -> 0.16f; BShape.DIAMOND -> 0.2f; BShape.RING -> 0.26f; else -> 0.2f }
        return bullets.add(x, y, cos(ang) * s, sin(ang) * s, r, shape, color, acc, turn)
    }

    fun aim(x: Float, y: Float) = atan2(py - y, px - x)

    fun fan(x: Float, y: Float, ang: Float, n: Int, spread: Float, speed: Float, shape: Int = BShape.ORB, color: Int = PINK) {
        if (n == 1) { fire(x, y, ang, speed, shape, color); return }
        for (k in 0 until n) fire(x, y, ang - spread / 2 + spread * k / (n - 1), speed, shape, color)
    }

    fun ring(x: Float, y: Float, n: Int, offset: Float, speed: Float, shape: Int = BShape.ORB, color: Int = PINK, acc: Float = 0f, turn: Float = 0f) {
        for (k in 0 until n) fire(x, y, offset + k * 6.2832f / n, speed, shape, color, acc, turn)
    }

    /** A big orb that bursts into a ring of [n] after [fuse] seconds. */
    fun bomb(x: Float, y: Float, ang: Float, speed: Float, fuse: Float, n: Int, color: Int = ORANGE) {
        val i = fire(x, y, ang, speed, BShape.BIG, color, -0.6f)
        if (i >= 0) { bullets.fuse[i] = fuse; bullets.burst[i] = n }
    }

    fun laser(owner: Enemy?, x: Float, y: Float, ang: Float, warn: Float, life: Float, width: Float, spin: Float = 0f, color: Int = PINK): Beam? {
        val b = beams.firstOrNull { !it.active } ?: return null
        b.active = true; b.owner = owner
        b.ox = if (owner != null) x - owner.x else 0f; b.oy = if (owner != null) y - owner.y else 0f
        b.x = x; b.y = y; b.ang = ang; b.spin = spin; b.width = width; b.warn = warn; b.life = life; b.age = 0f; b.color = color
        return b
    }

    private fun updateBullets(dt: Float) {
        val b = bullets
        var i = b.n - 1
        while (i >= 0) {
            b.age[i] += dt
            if (b.turn[i] != 0f) {
                val c = cos(b.turn[i] * dt); val s = sin(b.turn[i] * dt)
                val vx = b.vx[i]
                b.vx[i] = vx * c - b.vy[i] * s
                b.vy[i] = vx * s + b.vy[i] * c
            }
            if (b.acc[i] != 0f) {
                val sp = hypot(b.vx[i], b.vy[i])
                val ns = (sp * (1f + b.acc[i] * dt)).coerceIn(0.6f, 22f)
                if (sp > 1e-4f) { b.vx[i] *= ns / sp; b.vy[i] *= ns / sp }
            }
            b.x[i] += b.vx[i] * dt
            b.y[i] += b.vy[i] * dt
            if (b.fuse[i] > 0f && b.age[i] >= b.fuse[i]) {
                val bx = b.x[i]; val by = b.y[i]; val n = b.burst[i]; val c = b.color[i]
                b.remove(i)
                ring(bx, by, n, rnd.nextFloat(), 5.5f, BShape.RICE, c)
                burst(bx, by, BULLET_COLORS[c], 8, 5f, 0.3f)
                sfx.play(Snd.ENEMY_SHOT, 0.5f, 0.8f, minGapMs = 60)
                i--
                continue
            }
            val m = b.r[i] + 1f
            if (b.x[i] < -W - m || b.x[i] > W + m + 4f || b.y[i] < -H - m || b.y[i] > H + m) b.remove(i)
            i--
        }
    }

    /** Wipes the screen's bullets; [credit] turns them into a sparkle of score. */
    fun clearBullets(credit: Boolean) {
        val b = bullets
        for (i in 0 until b.n) {
            if (i % 3 == 0) parts.add(b.x[i], b.y[i], 0f, 0f, 0.4f, 0.5f, BULLET_COLORS[b.color[i]], Shape.STAR, 0f, -1f)
            if (credit) score += (5 * mult).toLong()
        }
        b.n = 0
        for (bm in beams) if (bm.active) { bm.active = false; bm.owner = null }
    }

    private fun clearNear(x: Float, y: Float, r: Float) {
        val b = bullets
        var i = b.n - 1
        while (i >= 0) {
            val dx = b.x[i] - x; val dy = b.y[i] - y
            if (dx * dx + dy * dy < r * r) {
                parts.add(b.x[i], b.y[i], dx * 2f, dy * 2f, 0.3f, 0.35f, BULLET_COLORS[b.color[i]], Shape.SOFT, 4f)
                b.remove(i)
            }
            i--
        }
    }

    private fun updateBeams(dt: Float) {
        for (b in beams) {
            if (!b.active) continue
            b.age += dt
            b.ang += b.spin * dt
            val o = b.owner
            if (o != null) {
                if (!o.alive) { b.active = false; b.owner = null; continue }
                b.x = o.x + b.ox; b.y = o.y + b.oy
            }
            if (b.age > b.warn + b.life) { b.active = false; b.owner = null; continue }
            if (b.firing && rnd.nextFloat() < 0.5f) {
                val l = rnd.nextFloat() * 30f
                parts.add(b.x + cos(b.ang) * l, b.y + sin(b.ang) * l, (rnd.nextFloat() - 0.5f) * 4f, (rnd.nextFloat() - 0.5f) * 4f, 0.25f, 0.3f, BULLET_COLORS[b.color], Shape.SPARK, 3f)
            }
        }
    }

    // ---------------------------------------------------------------- shots, pickups, particles

    private fun updateShots(h: Float) = wpn.updateShots(h)

    fun drop(x: Float, y: Float, kind: Int, value: Int) {
        val a = rnd.nextFloat() * 6.283f
        val s = 2f + rnd.nextFloat() * 4f
        pickups.add(x, y, cos(a) * s, sin(a) * s, kind, value)
    }

    private fun updatePickups(h: Float) {
        val p = pickups
        val mag = ship.magnet
        var i = p.n - 1
        while (i >= 0) {
            p.age[i] += h
            val dx = px - p.x[i]; val dy = py - p.y[i]
            val d = sqrt(dx * dx + dy * dy)
            if (alive && (d < mag || p.pulled[i])) p.pulled[i] = true
            if (p.pulled[i] && alive) {
                val sp = 10f + p.age[i] * 25f
                p.vx[i] += (dx / max(d, 0.01f) * sp - p.vx[i]) * min(1f, h * 10f)
                p.vy[i] += (dy / max(d, 0.01f) * sp - p.vy[i]) * min(1f, h * 10f)
            } else {
                // Drift with the starfield, slowing from the burst.
                p.vx[i] += (-1.6f - p.vx[i]) * min(1f, h * 2.5f)
                p.vy[i] *= 1f - min(1f, h * 2.5f)
            }
            p.x[i] += p.vx[i] * h
            p.y[i] += p.vy[i] * h
            if (alive && d < 0.75f) {
                collect(p.kind[i], p.value[i], p.x[i], p.y[i])
                p.remove(i)
            } else if (p.x[i] < -W - 1f) p.remove(i)
            i--
        }
    }

    private fun collect(kind: Int, value: Int, x: Float, y: Float) {
        when (kind) {
            PKind.CREDIT, PKind.GEM -> {
                credits += value
                score += value * 10L
                sfx.play(Snd.COIN, if (kind == PKind.GEM) 0.6f else 0.35f, 1f + min(chain, 50) * 0.01f, minGapMs = 45)
                parts.add(x, y, 0f, 0f, 0.25f, 0.6f, 0xFFFFD050.toInt(), Shape.STAR, 0f, 2f)
            }
            PKind.REPAIR -> {
                pickupsN++
                hp = min(ship.maxHp.toFloat(), hp + ship.maxHp * 0.2f)
                sfx.play(Snd.PICKUP, minGapMs = 0)
                ring(px, py, 0xFF6BFF8A.toInt(), 0.5f, 6f)
                listener?.event(WorldEvent.Callout("Hull repaired"))
            }
            PKind.CHARGE -> {
                pickupsN++
                charge = min(1f, charge + 0.3f)
                sfx.play(Snd.PICKUP, 1f, 1.2f, minGapMs = 0)
                ring(px, py, glow, 0.5f, 6f)
            }
        }
    }

    private fun updateParticles(h: Float) {
        val p = parts
        var i = p.n - 1
        while (i >= 0) {
            p.life[i] -= h
            if (p.life[i] <= 0f) { p.remove(i); i--; continue }
            val k = max(0f, 1f - p.drag[i] * h)
            p.vx[i] *= k; p.vy[i] *= k
            p.x[i] += p.vx[i] * h
            p.y[i] += p.vy[i] * h
            p.size[i] = max(0.01f, p.size[i] + p.grow[i] * h)
            i--
        }
    }

    private fun updateArcs(h: Float) {
        for (a in arcs) if (a.life > 0f) a.life -= h
    }

    fun arc(x1: Float, y1: Float, x2: Float, y2: Float) {
        val a = arcs.firstOrNull { it.life <= 0f } ?: return
        a.x1 = x1; a.y1 = y1; a.x2 = x2; a.y2 = y2; a.life = 0.14f; a.seed = rnd.nextInt()
    }

    // ---------------------------------------------------------------- collisions

    private fun collide(h: Float, wdt: Float) {
        wpn.hitEnemies(h)
        if (!alive || state != State.PLAYING || demo) return
        val phase = specialT > 0f && loadout.special == Special.PHASE
        val hb = ship.hitbox
        val gz = ship.graze
        val b = bullets
        var i = b.n - 1
        while (i >= 0) {
            val dx = b.x[i] - px; val dy = b.y[i] - py
            val rr = b.r[i]
            val d2 = dx * dx + dy * dy
            val reach = gz + rr
            if (d2 < reach * reach) {
                val hr = hb + rr * 0.75f
                if (d2 < hr * hr && !phase && iframes <= 0f && !wpn.invulnerable) {
                    b.remove(i)
                    hurt(9f + difficulty * 2.2f)
                    if (!alive) return
                    i--
                    continue
                }
                if (!b.grazed[i]) {
                    b.grazed[i] = true
                    grazes++
                    score += (20 * mult).toLong()
                    addCharge(0.007f)
                    chainT = max(chainT, 0.8f)
                    parts.add(px + dx * 0.5f, py + dy * 0.5f, dx * 3f, dy * 3f, 0.25f, 0.4f, 0xFFFFFFFF.toInt(), Shape.SPARK, 5f)
                    sfx.play(Snd.GRAZE, 0.4f, 1f, minGapMs = 40)
                }
            }
            i--
        }
        // Bodies.
        for (k in 0 until ne) {
            val e = enemies[k]
            if (!e.alive) continue
            val dx = e.x - px; val dy = e.y - py
            val rr = e.r * e.size * 0.8f + hb + 0.25f
            if (dx * dx + dy * dy < rr * rr) {
                if (!phase) hurt(if (e.foe == Foe.BOSS || e.foe == Foe.CARRIER) 18f else 14f + difficulty * 2f)
                if (e.foe != Foe.BOSS && e.foe != Foe.CARRIER) damage(e, 40f)
                if (!alive) return
            }
        }
        // Lasers.
        if (!phase) for (bm in beams) {
            if (!bm.active || !bm.firing) continue
            val c = cos(bm.ang); val s = sin(bm.ang)
            val dx = px - bm.x; val dy = py - bm.y
            val along = dx * c + dy * s
            val across = abs(-dx * s + dy * c)
            if (along > 0f && across < bm.width * 0.42f + hb) { hurt(14f + difficulty * 2f); if (!alive) return }
        }
    }

    // ---------------------------------------------------------------- effects

    fun burst(x: Float, y: Float, color: Int, n: Int, speed: Float, life: Float) {
        for (k in 0 until n) {
            val a = rnd.nextFloat() * 6.283f
            val s = speed * (0.3f + rnd.nextFloat())
            parts.add(x, y, cos(a) * s, sin(a) * s, life * (0.5f + rnd.nextFloat()), 0.18f + rnd.nextFloat() * 0.15f, color, Shape.SPARK, 3f)
        }
    }

    fun ring(x: Float, y: Float, color: Int, life: Float, grow: Float) {
        parts.add(x, y, 0f, 0f, life, 0.3f, color, Shape.WAVE, 0f, grow)
    }

    /** An explosion of radius about [size]: a flash, a shock ring, fire and sparks. */
    fun explode(x: Float, y: Float, size: Float, color: Int) {
        parts.add(x, y, 0f, 0f, 0.18f, size * 2.2f, 0xFFFFFFFF.toInt(), Shape.SOFT, 0f, size * 4f)
        ring(x, y, color, 0.35f + size * 0.1f, size * 9f)
        val fire = (6 + size * 7).toInt()
        for (k in 0 until fire) {
            val a = rnd.nextFloat() * 6.283f
            val s = (1f + rnd.nextFloat() * 3f) * size
            parts.add(x, y, cos(a) * s, sin(a) * s, 0.35f + rnd.nextFloat() * 0.4f, size * (0.5f + rnd.nextFloat() * 0.6f), if (k % 3 == 0) 0xFFFFE0A0.toInt() else color, Shape.SOFT, 2.5f, -size * 0.6f)
        }
        burst(x, y, 0xFFFFF0D0.toInt(), (8 + size * 10).toInt(), 10f * (0.6f + size * 0.4f), 0.5f)
    }

    private fun rockBurst(e: Enemy) {
        val s = e.r * e.size
        parts.add(e.x, e.y, 0f, 0f, 0.15f, s * 1.8f, 0xFFFFF0E0.toInt(), Shape.SOFT, 0f, s * 3f)
        val n = (6 + s * 10).toInt()
        for (k in 0 until n) {
            val a = rnd.nextFloat() * 6.283f
            val sp = (2f + rnd.nextFloat() * 6f)
            parts.add(e.x, e.y, cos(a) * sp + e.vx, sin(a) * sp + e.vy, 0.5f + rnd.nextFloat() * 0.5f, 0.12f + rnd.nextFloat() * 0.18f, 0xFFB09A88.toInt(), Shape.DIAMOND, 1.5f)
        }
        burst(e.x, e.y, 0xFFFFC080.toInt(), (4 + s * 6).toInt(), 7f, 0.35f)
    }

    // ---------------------------------------------------------------- bosses

    private fun bossPhaseCheck(e: Enemy) {
        val p = when {
            e.frac < 0.34f -> 3
            e.frac < 0.67f -> 2
            else -> 1
        }
        if (p > e.phase) {
            e.phase = p
            e.phaseT = 0f
            e.fireT = 1.2f
            // A phase change wipes the screen and gives a breath.
            clearBullets(true)
            explode(e.x, e.y, 2f, BULLET_COLORS[ORANGE])
            shake = max(shake, 1f)
            freeze = 0.12f
            sfx.play(Snd.BIG_BOOM, 0.9f, minGapMs = 0)
            sfx.buzz(Haptics.Kind.BOOM)
        }
    }

    private fun bossDown(e: Enemy) {
        bossKills++
        val which = e.boss ?: Boss.WARDEN
        boss = null
        clearBullets(true)
        for (k in 0 until 8) explode(e.x + (rnd.nextFloat() - 0.5f) * 5f, e.y + (rnd.nextFloat() - 0.5f) * 4f, 1.4f + rnd.nextFloat(), if (k % 2 == 0) 0xFFFFB050.toInt() else glow)
        explode(e.x, e.y, 4f, 0xFFFFFFFF.toInt())
        ring(e.x, e.y, 0xFFFFFFFF.toInt(), 1.2f, 40f)
        flash = 1f; flashColor = -1
        shake = 2.2f
        freeze = 0.35f
        val value = (Foe.BOSS.credits * (1f + difficulty * 0.25f)).toInt()
        var left = value
        while (left > 0) { val v = min(10, left); drop(e.x, e.y, if (v == 10) PKind.GEM else PKind.CREDIT, v); left -= v }
        drop(e.x, e.y, PKind.REPAIR, 1)
        sfx.play(Snd.BOSS_BOOM, minGapMs = 0)
        sfx.buzz(Haptics.Kind.BIG_BOOM)
        listener?.event(WorldEvent.BossDown(which))
        if (rift) {
            score += 20000
            hp = min(ship.maxHp.toFloat(), hp + ship.maxHp * 0.3f)
        }
    }

    private fun spawnBoss(which: Boss, d: Float) {
        val e = newEnemy(Foe.BOSS, W + 5f, 0f, d) ?: return
        e.boss = which
        val mul = when (which) { Boss.WARDEN -> 0.8f; Boss.HYDRA -> 1f; Boss.TEMPEST -> 1.1f; Boss.REVENANT -> 1.2f; Boss.VOID_HEART -> 1.4f }
        e.maxHp *= mul * if (rift) 0.8f else 1f
        e.hp = e.maxHp
        e.r = 2.6f
        e.phase = -1
        boss = e
    }

    private fun argb(v: Long) = v.toInt()
}
