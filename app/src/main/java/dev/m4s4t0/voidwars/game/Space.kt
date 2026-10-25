package dev.m4s4t0.voidwars.game

import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.model.Foe
import dev.m4s4t0.voidwars.model.HubTheme
import dev.m4s4t0.voidwars.model.Hubs
import dev.m4s4t0.voidwars.model.Loadout
import dev.m4s4t0.voidwars.model.MissionSpec
import dev.m4s4t0.voidwars.model.Mode
import dev.m4s4t0.voidwars.model.Primary
import dev.m4s4t0.voidwars.model.RunResult
import dev.m4s4t0.voidwars.model.ShipStats
import dev.m4s4t0.voidwars.model.Special
import dev.m4s4t0.voidwars.model.Support
import dev.m4s4t0.voidwars.model.swatch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The places in the hub. The core services sit on an inner ring around the centrepiece; each mission mode has its own
 * station on the outer ring. Inside a station's dock radius the pilot can dock; picking one from the list flies there on
 * autopilot.
 */
enum class Station(
    val title: String, val blurb: String, val icon: String, val color: Int,
    val ring: Int, val slot: Int, val mode: Mode? = null, val style: Int = 0,
) {
    HANGAR("Hangar", "Refit and repaint your ship", "hangar", 0xFF22E3FF.toInt(), 0, 0, style = 1),
    CAMPAIGN("Command", "The campaign, sector by sector", "map", 0xFFFFD050.toInt(), 0, 1, style = 2),
    CONTRACTS("Contract board", "Today's paid jobs", "contract", 0xFF6BFF8A.toInt(), 0, 2, style = 2),
    MARKET("Black market", "Rare goods for void shards", "market", 0xFFC070FF.toInt(), 0, 3, style = 3),
    RIFT("The Rift", "Endless, as deep as you dare", "rift", 0xFFFF3D8B.toInt(), 0, 4, style = 4),
    MISSIONS("Mission board", "Fresh jobs in every zone", "missions", 0xFFFF8A2A.toInt(), 0, 5, style = 2),
    SETTINGS("Comms relay", "Settings and records", "settings", 0xFF7CC8FF.toInt(), 0, 6, style = 0),
    SURVIVAL_POST(Mode.SURVIVAL, 0, 0xFF9BFF3A, 5),
    BOSS_RUSH_POST(Mode.BOSS_RUSH, 1, 0xFFFF4060, 5),
    BLITZ_POST(Mode.BLITZ, 2, 0xFFFFE040, 5),
    EXPLORATION_POST(Mode.EXPLORATION, 3, 0xFF6FB6FF, 6),
    BOUNTY_POST(Mode.BOUNTY, 4, 0xFFFF7A1F, 7),
    ESCORT_POST(Mode.ESCORT, 5, 0xFF6BFFC8, 6),
    SALVAGE_POST(Mode.SALVAGE, 6, 0xFFB0B8C8, 7),
    MINING_POST(Mode.MINING, 7, 0xFFE0A060, 6),
    DEFENSE_POST(Mode.DEFENSE, 8, 0xFF40A0FF, 7),
    ACE_POST(Mode.ACE, 9, 0xFFFF4FB8, 5);

    constructor(m: Mode, slot: Int, color: Long, style: Int) : this(m.title, m.blurb, m.icon, color.toInt(), 1, slot, m, style)

    val angle: Float get() = if (ring == 0) (PI / 2 + slot * 2 * PI / 7).toFloat() else (PI / 2 + (slot + 0.5) * 2 * PI / 10).toFloat()
    val radius: Float get() = if (ring == 0) 34f else 72f
    val x: Float get() = cos(angle) * radius
    val y: Float get() = sin(angle) * radius
    val size: Float get() = if (ring == 0) 3.2f else 2.6f
    val dock: Float get() = size + 4f

    companion object {
        fun of(mode: Mode): Station = entries.first { it.mode == mode }
    }
}

/** What the free-flight HUD shows, sent about 20 times a second. */
data class SpaceHud(
    val hull: Float,
    val charge: Float,
    val boost: Float,
    val objective: Int,
    val goal: Int,
    /** Seconds left, or -1 when the mission has no clock. */
    val timeLeft: Float,
    val credits: Int,
    val score: Long,
    val seconds: Float,
    /** The convoy's or outpost's hull (0..1), or -1 when there is none. */
    val ally: Float,
    val near: Station?,
    val gun: Primary,
    val specialOn: Float,
    val kills: Int,
    val hub: Boolean,
    val autopilot: Station?,
)

/** Mission objects that aren't ships. */
object OKind {
    const val BEACON = 0
    const val POD = 1
    const val CONVOY = 2
    const val GATE = 3
    const val OUTPOST = 4
}

class Obj {
    var alive = false
    var kind = 0
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var r = 1f
    var hp = 0f; var maxHp = 0f
    var done = false
    var seen = false
    var t = 0f
    var rot = 0f
    val frac get() = if (maxHp > 0f) hp / maxHp else 0f
}

/**
 * The free-flight engine: the hub and the open-space mission modes. Unlike [World] (a side-scroller), the pilot flies
 * in every direction across a wide plane, steered with a floating stick (north is up on the screen), and the guns fire
 * on their own whenever something hostile is in range. World units match [World]'s; +y is north.
 */
class Space(val sfx: Sfx = DeviceSfx) {
    enum class State { IDLE, PLAYING, DOWN, OVER }

    var listener: WorldListener? = null
    var state = State.IDLE
        private set
    var hub = false
        private set
    /** Which hub this is, for its looks. */
    var theme: HubTheme = Hubs.all[0]
        private set
    var spec: MissionSpec? = null
        private set

    val bullets = Bullets(900)
    val shots = Shots(500)
    val parts = Particles(2400)
    val pickups = Pickups(300)
    val enemies = Array(120) { Enemy() }
    var ne = 0
        private set
    val objs = Array(40) { Obj() }
    var no = 0
        private set
    val arcs = Array(10) { Arc() }

    var loadout = Loadout()
        private set
    lateinit var ship: ShipStats
        private set
    var glow = 0xFF22E3FF.toInt()
        private set
    var trim = 0xFFFFFFFF.toInt()
        private set
    /** The fitted trail, shot colour and kill burst. */
    val flair = Flair()

    // the pilot
    var px = 0f; var py = 0f
    var pvx = 0f; var pvy = 0f
    /** Heading in radians (0 = east, counter-clockwise). */
    var ang = 0f
        private set
    var bank = 0f
        private set
    /** The fire button: the guns only fire while it's held, and the nose locks onto a target in reach. */
    var trigger = false
    /** Volleys the pilot's guns have fired this run. */
    var gunShots = 0
        private set
    var hp = 0f
        private set
    var alive = false
        private set
    var iframes = 0f
        private set
    var boostT = 0f
        private set
    var boostMeter = 1f
        private set
    var charge = 0f
        private set
    var specialT = 0f
        private set
    var holeT = 0f
        private set
    var hx = 0f; var hy = 0f
    /** Seconds of mission time (or hub time). */
    var t = 0f
        private set
    var shake = 0f
        private set
    var flash = 0f
        private set
    var flashColor = 0
        private set

    // the floating stick, in screen pixels; the renderer sets [stickR] and draws the rest
    var stickR = 120f
    var stickOn = false
        private set
    var ox = 0f; var oy = 0f
    var sx = 0f; var sy = 0f
    private var stickId = -1

    var autopilot: Station? = null
        private set
    var near: Station? = null
        private set
    private var docked: Station? = null

    // the mission
    var objective = 0
        private set
    var goal = 0
        private set
    var difficulty = 1f
        private set
    var sector = 0
        private set
    /** How far from the arena's centre the pilot may stray. */
    var bound = 90f
        private set
    var cx = 0f; var cy = 0f
    var score = 0L
        private set
    var credits = 0
        private set
    var kills = 0
        private set
    private var rocks = 0
    private var elites = 0
    private var spawned = 0
    private var pickupsN = 0
    private var specials = 0
    private var hits = 0
    private var bestChain = 0
    private var revived = false
    private var downT = 0f
    private var askedRevive = false
    private var endT = -1f
    private var cleared = false

    private var acc = 0f
    private var hudT = 0f
    private var rnd = Random(1)
    private var seedN = 1
    private var gunT = 0f
    private var supT = 0f
    var supAng = 0f
        private set
    private var spawnT = 0f
    private var waveT = 0f
    private var aceT = 0f
    private var turretT = 0f
    private var trailT = 0f
    private var hullRegenT = 0f

    val hull get() = if (::ship.isInitialized && ship.maxHp > 0) hp / ship.maxHp else 0f
    private val overdrive get() = specialT > 0f && loadout.special.base == Special.OVERDRIVE
    private val phase get() = specialT > 0f && loadout.special.base == Special.PHASE
    private val enemyTime get() = if (phase) 0.4f else 1f
    val convoy: Obj? get() = (0 until no).map { objs[it] }.firstOrNull { it.alive && (it.kind == OKind.CONVOY || it.kind == OKind.OUTPOST) }

    // ---------------------------------------------------------------- starting

