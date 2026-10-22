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
import dev.m4s4t0.voidwars.game.BShape
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
    enum class Mode { MENU, HANGAR, PLAY }

    val world = World().also { it.listener = listener }
    var mode = Mode.MENU
        private set
    var paused = false
    /** The ship on the hangar's turntable. */
    var preview = Loadout()
    var yaw = -0.6f
    private var yawV = 0f
    var viewH = 1
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

    // ---------------------------------------------------------------- commands (GL thread)

    fun menu() {
        mode = Mode.MENU
        paused = false
        val l = Game.loadout
        world.start(l, l.stats { 3 }, null, demo = true, quality = quality.particles)
    }

    fun hangar(l: Loadout) {
        mode = Mode.HANGAR
        preview = l
        paused = false
    }

    fun play(l: Loadout, stats: ShipStats, stage: Stage?) {
        mode = Mode.PLAY
        paused = false
        world.start(l, stats, stage, quality = quality.particles)
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
        if (mode == Mode.MENU && world.state == World.State.IDLE) menu()
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = (now - last) / 1e9f
        // The menu and hangar don't need more than 30 frames a second.
        if (mode != Mode.PLAY && dt < 1f / 31f) {
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
            Mode.HANGAR -> { yaw += yawV * dt * 60f; yawV *= 0.94f; if (abs(yawV) < 0.0005f) yaw += dt * 0.35f }
        }

        // Camera, with shake.
        var sx = 0f; var sy = 0f
        if (mode == Mode.PLAY && shakeOn && world.shake > 0f) {
            val s = world.shake * world.shake * 0.35f
            sx = (world.rnd.nextFloat() - 0.5f) * s; sy = (world.rnd.nextFloat() - 0.5f) * s
        }
        Matrix.setLookAtM(view, 0, sx, sy, camZ, sx, sy, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        drawSky()
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        lit.use()
        lit.f("uTime", time)
        lit.v3("uEye", sx, sy, camZ)
        if (mode == Mode.HANGAR) drawHangar() else drawWorldMeshes()
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        sprite.use()
        sprite.m4("uVp", vp)
        batch.begin()
        if (mode == Mode.HANGAR) hangarSprites() else worldSprites()
        batch.flush(sprite)
        glDisable(GL_BLEND)
        glDepthMask(true)
    }

    // ---------------------------------------------------------------- sky

    private fun drawSky() {
        sky.use()
        sky.v2("uRes", w.toFloat(), h.toFloat())
        sky.f("uTime", time)
        sky.f("uOct", quality.nebula.toFloat())
        val sec = Campaign.SECTORS.getOrNull(world.sector)
        val rift = mode == Mode.PLAY && world.rift
        when {
            mode == Mode.HANGAR -> { sky.rgb("uNebA", 0xFF3A2380.toInt()); sky.rgb("uNebB", 0xFF0B4A66.toInt()); sky.rgb("uPlanetCol", 0xFF7B5CFF.toInt()) }
            rift -> { sky.rgb("uNebA", 0xFF5A0F6E.toInt()); sky.rgb("uNebB", 0xFF102A80.toInt()); sky.rgb("uPlanetCol", 0xFFB040FF.toInt()) }
            mode == Mode.MENU || sec == null -> { sky.rgb("uNebA", 0xFF2D1A70.toInt()); sky.rgb("uNebB", 0xFF0B5570.toInt()); sky.rgb("uPlanetCol", 0xFF8A6CFF.toInt()) }
            else -> { sky.rgb("uNebA", sec.nebulaA.toInt()); sky.rgb("uNebB", sec.nebulaB.toInt()); sky.rgb("uPlanetCol", sec.planet.toInt()) }
        }
        val scroll = if (mode == Mode.HANGAR) time * 4f else world.t * 30f
        // The planet drifts past very slowly, a little further each stage.
        val stageOff = (world.stage?.index ?: 0) * 0.35f
        val px = aspect * 0.75f - ((scroll * 0.0012f + stageOff) % (aspect * 2f + 1.6f))
        if (mode == Mode.HANGAR) sky.v3("uPlanet", -aspect * 0.15f, -0.85f, 0.65f) else sky.v3("uPlanet", px, -0.42f + 0.1f * world.sector, 0.42f)
        sky.f("uScroll", scroll)
        val f = if (mode == Mode.PLAY) world.flash else 0f
        sky.f("uFlash", f)
        sky.rgb("uFlashCol", world.flashColor)
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
        if (l.support == Support.DRONES) {
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
                val od = wd.specialT > 0f && wd.loadout.special == Special.OVERDRIVE
                shipMeshes(wd.loadout, if (od) 0.15f + 0.15f * sin(time * 30f) else 0f)
            }
            if (wd.loadout.support == Support.DRONES) {
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

    private fun enemyColors(e: Enemy) {
        val sec = world.sector
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
            Foe.BOSS -> when (e.boss ?: Boss.WARDEN) {
                Boss.WARDEN -> colors(0xFF4A4E5E.toInt(), 0xFF8A6A2A.toInt(), 0xFFFFC23D.toInt(), 2f)
                Boss.HYDRA -> colors(0xFF5A1626.toInt(), 0xFF2E2A36.toInt(), 0xFFFF4A6A.toInt(), 1f)
                Boss.TEMPEST -> colors(0xFF14405A.toInt(), 0xFFA0E8FF.toInt(), 0xFF7CE0FF.toInt(), 3f)
                Boss.REVENANT -> colors(0xFF262A2E.toInt(), 0xFF3E4A3A.toInt(), 0xFF7BFF9A.toInt(), 2f)
                Boss.VOID_HEART -> colors(0xFF1A0E2E.toInt(), 0xFF3A1E6A.toInt(), 0xFFC070FF.toInt(), 5f)
            }
        }
    }

    // ---------------------------------------------------------------- sprites

    private fun hangarSprites() {
        val l = preview
        val g = swatch(l.paint.glow).argb.toInt()
        val x = -world.W * 0.36f
        // A soft glow pad under the ship and a pulse from the engines.
        batch.dot(x, -1.4f, 5.5f, g, 1f, Shape.SOFT)
        batch.quad(x, -1.4f, 5.2f, 1.6f, 1f, 0f, 0xFFFFFFFF.toInt(), 1f, Shape.WAVE)
        val a = (0.5f + 0.5f * sin(time * 2f))
        batch.quad(x, -1.4f, 4.4f * (0.9f + 0.1f * a), 1.3f, 1f, 0f, g, 1f, Shape.RING)
        if (l.support == Support.ORBITALS) {
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
        val pk = wd.pickups
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
                else -> batch.dot(pk.x[i], pk.y[i], 0.6f * bob, 0xFF7CE0FF.toInt(), 1f, Shape.STAR)
            }
        }

        // Particles.
        val p = wd.parts
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

        // Thrusters' glow behind the ship.
        if (wd.alive && wd.state != World.State.IDLE) {
            val noz = ShipMeshes.nozzles(wd.loadout.engine)
            for (o in noz) {
                val fl = 0.85f + 0.15f * sin(time * 50f + o * 9f)
                batch.dot(wd.px - 1.12f, wd.py + o * cos(wd.bank), 0.42f * fl, glow, 1f, Shape.ORB)
            }
        }

        // The player's shots.
        val s = wd.shots
        for (i in 0 until s.n) {
            val sp = hypot(s.vx[i], s.vy[i]).coerceAtLeast(0.01f)
            val dx = s.vx[i] / sp; val dy = s.vy[i] / sp
            when (s.kind[i]) {
                SKind.BOLT -> batch.quad(s.x[i], s.y[i], 0.55f, s.r[i] * 1.6f, dx, dy, glow, 1f, Shape.BEAM)
                SKind.DRONE -> batch.quad(s.x[i], s.y[i], 0.4f, s.r[i] * 1.5f, dx, dy, wd.trim, 1f, Shape.BEAM)
                SKind.PELLET -> batch.dot(s.x[i], s.y[i], s.r[i] * 2.4f, glow, 1f, Shape.ORB)
                SKind.MISSILE -> {
                    batch.quad(s.x[i] - dx * 0.25f, s.y[i] - dy * 0.25f, 0.5f, 0.22f, dx, dy, 0xFFFF9A40.toInt(), 1f, Shape.BEAM)
                    batch.quad(s.x[i], s.y[i], 0.3f, 0.16f, dx, dy, 0xFFFFFFFF.toInt(), 1f, Shape.DIAMOND)
                }
                SKind.PLASMA -> {
                    batch.dot(s.x[i], s.y[i], s.r[i] * 2.2f, wd.trim, 1f, Shape.SOFT)
                    batch.dot(s.x[i], s.y[i], s.r[i] * 1.4f, glow, 1f, Shape.ORB)
                    batch.dot(s.x[i], s.y[i], s.r[i] * 1.6f, wd.trim, 1f, Shape.RING)
                }
                SKind.RAIL -> batch.quad(s.x[i] - dx * 1.2f, s.y[i] - dy * 1.2f, 1.9f, s.r[i] * 1.8f, dx, dy, glow, 1f, Shape.BEAM)
                SKind.FLAK -> { batch.dot(s.x[i], s.y[i], s.r[i] * 2.4f, 0xFFFFA040.toInt(), 1f, Shape.ORB) }
                else -> batch.quad(s.x[i], s.y[i], 0.28f, 0.1f, dx, dy, 0xFFFFC070.toInt(), 1f, Shape.SPARK)
            }
        }

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

        // Tesla arcs: jagged bright lines.
        for (a in wd.arcs) {
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

        // Enemy bullets last, so nothing hides them.
        val b = wd.bullets
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

        // The hitbox: the one dot that matters.
        if (mode == Mode.PLAY && showHitbox && wd.alive) {
            val hb = wd.ship.hitbox
            batch.dot(wd.px, wd.py, hb * 2.4f, 0xFFFFFFFF.toInt(), 1f, Shape.BULLET)
            batch.dot(wd.px, wd.py, hb * 4.2f, glow, 1f, Shape.RING)
        }

        // Full-screen flash and the phase shift's tint.
        if (mode == Mode.PLAY) {
            if (wd.flash > 0f) batch.quad(0f, 0f, wd.W + 2f, World.H + 2f, 1f, 0f, wd.flashColor, wd.flash * 0.35f, Shape.FILL)
            if (wd.timeScale < 0.99f) batch.quad(0f, 0f, wd.W + 2f, World.H + 2f, 1f, 0f, 0xFF3A1E80.toInt(), 0.12f, Shape.FILL)
            if (wd.state == World.State.PLAYING && wd.alive && wd.hull < 0.25f) {
                batch.quad(0f, 0f, wd.W + 2f, World.H + 2f, 1f, 0f, 0xFF500010.toInt(), 0.1f + 0.08f * sin(time * 6f), Shape.FILL)
            }
        }
    }
}
