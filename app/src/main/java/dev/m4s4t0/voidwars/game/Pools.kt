package dev.m4s4t0.voidwars.game

import dev.m4s4t0.voidwars.model.Boss
import dev.m4s4t0.voidwars.model.Foe

/*
 * Everything that flies is kept in fixed pools made once at start-up: nothing is allocated while playing, so the
 * garbage collector never stutters a frame. Removal swaps the last live entry into the hole, so loops that remove run
 * backwards.
 */

/** Enemy bullet looks: [shape] picks the sprite, [color] one of [World.BULLET_COLORS]. */
object BShape {
    const val ORB = 0
    const val BIG = 1
    const val RICE = 2
    const val DIAMOND = 3
    const val RING = 4
}

class Bullets(val cap: Int) {
    var n = 0
    val x = FloatArray(cap); val y = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap)
    val r = FloatArray(cap)
    /** Speed change a second (a fraction), and turn a second (radians). */
    val acc = FloatArray(cap); val turn = FloatArray(cap)
    val age = FloatArray(cap)
    /** Seconds until it bursts into a ring of [burst] bullets (0 = never). */
    val fuse = FloatArray(cap); val burst = IntArray(cap)
    val shape = IntArray(cap); val color = IntArray(cap)
    val grazed = BooleanArray(cap)

    fun add(px: Float, py: Float, pvx: Float, pvy: Float, pr: Float, pShape: Int, pColor: Int, pAcc: Float = 0f, pTurn: Float = 0f): Int {
        if (n >= cap) return -1
        val i = n++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; r[i] = pr
        acc[i] = pAcc; turn[i] = pTurn; age[i] = 0f; fuse[i] = 0f; burst[i] = 0
        shape[i] = pShape; color[i] = pColor; grazed[i] = false
        return i
    }

    fun remove(i: Int) {
        val l = --n
        if (i == l) return
        x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]; r[i] = r[l]
        acc[i] = acc[l]; turn[i] = turn[l]; age[i] = age[l]; fuse[i] = fuse[l]; burst[i] = burst[l]
        shape[i] = shape[l]; color[i] = color[l]; grazed[i] = grazed[l]
    }
}

/** The player's shots. */
object SKind {
    const val BOLT = 0
    const val PELLET = 1
    const val MISSILE = 2
    const val PLASMA = 3
    const val RAIL = 4
    const val FLAK = 5
    const val SHRAPNEL = 6
    const val DRONE = 7
}

class Shots(val cap: Int) {
    var n = 0
    val x = FloatArray(cap); val y = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap)
    val r = FloatArray(cap)
    val dmg = FloatArray(cap)
    val life = FloatArray(cap)
    val age = FloatArray(cap)
    val kind = IntArray(cap)
    /** Piercing shots remember the last enemy they hit, so they don't hit it again every step. */
    val last = IntArray(cap)

    fun add(px: Float, py: Float, pvx: Float, pvy: Float, pr: Float, pDmg: Float, pLife: Float, pKind: Int): Int {
        if (n >= cap) return -1
        val i = n++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; r[i] = pr
        dmg[i] = pDmg; life[i] = pLife; age[i] = 0f; kind[i] = pKind; last[i] = -1
        return i
    }

    fun remove(i: Int) {
        val l = --n
        if (i == l) return
        x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]; r[i] = r[l]
        dmg[i] = dmg[l]; life[i] = life[l]; age[i] = age[l]; kind[i] = kind[l]; last[i] = last[l]
    }
}

/** Sprite looks the renderer knows (shared by particles, shots and bullets). */
object Shape {
    const val ORB = 0
    const val RING = 1
    const val SPARK = 2
    const val SOFT = 3
    const val DIAMOND = 4
    const val BEAM = 5
    const val STAR = 6
    const val WAVE = 7
    const val BULLET = 8
    const val HEX = 9
    const val HOLE = 10
    const val FILL = 11
}