    /** The hub. [from] is the station the pilot just left (null: fresh from the menu). */
    fun hub(l: Loadout, stats: ShipStats, from: Station?, quality: Float = 1f, theme: HubTheme = Hubs.all[0]) {
        reset(l, stats, quality, null)
        hub = true
        this.theme = theme
        bound = 96f
        if (from != null) {
            val a = from.angle
            val r = from.radius - from.dock - 2.5f
            px = cos(a) * r; py = sin(a) * r
            ang = a + PI.toFloat()
            pvx = cos(ang) * 6f; pvy = sin(ang) * 6f
        } else {
            px = 0f; py = 12f; ang = (PI / 2).toFloat()
        }
        docked = from
        for (k in 0 until theme.traffic) traffic(rnd.nextFloat() * 80f - 40f, rnd.nextFloat() * 80f - 40f)
        // The hub's rocks: a slow ring outside the stations or, in a field, scattered between them (clear of the docks).
        var k = 0
        var tries = 0
        while (k < theme.rocks && tries++ < theme.rocks * 8) {
            val a = rnd.nextFloat() * 6.283f
            val r = if (theme.field) 14f + rnd.nextFloat() * 96f else 82f + rnd.nextFloat() * 20f
            val x = cos(a) * r; val y = sin(a) * r
            if (theme.field && Station.entries.any { hypot(it.x - x, it.y - y) < it.dock + 4f }) continue
            val e = newEnemy(if (k % 3 == 0) Foe.ROCK_L else if (theme.field && k % 3 == 1) Foe.ROCK_S else Foe.ROCK_M, x, y) ?: break
            k++
            e.ally = true; e.mode = 1
            val drift = if (theme.field) 0.25f else 0.6f
            e.vx = -sin(a) * drift; e.vy = cos(a) * drift
            e.spin = (rnd.nextFloat() - 0.5f) * 0.8f; e.rollV = (rnd.nextFloat() - 0.5f) * 0.8f
            e.size = 1.2f + rnd.nextFloat()
        }
    }

    /** One of the open-space missions. */
    fun start(l: Loadout, stats: ShipStats, s: MissionSpec, quality: Float = 1f) {
        reset(l, stats, quality, s)
        hub = false
        difficulty = s.difficulty
        sector = s.sector
        goal = s.goal
        rnd = Random(s.seed)
        charge = 0.35f
        px = 0f; py = -6f; ang = (PI / 2).toFloat()
        when (s.mode) {
            Mode.EXPLORATION -> {
                bound = 150f
                place(OKind.BEACON, goal, 30f, 140f, 22f, 2f)
                field(30, 150f)
            }
            Mode.SALVAGE -> {
                bound = 130f
                place(OKind.POD, goal + 4, 20f, 120f, 14f, 1f)
                for (i in 0 until no) {
                    val o = objs[i]
                    for (k in 0 until 2) {
                        val a = rnd.nextFloat() * 6.283f
                        newEnemy(Foe.MINE, o.x + cos(a) * 5f, o.y + sin(a) * 5f)
                    }
                }
                field(16, 130f)
            }
            Mode.MINING -> { bound = 115f; field(50, 110f) }
            Mode.BOUNTY -> bound = 140f
            Mode.ESCORT -> {
                bound = 60f
                val c = obj(OKind.CONVOY, 0f, 0f, 2.6f)
                c.hp = 600f + 60f * difficulty; c.maxHp = c.hp
                val a = rnd.nextFloat() * 6.283f
                val dist = 140f + 8f * difficulty
                obj(OKind.GATE, cos(a) * dist, sin(a) * dist, 4f)
                c.rot = a
                px = -cos(a) * 6f; py = -sin(a) * 6f; ang = a
                goal = 100
            }
            Mode.DEFENSE -> {
                bound = 70f
                val o = obj(OKind.OUTPOST, 0f, 0f, 3.4f)
                o.hp = 1500f + 100f * difficulty; o.maxHp = o.hp
                px = 0f; py = -9f
                waveT = 4f
            }
            Mode.ACE -> { bound = 90f; aceT = 3f }
            else -> bound = 100f
        }
        spawnT = 4f
        listener?.event(WorldEvent.Callout(s.goalText, big = true))
    }

    private fun reset(l: Loadout, stats: ShipStats, quality: Float, s: MissionSpec?) {
        loadout = l; ship = stats; spec = s
        parts.limit = (parts.cap * quality).toInt().coerceAtLeast(400)
        glow = swatch(l.paint.glow).argb.toInt()
        trim = swatch(l.paint.secondary).argb.toInt()
        flair.set(l.paint, glow)
        bullets.n = 0; shots.n = 0; parts.n = 0; pickups.n = 0
        for (i in 0 until ne) enemies[i].alive = false
        ne = 0
        for (o in objs) o.alive = false
        no = 0
        for (a in arcs) a.life = 0f
        rnd = Random(System.nanoTime())
        px = 0f; py = 0f; pvx = 0f; pvy = 0f; ang = 0f; bank = 0f
        hp = ship.maxHp.toFloat(); alive = true; iframes = 1.5f
        boostT = 0f; boostMeter = 1f; charge = 0f; specialT = 0f; holeT = 0f
        t = 0f; acc = 0f; hudT = 0f; shake = 0f; flash = 0f
        stickOn = false; stickId = -1; trigger = false; gunShots = 0; autopilot = null; near = null; docked = null
        objective = 0; goal = 0; difficulty = 1f; sector = 0; cx = 0f; cy = 0f
        score = 0; credits = 0; kills = 0; rocks = 0; elites = 0; spawned = 0; pickupsN = 0; specials = 0; hits = 0
        bestChain = 0; revived = false; downT = 0f; askedRevive = false; endT = -1f; cleared = false
        gunT = 0f; supT = 0f; spawnT = 0f; waveT = 0f; aceT = 0f; turretT = 0f; hullRegenT = 0f
        state = State.PLAYING
    }

    fun stop() {
        state = State.IDLE
        stickOn = false; stickId = -1
    }

    private fun obj(kind: Int, x: Float, y: Float, r: Float): Obj {
        val o = objs[no++]
        o.alive = true; o.kind = kind; o.x = x; o.y = y; o.vx = 0f; o.vy = 0f; o.r = r
        o.hp = 0f; o.maxHp = 0f; o.done = false; o.seen = false; o.t = 0f; o.rot = rnd.nextFloat() * 6.283f
        return o
    }

    /** [n] objects of [kind] scattered between [minR] and [maxR] from the centre, at least [gap] apart. */
    private fun place(kind: Int, n: Int, minR: Float, maxR: Float, gap: Float, r: Float) {
        var tries = 0
        while (no < objs.size && (0 until no).count { objs[it].kind == kind } < n && tries++ < 600) {
            val a = rnd.nextFloat() * 6.283f
            val d = minR + rnd.nextFloat() * (maxR - minR)
            val x = cos(a) * d; val y = sin(a) * d
            val g = if (tries > 300) gap * 0.5f else gap
            if ((0 until no).any { hypot(objs[it].x - x, objs[it].y - y) < g }) continue
            obj(kind, x, y, r)
        }
    }

    /** A field of [n] drifting rocks within [r] of the centre (clear of the start). */
    private fun field(n: Int, r: Float) {
        for (k in 0 until n) {
            val a = rnd.nextFloat() * 6.283f
            val d = 14f + rnd.nextFloat() * (r - 14f)
            rock(cos(a) * d, sin(a) * d)
        }
    }

    private fun rock(x: Float, y: Float): Enemy? {
        val roll = rnd.nextFloat()
        val foe = when { roll < 0.3f -> Foe.ROCK_L; roll < 0.75f -> Foe.ROCK_M; else -> Foe.ROCK_S }
        val e = newEnemy(foe, x, y) ?: return null
        e.vx = (rnd.nextFloat() - 0.5f) * 1.6f; e.vy = (rnd.nextFloat() - 0.5f) * 1.6f
        e.spin = (rnd.nextFloat() - 0.5f) * 1.5f; e.rollV = (rnd.nextFloat() - 0.5f) * 2f
        e.size = 0.9f + rnd.nextFloat() * 0.5f
        e.mode = 1
        return e
    }

    private fun traffic(x: Float, y: Float) {
        val e = newEnemy(Foe.DART, x, y) ?: return
        e.ally = true
        e.a = Station.entries.random(rnd).ordinal.toFloat()
    }

    // ---------------------------------------------------------------- input

    fun touchDown(id: Int, x: Float, y: Float) {
        if (state != State.PLAYING) return
        if (stickId == -1) {
            stickId = id; stickOn = true
            ox = x; oy = y; sx = x; sy = y
        }
    }

    fun touchMove(id: Int, x: Float, y: Float) {
        if (id != stickId) return
        val dx = x - ox; val dy = y - oy
        val d = hypot(dx, dy)
        // the stick's base follows a finger that runs past the rim
        if (d > stickR) { ox = x - dx / d * stickR; oy = y - dy / d * stickR }
        sx = x; sy = y
    }

    fun touchUp(id: Int) {
        if (id != stickId) return
        stickId = -1; stickOn = false
    }

    fun touchCancel() { stickId = -1; stickOn = false }

    fun boost() {
        if (state != State.PLAYING || boostT > 0f) return
        if (!hub) {
            if (boostMeter < 0.5f) { sfx.play(Snd.DENY, 0.5f); return }
            boostMeter -= 0.5f
        }
        boostT = 1.2f
        sfx.play(Snd.WHOOSH, 0.8f)
        ring(px, py, glow, 0.3f, 10f)
    }

    /** Fly to [s] on autopilot; any touch of the stick takes back control. */
    fun autopilot(s: Station?) {
        autopilot = s
        docked = null
        if (s != null) sfx.play(Snd.TAP, 0.6f)
    }

