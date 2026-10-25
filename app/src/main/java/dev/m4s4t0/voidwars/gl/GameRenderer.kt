package dev.m4s4t0.voidwars.gl

import android.opengl.GLES20.GL_BLEND
import android.opengl.GLES20.GL_COLOR_BUFFER_BIT
import android.opengl.GLES20.GL_DEPTH_BUFFER_BIT
import android.opengl.GLES20.GL_DEPTH_TEST
import android.opengl.GLES20.GL_FLOAT
import android.opengl.GLES20.GL_LEQUAL
import android.opengl.GLES20.GL_ONE
import android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA
import android.opengl.GLES20.GL_TRIANGLE_STRIP
import android.opengl.GLES20.glBlendFunc
import android.opengl.GLES20.glClear
import android.opengl.GLES20.glClearColor
import android.opengl.GLES20.glDepthFunc
import android.opengl.GLES20.glDepthMask
import android.opengl.GLES20.glDisable
import android.opengl.GLES20.glDisableVertexAttribArray
import android.opengl.GLES20.glDrawArrays
import android.opengl.GLES20.glEnable
import android.opengl.GLES20.glEnableVertexAttribArray
import android.opengl.GLES20.glVertexAttribPointer
import android.opengl.GLES20.glViewport
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import dev.m4s4t0.voidwars.game.Arc
import dev.m4s4t0.voidwars.game.BShape
import dev.m4s4t0.voidwars.game.Bullets
import dev.m4s4t0.voidwars.game.Particles
import dev.m4s4t0.voidwars.game.Pickups
import dev.m4s4t0.voidwars.game.Shots
import dev.m4s4t0.voidwars.game.Space
import dev.m4s4t0.voidwars.game.Station
import dev.m4s4t0.voidwars.game.OKind
import dev.m4s4t0.voidwars.model.MissionSpec
import dev.m4s4t0.voidwars.game.Enemy
import dev.m4s4t0.voidwars.game.PKind
import dev.m4s4t0.voidwars.game.SKind
import dev.m4s4t0.voidwars.game.Shape
import dev.m4s4t0.voidwars.game.World
import dev.m4s4t0.voidwars.game.WorldListener
import dev.m4s4t0.voidwars.model.Boss
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Foe
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.HubTheme
import dev.m4s4t0.voidwars.model.Scenery
import dev.m4s4t0.voidwars.model.Loadout
import dev.m4s4t0.voidwars.model.Quality
import dev.m4s4t0.voidwars.model.ShipStats
import dev.m4s4t0.voidwars.model.Special
import dev.m4s4t0.voidwars.model.Stage
import dev.m4s4t0.voidwars.model.Support
import dev.m4s4t0.voidwars.model.swatch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Draws the game. Owns the [World], which only ever runs on the GL thread; the UI talks to it through
 * GameView.queueEvent. Three modes: the menu (an attract-mode battle in the background), the hangar (one ship,
 * turning, lit up like a showroom) and play.
 */
class GameRenderer(private val quality: Quality, listener: WorldListener) : GLSurfaceView.Renderer {
    enum class Mode { MENU, HANGAR, PLAY, SPACE }

    val world = World().also { it.listener = listener }
    /** Free flight: the hub and the open-space missions. */
    val space = Space().also { it.listener = listener }
    var mode = Mode.MENU
        private set
    var paused = false
    /** The ship on the hangar's turntable. */
    var preview = Loadout()
    var yaw = -0.6f
    private var yawV = 0f
    var viewH = 1
    var viewW = 1
    var showHitbox = true
    var shakeOn = true