class Particles(val cap: Int) {
    var n = 0
    /** How many of the pool to use (the quality setting). */
    var limit = cap
    val x = FloatArray(cap); val y = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap)
    val life = FloatArray(cap); val max = FloatArray(cap)
    val size = FloatArray(cap); val grow = FloatArray(cap)
    val drag = FloatArray(cap)
    val color = IntArray(cap); val shape = IntArray(cap)

    fun add(px: Float, py: Float, pvx: Float, pvy: Float, pLife: Float, pSize: Float, pColor: Int, pShape: Int, pDrag: Float = 2f, pGrow: Float = 0f) {
        if (n >= limit) return
        val i = n++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = pLife; max[i] = pLife; size[i] = pSize; grow[i] = pGrow; drag[i] = pDrag
        color[i] = pColor; shape[i] = pShape
    }

    fun remove(i: Int) {
        val l = --n
        if (i == l) return
        x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]
        life[i] = life[l]; max[i] = max[l]; size[i] = size[l]; grow[i] = grow[l]; drag[i] = drag[l]
        color[i] = color[l]; shape[i] = shape[l]
    }
}

object PKind {
    const val CREDIT = 0
    const val GEM = 1
    const val REPAIR = 2
    const val CHARGE = 3
    /** A gun for a while; the value is its Primary ordinal. */
    const val WEAPON = 4
}

class Pickups(val cap: Int) {
    var n = 0
    val x = FloatArray(cap); val y = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap)
    val age = FloatArray(cap)
    val kind = IntArray(cap); val value = IntArray(cap)
    /** Pulled in by the magnet (or the stage-clear vacuum): they never let go. */
    val pulled = BooleanArray(cap)

    fun add(px: Float, py: Float, pvx: Float, pvy: Float, pKind: Int, pValue: Int): Int {
        if (n >= cap) return -1
        val i = n++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; age[i] = 0f; kind[i] = pKind; value[i] = pValue; pulled[i] = false
        return i
    }

    fun remove(i: Int) {
        val l = --n
        if (i == l) return
        x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]; age[i] = age[l]; kind[i] = kind[l]; value[i] = value[l]; pulled[i] = pulled[l]
    }
}

/** An enemy laser: a warning line for [warn] seconds, then a deadly beam for [life]. Follows its owner if it has one. */
class Beam {
    var active = false
    var x = 0f; var y = 0f
    var ang = 0f
    /** Radians a second, for sweeping beams. */
    var spin = 0f
    var width = 0.5f
    var warn = 0f
    var life = 0f
    var age = 0f
    var color = 0
    var owner: Enemy? = null
    var ox = 0f; var oy = 0f
    val firing get() = age >= warn
}

/** A lightning bolt between two points, for the tesla coil. */
class Arc {
    var life = 0f
    var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f
    var seed = 0
}

/** One enemy. Its behaviour lives in Foes.kt (and Bosses.kt), chosen by [foe] and [mode]. */
class Enemy {
    var alive = false
    var foe = Foe.DRONE
    var boss: Boss? = null
    var mode = 0
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var hp = 0f; var maxHp = 0f
    var r = 0.5f
    var t = 0f
    /** Free numbers for the behaviour: a start position, a phase, a target. */
    var a = 0f; var b = 0f; var c = 0f; var d = 0f
    var fireT = 0f
    var fired = 0
    var phase = 0
    var phaseT = 0f
    /** Rotation shown (radians about the view axis), spin, and a roll for the 3D tumble. */
    var rot = 0f; var spin = 0f
    var roll = 0f; var rollV = 0f
    var flash = 0f
    var seed = 0
    /** Counts towards the stage's kill ratio (rock fragments and summoned drones don't). */
    var counted = true
    /** Has been on screen: only then can it leave. */
    var entered = false
    var size = 1f

    val frac get() = if (maxHp > 0f) hp / maxHp else 0f
}