    /** Dock at the nearest station, if one is close enough. */
    fun dock() {
        val s = near ?: return
        dockAt(s)
    }

    private fun dockAt(s: Station) {
        autopilot = null
        docked = s
        pvx *= 0.2f; pvy *= 0.2f
        sfx.play(Snd.BELL, 0.7f)
        listener?.event(WorldEvent.Docked(s))
    }

    fun special() {
        if (state != State.PLAYING || hub || !alive || charge < 1f || specialT > 0f) return
        charge = 0f
        specials++
        val kind = loadout.special
        val lvl = ship.specialLevel
        val power = (1f + 0.25f * (lvl - 1)) * kind.power
        val secs = kind.seconds * (1f + 0.1f * (lvl - 1))
        sfx.play(Snd.SPECIAL)
        sfx.buzz(Haptics.Kind.BOOM)
        when (kind.base) {
            Special.NOVA -> {
                val r = 16f * if (power > 1.6f) 1.25f else 1f
                ring(px, py, glow, 0.6f, r * 2.2f)
                ring(px, py, 0xFFFFFFFF.toInt(), 0.45f, r * 1.6f)
                flash(glow, 0.5f)
                shake = max(shake, 1.2f)
                clearNear(px, py, r)
                for (i in 0 until ne) {
                    val e = enemies[i]
                    if (e.alive && !e.ally && hypot(e.x - px, e.y - py) < r + e.r) damage(e, 140f * power * ship.damage)
                }
            }
            Special.BARRAGE -> {
                val n = (16 * power).toInt()
                for (k in 0 until n) {
                    val a = ang + k * 6.283f / n
                    val i = shots.add(px, py, cos(a) * 18f, sin(a) * 18f, 0.3f, 20f * power * ship.damage, 3f, SKind.MISSILE)
                    if (i >= 0) shots.last[i] = -1
                }
            }
            Special.SINGULARITY -> {
                specialT = secs; holeT = secs
                hx = px + cos(ang) * 8f; hy = py + sin(ang) * 8f
            }
            else -> specialT = secs
        }
        if (kind.base == Special.OVERDRIVE) flash(0xFFFFC040.toInt(), 0.3f)
        if (kind.base == Special.PHASE) flash(0xFF8080FF.toInt(), 0.3f)
    }

    fun revive() {
        if (state != State.DOWN || revived) return
        revived = true
        state = State.PLAYING
        alive = true
        hp = ship.maxHp.toFloat()
        iframes = 3f
        clearNear(px, py, 40f)
        ring(px, py, glow, 0.6f, 20f)
        sfx.play(Snd.SHIELD)
    }

    fun giveUp() {
        if (state == State.DOWN || state == State.PLAYING) finish(false)
    }

    // ---------------------------------------------------------------- the loop

    fun frame(dtRaw: Float) {
        if (state == State.IDLE) return
        val dt = min(dtRaw, 0.05f)
        acc += dt
        while (acc >= STEP) {
            acc -= STEP
            step(STEP)
        }
        hudT -= dt
        if (hudT <= 0f) { hudT = 0.05f; sendHud() }
    }

    private fun step(h: Float) {
        shake = max(0f, shake - h * 2.5f)
        flash = max(0f, flash - h * 2f)
        if (state == State.OVER) { updateParticles(h); return }
        t += h
        if (state == State.PLAYING && alive) fly(h)
        else { pvx *= 1f - h; pvy *= 1f - h; px += pvx * h; py += pvy * h }
        if (state == State.DOWN) {
            downT += h
            if (downT > 1.4f && !askedRevive) {
                askedRevive = true
                listener?.event(WorldEvent.Died(!revived))
            }
        }
        if (hub) hubStep(h) else {
            mission(h)
            if (alive) { guns(h); support(h) }
            specials(h)
            updateBullets(h)
        }
        updateEnemies(h)
        updateShots(h)
        updatePickups(h)
        updateParticles(h)
        for (a in arcs) if (a.life > 0f) a.life -= h
        if (endT >= 0f) {
            endT -= h
            if (endT < 0f) finish(cleared)
        }
    }

    private fun fly(h: Float) {
        iframes = max(0f, iframes - h)
        boostT = max(0f, boostT - h)
        boostMeter = if (hub) 1f else min(1f, boostMeter + h * 0.25f)
        if (!hub && ship.regen > 0f) {
            hullRegenT += h
            if (hullRegenT > 3f) hp = min(ship.maxHp.toFloat(), hp + ship.regen * h * 4f)
        }
        // where the pilot wants to go: a direction (wx, wy) and how hard (m)
        var wx = 0f; var wy = 0f
        var m = 0f
        if (stickOn) {
            val dx = sx - ox; val dy = -(sy - oy)
            val len = hypot(dx, dy)
            val d = len / stickR
            if (d > DEAD) {
                wx = dx / len; wy = dy / len
                // a gentle curve: fine control near the centre, full thrust well short of the rim
                val r = min(1f, (d - DEAD) / 0.7f)
                m = r * (0.35f + 0.65f * r)
                autopilot = null
            }
        } else autopilot?.let { s ->
            val dx = s.x - px; val dy = s.y - py
            val d = hypot(dx, dy)
            if (d > 0.01f) { wx = dx / d; wy = dy / d }
            m = if (d < 8f + s.dock) max(0.25f, (d - s.dock * 0.5f) / (8f + s.dock)) else 1f
            if (d < s.dock - 0.5f) dockAt(s)
        }
        var maxV = ship.speed * 0.8f * if (hub) 1.4f else 1f
        if (boostT > 0f) {
            maxV *= 2f
            if (m == 0f) { wx = cos(ang); wy = sin(ang) }
            m = 1f
        }
        // the nose: onto a target while the trigger's held, so you can strafe it; otherwise where the stick points
        val before = ang
        val lock = if (trigger && !hub) lockOn(if (m > 0f) atan2(wy, wx) else ang) else null
        if (lock != null) ang = turnTo(ang, atan2(lock.y - py, lock.x - px), 6f * h)
        else if (m > 0f) ang = turnTo(ang, atan2(wy, wx), (4f + 6f * m) * h)
        val turn = wrap(ang - before) / h
        bank += ((turn * 0.12f).coerceIn(-0.85f, 0.85f) - bank) * min(1f, h * 7f)
        // thrust: the velocity eases toward the stick's, braking harder than it speeds up, and drifts when let go
        val dvx = wx * maxV * m - pvx; val dvy = wy * maxV * m - pvy
        val dv = hypot(dvx, dvy)
        if (dv > 1e-4f) {
            val braking = pvx * dvx + pvy * dvy < 0f
            val accel = maxV * when { m == 0f -> 0.7f; braking -> 3.4f; else -> 2.4f }
            val step = min(dv, accel * h)
            pvx += dvx / dv * step; pvy += dvy / dv * step
        }
        // a soft wall at the edge of the arena
        val ex = px - cx; val ey = py - cy
        val ed = hypot(ex, ey)
        if (ed > bound) {
            val push = (ed - bound) * 6f * h
            pvx -= ex / ed * push; pvy -= ey / ed * push
            if (ed > bound + 4f) { px = cx + ex / ed * (bound + 4f); py = cy + ey / ed * (bound + 4f) }
        }
        px += pvx * h; py += pvy * h
        exhaust(h)
        // the nearest station in reach
        if (hub) {
            var best: Station? = null
            var bd = Float.MAX_VALUE
            for (s in Station.entries) {
                val d = hypot(s.x - px, s.y - py)
                if (d < s.dock && d < bd) { bd = d; best = s }
            }
            if (best != near && best != null) sfx.play(Snd.TAP, 0.4f)
            near = best
            if (best == null) docked = null
        }
    }

    private fun exhaust(h: Float) {
        trailT -= h
        if (trailT > 0f) return
        trailT = 1f / 60f
        val e = loadout.engine
        val len = e.trail * if (boostT > 0f) 1.8f else 1f
        val sp = hypot(pvx, pvy)
        if (sp < 1.5f && boostT <= 0f) return
        val c = cos(ang); val s = sin(ang)
        val bx = px - c * 0.95f; val by = py - s * 0.95f
        flair.puff(parts, rnd, bx, by, -c * 9f * len + pvx * 0.4f, -s * 9f * len + pvy * 0.4f, -s, c, len)
        if (rnd.nextFloat() < 0.5f) parts.add(bx, by, -c * 12f * len + pvx * 0.4f, -s * 12f * len + pvy * 0.4f, 0.12f * len, 0.2f, 0xFFFFFFFF.toInt(), Shape.SPARK, 2f)
    }

    private fun hubStep(h: Float) {
        // the traffic: darts plying between stations
        for (i in 0 until ne) {
            val e = enemies[i]
            if (!e.alive || !e.ally || e.foe != Foe.DART) continue
            val s = Station.entries[e.a.toInt().coerceIn(0, Station.entries.size - 1)]
            val dx = s.x - e.x; val dy = s.y - e.y
            val d = hypot(dx, dy)
            if (d < s.dock) {
                e.a = Station.entries.random(rnd).ordinal.toFloat()
                continue
            }
            val sp = 10f
            e.vx += (dx / d * sp - e.vx) * min(1f, h * 1.5f)
            e.vy += (dy / d * sp - e.vy) * min(1f, h * 1.5f)
        }
        // a gentle drift of sparkle around the centrepiece
        if (rnd.nextFloat() < h * 12f) {
            val a = rnd.nextFloat() * 6.283f
            val r = 4f + rnd.nextFloat() * 3f
            parts.add(cos(a) * r, sin(a) * r, -sin(a) * 2f, cos(a) * 2f, 1.4f, 0.25f, theme.dust.toInt(), Shape.SPARK, 0.3f)
        }
    }

