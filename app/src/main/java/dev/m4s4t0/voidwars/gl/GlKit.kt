package dev.m4s4t0.voidwars.gl

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer

fun floats(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
fun floats(a: FloatArray): FloatBuffer = floats(a.size).apply { put(a); position(0) }
fun ints(n: Int): IntBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
fun shorts(a: ShortArray): ShortBuffer = ByteBuffer.allocateDirect(a.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(a); position(0) }

/** A linked shader program with its attribute and uniform locations looked up once. */
class Program(vs: String, fs: String) {
    val id: Int = GLES20.glCreateProgram()
    private val attribs = HashMap<String, Int>()
    private val uniforms = HashMap<String, Int>()

    init {
        GLES20.glAttachShader(id, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(id, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(id)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) Log.e(TAG, "link: " + GLES20.glGetProgramInfoLog(id))
    }

    fun use() = GLES20.glUseProgram(id)
    fun a(name: String) = attribs.getOrPut(name) { GLES20.glGetAttribLocation(id, name) }
    fun u(name: String) = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }

    fun f(name: String, v: Float) = GLES20.glUniform1f(u(name), v)
    fun v2(name: String, x: Float, y: Float) = GLES20.glUniform2f(u(name), x, y)
    fun v3(name: String, x: Float, y: Float, z: Float) = GLES20.glUniform3f(u(name), x, y, z)
    fun v3(name: String, c: FloatArray) = GLES20.glUniform3f(u(name), c[0], c[1], c[2])
    fun rgb(name: String, argb: Int) = GLES20.glUniform3f(u(name), (argb shr 16 and 255) / 255f, (argb shr 8 and 255) / 255f, (argb and 255) / 255f)
    fun m4(name: String, m: FloatArray) = GLES20.glUniformMatrix4fv(u(name), 1, false, m, 0)

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) Log.e(TAG, "compile: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    companion object {
        const val TAG = "VoidWars"
    }
}

/**
 * A flat-shaded mesh in one VBO: position, normal and a material channel per vertex (0 paint, 1 trim, 2 glow,
 * 3 dark metal, 4 glass), drawn as plain triangles.
 */
class LitMesh(data: FloatArray) {
    private val vbo: Int
    private val count = data.size / 7

    init {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, floats(data), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw(p: Program) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        val ap = p.a("aPos"); val an = p.a("aNor"); val am = p.a("aMat")
        GLES20.glEnableVertexAttribArray(ap)
        GLES20.glVertexAttribPointer(ap, 3, GLES20.GL_FLOAT, false, 28, 0)
        GLES20.glEnableVertexAttribArray(an)
        GLES20.glVertexAttribPointer(an, 3, GLES20.GL_FLOAT, false, 28, 12)
        GLES20.glEnableVertexAttribArray(am)
        GLES20.glVertexAttribPointer(am, 1, GLES20.GL_FLOAT, false, 28, 24)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(ap)
        GLES20.glDisableVertexAttribArray(an)
        GLES20.glDisableVertexAttribArray(am)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun release() = GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
}

/**
 * Every glowing thing in one draw call: quads of (x, y, u, v, rgba, shape), written into an int array (floats by
 * their bits) and streamed to the GPU each frame. Blended premultiplied, so a shape can glow (added) or cover in the
 * same batch. The alpha passed in is opacity: 0 draws nothing.
 */
class Batch(val maxQuads: Int) {
    private val data = IntArray(maxQuads * 4 * 6)
    private val buf = ints(maxQuads * 4 * 6)
    private var vbo = 0
    private var ibo = 0
    var quads = 0
        private set

    fun init() {
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vbo = ids[0]; ibo = ids[1]
        val idx = ShortArray(maxQuads * 6)
        for (q in 0 until maxQuads) {
            val v = (q * 4).toShort()
            idx[q * 6] = v; idx[q * 6 + 1] = (v + 1).toShort(); idx[q * 6 + 2] = (v + 2).toShort()
            idx[q * 6 + 3] = v; idx[q * 6 + 4] = (v + 2).toShort(); idx[q * 6 + 5] = (v + 3).toShort()
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, shorts(idx), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, null, GLES20.GL_STREAM_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun begin() { quads = 0 }

    /**
     * A sprite centred on (x, y), [hx] long along the unit direction (dx, dy) and [hy] wide across it, tinted
     * [argb] at [alpha].
     */
    fun quad(x: Float, y: Float, hx: Float, hy: Float, dx: Float, dy: Float, argb: Int, alpha: Float, shape: Int) {
        if (quads >= maxQuads) return
        val ax = dx * hx; val ay = dy * hx
        val bx = -dy * hy; val by = dx * hy
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        // Bytes in memory are r, g, b, a (little-endian ints).
        val col = (a shl 24) or ((argb and 255) shl 16) or (argb and 0xFF00) or ((argb shr 16) and 255)
        val sh = java.lang.Float.floatToRawIntBits(shape.toFloat())
        var o = quads * 24
        o = vert(o, x - ax - bx, y - ay - by, -1f, -1f, col, sh)
        o = vert(o, x + ax - bx, y + ay - by, 1f, -1f, col, sh)
        o = vert(o, x + ax + bx, y + ay + by, 1f, 1f, col, sh)
        vert(o, x - ax + bx, y - ay + by, -1f, 1f, col, sh)
        quads++
    }

    /** A round sprite of radius [r]. */
    fun dot(x: Float, y: Float, r: Float, argb: Int, alpha: Float, shape: Int) = quad(x, y, r, r, 1f, 0f, argb, alpha, shape)

    private fun vert(o: Int, x: Float, y: Float, u: Float, v: Float, col: Int, sh: Int): Int {
        data[o] = java.lang.Float.floatToRawIntBits(x)
        data[o + 1] = java.lang.Float.floatToRawIntBits(y)
        data[o + 2] = java.lang.Float.floatToRawIntBits(u)
        data[o + 3] = java.lang.Float.floatToRawIntBits(v)
        data[o + 4] = col
        data[o + 5] = sh
        return o + 6
    }

    fun flush(p: Program) {
        if (quads == 0) return
        val n = quads * 24
        buf.position(0)
        buf.put(data, 0, n)
        buf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, n * 4, buf)
        val ap = p.a("aPos"); val au = p.a("aUv"); val ac = p.a("aCol"); val ash = p.a("aShape")
        GLES20.glEnableVertexAttribArray(ap)
        GLES20.glVertexAttribPointer(ap, 2, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(au)
        GLES20.glVertexAttribPointer(au, 2, GLES20.GL_FLOAT, false, 24, 8)
        GLES20.glEnableVertexAttribArray(ac)
        GLES20.glVertexAttribPointer(ac, 4, GLES20.GL_UNSIGNED_BYTE, true, 24, 16)
        GLES20.glEnableVertexAttribArray(ash)
        GLES20.glVertexAttribPointer(ash, 1, GLES20.GL_FLOAT, false, 24, 20)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, quads * 6, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glDisableVertexAttribArray(ap)
        GLES20.glDisableVertexAttribArray(au)
        GLES20.glDisableVertexAttribArray(ac)
        GLES20.glDisableVertexAttribArray(ash)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        quads = 0
    }
}
