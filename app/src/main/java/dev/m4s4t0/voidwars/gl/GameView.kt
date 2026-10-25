package dev.m4s4t0.voidwars.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import dev.m4s4t0.voidwars.game.Hud
import dev.m4s4t0.voidwars.game.SpaceHud
import dev.m4s4t0.voidwars.game.World
import dev.m4s4t0.voidwars.game.WorldEvent
import dev.m4s4t0.voidwars.game.WorldListener
import dev.m4s4t0.voidwars.model.Quality
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig

/**
 * The game's one GL surface, shared by the menu, the hangar and play. Rendered at the quality's scale (the compositor
 * stretches it), multisampled only on High. The world's HUD updates and events arrive here on the GL thread and are
 * passed to [listener] on the main thread.
 */
@SuppressLint("ViewConstructor")
class GameView(ctx: Context, private val quality: Quality, private val cap60: Boolean, listener: WorldListener) : GLSurfaceView(ctx) {
    val renderer = GameRenderer(
        quality,
        object : WorldListener {
            override fun hud(h: Hud) { post { listener.hud(h) } }
            override fun event(e: WorldEvent) { post { listener.event(e) } }
            override fun space(h: SpaceHud) { post { listener.space(h) } }
        },
    )
    private var lastX = 0f
    private var downX = 0f
    private var lastT = 0L
    private var vel = 0f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser { egl, display ->
            fun pick(samples: Int): EGLConfig? {
                val ms = if (samples > 0) intArrayOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples) else intArrayOf()
                val attrs = intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, 4, *ms, EGL10.EGL_NONE,
                )
                val out = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                return if (egl.eglChooseConfig(display, attrs, out, 1, num) && num[0] > 0) out[0] else null
            }
            (if (quality.msaa) pick(4) else null) ?: pick(0) ?: error("No OpenGL ES 2 config")
        }
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    /** Runs [block] on the GL thread, with the renderer. */
    fun gl(block: GameRenderer.() -> Unit) = queueEvent { renderer.block() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            holder.setFixedSize((w * quality.scale).toInt(), (h * quality.scale).toInt())
            gl { viewH = h; viewW = w; world.W = World.H * w / h }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        if (cap60) runCatching { holder.surface.setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = e.actionIndex
                if (e.actionMasked == MotionEvent.ACTION_DOWN) { lastX = e.x; downX = e.x; vel = 0f; lastT = e.eventTime }
                send(0, e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX
                val dtMs = (e.eventTime - lastT).coerceAtLeast(1L)
                vel = dx / dtMs * 16f
                lastX = e.x; lastT = e.eventTime
                gl { if (mode == GameRenderer.Mode.HANGAR) drag(dx) }
                for (i in 0 until e.pointerCount) send(1, e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                if (e.actionMasked == MotionEvent.ACTION_UP) { val v = vel; gl { if (mode == GameRenderer.Mode.HANGAR) fling(v) } }
                send(2, e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> send(3, 0, 0f, 0f)
        }
        return true
    }

    private fun send(action: Int, id: Int, x: Float, y: Float) = queueEvent {
        if (renderer.mode == GameRenderer.Mode.PLAY && !renderer.paused) renderer.world.touch(action, id, x, y)
        else if (renderer.mode == GameRenderer.Mode.SPACE && !renderer.paused) with(renderer.space) {
            when (action) {
                0 -> touchDown(id, x, y)
                1 -> touchMove(id, x, y)
                2 -> touchUp(id)
                else -> touchCancel()
            }
        }
    }
}