    private fun sendHud() {
        val l = listener ?: return
        val s = spec
        val c = convoy
        l.space(
            SpaceHud(
                hull = hull,
                charge = charge,
                boost = boostMeter,
                objective = objective,
                goal = goal,
                timeLeft = if (s != null && s.seconds > 0f) max(0f, s.seconds - t) else -1f,
                credits = credits,
                score = score,
                seconds = t,
                ally = c?.frac ?: -1f,
                near = near,
                gun = loadout.primary,
                specialOn = if (specialT > 0f && loadout.special.seconds > 0f) specialT / (loadout.special.seconds * (1f + 0.1f * (ship.specialLevel - 1))) else 0f,
                kills = kills,
                hub = hub,
                autopilot = autopilot,
            ),
        )
    }

    // ---------------------------------------------------------------- missions

    private fun mission(h: Float) {
        val s = spec ?: return
        if (state != State.PLAYING || endT >= 0f) return
        if (s.seconds > 0f && t >= s.seconds) { win(objective >= goal); return }
        when (s.mode) {
            Mode.EXPLORATION -> for (i in 0 until no) {
                val o = objs[i]
                if (!o.alive || o.kind != OKind.BEACON || o.done) continue
                o.rot += h
                val d = hypot(o.x - px, o.y - py)
                if (!o.seen && d < 14f + difficulty) {
                    o.seen = true
                    group(o.x, o.y, 3 + (difficulty / 3f).toInt(), 6f)
                    listener?.event(WorldEvent.Callout("Beacon guarded"))
                }
                if (d < 4f) {
                    o.t += h
                    if (rnd.nextFloat() < h * 30f) parts.add(o.x, o.y, (rnd.nextFloat() - 0.5f) * 4f, (rnd.nextFloat() - 0.5f) * 4f, 0.5f, 0.3f, 0xFF6FB6FF.toInt(), Shape.SPARK, 1f)
                    if (o.t >= 1.2f) {
                        o.done = true
                        objective++
                        score += 500
                        ring(o.x, o.y, 0xFF6FB6FF.toInt(), 0.8f, 18f)
                        sfx.play(Snd.LEVEL)
                        listener?.event(WorldEvent.Callout("Beacon charted  $objective/$goal"))
                    }
                } else o.t = max(0f, o.t - h)
            }
            Mode.SALVAGE -> for (i in 0 until no) {
                val o = objs[i]
                if (!o.alive || o.kind != OKind.POD) continue
                o.rot += h * 0.7f
                if (hypot(o.x - px, o.y - py) < 2.2f + o.r) {
                    o.alive = false
                    objective++
                    score += 300
                    credits += 10 + difficulty.toInt() * 2
                    pickupsN++
                    burst(o.x, o.y, 0xFFFFE0A0.toInt(), 18, 8f, 0.5f)
                    sfx.play(Snd.PICKUP)
                    listener?.event(WorldEvent.Callout("Pod recovered  $objective/$goal"))
                    if (rnd.nextFloat() < 0.5f) group(o.x + (rnd.nextFloat() - 0.5f) * 30f, o.y + (rnd.nextFloat() - 0.5f) * 30f, 2 + (difficulty / 4f).toInt(), 5f)
                }
            }
            Mode.MINING -> {
                var n = 0
                for (i in 0 until ne) if (enemies[i].alive && enemies[i].foe.isRock()) n++
                if (n < 40) {
                    val a = rnd.nextFloat() * 6.283f
                    val r = 45f + rnd.nextFloat() * 60f
                    val x = px + cos(a) * r; val y = py + sin(a) * r
                    if (hypot(x, y) < bound) rock(x, y)
                }
            }
            Mode.BOUNTY -> {
                var live = 0
                for (i in 0 until ne) if (enemies[i].alive && enemies[i].mode == 3) live++
                if (live < min(3, goal - objective) && rnd.nextFloat() < h * 0.5f) bountyTarget()
            }
            Mode.ESCORT -> {
                val c = objs[0]; val g = objs[1]
                val dx = g.x - c.x; val dy = g.y - c.y
                val d = hypot(dx, dy)
                val total = 140f + 8f * difficulty
                // the convoy waits for its escort
                val sp = if (hypot(px - c.x, py - c.y) < 30f) 3f else 1.2f
                c.vx = dx / d * sp; c.vy = dy / d * sp
                c.x += c.vx * h; c.y += c.vy * h
                c.rot = atan2(dy, dx)
                cx = c.x; cy = c.y
                objective = ((1f - d / total) * 100f).toInt().coerceIn(0, 100)
                if (rnd.nextFloat() < h * 20f) parts.add(c.x - cos(c.rot) * 2.8f, c.y - sin(c.rot) * 2.8f, -cos(c.rot) * 5f, -sin(c.rot) * 5f, 0.4f, 0.5f, 0xFF6BFFC8.toInt(), Shape.SOFT, 2f, -0.8f)
                if (d < g.r) {
                    objective = 100
                    ring(g.x, g.y, 0xFF6BFFC8.toInt(), 1f, 30f)
                    listener?.event(WorldEvent.Callout("Convoy jumped out!", big = true))
                    win(true)
                } else if (c.hp <= 0f) lose("The convoy is lost")
            }
            Mode.DEFENSE -> {
                val o = objs[0]
                objective = t.toInt()
                waveT -= h
                if (waveT <= 0f) {
                    waveT = max(5f, 9f - difficulty * 0.2f)
                    val a = rnd.nextFloat() * 6.283f
                    val n = 4 + (difficulty * 0.6f).toInt() + (t / 30f).toInt()
                    group(cos(a) * 46f, sin(a) * 46f, min(n, 14), 6f, target = 1)
                    listener?.event(WorldEvent.Callout("Wave incoming"))
                    sfx.play(Snd.WARNING, 0.6f)
                }
                // the outpost's own turret
                turretT -= h
                if (turretT <= 0f) {
                    val e = nearestEnemy(o.x, o.y, 16f)
                    if (e != null) {
                        turretT = 0.5f
                        val a = atan2(e.y - o.y, e.x - o.x)
                        val i = shots.add(o.x + cos(a) * 3f, o.y + sin(a) * 3f, cos(a) * 30f, sin(a) * 30f, 0.25f, 8f + difficulty, 1f, SKind.BOLT)
                        if (i >= 0) shots.last[i] = -1
                    }
                }
                if (o.hp <= 0f) lose("The outpost has fallen")
                else if (t >= goal) win(true)
            }
            Mode.ACE -> {
                var live = false
                for (i in 0 until ne) if (enemies[i].alive && enemies[i].mode == 2) live = true
                if (!live && objective < goal) {
                    aceT -= h
                    if (aceT <= 0f) { aceT = 4f; ace() }
                }
            }
            else -> {}
        }
        if (s.mode != Mode.ESCORT && s.mode != Mode.DEFENSE && objective >= goal) win(true)
        director(h)
    }

    private fun Foe.isRock() = this == Foe.ROCK_S || this == Foe.ROCK_M || this == Foe.ROCK_L

    private fun win(ok: Boolean) {
        if (endT >= 0f) return
        cleared = ok
        endT = 1.6f
        if (ok) {
            sfx.play(Snd.DONE)
            sfx.buzz(Haptics.Kind.SUCCESS)
            clearNear(px, py, 200f)
            listener?.event(WorldEvent.Callout("Mission complete", big = true))
        } else listener?.event(WorldEvent.Callout("Out of time", big = true))
    }

    private fun lose(why: String) {
        if (endT >= 0f) return
        cleared = false
        endT = 2f
        sfx.play(Snd.BIG_BOOM)
        listener?.event(WorldEvent.Callout(why, big = true))
        convoy?.let { explode(it.x, it.y, 3f, 0xFFFF8040.toInt()) }
    }

    private fun finish(ok: Boolean) {
        if (state == State.OVER) return
        state = State.OVER
        stickOn = false; stickId = -1
        // whatever is still lying around is banked
        for (i in 0 until pickups.n) if (pickups.kind[i] == PKind.CREDIT || pickups.kind[i] == PKind.GEM) credits += pickups.value[i]
        pickups.n = 0
        listener?.event(
            WorldEvent.Finished(
                RunResult(null, ok, score, credits, kills, rocks, elites, 0, spawned, 0, pickupsN, specials, if (ok) hull else 0f, hits, t, bestChain, 0, spec, objective),
            ),
        )
    }

    /** Ambient hostiles: groups appear out of sight around the pilot, more of them deeper in. */
    private fun director(h: Float) {
        val s = spec ?: return
        spawnT -= h
        if (spawnT > 0f) return
        val calm = when (s.mode) {
            Mode.EXPLORATION, Mode.MINING, Mode.SALVAGE -> 1.6f
            Mode.ACE, Mode.DEFENSE -> 3f
            else -> 1f
        }
        spawnT = max(1.8f, 6f - 0.22f * difficulty) * calm
        var hostile = 0
        for (i in 0 until ne) { val e = enemies[i]; if (e.alive && !e.ally && !e.foe.isRock()) hostile++ }
        val cap = min(24, (6 + 1.2f * difficulty).toInt())
        if (hostile >= cap) return
        val a = rnd.nextFloat() * 6.283f
        val r = 34f + rnd.nextFloat() * 8f
        val n = (2 + rnd.nextInt(2 + (difficulty / 3f).toInt())).coerceAtMost(cap - hostile)
        val target = if (s.mode == Mode.ESCORT && rnd.nextBoolean()) 1 else 0
        group(px + cos(a) * r, py + sin(a) * r, n, 4f, target)
    }