    private lateinit var lit: Program
    private lateinit var sprite: Program
    private lateinit var sky: Program
    private val batch = Batch(7000)
    private var quadVbo = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))

    private val hulls = HashMap<Any, LitMesh>()
    private fun mesh(key: Any, make: () -> FloatArray) = hulls.getOrPut(key) { LitMesh(make()) }

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var aspect = 1.7f
    private var w = 1
    private var h = 1
    private var last = 0L
    private var time = 0f
    private val camZ = World.H / tan(Math.toRadians(20.0)).toFloat()
    private val projSpace = FloatArray(16)
    private val ui = FloatArray(16)
    private val v4 = FloatArray(4)
    private val o4 = FloatArray(4)
    // the chase camera's smoothed target
    private var camX = 0f
    private var camY = 0f

    // ---------------------------------------------------------------- commands (GL thread)

    fun menu() {
        mode = Mode.MENU
        space.stop()
        paused = false
        val l = Game.loadout
        world.start(l, l.stats { 3 }, null, demo = true, quality = quality.particles)
    }

    fun hangar(l: Loadout) {
        mode = Mode.HANGAR
        space.stop()
        preview = l
        paused = false
    }

    fun play(l: Loadout, stats: ShipStats, stage: Stage?, spec: MissionSpec? = null) {
        mode = Mode.PLAY
        space.stop()
        paused = false
        world.start(l, stats, stage, quality = quality.particles, spec = spec)
    }

    /** Free flight around the hub; [from] is the station just left. */
    fun hub(l: Loadout, stats: ShipStats, from: Station?, theme: HubTheme) {
        mode = Mode.SPACE
        paused = false
        world.stop()
        space.hub(l, stats, from, quality.particles, theme)
        camX = space.px; camY = space.py
    }

    /** An open-space mission. */
    fun mission(l: Loadout, stats: ShipStats, spec: MissionSpec) {
        mode = Mode.SPACE
        paused = false
        world.stop()
        space.start(l, stats, spec, quality.particles)
        camX = space.px; camY = space.py
    }

    fun drag(dx: Float) { yawV = 0f; yaw += dx * 0.012f }
    fun fling(v: Float) { yawV = v * 0.012f }

    // ---------------------------------------------------------------- GL

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        lit = Program(Shaders.LIT_VS, Shaders.LIT_FS)
        sprite = Program(Shaders.SPRITE_VS, Shaders.SPRITE_FS)
        sky = Program(Shaders.SKY_VS, Shaders.SKY_FS)
        batch.init()
        hulls.clear()
        glClearColor(0f, 0f, 0f, 1f)
        glDepthFunc(GL_LEQUAL)
        last = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width; h = height
        glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
        world.W = World.H * aspect
        Matrix.perspectiveM(proj, 0, 40f, aspect, 1f, 80f)
        Matrix.perspectiveM(projSpace, 0, 48f, aspect, 1f, 260f)
        if (mode == Mode.MENU && world.state == World.State.IDLE) menu()
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = (now - last) / 1e9f
        // The menu and hangar don't need more than 30 frames a second.
        if (mode != Mode.PLAY && mode != Mode.SPACE && dt < 1f / 31f) {
            try { Thread.sleep(((1f / 30f - dt) * 1000f).toLong().coerceIn(0L, 33L)) } catch (_: InterruptedException) {}
            dt = (System.nanoTime() - last) / 1e9f
        }
        last = System.nanoTime()
        dt = min(dt, 0.1f)
        time += dt
        world.unitsPerPx = 2f * World.H / viewH.coerceAtLeast(1)
        when (mode) {
            Mode.MENU -> world.frame(dt)
            Mode.PLAY -> if (!paused) world.frame(dt)
            Mode.SPACE -> { space.stickR = viewH * 0.12f; if (!paused) space.frame(dt) }
            Mode.HANGAR -> { yaw += yawV * dt * 60f; yawV *= 0.94f; if (abs(yawV) < 0.0005f) yaw += dt * 0.35f }
        }

        // Camera, with shake.
        var sx = 0f; var sy = 0f
        if (mode == Mode.PLAY && shakeOn && world.shake > 0f) {
            val s = world.shake * world.shake * 0.35f
            sx = (world.rnd.nextFloat() - 0.5f) * s; sy = (world.rnd.nextFloat() - 0.5f) * s
        }
        if (mode == Mode.SPACE) { spaceFrame(dt); return }
        Matrix.setLookAtM(view, 0, sx, sy, camZ, sx, sy, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        drawSky()
        // Glows that belong under the ship, so they never hide it.
        spritePass { if (mode == Mode.HANGAR) hangarPad() else shipUnderglow() }
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        lit.use()
        lit.f("uTime", time)
        lit.v3("uEye", sx, sy, camZ)
        if (mode == Mode.HANGAR) drawHangar() else drawWorldMeshes()
        glDisable(GL_DEPTH_TEST)
        spritePass { if (mode == Mode.HANGAR) hangarSprites() else worldSprites() }
        glDepthMask(true)
    }

    private inline fun spritePass(m: FloatArray = vp, z: Float = 0f, draw: () -> Unit) {
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        sprite.use()
        sprite.m4("uVp", m)
        sprite.f("uZ", z)
        batch.begin()
        draw()
        batch.flush(sprite)
        glDisable(GL_BLEND)
    }

    // ---------------------------------------------------------------- sky

    private fun drawSky() {
        sky.use()
        sky.v2("uRes", w.toFloat(), h.toFloat())
        sky.f("uTime", time)
        sky.f("uOct", quality.nebula.toFloat())
        val free = mode == Mode.SPACE
        val sec = if (free) (if (space.hub) null else Campaign.SECTORS.getOrNull(space.sector)) else Campaign.SECTORS.getOrNull(world.sector)
        val rift = mode == Mode.PLAY && world.rift
        when {
            free && sec == null -> { val th = space.theme; sky.rgb("uNebA", th.nebulaA.toInt()); sky.rgb("uNebB", th.nebulaB.toInt()); sky.rgb("uPlanetCol", th.planet.toInt()) }
            mode == Mode.HANGAR -> { sky.rgb("uNebA", 0xFF3A2380.toInt()); sky.rgb("uNebB", 0xFF0B4A66.toInt()); sky.rgb("uPlanetCol", 0xFF7B5CFF.toInt()) }
            rift -> { sky.rgb("uNebA", 0xFF5A0F6E.toInt()); sky.rgb("uNebB", 0xFF102A80.toInt()); sky.rgb("uPlanetCol", 0xFFB040FF.toInt()) }
            mode == Mode.MENU || sec == null -> { sky.rgb("uNebA", 0xFF2D1A70.toInt()); sky.rgb("uNebB", 0xFF0B5570.toInt()); sky.rgb("uPlanetCol", 0xFF8A6CFF.toInt()) }
            else -> { sky.rgb("uNebA", sec.nebulaA.toInt()); sky.rgb("uNebB", sec.nebulaB.toInt()); sky.rgb("uPlanetCol", sec.planet.toInt()) }
        }
        val scroll = if (mode == Mode.HANGAR) time * 4f else world.t * 30f
        // The planet drifts past very slowly, a little further each stage.
        val stageOff = (world.stage?.index ?: 0) * 0.35f
        val px = aspect * 0.75f - ((scroll * 0.0012f + stageOff) % (aspect * 2f + 1.6f))
        when {
            free && space.hub -> { val th = space.theme; sky.v3("uPlanet", aspect * th.planetX - space.px * 0.0015f, th.planetY - space.py * 0.0015f, th.planetSize) }
            free -> sky.v3("uPlanet", aspect * 0.55f - space.px * 0.0015f, 0.35f - space.py * 0.0015f, 0.5f)
            mode == Mode.HANGAR -> sky.v3("uPlanet", -aspect * 0.15f, -0.85f, 0.65f)
            else -> sky.v3("uPlanet", px, -0.42f + 0.1f * (world.sector % 5), 0.42f)
        }
        // free flight scrolls the sky both ways with the ship
        sky.f("uScroll", if (free) space.px * 3f else scroll)
        sky.f("uScrollY", if (free) space.py * 3f else 0f)
        val f = if (mode == Mode.PLAY) world.flash else if (free) space.flash else 0f
        sky.f("uFlash", f)
        sky.rgb("uFlashCol", if (free) space.flashColor else world.flashColor)
        sky.f("uDim", if (mode == Mode.HANGAR) 0.75f else if (mode == Mode.MENU) 0.85f else 1f)
        val ap = sky.a("aPos")
        glEnableVertexAttribArray(ap)
        quadVbo.position(0)
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ARRAY_BUFFER, 0)
        glVertexAttribPointer(ap, 2, GL_FLOAT, false, 8, quadVbo)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(ap)
    }

    // ---------------------------------------------------------------- meshes

    private fun paint(l: Loadout) {
        lit.rgb("uC0", swatch(l.paint.primary).argb.toInt())
        lit.rgb("uC1", swatch(l.paint.secondary).argb.toInt())
        lit.rgb("uC2", swatch(l.paint.glow).argb.toInt())
        lit.f("uFinish", l.paint.finish.ordinal.toFloat())
        lit.f("uDecal", l.paint.decal.ordinal.toFloat())
    }

    private fun colors(c0: Int, c1: Int, c2: Int, finish: Float = 1f) {
        lit.rgb("uC0", c0); lit.rgb("uC1", c1); lit.rgb("uC2", c2)
        lit.f("uFinish", finish); lit.f("uDecal", 0f)
    }

    private fun drawMesh(m: LitMesh, flash: Float) {
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        lit.m4("uMvp", mvp)
        lit.m4("uModel", model)
        lit.f("uFlash", flash)
        m.draw(lit)
    }

    private fun shipMeshes(l: Loadout, flash: Float) {
        drawMesh(mesh(l.hull) { ShipMeshes.hull(l.hull) }, flash)
        drawMesh(mesh(l.wings) { ShipMeshes.wings(l.wings) }, flash)
        drawMesh(mesh(l.engine) { ShipMeshes.engine(l.engine) }, flash)
    }

    private fun drawHangar() {
        val l = preview
        paint(l)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, -world.W * 0.36f, 0.4f, 2f)
        Matrix.rotateM(model, 0, -52f, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 0f, 1f)
        Matrix.translateM(model, 0, 0f, 0f, sin(time * 1.3f) * 0.08f)
        Matrix.scaleM(model, 0, 3.6f, 3.6f, 3.6f)
        shipMeshes(l, 0f)
        if (l.support.base == Support.DRONES) {
            val base = model.copyOf()
            for (s in floatArrayOf(1f, -1f)) {
                System.arraycopy(base, 0, model, 0, 16)
                Matrix.translateM(model, 0, -0.5f, s * 1.45f, 0.1f + sin(time * 2f + s) * 0.05f)
                Matrix.scaleM(model, 0, 0.8f, 0.8f, 0.8f)
                drawMesh(mesh("drone") { ShipMeshes.drone() }, 0f)
            }
        }
    }

    private fun drawWorldMeshes() {
        val wd = world
        // Enemies.
        for (i in 0 until wd.ne) {
            val e = wd.enemies[i]
            if (!e.alive) continue
            enemyColors(e)
            val s = e.r * e.size
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, e.x, e.y, 0f)
            Matrix.rotateM(model, 0, Math.toDegrees(e.rot.toDouble()).toFloat(), 0f, 0f, 1f)
            if (isRock(e.foe)) Matrix.rotateM(model, 0, Math.toDegrees(e.roll.toDouble()).toFloat(), 0.6f, 0.8f, 0.2f)
            else Matrix.rotateM(model, 0, Math.toDegrees(e.roll.toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.scaleM(model, 0, s, s, s)
            drawMesh(enemyMesh(e), min(1f, e.flash * 14f))
        }
        // The player.
        if (wd.alive && wd.state != World.State.IDLE) {
            val blink = wd.iframes > 0f && !wd.demo && ((wd.t * 12f).toInt() % 2 == 0)
            if (!blink) {
                paint(wd.loadout)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, wd.px, wd.py, 0.2f)
                Matrix.rotateM(model, 0, Math.toDegrees(wd.bank.toDouble()).toFloat(), 1f, 0f, 0f)
                val od = wd.specialT > 0f && wd.loadout.special.base == Special.OVERDRIVE
                shipMeshes(wd.loadout, if (od) 0.15f + 0.15f * sin(time * 30f) else 0f)
            }
            if (wd.loadout.support.base == Support.DRONES) {
                paint(wd.loadout)
                for (k in 0 until 2) {
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, wd.wpn.dX[k], wd.wpn.dY[k], 0.2f)
                    Matrix.rotateM(model, 0, Math.toDegrees(wd.bank.toDouble() * 0.6).toFloat(), 1f, 0f, 0f)
                    drawMesh(mesh("drone") { ShipMeshes.drone() }, 0f)
                }
            }
        }
    }

    private fun isRock(f: Foe) = f == Foe.ROCK_S || f == Foe.ROCK_M || f == Foe.ROCK_L

    private fun enemyMesh(e: Enemy): LitMesh = when (e.foe) {
        Foe.ROCK_S, Foe.ROCK_M, Foe.ROCK_L -> { val k = abs(e.seed) % 3; mesh("rock$k") { ShipMeshes.rock(k * 5 + 1) } }
        Foe.DRONE -> mesh(Foe.DRONE) { ShipMeshes.drone2() }
        Foe.DART -> mesh(Foe.DART) { ShipMeshes.dart() }
        Foe.GUNSHIP -> mesh(Foe.GUNSHIP) { ShipMeshes.gunship() }
        Foe.SPINNER -> mesh(Foe.SPINNER) { ShipMeshes.spinner() }
        Foe.MINE -> mesh(Foe.MINE) { ShipMeshes.mine() }
        Foe.LANCER -> mesh(Foe.LANCER) { ShipMeshes.lancer() }
        Foe.CARRIER -> mesh(Foe.CARRIER) { ShipMeshes.carrier() }
        Foe.BOSS -> { val b = e.boss ?: Boss.WARDEN; mesh(b) { ShipMeshes.boss(b) } }
    }

    private fun enemyColors(e: Enemy, sec: Int = world.sector) {
        when (e.foe) {
            Foe.ROCK_S, Foe.ROCK_M, Foe.ROCK_L -> {
                val rock = intArrayOf(0xFF6B5E58.toInt(), 0xFF6E4A44.toInt(), 0xFF4E5A6E.toInt(), 0xFF4A4A52.toInt(), 0xFF3A2E52.toInt())[sec % 5]
                colors(rock, rock, 0xFFFF8A3A.toInt(), 0f)
            }
            Foe.DRONE -> colors(0xFF5A1E2E.toInt(), 0xFF2A2A36.toInt(), 0xFFFF3D6A.toInt())
            Foe.DART -> colors(0xFF6A2A12.toInt(), 0xFF2A2230.toInt(), 0xFFFF9A2A.toInt())
            Foe.GUNSHIP -> colors(0xFF3A3F4E.toInt(), 0xFF7A1E2A.toInt(), 0xFFFF4A3A.toInt(), 2f)
            Foe.SPINNER -> colors(0xFF4A2468.toInt(), 0xFF22222E.toInt(), 0xFFD070FF.toInt(), 2f)
            Foe.MINE -> colors(0xFF2C2C34.toInt(), 0xFFB8202E.toInt(), 0xFFFF2A3A.toInt(), 2f)
            Foe.LANCER -> colors(0xFF1E3A5A.toInt(), 0xFF2A2A36.toInt(), 0xFFFF3D8B.toInt(), 2f)
            Foe.CARRIER -> colors(0xFF34384A.toInt(), 0xFF6A2234.toInt(), 0xFFFFB03A.toInt(), 2f)
            Foe.BOSS -> { val b = e.boss ?: Boss.WARDEN; val fin = intArrayOf(2, 1, 3, 2, 5)[b.style]; if (b.hull != 0L) colors(b.hull.toInt(), b.trim.toInt(), b.glow.toInt(), fin.toFloat()) else when (b) {
                Boss.WARDEN -> colors(0xFF4A4E5E.toInt(), 0xFF8A6A2A.toInt(), 0xFFFFC23D.toInt(), 2f)
                Boss.HYDRA -> colors(0xFF5A1626.toInt(), 0xFF2E2A36.toInt(), 0xFFFF4A6A.toInt(), 1f)
                Boss.TEMPEST -> colors(0xFF14405A.toInt(), 0xFFA0E8FF.toInt(), 0xFF7CE0FF.toInt(), 3f)
                Boss.REVENANT -> colors(0xFF262A2E.toInt(), 0xFF3E4A3A.toInt(), 0xFF7BFF9A.toInt(), 2f)
                else -> colors(0xFF1A0E2E.toInt(), 0xFF3A1E6A.toInt(), 0xFFC070FF.toInt(), 5f)
            } }
        }
    }

    // ---------------------------------------------------------------- sprites

    /** A soft glow pad under the hangar's ship, with a slow pulse. */
    private fun hangarPad() {
        val g = swatch(preview.paint.glow).argb.toInt()
        val x = -world.W * 0.36f
        batch.dot(x, -1.4f, 5.5f, g, 0.55f, Shape.SOFT)
        batch.quad(x, -1.4f, 5.2f, 1.6f, 1f, 0f, 0xFFFFFFFF.toInt(), 0.6f, Shape.WAVE)
        val a = (0.5f + 0.5f * sin(time * 2f))
        batch.quad(x, -1.4f, 4.4f * (0.9f + 0.1f * a), 1.3f, 1f, 0f, g, 0.7f, Shape.RING)
    }

    /** The engines' glow and the hitbox ring, drawn before the ship so it sits on top of them. */
    private fun shipUnderglow() {
        val wd = world
        if (!wd.alive || wd.state == World.State.IDLE) return
        val noz = ShipMeshes.nozzles(wd.loadout.engine)
        for (o in noz) {
            val fl = 0.85f + 0.15f * sin(time * 50f + o * 9f)
            batch.dot(wd.px - 1.12f, wd.py + o * cos(wd.bank), 0.42f * fl, wd.glow, 1f, Shape.ORB)
        }
        if (mode == Mode.PLAY && showHitbox) batch.dot(wd.px, wd.py, wd.ship.hitbox * 4.2f, wd.glow, 0.45f, Shape.RING)
    }

    private fun hangarSprites() {
        val l = preview
        val g = swatch(l.paint.glow).argb.toInt()
        val x = -world.W * 0.36f
        if (l.support.base == Support.ORBITALS) {
            for (k in 0 until 3) {
                val an = time * 1.6f + k * 2.094f
                batch.dot(x + cos(an) * 5.5f, 0.4f + sin(an) * 2.2f, 0.55f, g, 1f, Shape.ORB)
            }
        }
    }

    private fun worldSprites() {
        val wd = world
        val glow = wd.glow
        // Pickups under everything else.
        pickupSprites(wd.pickups)
        particleSprites(wd.parts)
        shotSprites(wd.shots, wd.flair.shotColor(wd.t), wd.trim)
        worldExtras()
        arcSprites(wd.arcs)
        worldBeams()
        bulletSprites(wd.bullets)
        worldOverlay()
    }

    private fun pickupSprites(pk: Pickups) {
        for (i in 0 until pk.n) {
            val bob = 1f + 0.12f * sin(pk.age[i] * 8f + i)
            when (pk.kind[i]) {
                PKind.CREDIT -> { batch.dot(pk.x[i], pk.y[i], 0.34f * bob, 0xFFFFC23D.toInt(), 1f, Shape.HEX) }
                PKind.GEM -> { batch.dot(pk.x[i], pk.y[i], 0.75f, 0xFFB070FF.toInt(), 1f, Shape.SOFT); batch.dot(pk.x[i], pk.y[i], 0.42f * bob, 0xFFC890FF.toInt(), 1f, Shape.DIAMOND) }
                PKind.WEAPON -> {
                    val c = World.CRATE_COLOR
                    batch.dot(pk.x[i], pk.y[i], 1.1f, c, 0.8f, Shape.SOFT)
                    batch.dot(pk.x[i], pk.y[i], 0.85f * bob, c, 1f, Shape.RING)
                    batch.quad(pk.x[i], pk.y[i], 0.5f * bob, 0.5f * bob, cos(pk.age[i] * 2f), sin(pk.age[i] * 2f), c, 1f, Shape.DIAMOND)
                    batch.dot(pk.x[i], pk.y[i], 0.22f, 0xFFFFFFFF.toInt(), 1f, Shape.ORB)
                }
                PKind.REPAIR -> { batch.dot(pk.x[i], pk.y[i], 0.6f * bob, 0xFF5AFF8A.toInt(), 1f, Shape.ORB); batch.dot(pk.x[i], pk.y[i], 0.7f, 0xFF5AFF8A.toInt(), 1f, Shape.RING) }
                PKind.ORE -> { batch.dot(pk.x[i], pk.y[i], 0.7f, 0xFFFF8A3A.toInt(), 0.7f, Shape.SOFT); batch.dot(pk.x[i], pk.y[i], 0.36f * bob, 0xFFFFB070.toInt(), 1f, Shape.HEX) }
                else -> batch.dot(pk.x[i], pk.y[i], 0.6f * bob, 0xFF7CE0FF.toInt(), 1f, Shape.STAR)
            }
        }
    }

    private fun particleSprites(p: Particles) {
        for (i in 0 until p.n) {
            val f = p.life[i] / p.max[i]
            val sh = p.shape[i]
            if (sh == Shape.SPARK) {
                val sp = hypot(p.vx[i], p.vy[i])
                if (sp > 0.5f) {
                    batch.quad(p.x[i], p.y[i], p.size[i] + sp * 0.025f, p.size[i] * 0.45f, p.vx[i] / sp, p.vy[i] / sp, p.color[i], f, Shape.SPARK)
                    continue
                }
            }
            batch.dot(p.x[i], p.y[i], p.size[i], p.color[i], if (sh == Shape.WAVE || sh == Shape.RING) f else min(1f, f * 1.6f), sh)
        }
    }

    private fun shotSprites(s: Shots, glow: Int, trim: Int) {
        for (i in 0 until s.n) {
            val sp = hypot(s.vx[i], s.vy[i]).coerceAtLeast(0.01f)
            val dx = s.vx[i] / sp; val dy = s.vy[i] / sp
            when (s.kind[i]) {
                SKind.BOLT -> batch.quad(s.x[i], s.y[i], 0.55f, s.r[i] * 1.6f, dx, dy, glow, 1f, Shape.BEAM)
                SKind.DRONE -> batch.quad(s.x[i], s.y[i], 0.4f, s.r[i] * 1.5f, dx, dy, trim, 1f, Shape.BEAM)
                SKind.PELLET -> batch.dot(s.x[i], s.y[i], s.r[i] * 2.4f, glow, 1f, Shape.ORB)
                SKind.MISSILE -> {
                    batch.quad(s.x[i] - dx * 0.25f, s.y[i] - dy * 0.25f, 0.5f, 0.22f, dx, dy, 0xFFFF9A40.toInt(), 1f, Shape.BEAM)
                    batch.quad(s.x[i], s.y[i], 0.3f, 0.16f, dx, dy, 0xFFFFFFFF.toInt(), 1f, Shape.DIAMOND)
                }
                SKind.PLASMA -> {
                    batch.dot(s.x[i], s.y[i], s.r[i] * 2.2f, trim, 1f, Shape.SOFT)
                    batch.dot(s.x[i], s.y[i], s.r[i] * 1.4f, glow, 1f, Shape.ORB)
                    batch.dot(s.x[i], s.y[i], s.r[i] * 1.6f, trim, 1f, Shape.RING)
                }
                SKind.RAIL -> batch.quad(s.x[i] - dx * 1.2f, s.y[i] - dy * 1.2f, 1.9f, s.r[i] * 1.8f, dx, dy, glow, 1f, Shape.BEAM)
                SKind.FLAK -> { batch.dot(s.x[i], s.y[i], s.r[i] * 2.4f, 0xFFFFA040.toInt(), 1f, Shape.ORB) }
                else -> batch.quad(s.x[i], s.y[i], 0.28f, 0.1f, dx, dy, 0xFFFFC070.toInt(), 1f, Shape.SPARK)
            }
        }
    }

    private fun worldExtras() {
        val wd = world
        val glow = wd.glow
        // The player's lance.
        if (wd.laserOn) {
            val x0 = wd.px + 0.9f
            val len = wd.W + 2f - x0
            val lw = wd.wpn.laserW
            val fl = 1f + 0.12f * sin(time * 60f)
            batch.quad(x0 + len / 2, wd.py, len / 2 + 0.3f, lw * 2.4f * fl, 1f, 0f, glow, 1f, Shape.BEAM)
            batch.quad(x0 + len / 2, wd.py, len / 2 + 0.3f, lw * 1.1f, 1f, 0f, 0xFFFFFFFF.toInt(), 1f, Shape.BEAM)
            batch.dot(x0, wd.py, lw * 3f * fl, glow, 1f, Shape.ORB)
        }

        // Orbitals and the singularity.
        val orbs = if (wd.alive) wd.wpn.orbs else 0
        for (k in 0 until orbs) {
            val a = wd.wpn.orbA + k * 2f * PI.toFloat() / orbs
            val ox = wd.px + cos(a) * wd.wpn.orbR; val oy = wd.py + sin(a) * wd.wpn.orbR
            batch.dot(ox, oy, 0.75f, glow, 1f, Shape.SOFT)
            batch.dot(ox, oy, 0.38f, wd.trim, 1f, Shape.ORB)
        }
        if (wd.wpn.singT > 0f) {
            val x = wd.wpn.singX; val y = wd.wpn.singY
            val grow = min(1f, (5f - wd.wpn.singT) * 4f).coerceAtLeast(0.2f) * min(1f, wd.wpn.singT * 3f)
            batch.dot(x, y, 4.2f * grow, 0xFF7B2CFF.toInt(), 1f, Shape.SOFT)
            batch.dot(x, y, 2.4f * grow, wd.trim, 1f, Shape.HOLE)
            for (k in 0 until 3) {
                val r = ((time * 0.8f + k / 3f) % 1f)
                batch.dot(x, y, (4.5f - r * 3.5f) * grow, glow, 1f - r, Shape.WAVE)
            }
        }
    }

    private fun arcSprites(arcs: Array<Arc>) {
        // Tesla arcs: jagged bright lines.
        for (a in arcs) {
            if (a.life <= 0f) continue
            var seed = a.seed
            var lx = a.x1; var ly = a.y1
            val segs = 6
            val dx = a.x2 - a.x1; val dy = a.y2 - a.y1
            val len = hypot(dx, dy).coerceAtLeast(0.01f)
            val nx = -dy / len; val ny = dx / len
            for (k in 1..segs) {
                seed = seed * 1103515245 + 12345
                val j = if (k == segs) 0f else (((seed ushr 16) and 1023) / 1023f - 0.5f) * 0.9f
                val tx = a.x1 + dx * k / segs + nx * j; val ty = a.y1 + dy * k / segs + ny * j
                val sx = tx - lx; val sy = ty - ly
                val sl = hypot(sx, sy).coerceAtLeast(0.01f)
                batch.quad((lx + tx) / 2, (ly + ty) / 2, sl / 2 + 0.1f, 0.22f, sx / sl, sy / sl, 0xFF9AE8FF.toInt(), min(1f, a.life * 10f), Shape.BEAM)
                lx = tx; ly = ty
            }
        }
    }

    private fun worldBeams() {
        val wd = world
        // Enemy lasers: a flickering warning line, then the beam.
        for (bm in wd.beams) {
            if (!bm.active) continue
            val c = cos(bm.ang); val sn = sin(bm.ang)
            val len = 40f
            val col = World.BULLET_COLORS[bm.color]
            if (!bm.firing) {
                val a = 0.4f + 0.3f * sin(bm.age * 40f)
                batch.quad(bm.x + c * len / 2, bm.y + sn * len / 2, len / 2, 0.09f, c, sn, col, a, Shape.BEAM)
            } else {
                val end = min(1f, (bm.warn + bm.life - bm.age) * 6f)
                val fl = 1f + 0.08f * sin(time * 70f)
                batch.quad(bm.x + c * len / 2, bm.y + sn * len / 2, len / 2, bm.width * 1.3f * fl * end, c, sn, col, 1f, Shape.BEAM)
                batch.quad(bm.x + c * len / 2, bm.y + sn * len / 2, len / 2, bm.width * 0.55f * end, c, sn, 0xFFFFFFFF.toInt(), 1f, Shape.BEAM)
                batch.dot(bm.x, bm.y, bm.width * 2.2f, col, 1f, Shape.ORB)
            }
        }
    }

    private fun bulletSprites(b: Bullets) {
        // Enemy bullets last, so nothing hides them.
        for (i in 0 until b.n) {
            val col = World.BULLET_COLORS[b.color[i]]
            val r = b.r[i]
            when (b.shape[i]) {
                BShape.RICE -> {
                    val sp = hypot(b.vx[i], b.vy[i]).coerceAtLeast(0.01f)
                    batch.quad(b.x[i], b.y[i], r * 2.4f, r * 1.4f, b.vx[i] / sp, b.vy[i] / sp, col, 1f, Shape.BULLET)
                }
                BShape.DIAMOND -> {
                    val sp = hypot(b.vx[i], b.vy[i]).coerceAtLeast(0.01f)
                    batch.quad(b.x[i], b.y[i], r * 2.2f, r * 1.5f, b.vx[i] / sp, b.vy[i] / sp, col, 1f, Shape.DIAMOND)
                    batch.dot(b.x[i], b.y[i], r * 0.8f, 0xFFFFFFFF.toInt(), 1f, Shape.ORB)
                }
                BShape.RING -> {
                    batch.dot(b.x[i], b.y[i], r * 1.9f, col, 1f, Shape.RING)
                    batch.dot(b.x[i], b.y[i], r * 1.3f, col, 1f, Shape.BULLET)
                }
                else -> batch.dot(b.x[i], b.y[i], r * 1.7f, col, 1f, Shape.BULLET)
            }
        }
    }

    private fun worldOverlay() {
        val wd = world
        // The hitbox: the one dot that matters (its ring is under the ship).
        if (mode == Mode.PLAY && showHitbox && wd.alive) batch.dot(wd.px, wd.py, wd.ship.hitbox * 1.8f, 0xFFFFFFFF.toInt(), 1f, Shape.BULLET)

        // Full-screen flash and the phase shift's tint.
        if (mode == Mode.PLAY) {
            if (wd.flash > 0f) batch.quad(0f, 0f, wd.W + 2f, World.H + 2f, 1f, 0f, wd.flashColor, wd.flash * 0.35f, Shape.FILL)
            if (wd.timeScale < 0.99f) batch.quad(0f, 0f, wd.W + 2f, World.H + 2f, 1f, 0f, 0xFF3A1E80.toInt(), 0.12f, Shape.FILL)
            if (wd.state == World.State.PLAYING && wd.alive && wd.hull < 0.25f) {
                batch.quad(0f, 0f, wd.W + 2f, World.H + 2f, 1f, 0f, 0xFF500010.toInt(), 0.1f + 0.08f * sin(time * 6f), Shape.FILL)
            }
        }
    }

    // ---------------------------------------------------------------- free flight

    /**
     * The hub and the open-space missions: a chase camera that hangs behind and above the ship, looking a little
     * ahead of where it's going, over a world that is open in every direction.
     */
    private fun spaceFrame(dt: Float) {
        val sp = space
        // Lead the camera into the direction of travel, smoothed so turns don't whip it around.
        val lead = if (sp.hub) 0.35f else 0.25f
        // ahead of where the ship's going, and a little toward its nose, which is where its targets are when strafing
        val nose = if (sp.hub) 0f else 2.5f
        val tx = sp.px + sp.pvx * lead + cos(sp.ang) * nose; val ty = sp.py + sp.pvy * lead + sin(sp.ang) * nose
        val k = min(1f, dt * 3.5f)
        camX += (tx - camX) * k; camY += (ty - camY) * k
        var shx = 0f; var shy = 0f
        if (shakeOn && sp.shake > 0f) {
            val s = sp.shake * sp.shake * 0.35f
            shx = (Math.random().toFloat() - 0.5f) * s; shy = (Math.random().toFloat() - 0.5f) * s
        }
        val ex = camX + shx; val ey = camY - 20f + shy; val ez = 34f
        Matrix.setLookAtM(view, 0, ex, ey, ez, camX + shx, camY + 3f + shy, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, projSpace, 0, view, 0)
        Matrix.orthoM(ui, 0, 0f, viewW.toFloat(), viewH.toFloat(), 0f, -1f, 1f)

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        drawSky()
        // Two layers of dust, below the play plane, so speed and direction read even in empty space.
        val tint = if (sp.hub) sp.theme.dust.toInt() else 0xFF9AD8FF.toInt()
        if (sp.hub) spritePass(vp, -60f) { hubFar(sp.theme) }
        spritePass(vp, -30f) { dust(70f, 0.35f, 0.5f, 11, tint) }
        spritePass(vp, -9f) { dust(14f, 0.12f, 0.45f, 3, tint) }
        if (sp.hub) spritePass(vp, -4f) { hubNear(sp.theme) }
        spritePass { spaceUnder() }
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        lit.use()
        lit.f("uTime", time)
        lit.v3("uEye", ex, ey, ez)
        spaceMeshes()
        glDisable(GL_DEPTH_TEST)
        spritePass { spaceSprites() }
        spritePass(ui) { spaceScreen() }
        glDepthMask(true)
    }

    /** Hashed stars in [cell]-sized squares around the camera. */
    private fun dust(cell: Float, r: Float, a: Float, seed: Int, tint: Int = 0xFF9AD8FF.toInt()) {
        val span = if (cell > 20f) 3 else 6
        val c0 = kotlin.math.floor(camX / cell).toInt(); val r0 = kotlin.math.floor(camY / cell).toInt()
        for (i in c0 - span..c0 + span) for (j in r0 - span / 2 - 1..r0 + span + 1) {
            var hsh = i * 73856093 xor j * 19349663 xor seed * 83492791
            for (n in 0 until 3) {
                hsh = hsh * 1103515245 + 12345
                val fx = ((hsh ushr 8) and 1023) / 1023f
                hsh = hsh * 1103515245 + 12345
                val fy = ((hsh ushr 8) and 1023) / 1023f
                val tw = 0.6f + 0.4f * sin(time * 2f + (hsh and 255))
                val col = if ((hsh ushr 20) and 7 == 0) tint else 0xFFFFFFFF.toInt()
                batch.dot((i + fx) * cell, (j + fy) * cell, r * (if (cell > 20f) 3f else 1f), col, a * tw, Shape.ORB)
            }
        }
    }

    private fun dim(c: Int) = ((c and 0xFEFEFE) shr 1) or (0xFF shl 24)

    /** A steady 0..1 hash of two ints, so hub scenery sits in the same place every frame. */
    private fun hf(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    /** A line of sprite from ([x0], [y0]) to ([x1], [y1]). */
    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int, a: Float, shape: Int = Shape.BEAM) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        batch.quad((x0 + x1) / 2f, (y0 + y1) / 2f, len / 2f + w, w, dx / len, dy / len, col, a, shape)
    }

    /** Big soft clouds far below the hub, drifting slowly; [pulse] makes them breathe. */
    private fun clouds(ox: Float, oy: Float, n: Int, seed: Int, c0: Int, c1: Int, a: Float, r0: Float, r1: Float, pulse: Float = 0f) {
        for (i in 0 until n) {
            val x = ox + (hf(i, seed) - 0.5f) * 230f + sin(time * 0.05f + i) * 8f
            val y = oy + (hf(i, seed + 1) - 0.5f) * 110f + cos(time * 0.04f + i * 1.3f) * 5f
            val p = if (pulse > 0f) 1f - pulse + pulse * (0.5f + 0.5f * sin(time * 1.3f + i * 2.1f)) else 1f
            batch.dot(x, y, r0 + hf(i, seed + 2) * (r1 - r0), if (i % 2 == 0) c0 else c1, a * p, Shape.SOFT)
        }
    }

    /** Tiny lights on distant hulls, blinking out of step. */
    private fun lights(ox: Float, oy: Float, n: Int, seed: Int, col: Int, a: Float) {
        for (i in 0 until n) {
            val x = ox + (hf(i, seed) - 0.5f) * 220f; val y = oy + (hf(i, seed + 1) - 0.5f) * 100f
            val on = ((time * 0.8f + hf(i, seed + 2) * 4f) % 2f) < 1.5f
            if (on) batch.dot(x, y, 0.5f + hf(i, seed + 3), col, a, Shape.ORB)
        }
    }

    /** The hub's far backdrop, under the dust: weather, rings, a black hole, whatever the place has. */
    private fun hubFar(th: HubTheme) {
        // Anchored partly to the camera so it reads as very far away and is always somewhere in view.
        val ox = camX * 0.6f; val oy = camY * 0.6f + 40f
        val glow = th.glow.toInt(); val dust = th.dust.toInt()
        when (th.scenery) {
            Scenery.HAVEN -> clouds(ox, oy, 6, 1, 0xFF3A4AFF.toInt(), 0xFF2A8AAA.toInt(), 0.08f, 30f, 50f)
            Scenery.BELT -> {
                clouds(ox, oy, 8, 2, 0xFF8A5A2A.toInt(), 0xFF5A4A3A.toInt(), 0.1f, 25f, 45f)
                for (i in 0 until 260) {
                    val a = hf(i, 3) * 6.283f + time * 0.004f; val r = 90f + hf(i, 4) * 50f
                    batch.dot(ox + cos(a) * r, oy - 40f + sin(a) * r * 0.45f, 0.4f + hf(i, 5) * 0.9f, 0xFFB09070.toInt(), 0.5f, Shape.ORB)
                }
            }
            Scenery.SCRAP -> { clouds(ox, oy, 9, 3, 0xFFAA2A1A.toInt(), 0xFF5A2A10.toInt(), 0.12f, 25f, 50f); lights(ox, oy, 30, 4, 0xFFFF7A3A.toInt(), 0.7f) }
            Scenery.STORM -> {
                clouds(ox, oy, 12, 5, 0xFF2A4AAA.toInt(), 0xFF5A2AA0.toInt(), 0.14f, 25f, 50f, 0.4f)
                val slot = kotlin.math.floor(time / 0.9f).toInt(); val ph = time / 0.9f - slot
                for (b in 0 until 2) {
                    if (hf(slot, 50 + b) > 0.65f || ph > 0.22f) continue
                    val fl = 1f - ph / 0.22f
                    var x = ox + (hf(slot, 60 + b) - 0.5f) * 180f; var y = oy + 30f + hf(slot, 70 + b) * 20f
                    batch.dot(x, y - 18f, 40f, 0xFF8AB8FF.toInt(), 0.25f * fl, Shape.SOFT)
                    for (k in 0 until 9) {
                        val nx = x + (hf(slot * 13 + k, 80 + b) - 0.5f) * 12f; val ny = y - 4f - hf(slot * 7 + k, 90 + b) * 5f
                        seg(x, y, nx, ny, 0.5f, 0xFFE0F4FF.toInt(), fl)
                        x = nx; y = ny
                    }
                }
            }
            Scenery.WRECKS -> clouds(ox, oy, 8, 6, 0xFF2A6A4A.toInt(), 0xFF1A3A3A.toInt(), 0.1f, 30f, 55f)
            Scenery.RIFT -> {
                clouds(ox, oy, 6, 7, 0xFF5A1A9A.toInt(), 0xFF1A0A3A.toInt(), 0.12f, 30f, 50f)
                for (c in 0 until 5) {
                    var x = ox + (hf(c, 8) - 0.5f) * 200f; var y = oy + (hf(c, 9) - 0.5f) * 80f
                    var ang = hf(c, 10) * 6.283f
                    val pul = 0.55f + 0.45f * sin(time * 1.7f + c * 1.9f)
                    for (k in 0 until 12) {
                        ang += (hf(c * 31 + k, 11) - 0.5f) * 1.6f
                        val l = 4f + hf(c * 31 + k, 12) * 6f
                        val nx = x + cos(ang) * l; val ny = y + sin(ang) * l
                        seg(x, y, nx, ny, 2.5f, 0xFF7A2AFF.toInt(), 0.18f * pul, Shape.SOFT)
                        seg(x, y, nx, ny, 0.45f, 0xFFE0B0FF.toInt(), 0.8f * pul)
                        x = nx; y = ny
                    }
                }
            }
            Scenery.AURORA -> for (i in 0 until 70) {
                val x = ox - 140f + i * 4f
                val y = oy + 25f + 8f * sin(x * 0.04f + time * 0.35f)
                val a = 0.07f + 0.06f * sin(x * 0.11f - time * 0.9f)
                batch.quad(x, y, 14f + 5f * sin(x * 0.07f + time), 3f, 0f, 1f, if (i % 3 == 0) 0xFF5AE0FF.toInt() else 0xFF5AFF9A.toInt(), a, Shape.BEAM)
            }
            Scenery.EMBERS -> {
                clouds(ox, oy, 8, 13, 0xFFFF4A1A.toInt(), 0xFF8A1A08.toInt(), 0.1f, 25f, 50f, 0.3f)
                for (f in 0 until 4) {
                    val ph = (time * 0.07f + f * 0.25f) % 1f
                    batch.dot(ox - 60f + f * 40f, oy + 30f + ph * 20f, 6f + ph * 18f, 0xFFFFA81F.toInt(), 0.25f * (1f - ph), Shape.SOFT)
                }
            }
            Scenery.HIVE -> clouds(ox, oy, 12, 14, 0xFF6AAA10.toInt(), 0xFF2A5A10.toInt(), 0.13f, 18f, 40f, 0.6f)
            Scenery.FOG -> clouds(ox, oy, 16, 15, 0xFFD8D8F0.toInt(), 0xFF9AA8C8.toInt(), 0.09f, 30f, 60f)
            Scenery.RINGS -> for (i in 0 until 420) {
                val a = hf(i, 16) * 6.283f + time * 0.006f; val r = 70f + hf(i, 17) * 55f
                val band = if (r in 95f..100f) 0.15f else 0.55f
                batch.dot(ox + cos(a) * r, oy - 30f + sin(a) * r * 0.32f, 0.5f + hf(i, 18) * 0.7f, if (i % 4 == 0) dust else 0xFFC0A080.toInt(), band, Shape.ORB)
            }
            Scenery.SUN -> {
                batch.dot(ox, oy + 50f, 90f, 0xFFFFC23D.toInt(), 0.18f, Shape.SOFT)
                for (i in 0 until 50) {
                    val ph = (time * 0.25f + hf(i, 19)) % 1f
                    batch.quad(ox + (hf(i, 20) - 0.5f) * 240f, oy + 60f - ph * 130f, 9f, 0.5f, 0f, 1f, 0xFFFFE070.toInt(), 0.3f * (1f - ph), Shape.BEAM)
                }
            }
            Scenery.PHANTOM -> for (i in 0 until 12) {
                val fl = (0.5f + 0.5f * sin(time * 2.3f + i * 1.7f)).let { it * it }
                batch.dot(ox + (hf(i, 21) - 0.5f) * 220f, oy + (hf(i, 22) - 0.5f) * 100f, 25f + hf(i, 23) * 30f, if (i % 2 == 0) 0xFF8A6AFF.toInt() else 0xFF5A8AFF.toInt(), 0.13f * fl, Shape.SOFT)
            }
            Scenery.FLEET -> { clouds(ox, oy, 5, 24, 0xFF5A3A1A.toInt(), 0xFF2A2A3A.toInt(), 0.08f, 30f, 50f); lights(ox, oy, 80, 25, 0xFFFFB03A.toInt(), 0.8f) }
            Scenery.CRYSTALS -> {
                clouds(ox, oy, 8, 26, 0xFF1AAAA0.toInt(), 0xFF6A1AA0.toInt(), 0.1f, 25f, 45f, 0.3f)
                glints(ox, oy, 60, 27, glow)
            }
            Scenery.GAS -> {
                clouds(ox, oy, 8, 28, 0xFF6A2AAA.toInt(), 0xFF2A3A8A.toInt(), 0.1f, 30f, 50f)
                val slot = kotlin.math.floor(time / 1.3f).toInt(); val ph = time / 1.3f - slot
                if (hf(slot, 29) < 0.6f && ph < 0.3f)
                    batch.dot(ox + (hf(slot, 30) - 0.5f) * 160f, oy - 30f + hf(slot, 31) * 20f, 30f, 0xFFE0A0FF.toInt(), 0.3f * (1f - ph / 0.3f), Shape.SOFT)
            }
            Scenery.HOLE -> {
                val hx = ox + 55f; val hy = oy + 15f
                batch.dot(hx, hy, 55f, 0xFFFF7A2A.toInt(), 0.16f, Shape.SOFT)
                for (i in 0 until 160) {
                    val a = hf(i, 32) * 6.283f + time * (0.6f - hf(i, 33) * 0.3f); val r = 13f + hf(i, 34) * 14f
                    val hot = 1f - (r - 13f) / 14f
                    batch.dot(hx + cos(a) * r, hy + sin(a) * r * 0.32f, 0.6f + hot * 0.6f, if (hot > 0.6f) 0xFFFFE0B0.toInt() else 0xFFFF8A2A.toInt(), 0.35f + hot * 0.5f, Shape.ORB)
                }
                batch.dot(hx, hy, 9f, 0xFF000000.toInt(), 1f, Shape.HOLE)
                batch.dot(hx, hy, 11f, 0xFFFFC090.toInt(), 0.35f, Shape.WAVE)
            }
            Scenery.ARMADA -> lights(ox, oy, 60, 35, 0xFFFF3D6A.toInt(), 0.75f)
            Scenery.DUSK -> clouds(ox, oy, 12, 36, 0xFFFF8A5A.toInt(), 0xFF8A3A7A.toInt(), 0.11f, 25f, 55f)
            Scenery.SHARDS -> { clouds(ox, oy, 6, 37, 0xFF2A5AAA.toInt(), 0xFF5A2A9A.toInt(), 0.08f, 30f, 50f); glints(ox, oy, 90, 38, dust) }
            Scenery.SINGULARITY -> {
                val cx = ox; val cy = oy + 10f
                batch.dot(cx, cy, 50f + 6f * sin(time * 0.8f), 0xFFFFFFFF.toInt(), 0.12f, Shape.SOFT)
                for (arm in 0 until 3) for (k in 0 until 45) {
                    val a = k * 0.2f + time * 0.25f + arm * 2.094f; val r = 3f + k * 1.4f
                    batch.dot(cx + cos(a) * r, cy + sin(a) * r * 0.6f, 1.6f - k * 0.025f, if (k % 3 == 0) 0xFFC9A8FF.toInt() else 0xFFFFFFFF.toInt(), 0.6f - k * 0.012f, Shape.SOFT)
                }
                batch.dot(cx, cy, 4f, 0xFFFFFFFF.toInt(), 0.9f, Shape.ORB)
            }
        }
    }

    /** Twinkling diamond glints scattered across the far backdrop. */
    private fun glints(ox: Float, oy: Float, n: Int, seed: Int, col: Int) {
        for (i in 0 until n) {
            val tw = sin(time * (1.5f + hf(i, seed + 2) * 2f) + i).let { if (it > 0.7f) (it - 0.7f) / 0.3f else 0f }
            if (tw > 0f) batch.dot(ox + (hf(i, seed) - 0.5f) * 230f, oy + (hf(i, seed + 1) - 0.5f) * 110f, 1.4f, col, tw, Shape.STAR)
        }
    }

    /** Motes just under the play plane, drifting past the ship: embers, spores, snow, glints. */
    private fun hubNear(th: HubTheme) {
        when (th.scenery) {
            Scenery.EMBERS -> motes(9f, 0.25f, 0.8f, 41, 0xFFFFA81F.toInt(), 0.6f, 1.6f, Shape.SPARK)
            Scenery.HIVE -> motes(10f, 0.35f, 0.5f, 42, 0xFFC8FF2A.toInt(), 0.3f, 0.5f, Shape.SOFT)
            Scenery.AURORA -> motes(8f, 0.15f, 0.7f, 43, 0xFFFFFFFF.toInt(), 0.4f, -0.9f, Shape.ORB)
            Scenery.BELT, Scenery.RINGS -> motes(11f, 0.6f, 0.25f, 44, 0xFFB09070.toInt(), 0.2f, 0.1f, Shape.SOFT)
            Scenery.CRYSTALS, Scenery.SHARDS -> motes(10f, 0.3f, 0.9f, 45, th.dust.toInt(), 0.1f, 0.15f, Shape.DIAMOND, twinkle = true)
            Scenery.STORM -> motes(12f, 0.2f, 0.9f, 46, 0xFFA0E8FF.toInt(), 3f, -1f, Shape.SPARK, twinkle = true)
            Scenery.SCRAP -> motes(13f, 0.2f, 0.8f, 47, 0xFFFF9A3A.toInt(), 0f, -1.2f, Shape.SPARK, twinkle = true)
            Scenery.SUN -> motes(9f, 0.2f, 0.6f, 48, 0xFFFFF0A0.toInt(), 0f, -2.5f, Shape.SPARK)
            Scenery.SINGULARITY, Scenery.PHANTOM -> motes(11f, 0.3f, 0.5f, 49, th.dust.toInt(), 0.2f, 0.2f, Shape.SOFT, twinkle = true)
            Scenery.FOG -> motes(14f, 2.5f, 0.06f, 50, 0xFFFFFFFF.toInt(), 0.3f, 0f, Shape.SOFT)
            else -> {}
        }
    }

    private fun motes(cell: Float, r: Float, a: Float, seed: Int, col: Int, vx: Float, vy: Float, shape: Int, twinkle: Boolean = false) {
        val c0 = kotlin.math.floor(camX / cell).toInt(); val r0 = kotlin.math.floor(camY / cell).toInt()
        val sx = 34f / cell + 1; val sy = 24f / cell + 1
        val len = kotlin.math.sqrt(vx * vx + vy * vy)
        val dx = if (len > 0f) vx / len else 1f; val dy = if (len > 0f) vy / len else 0f
        for (i in c0 - sx.toInt()..c0 + sx.toInt()) for (j in r0 - 2..r0 + sy.toInt()) {
            for (n in 0 until 2) {
                val k = i * 7919 + j * 104729 + n * 31
                val fx = ((hf(k, seed) + time * vx / cell) % 1f + 1f) % 1f
                val fy = ((hf(k, seed + 1) + time * vy / cell) % 1f + 1f) % 1f
                val tw = if (twinkle) sin(time * 3f + hf(k, seed + 2) * 20f).coerceAtLeast(0f) else 0.6f + 0.4f * sin(time * 2f + n + i)
                if (shape == Shape.SPARK) batch.quad((i + fx) * cell, (j + fy) * cell, r * 4f, r, dx, dy, col, a * tw, shape)
                else batch.dot((i + fx) * cell, (j + fy) * cell, r * 2f, col, a * tw, shape)
            }
        }
    }

    /** Big set pieces around the hub: wrecks, shipyards, crystal spires. Drawn lit, before the stations. */
    private fun hubProps(th: HubTheme) {
        fun place(i: Int, seed: Int, r0: Float, r1: Float, z: Float, s: Float, spin: Float, tilt: Float) {
            val a = hf(i, seed) * 6.283f; val r = r0 + hf(i, seed + 1) * (r1 - r0)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, cos(a) * r, sin(a) * r, z)
            Matrix.rotateM(model, 0, hf(i, seed + 2) * 360f + time * spin, 0f, 0f, 1f)
            if (tilt != 0f) Matrix.rotateM(model, 0, tilt + time * spin * 0.5f, hf(i, seed + 3), 1f, 0.3f)
            Matrix.scaleM(model, 0, s, s, s)
        }
        when (th.scenery) {
            Scenery.SCRAP -> for (i in 0 until 12) {
                colors(0xFF5A3A2A.toInt(), 0xFF3A2A22.toInt(), 0xFFFF5A3A.toInt(), 0.5f)
                place(i, 100, if (i % 2 == 0) 50f else 92f, if (i % 2 == 0) 56f else 118f, -5f, 2f + hf(i, 104) * 2f, (hf(i, 105) - 0.5f) * 12f, 40f)
                drawMesh(when (i % 3) { 0 -> mesh(Foe.GUNSHIP) { ShipMeshes.gunship() }; 1 -> mesh(Foe.CARRIER) { ShipMeshes.carrier() }; else -> mesh(Foe.DART) { ShipMeshes.dart() } }, 0f)
            }
            Scenery.WRECKS -> for (i in 0 until 7) {
                colors(0xFF2A302E.toInt(), 0xFF1A201E.toInt(), 0xFF7BFF9A.toInt(), 0.5f)
                place(i, 110, 100f, 140f, -12f, 5f + hf(i, 114) * 4f, (hf(i, 115) - 0.5f) * 1.5f, 25f + hf(i, 116) * 40f)
                drawMesh(mesh(Foe.CARRIER) { ShipMeshes.carrier() }, 0f)
            }
            Scenery.FLEET -> for (i in 0 until 12) {
                colors(th.hull.toInt(), th.trim.toInt(), th.glow.toInt(), 2f)
                val a = i * 0.5236f + time * 0.008f
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, cos(a) * 108f, sin(a) * 108f, -6f)
                Matrix.rotateM(model, 0, Math.toDegrees(a.toDouble()).toFloat() + 180f, 0f, 0f, 1f)
                Matrix.scaleM(model, 0, 4f, 4f, 4f)
                drawMesh(mesh(Foe.CARRIER) { ShipMeshes.carrier() }, 0f)
            }
            Scenery.ARMADA -> for (i in 0 until 8) {
                colors(0xFF14141A.toInt(), 0xFF22222C.toInt(), 0xFFFF3D6A.toInt(), 1.5f)
                place(i, 120, 115f, 160f, -18f, 9f + hf(i, 124) * 5f, 0.3f, 0f)
                drawMesh(mesh(Foe.CARRIER) { ShipMeshes.carrier() }, 0f)
            }
            Scenery.CRYSTALS -> for (i in 0 until 16) {
                colors(0xFF1A5A6A.toInt(), 0xFF2AB8C0.toInt(), 0xFF5AFFE0.toInt(), 3f)
                val a = hf(i, 130) * 6.283f; val r = if (i % 2 == 0) 50f + hf(i, 131) * 6f else 90f + hf(i, 131) * 30f
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, cos(a) * r, sin(a) * r, -8f)
                Matrix.rotateM(model, 0, (hf(i, 132) - 0.5f) * 50f, 1f, 0f, 0f)
                Matrix.rotateM(model, 0, hf(i, 133) * 360f, 0f, 0f, 1f)
                val sz = 1.5f + hf(i, 134) * 1.5f
                Matrix.scaleM(model, 0, sz, sz, sz * 4f)
                drawMesh(mesh("rock${i % 3}") { ShipMeshes.rock((i % 3) * 5 + 1) }, 0.04f + 0.04f * sin(time * 2f + i))
            }
            Scenery.HIVE -> for (i in 0 until 9) {
                colors(0xFF2A3A10.toInt(), 0xFF5A7A1A.toInt(), 0xFFC8FF2A.toInt(), 1f)
                val p = 1f + 0.06f * sin(time * 1.5f + i * 1.1f)
                place(i, 140, 100f, 140f, -12f, (6f + hf(i, 144) * 5f) * p, 0.6f, 20f)
                drawMesh(mesh("rock${i % 3}") { ShipMeshes.rock((i % 3) * 5 + 1) }, 0.03f + 0.03f * sin(time * 1.5f + i * 1.1f))
            }
            else -> {}
        }
    }

    private fun spaceMeshes() {
        val sp = space
        if (sp.hub) {
            val th = sp.theme
            hubProps(th)
            colors(th.hull.toInt(), th.trim.toInt(), th.glow.toInt(), 2f)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, 0f, 0f, -5f)
            Matrix.rotateM(model, 0, time * 5f, 0f, 0f, 1f)
            Matrix.scaleM(model, 0, 11f, 11f, 11f)
            drawMesh(mesh("hubCore") { ShipMeshes.hubCore() }, 0f)
            for (st in Station.entries) {
                colors(th.hull.toInt(), dim(st.color), st.color, 2f)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, st.x, st.y, -1.2f + 0.3f * sin(time + st.ordinal))
                Matrix.rotateM(model, 0, time * (if (st.slot % 2 == 0) 9f else -9f) + st.ordinal * 37f, 0f, 0f, 1f)
                Matrix.scaleM(model, 0, st.size, st.size, st.size)
                val hi = if (sp.near == st || sp.autopilot == st) 0.07f + 0.05f * sin(time * 5f) else 0f
                drawMesh(mesh("station${st.style}") { ShipMeshes.station(st.style) }, hi)
            }
        }
        // Mission objects.
        for (i in 0 until sp.no) {
            val o = sp.objs[i]
            if (!o.alive) continue
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, o.x, o.y, 0f)
            val m = when (o.kind) {
                OKind.BEACON -> {
                    if (o.done) colors(0xFF3A3A44.toInt(), 0xFF555566.toInt(), 0xFF5AFF8A.toInt()) else colors(0xFF3A3A44.toInt(), 0xFF9AD8FF.toInt(), 0xFFFFD23D.toInt())
                    Matrix.rotateM(model, 0, time * 40f, 0f, 0f, 1f)
                    Matrix.rotateM(model, 0, 30f, 1f, 0f, 0f)
                    mesh("beacon") { ShipMeshes.beacon() }
                }
                OKind.POD -> {
                    colors(0xFF5A5E6E.toInt(), 0xFF2A2A36.toInt(), 0xFF7CE0FF.toInt(), 2f)
                    Matrix.rotateM(model, 0, Math.toDegrees(o.rot.toDouble()).toFloat(), 0.3f, 0.5f, 1f)
                    mesh("pod") { ShipMeshes.pod() }
                }
                OKind.CONVOY -> {
                    colors(0xFF6A6E7A.toInt(), 0xFF2E4A6A.toInt(), 0xFF5AFF8A.toInt(), 2f)
                    Matrix.rotateM(model, 0, Math.toDegrees(o.rot.toDouble()).toFloat(), 0f, 0f, 1f)
                    mesh("convoy") { ShipMeshes.convoy() }
                }
                OKind.GATE -> {
                    colors(0xFF3A3F4E.toInt(), 0xFF5AFF8A.toInt(), 0xFF5AFF8A.toInt(), 2f)
                    Matrix.rotateM(model, 0, time * 20f, 0f, 0f, 1f)
                    mesh("gate") { ShipMeshes.gate() }
                }
                else -> {
                    colors(0xFF4A4E5E.toInt(), 0xFF2E4A6A.toInt(), 0xFF7CE0FF.toInt(), 2f)
                    Matrix.rotateM(model, 0, time * 6f, 0f, 0f, 1f)
                    mesh("outpost") { ShipMeshes.outpost() }
                }
            }
            Matrix.scaleM(model, 0, o.r, o.r, o.r)
            drawMesh(m, 0f)
        }
        // Ships and rocks, the hub's traffic included.
        for (i in 0 until sp.ne) {
            val e = sp.enemies[i]
            if (!e.alive) continue
            if (e.ally && !isRock(e.foe)) colors(if (sp.hub) sp.theme.hull.toInt() else 0xFF3A4A5E.toInt(), 0xFFD8E0EA.toInt(), if (sp.hub) sp.theme.glow.toInt() else 0xFF7CE0FF.toInt(), 2f)
            else if (sp.hub) sp.theme.let { colors(it.rock.toInt(), it.rock.toInt(), it.glow.toInt(), if (it.scenery == Scenery.CRYSTALS || it.scenery == Scenery.SHARDS || it.scenery == Scenery.AURORA) 3f else 0f) }
            else enemyColors(e, sp.sector)
            val s = e.r * e.size
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, e.x, e.y, 0f)
            Matrix.rotateM(model, 0, Math.toDegrees(e.rot.toDouble()).toFloat(), 0f, 0f, 1f)
            if (isRock(e.foe)) Matrix.rotateM(model, 0, Math.toDegrees(e.roll.toDouble()).toFloat(), 0.6f, 0.8f, 0.2f)
            else Matrix.rotateM(model, 0, Math.toDegrees(e.roll.toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.scaleM(model, 0, s, s, s)
            drawMesh(enemyMesh(e), min(1f, e.flash * 14f))
        }
        // The player.
        if (sp.alive && sp.state != Space.State.IDLE) {
            val blink = sp.iframes > 0f && !sp.hub && ((sp.t * 12f).toInt() % 2 == 0)
            val deg = Math.toDegrees(sp.ang.toDouble()).toFloat()
            if (!blink) {
                paint(sp.loadout)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, sp.px, sp.py, 0.2f)
                Matrix.rotateM(model, 0, deg, 0f, 0f, 1f)
                Matrix.rotateM(model, 0, Math.toDegrees(sp.bank.toDouble()).toFloat(), 1f, 0f, 0f)
                val od = sp.specialT > 0f && sp.loadout.special.base == Special.OVERDRIVE
                shipMeshes(sp.loadout, if (od) 0.15f + 0.15f * sin(time * 30f) else 0f)
            }
            if (sp.loadout.support.base == Support.DRONES) {
                paint(sp.loadout)
                for (q in 0 until sp.supCount) {
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, sp.supX(q), sp.supY(q), 0.2f)
                    Matrix.rotateM(model, 0, deg, 0f, 0f, 1f)
                    drawMesh(mesh("drone") { ShipMeshes.drone() }, 0f)
                }
            }
        }
    }

    /** Glows that belong under the meshes: engines, station pads, the arena's edge. */
    private fun spaceUnder() {
        val sp = space
        if (sp.state == Space.State.IDLE) return
        if (sp.hub) {
            batch.dot(0f, 0f, 30f, sp.theme.glow.toInt(), 0.2f, Shape.SOFT)
            for (st in Station.entries) {
                batch.dot(st.x, st.y, st.size * 2.6f, st.color, 0.35f, Shape.SOFT)
                if (sp.near == st || sp.autopilot == st) {
                    val pu = 0.5f + 0.5f * sin(time * 4f)
                    batch.dot(st.x, st.y, st.dock * (0.95f + 0.05f * pu), st.color, 0.4f + 0.3f * pu, Shape.RING)
                }
            }
        }
        // The edge of the play area, shown only when it's close.
        val dx = sp.px - sp.cx; val dy = sp.py - sp.cy
        val dc = hypot(dx, dy)
        if (dc > sp.bound - 30f) {
            val a0 = kotlin.math.atan2(dy, dx)
            val al = ((dc - (sp.bound - 30f)) / 30f).coerceIn(0f, 1f)
            val step = 2.5f / sp.bound
            for (n in -18..18) {
                val a = a0 + n * step
                batch.dot(sp.cx + cos(a) * sp.bound, sp.cy + sin(a) * sp.bound, 0.35f, 0xFFFF4A6A.toInt(), al * (1f - abs(n) / 19f), Shape.ORB)
            }
        }
        if (!sp.alive) return
        val c = cos(sp.ang); val s = sin(sp.ang)
        val boost = sp.boostT > 0f
        for (o in ShipMeshes.nozzles(sp.loadout.engine)) {
            val fl = 0.85f + 0.15f * sin(time * 50f + o * 9f)
            val off = o * cos(sp.bank)
            val x = sp.px - c * 1.12f - s * off; val y = sp.py - s * 1.12f + c * off
            batch.dot(x, y, (if (boost) 0.75f else 0.42f) * fl, sp.glow, 1f, Shape.ORB)
            if (boost) batch.quad(x - c * 1f, y - s * 1f, 1.2f, 0.3f, c, s, sp.glow, 0.7f, Shape.BEAM)
        }
    }

    private fun spaceSprites() {
        val sp = space
        pickupSprites(sp.pickups)
        particleSprites(sp.parts)
        shotSprites(sp.shots, sp.flair.shotColor(sp.t), sp.trim)
        // Orbitals.
        if (sp.alive && sp.loadout.support.base == Support.ORBITALS) for (q in 0 until sp.supCount) {
            batch.dot(sp.supX(q), sp.supY(q), 0.75f, sp.glow, 1f, Shape.SOFT)
            batch.dot(sp.supX(q), sp.supY(q), 0.38f, sp.trim, 1f, Shape.ORB)
        }
        if (sp.holeT > 0f) {
            val grow = min(1f, sp.holeT * 3f)
            batch.dot(sp.hx, sp.hy, 4.2f * grow, 0xFF7B2CFF.toInt(), 1f, Shape.SOFT)
            batch.dot(sp.hx, sp.hy, 2.4f * grow, sp.trim, 1f, Shape.HOLE)
            for (q in 0 until 3) {
                val r = ((time * 0.8f + q / 3f) % 1f)
                batch.dot(sp.hx, sp.hy, (4.5f - r * 3.5f) * grow, sp.glow, 1f - r, Shape.WAVE)
            }
        }
        arcSprites(sp.arcs)
        // Health bars over the convoy, the outpost and the tough ships.
        for (i in 0 until sp.no) {
            val o = sp.objs[i]
            if (!o.alive) continue
            if (o.maxHp > 0f) bar(o.x, o.y + o.r + 1.4f, 3f, o.frac, 0xFF5AFF8A.toInt())
            if (o.kind == OKind.BEACON && !o.done) {
                val pu = (time * 0.7f) % 1f
                batch.dot(o.x, o.y, o.r * (1.5f + pu * 3f), 0xFFFFD23D.toInt(), 1f - pu, Shape.WAVE)
            }
        }
        for (i in 0 until sp.ne) {
            val e = sp.enemies[i]
            if (!e.alive || e.ally || isRock(e.foe)) continue
            if (e.mode >= 2) batch.dot(e.x, e.y, e.r * e.size * 1.9f, 0xFFFF4A6A.toInt(), 0.5f + 0.3f * sin(time * 6f), Shape.RING)
            if (e.maxHp >= 60f && e.hp < e.maxHp) bar(e.x, e.y + e.r * e.size + 1f, 1.8f, e.frac, 0xFFFF4A6A.toInt())
        }
        bulletSprites(sp.bullets)
    }

    private fun bar(x: Float, y: Float, half: Float, f: Float, col: Int) {
        batch.quad(x, y, half + 0.08f, 0.2f, 1f, 0f, 0xFF000000.toInt(), 0.6f, Shape.FILL)
        val w = half * f.coerceIn(0f, 1f)
        batch.quad(x - half + w, y, w, 0.13f, 1f, 0f, col, 0.95f, Shape.FILL)
    }

    /** Projects a world point to view pixels into [o4]; false when it's behind the camera. */
    private fun toScreen(x: Float, y: Float): Boolean {
        v4[0] = x; v4[1] = y; v4[2] = 0f; v4[3] = 1f
        Matrix.multiplyMV(o4, 0, vp, 0, v4, 0)
        val w = o4[3]
        if (w <= 0.01f) return false
        o4[0] = (o4[0] / w + 1f) * 0.5f * viewW
        o4[1] = (1f - o4[1] / w) * 0.5f * viewH
        return true
    }

    /** An arrow on the screen's edge pointing at an off-screen thing. */
    private fun edgeArrow(x: Float, y: Float, col: Int, big: Boolean) {
        val ok = toScreen(x, y)
        val vw = viewW.toFloat(); val vh = viewH.toFloat()
        val m = vh * 0.07f
        if (ok && o4[0] in m..vw - m && o4[1] in m..vh - m) return
        val cxs = vw / 2; val cys = vh / 2
        var dx: Float; var dy: Float
        if (ok) { dx = o4[0] - cxs; dy = o4[1] - cys } else { dx = x - space.px; dy = -(y - space.py) }
        val d = hypot(dx, dy).coerceAtLeast(0.01f)
        dx /= d; dy /= d
        val tx = if (abs(dx) > 1e-4f) (cxs - m) / abs(dx) else Float.MAX_VALUE
        val ty = if (abs(dy) > 1e-4f) (cys - m) / abs(dy) else Float.MAX_VALUE
        val t = min(tx, ty)
        val ax = cxs + dx * t; val ay = cys + dy * t
        val s = vh * (if (big) 0.03f else 0.018f)
        val pu = if (big) 0.75f + 0.25f * sin(time * 6f) else 0.55f
        batch.quad(ax, ay, s * 1.3f, s * 0.75f, dx, dy, col, pu, Shape.DIAMOND)
        if (big) batch.dot(ax - dx * s * 1.6f, ay - dy * s * 1.6f, s * 0.5f, col, pu, Shape.ORB)
    }

    /** Screen-space overlays: the floating stick, pointers to what matters, flashes. */
    private fun spaceScreen() {
        val sp = space
        val vw = viewW.toFloat(); val vh = viewH.toFloat()
        if (sp.state == Space.State.PLAYING) {
            if (sp.hub) {
                for (st in Station.entries) {
                    val target = sp.autopilot == st
                    if (st.ring == 0 || target) edgeArrow(st.x, st.y, st.color, target)
                }
            } else {
                for (i in 0 until sp.no) {
                    val o = sp.objs[i]
                    if (!o.alive || o.done) continue
                    val col = when (o.kind) { OKind.BEACON -> 0xFFFFD23D.toInt(); OKind.POD -> 0xFF7CE0FF.toInt(); else -> 0xFF5AFF8A.toInt() }
                    edgeArrow(o.x, o.y, col, o.kind != OKind.POD)
                }
                for (i in 0 until sp.ne) {
                    val e = sp.enemies[i]
                    if (e.alive && !e.ally && e.mode >= 2 && !isRock(e.foe)) edgeArrow(e.x, e.y, 0xFFFF4A6A.toInt(), true)
                }
            }
            if (sp.stickOn) {
                val r = sp.stickR
                batch.dot(sp.ox, sp.oy, r, 0xFFFFFFFF.toInt(), 0.18f, Shape.SOFT)
                batch.dot(sp.ox, sp.oy, r, 0xFFFFFFFF.toInt(), 0.45f, Shape.RING)
                batch.dot(sp.sx, sp.sy, r * 0.38f, sp.glow, 0.8f, Shape.ORB)
            }
        }
        if (sp.flash > 0f) batch.quad(vw / 2, vh / 2, vw / 2 + 2f, vh / 2 + 2f, 1f, 0f, sp.flashColor, sp.flash * 0.35f, Shape.FILL)
        if (!sp.hub && sp.state == Space.State.PLAYING && sp.alive && sp.hull < 0.25f) {
            batch.quad(vw / 2, vh / 2, vw / 2 + 2f, vh / 2 + 2f, 1f, 0f, 0xFF500010.toInt(), 0.1f + 0.08f * sin(time * 6f), Shape.FILL)
        }
    }
}