    private fun roster(): Foe {
        val pool = ArrayList<Foe>(8)
        pool += Foe.DRONE; pool += Foe.DRONE; pool += Foe.DART
        if (sector >= 1 || difficulty > 2f) { pool += Foe.GUNSHIP; pool += Foe.DART }
        if (sector >= 2 || difficulty > 3.5f) pool += Foe.MINE
        if (sector >= 3 || difficulty > 5f) pool += Foe.SPINNER
        if (sector >= 4 || difficulty > 6.5f) { pool += Foe.LANCER; pool += Foe.GUNSHIP }
        if ((sector >= 6 || difficulty > 9f) && rnd.nextFloat() < 0.15f) return Foe.CARRIER
        return pool[rnd.nextInt(pool.size)]
    }

    /** A group of [n] hostiles around ([x], [y]). [target] 1: they go for the convoy or outpost. */
    private fun group(x: Float, y: Float, n: Int, spread: Float, target: Int = 0) {
        val lead = roster()
        for (k in 0 until n) {
            val foe = if (k == 0 || rnd.nextFloat() < 0.6f) lead else roster()
            val a = rnd.nextFloat() * 6.283f
            val r = rnd.nextFloat() * spread
            val e = newEnemy(foe, x + cos(a) * r, y + sin(a) * r) ?: return
            e.d = target.toFloat()
            e.c = rnd.nextFloat() * 6.283f
        }
    }

    private fun bountyTarget() {
        val a = rnd.nextFloat() * 6.283f
        val r = 50f + rnd.nextFloat() * 40f
        var x = px + cos(a) * r; var y = py + sin(a) * r
        val d = hypot(x, y)
        if (d > bound - 10f) { x *= (bound - 10f) / d; y *= (bound - 10f) / d }
        val big = difficulty >= 6f && rnd.nextBoolean()
        val e = newEnemy(if (big) Foe.CARRIER else Foe.GUNSHIP, x, y) ?: return
        e.mode = 3
        if (!big) { e.hp *= 4f; e.maxHp = e.hp }
        e.size = 1.35f
        elitesSpawned++
        for (k in 0 until 3) {
            val b = rnd.nextFloat() * 6.283f
            newEnemy(if (k == 0) Foe.DART else Foe.DRONE, x + cos(b) * 4f, y + sin(b) * 4f)?.c = b
        }
        listener?.event(WorldEvent.Callout("Bounty marked"))
    }

    private fun ace() {
        val a = rnd.nextFloat() * 6.283f
        val e = newEnemy(Foe.LANCER, px + cos(a) * 36f, py + sin(a) * 36f) ?: return
        e.mode = 2
        e.hp = 300f + 60f * difficulty; e.maxHp = e.hp
        e.size = 1.3f
        elitesSpawned++
        for (k in 0 until 2) newEnemy(Foe.DART, e.x + cos(a + 1.6f * (k * 2 - 1)) * 3f, e.y + sin(a + 1.6f * (k * 2 - 1)) * 3f)
        sfx.play(Snd.WARNING, 0.8f)
        listener?.event(WorldEvent.Callout("Ace inbound: ${ACES[(objective) % ACES.size]}", big = true))
    }

    private var elitesSpawned = 0

    // ---------------------------------------------------------------- enemies

    private fun newEnemy(foe: Foe, x: Float, y: Float): Enemy? {
        if (ne >= enemies.size) return null
        val e = enemies[ne++]
        e.alive = true; e.ally = false; e.foe = foe; e.boss = null; e.mode = 0
        e.x = x; e.y = y; e.vx = 0f; e.vy = 0f
        e.hp = foe.hp * (1f + 0.25f * difficulty); e.maxHp = e.hp
        e.r = foe.radius; e.t = 0f
        e.a = 0f; e.b = 0f; e.c = 0f; e.d = 0f
        e.fireT = 1f + rnd.nextFloat() * 1.5f; e.fired = 0; e.phase = 0; e.phaseT = 0f
        e.rot = rnd.nextFloat() * 6.283f; e.spin = 0f; e.roll = 0f; e.rollV = 0f; e.flash = 0f
        e.seed = seedN++; e.counted = !foe.isRock(); e.entered = true; e.size = 1f
        if (!e.ally) spawned++
        return e
    }

    private fun removeEnemy(i: Int) {
        enemies[i].alive = false
        val l = --ne
        if (i != l) { val tmp = enemies[i]; enemies[i] = enemies[l]; enemies[l] = tmp }
    }

    private fun updateEnemies(hRaw: Float) {
        val h = if (hub) hRaw else hRaw * enemyTime
        val c = convoy
        var i = ne - 1
        while (i >= 0) {
            val e = enemies[i]
            if (!e.alive) { removeEnemy(i); i--; continue }
            e.t += h
            e.flash = max(0f, e.flash - hRaw * 6f)
            if (!e.ally && state == State.PLAYING && !hub) think(e, h, c)
            // keep the ships from stacking up
            if (!e.foe.isRock() && e.foe != Foe.MINE) for (j in 0 until ne) {
                val o = enemies[j]
                if (o === e || !o.alive || o.foe.isRock()) continue
                val dx = e.x - o.x; val dy = e.y - o.y
                val d2 = dx * dx + dy * dy
                val min = e.r + o.r + 0.6f
                if (d2 < min * min && d2 > 1e-4f) {
                    val d = sqrt(d2)
                    val push = (min - d) * 4f * h
                    e.vx += dx / d * push * 6f; e.vy += dy / d * push * 6f
                }
            }
            e.x += e.vx * h; e.y += e.vy * h
            if (e.foe.isRock()) {
                e.rot += e.spin * h
                e.roll += e.rollV * h
            } else if (e.foe != Foe.MINE) {
                val sp = e.vx * e.vx + e.vy * e.vy
                if (sp > 0.5f) {
                    val want = atan2(e.vy, e.vx)
                    val before = e.rot
                    e.rot = turnTo(e.rot, want, 5f * h)
                    e.roll += ((wrap(e.rot - before) / max(h, 1e-4f) * 0.15f).coerceIn(-0.8f, 0.8f) - e.roll) * min(1f, h * 5f)
                }
            } else e.rot += h * 1.5f
            // contact with the pilot
            if (!e.ally && alive && state == State.PLAYING) {
                val dx = e.x - px; val dy = e.y - py
                val rr = e.r * e.size + 0.6f
                if (dx * dx + dy * dy < rr * rr) {
                    if (e.foe == Foe.MINE) { mineBlast(e); i--; continue }
                    hurt(14f + 2f * difficulty)
                    damage(e, 40f)
                    val d = max(0.01f, sqrt(dx * dx + dy * dy))
                    pvx -= dx / d * 8f; pvy -= dy / d * 8f
                    e.vx += dx / d * 6f; e.vy += dy / d * 6f
                }
            }
            // the far ones fade out (marked targets, aces and the field never do)
            if (e.mode < 2 && !e.ally) {
                val far = if (e.foe.isRock() || e.foe == Foe.MINE) 170f else 95f
                if (abs(e.x - px) > far || abs(e.y - py) > far) { e.alive = false; removeEnemy(i); i--; continue }
            }
            i--
        }
    }

    /** One hostile's brain. [c] is the convoy or outpost, if there is one. */
    private fun think(e: Enemy, h: Float, c: Obj?) {
        val onAlly = e.d == 1f && c != null && c.alive
        val tx = if (onAlly) c!!.x else px
        val ty = if (onAlly) c!!.y else py
        val dx = tx - e.x; val dy = ty - e.y
        val d = max(0.01f, hypot(dx, dy))
        val ux = dx / d; val uy = dy / d
        val dm = 1f + difficulty * 0.06f
        e.fireT -= h
        fun steer(vx: Float, vy: Float, k: Float) {
            e.vx += (vx - e.vx) * min(1f, h * k); e.vy += (vy - e.vy) * min(1f, h * k)
        }
        fun orbit(r: Float, sp: Float) {
            // close to range r, then circle (direction set by e.c's sign)
            val dir = if (e.c > PI) 1f else -1f
            val radial = (d - r) * 0.8f
            steer(ux * radial - uy * sp * dir, uy * radial + ux * sp * dir, 2f)
        }
        val aim = atan2(dy, dx)
        when (e.foe) {
            Foe.DRONE -> {
                val w = sin(e.t * 2f + e.c) * 0.6f
                val sp = 9f * dm
                steer((ux * cos(w) - uy * sin(w)) * sp, (uy * cos(w) + ux * sin(w)) * sp, 1.5f)
                if (d < 22f && e.fireT <= 0f) { e.fireT = 2.4f + rnd.nextFloat(); fire(e.x, e.y, aim, 8f, BShape.ORB, World.PINK) }
            }
            Foe.DART -> {
                // strafing passes: rush in, overshoot, swing round
                if (e.phase == 0) {
                    steer(ux * 17f * dm, uy * 17f * dm, 2.5f)
                    if (d < 6f) { e.phase = 1; e.phaseT = 1.4f }
                    if (d < 18f && e.fireT <= 0f) {
                        e.fireT = 1.6f
                        for (k in -1..1) fire(e.x, e.y, aim + k * 0.18f, 11f, BShape.RICE, World.ORANGE)
                    }
                } else {
                    e.phaseT -= h
                    if (e.phaseT <= 0f) e.phase = 0
                }
            }
            Foe.GUNSHIP -> {
                orbit(14f, 5f * dm)
                if (d < 26f && e.fireT <= 0f) {
                    e.fireT = 2.2f
                    for (k in -2..2) fire(e.x, e.y, aim + k * 0.16f, 9f, BShape.ORB, World.PINK)
                }
            }
            Foe.SPINNER -> {
                orbit(12f, 2.5f)
                e.b += h * 1.6f
                if (d < 24f && e.fireT <= 0f) {
                    e.fireT = 0.28f
                    for (k in 0 until 3) fire(e.x, e.y, e.b + k * 2.094f, 7f, BShape.RICE, World.VIOLET)
                }
            }
            Foe.LANCER -> {
                val elite = e.mode == 2
                orbit(if (elite) 11f else 12f, (if (elite) 9f else 6f) * dm)
                // a sidestep now and then
                e.phaseT -= h
                if (e.phaseT <= 0f) {
                    e.phaseT = 1.5f + rnd.nextFloat() * 1.5f
                    val s = if (rnd.nextBoolean()) 1f else -1f
                    e.vx += -uy * 14f * s; e.vy += ux * 14f * s
                }
                if (d < 24f && e.fireT <= 0f) {
                    e.fireT = if (elite) 1.1f else 1.8f
                    val n = if (elite) 3 + (difficulty / 4f).toInt().coerceAtMost(3) else 2
                    for (k in 0 until n) fire(e.x, e.y, aim + (k - (n - 1) / 2f) * 0.1f, 12f + k, BShape.DIAMOND, World.ICE)
                    if (elite && e.fired++ % 4 == 3) for (k in 0 until 12) fire(e.x, e.y, k * 0.5236f, 7f, BShape.RICE, World.RED)
                }
            }
            Foe.MINE -> {
                e.vx *= 1f - h; e.vy *= 1f - h
                val pd = hypot(px - e.x, py - e.y)
                if (pd < 3.5f) {
                    e.phaseT += h
                    if (e.phaseT > 0.5f) mineBlast(e)
                } else if (pd < 9f) {
                    // creep toward the pilot
                    e.vx += (px - e.x) / pd * 2f * h; e.vy += (py - e.y) / pd * 2f * h
                }
            }
            Foe.CARRIER -> {
                orbit(18f, 2f)
                if (e.fireT <= 0f) {
                    e.fireT = 3.4f
                    e.fired++
                    if (e.fired % 2 == 0) {
                        for (k in 0 until 2) newEnemy(Foe.DRONE, e.x + cos(e.rot + 1.6f * (k * 2 - 1)) * 2.5f, e.y + sin(e.rot + 1.6f * (k * 2 - 1)) * 2.5f)?.let { it.counted = false; it.d = e.d }
                    } else for (k in 0 until 16) fire(e.x, e.y, k * 0.3927f + e.t, 7f, BShape.ORB, World.PINK)
                }
            }
            else -> {
                // rocks drift and spin
                e.vx *= 1f - h * 0.02f; e.vy *= 1f - h * 0.02f
            }
        }
        // hard limit on speed
        val sp = hypot(e.vx, e.vy)
        val lim = if (e.foe.isRock()) 6f else 24f
        if (sp > lim) { e.vx *= lim / sp; e.vy *= lim / sp }
    }

    private fun mineBlast(e: Enemy) {
        if (!e.alive) return
        e.alive = false
        explode(e.x, e.y, 1.4f, 0xFFFF6040.toInt())
        ring(e.x, e.y, 0xFFFF8040.toInt(), 0.4f, 12f)
        sfx.play(Snd.BOOM, 0.8f)
        if (hypot(px - e.x, py - e.y) < 4f) hurt(22f + 3f * difficulty)
        for (k in 0 until 8) fire(e.x, e.y, k * 0.785f, 6f, BShape.RICE, World.RED)
    }

    private fun fire(x: Float, y: Float, a: Float, speed: Float, shape: Int, color: Int) {
        if (state != State.PLAYING) return
        val s = speed * (1f + difficulty * 0.03f)
        val r = when (shape) { BShape.BIG -> 0.42f; BShape.RICE -> 0.16f; BShape.DIAMOND -> 0.2f; BShape.RING -> 0.26f; else -> 0.2f }
        bullets.add(x, y, cos(a) * s, sin(a) * s, r, shape, color)
        sfx.play(Snd.ENEMY_SHOT, 0.25f, pan = ((x - px) / 20f).coerceIn(-1f, 1f), minGapMs = 60)
    }

    private fun updateBullets(hRaw: Float) {
        val h = hRaw * enemyTime
        val b = bullets
        val c = convoy
        val dmg = 9f + 2.2f * difficulty
        val hit = if (::ship.isInitialized) ship.hitbox + 0.25f else 0.45f
        var i = b.n - 1
        while (i >= 0) {
            b.x[i] += b.vx[i] * h; b.y[i] += b.vy[i] * h
            b.age[i] += h
            val dx = b.x[i] - px; val dy = b.y[i] - py
            if (b.age[i] > 6f || abs(dx) > 60f || abs(dy) > 60f) { b.remove(i); i--; continue }
            val rr = hit + b.r[i] * 0.6f
            if (alive && !phase && dx * dx + dy * dy < rr * rr) {
                hurt(dmg)
                if (i < b.n) b.remove(i)
                i--; continue
            }
            if (c != null && c.alive && hypot(b.x[i] - c.x, b.y[i] - c.y) < c.r) {
                c.hp -= dmg * 0.6f
                burst(b.x[i], b.y[i], 0xFFFFC080.toInt(), 3, 5f, 0.25f)
                b.remove(i); i--; continue
            }
            if (holeT > 0f && hypot(b.x[i] - hx, b.y[i] - hy) < 3f) { b.remove(i); i--; continue }
            i--
        }
    }

    // ---------------------------------------------------------------- the pilot's guns

    private fun guns(h: Float) {
        gunT -= h * if (overdrive) 2.2f else 1f
        if (gunT > 0f || state != State.PLAYING || !trigger) return
        gunShots++
        // aim assist: the nearest hostile in a cone ahead
        var aim = ang
        var best = 28f
        for (i in 0 until ne) {
            val e = enemies[i]
            if (!e.alive || e.ally) continue
            val dx = e.x - px; val dy = e.y - py
            val d = hypot(dx, dy)
            if (d >= best) continue
            val a = atan2(dy, dx)
            if (abs(wrap(a - ang)) < 0.44f) { best = d; aim = a }
        }
        val g = loadout.primary
        val lvl = ship.primaryLevel
        val dm = g.power * ship.damage * (1f + 0.12f * (lvl - 1))
        val rate = g.rate * ship.fireRate
        val c = cos(aim); val s = sin(aim)
        val nx = -s; val ny = c
        val mx = px + c * 0.9f; val my = py + s * 0.9f
        fun shot(ox: Float, a: Float, sp: Float, r: Float, dmg: Float, life: Float, kind: Int) {
            val i = shots.add(mx + nx * ox, my + ny * ox, cos(a) * sp + pvx * 0.5f, sin(a) * sp + pvy * 0.5f, r, dmg, life, kind)
            if (i >= 0) shots.last[i] = -1
        }
        when (g.base) {
            Primary.PULSE -> {
                val streams = 2 + (lvl - 1) / 2
                for (k in 0 until streams) shot((k - (streams - 1) / 2f) * 0.3f, aim + (k - (streams - 1) / 2f) * 0.03f, 46f, 0.22f, 6f * dm, 0.75f, SKind.BOLT)
                gunT = 1f / (9f * rate)
                sfx.play(Snd.SHOT, 0.35f, minGapMs = 70)
            }
            Primary.SPREAD -> {
                val n = 5 + (lvl - 1) / 2 * 2
                for (k in 0 until n) shot(0f, aim + (k - (n - 1) / 2f) * 0.13f, 36f, 0.2f, 4f * dm, 0.6f, SKind.PELLET)
                gunT = 1f / (4f * rate)
                sfx.play(Snd.SHOT_HEAVY, 0.4f)
            }
            Primary.LASER -> {
                shot(0f, aim, 60f, 0.2f, 2.5f * dm, 0.5f, SKind.RAIL)
                gunT = 1f / (16f * rate)
                sfx.play(Snd.LASER, 0.25f, minGapMs = 90)
            }
            Primary.HOMING -> {
                for (k in -1..1 step 2) shot(k * 0.5f, aim + k * 0.5f, 16f, 0.3f, 12f * dm, 2.5f, SKind.MISSILE)
                gunT = 1f / (3f * rate)
                sfx.play(Snd.MISSILE, 0.4f)
            }
            Primary.PLASMA -> {
                shot(0f, aim, 26f, 0.55f, 18f * dm, 1.4f, SKind.PLASMA)
                gunT = 1f / (2.2f * rate)
                sfx.play(Snd.SHOT_HEAVY, 0.5f, 0.7f)
            }
            Primary.RAIL -> {
                shot(0f, aim, 110f, 0.25f, 45f * dm, 0.4f, SKind.RAIL)
                gunT = 1f / (1.4f * rate)
                sfx.play(Snd.RAIL, 0.5f)
                parts.add(mx, my, 0f, 0f, 0.12f, 0.8f, glow, Shape.SOFT, 0f, 3f)
            }
            Primary.FLAK -> {
                shot(0f, aim, 30f, 0.3f, 10f * dm, 0.35f, SKind.FLAK)
                gunT = 1f / (2.5f * rate)
                sfx.play(Snd.FLAK, 0.4f)
            }
            else -> gunT = 0.2f
        }
    }

    /** The nearest hostile within reach and within a wide cone of [dir], for the nose to hold while firing. */
    private fun lockOn(dir: Float): Enemy? {
        var best: Enemy? = null
        var bd = 24f
        for (i in 0 until ne) {
            val e = enemies[i]
            if (!e.alive || e.ally) continue
            val dx = e.x - px; val dy = e.y - py
            val d = hypot(dx, dy)
            if (d < bd && abs(wrap(atan2(dy, dx) - dir)) < 1.1f) { bd = d; best = e }
        }
        return best
    }

    private fun nearestEnemy(x: Float, y: Float, r: Float): Enemy? {
        var best: Enemy? = null
        var bd = r * r
        for (i in 0 until ne) {
            val e = enemies[i]
            if (!e.alive || e.ally) continue
            val dx = e.x - x; val dy = e.y - y
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = e }
        }
        return best
    }

    /** How many support pieces fly with the pilot, and where (for the renderer). */
    val supCount: Int get() = when (loadout.support.base) {
        Support.DRONES -> 2
        Support.ORBITALS -> if (loadout.support == Support.BASTION) 4 else 3
        else -> 0
    }

    fun supX(i: Int): Float = when (loadout.support.base) {
        Support.ORBITALS -> px + cos(supAng + i * 6.283f / supCount) * orbR
        else -> px + cos(ang) * -1.1f + -sin(ang) * (if (i == 0) 1.5f else -1.5f)
    }

    fun supY(i: Int): Float = when (loadout.support.base) {
        Support.ORBITALS -> py + sin(supAng + i * 6.283f / supCount) * orbR
        else -> py + sin(ang) * -1.1f + cos(ang) * (if (i == 0) 1.5f else -1.5f)
    }

    private val orbR get() = if (loadout.support == Support.BASTION) 3f else 2.4f

    private fun support(h: Float) {
        val sup = loadout.support
        val power = sup.power * (1f + 0.15f * (ship.supportLevel - 1)) * ship.damage
        supAng += h * 3f
        supT -= h
        when (sup.base) {
            Support.DRONES -> if (supT <= 0f) {
                val e = nearestEnemy(px, py, 26f) ?: return
                supT = 0.45f
                for (k in 0 until 2) {
                    val x = supX(k); val y = supY(k)
                    val a = atan2(e.y - y, e.x - x)
                    val i = shots.add(x, y, cos(a) * 40f, sin(a) * 40f, 0.18f, 5f * power, 0.8f, SKind.DRONE)
                    if (i >= 0) shots.last[i] = -1
                }
            }
            Support.MISSILES -> if (supT <= 0f) {
                if (nearestEnemy(px, py, 30f) == null) return
                supT = 1.6f / sup.power
                for (k in -1..1 step 2) {
                    val a = ang + k * 1.4f
                    val i = shots.add(px, py, cos(a) * 10f, sin(a) * 10f, 0.3f, 14f * power, 3f, SKind.MISSILE)
                    if (i >= 0) shots.last[i] = -1
                }
                sfx.play(Snd.MISSILE, 0.3f)
            }
            Support.ORBITALS -> {
                val n = supCount
                for (k in 0 until n) {
                    val x = supX(k); val y = supY(k)
                    for (i in 0 until ne) {
                        val e = enemies[i]
                        if (e.alive && !e.ally && hypot(e.x - x, e.y - y) < e.r * e.size + 0.5f) damage(e, 40f * power * h)
                    }
                    var b = bullets.n - 1
                    while (b >= 0) {
                        val dx = bullets.x[b] - x; val dy = bullets.y[b] - y
                        if (dx * dx + dy * dy < 0.36f) { burst(bullets.x[b], bullets.y[b], glow, 3, 4f, 0.2f); bullets.remove(b) }
                        b--
                    }
                }
            }
            Support.TESLA -> if (supT <= 0f) {
                val reach = if (sup == Support.STORM_LATTICE) 12f else 9f
                var e = nearestEnemy(px, py, reach) ?: return
                supT = 0.7f
                var fx = px; var fy = py
                val hitSet = HashSet<Int>()
                for (k in 0 until 3) {
                    arc(fx, fy, e.x, e.y)
                    hitSet += e.seed
                    damage(e, 16f * power)
                    fx = e.x; fy = e.y
                    var next: Enemy? = null
                    var bd = 25f
                    for (i in 0 until ne) {
                        val o = enemies[i]
                        if (!o.alive || o.ally || o.seed in hitSet) continue
                        val d = (o.x - fx) * (o.x - fx) + (o.y - fy) * (o.y - fy)
                        if (d < bd) { bd = d; next = o }
                    }
                    e = next ?: break
                }
                sfx.play(Snd.ZAP, 0.4f)
            }
            else -> {}
        }
    }

    private fun specials(h: Float) {
        if (specialT > 0f) specialT = max(0f, specialT - h)
        if (!alive) return
        if (charge < 1f) {
            val before = charge
            charge = min(1f, charge + h * 0.012f * ship.charge)
            if (before < 1f && charge >= 1f) { sfx.play(Snd.CHARGED); listener?.event(WorldEvent.Callout("Special ready")) }
        }
        if (holeT > 0f) {
            holeT = max(0f, holeT - h)
            val power = loadout.special.power * (1f + 0.25f * (ship.specialLevel - 1))
            for (i in 0 until ne) {
                val e = enemies[i]
                if (!e.alive || e.ally) continue
                val dx = hx - e.x; val dy = hy - e.y
                val d = hypot(dx, dy)
                if (d < 10f * power.coerceAtMost(1.6f) && d > 0.1f) {
                    e.vx += dx / d * 30f * h; e.vy += dy / d * 30f * h
                    if (d < 2.5f) damage(e, 60f * power * ship.damage * h)
                }
            }
            if (rnd.nextFloat() < h * 40f) {
                val a = rnd.nextFloat() * 6.283f
                parts.add(hx + cos(a) * 6f, hy + sin(a) * 6f, -cos(a) * 12f, -sin(a) * 12f, 0.45f, 0.25f, glow, Shape.SPARK, 0f)
            }
        }
    }

    private fun updateShots(h: Float) {
        val s = shots
        var i = s.n - 1
        while (i >= 0) {
            s.age[i] += h
            val k = s.kind[i]
            if (s.age[i] >= s.life[i]) {
                if (k == SKind.FLAK) flakBurst(s.x[i], s.y[i], s.dmg[i] * 0.4f)
                s.remove(i); i--; continue
            }
            if (k == SKind.MISSILE) {
                val e = nearestEnemy(s.x[i], s.y[i], 30f)
                val sp = max(16f, hypot(s.vx[i], s.vy[i]) + h * 30f).coerceAtMost(32f)
                var a = atan2(s.vy[i], s.vx[i])
                if (e != null) a = turnTo(a, atan2(e.y - s.y[i], e.x - s.x[i]), 5f * h)
                s.vx[i] = cos(a) * sp; s.vy[i] = sin(a) * sp
                if (rnd.nextFloat() < 0.5f) parts.add(s.x[i], s.y[i], 0f, 0f, 0.25f, 0.22f, trim, Shape.SOFT, 1f, -0.6f)
            }
            s.x[i] += s.vx[i] * h; s.y[i] += s.vy[i] * h
            val pierce = k == SKind.RAIL || k == SKind.PLASMA
            var gone = false
            for (j in 0 until ne) {
                val e = enemies[j]
                if (!e.alive || e.ally) continue
                if (pierce && s.last[i] == e.seed) continue
                val dx = e.x - s.x[i]; val dy = e.y - s.y[i]
                val rr = e.r * e.size + s.r[i]
                if (dx * dx + dy * dy < rr * rr) {
                    damage(e, s.dmg[i])
                    burst(s.x[i], s.y[i], glow, 2, 5f, 0.18f)
                    if (k == SKind.FLAK) flakBurst(s.x[i], s.y[i], s.dmg[i] * 0.4f)
                    if (pierce) s.last[i] = e.seed else { gone = true; break }
                }
            }
            if (gone) { s.remove(i); i--; continue }
            i--
        }
    }

    private fun flakBurst(x: Float, y: Float, dmg: Float) {
        for (k in 0 until 8) {
            val a = k * 0.785f + rnd.nextFloat() * 0.3f
            val i = shots.add(x, y, cos(a) * 22f, sin(a) * 22f, 0.16f, dmg, 0.3f, SKind.SHRAPNEL)
            if (i >= 0) shots.last[i] = -1
        }
        burst(x, y, 0xFFFFC060.toInt(), 6, 8f, 0.25f)
    }

    private fun damage(e: Enemy, dmg: Float) {
        if (!e.alive || e.ally) return
        e.hp -= dmg
        e.flash = 1f
        if (e.hp <= 0f) kill(e)
        else sfx.play(Snd.HIT, 0.2f, minGapMs = 50)
    }

    private fun kill(e: Enemy) {
        e.alive = false
        val f = e.foe
        score += f.score.toLong()
        charge = min(1f, charge + f.score * 0.00045f * ship.charge)
        if (e.counted) kills++
        if (f.isRock()) {
            rocks++
            rockBurst(e)
            sfx.play(Snd.ROCK, 0.6f)
            val kids = when (f) { Foe.ROCK_L -> 3; Foe.ROCK_M -> 2 + rnd.nextInt(2); else -> 0 }
            for (k in 0 until kids) {
                val a = rnd.nextFloat() * 6.283f
                val c = newEnemy(if (f == Foe.ROCK_L) Foe.ROCK_M else Foe.ROCK_S, e.x + cos(a) * 0.5f, e.y + sin(a) * 0.5f) ?: break
                c.vx = e.vx + cos(a) * 3f; c.vy = e.vy + sin(a) * 3f
                c.spin = (rnd.nextFloat() - 0.5f) * 3f; c.rollV = (rnd.nextFloat() - 0.5f) * 3f
                c.mode = 1; c.counted = false
            }
            if (spec?.mode == Mode.MINING) {
                val ore = when (f) { Foe.ROCK_L -> 3 + rnd.nextInt(3); Foe.ROCK_M -> 1 + rnd.nextInt(2); else -> if (rnd.nextFloat() < 0.3f) 1 else 0 }
                for (k in 0 until ore) drop(e.x, e.y, PKind.ORE, 1)
            }
        } else {
            explode(e.x, e.y, e.r * e.size, if (e.mode >= 2) 0xFFFF4FB8.toInt() else 0xFFFF8A40.toInt())
            flair.kill(parts, rnd, e.x, e.y, e.r * e.size)
            sfx.play(if (f == Foe.CARRIER || e.mode >= 2) Snd.BIG_BOOM else Snd.BOOM, 0.7f, pan = ((e.x - px) / 20f).coerceIn(-1f, 1f))
            shake = max(shake, if (e.mode >= 2) 1f else 0.3f)
        }
        if (e.mode >= 2) {
            elites++
            objective++
            score += 2000
            sfx.buzz(Haptics.Kind.BIG_BOOM)
            listener?.event(WorldEvent.Callout(if (e.mode == 2) "Ace down!  $objective/$goal" else "Target destroyed  $objective/$goal", big = true))
        }
        var value = (f.credits * (1f + difficulty * 0.18f) * (0.7f + rnd.nextFloat() * 0.6f)).toInt()
        if (e.mode >= 2) value += 40 + (difficulty * 8).toInt()
        while (value >= 10) { drop(e.x, e.y, PKind.GEM, 10); value -= 10 }
        for (k in 0 until min(value, 5)) drop(e.x, e.y, PKind.CREDIT, 1)
        if (f == Foe.GUNSHIP || f == Foe.SPINNER || f == Foe.LANCER || f == Foe.CARRIER) {
            val r = rnd.nextFloat()
            if (r < 0.12f) drop(e.x, e.y, PKind.REPAIR, 0) else if (r < 0.3f) drop(e.x, e.y, PKind.CHARGE, 0)
        }
    }

    private fun hurt(dmg: Float) {
        if (hub || !alive || iframes > 0f || overdrive || state != State.PLAYING) return
        hp -= dmg
        hits++
        hullRegenT = 0f
        iframes = ship.iframes * 0.6f
        shake = max(shake, 0.9f)
        flash(0xFFFF3040.toInt(), 0.35f)
        sfx.play(Snd.HURT)
        sfx.buzz(Haptics.Kind.HIT)
        burst(px, py, 0xFFFF6060.toInt(), 10, 8f, 0.4f)
        clearNear(px, py, 3.2f)
        if (hp <= 0f) die()
    }

    private fun die() {
        hp = 0f
        alive = false
        state = State.DOWN
        downT = 0f
        stickOn = false; stickId = -1
        explode(px, py, 1.6f, glow)
        sfx.play(Snd.BIG_BOOM)
        sfx.buzz(Haptics.Kind.BIG_BOOM)
    }

    private fun drop(x: Float, y: Float, kind: Int, value: Int) {
        val a = rnd.nextFloat() * 6.283f
        val s = 2f + rnd.nextFloat() * 4f
        pickups.add(x, y, cos(a) * s, sin(a) * s, kind, value)
    }

    private fun updatePickups(h: Float) {
        val p = pickups
        val mag = if (::ship.isInitialized) ship.magnet * 1.6f + 2f else 6f
        var i = p.n - 1
        while (i >= 0) {
            p.age[i] += h
            val dx = px - p.x[i]; val dy = py - p.y[i]
            val d = hypot(dx, dy)
            if (alive && (d < mag || p.pulled[i])) {
                p.pulled[i] = true
                val sp = 10f + p.age[i] * 25f
                p.vx[i] = dx / max(d, 0.01f) * sp; p.vy[i] = dy / max(d, 0.01f) * sp
            } else {
                val k = max(0f, 1f - h * 2f)
                p.vx[i] *= k; p.vy[i] *= k
            }
            p.x[i] += p.vx[i] * h; p.y[i] += p.vy[i] * h
            if (alive && d < 0.9f) { collect(i); p.remove(i); i--; continue }
            if (p.age[i] > 25f || d > 90f) { p.remove(i); i--; continue }
            i--
        }
    }

    private fun collect(i: Int) {
        val p = pickups
        pickupsN++
        when (p.kind[i]) {
            PKind.CREDIT, PKind.GEM -> {
                credits += p.value[i]
                score += p.value[i] * 10L
                sfx.play(Snd.COIN, 0.35f, minGapMs = 40)
            }
            PKind.REPAIR -> {
                hp = min(ship.maxHp.toFloat(), hp + ship.maxHp * 0.2f)
                sfx.play(Snd.POWER)
                listener?.event(WorldEvent.Callout("Hull repaired"))
            }
            PKind.CHARGE -> { charge = min(1f, charge + 0.25f); sfx.play(Snd.POWER) }
            PKind.ORE -> {
                objective += p.value[i]
                score += 50
                sfx.play(Snd.PICKUP, 0.4f, 1f + min(objective, 40) * 0.01f, minGapMs = 40)
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- effects

    private fun updateParticles(h: Float) {
        val p = parts
        var i = p.n - 1
        while (i >= 0) {
            p.life[i] -= h
            if (p.life[i] <= 0f) { p.remove(i); i--; continue }
            val k = max(0f, 1f - p.drag[i] * h)
            p.vx[i] *= k; p.vy[i] *= k
            p.x[i] += p.vx[i] * h; p.y[i] += p.vy[i] * h
            p.size[i] = max(0.01f, p.size[i] + p.grow[i] * h)
            i--
        }
    }

    private fun flash(color: Int, amount: Float) { flashColor = color; flash = max(flash, amount) }

    private fun arc(x1: Float, y1: Float, x2: Float, y2: Float) {
        val a = arcs.firstOrNull { it.life <= 0f } ?: return
        a.x1 = x1; a.y1 = y1; a.x2 = x2; a.y2 = y2; a.life = 0.14f; a.seed = rnd.nextInt()
    }

    private fun clearNear(x: Float, y: Float, r: Float) {
        val b = bullets
        var i = b.n - 1
        while (i >= 0) {
            val dx = b.x[i] - x; val dy = b.y[i] - y
            if (dx * dx + dy * dy < r * r) {
                parts.add(b.x[i], b.y[i], dx * 0.5f, dy * 0.5f, 0.3f, 0.35f, World.BULLET_COLORS[b.color[i]], Shape.SOFT, 4f)
                b.remove(i)
            }
            i--
        }
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, speed: Float, life: Float) {
        for (k in 0 until n) {
            val a = rnd.nextFloat() * 6.283f
            val s = speed * (0.3f + rnd.nextFloat())
            parts.add(x, y, cos(a) * s, sin(a) * s, life * (0.5f + rnd.nextFloat()), 0.18f + rnd.nextFloat() * 0.15f, color, Shape.SPARK, 3f)
        }
    }

    private fun ring(x: Float, y: Float, color: Int, life: Float, grow: Float) {
        parts.add(x, y, 0f, 0f, life, 0.3f, color, Shape.WAVE, 0f, grow)
    }

    private fun explode(x: Float, y: Float, size: Float, color: Int) {
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
        for (k in 0 until (6 + s * 10).toInt()) {
            val a = rnd.nextFloat() * 6.283f
            val sp = 2f + rnd.nextFloat() * 6f
            parts.add(e.x, e.y, cos(a) * sp + e.vx, sin(a) * sp + e.vy, 0.5f + rnd.nextFloat() * 0.5f, 0.12f + rnd.nextFloat() * 0.18f, 0xFFB09A88.toInt(), Shape.DIAMOND, 1.5f)
        }
        burst(e.x, e.y, 0xFFFFC080.toInt(), (4 + s * 6).toInt(), 7f, 0.35f)
    }

    companion object {
        const val STEP = 1f / 90f
        private val ACES = listOf("Red Vesper", "Kestrel", "Nine Lives", "The Widow", "Old Ironside", "Halcyon", "Mordant", "Stray Cat")

        /** [a] wrapped to -π..π. */
        fun wrap(a: Float): Float {
            var x = a
            while (x > PI) x -= (2 * PI).toFloat()
            while (x < -PI) x += (2 * PI).toFloat()
            return x
        }

        /** [from] turned toward [to] by at most [step] radians. */
        /** How far the stick must move, as a fraction of its reach, before the ship does. */
        const val DEAD = 0.1f

        fun turnTo(from: Float, to: Float, step: Float): Float {
            val d = wrap(to - from)
            return wrap(from + d.coerceIn(-step, step))
        }
    }
}
